package com.freevibe.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.freevibe.data.model.COMMUNITY_GUIDELINES_VERSION
import com.freevibe.data.model.hasAcceptedCommunityGuidelinesVersion
import com.freevibe.service.VIDEO_AUTO_BATTERY_SAVER_PREF
import com.freevibe.service.VIDEO_FPS_LIMIT_PREF
import com.freevibe.service.VIDEO_FPS_OVERLAY_PREF
import com.freevibe.service.VIDEO_PLAYBACK_SPEED_PREF
import com.freevibe.service.VIDEO_PREFS_NAME
import com.freevibe.service.sanitizeVideoFpsLimit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("freevibe_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        fun defaultRingtoneQuery(): String =
            "Ringtones"

        fun defaultNotificationQuery(): String =
            "Notifications"

        fun defaultAlarmQuery(): String =
            "Alarms"

        fun defaultTopHitQueries(): List<String> = listOf(
            "Ringtones",
            "phone ringtone sound effect",
            "classic phone ringtones",
        )

        const val DEFAULT_GENERATED_CONTENT_PROVIDER_ENABLED = false
        const val DEFAULT_COMMUNITY_PROVIDER_ENABLED = false
    }

    private val dataStore = context.dataStore

    // ── API Keys (optional, for higher rate limits) ────────────────

    val wallhavenApiKey: Flow<String> = get(Keys.WALLHAVEN_KEY, "")
    val pexelsApiKey: Flow<String> = get(Keys.PEXELS_KEY, com.freevibe.BuildConfig.PEXELS_API_KEY)
    val pixabayApiKey: Flow<String> = get(Keys.PIXABAY_KEY, com.freevibe.BuildConfig.PIXABAY_API_KEY)
    val freesoundApiKey: Flow<String> = get(Keys.FREESOUND_KEY, com.freevibe.BuildConfig.FREESOUND_API_KEY)
    val stabilityAiKey: Flow<String> = get(Keys.STABILITY_KEY, com.freevibe.BuildConfig.STABILITY_AI_KEY)
    val generatedContentProviderEnabled: Flow<Boolean> = get(
        Keys.GENERATED_CONTENT_PROVIDER_ENABLED,
        DEFAULT_GENERATED_CONTENT_PROVIDER_ENABLED,
    )
    val generatedContentDisclosureAccepted: Flow<Boolean> = get(Keys.GENERATED_CONTENT_DISCLOSURE_ACCEPTED, false)
    val wallhavenProviderEnabled: Flow<Boolean> = get(Keys.WALLHAVEN_PROVIDER_ENABLED, true)
    val bingProviderEnabled: Flow<Boolean> = get(Keys.BING_PROVIDER_ENABLED, true)
    val pexelsProviderEnabled: Flow<Boolean> = get(Keys.PEXELS_PROVIDER_ENABLED, true)
    val pixabayProviderEnabled: Flow<Boolean> = get(Keys.PIXABAY_PROVIDER_ENABLED, true)
    val communityProviderEnabled: Flow<Boolean> = get(
        Keys.COMMUNITY_PROVIDER_ENABLED,
        DEFAULT_COMMUNITY_PROVIDER_ENABLED,
    )
    val communityGuidelinesAcceptedVersion: Flow<Int> = get(Keys.COMMUNITY_GUIDELINES_ACCEPTED_VERSION, 0)
    val communityGuidelinesAccepted: Flow<Boolean> =
        communityGuidelinesAcceptedVersion.map(::hasAcceptedCommunityGuidelinesVersion)

    // Sanitize API keys: strip surrounding whitespace and reject any control chars (including CR/LF
    // which OkHttp would throw on when placed in a request header — prefer to drop them here with
    // a clean user-facing validation instead of a crash at request time).
    private fun sanitizeApiKey(key: String): String =
        key.trim().filter { it.code >= 0x20 && it.code != 0x7F }

    suspend fun setWallhavenKey(key: String) = set(Keys.WALLHAVEN_KEY, sanitizeApiKey(key))
    suspend fun setPexelsKey(key: String) = set(Keys.PEXELS_KEY, sanitizeApiKey(key))
    suspend fun setPixabayKey(key: String) = set(Keys.PIXABAY_KEY, sanitizeApiKey(key))
    suspend fun setFreesoundKey(key: String) = set(Keys.FREESOUND_KEY, sanitizeApiKey(key))
    suspend fun setStabilityKey(key: String) = set(Keys.STABILITY_KEY, sanitizeApiKey(key))
    suspend fun setGeneratedContentProviderEnabled(enabled: Boolean) =
        set(Keys.GENERATED_CONTENT_PROVIDER_ENABLED, enabled)
    suspend fun setGeneratedContentDisclosureAccepted(accepted: Boolean) =
        set(Keys.GENERATED_CONTENT_DISCLOSURE_ACCEPTED, accepted)
    suspend fun setWallhavenProviderEnabled(enabled: Boolean) = set(Keys.WALLHAVEN_PROVIDER_ENABLED, enabled)
    suspend fun setBingProviderEnabled(enabled: Boolean) = set(Keys.BING_PROVIDER_ENABLED, enabled)
    suspend fun setPexelsProviderEnabled(enabled: Boolean) = set(Keys.PEXELS_PROVIDER_ENABLED, enabled)
    suspend fun setPixabayProviderEnabled(enabled: Boolean) = set(Keys.PIXABAY_PROVIDER_ENABLED, enabled)
    suspend fun setCommunityProviderEnabled(enabled: Boolean) = set(Keys.COMMUNITY_PROVIDER_ENABLED, enabled)
    suspend fun acceptCommunityGuidelines() =
        set(Keys.COMMUNITY_GUIDELINES_ACCEPTED_VERSION, COMMUNITY_GUIDELINES_VERSION)
    suspend fun resetCommunityGuidelines() = set(Keys.COMMUNITY_GUIDELINES_ACCEPTED_VERSION, 0)

    // ── Auto-wallpaper ────────────────────────────────────────────

    val autoWallpaperEnabled: Flow<Boolean> = get(Keys.AUTO_WP_ENABLED, false)
    val autoWallpaperInterval: Flow<Long> = get(Keys.AUTO_WP_INTERVAL, 12L)
    val autoWallpaperSource: Flow<String> = get(Keys.AUTO_WP_SOURCE, "wallhaven")
    val autoWallpaperTarget: Flow<String> = get(Keys.AUTO_WP_TARGET, "BOTH")
    val localWallpaperFolderUri: Flow<String> = get(Keys.LOCAL_WALLPAPER_FOLDER_URI, "")
    /** Hold rotation until the device is plugged in (battery-friendly). */
    val autoWallpaperRequiresCharging: Flow<Boolean> = get(Keys.AUTO_WP_REQUIRES_CHARGING, false)
    /** Hold rotation until on Wi-Fi / unmetered (data-cap-friendly). */
    val autoWallpaperRequiresWiFiOnly: Flow<Boolean> = get(Keys.AUTO_WP_REQUIRES_WIFI, false)
    /** Hold rotation until the device is idle (no active foreground use). */
    val autoWallpaperRequiresIdle: Flow<Boolean> = get(Keys.AUTO_WP_REQUIRES_IDLE, false)
    val avoidRecentRepeats: Flow<Boolean> = get(Keys.AVOID_RECENT_REPEATS, false)
    suspend fun setAvoidRecentRepeats(v: Boolean) = set(Keys.AVOID_RECENT_REPEATS, v)
    suspend fun getRecentRotationIds(): List<String> =
        get(Keys.RECENT_ROTATION_IDS, "").first()
            .split(",")
            .filter { it.isNotBlank() }
    suspend fun addRecentRotationId(id: String) {
        val recent = getRecentRotationIds().toMutableList()
        recent.add(id)
        val maxSize = 50
        while (recent.size > maxSize) recent.removeAt(0)
        set(Keys.RECENT_ROTATION_IDS, recent.joinToString(","))
    }
    suspend fun clearRecentRotationIds() = set(Keys.RECENT_ROTATION_IDS, "")

    /** NX-6: rotate once on every device unlock (USER_PRESENT). Opt-in, foreground-service-backed. */
    val rotateOnUnlock: Flow<Boolean> = get(Keys.ROTATE_ON_UNLOCK, false)
    /** NX-6: pre-stage a new wallpaper on screen-off so unlock shows the new one. */
    val rotateOnScreenOff: Flow<Boolean> = get(Keys.ROTATE_ON_SCREEN_OFF, false)

    suspend fun setAutoWallpaperEnabled(enabled: Boolean) = set(Keys.AUTO_WP_ENABLED, enabled)
    suspend fun setAutoWallpaperInterval(hours: Long) = set(Keys.AUTO_WP_INTERVAL, hours)
    suspend fun setAutoWallpaperSource(source: String) = set(Keys.AUTO_WP_SOURCE, source)
    suspend fun setAutoWallpaperTarget(target: String) = set(Keys.AUTO_WP_TARGET, target)
    suspend fun setLocalWallpaperFolderUri(uri: String) = set(Keys.LOCAL_WALLPAPER_FOLDER_URI, uri.trim())
    suspend fun setAutoWallpaperRequiresCharging(v: Boolean) = set(Keys.AUTO_WP_REQUIRES_CHARGING, v)
    suspend fun setAutoWallpaperRequiresWiFiOnly(v: Boolean) = set(Keys.AUTO_WP_REQUIRES_WIFI, v)
    suspend fun setAutoWallpaperRequiresIdle(v: Boolean) = set(Keys.AUTO_WP_REQUIRES_IDLE, v)
    suspend fun setRotateOnUnlock(v: Boolean) = set(Keys.ROTATE_ON_UNLOCK, v)
    suspend fun setRotateOnScreenOff(v: Boolean) = set(Keys.ROTATE_ON_SCREEN_OFF, v)
    val autoWallpaperDarkenPercent: Flow<Int> = get(Keys.AUTO_WP_DARKEN_PERCENT, 0)
    suspend fun setAutoWallpaperDarkenPercent(v: Int) = set(Keys.AUTO_WP_DARKEN_PERCENT, v.coerceIn(0, 100))

    // ── Auto-backup ─────────────────────────────────────────────

    val autoBackupEnabled: Flow<Boolean> = get(Keys.AUTO_BACKUP_ENABLED, false)
    val autoBackupFolderUri: Flow<String> = get(Keys.AUTO_BACKUP_FOLDER_URI, "")
    val autoBackupIntervalHours: Flow<Long> = get(Keys.AUTO_BACKUP_INTERVAL_HOURS, 24L)
    val autoBackupKeepCount: Flow<Int> = get(Keys.AUTO_BACKUP_KEEP_COUNT, 5)

    suspend fun setAutoBackupEnabled(v: Boolean) = set(Keys.AUTO_BACKUP_ENABLED, v)
    suspend fun setAutoBackupFolderUri(uri: String) = set(Keys.AUTO_BACKUP_FOLDER_URI, uri.trim())
    suspend fun setAutoBackupIntervalHours(hours: Long) = set(Keys.AUTO_BACKUP_INTERVAL_HOURS, hours)
    suspend fun setAutoBackupKeepCount(count: Int) = set(Keys.AUTO_BACKUP_KEEP_COUNT, count)

    // Ringtone shuffle
    val ringtoneShuffleEnabled: Flow<Boolean> = get(Keys.RINGTONE_SHUFFLE_ENABLED, false)
    val ringtoneShuffleIntervalHours: Flow<Long> = get(Keys.RINGTONE_SHUFFLE_INTERVAL_HOURS, 24L)
    suspend fun setRingtoneShuffleEnabled(v: Boolean) = set(Keys.RINGTONE_SHUFFLE_ENABLED, v)
    suspend fun setRingtoneShuffleIntervalHours(hours: Long) = set(Keys.RINGTONE_SHUFFLE_INTERVAL_HOURS, hours)
    suspend fun ringtoneShuffleLastAppliedId(): String = get(Keys.RINGTONE_SHUFFLE_LAST_APPLIED_ID, "").first()
    suspend fun setRingtoneShuffleLastAppliedId(id: String) = set(Keys.RINGTONE_SHUFFLE_LAST_APPLIED_ID, id)
    val alarmShuffleEnabled: Flow<Boolean> = get(Keys.ALARM_SHUFFLE_ENABLED, false)
    suspend fun setAlarmShuffleEnabled(v: Boolean) = set(Keys.ALARM_SHUFFLE_ENABLED, v)
    suspend fun alarmShuffleLastAppliedId(): String = get(Keys.ALARM_SHUFFLE_LAST_APPLIED_ID, "").first()
    suspend fun setAlarmShuffleLastAppliedId(id: String) = set(Keys.ALARM_SHUFFLE_LAST_APPLIED_ID, id)

    // Sound profiles
    val soundProfilesEnabled: Flow<Boolean> = get(Keys.SOUND_PROFILES_ENABLED, false)
    suspend fun setSoundProfilesEnabled(v: Boolean) = set(Keys.SOUND_PROFILES_ENABLED, v)
    val soundProfilesJson: Flow<String> = get(Keys.SOUND_PROFILES_JSON, "")
    suspend fun setSoundProfilesJson(json: String) = set(Keys.SOUND_PROFILES_JSON, json)
    val soundProfileLastAppliedId: Flow<String> = get(Keys.SOUND_PROFILE_LAST_APPLIED_ID, "")
    suspend fun setSoundProfileLastAppliedId(id: String) = set(Keys.SOUND_PROFILE_LAST_APPLIED_ID, id)

    // Wallpaper packs (24H)
    val wallpaperPackEnabled: Flow<Boolean> = get(Keys.WALLPAPER_PACK_ENABLED, false)
    suspend fun setWallpaperPackEnabled(v: Boolean) = set(Keys.WALLPAPER_PACK_ENABLED, v)
    val wallpaperPackJson: Flow<String> = get(Keys.WALLPAPER_PACK_JSON, "")
    suspend fun setWallpaperPackJson(json: String) = set(Keys.WALLPAPER_PACK_JSON, json)
    val wallpaperPackLastAppliedDaypart: Flow<String> = get(Keys.WALLPAPER_PACK_LAST_DAYPART, "")
    suspend fun setWallpaperPackLastAppliedDaypart(daypart: String) = set(Keys.WALLPAPER_PACK_LAST_DAYPART, daypart)

    // Live wallpaper dimming
    val liveWallpaperDimEnabled: Flow<Boolean> = get(Keys.LIVE_WALLPAPER_DIM_ENABLED, false)
    suspend fun setLiveWallpaperDimEnabled(v: Boolean) = set(Keys.LIVE_WALLPAPER_DIM_ENABLED, v)

    val lastAppliedRingtoneUri: kotlinx.coroutines.flow.Flow<String> = get(Keys.LAST_APPLIED_RINGTONE_URI, "")
    suspend fun setLastAppliedRingtoneUri(uri: String) = set(Keys.LAST_APPLIED_RINGTONE_URI, uri)
    val lastAppliedNotificationUri: kotlinx.coroutines.flow.Flow<String> = get(Keys.LAST_APPLIED_NOTIFICATION_URI, "")
    suspend fun setLastAppliedNotificationUri(uri: String) = set(Keys.LAST_APPLIED_NOTIFICATION_URI, uri)
    val lastAppliedAlarmUri: kotlinx.coroutines.flow.Flow<String> = get(Keys.LAST_APPLIED_ALARM_URI, "")
    suspend fun setLastAppliedAlarmUri(uri: String) = set(Keys.LAST_APPLIED_ALARM_URI, uri)

    // ── Sound settings ────────────────────────────────────────────

    val autoPreviewSounds: Flow<Boolean> = get(Keys.AUTO_PREVIEW, true)
    val soundPreviewVolume: Flow<Float> = get(Keys.PREVIEW_VOLUME, 0.7f)

    suspend fun setAutoPreview(enabled: Boolean) = set(Keys.AUTO_PREVIEW, enabled)
    suspend fun setPreviewVolume(volume: Float) = set(Keys.PREVIEW_VOLUME, volume)

    // ── Display settings ──────────────────────────────────────────

    val wallpaperGridColumns: Flow<Int> = get(Keys.GRID_COLUMNS, 2)
    val showNsfwContent: Flow<Boolean> = get(Keys.SHOW_NSFW, false)
    /**
     * Wallhaven "sketchy" tier — suggestive imagery short of explicit nudity.
     * Independent of NSFW; both still require an API key set on Wallhaven's side.
     * Default false (SFW only) per safe-by-default principle.
     */
    val showSketchyContent: Flow<Boolean> = get(Keys.SHOW_SKETCHY, false)
    val preferredResolution: Flow<String> = get(Keys.PREF_RESOLUTION, "")

    suspend fun setGridColumns(columns: Int) = set(Keys.GRID_COLUMNS, columns)
    suspend fun setShowNsfw(show: Boolean) = set(Keys.SHOW_NSFW, show)
    suspend fun setShowSketchy(show: Boolean) = set(Keys.SHOW_SKETCHY, show)
    suspend fun setPreferredResolution(res: String) = set(Keys.PREF_RESOLUTION, res)

    // ── Reddit settings ───────────────────────────────────────────

    val redditSubreddits: Flow<String> = get(Keys.REDDIT_SUBS, "wallpapers,MobileWallpaper,wallpaper,WQHD_Wallpaper,MinimalWallpaper,phonewallpapers,iWallpaper")
    val redditVideoSubreddits: Flow<String> = get(Keys.REDDIT_VIDEO_SUBS, "livewallpapers,LiveWallpaper,Cinemagraphs,perfectloops")
    val redditProviderEnabled: Flow<Boolean> = get(Keys.REDDIT_PROVIDER_ENABLED, false)

    suspend fun setRedditSubreddits(subs: String) = set(Keys.REDDIT_SUBS, subs)
    suspend fun setRedditVideoSubreddits(subs: String) = set(Keys.REDDIT_VIDEO_SUBS, subs)
    suspend fun setRedditProviderEnabled(enabled: Boolean) = set(Keys.REDDIT_PROVIDER_ENABLED, enabled)

    // ── YouTube sound search ──────────────────────────────────────

    val ytSoundQueryRingtones: Flow<String> = get(Keys.YT_SOUND_RINGTONES, defaultRingtoneQuery())
    val ytSoundQueryNotifications: Flow<String> = get(Keys.YT_SOUND_NOTIFICATIONS, defaultNotificationQuery())
    val ytSoundQueryAlarms: Flow<String> = get(Keys.YT_SOUND_ALARMS, defaultAlarmQuery())
    val ytSoundBlockedWords: Flow<String> = get(Keys.YT_SOUND_BLOCKED, "compilation,mix,playlist,ranked,tier list,reaction,review,tutorial,how to,podcast,interview,live stream,part,episode")
    val youtubeProviderEnabled: Flow<Boolean> = get(Keys.YOUTUBE_PROVIDER_ENABLED, true)

    suspend fun setYtSoundQueryRingtones(q: String) = set(Keys.YT_SOUND_RINGTONES, q)
    suspend fun setYtSoundQueryNotifications(q: String) = set(Keys.YT_SOUND_NOTIFICATIONS, q)
    suspend fun setYtSoundQueryAlarms(q: String) = set(Keys.YT_SOUND_ALARMS, q)
    suspend fun setYtSoundBlockedWords(words: String) = set(Keys.YT_SOUND_BLOCKED, words)
    suspend fun setYoutubeProviderEnabled(enabled: Boolean) = set(Keys.YOUTUBE_PROVIDER_ENABLED, enabled)

    // ── Wallpaper scheduler ─────────────────────────────────────

    val schedulerEnabled: Flow<Boolean> = get(Keys.SCHEDULER_ENABLED, false)
    val schedulerIntervalMinutes: Flow<Long> = get(Keys.SCHEDULER_INTERVAL, 360L) // 6hr default
    val schedulerSource: Flow<String> = get(Keys.SCHEDULER_SOURCE, "discover")
    val schedulerHomeEnabled: Flow<Boolean> = get(Keys.SCHEDULER_HOME, true)
    val schedulerLockEnabled: Flow<Boolean> = get(Keys.SCHEDULER_LOCK, true)
    val schedulerShuffle: Flow<Boolean> = get(Keys.SCHEDULER_SHUFFLE, true)
    val schedulerCollectionId: Flow<Long> = get(Keys.SCHEDULER_COLLECTION, -1L)
    val schedulerDaySource: Flow<String> = get(Keys.SCHEDULER_DAY_SOURCE, "")
    val schedulerNightSource: Flow<String> = get(Keys.SCHEDULER_NIGHT_SOURCE, "")

    suspend fun setSchedulerEnabled(enabled: Boolean) = set(Keys.SCHEDULER_ENABLED, enabled)
    suspend fun setSchedulerInterval(minutes: Long) = set(Keys.SCHEDULER_INTERVAL, minutes)
    suspend fun setSchedulerSource(source: String) = set(Keys.SCHEDULER_SOURCE, source)
    suspend fun setSchedulerHome(enabled: Boolean) = set(Keys.SCHEDULER_HOME, enabled)
    suspend fun setSchedulerLock(enabled: Boolean) = set(Keys.SCHEDULER_LOCK, enabled)
    suspend fun setSchedulerShuffle(shuffle: Boolean) = set(Keys.SCHEDULER_SHUFFLE, shuffle)
    suspend fun setSchedulerCollection(id: Long) = set(Keys.SCHEDULER_COLLECTION, id)
    suspend fun setSchedulerDaySource(source: String) = set(Keys.SCHEDULER_DAY_SOURCE, source)
    suspend fun setSchedulerNightSource(source: String) = set(Keys.SCHEDULER_NIGHT_SOURCE, source)

    // ── Video wallpaper settings ────────────────────────────────

    val videoFpsLimit: Flow<Int> = get(Keys.VIDEO_FPS_LIMIT, 30)
    val videoPlaybackSpeed: Flow<Float> = get(Keys.VIDEO_PLAYBACK_SPEED, 1.0f)
    val videoFpsOverlayEnabled: Flow<Boolean> = get(Keys.VIDEO_FPS_OVERLAY, false)
    val videoAutoBatterySaver: Flow<Boolean> = get(Keys.VIDEO_AUTO_BATTERY_SAVER, true)

    suspend fun setVideoFpsLimit(fps: Int) {
        val sanitized = sanitizeVideoFpsLimit(fps)
        // Write SharedPreferences FIRST so VideoWallpaperService (which reads SP) always sees
        // the new value even if the suspending DataStore write gets cancelled mid-flight.
        // If cancellation happens between, DataStore catches up on the next successful write.
        context.getSharedPreferences(VIDEO_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(VIDEO_FPS_LIMIT_PREF, sanitized).apply()
        set(Keys.VIDEO_FPS_LIMIT, sanitized)
    }
    suspend fun setVideoPlaybackSpeed(speed: Float) {
        // SharedPreferences first — same rationale as setVideoFpsLimit. WallpaperService
        // cannot easily subscribe to DataStore, so SP is the source of truth for the runtime.
        context.getSharedPreferences(VIDEO_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(VIDEO_PLAYBACK_SPEED_PREF, speed).apply()
        set(Keys.VIDEO_PLAYBACK_SPEED, speed)
    }
    suspend fun setVideoFpsOverlayEnabled(enabled: Boolean) {
        context.getSharedPreferences(VIDEO_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(VIDEO_FPS_OVERLAY_PREF, enabled).apply()
        set(Keys.VIDEO_FPS_OVERLAY, enabled)
    }
    suspend fun setVideoAutoBatterySaver(enabled: Boolean) {
        context.getSharedPreferences(VIDEO_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(VIDEO_AUTO_BATTERY_SAVER_PREF, enabled).apply()
        set(Keys.VIDEO_AUTO_BATTERY_SAVER, enabled)
    }

    // ── Effects / adaptive settings ─────────────────────────────

    val adaptiveTintEnabled: Flow<Boolean> = get(Keys.ADAPTIVE_TINT, false)
    val adaptiveTintIntensity: Flow<Float> = get(Keys.ADAPTIVE_TINT_INTENSITY, 0.3f)
    val weatherEffectsEnabled: Flow<Boolean> = get(Keys.WEATHER_EFFECTS, false)
    val reduceAnimations: Flow<Boolean> = get(Keys.REDUCE_ANIMATIONS, false)
    val darkModeAutoSwitch: Flow<Boolean> = get(Keys.DARK_MODE_SWITCH, false)
    val darkModeWallpaperId: Flow<String> = get(Keys.DARK_WALLPAPER_ID, "")
    val lightModeWallpaperId: Flow<String> = get(Keys.LIGHT_WALLPAPER_ID, "")

    suspend fun setAdaptiveTintEnabled(enabled: Boolean) = set(Keys.ADAPTIVE_TINT, enabled)
    suspend fun setAdaptiveTintIntensity(intensity: Float) = set(Keys.ADAPTIVE_TINT_INTENSITY, intensity)
    suspend fun setWeatherEffectsEnabled(enabled: Boolean) = set(Keys.WEATHER_EFFECTS, enabled)
    suspend fun setReduceAnimations(enabled: Boolean) = set(Keys.REDUCE_ANIMATIONS, enabled)
    suspend fun setDarkModeAutoSwitch(enabled: Boolean) = set(Keys.DARK_MODE_SWITCH, enabled)
    suspend fun setDarkModeWallpaperId(id: String) = set(Keys.DARK_WALLPAPER_ID, id)
    suspend fun setLightModeWallpaperId(id: String) = set(Keys.LIGHT_WALLPAPER_ID, id)

    // ── Personalization ──────────────────────────────────────────

    val userStyles: Flow<String> = get(Keys.USER_STYLES, "")
    suspend fun setUserStyles(styles: String) = set(Keys.USER_STYLES, styles)

    // ── Generic helpers ───────────────────────────────────────────

    private fun <T> get(key: Preferences.Key<T>, default: T): Flow<T> =
        dataStore.data.catch { emit(emptyPreferences()) }.map { it[key] ?: default }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private object Keys {
        val WALLHAVEN_KEY = stringPreferencesKey("wallhaven_api_key")
        val PEXELS_KEY = stringPreferencesKey("pexels_api_key")
        val PIXABAY_KEY = stringPreferencesKey("pixabay_api_key")
        val FREESOUND_KEY = stringPreferencesKey("freesound_api_key")
        val STABILITY_KEY = stringPreferencesKey("stability_ai_key")
        val GENERATED_CONTENT_PROVIDER_ENABLED = booleanPreferencesKey("generated_content_provider_enabled")
        val GENERATED_CONTENT_DISCLOSURE_ACCEPTED = booleanPreferencesKey("generated_content_disclosure_accepted")
        val WALLHAVEN_PROVIDER_ENABLED = booleanPreferencesKey("wallhaven_provider_enabled")
        val BING_PROVIDER_ENABLED = booleanPreferencesKey("bing_provider_enabled")
        val PEXELS_PROVIDER_ENABLED = booleanPreferencesKey("pexels_provider_enabled")
        val PIXABAY_PROVIDER_ENABLED = booleanPreferencesKey("pixabay_provider_enabled")
        val COMMUNITY_PROVIDER_ENABLED = booleanPreferencesKey("community_provider_enabled")
        val COMMUNITY_GUIDELINES_ACCEPTED_VERSION = intPreferencesKey("community_guidelines_accepted_version")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_FOLDER_URI = stringPreferencesKey("auto_backup_folder_uri")
        val AUTO_BACKUP_INTERVAL_HOURS = longPreferencesKey("auto_backup_interval_hours")
        val AUTO_BACKUP_KEEP_COUNT = intPreferencesKey("auto_backup_keep_count")
        val AUTO_WP_ENABLED = booleanPreferencesKey("auto_wp_enabled")
        val AUTO_WP_INTERVAL = longPreferencesKey("auto_wp_interval")
        val AUTO_WP_SOURCE = stringPreferencesKey("auto_wp_source")
        val AUTO_WP_TARGET = stringPreferencesKey("auto_wp_target")
        val LOCAL_WALLPAPER_FOLDER_URI = stringPreferencesKey("local_wallpaper_folder_uri")
        val AUTO_PREVIEW = booleanPreferencesKey("auto_preview")
        val PREVIEW_VOLUME = floatPreferencesKey("preview_volume")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val SHOW_NSFW = booleanPreferencesKey("show_nsfw")
        val SHOW_SKETCHY = booleanPreferencesKey("show_sketchy")
        val AUTO_WP_REQUIRES_CHARGING = booleanPreferencesKey("auto_wp_requires_charging")
        val AUTO_WP_REQUIRES_WIFI = booleanPreferencesKey("auto_wp_requires_wifi")
        val AUTO_WP_REQUIRES_IDLE = booleanPreferencesKey("auto_wp_requires_idle")
        val ROTATE_ON_UNLOCK = booleanPreferencesKey("rotate_on_unlock")
        val ROTATE_ON_SCREEN_OFF = booleanPreferencesKey("rotate_on_screen_off")
        val AUTO_WP_DARKEN_PERCENT = intPreferencesKey("auto_wp_darken_percent")
        val PREF_RESOLUTION = stringPreferencesKey("pref_resolution")
        val REDDIT_SUBS = stringPreferencesKey("reddit_subreddits")
        val REDDIT_VIDEO_SUBS = stringPreferencesKey("reddit_video_subreddits")
        val REDDIT_PROVIDER_ENABLED = booleanPreferencesKey("reddit_provider_enabled")
        // YouTube sound search
        val YT_SOUND_RINGTONES = stringPreferencesKey("yt_sound_ringtones")
        val YT_SOUND_NOTIFICATIONS = stringPreferencesKey("yt_sound_notifications")
        val YT_SOUND_ALARMS = stringPreferencesKey("yt_sound_alarms")
        val YT_SOUND_BLOCKED = stringPreferencesKey("yt_sound_blocked")
        val YOUTUBE_PROVIDER_ENABLED = booleanPreferencesKey("youtube_provider_enabled")
        // Scheduler
        val SCHEDULER_ENABLED = booleanPreferencesKey("scheduler_enabled")
        val SCHEDULER_INTERVAL = longPreferencesKey("scheduler_interval_min")
        val SCHEDULER_SOURCE = stringPreferencesKey("scheduler_source")
        val SCHEDULER_HOME = booleanPreferencesKey("scheduler_home")
        val SCHEDULER_LOCK = booleanPreferencesKey("scheduler_lock")
        val SCHEDULER_SHUFFLE = booleanPreferencesKey("scheduler_shuffle")
        val SCHEDULER_COLLECTION = longPreferencesKey("scheduler_collection_id")
        val SCHEDULER_DAY_SOURCE = stringPreferencesKey("scheduler_day_source")
        val SCHEDULER_NIGHT_SOURCE = stringPreferencesKey("scheduler_night_source")
        val AVOID_RECENT_REPEATS = booleanPreferencesKey("avoid_recent_repeats")
        val RECENT_ROTATION_IDS = stringPreferencesKey("recent_rotation_ids")
        // Video wallpaper
        val VIDEO_FPS_LIMIT = intPreferencesKey("video_fps_limit")
        val VIDEO_PLAYBACK_SPEED = floatPreferencesKey("video_playback_speed")
        val VIDEO_FPS_OVERLAY = booleanPreferencesKey("video_fps_overlay_enabled")
        val VIDEO_AUTO_BATTERY_SAVER = booleanPreferencesKey("video_auto_battery_saver")
        // Effects / adaptive
        val ADAPTIVE_TINT = booleanPreferencesKey("adaptive_tint_enabled")
        val ADAPTIVE_TINT_INTENSITY = floatPreferencesKey("adaptive_tint_intensity")
        val WEATHER_EFFECTS = booleanPreferencesKey("weather_effects_enabled")
        val REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        val DARK_MODE_SWITCH = booleanPreferencesKey("dark_mode_auto_switch")
        val DARK_WALLPAPER_ID = stringPreferencesKey("dark_mode_wallpaper_id")
        val LIGHT_WALLPAPER_ID = stringPreferencesKey("light_mode_wallpaper_id")
        val USER_STYLES = stringPreferencesKey("user_styles")
        // Ringtone shuffle
        val RINGTONE_SHUFFLE_ENABLED = booleanPreferencesKey("ringtone_shuffle_enabled")
        val RINGTONE_SHUFFLE_INTERVAL_HOURS = longPreferencesKey("ringtone_shuffle_interval_hours")
        val RINGTONE_SHUFFLE_LAST_APPLIED_ID = stringPreferencesKey("ringtone_shuffle_last_applied_id")
        val ALARM_SHUFFLE_ENABLED = booleanPreferencesKey("alarm_shuffle_enabled")
        val ALARM_SHUFFLE_LAST_APPLIED_ID = stringPreferencesKey("alarm_shuffle_last_applied_id")
        val WALLPAPER_PACK_ENABLED = booleanPreferencesKey("wallpaper_pack_enabled")
        val WALLPAPER_PACK_JSON = stringPreferencesKey("wallpaper_pack_json")
        val WALLPAPER_PACK_LAST_DAYPART = stringPreferencesKey("wallpaper_pack_last_daypart")
        val LIVE_WALLPAPER_DIM_ENABLED = booleanPreferencesKey("live_wallpaper_dim_enabled")
        val SOUND_PROFILES_ENABLED = booleanPreferencesKey("sound_profiles_enabled")
        val SOUND_PROFILES_JSON = stringPreferencesKey("sound_profiles_json")
        val SOUND_PROFILE_LAST_APPLIED_ID = stringPreferencesKey("sound_profile_last_applied_id")
        val LAST_APPLIED_RINGTONE_URI = stringPreferencesKey("last_applied_ringtone_uri")
        val LAST_APPLIED_NOTIFICATION_URI = stringPreferencesKey("last_applied_notification_uri")
        val LAST_APPLIED_ALARM_URI = stringPreferencesKey("last_applied_alarm_uri")
    }
}
