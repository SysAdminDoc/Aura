package com.freevibe.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import com.freevibe.R
import com.freevibe.service.VideoPlaylist
import com.freevibe.service.VideoWallpaperStorage
import com.freevibe.util.displayMessage
import com.freevibe.util.rethrowIfCancelled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoPlaylistUiState(
    val playlist: VideoPlaylist = VideoPlaylist(),
    val active: Boolean = false,
    val working: Boolean = false,
)

/** The video playlist dialog: what it renders and every change to the saved list. */
internal class SettingsVideoPlaylistDelegate(
    private val context: Context,
    private val storage: VideoWallpaperStorage,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(VideoPlaylistUiState())
    val state: StateFlow<VideoPlaylistUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun refresh() {
        scope.launch {
            runCatching { storage.readPlaylist() to storage.isPlaylistActive() }
                .onSuccess { (playlist, active) -> _state.update { it.copy(playlist = playlist, active = active) } }
                .onFailure { error ->
                    error.rethrowIfCancelled()
                    Log.w(TAG, "Couldn't read the video playlist", error)
                }
        }
    }

    fun addClips(uris: List<Uri>) {
        if (uris.isEmpty()) return
        edit {
            uris.forEach { uri ->
                storage.addPlaylistClip(uri)
                    .onSuccess { playlist -> _state.update { it.copy(playlist = playlist) } }
                    .onFailure(::report)
            }
        }
    }

    fun setScale(index: Int, scaleMode: String) = change { storage.setPlaylistClipScale(index, scaleMode) }

    fun move(from: Int, to: Int) = change { storage.movePlaylistClip(from, to) }

    fun remove(index: Int) = change { storage.removePlaylistClip(index) }

    fun setShuffle(shuffle: Boolean) = change { storage.setPlaylistShuffle(shuffle) }

    fun setAdvance(advanceSeconds: Int) = change { storage.setPlaylistAdvance(advanceSeconds) }

    /** [onDone] runs once the list is the one the wallpaper will read. */
    fun activate(onDone: () -> Unit) {
        edit {
            storage.activatePlaylist()
                .onSuccess { playable ->
                    _state.update { it.copy(active = playable) }
                    if (playable) onDone()
                }
                .onFailure(::report)
        }
    }

    private fun change(request: suspend () -> Result<VideoPlaylist>) {
        edit {
            request()
                .onSuccess { playlist ->
                    _state.update { it.copy(playlist = playlist, active = it.active && playlist.clips.isNotEmpty()) }
                }
                .onFailure(::report)
        }
    }

    private fun edit(block: suspend () -> Unit) {
        scope.launch {
            _state.update { it.copy(working = true) }
            try {
                block()
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }

    private fun report(error: Throwable) {
        error.rethrowIfCancelled()
        Log.w(TAG, "Video playlist change failed", error)
        _messages.tryEmit(
            error.displayMessage(context)?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.settings_feedback_prepare_video_failed),
        )
    }

    private companion object {
        const val TAG = "SettingsVideoPlaylist"
    }
}
