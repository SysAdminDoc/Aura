package com.freevibe.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.freevibe.BuildConfig
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.RequestPurpose
import com.freevibe.data.remote.purpose
import com.freevibe.data.remote.tiktok.tiktokVideoId
import com.freevibe.data.repository.TikTokSoundRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

private const val TIKTOK_AUDIO_DIR = "tiktok-audio"
private const val MAX_TIKTOK_VIDEO_BYTES = 40L * 1024L * 1024L
private const val KEEP_EXTRACTED_FILES = 12
private const val DEFAULT_SAMPLE_BUFFER_BYTES = 256 * 1024

/**
 * TikTok serves ringtones as short MP4 videos. Aura keeps only the sound: the
 * clip is downloaded to cache, its AAC track is copied into an .m4a without
 * re-encoding, and the video file is deleted. Apply, download, and the editor all
 * receive a `file://` locator to that .m4a.
 */
@Singleton
class TikTokAudioExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val repository: TikTokSoundRepository,
) {
    private val mutex = Mutex()
    private val userAgent = "Aura/${BuildConfig.VERSION_NAME} (Android; Open Source)"

    suspend fun extract(sound: Sound): String? = withContext(Dispatchers.IO) {
        val videoId = sound.tiktokVideoId() ?: return@withContext null
        mutex.withLock {
            val directory = File(context.cacheDir, TIKTOK_AUDIO_DIR).apply { mkdirs() }
            val audio = File(directory, "tiktok_$videoId.m4a")
            if (audio.length() > 0L) {
                audio.setLastModified(System.currentTimeMillis())
                return@withLock Uri.fromFile(audio).toString()
            }
            // Extraction is serialized by the mutex, so any .part file here was left by a
            // process that died mid-download (a clip can be 40 MB).
            directory.listFiles { file -> file.isFile && file.name.endsWith(".part") }?.forEach { it.delete() }
            val video = File(directory, "tiktok_$videoId.mp4.part")
            val partialAudio = File(directory, "tiktok_$videoId.m4a.part")
            try {
                downloadClip(sound, video)
                remuxAudioTrack(video, partialAudio)
                if (!partialAudio.renameTo(audio)) throw IOException("Could not store the TikTok sound")
            } finally {
                video.delete()
                partialAudio.delete()
            }
            pruneExtracted(directory, keep = audio)
            Uri.fromFile(audio).toString()
        }
    }

    private suspend fun downloadClip(sound: Sound, target: File) {
        val stored = repository.playableMediaUrl(sound) ?: throw IOException("TikTok video link is unavailable")
        val code = download(stored, target)
        if (code in 200..299) return
        // Signed links can die before their stated expiry; ask the embed once for a new one.
        if (code == 403 || code == 404 || code == 410) {
            val fresh = repository.playableMediaUrl(sound, forceFresh = true)
            if (fresh != null && fresh != stored && download(fresh, target) in 200..299) return
        }
        throw IOException("TikTok video download failed (HTTP $code)")
    }

    private fun download(url: String, target: File): Int {
        val request = Request.Builder().purpose(RequestPurpose.DOWNLOAD)
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return response.code
            val body = response.body ?: throw IOException("TikTok video download returned no body")
            if (body.contentLength() > MAX_TIKTOK_VIDEO_BYTES) throw IOException("TikTok clip is too large to import")
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_TIKTOK_VIDEO_BYTES) throw IOException("TikTok clip is too large to import")
                        output.write(buffer, 0, read)
                    }
                }
            }
            return response.code
        }
    }

    private fun remuxAudioTrack(video: File, target: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var failure: Throwable? = null
        try {
            extractor.setDataSource(video.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("TikTok clip has no sound track")
            val format = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
            val bufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(DEFAULT_SAMPLE_BUFFER_BYTES)
            } else {
                DEFAULT_SAMPLE_BUFFER_BYTES
            }
            val output = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
            val outputTrack = output.addTrack(format)
            output.start()
            muxerStarted = true
            val copied = copyAudioSamples(
                source = MediaExtractorSampleSource(extractor),
                sink = MediaMuxerSampleSink(output, outputTrack),
                buffer = ByteBuffer.allocate(bufferSize),
            )
            if (copied == 0) throw IOException("TikTok clip sound track is empty")
        } catch (t: Throwable) {
            failure = t
            throw t
        } finally {
            val output = muxer
            closeRemuxResources(
                failure = failure,
                stopMuxer = if (output != null && muxerStarted) output::stop else null,
                releaseMuxer = output?.let { it::release },
                releaseExtractor = extractor::release,
            )
        }
    }

    private fun pruneExtracted(directory: File, keep: File) {
        directory.listFiles { file -> file.isFile && file.name.endsWith(".m4a") && file != keep }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_EXTRACTED_FILES - 1)
            ?.forEach { it.delete() }
    }
}

