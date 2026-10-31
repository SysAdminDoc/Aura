package com.freevibe.ui.screens.sounds

import android.content.Context
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound

internal fun Sound.youtubeVideoId(): String? =
    takeIf { source == ContentSource.YOUTUBE }
        ?.id
        ?.removePrefix("yt_")
        ?.takeIf { it.isNotBlank() && it != id }

internal fun matchesSoundIdentity(
    sound: Sound,
    id: String,
    source: ContentSource? = null,
    previewUrl: String? = null,
    downloadUrl: String? = null,
): Boolean {
    if (sound.id != id) return false
    if (source != null && sound.source != source) return false
    if (!previewUrl.isNullOrBlank() && sound.previewUrl != previewUrl) return false
    if (!downloadUrl.isNullOrBlank() && sound.downloadUrl != downloadUrl) return false
    return true
}

internal fun youtubeDisabledMessage(context: Context): String =
    context.getString(com.freevibe.R.string.video_wp_youtube_disabled)

internal fun categorizeSoundError(context: Context, error: Exception): String = when (error) {
    is java.net.UnknownHostException -> context.getString(com.freevibe.R.string.sound_error_no_internet)
    is java.net.SocketTimeoutException -> context.getString(com.freevibe.R.string.sound_error_timed_out)
    is java.net.ConnectException -> context.getString(com.freevibe.R.string.sound_error_connect_failed)
    else -> error.message ?: context.getString(com.freevibe.R.string.sound_error_generic)
}
