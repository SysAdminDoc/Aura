package com.freevibe.service

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
@LargeTest
class AudioTrimmerInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtureDirectory get() = File(context.cacheDir, "audio_platform_fixtures")
    private val trimmer get() = AudioTrimmer(context)

    @After
    fun cleanFixtures() {
        trimmer.clearTrimCache()
        fixtureDirectory.deleteRecursively()
    }

    @Test
    fun platformWavPipelineTrimsAndFadesByteComparableFixture() = runBlocking {
        val sampleRate = 8_000
        val input = File(fixtureDirectory, "constant.wav")
        writePcm16Wav(input, ShortArray(sampleRate) { 10_000 }, sampleRate)

        val output = trimmer.trim(
            inputPath = input.absolutePath,
            startMs = 200L,
            endMs = 800L,
            outputFileName = "platform_fade_fixture",
            fadeInMs = 200L,
            fadeOutMs = 200L,
            fadeCurve = AudioFadeCurve.LINEAR,
            exportFormat = AudioExportFormat.WAV,
        ).getOrThrow()

        val expected = ShortArray(4_800) { index ->
            val gain = when {
                index < 1_600 -> index / 1_600f
                index >= 3_200 -> (4_800 - index) / 1_600f
                else -> 1f
            }
            (10_000 * gain).roundToInt().toShort()
        }
        assertArrayEquals(expected, readPcm16Wav(File(output)))
    }

    @Test
    fun platformWavSpeedKeepsPitchAndHalvesDuration() = runBlocking {
        val sampleRate = 44_100
        val input = File(fixtureDirectory, "tone.wav")
        writePcm16Wav(input, sineFixture(sampleRate, durationMs = 1_000L), sampleRate)

        val output = trimmer.trim(
            inputPath = input.absolutePath,
            startMs = 0L,
            endMs = 1_000L,
            outputFileName = "platform_speed_fixture",
            playbackSpeed = 2f,
            exportFormat = AudioExportFormat.WAV,
        ).getOrThrow()
        val samples = readPcm16Wav(File(output))
        val durationMs = samples.size * 1_000L / sampleRate
        val zeroCrossings = (1 until samples.size).count { index ->
            val first = samples[index - 1]
            val second = samples[index]
            first < 0 && second >= 0 || first >= 0 && second < 0
        }
        val detectedFrequency = zeroCrossings * 500f / durationMs

        assertTrue(durationMs in 490L..510L)
        assertTrue(detectedFrequency in 420f..460f)
    }

    @Test
    fun platformAacPipelineConvertsWavToM4a() = runBlocking {
        val sampleRate = 44_100
        val input = File(fixtureDirectory, "convert.wav")
        writePcm16Wav(input, sineFixture(sampleRate, durationMs = 1_000L), sampleRate)

        val output = trimmer.trim(
            inputPath = input.absolutePath,
            startMs = 0L,
            endMs = 1_000L,
            outputFileName = "platform_aac_fixture",
            exportFormat = AudioExportFormat.M4A,
            bitrateKbps = 192,
        ).getOrThrow()
        val (mimeType, durationMs) = readEncodedAudio(File(output))

        assertEquals("audio/mp4a-latm", mimeType)
        val aacFrameDurationMs = (1_024L * 1_000L + sampleRate - 1L) / sampleRate
        assertTrue(isTrimDurationWithinCodecBoundaries(1_000L, durationMs, aacFrameDurationMs))
    }

    @Test
    fun mp3ExportDecodesBackToTheSameTone() = runBlocking {
        val sampleRate = 44_100
        val input = File(fixtureDirectory, "mp3_source.wav")
        writePcm16Wav(input, sineFixture(sampleRate, durationMs = 3_000L), sampleRate)

        val started = SystemClock.elapsedRealtime()
        val output = trimmer.trim(
            inputPath = input.absolutePath,
            startMs = 0L,
            endMs = 3_000L,
            outputFileName = "mp3_export",
            exportFormat = AudioExportFormat.MP3,
            bitrateKbps = 192,
        ).getOrThrow()
        Log.i(TAG, "MP3 export of 3 s took ${SystemClock.elapsedRealtime() - started} ms")

        val (mimeType, durationMs) = readEncodedAudio(File(output))
        assertEquals("audio/mpeg", mimeType)
        assertTrue("duration $durationMs", durationMs in 2_950L..3_100L)
        assertToneIsAbout440Hz(output, durationMs = 3_000L)
    }

    @Test
    fun flacExportKeepsEverySample() = runBlocking {
        val sampleRate = 44_100
        val samples = sineFixture(sampleRate, durationMs = 2_000L)
        val input = File(fixtureDirectory, "flac_source.wav")
        writePcm16Wav(input, samples, sampleRate)

        val output = trimmer.trim(
            inputPath = input.absolutePath,
            startMs = 0L,
            endMs = 2_000L,
            outputFileName = "flac_export",
            exportFormat = AudioExportFormat.FLAC,
        ).getOrThrow()

        // The framework's FLAC extractor hands back PCM on some releases and FLAC on others.
        val (mimeType, durationMs) = readEncodedAudio(File(output))
        assertTrue("mime $mimeType", mimeType == "audio/raw" || mimeType == "audio/flac")
        assertEquals(2_000L, durationMs)
        assertArrayEquals(samples, decodeToPcm(output, durationMs = 2_000L))
    }

    @Test
    fun oggExportCarriesTheLoopTagAndTheRightLength() = runBlocking {
        Assume.assumeTrue(AudioExportFormat.OGG in trimmer.availableExportFormats())
        val sampleRate = 44_100
        val input = File(fixtureDirectory, "ogg_source.wav")
        writePcm16Wav(input, sineFixture(sampleRate, durationMs = 2_000L), sampleRate)

        val output = trimmer.trim(
            inputPath = input.absolutePath,
            startMs = 0L,
            endMs = 2_000L,
            outputFileName = "ogg_export",
            exportFormat = AudioExportFormat.OGG,
            bitrateKbps = 128,
        ).getOrThrow()

        val (mimeType, durationMs) = readEncodedAudio(File(output))
        Log.i(TAG, "OGG export holds $mimeType")
        assertTrue("mime $mimeType", mimeType == "audio/vorbis" || mimeType == "audio/opus")
        assertTrue("duration $durationMs", durationMs in 1_950L..2_100L)
        assertTrue(
            File(output).readBytes().toString(Charsets.ISO_8859_1).contains("ANDROID_LOOP=true"),
        )
        assertToneIsAbout440Hz(output, durationMs = 2_000L)
    }

    @Test
    fun oggLosslessCutOfAnExportKeepsTheLoopTag() = runBlocking {
        Assume.assumeTrue(AudioExportFormat.OGG in trimmer.availableExportFormats())
        val sampleRate = 44_100
        val wav = File(fixtureDirectory, "ogg_cut_source.wav")
        writePcm16Wav(wav, sineFixture(sampleRate, durationMs = 3_000L), sampleRate)
        val source = trimmer.trim(
            inputPath = wav.absolutePath,
            startMs = 0L,
            endMs = 3_000L,
            outputFileName = "ogg_cut_source",
            exportFormat = AudioExportFormat.OGG,
        ).getOrThrow()

        val output = trimmer.trim(
            inputPath = source,
            startMs = 1_000L,
            endMs = 2_000L,
            outputFileName = "ogg_cut",
            losslessCut = true,
        ).getOrThrow()

        assertLoopTaggedCut(output)
    }

    @Test
    fun vorbisLosslessCutWorksOnADeviceWithNoVorbisEncoder() = runBlocking {
        val input = assetFixture("tone_vorbis_3s.ogg")

        val output = trimmer.trim(
            inputPath = input.absolutePath,
            startMs = 1_000L,
            endMs = 2_000L,
            outputFileName = "vorbis_cut",
            losslessCut = true,
        ).getOrThrow()

        assertEquals("audio/vorbis", readEncodedAudio(File(output)).first)
        assertLoopTaggedCut(output)
        val cutPackets = oggAudioPackets(output)
        val sourcePackets = oggAudioPackets(input.absolutePath).map { it.toList() }.toSet()
        assertTrue("the cut holds no audio", cutPackets.isNotEmpty())
        assertTrue(
            "a cut packet isn't one of the source's",
            cutPackets.all { it.toList() in sourcePackets },
        )
    }

    private fun oggAudioPackets(path: String): List<ByteArray> = buildList {
        forEachOggAudioPacket(path) { packet, _ ->
            add(packet)
            true
        }
    }

    /** Real music carries ID3 tags or Vorbis comments, and Media3 passes them on to the muxer. */
    @Test
    fun taggedFilesExportToEveryFormat() = runBlocking {
        val failures = mutableListOf<String>()
        for (asset in listOf("tone_tagged_3s.mp3", "tone_vorbis_3s.ogg")) {
            val input = assetFixture(asset)
            for (format in trimmer.availableExportFormats()) {
                val result = runCatching {
                    val output = trimmer.trim(
                        inputPath = input.absolutePath,
                        startMs = 500L,
                        endMs = 2_500L,
                        outputFileName = "tagged_${input.extension}_${format.extension}",
                        exportFormat = format,
                    ).getOrThrow()
                    assertToneIsAbout440Hz(output, durationMs = 1_500L)
                    readEncodedAudio(File(output)).second
                }
                Log.i(TAG, "Tagged $asset to $format: $result")
                result.exceptionOrNull()?.let { failures += "$asset to $format: ${it.message}" }
            }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    @Test
    fun taggedMp3LosslessCutKeepsTheTone() = runBlocking {
        val input = assetFixture("tone_tagged_3s.mp3")

        val output = trimmer.trim(
            inputPath = input.absolutePath,
            startMs = 1_000L,
            endMs = 2_000L,
            outputFileName = "tagged_mp3_cut",
            losslessCut = true,
        ).getOrThrow()

        val durationMs = readEncodedAudio(File(output)).second
        assertTrue("duration $durationMs", durationMs in 900L..1_200L)
        assertToneIsAbout440Hz(output, durationMs = 900L)
    }

    /**
     * A cut holds exactly the frames asked for, and they're the frames a decode of the whole file has at
     * that spot. A chirp changes pitch all the way through, so a cut that starts in the wrong place can't pass.
     */
    @Test
    fun aCutIsExactlyTheStretchAskedFor() = runBlocking {
        val inputs = CHIRP_ASSETS.map(::assetFixture).toMutableList()
        val wav = File(fixtureDirectory, "chirp_3s.wav")
        decodeWhole(inputs.first(), "whole_for_wav").copyTo(wav)
        val flac = trimmer.trim(
            inputPath = wav.absolutePath,
            startMs = 0L,
            endMs = 3_000L,
            outputFileName = "chirp_flac_input",
            exportFormat = AudioExportFormat.FLAC,
        ).getOrThrow()
        inputs += wav
        inputs += File(fixtureDirectory, "chirp_3s.flac").also { File(flac).copyTo(it) }

        val failures = mutableListOf<String>()
        for (input in inputs) {
            val whole = decodeWhole(input, "whole_${input.extension}")
            val wholeSamples = readPcm16Wav(whole)
            val sampleRate = readAudioFormat(whole).getInteger(MediaFormat.KEY_SAMPLE_RATE)
            for ((startMs, endMs) in CUT_RANGES) {
                val began = SystemClock.elapsedRealtime()
                val result = runCatching {
                    val cut = readPcm16Wav(
                        File(
                            trimmer.trim(
                                inputPath = input.absolutePath,
                                startMs = startMs,
                                endMs = endMs,
                                outputFileName = "cut_${input.extension}_$startMs",
                                exportFormat = AudioExportFormat.WAV,
                            ).getOrThrow(),
                        ),
                    )
                    val offset = (startMs * sampleRate / 1_000L).toInt()
                    val expected = minOf((endMs - startMs) * sampleRate / 1_000L, (wholeSamples.size - offset).toLong())
                    assertEquals("frames", expected.toInt(), cut.size)
                    // WebM only stores times to the millisecond, so a cut from the middle can start that far off.
                    val shiftLimit = if (input.extension == "webm") sampleRate / 1_000 else 0
                    val (shift, worst) = (-shiftLimit..shiftLimit)
                        .filter { offset + it >= 0 && offset + it + cut.size <= wholeSamples.size }
                        .map { shift -> shift to cut.indices.maxOf { abs(cut[it] - wholeSamples[offset + shift + it]) } }
                        .minBy { it.second }
                    // An AAC decoder started mid-stream doesn't round quite like one that ran from the top.
                    val differenceLimit = if (input.extension == "m4a") AAC_RESTART_DIFFERENCE else 0
                    assertTrue(
                        "difference from the whole decode is $worst, $shift frames off",
                        worst <= differenceLimit,
                    )
                    "$shift frames off, difference $worst"
                }
                Log.i(
                    TAG,
                    "Cut ${input.name} $startMs..$endMs at $sampleRate Hz in " +
                        "${SystemClock.elapsedRealtime() - began} ms: $result",
                )
                result.exceptionOrNull()?.let { failures += "${input.name} $startMs..$endMs: ${it.message}" }
            }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    @Test
    fun fadesOnACompressedCutLandOnTheCutsOwnEnds() = runBlocking {
        for (asset in listOf("chirp_3s.mp3", "chirp_3s.m4a")) {
            val input = assetFixture(asset)
            val output = File(
                trimmer.trim(
                    inputPath = input.absolutePath,
                    startMs = 500L,
                    endMs = 2_500L,
                    outputFileName = "faded_${input.extension}",
                    fadeInMs = 200L,
                    fadeOutMs = 200L,
                    fadeCurve = AudioFadeCurve.LINEAR,
                    exportFormat = AudioExportFormat.WAV,
                ).getOrThrow(),
            )
            val samples = readPcm16Wav(output)
            val sampleRate = readAudioFormat(output).getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val edge = sampleRate / 200
            assertEquals(asset, sampleRate * 2, samples.size)
            val opening = (0 until edge).maxOf { abs(samples[it].toInt()) }
            val closing = (samples.size - edge until samples.size).maxOf { abs(samples[it].toInt()) }
            val middle = (sampleRate / 2 until sampleRate * 3 / 2).maxOf { abs(samples[it].toInt()) }
            Log.i(TAG, "Faded $asset opens at $opening, closes at $closing, plays at $middle")
            assertTrue("$asset opens at $opening", opening < middle / 20)
            assertTrue("$asset closes at $closing", closing < middle / 20)
            assertTrue("$asset plays at $middle", middle > TONE_FLOOR)
        }
    }

    @Test
    fun speedOnACompressedCutKeepsAnExactLength() = runBlocking {
        for (asset in listOf("chirp_3s.mp3", "chirp_3s.ogg")) {
            val input = assetFixture(asset)
            val output = File(
                trimmer.trim(
                    inputPath = input.absolutePath,
                    startMs = 500L,
                    endMs = 2_500L,
                    outputFileName = "fast_${input.extension}",
                    playbackSpeed = 2f,
                    exportFormat = AudioExportFormat.WAV,
                ).getOrThrow(),
            )
            val samples = readPcm16Wav(output)
            val sampleRate = readAudioFormat(output).getInteger(MediaFormat.KEY_SAMPLE_RATE)
            assertEquals(asset, sampleRate, samples.size)
            val loudest = samples.maxOf { abs(it.toInt()) }
            assertTrue("$asset plays at $loudest", loudest > TONE_FLOOR)
        }
    }

    private suspend fun decodeWhole(input: File, name: String): File = File(
        trimmer.trim(
            inputPath = input.absolutePath,
            startMs = 0L,
            endMs = 3_000L,
            outputFileName = name,
            exportFormat = AudioExportFormat.WAV,
        ).getOrThrow(),
    )

    private fun assetFixture(name: String): File {
        val fixture = File(fixtureDirectory, name)
        fixture.parentFile?.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { source ->
            fixture.outputStream().use { destination -> source.copyTo(destination) }
        }
        return fixture
    }

    private suspend fun assertLoopTaggedCut(output: String) {
        val durationMs = readEncodedAudio(File(output)).second
        Log.i(TAG, "Lossless OGG cut of ${File(output).name} runs $durationMs ms")
        assertTrue("duration $durationMs", durationMs in 900L..1_200L)
        assertTrue(File(output).readBytes().toString(Charsets.ISO_8859_1).contains("ANDROID_LOOP=true"))
        assertToneIsAbout440Hz(output, durationMs = 900L)
    }

    @Test
    fun opusExportResamplesInputTheEncoderCannotTake() = runBlocking {
        Assume.assumeTrue(AudioExportFormat.OPUS in trimmer.availableExportFormats())
        val sampleRate = 44_100
        val input = File(fixtureDirectory, "opus_source.wav")
        writePcm16Wav(input, sineFixture(sampleRate, durationMs = 2_000L), sampleRate)

        val output = trimmer.trim(
            inputPath = input.absolutePath,
            startMs = 0L,
            endMs = 2_000L,
            outputFileName = "opus_export",
            exportFormat = AudioExportFormat.OPUS,
            bitrateKbps = 96,
        ).getOrThrow()

        val (mimeType, durationMs) = readEncodedAudio(File(output))
        assertEquals("audio/opus", mimeType)
        assertEquals(48_000, readAudioFormat(File(output)).getInteger(MediaFormat.KEY_SAMPLE_RATE))
        assertTrue("duration $durationMs", durationMs in 1_950L..2_100L)
        assertToneIsAbout440Hz(output, durationMs = 2_000L)
    }

    @Test
    fun mp3LosslessCutKeepsTheToneAndTheWindow() = runBlocking {
        val sampleRate = 44_100
        val wav = File(fixtureDirectory, "cut_source.wav")
        writePcm16Wav(wav, sineFixture(sampleRate, durationMs = 4_000L), sampleRate)
        val mp3 = File(fixtureDirectory, "cut_source.mp3")
        encodeWavToMp3(wav, mp3, bitrateKbps = 128)

        val output = trimmer.trim(
            inputPath = mp3.absolutePath,
            startMs = 1_000L,
            endMs = 3_000L,
            outputFileName = "mp3_cut",
            exportFormat = AudioExportFormat.MP3,
            losslessCut = true,
        ).getOrThrow()

        val (mimeType, durationMs) = readEncodedAudio(File(output))
        assertEquals("audio/mpeg", mimeType)
        assertTrue("duration $durationMs", durationMs in 1_900L..2_100L)
        assertToneIsAbout440Hz(output, durationMs = 2_000L)
    }

    @Test
    fun mp3LosslessCutAcrossMixedBitratesGetsAnAccurateLength() = runBlocking {
        val sampleRate = 44_100
        val wav = File(fixtureDirectory, "mixed_source.wav")
        writePcm16Wav(wav, sineFixture(sampleRate, durationMs = 3_000L), sampleRate)
        val low = File(fixtureDirectory, "mixed_low.mp3")
        val high = File(fixtureDirectory, "mixed_high.mp3")
        encodeWavToMp3(wav, low, bitrateKbps = 96)
        encodeWavToMp3(wav, high, bitrateKbps = 320)
        val mixed = File(fixtureDirectory, "mixed_source.mp3")
        mixed.writeBytes(low.readBytes() + high.readBytes())

        val output = trimmer.trim(
            inputPath = mixed.absolutePath,
            startMs = 2_000L,
            endMs = 4_000L,
            outputFileName = "mp3_mixed_cut",
            exportFormat = AudioExportFormat.MP3,
            losslessCut = true,
        ).getOrThrow()

        // Without the seek header the extractor would judge length from the first frame's bitrate.
        val (_, durationMs) = readEncodedAudio(File(output))
        assertTrue("duration $durationMs", durationMs in 1_900L..2_100L)
    }

    @Test
    fun flacLosslessCutKeepsExactSamplesAtFrameBoundaries() = runBlocking {
        val sampleRate = 44_100
        val samples = sineFixture(sampleRate, durationMs = 3_000L)
        val wav = File(fixtureDirectory, "flac_cut_source.wav")
        writePcm16Wav(wav, samples, sampleRate)
        val flac = File(fixtureDirectory, "flac_cut_source.flac")
        encodeWavToFlac(wav, flac)

        val output = trimmer.trim(
            inputPath = flac.absolutePath,
            startMs = 1_000L,
            endMs = 2_000L,
            outputFileName = "flac_cut",
            exportFormat = AudioExportFormat.FLAC,
            losslessCut = true,
        ).getOrThrow()

        val frame = 4_096
        val firstFrame = sampleRate * 1 / frame
        val lastFrame = (sampleRate * 2 - 1) / frame
        val expected = samples.copyOfRange(firstFrame * frame, (lastFrame + 1) * frame)
        val cutDurationMs = expected.size * 1_000L / sampleRate
        assertArrayEquals(expected, decodeToPcm(output, durationMs = cutDurationMs + 1L))
    }

    @Test
    fun exportChipsAlwaysOfferTheFormatsAuraEncodesItself() {
        val formats = trimmer.availableExportFormats()
        Log.i(TAG, "Export formats on this device: $formats")

        assertTrue(AudioExportFormat.MP3 in formats)
        assertTrue(AudioExportFormat.FLAC in formats)
        assertTrue(AudioExportFormat.WAV in formats)
    }

    private suspend fun decodeToPcm(path: String, durationMs: Long): ShortArray {
        val decoded = trimmer.trim(
            inputPath = path,
            startMs = 0L,
            endMs = durationMs,
            outputFileName = "decoded_${File(path).nameWithoutExtension}",
            exportFormat = AudioExportFormat.WAV,
        ).getOrThrow()
        return readPcm16Wav(File(decoded))
    }

    private suspend fun assertToneIsAbout440Hz(path: String, durationMs: Long) {
        val decoded = trimmer.trim(
            inputPath = path,
            startMs = 0L,
            endMs = durationMs,
            outputFileName = "decoded_${File(path).nameWithoutExtension}",
            exportFormat = AudioExportFormat.WAV,
        ).getOrThrow()
        val samples = readPcm16Wav(File(decoded))
        val sampleRate = readAudioFormat(File(decoded)).getInteger(MediaFormat.KEY_SAMPLE_RATE)
        // A lossless MP3 cut can open with a few quiet frames, since the first ones it keeps lean on
        // bits from frames it dropped. The tone is measured where it plays, and has to fill the window.
        val firstLoud = samples.indexOfFirst { abs(it.toInt()) > TONE_FLOOR }
        val lastLoud = samples.indexOfLast { abs(it.toInt()) > TONE_FLOOR }
        assertTrue("no tone at all", firstLoud in 0 until lastLoud)
        val toneMs = (lastLoud - firstLoud) * 1_000L / sampleRate
        assertTrue("only $toneMs ms of tone in $durationMs ms", toneMs >= durationMs * 4 / 5)
        val zeroCrossings = (firstLoud + 1..lastLoud).count { index ->
            val first = samples[index - 1]
            val second = samples[index]
            first < 0 && second >= 0 || first >= 0 && second < 0
        }
        val frequency = zeroCrossings * 500f / toneMs
        assertTrue("detected $frequency Hz", frequency in 420f..460f)
    }

    private fun sineFixture(sampleRate: Int, durationMs: Long): ShortArray {
        val sampleCount = (sampleRate * durationMs / 1_000L).toInt()
        return ShortArray(sampleCount) { index ->
            (sin(2.0 * PI * 440.0 * index / sampleRate) * 12_000.0).roundToInt().toShort()
        }
    }

    private fun writePcm16Wav(file: File, samples: ShortArray, sampleRate: Int) {
        file.parentFile?.mkdirs()
        val dataSize = samples.size * Short.SIZE_BYTES
        val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray(Charsets.US_ASCII))
        bytes.putInt(36 + dataSize)
        bytes.put("WAVE".toByteArray(Charsets.US_ASCII))
        bytes.put("fmt ".toByteArray(Charsets.US_ASCII))
        bytes.putInt(16)
        bytes.putShort(1.toShort())
        bytes.putShort(1.toShort())
        bytes.putInt(sampleRate)
        bytes.putInt(sampleRate * Short.SIZE_BYTES)
        bytes.putShort(Short.SIZE_BYTES.toShort())
        bytes.putShort(16.toShort())
        bytes.put("data".toByteArray(Charsets.US_ASCII))
        bytes.putInt(dataSize)
        samples.forEach { sample -> bytes.putShort(sample) }
        file.writeBytes(bytes.array())
    }

    private fun readPcm16Wav(file: File): ShortArray {
        val bytes = file.readBytes()
        require(bytes.size >= 44 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF")
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkName = bytes.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
            require(chunkSize >= 0 && offset + 8 + chunkSize <= bytes.size)
            if (chunkName == "data") {
                val data = ByteBuffer.wrap(bytes, offset + 8, chunkSize).order(ByteOrder.LITTLE_ENDIAN)
                return ShortArray(chunkSize / Short.SIZE_BYTES) { data.getShort() }
            }
            offset += 8 + chunkSize + (chunkSize and 1)
        }
        error("WAV data chunk was not found")
    }

    private fun readEncodedAudio(file: File): Pair<String?, Long> {
        val format = readAudioFormat(file)
        return format.getString(MediaFormat.KEY_MIME) to format.getLong(MediaFormat.KEY_DURATION) / 1_000L
    }

    private fun readAudioFormat(file: File): MediaFormat {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount)
                .map(extractor::getTrackFormat)
                .first { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        } finally {
            extractor.release()
        }
    }

    private companion object {
        const val TAG = "AudioTrimmerTest"
        const val TONE_FLOOR = 500

        /** Out of 32,767. The S25 measured 13 at worst, and a wrong start measures in the tens of thousands. */
        const val AAC_RESTART_DIFFERENCE = 32
        val CHIRP_ASSETS = listOf("chirp_3s.m4a", "chirp_3s.mp3", "chirp_3s.ogg", "chirp_3s.opus", "chirp_3s.webm")
        val CUT_RANGES = listOf(500L to 2_500L, 1_000L to 2_000L, 0L to 2_000L, 1_000L to 3_000L, 777L to 1_234L)
    }
}
