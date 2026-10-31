package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.remote.RequestPurpose
import com.freevibe.data.remote.purpose
import com.freevibe.util.rethrowIfCancelled
import com.freevibe.data.model.Sound
import com.freevibe.data.repository.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundUrlResolver @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val youtubeRepo: YouTubeRepository,
    private val tiktokAudioExtractor: TikTokAudioExtractor,
) {
    suspend fun resolve(sound: Sound): String? = withContext(Dispatchers.IO) {
        val directCandidates = listOf(sound.downloadUrl, sound.previewUrl)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        directCandidates.firstOrNull(::isLocalMediaLocator)?.let { return@withContext it }

        // A TikTok link is a video; only the extracted sound track may leave here. Like every
        // other path, a failure resolves to null so callers show their own error.
        if (sound.source == ContentSource.TIKTOK) {
            return@withContext try {
                tiktokAudioExtractor.extract(sound)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                null
            }
        }

        sound.youtubeVideoId()?.let { videoId ->
            youtubeRepo.getAudioStreamUrl(videoId)?.let { return@withContext it }
            directCandidates.firstOrNull { candidate -> canFetch(candidate) }?.let { return@withContext it }
            return@withContext directCandidates.firstOrNull()
        }

        if (!shouldValidate(sound)) {
            return@withContext directCandidates.firstOrNull()
        }

        directCandidates.firstOrNull { candidate -> canFetch(candidate) }?.let { return@withContext it }

        resolveFromSourcePage(sound.sourcePageUrl)?.let { return@withContext it }

        directCandidates.firstOrNull()
    }

    private fun shouldValidate(sound: Sound): Boolean =
        sound.source == ContentSource.BUNDLED ||
            sound.source == ContentSource.FREESOUND ||
            sound.sourcePageUrl.contains("freesound.org", ignoreCase = true)

    private fun canFetch(url: String): Boolean {
        val request = Request.Builder().purpose(RequestPurpose.RESOLVE).url(url).head().build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 405
            }
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            false
        }
    }

    private fun resolveFromSourcePage(pageUrl: String): String? {
        if (!pageUrl.contains("freesound.org", ignoreCase = true)) return null
        val request = Request.Builder().purpose(RequestPurpose.RESOLVE).url(pageUrl).build()
        val html = try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string().orEmpty()
            }
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            ""
        }
        if (html.isBlank()) return null

        val patterns = HTML_AUDIO_URL_PATTERNS
        return patterns
            .asSequence()
            .mapNotNull { pattern -> pattern.find(html)?.groupValues?.getOrNull(1) }
            .map(::normalizeUrl)
            .firstOrNull { it.isNotBlank() }
    }

    private fun normalizeUrl(raw: String): String {
        var normalized = raw.replace("&amp;", "&")
        normalized = normalized
            .replace("https://freesound.orghttps://", "https://")
            .replace("https://freesound.orghttp://", "https://")
        if (normalized.startsWith("//")) {
            normalized = "https:$normalized"
        }
        return normalized.trim()
    }

    companion object {
        private val HTML_AUDIO_URL_PATTERNS = listOf(
            Regex("""data-static-file-url="([^"]+)""""),
            Regex("""twitter:player:stream" content="([^"]+)""""),
            Regex("""data-mp3="([^"]+)""""),
            Regex("""og:audio" content="([^"]+)""""),
        )
    }
}

private fun Sound.youtubeVideoId(): String? =
    takeIf { source == ContentSource.YOUTUBE }
        ?.id
        ?.removePrefix("yt_")
        ?.takeIf { it.isNotBlank() && it != id }
