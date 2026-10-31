@file:androidx.annotation.OptIn(
    markerClass = [
        androidx.media3.common.util.ExperimentalApi::class,
        androidx.media3.common.util.UnstableApi::class,
    ],
)

package com.freevibe.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.metrics.LogSessionId
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.SpeedParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.MuxerException
import androidx.media3.muxer.OggMuxer
import androidx.media3.muxer.SeekableMuxerOutput
import androidx.media3.muxer.WavMuxer
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Codec
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

private const val MEDIA3_TIMEOUT_MILLISECONDS = 120_000L
private const val OPUS_SAMPLE_RATE_HZ = 48_000
private const val ANDROID_LOOP_COMMENT = "ANDROID_LOOP=true"
private const val LOSSLESS_VERIFY_MAX_BYTES = 64L * 1024 * 1024
private const val LOSSLESS_FALLBACK_PACKET_BYTES = 1024 * 1024
private const val RAW_PCM_CODEC_BUFFER_BYTES = 64 * 1024
private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")

enum class AudioFadeCurve {
    LINEAR,
    SMOOTH,
    EXPONENTIAL,
}

enum class AudioExportFormat(
    val extension: String,
    val bitratesKbps: List<Int>,
    val defaultBitrateKbps: Int?,
) {
    MP3("mp3", listOf(96, 128, 192, 256, 320), 192),
    OGG("ogg", listOf(96, 128, 192, 256), 192),
    OPUS("opus", listOf(48, 64, 96, 128, 192), 96),
    WAV("wav", emptyList(), null),
    FLAC("flac", emptyList(), null),
    M4A("m4a", listOf(96, 128, 192, 256), 192),
}

internal fun isTrimDurationWithinCodecBoundaries(
    expectedDurationMs: Long,
    actualDurationMs: Long,
    frameDurationMs: Long,
): Boolean = abs(expectedDurationMs - actualDurationMs) <=
    frameDurationMs.coerceAtLeast(1L) * 2L

internal fun isLosslessCutAllowed(
    fadeInMs: Long,
    fadeOutMs: Long,
    playbackSpeed: Float = 1f,
): Boolean = fadeInMs == 0L && fadeOutMs == 0L && playbackSpeed == 1f

internal fun losslessCutExportFormat(inputPath: String?): AudioExportFormat? {
    val extension = inputPath
        ?.let(::File)
        ?.extension
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
        ?: return null
    return AudioExportFormat.entries.firstOrNull { it.extension == extension }
}

internal fun areEncodedAudioPacketsContiguousCopy(
    sourcePackets: List<ByteArray>,
    outputPackets: List<ByteArray>,
): Boolean {
    if (sourcePackets.isEmpty() || outputPackets.isEmpty() || outputPackets.size > sourcePackets.size) {
        return false
    }
    val lastStart = sourcePackets.size - outputPackets.size
    return (0..lastStart).any { start ->
        outputPackets.indices.all { offset ->
            sourcePackets[start + offset].contentEquals(outputPackets[offset])
        }
    }
}

internal enum class PlatformAudioContainer {
    MP4,
    OGG,
    WAV,
}

internal data class PlatformAudioExportPlan(
    val audioMimeType: String,
    val container: PlatformAudioContainer,
    val bitrateKbps: Int?,
)