/**
 * Stops and releases the muxer, then the extractor, whatever throws on the way. MediaMuxer.stop()
 * throws when nothing was written; that is added to [failure] as suppressed so the error that got
 * here is the one reported, and rethrown only when the remux had otherwise succeeded.
 */
internal fun closeRemuxResources(
    failure: Throwable?,
    stopMuxer: (() -> Unit)?,
    releaseMuxer: (() -> Unit)?,
    releaseExtractor: () -> Unit,
) {
    try {
        try {
            stopMuxer?.invoke()
        } catch (e: RuntimeException) {
            failure?.addSuppressed(e) ?: throw e
        } finally {
            releaseMuxer?.invoke()
        }
    } finally {
        releaseExtractor()
    }
}

/** One selected track read sample by sample. */
internal interface AudioSampleSource {
    /** Bytes read into [buffer], or -1 once the track is exhausted. */
    fun readSample(buffer: ByteBuffer): Int
    val sampleTimeUs: Long
    val isSyncSample: Boolean
    fun advance()
}

internal interface AudioSampleSink {
    fun write(buffer: ByteBuffer, size: Int, presentationTimeUs: Long, isSyncSample: Boolean)
}

/**
 * Copies every sample of the source track. TikTok's AAC tracks carry an edit-list
 * lead-in, so the first samples report negative times: the loop ends on an empty
 * read, never on a negative time, and every stamp is shifted by that lead-in
 * because MediaMuxer rejects negative presentation times.
 */
internal fun copyAudioSamples(source: AudioSampleSource, sink: AudioSampleSink, buffer: ByteBuffer): Int {
    var shiftUs: Long? = null
    var copied = 0
    while (true) {
        buffer.clear()
        val size = source.readSample(buffer)
        if (size < 0) break
        val timeUs = source.sampleTimeUs
        val shift = shiftUs ?: minOf(timeUs, 0L).also { shiftUs = it }
        sink.write(buffer, size, (timeUs - shift).coerceAtLeast(0L), source.isSyncSample)
        copied++
        source.advance()
    }
    return copied
}

private class MediaExtractorSampleSource(private val extractor: MediaExtractor) : AudioSampleSource {
    override fun readSample(buffer: ByteBuffer): Int = extractor.readSampleData(buffer, 0)
    override val sampleTimeUs: Long get() = extractor.sampleTime
    override val isSyncSample: Boolean get() = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
    override fun advance() {
        extractor.advance()
    }
}

private class MediaMuxerSampleSink(
    private val muxer: MediaMuxer,
    private val trackIndex: Int,
) : AudioSampleSink {
    private val info = MediaCodec.BufferInfo()

    override fun write(buffer: ByteBuffer, size: Int, presentationTimeUs: Long, isSyncSample: Boolean) {
        info.set(0, size, presentationTimeUs, if (isSyncSample) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
        muxer.writeSampleData(trackIndex, buffer, info)
    }
}
