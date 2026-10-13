package com.freevibe.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.SCHEDULER_DAY_NIGHT_MODE_SINGLE
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.service.AgslShaderGallery
import com.freevibe.service.AutoBackupWorker
import com.freevibe.service.AutoWallpaperWorker
import com.freevibe.service.LocalWallpaperCatalog
import com.freevibe.service.RotationTriggerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Owns wallpaper rotation, scheduler, appearance, and backup preference jobs. */
internal class SettingsRotationDelegate(
    private val context: Context,
    private val prefs: PreferencesManager,
    private val collectionRepo: CollectionRepository,
    private val localWallpaperCatalog: LocalWallpaperCatalog,
    private val scope: CoroutineScope,
) {
    private val sharing = SharingStarted.WhileSubscribed(5000)

    val autoWpEnabled = prefs.autoWallpaperEnabled.stateIn(scope, sharing, false)
    val autoWpInterval = prefs.autoWallpaperInterval.stateIn(scope, sharing, 12L)
    val autoWpSource = prefs.autoWallpaperSource.stateIn(scope, sharing, "wallhaven")
    val autoWpTarget = prefs.autoWallpaperTarget.stateIn(scope, sharing, WallpaperTarget.BOTH.name)
    val localWallpaperFolderUri = prefs.localWallpaperFolderUri.stateIn(scope, sharing, "")
    val autoWpRequiresCharging = prefs.autoWallpaperRequiresCharging.stateIn(scope, sharing, false)
    val autoWpRequiresWiFi = prefs.autoWallpaperRequiresWiFiOnly.stateIn(scope, sharing, false)
    val autoWpRequiresIdle = prefs.autoWallpaperRequiresIdle.stateIn(scope, sharing, false)
    val autoWallpaperDarkenPercent = prefs.autoWallpaperDarkenPercent.stateIn(scope, sharing, 0)
    val autoWallpaperNightVariantEnabled = prefs.autoWallpaperNightVariantEnabled.stateIn(scope, sharing, false)
    val autoBackupEnabled = prefs.autoBackupEnabled.stateIn(scope, sharing, false)
    val autoBackupFolderUri = prefs.autoBackupFolderUri.stateIn(scope, sharing, "")
    val autoBackupIntervalHours = prefs.autoBackupIntervalHours.stateIn(scope, sharing, 24L)
    val autoBackupKeepCount = prefs.autoBackupKeepCount.stateIn(scope, sharing, 5)
    val rotateOnUnlock = prefs.rotateOnUnlock.stateIn(scope, sharing, false)
    val rotateOnScreenOff = prefs.rotateOnScreenOff.stateIn(scope, sharing, false)
    val avoidRecentRepeats = prefs.avoidRecentRepeats.stateIn(scope, sharing, false)
    val restartRotationOnManualApply = prefs.autoWallpaperRestartOnManual.stateIn(scope, sharing, true)
    val schedulerEnabled = prefs.schedulerEnabled.stateIn(scope, sharing, false)
    val schedulerInterval = prefs.schedulerIntervalMinutes.stateIn(scope, sharing, 360L)
    val schedulerSource = prefs.schedulerSource.stateIn(scope, sharing, "discover")
    val schedulerDaySource = prefs.schedulerDaySource.stateIn(scope, sharing, "")
    val schedulerNightSource = prefs.schedulerNightSource.stateIn(scope, sharing, "")
    val schedulerDayNightMode = prefs.schedulerDayNightMode.stateIn(scope, sharing, SCHEDULER_DAY_NIGHT_MODE_SINGLE)
    val schedulerDayStartHour = prefs.schedulerDayStartHour.stateIn(scope, sharing, 6)
    val schedulerNightStartHour = prefs.schedulerNightStartHour.stateIn(scope, sharing, 18)
    val schedulerHome = prefs.schedulerHomeEnabled.stateIn(scope, sharing, true)
    val schedulerLock = prefs.schedulerLockEnabled.stateIn(scope, sharing, true)
    val schedulerShuffle = prefs.schedulerShuffle.stateIn(scope, sharing, true)
    val weatherEffects = prefs.weatherEffectsEnabled.stateIn(scope, sharing, false)
    val adaptiveTint = prefs.adaptiveTintEnabled.stateIn(scope, sharing, false)
    val adaptiveTintIntensity = prefs.adaptiveTintIntensity.stateIn(scope, sharing, 0.3f)
    val reduceAnimations = prefs.reduceAnimations.stateIn(scope, sharing, false)
    val darkModeSwitch = prefs.darkModeAutoSwitch.stateIn(scope, sharing, false)
    val darkModeWallpaperId = prefs.darkModeWallpaperId.stateIn(scope, sharing, "")
    val lightModeWallpaperId = prefs.lightModeWallpaperId.stateIn(scope, sharing, "")
    val liveWallpaperShaderPreset = prefs.liveWallpaperShaderPreset.stateIn(scope, sharing, AgslShaderGallery.NONE_ID)
    val collections: StateFlow<List<WallpaperCollectionEntity>> = collectionRepo.getAll()
        .stateIn(scope, sharing, emptyList())
    val schedulerCollectionId = prefs.schedulerCollectionId.stateIn(scope, sharing, -1L)
    val localWallpaperFolders = localWallpaperCatalog.folders.stateIn(scope, sharing, emptyList())
    val localWallpaperItems = localWallpaperCatalog.items.stateIn(scope, sharing, emptyList())

    init {
        scope.launch(Dispatchers.IO) {
            localWallpaperCatalog.migrateLegacyFolder(prefs.localWallpaperFolderUri.first())
        }
    }

    fun setAutoWallpaper(enabled: Boolean) = scope.launch {
        prefs.setAutoWallpaperEnabled(enabled)
        if (enabled) {
            AutoWallpaperWorker.schedule(context, autoWpInterval.value * 60)
        } else {
            AutoWallpaperWorker.cancel(context)
        }
    }

    fun setAutoWpInterval(hours: Long) = scope.launch {
        prefs.setAutoWallpaperInterval(hours)
        if (autoWpEnabled.value) AutoWallpaperWorker.schedule(context, hours * 60)
    }

    fun setAutoWpSource(source: String) = scope.launch { prefs.setAutoWallpaperSource(source) }

    fun setAutoWpTarget(target: WallpaperTarget) = scope.launch { prefs.setAutoWallpaperTarget(target.name) }

    fun setLocalWallpaperFolderUri(uri: String) = scope.launch {
        val nextUri = uri.trim()
        val previousUri = prefs.localWallpaperFolderUri.first().trim()
        prefs.setLocalWallpaperFolderUri(nextUri)
        if (previousUri.isNotBlank() && previousUri != nextUri) {
            releasePersistedUriPermission(previousUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun addLocalWallpaperFolder(uri: String, makePrimary: Boolean = true) = scope.launch {
        val nextUri = uri.trim()
        if (nextUri.isBlank()) return@launch
        if (makePrimary) prefs.setLocalWallpaperFolderUri(nextUri)
        localWallpaperCatalog.addFolder(nextUri)
    }

    fun clearLocalWallpaperFolderUri() = scope.launch {
        val uri = prefs.localWallpaperFolderUri.first().trim()
        if (uri.isNotBlank()) releasePersistedUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uri.isNotBlank()) localWallpaperCatalog.removeFolder(uri)
        prefs.setLocalWallpaperFolderUri("")
    }

    fun removeLocalWallpaperFolder(uri: String) = scope.launch {
        val nextUri = uri.trim()
        localWallpaperCatalog.removeFolder(nextUri)
        if (prefs.localWallpaperFolderUri.first().trim() == nextUri) {
            prefs.setLocalWallpaperFolderUri("")
        }
    }

    fun rescanLocalWallpaperFolder(uri: String) = scope.launch {
        localWallpaperCatalog.rescanFolder(uri)
    }

    fun rescanAllLocalWallpaperFolders() = scope.launch {
        localWallpaperCatalog.rescanAll()
    }

    fun setLocalWallpaperFolderTarget(uri: String, target: WallpaperTarget) = scope.launch {
        localWallpaperCatalog.updateFolderTarget(uri, target)
    }

    fun updateLocalWallpaperTags(documentUri: String, tags: String) = scope.launch {
        localWallpaperCatalog.updateTags(documentUri, tags)
    }

    fun setAutoWallpaperRequiresCharging(value: Boolean) = scope.launch {
        prefs.setAutoWallpaperRequiresCharging(value)
        if (autoWpEnabled.value) AutoWallpaperWorker.schedule(context, autoWpInterval.value * 60)
    }

    fun setAutoWallpaperRequiresWiFiOnly(value: Boolean) = scope.launch {
        prefs.setAutoWallpaperRequiresWiFiOnly(value)
        if (autoWpEnabled.value) AutoWallpaperWorker.schedule(context, autoWpInterval.value * 60)
    }

    fun setRotateOnUnlock(value: Boolean) = scope.launch {
        prefs.setRotateOnUnlock(value)
        RotationTriggerService.reconcile(context, unlock = value, screenOff = rotateOnScreenOff.value)
    }

    fun setRotateOnScreenOff(value: Boolean) = scope.launch {
        prefs.setRotateOnScreenOff(value)
        RotationTriggerService.reconcile(context, unlock = rotateOnUnlock.value, screenOff = value)
    }

    fun setAvoidRecentRepeats(value: Boolean) = scope.launch {
        prefs.setAvoidRecentRepeats(value)
        if (!value) prefs.clearRecentRotationIds()
    }

    fun setRestartRotationOnManualApply(value: Boolean) = scope.launch {
        prefs.setAutoWallpaperRestartOnManual(value)
    }

    fun setAutoWallpaperRequiresIdle(value: Boolean) = scope.launch {
        prefs.setAutoWallpaperRequiresIdle(value)
        if (autoWpEnabled.value) AutoWallpaperWorker.schedule(context, autoWpInterval.value * 60)
    }

    fun setAutoWallpaperDarkenPercent(percent: Int) = scope.launch {
        prefs.setAutoWallpaperDarkenPercent(percent)
    }

    fun setAutoWallpaperNightVariantEnabled(enabled: Boolean) = scope.launch {
        prefs.setAutoWallpaperNightVariantEnabled(enabled)
    }

    fun setAutoBackupEnabled(enabled: Boolean) = scope.launch {
        prefs.setAutoBackupEnabled(enabled)
        if (enabled) AutoBackupWorker.schedule(context) else AutoBackupWorker.cancel(context)
    }

    fun setAutoBackupFolderUri(uri: String) = scope.launch {
        val nextUri = uri.trim()
        val previousUri = prefs.autoBackupFolderUri.first().trim()
        prefs.setAutoBackupFolderUri(nextUri)
        if (previousUri.isNotBlank() && previousUri != nextUri) {
            releasePersistedUriPermission(
                previousUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        if (autoBackupEnabled.value) AutoBackupWorker.schedule(context)
    }

    fun clearAutoBackupFolderUri() = scope.launch {
        val uri = prefs.autoBackupFolderUri.first().trim()
        if (uri.isNotBlank()) {
            releasePersistedUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.setAutoBackupEnabled(false)
        prefs.setAutoBackupFolderUri("")
        AutoBackupWorker.cancel(context)
    }

    fun setAutoBackupIntervalHours(hours: Long) = scope.launch {
        prefs.setAutoBackupIntervalHours(hours.coerceAtLeast(1L))
        if (autoBackupEnabled.value) AutoBackupWorker.schedule(context)
    }

    fun setAutoBackupKeepCount(count: Int) = scope.launch {
        prefs.setAutoBackupKeepCount(count.coerceAtLeast(1))
        if (autoBackupEnabled.value) AutoBackupWorker.schedule(context)
    }

    fun setSchedulerEnabled(enabled: Boolean) = scope.launch {
        prefs.setSchedulerEnabled(enabled)
        if (enabled) AutoWallpaperWorker.schedule(context, schedulerInterval.value)
        else AutoWallpaperWorker.cancel(context)
    }

    fun setSchedulerInterval(minutes: Long) = scope.launch {
        prefs.setSchedulerInterval(minutes)
        if (schedulerEnabled.value) AutoWallpaperWorker.schedule(context, minutes)
    }

    fun setSchedulerSource(source: String) = setSchedulerSource(SchedulerSourceTarget.DEFAULT, source)

    fun setSchedulerSource(target: SchedulerSourceTarget, source: String) = scope.launch {
        when (target) {
            SchedulerSourceTarget.DEFAULT -> prefs.setSchedulerSource(source)
            SchedulerSourceTarget.DAY -> prefs.setSchedulerDaySource(source)
            SchedulerSourceTarget.NIGHT -> prefs.setSchedulerNightSource(source)
        }
        rescheduleSchedulerIfEnabled()
    }

    fun setSchedulerDayNightMode(mode: String) = scope.launch {
        prefs.setSchedulerDayNightMode(mode)
        rescheduleSchedulerIfEnabled()
    }

    fun setSchedulerDayStartHour(hour: Int) = scope.launch { prefs.setSchedulerDayStartHour(hour) }

    fun setSchedulerNightStartHour(hour: Int) = scope.launch { prefs.setSchedulerNightStartHour(hour) }

    fun setSchedulerCollection(
        id: Long,
        target: SchedulerSourceTarget = SchedulerSourceTarget.DEFAULT,
    ) = scope.launch {
        prefs.setSchedulerCollection(id)
        when (target) {
            SchedulerSourceTarget.DEFAULT -> prefs.setSchedulerSource("collection")
            SchedulerSourceTarget.DAY -> prefs.setSchedulerDaySource("collection")
            SchedulerSourceTarget.NIGHT -> prefs.setSchedulerNightSource("collection")
        }
        rescheduleSchedulerIfEnabled()
    }

    fun setSchedulerHome(enabled: Boolean) = scope.launch { prefs.setSchedulerHome(enabled) }
    fun setSchedulerLock(enabled: Boolean) = scope.launch { prefs.setSchedulerLock(enabled) }
    fun setSchedulerShuffle(shuffle: Boolean) = scope.launch { prefs.setSchedulerShuffle(shuffle) }

    private suspend fun rescheduleSchedulerIfEnabled() {
        if (prefs.schedulerEnabled.first()) {
            AutoWallpaperWorker.schedule(context, prefs.schedulerIntervalMinutes.first())
        }
    }

    fun setWeatherEffects(enabled: Boolean) = scope.launch { prefs.setWeatherEffectsEnabled(enabled) }
    fun isDailyWallpaperEnabled(): Boolean = prefs.isDailyWallpaperEnabled()
    fun weatherVfxEffect(): String = prefs.weatherVfxEffect()
    fun touchEffectStrength(): String = prefs.touchEffectStrength()
    fun setDailyWallpaperEnabled(enabled: Boolean) = prefs.setDailyWallpaperEnabled(enabled)
    fun setWeatherVfxEffect(effect: String) = prefs.setWeatherVfxEffect(effect)
    fun setTouchEffectStrength(strength: String) = prefs.setTouchEffectStrength(strength)
    fun setReduceAnimations(enabled: Boolean) = scope.launch { prefs.setReduceAnimations(enabled) }
    fun setAdaptiveTint(enabled: Boolean) = scope.launch { prefs.setAdaptiveTintEnabled(enabled) }
    fun setAdaptiveTintIntensity(intensity: Float) = scope.launch { prefs.setAdaptiveTintIntensity(intensity) }
    fun setDarkModeSwitch(enabled: Boolean) = scope.launch { prefs.setDarkModeAutoSwitch(enabled) }
    fun setDarkModeWallpaperId(id: String) = scope.launch { prefs.setDarkModeWallpaperId(id) }
    fun setLightModeWallpaperId(id: String) = scope.launch { prefs.setLightModeWallpaperId(id) }
    fun setLiveWallpaperShaderPreset(id: String) = scope.launch {
        prefs.setLiveWallpaperShaderPreset(AgslShaderGallery.sanitizeId(id))
    }

    private fun releasePersistedUriPermission(uriString: String, flags: Int) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), flags)
        }
    }
}