internal fun platformAudioExportPlan(
    exportFormat: AudioExportFormat,
    availableEncoderMimeTypes: Set<String>,
    sdkInt: Int,
    losslessInputMimeType: String? = null,
): PlatformAudioExportPlan? {
    if (losslessInputMimeType != null) {
        return when (exportFormat) {
            AudioExportFormat.M4A -> losslessInputMimeType
                .takeIf { it == MimeTypes.AUDIO_AAC }
                ?.let { PlatformAudioExportPlan(it, PlatformAudioContainer.MP4, null) }
            AudioExportFormat.WAV -> losslessInputMimeType
                .takeIf { it == MimeTypes.AUDIO_RAW }
                ?.let { PlatformAudioExportPlan(it, PlatformAudioContainer.WAV, null) }
            AudioExportFormat.OGG -> losslessInputMimeType
                .takeIf { it == MimeTypes.AUDIO_VORBIS || it == MimeTypes.AUDIO_OPUS }
                ?.let { PlatformAudioExportPlan(it, PlatformAudioContainer.OGG, null) }
            AudioExportFormat.OPUS -> losslessInputMimeType
                .takeIf { it == MimeTypes.AUDIO_OPUS }
                ?.let { PlatformAudioExportPlan(it, PlatformAudioContainer.OGG, null) }
            AudioExportFormat.MP3,
            AudioExportFormat.FLAC,
            -> null
        }
    }

    return when (exportFormat) {
        AudioExportFormat.M4A -> MimeTypes.AUDIO_AAC
            .takeIf(availableEncoderMimeTypes::contains)
            ?.let { PlatformAudioExportPlan(it, PlatformAudioContainer.MP4, exportFormat.defaultBitrateKbps) }
        AudioExportFormat.WAV -> PlatformAudioExportPlan(MimeTypes.AUDIO_RAW, PlatformAudioContainer.WAV, null)
        AudioExportFormat.OPUS -> MimeTypes.AUDIO_OPUS
            .takeIf { sdkInt >= Build.VERSION_CODES.Q && it in availableEncoderMimeTypes }
            ?.let { PlatformAudioExportPlan(it, PlatformAudioContainer.OGG, exportFormat.defaultBitrateKbps) }
        // Most phones have no Vorbis encoder, and an Ogg file may hold Opus just as well.
        AudioExportFormat.OGG -> when {
            MimeTypes.AUDIO_VORBIS in availableEncoderMimeTypes ->
                PlatformAudioExportPlan(MimeTypes.AUDIO_VORBIS, PlatformAudioContainer.OGG, exportFormat.defaultBitrateKbps)
            sdkInt >= Build.VERSION_CODES.Q && MimeTypes.AUDIO_OPUS in availableEncoderMimeTypes ->
                PlatformAudioExportPlan(MimeTypes.AUDIO_OPUS, PlatformAudioContainer.OGG, exportFormat.defaultBitrateKbps)
            else -> null
        }
        AudioExportFormat.MP3,
        AudioExportFormat.FLAC,
        -> null
    }
}

/** MP3 and FLAC are encoded by the app itself, and the other formats need a platform encoder. */
internal fun exportableAudioFormats(
    availableEncoderMimeTypes: Set<String>,
    sdkInt: Int,
): List<AudioExportFormat> = AudioExportFormat.entries.filter { format ->
    format == AudioExportFormat.MP3 ||
        format == AudioExportFormat.FLAC ||
        platformAudioExportPlan(format, availableEncoderMimeTypes, sdkInt) != null
}

internal fun speedAdjustedDurationMs(durationMs: Long, playbackSpeed: Float): Long {
    require(playbackSpeed > 0f) { "Playback speed must be positive" }
    return (durationMs / playbackSpeed).roundToLong()
}

internal fun AudioFadeCurve.gain(progress: Float): Float {
    val clamped = progress.coerceIn(0f, 1f)
    return when (this) {
        AudioFadeCurve.LINEAR -> clamped
        AudioFadeCurve.SMOOTH -> sin(clamped * PI.toFloat() / 2f)
        AudioFadeCurve.EXPONENTIAL -> clamped.pow(3)
    }
}

