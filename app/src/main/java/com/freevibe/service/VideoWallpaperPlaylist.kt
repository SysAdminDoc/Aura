package com.freevibe.service

import android.content.SharedPreferences
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

internal const val VIDEO_WALLPAPER_PLAYLIST_PREF = "video_playlist"
internal const val VIDEO_WALLPAPER_PLAYLIST_ACTIVE_PREF = "video_playlist_active"
internal const val MAX_VIDEO_PLAYLIST_CLIPS = 12
internal const val MAX_VIDEO_PLAYLIST_BYTES = 1024L * 1024L * 1024L
internal const val VIDEO_PLAYLIST_PRELOAD_US = 1_000_000L

/**
 * Seconds a clip stays on screen before the next one. Zero means it plays to its end. Kept in step
 * with the settings_video_playlist_advance_* strings.
 */
internal val VIDEO_PLAYLIST_ADVANCE_CHOICES = listOf(0, 30, 60, 300)

data class VideoPlaylistClip(
    /** What plays: the prepared copy for this clip's fit or fill. */
    val path: String,
    /** The saved source, which a new fit or fill is prepared from. */
    val originalPath: String,
    val name: String,
    val scaleMode: String,
)

data class VideoPlaylist(
    val clips: List<VideoPlaylistClip> = emptyList(),
    val shuffle: Boolean = false,
    val advanceSeconds: Int = 0,
)

internal fun encodeVideoPlaylist(playlist: VideoPlaylist): String {
    val clips = JSONArray()
    playlist.clips.forEach { clip ->
        clips.put(
            JSONObject()
                .put("path", clip.path)
                .put("originalPath", clip.originalPath)
                .put("name", clip.name)
                .put("scaleMode", clip.scaleMode),
        )
    }
    return JSONObject()
        .put("clips", clips)
        .put("shuffle", playlist.shuffle)
        .put("advanceSeconds", playlist.advanceSeconds)
        .toString()
}

internal fun decodeVideoPlaylist(json: String?): VideoPlaylist {
    if (json.isNullOrBlank()) return VideoPlaylist()
    return try {
        val root = JSONObject(json)
        val entries = root.optJSONArray("clips") ?: JSONArray()
        val clips = (0 until entries.length()).mapNotNull { index ->
            val entry = entries.optJSONObject(index) ?: return@mapNotNull null
            val path = entry.optString("path").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            VideoPlaylistClip(
                path = path,
                originalPath = entry.optString("originalPath").ifBlank { path },
                name = entry.optString("name").ifBlank { File(path).name },
                scaleMode = normalizeVideoWallpaperScaleMode(entry.optString("scaleMode")),
            )
        }.take(MAX_VIDEO_PLAYLIST_CLIPS)
        VideoPlaylist(
            clips = clips,
            shuffle = root.optBoolean("shuffle"),
            advanceSeconds = root.optInt("advanceSeconds").takeIf { it in VIDEO_PLAYLIST_ADVANCE_CHOICES } ?: 0,
        )
    } catch (_: JSONException) {
        VideoPlaylist()
    }
}

/**
 * The list the wallpaper should play right now, or null when it's showing a single video. A clip
 * whose prepared copy went missing falls back to its saved source, and one with neither is left out.
 */
internal fun readActiveVideoPlaylist(prefs: SharedPreferences): VideoPlaylist? {
    if (!prefs.getBoolean(VIDEO_WALLPAPER_PLAYLIST_ACTIVE_PREF, false)) return null
    val playlist = decodeVideoPlaylist(prefs.getString(VIDEO_WALLPAPER_PLAYLIST_PREF, null))
    val playable = playlist.clips.mapNotNull { clip ->
        when {
            File(clip.path).isFile -> clip
            File(clip.originalPath).isFile -> clip.copy(path = clip.originalPath)
            else -> null
        }
    }
    return playlist.copy(clips = playable).takeIf { playable.isNotEmpty() }
}

/** Tells two lists apart the way a single wallpaper's path does, so an edit restarts playback. */
internal fun videoPlaylistKey(playlist: VideoPlaylist): String = "playlist:" + encodeVideoPlaylist(playlist)

/** A timer moves the list by itself when one is set, so a clip then loops until it fires. */
internal fun videoPlaylistRepeatMode(playlist: VideoPlaylist): Int =
    if (playlist.clips.size > 1 && playlist.advanceSeconds == 0) {
        Player.REPEAT_MODE_ALL
    } else {
        Player.REPEAT_MODE_ONE
    }

internal fun nextVideoPlaylistIndex(current: Int, size: Int): Int =
    if (size <= 0) 0 else (current + 1) % size

/** Repeating one clip counts as no repeat to a player, so the end of the list has to wrap by hand. */
internal fun Player.moveToNextPlaylistClip() {
    if (hasNextMediaItem()) {
        seekToNextMediaItem()
    } else {
        seekToDefaultPosition(currentTimeline.getFirstWindowIndex(shuffleModeEnabled))
    }
}
