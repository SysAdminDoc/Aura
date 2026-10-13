package com.freevibe.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.freevibe.data.legal.ProviderSetup
import com.freevibe.data.legal.isProviderAvailableInCurrentArtifact
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.service.Daypart
import com.freevibe.service.PickedFileGrants
import com.freevibe.service.SoundProfileWorker
import com.freevibe.service.parseProfiles
import com.freevibe.service.soundProfilesNeedWorker
import com.freevibe.service.WallpaperPack
import com.freevibe.service.WallpaperPackWorker
import com.freevibe.service.shouldSchedulePack
import com.freevibe.service.WallpaperStyleLearningProfile
import com.freevibe.service.WallpaperClockOverlayMode
import com.freevibe.service.WallpaperClockOverlayPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The name the document picker shows for a file, or null when its provider doesn't give one. */
private fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()?.takeIf { it.isNotBlank() }

/** Owns sound, video, provider, and generated-content settings. */
internal class SettingsMediaDelegate(
    private val context: Context,
    private val prefs: PreferencesManager,
    private val scope: CoroutineScope,
    private val pickedFileGrants: PickedFileGrants,
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
        syncSoundProfileWork()
    }

    fun setSoundProfilesJson(json: String) = scope.launch {
        prefs.setSoundProfilesJson(json)
        prefs.setSoundProfileLastAppliedId("")
        // An edit should be heard now, not at the next 15 minute tick.
        if (syncSoundProfileWork()) SoundProfileWorker.runNow(context)
    }

    /**
     * The worker runs only while the switch is on and a profile can change a sound. When it stops,
     * one last run puts back the sounds a profile had changed.
     */
    private suspend fun syncSoundProfileWork(): Boolean {
        val needed = soundProfilesNeedWorker(
            prefs.soundProfilesEnabled.first(),
            parseProfiles(prefs.soundProfilesJson.first()),
        )
        if (needed) {
            SoundProfileWorker.schedule(context)
        } else {
            SoundProfileWorker.cancel(context)
            SoundProfileWorker.runNow(context)
        }
        return needed
    }

    private fun applyPackSchedule(toggleOn: Boolean, packJson: String) {
        if (shouldSchedulePack(toggleOn, packJson)) WallpaperPackWorker.schedule(context) else WallpaperPackWorker.cancel(context)
    }

    fun setWallpaperPackEnabled(enabled: Boolean) = scope.launch {
        prefs.setWallpaperPackEnabled(enabled)
        applyPackSchedule(enabled, prefs.wallpaperPackJson.first())
    }

    /** One pack change at a time, each made on the pack as it is stored. */
    private val packChanges = Mutex()

    /**
     * Changes the stored pack. The editor passes the change and not a changed copy. Its copy can be
     * a save behind, and writing that back would return a replaced picture to the pack after the
     * read access kept for it was given up.
     */
    fun editWallpaperPack(edit: (WallpaperPack) -> WallpaperPack) = scope.launch { changePack(edit) }

    fun deleteWallpaperPack() = scope.launch { packChanges.withLock { savePack("") } }

    /**
     * Puts a picked wallpaper in the pack. This runs here and not in the editor, so closing the
     * editor right after a pick can't drop the wallpaper while its name is still being read.
     */
    fun setWallpaperPackSlot(daypart: Daypart, uri: Uri, unnamedLabel: String) = scope.launch {
        val label = withContext(Dispatchers.IO) { displayNameOf(context, uri) } ?: unnamedLabel
        changePack { it.withSlot(daypart, uri.toString(), label) }
    }

    private suspend fun changePack(edit: (WallpaperPack) -> WallpaperPack) = packChanges.withLock {
        savePack(edit(editablePack(prefs.wallpaperPackJson.first())).toEditorJson())
    }

    /**
     * Saves the pack, forgets what was last applied so the change shows now, and fits the schedule
     * to it. A picture the pack stopped using then gives up the read access that was kept for it.
     */
    private suspend fun savePack(json: String) {
        val before = prefs.wallpaperPackJson.first()
        prefs.setWallpaperPackJson(json)
        prefs.setWallpaperPackLastAppliedDaypart("")
        applyPackSchedule(prefs.wallpaperPackEnabled.first(), json)
        pickedFileGrants.releaseDroppedFromPack(before, json)
    }

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