internal class PcmFadeAudioProcessor(
    private val durationMs: Long,
    private val fadeInMs: Long,
    private val fadeOutMs: Long,
    private val fadeCurve: AudioFadeCurve,
) : BaseAudioProcessor() {
    private var processedFrames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean =
        super.isActive() && (fadeInMs > 0L || fadeOutMs > 0L)

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = inputAudioFormat.bytesPerFrame
        require(bytesPerFrame > 0 && inputBuffer.remaining() % bytesPerFrame == 0) {
            "PCM input must contain complete audio frames"
        }
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(input.remaining()).order(ByteOrder.nativeOrder())
        while (input.hasRemaining()) {
            val positionMs = processedFrames * 1000f / inputAudioFormat.sampleRate
            val fadeInGain = if (fadeInMs > 0L && positionMs < fadeInMs) {
                fadeCurve.gain(positionMs / fadeInMs)
            } else {
                1f
            }
            val fadeOutStartMs = durationMs - fadeOutMs
            val fadeOutGain = if (fadeOutMs > 0L && positionMs >= fadeOutStartMs) {
                fadeCurve.gain((durationMs - positionMs) / fadeOutMs)
            } else {
                1f
            }
            val gain = min(fadeInGain, fadeOutGain)
            repeat(inputAudioFormat.channelCount) {
                val scaled = (input.getShort() * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output.putShort(scaled.toShort())
            }
            processedFrames++
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        processedFrames = 0L
    }

    override fun onReset() {
        processedFrames = 0L
    }
}

private class ConstantAudioSpeedProvider(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

/**
 * Transformer hands the muxer every tag on the input, and OggMuxer and WavMuxer throw on all of
 * them, so a tagged MP3, FLAC or OGG would fail the export. Exports don't carry those tags on, and
 * the OGG loop tag goes into the finished file through [addOggComment].
 */
private class TagDroppingMuxer(delegate: Muxer) : Muxer by delegate {
    override fun addMetadataEntry(metadataEntry: Metadata.Entry) = Unit
}

internal class PlatformAudioMuxerFactory(
    private val plan: PlatformAudioExportPlan,
) : Muxer.Factory {
    override fun create(path: String): Muxer = try {
        when (plan.container) {
            PlatformAudioContainer.OGG -> TagDroppingMuxer(OggMuxer.Builder(FileOutputStream(path).channel).build())
            PlatformAudioContainer.WAV -> TagDroppingMuxer(WavMuxer(SeekableMuxerOutput.of(path)))
            PlatformAudioContainer.MP4 -> error("The platform MP4 muxer is selected by Transformer")
        }
    } catch (error: Exception) {
        throw MuxerException("Could not create the ${plan.container.name} audio muxer", error)
    }

    override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
        if (trackType == C.TRACK_TYPE_AUDIO) ImmutableList.of(plan.audioMimeType) else ImmutableList.of()
}

/**
 * Media3 1.11 exposes WavMuxer but still sends AUDIO_RAW through EncoderFactory.
 * Keep Transformer in charge of clipping, effects, and speed while passing its
 * processed PCM straight to WavMuxer instead of looking for a nonexistent PCM encoder.
 */
private class PlatformAudioEncoderFactory(
    context: Context,
    bitrateKbps: Int?,
    private val targetAudioMimeType: String,
) : Codec.EncoderFactory {
    private val delegate = DefaultEncoderFactory.Builder(context)
        .apply {
            bitrateKbps?.let { bitrate ->
                setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder()
                        .setBitrate(bitrate * 1_000)
                        .build(),
                )
            }
        }
        .setEnableFallback(false)
        .build()

    override fun createForAudioEncoding(format: Format, logSessionId: LogSessionId?): Codec =
        if (targetAudioMimeType == MimeTypes.AUDIO_RAW) {
            RawPcmPassthroughCodec(
                format.buildUpon()
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .build(),
            )
        } else {
            delegate.createForAudioEncoding(format, logSessionId)
        }

    override fun createForVideoEncoding(format: Format, logSessionId: LogSessionId?): Codec =
        delegate.createForVideoEncoding(format, logSessionId)

    override fun isVideoFormatSupported(format: Format): Boolean =
        delegate.isVideoFormatSupported(format)

    override fun audioNeedsEncoding(): Boolean = delegate.audioNeedsEncoding()

    override fun videoNeedsEncoding(): Boolean = delegate.videoNeedsEncoding()
}

private class RawPcmPassthroughCodec(
    private val format: Format,
) : Codec {
    private val inputData = ByteBuffer.allocateDirect(RAW_PCM_CODEC_BUFFER_BYTES)
        .order(ByteOrder.nativeOrder())
    private var outputData: ByteBuffer? = null
    private var outputInfo: MediaCodec.BufferInfo? = null
    private var inputEnded = false

    override fun getConfigurationFormat(): Format = format

    override fun getName(): String = "AuraRawPcmPassthrough"

    override fun getInputSurface(): Surface =
        throw UnsupportedOperationException("Raw PCM does not use an input surface")

    override fun maybeDequeueInputBuffer(inputBuffer: DecoderInputBuffer): Boolean {
        if (inputEnded || outputData != null) return false
        inputData.clear()
        inputBuffer.clear()
        inputBuffer.data = inputData
        return true
    }

    override fun queueInputBuffer(inputBuffer: DecoderInputBuffer) {
        check(!inputEnded) { "PCM input cannot be queued after end of stream" }
        val data = requireNotNull(inputBuffer.data)
        if (inputBuffer.isEndOfStream) {
            inputEnded = true
            inputBuffer.clear()
            inputBuffer.data = null
            return
        }
        check(outputData == null) { "PCM output must be released before more input is queued" }
        outputData = data.asReadOnlyBuffer()
        outputInfo = MediaCodec.BufferInfo().apply {
            set(0, data.remaining(), inputBuffer.timeUs, 0)
        }
        inputBuffer.data = null
    }

    override fun signalEndOfInputStream() {
        inputEnded = true
    }

    override fun getInputFormat(): Format = format

    override fun getOutputFormat(): Format = format

    override fun getOutputBuffer(): ByteBuffer? = outputData

    override fun getOutputBufferInfo(): MediaCodec.BufferInfo? = outputInfo

    override fun releaseOutputBuffer(render: Boolean) {
        check(!render) { "Raw PCM output cannot be rendered" }
        outputData = null
        outputInfo = null
    }

    override fun releaseOutputBuffer(renderPresentationTimeUs: Long) {
        throw UnsupportedOperationException("Raw PCM output cannot be rendered")
    }

    override fun isEnded(): Boolean = inputEnded && outputData == null

    override fun release() {
        outputData = null
        outputInfo = null
        inputEnded = true
    }
}

@Singleton
class AudioTrimmer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun availableExportFormats(): List<AudioExportFormat> =
        exportableAudioFormats(availableAudioEncoderMimeTypes(), Build.VERSION.SDK_INT)

    /** Clip and process audio with Media3, then encode it with a platform codec or the app's own. */
    suspend fun trim(
        inputPath: String,
        startMs: Long,
        endMs: Long,
        outputFileName: String,
        fadeInMs: Long = 0,
        fadeOutMs: Long = 0,
        fadeCurve: AudioFadeCurve = AudioFadeCurve.LINEAR,
        playbackSpeed: Float = 1f,
        exportFormat: AudioExportFormat = AudioExportFormat.M4A,
        bitrateKbps: Int? = exportFormat.defaultBitrateKbps,
        losslessCut: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        var pendingOutput: File? = null
        var pendingIntermediate: File? = null
        runCatching {
            require(startMs >= 0L) { "Start time must not be negative" }
            require(endMs > startMs) { "End time must be after start time" }
            require(playbackSpeed in 0.5f..2f) { "Playback speed must be between 0.5x and 2x" }
            require(!losslessCut || isLosslessCutAllowed(fadeInMs, fadeOutMs, playbackSpeed)) {
                "Lossless cut requires fades and speed changes to be disabled"
            }
            val effectiveFormat = if (losslessCut) {
                losslessCutExportFormat(inputPath)
                    ?: throw Exception("Lossless cut requires a supported audio file format")
            } else {
                exportFormat
            }
            require(losslessCut || bitrateKbps == null || bitrateKbps in exportFormat.bitratesKbps) {
                "Unsupported ${exportFormat.name} bitrate: $bitrateKbps kbps"
            }

            val outputDir = File(context.cacheDir, "trimmed")
            outputDir.mkdirs()
            val outputFile = File(
                outputDir,
                "${outputFileName.replace(SANITIZE_REGEX, "_")}.${effectiveFormat.extension}",
            )
            pendingOutput = outputFile
            outputFile.delete()

            if (losslessCut) {
                val inputMimeType = readAudioMimeType(inputPath)
                val plan = platformAudioExportPlan(
                    exportFormat = effectiveFormat,
                    availableEncoderMimeTypes = emptySet(),
                    sdkInt = Build.VERSION.SDK_INT,
                    losslessInputMimeType = inputMimeType,
                )
                when {
                    plan != null -> exportWithMedia3(
                        inputPath = inputPath,
                        outputPath = outputFile.absolutePath,
                        startMs = startMs,
                        endMs = endMs,
                        fadeInMs = 0L,
                        fadeOutMs = 0L,
                        fadeCurve = fadeCurve,
                        playbackSpeed = 1f,
                        plan = plan,
                    )
                    effectiveFormat == AudioExportFormat.FLAC ->
                        cutFlacFrames(File(inputPath), outputFile, startMs, endMs) { ensureActive() }
                    effectiveFormat == AudioExportFormat.MP3 && inputMimeType == MimeTypes.AUDIO_MPEG ->
                        cutMp3Frames(inputPath, outputFile, startMs, endMs) { ensureActive() }
                    else -> throw Exception("Lossless cut can't copy $inputMimeType into ${effectiveFormat.name}")
                }
            } else {
                val plan = platformAudioExportPlan(
                    exportFormat = exportFormat,
                    availableEncoderMimeTypes = availableAudioEncoderMimeTypes(),
                    sdkInt = Build.VERSION.SDK_INT,
                )?.copy(bitrateKbps = bitrateKbps)
                if (plan != null) {
                    exportWithMedia3(
                        inputPath = inputPath,
                        outputPath = outputFile.absolutePath,
                        startMs = startMs,
                        endMs = endMs,
                        fadeInMs = fadeInMs,
                        fadeOutMs = fadeOutMs,
                        fadeCurve = fadeCurve,
                        playbackSpeed = playbackSpeed,
                        plan = plan,
                        outputSampleRateHz = OPUS_SAMPLE_RATE_HZ.takeIf { plan.audioMimeType == MimeTypes.AUDIO_OPUS },
                    )
                } else {
                    require(exportFormat == AudioExportFormat.MP3 || exportFormat == AudioExportFormat.FLAC) {
                        "This device has no ${exportFormat.name} encoder"
                    }
                    val intermediate = File(
                        outputDir,
                        ".${outputFile.nameWithoutExtension}.${System.nanoTime()}.wav",
                    )
                    pendingIntermediate = intermediate
                    intermediate.delete()
                    exportWithMedia3(
                        inputPath = inputPath,
                        outputPath = intermediate.absolutePath,
                        startMs = startMs,
                        endMs = endMs,
                        fadeInMs = fadeInMs,
                        fadeOutMs = fadeOutMs,
                        fadeCurve = fadeCurve,
                        playbackSpeed = playbackSpeed,
                        plan = PlatformAudioExportPlan(
                            audioMimeType = MimeTypes.AUDIO_RAW,
                            container = PlatformAudioContainer.WAV,
                            bitrateKbps = null,
                        ),
                    )
                    require(intermediate.exists() && intermediate.length() > 100L) {
                        "Platform audio processing did not produce an intermediate file"
                    }
                    if (exportFormat == AudioExportFormat.MP3) {
                        encodeWavToMp3(intermediate, outputFile, bitrateKbps ?: checkNotNull(exportFormat.defaultBitrateKbps)) {
                            ensureActive()
                        }
                    } else {
                        encodeWavToFlac(intermediate, outputFile) { ensureActive() }
                    }
                }
            }

            require(outputFile.exists() && outputFile.length() > 0L) {
                "Audio export did not produce a non-empty file"
            }
            if (effectiveFormat == AudioExportFormat.OGG) addOggComment(outputFile, ANDROID_LOOP_COMMENT)
            if (losslessCut && !losslessCutMatchesSource(inputPath, outputFile, effectiveFormat)) {
                throw Exception("Lossless export verification failed: copied audio bytes changed")
            }
            val timing = readEncodedAudioTiming(outputFile.absolutePath)
            val expectedDurationMs = speedAdjustedDurationMs(endMs - startMs, playbackSpeed)
            if (
                timing.durationMs > 0L &&
                !isTrimDurationWithinCodecBoundaries(
                    expectedDurationMs = expectedDurationMs,
                    actualDurationMs = timing.durationMs,
                    frameDurationMs = timing.frameDurationMs,
                )
            ) {
                throw Exception(
                    "Audio export duration ${timing.durationMs}ms exceeded codec-boundary trim tolerance",
                )
            }
            outputFile.absolutePath
        }.onSuccess {
            pendingIntermediate?.delete()
        }.onFailure {
            pendingIntermediate?.delete()
            pendingOutput?.delete()
            it.rethrowIfCancelled()
        }
    }

    private suspend fun exportWithMedia3(
        inputPath: String,
        outputPath: String,
        startMs: Long,
        endMs: Long,
        fadeInMs: Long,
        fadeOutMs: Long,
        fadeCurve: AudioFadeCurve,
        playbackSpeed: Float,
        plan: PlatformAudioExportPlan,
        outputSampleRateHz: Int? = null,
    ) = withTimeout(MEDIA3_TIMEOUT_MILLISECONDS) {
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(File(inputPath)))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                            .setEndPositionMs(endMs)
                            .build(),
                    )
                    .build()
                val outputDurationMs = speedAdjustedDurationMs(endMs - startMs, playbackSpeed)
                val audioProcessors = buildList<AudioProcessor> {
                    if (fadeInMs > 0L || fadeOutMs > 0L) {
                        add(
                            PcmFadeAudioProcessor(
                                durationMs = outputDurationMs,
                                fadeInMs = fadeInMs.coerceAtMost(outputDurationMs),
                                fadeOutMs = fadeOutMs.coerceAtMost(outputDurationMs),
                                fadeCurve = fadeCurve,
                            ),
                        )
                    }
                    if (outputSampleRateHz != null) {
                        add(SonicAudioProcessor().apply { setOutputSampleRateHz(outputSampleRateHz) })
                    }
                }
                val editedItemBuilder = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .setEffects(Effects(audioProcessors, emptyList()))
                if (playbackSpeed != 1f) {
                    editedItemBuilder.setSpeed(
                        SpeedParameters(ConstantAudioSpeedProvider(playbackSpeed), true),
                    )
                }

                val transformerBuilder = Transformer.Builder(context)
                    .setLooper(Looper.getMainLooper())
                    .setAudioMimeType(plan.audioMimeType)
                    .setEncoderFactory(
                        PlatformAudioEncoderFactory(
                            context = context,
                            bitrateKbps = plan.bitrateKbps,
                            targetAudioMimeType = plan.audioMimeType,
                        ),
                    )
                if (plan.container != PlatformAudioContainer.MP4) {
                    transformerBuilder.setMuxerFactory(
                        PlatformAudioMuxerFactory(plan),
                    )
                }

                lateinit var transformer: Transformer
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                }
                transformer = transformerBuilder.addListener(listener).build()
                continuation.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post { transformer.cancel() }
                }
                try {
                    transformer.start(editedItemBuilder.build(), outputPath)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }
    }

    private fun availableAudioEncoderMimeTypes(): Set<String> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .asSequence()
            .filter { it.isEncoder }
            .flatMap { it.supportedTypes.asSequence() }
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
    }.getOrDefault(emptySet())

    private fun readAudioMimeType(path: String): String? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            (0 until extractor.trackCount).firstNotNullOfOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.takeIf { it.startsWith("audio/") }
            }
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /** MP3 frames carry their own headers, so a cut is the frames of that stretch written back to back. */
    private fun cutMp3Frames(
        inputPath: String,
        output: File,
        startMs: Long,
        endMs: Long,
        beforeEachFrame: () -> Unit,
    ) {
        val startUs = startMs * 1_000L
        val endUs = endMs * 1_000L
        var firstFrame: ByteArray? = null
        var firstBitrateKbps = 0
        var mixedBitrates = false
        var frameCount = 0
        var audioBytes = 0L
        forEachEncodedAudioPacket(inputPath) { frame, timeUs ->
            beforeEachFrame()
            if (timeUs >= endUs) return@forEachEncodedAudioPacket false
            if (timeUs >= startUs) {
                val info = parseMp3FrameHeader(frame) ?: throw Exception("MP3 audio has a damaged frame")
                if (firstFrame == null) {
                    firstFrame = frame
                    firstBitrateKbps = info.bitrateKbps
                } else if (info.bitrateKbps != firstBitrateKbps) {
                    mixedBitrates = true
                }
                frameCount += 1
                audioBytes += frame.size
            }
            true
        }
        val first = firstFrame ?: throw Exception("The chosen stretch is past the end of the MP3 audio")
        FileOutputStream(output).use { mp3 ->
            if (mixedBitrates) mp3.write(mp3VbrTagFrame(first, frameCount, audioBytes))
            forEachEncodedAudioPacket(inputPath) { frame, timeUs ->
                beforeEachFrame()
                if (timeUs >= endUs) return@forEachEncodedAudioPacket false
                if (timeUs >= startUs) mp3.write(frame)
                true
            }
        }
    }

    // A FLAC cut renumbers its frames, so the extractor's packets can't match the source byte for byte.
    private fun losslessCutMatchesSource(inputPath: String, output: File, format: AudioExportFormat): Boolean =
        if (format == AudioExportFormat.FLAC) {
            flacCutKeepsSourceAudio(File(inputPath), output)
        } else {
            verifyLosslessPacketCopy(inputPath, output.absolutePath)
        }

    private fun verifyLosslessPacketCopy(inputPath: String, outputPath: String): Boolean {
        val outputPackets = readEncodedAudioPackets(outputPath)
        if (outputPackets.isEmpty()) return false
        val outputBytes = ByteArray(outputPackets.sumOf(ByteArray::size))
        var outputOffset = 0
        outputPackets.forEach { packet ->
            packet.copyInto(outputBytes, destinationOffset = outputOffset)
            outputOffset += packet.size
        }
        val prefix = IntArray(outputBytes.size)
        for (index in 1 until outputBytes.size) {
            var candidate = prefix[index - 1]
            while (candidate > 0 && outputBytes[index] != outputBytes[candidate]) {
                candidate = prefix[candidate - 1]
            }
            if (outputBytes[index] == outputBytes[candidate]) candidate++
            prefix[index] = candidate
        }

        var matched = 0
        var found = false
        forEachEncodedAudioPacket(inputPath, packetLoop@ { sourcePacket, _ ->
            sourcePacket.forEach { sourceByte ->
                while (matched > 0 && sourceByte != outputBytes[matched]) {
                    matched = prefix[matched - 1]
                }
                if (sourceByte == outputBytes[matched]) matched++
                if (matched == outputBytes.size) {
                    found = true
                    return@packetLoop false
                }
            }
            true
        })
        return found
    }

    private fun readEncodedAudioPackets(path: String): List<ByteArray> {
        val packets = ArrayList<ByteArray>()
        var totalBytes = 0L
        forEachEncodedAudioPacket(path) { packet, _ ->
            totalBytes += packet.size
            if (totalBytes > LOSSLESS_VERIFY_MAX_BYTES) {
                throw Exception("Lossless export verification input is too large")
            }
            packets += packet
            true
        }
        return packets
    }

    private fun forEachEncodedAudioPacket(path: String, block: (ByteArray, Long) -> Boolean): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false
            extractor.selectTrack(trackIndex)
            val fallbackBuffer = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                ByteBuffer.allocate(LOSSLESS_FALLBACK_PACKET_BYTES)
            } else {
                null
            }
            while (true) {
                val buffer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val sampleSize = extractor.sampleSize
                    if (sampleSize < 0L) break
                    require(sampleSize <= Int.MAX_VALUE) { "Encoded audio sample is too large" }
                    ByteBuffer.allocate(sampleSize.toInt())
                } else {
                    fallbackBuffer!!.apply { clear() }
                }
                val bytesRead = extractor.readSampleData(buffer, 0)
                if (bytesRead < 0) break
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && bytesRead == buffer.capacity()) {
                    throw Exception("Encoded audio sample exceeds the verifier buffer")
                }
                if (bytesRead > 0 && !block(buffer.array().copyOf(bytesRead), extractor.sampleTime)) return false
                if (!extractor.advance()) break
            }
            true
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private data class EncodedAudioTiming(val durationMs: Long, val frameDurationMs: Long)

    private fun readEncodedAudioTiming(path: String): EncodedAudioTiming {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return EncodedAudioTiming(0L, 1L)
            val format = extractor.getTrackFormat(trackIndex)
            val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                (format.getLong(MediaFormat.KEY_DURATION) / 1_000L).coerceAtLeast(0L)
            } else {
                0L
            }
            extractor.selectTrack(trackIndex)
            var previousSampleUs = C.TIME_UNSET
            var frameDurationMs = 1L
            var samplesInspected = 0
            while (samplesInspected < 8) {
                samplesInspected += 1
                val sampleUs = extractor.sampleTime
                if (sampleUs >= 0L) {
                    if (previousSampleUs != C.TIME_UNSET && sampleUs > previousSampleUs) {
                        frameDurationMs = ((sampleUs - previousSampleUs + 999L) / 1_000L)
                            .coerceAtLeast(1L)
                        break
                    }
                    previousSampleUs = sampleUs
                }
                if (!extractor.advance()) break
            }
            EncodedAudioTiming(durationMs, frameDurationMs)
        } catch (_: Exception) {
            EncodedAudioTiming(0L, 1L)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /** Clean up trimmed files cache */
    fun clearTrimCache() {
        File(context.cacheDir, "trimmed").deleteRecursively()
    }
}
