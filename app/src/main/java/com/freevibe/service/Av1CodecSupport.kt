package com.freevibe.service

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Av1CodecSupport @Inject constructor() {

    val hasHardwareAv1Decode: Boolean by lazy {
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder &&
                    info.isHardwareAccelerated &&
                    info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AV1, ignoreCase = true) }
            }
        } catch (_: Exception) {
            false
        }
    }

    private val MediaCodecInfo.isHardwareAccelerated: Boolean
        get() = try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                isHardwareAccelerated
            } else {
                !isSoftwareOnly
            }
        } catch (_: Exception) {
            false
        }

    fun preferredVideoMimeTypes(): List<String> = if (hasHardwareAv1Decode) {
        listOf(
            MediaFormat.MIMETYPE_VIDEO_AV1,
            MediaFormat.MIMETYPE_VIDEO_HEVC,
            MediaFormat.MIMETYPE_VIDEO_AVC,
        )
    } else {
        listOf(
            MediaFormat.MIMETYPE_VIDEO_HEVC,
            MediaFormat.MIMETYPE_VIDEO_AVC,
        )
    }
}
