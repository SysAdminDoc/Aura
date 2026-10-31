package com.freevibe.data.repository

import com.freevibe.BuildConfig
import com.freevibe.data.legal.isProviderAvailableInCurrentArtifact
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.RequestPurpose
import com.freevibe.data.remote.purpose
import com.freevibe.data.remote.tiktok.isTikTokMediaUrlUsable
import com.freevibe.data.remote.tiktok.parseMp4DurationSeconds
import com.freevibe.data.remote.tiktok.parseTikTokCreatorEmbed
import com.freevibe.data.remote.tiktok.parseTikTokVideoEmbed
import com.freevibe.data.remote.tiktok.tiktokVideoId
import com.freevibe.data.remote.tiktok.toSound
import com.freevibe.service.SourceMetrics
import com.freevibe.util.rethrowIfCancelled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val SOURCE_TIKTOK = "tiktok"
private const val DURATION_PROBE_BYTES = 1024
private const val DURATION_PROBE_CONCURRENCY = 4

/** Creators whose uploads are ringtone clips, fetched in this order. */
internal val TIKTOK_RINGTONE_CREATORS = listOf("ringtonesforiphone")

internal val TIKTOK_FEED_TTL_MS = TimeUnit.MINUTES.toMillis(30)

@Singleton
class TikTokSoundRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val sourceMetrics: SourceMetrics,
) {
    private val feedMutex = Mutex()
    @Volatile private var cachedFeed: List<Sound> = emptyList()
    @Volatile private var cachedAtMs = 0L
    private val durations = ConcurrentHashMap<String, Double>()
    private val userAgent = "Aura/${BuildConfig.VERSION_NAME} (Android; Open Source)"

    /**
     * Latest clips from the ringtone creators, most played first. A failed refresh
     * keeps serving the last good list for this process.
     */
    suspend fun ringtones(forceRefresh: Boolean = false): List<Sound> {
        if (!isProviderAvailableInCurrentArtifact(ContentSource.TIKTOK)) return emptyList()
        return feedMutex.withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cachedFeed.isNotEmpty() && now - cachedAtMs < TIKTOK_FEED_TTL_MS) {
                return@withLock cachedFeed
            }
            try {
                val fresh = sourceMetrics.measure(SOURCE_TIKTOK) { fetchCreatorFeeds() }
                cachedFeed = fresh
                cachedAtMs = now
                fresh
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                cachedFeed.ifEmpty { throw e }
            }
        }
    }

    /**
     * A media link for [sound] that is still signed: the stored one while it lasts,
     * otherwise a fresh one from the video's embed page.
     */
    suspend fun playableMediaUrl(sound: Sound, forceFresh: Boolean = false): String? {
        val videoId = sound.tiktokVideoId() ?: return null
        if (!forceFresh) {
            val nowSec = System.currentTimeMillis() / 1000
            listOf(sound.previewUrl, sound.downloadUrl)
                .firstOrNull { isTikTokMediaUrlUsable(it, nowSec) }
                ?.let { return it }
        }
        return freshMediaUrl(videoId)
    }

    suspend fun freshMediaUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val html = sourceMetrics.measure(SOURCE_TIKTOK) { fetchText(videoEmbedUrl(videoId)) }
        val media = parseTikTokVideoEmbed(html, videoId) ?: return@withContext null
        if (media.durationSeconds > 0.0) durations.putIfAbsent(videoId, media.durationSeconds)
        media.url
    }

    private suspend fun fetchCreatorFeeds(): List<Sound> {
        var firstFailure: Exception? = null
        val sounds = TIKTOK_RINGTONE_CREATORS.flatMap { handle ->
            try {
                fetchCreator(handle)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                if (firstFailure == null) firstFailure = e
                emptyList()
            }
        }
        firstFailure?.let { if (sounds.isEmpty()) throw it }
        return sounds.distinctBy { it.id }
    }

    private suspend fun fetchCreator(handle: String): List<Sound> = withContext(Dispatchers.IO) {
        val html = fetchText(creatorEmbedUrl(handle))
        val feed = parseTikTokCreatorEmbed(html, handle)
            ?: throw IOException("TikTok embed for @$handle had no video list")
        val videos = feed.videos.sortedByDescending { it.playCount }
        val permits = Semaphore(DURATION_PROBE_CONCURRENCY)
        coroutineScope {
            videos.map { video ->
                async {
                    val seconds = durations[video.id]
                        ?: permits.withPermit { probeDurationSeconds(video.playAddr) }
                            ?.also { durations[video.id] = it }
                        ?: 0.0
                    video.toSound(feed.user, seconds)
                }
            }.awaitAll()
        }
    }

    /** Reads the first kilobyte of the clip; a failed probe only costs the duration label. */
    private fun probeDurationSeconds(mediaUrl: String): Double? {
        val request = Request.Builder().purpose(RequestPurpose.RESOLVE)
            .url(mediaUrl)
            .header("User-Agent", userAgent)
            .header("Range", "bytes=0-${DURATION_PROBE_BYTES - 1}")
            .build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val head = response.body?.byteStream()?.use { it.readNBytesCompat(DURATION_PROBE_BYTES) } ?: return null
                parseMp4DurationSeconds(head)
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder().purpose(RequestPurpose.CATALOG)
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("TikTok embed HTTP ${response.code}")
            return response.body?.string().orEmpty().ifBlank { throw IOException("TikTok embed returned an empty page") }
        }
    }

    private fun creatorEmbedUrl(handle: String): String = "https://www.tiktok.com/embed/@$handle"

    private fun videoEmbedUrl(videoId: String): String = "https://www.tiktok.com/embed/v2/$videoId"
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var filled = 0
    while (filled < limit) {
        val read = read(buffer, filled, limit - filled)
        if (read < 0) break
        filled += read
    }
    return buffer.copyOf(filled)
}
