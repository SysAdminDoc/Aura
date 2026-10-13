package com.freevibe.service

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.freevibe.data.model.Sound
import com.freevibe.data.model.stableKey
import com.google.common.util.concurrent.ListenableFuture
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var controllerFuture: ListenableFuture<MediaController>? = null
    @Volatile private var controller: MediaController? = null
    @Volatile private var stopped = false
    @Volatile private var connectionGeneration = 0
    @Volatile private var playbackRequestedAtMs = 0L

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentSoundId = MutableStateFlow<String?>(null)
    val currentSoundId: StateFlow<String?> = _currentSoundId.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying && com.freevibe.BuildConfig.DEBUG && playbackRequestedAtMs > 0L) {
                Log.d("SoundPlayback", "Playback started in ${SystemClock.elapsedRealtime() - playbackRequestedAtMs}ms")
                playbackRequestedAtMs = 0L
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Re-check the controller's CURRENT state: a queued event from the
            // previous item must not clear state that play() just set for a new one.
            if (playbackState == Player.STATE_ENDED &&
                controller?.playbackState == Player.STATE_ENDED
            ) {
                _isPlaying.value = false
                _currentSoundId.value = null
                _currentPosition.value = 0L
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            // Expired stream URL / network drop: without this the UI keeps showing
            // an indefinite "playing"/resolving state for the failed sound. Skip if a
            // newer play() has already re-prepared the player (stale queued event).
            if (controller?.playerError != null) {
                _isPlaying.value = false
                _currentSoundId.value = null
                _currentPosition.value = 0L
            }
        }
    }

    private fun ensureConnected(onReady: (MediaController) -> Unit) {
        controller?.let { if (it.isConnected) { onReady(it); return } }

        // Release old controller/future before creating new ones
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller?.removeListener(playerListener)
        controller = null

        val generation = ++connectionGeneration

        try {
            val token = SessionToken(
                context,
                ComponentName(context, AudioPlaybackService::class.java),
            )
            controllerFuture = MediaController.Builder(context, token).buildAsync().also { future ->
                future.addListener({
                    try {
                        if (stopped || generation != connectionGeneration) return@addListener
                        val mc = future.get()
                        controller = mc
                        mc.addListener(playerListener)
                        onReady(mc)
                    } catch (e: Exception) {
                        Log.w("SoundPlayback", "Media controller connect failed: ${e.javaClass.simpleName}")
                        _currentSoundId.value = null
                        _isPlaying.value = false
                    }
                }, androidx.core.content.ContextCompat.getMainExecutor(context))
            }
        } catch (e: Exception) {
            Log.w("SoundPlayback", "Media controller build failed: ${e.javaClass.simpleName}")
            _currentSoundId.value = null
            _isPlaying.value = false
        }
    }

    @OptIn(markerClass = [UnstableApi::class])
    fun play(sound: Sound, url: String, volume: Float = 1f) {
        stopped = false
        playbackRequestedAtMs = SystemClock.elapsedRealtime()
        // Set synchronously: on the first play the controller connects asynchronously,
        // and callers keying progress loops on currentSoundId would otherwise see null
        // and bail before playback starts.
        _currentSoundId.value = sound.stableKey()
        ensureConnected { mc ->
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaId(sound.stableKey())
                .setCustomCacheKey(sound.stableKey())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(sound.name)
                        .setArtist(sound.uploaderName.ifBlank { null })
                        .build()
                )
                .build()
            mc.stop()
            mc.clearMediaItems()
            mc.setMediaItem(mediaItem)
            mc.prepare()
            mc.volume = volume
            mc.play()
            _currentSoundId.value = sound.stableKey()
            _duration.value = 0L
            _currentPosition.value = 0L
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun resume() {
        controller?.play()
    }

    fun stop() {
        stopped = true
        controller?.apply {
            stop()
            clearMediaItems()
        }
        _isPlaying.value = false
        _currentSoundId.value = null
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun setVolume(volume: Float) {
        controller?.volume = volume
    }

    /**
     * Call from a coroutine polling loop to update position/duration state.
     */
    fun pollProgress() {
        val mc = controller ?: return
        if (mc.isConnected && mc.duration > 0) {
            _currentPosition.value = mc.currentPosition
            _duration.value = mc.duration
        }
    }

    fun release() {
        stopped = true
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        _isPlaying.value = false
        _currentSoundId.value = null
        _currentPosition.value = 0L
        _duration.value = 0L
    }
}
