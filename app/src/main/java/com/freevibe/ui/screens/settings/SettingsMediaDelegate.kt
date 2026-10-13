package com.freevibe.ui.screens.settings

import android.content.Context
import com.freevibe.data.legal.ProviderSetup
import com.freevibe.data.legal.isProviderAvailableInCurrentArtifact
import com.freevibe.data.local.DEFAULT_REDDIT_VIDEO_SUBREDDITS
import com.freevibe.data.local.DEFAULT_REDDIT_WALLPAPER_SUBREDDITS
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.service.SoundProfileWorker
import com.freevibe.service.WallpaperPackWorker
import com.freevibe.service.WallpaperStyleLearningProfile
import com.freevibe.service.WallpaperClockOverlayMode
import com.freevibe.service.WallpaperClockOverlayPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Owns sound, video, provider, and generated-content settings. */
internal class SettingsMediaDelegate(
    private val context: Context,
    private val prefs: PreferencesManager,
    private val scope: CoroutineScope,
) {
    private val sharing = SharingStarted.WhileSubscribed(5000)

    val autoPreview = prefs.autoPreviewSounds.stateIn(scope, sharing, true)
    val gridColumns = prefs.wallpaperGridColumns.stateIn(scope, sharing, 2)
    val previewVolume = prefs.soundPreviewVolume.stateIn(scope, sharing, 0.7f)
    val soundProfilesEnabled = prefs.soundProfilesEnabled.stateIn(scope, sharing, false)
    val liveWallpaperDimEnabled = prefs.liveWallpaperDimEnabled.stateIn(scope, sharing, false)
    val liveWallpaperColorsEnabled = prefs.liveWallpaperColorsEnabled.stateIn(scope, sharing, true)
    val wallpaperClockOverlayEnabled = prefs.wallpaperClockOverlayEnabled.stateIn(scope, sharing, false)
    val wallpaperClockOverlayMode = prefs.wallpaperClockOverlayMode.stateIn(
        scope,
        sharing,
        WallpaperClockOverlayMode.TIME_AND_DATE.preferenceValue,
    )
    val wallpaperClockOverlayPosition = prefs.wallpaperClockOverlayPosition.stateIn(
        scope,
        sharing,
        WallpaperClockOverlayPosition.BOTTOM_RIGHT.preferenceValue,
    )
    val soundProfilesJson = prefs.soundProfilesJson.stateIn(scope, sharing, "")
    val wallpaperPackEnabled = prefs.wallpaperPackEnabled.stateIn(scope, sharing, false)
    val wallpaperPackJson = prefs.wallpaperPackJson.stateIn(scope, sharing, "")
    val redditSubs = prefs.redditSubreddits.stateIn(scope, sharing, DEFAULT_REDDIT_WALLPAPER_SUBREDDITS)
    val redditVideoSubs = prefs.redditVideoSubreddits.stateIn(scope, sharing, DEFAULT_REDDIT_VIDEO_SUBREDDITS)
    val redditProviderEnabled = prefs.redditProviderEnabled.stateIn(scope, sharing, true)
    val preferredRes = prefs.preferredResolution.stateIn(scope, sharing, "")
    val userStyles = prefs.userStyles.stateIn(scope, sharing, "")
    val wallpaperStyleLearningSignalCount = prefs.wallpaperStyleLearningJson
        .map { WallpaperStyleLearningProfile.parse(it).signalCount }
        .stateIn(scope, sharing, 0)
    val ytRingtonesQuery = prefs.ytSoundQueryRingtones.stateIn(
        scope,
        sharing,
        PreferencesManager.defaultRingtoneQuery(),
    )
    val ytNotificationsQuery = prefs.ytSoundQueryNotifications.stateIn(
        scope,
        sharing,
        PreferencesManager.defaultNotificationQuery(),
    )
    val ytAlarmsQuery = prefs.ytSoundQueryAlarms.stateIn(
        scope,
        sharing,
        PreferencesManager.defaultAlarmQuery(),
    )
    val ytBlockedWords = prefs.ytSoundBlockedWords.stateIn(
        scope,
        sharing,
        "compilation,mix,playlist,ranked,tier list,reaction,review,tutorial,how to,podcast,interview,live stream,part,episode",
    )
    val youtubeProviderEnabled = prefs.youtubeProviderEnabled.stateIn(
        scope,
        sharing,
        isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE),
    )
    val youtubePoTokenProviderUrl = prefs.youtubePoTokenProviderUrl.stateIn(scope, sharing, "")
    val videoFpsLimit = prefs.videoFpsLimit.stateIn(scope, sharing, 30)
    val videoPlaybackSpeed = prefs.videoPlaybackSpeed.stateIn(scope, sharing, 1f)
    val videoFpsOverlayEnabled = prefs.videoFpsOverlayEnabled.stateIn(scope, sharing, false)
    val videoAutoBatterySaver = prefs.videoAutoBatterySaver.stateIn(scope, sharing, true)
    val wallhavenApiKey = prefs.wallhavenApiKey.stateIn(scope, sharing, "")
    val pexelsApiKey = prefs.pexelsApiKey.stateIn(scope, sharing, "")
    val pixabayApiKey = prefs.pixabayApiKey.stateIn(scope, sharing, "")
    val generatedWallpaperProviderKey = prefs.generatedWallpaperProviderKey.stateIn(scope, sharing, "")
    val providerCredentialStorageUnavailable = prefs.providerCredentialStorageUnavailable
    val providerCredentialReentryRequired = prefs.providerCredentialReentryRequired
    val providerCredentialReentryKeys = prefs.providerCredentialReentryKeys
    val generatedContentProviderEnabled = prefs.generatedContentProviderEnabled.stateIn(
        scope,
        sharing,
        PreferencesManager.DEFAULT_GENERATED_CONTENT_PROVIDER_ENABLED,
    )
    val generatedContentDisclosureAccepted = prefs.generatedContentDisclosureAccepted.stateIn(scope, sharing, false)
    val wallhavenProviderEnabled = prefs.wallhavenProviderEnabled.stateIn(scope, sharing, true)
    val bingProviderEnabled = prefs.bingProviderEnabled.stateIn(scope, sharing, true)
    val pexelsProviderEnabled = prefs.pexelsProviderEnabled.stateIn(scope, sharing, true)
    val pixabayProviderEnabled = prefs.pixabayProviderEnabled.stateIn(scope, sharing, true)
    val providerSetup = prefs.providerSetup.stateIn(scope, sharing, ProviderSetup())

    fun setYtRingtonesQuery(query: String) = scope.launch { prefs.setYtSoundQueryRingtones(query) }
    fun setYtNotificationsQuery(query: String) = scope.launch { prefs.setYtSoundQueryNotifications(query) }
    fun setYtAlarmsQuery(query: String) = scope.launch { prefs.setYtSoundQueryAlarms(query) }
    fun setYtBlockedWords(words: String) = scope.launch { prefs.setYtSoundBlockedWords(words) }
    fun setYoutubeProviderEnabled(enabled: Boolean) = scope.launch { prefs.setYoutubeProviderEnabled(enabled) }
    fun setYoutubePoTokenProviderUrl(url: String) = scope.launch { prefs.setYoutubePoTokenProviderUrl(url) }

    fun setAutoPreview(enabled: Boolean) = scope.launch { prefs.setAutoPreview(enabled) }
    fun setGridColumns(columns: Int) = scope.launch { prefs.setGridColumns(columns) }
    fun setPreviewVolume(volume: Float) = scope.launch { prefs.setPreviewVolume(volume) }

    fun setSoundProfilesEnabled(enabled: Boolean) = scope.launch {
        prefs.setSoundProfilesEnabled(enabled)
        if (enabled) SoundProfileWorker.schedule(context) else SoundProfileWorker.cancel(context)
    }

    fun setSoundProfilesJson(json: String) = scope.launch {
        prefs.setSoundProfilesJson(json)
        prefs.setSoundProfileLastAppliedId("")
    }

    fun setWallpaperPackEnabled(enabled: Boolean) = scope.launch {
        prefs.setWallpaperPackEnabled(enabled)
        if (enabled) WallpaperPackWorker.schedule(context) else WallpaperPackWorker.cancel(context)
    }

    fun setWallpaperPackJson(json: String) = scope.launch {
        prefs.setWallpaperPackJson(json)
        prefs.setWallpaperPackLastAppliedDaypart("")
    }

    fun setRedditSubs(subs: String) = scope.launch { prefs.setRedditSubreddits(subs) }
    fun setRedditVideoSubs(subs: String) = scope.launch { prefs.setRedditVideoSubreddits(subs) }
    fun setRedditProviderEnabled(enabled: Boolean) = scope.launch { prefs.setRedditProviderEnabled(enabled) }
    fun setPreferredRes(resolution: String) = scope.launch { prefs.setPreferredResolution(resolution) }
    fun setUserStyles(styles: String) = scope.launch { prefs.setUserStyles(styles) }
    fun resetWallpaperStyleLearning() = scope.launch { prefs.clearWallpaperStyleLearning() }
    fun setWallhavenKey(key: String) = scope.launch { prefs.setWallhavenKey(key) }
    fun setPexelsKey(key: String) = scope.launch { prefs.setPexelsKey(key) }
    fun setPixabayKey(key: String) = scope.launch { prefs.setPixabayKey(key) }
    fun setGeneratedWallpaperProviderKey(key: String) =
        scope.launch { prefs.setGeneratedWallpaperProviderKey(key) }
    fun retryProviderCredentials() = prefs.retryProviderCredentials()
    fun setWallhavenProviderEnabled(enabled: Boolean) = scope.launch { prefs.setWallhavenProviderEnabled(enabled) }
    fun setBingProviderEnabled(enabled: Boolean) = scope.launch { prefs.setBingProviderEnabled(enabled) }
    fun setPexelsProviderEnabled(enabled: Boolean) = scope.launch { prefs.setPexelsProviderEnabled(enabled) }
    fun setPixabayProviderEnabled(enabled: Boolean) = scope.launch { prefs.setPixabayProviderEnabled(enabled) }
    fun setVideoFpsLimit(fps: Int) = scope.launch { prefs.setVideoFpsLimit(fps) }
    fun setVideoPlaybackSpeed(speed: Float) = scope.launch { prefs.setVideoPlaybackSpeed(speed) }
    fun setVideoFpsOverlayEnabled(enabled: Boolean) = scope.launch { prefs.setVideoFpsOverlayEnabled(enabled) }
    fun setVideoAutoBatterySaver(enabled: Boolean) = scope.launch { prefs.setVideoAutoBatterySaver(enabled) }
    fun setLiveWallpaperDimEnabled(enabled: Boolean) = scope.launch { prefs.setLiveWallpaperDimEnabled(enabled) }
    fun setLiveWallpaperColorsEnabled(enabled: Boolean) = scope.launch { prefs.setLiveWallpaperColorsEnabled(enabled) }
    fun setWallpaperClockOverlayEnabled(enabled: Boolean) = scope.launch {
        prefs.setWallpaperClockOverlayEnabled(enabled)
    }
    fun setWallpaperClockOverlayMode(mode: String) = scope.launch {
        prefs.setWallpaperClockOverlayMode(mode)
    }
    fun setWallpaperClockOverlayPosition(position: String) = scope.launch {
        prefs.setWallpaperClockOverlayPosition(position)
    }
    fun setGeneratedContentProviderEnabled(enabled: Boolean) = scope.launch {
        prefs.setGeneratedContentProviderEnabled(enabled)
    }
    fun acceptGeneratedContentDisclosure() = scope.launch {
        prefs.setGeneratedContentDisclosureAccepted(true)
    }
    fun resetGeneratedContentDisclosure() = scope.launch {
        prefs.setGeneratedContentDisclosureAccepted(false)
    }
}
