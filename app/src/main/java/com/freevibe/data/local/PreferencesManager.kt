package com.freevibe.data.local

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.freevibe.data.legal.isProviderAvailableInCurrentArtifact
import com.freevibe.data.legal.providerCapability
import com.freevibe.data.model.COMMUNITY_GUIDELINES_VERSION
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.FitCanvasMode
import com.freevibe.data.model.FitCanvasPreferences
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.WALLPAPER_PRESENTATION_FILL
import com.freevibe.data.model.normalizeWallpaperPresentation
import com.freevibe.data.model.hasAcceptedCommunityGuidelinesVersion
import com.freevibe.service.ADAPTIVE_TINT_ENABLED_PREF
import com.freevibe.service.ADAPTIVE_TINT_INTENSITY_PREF
import com.freevibe.service.AgslShaderGallery
import com.freevibe.service.DAILY_WALLPAPER_ENABLED_PREF
import com.freevibe.service.LIVE_WALLPAPER_COLORS_ENABLED_DEFAULT
import com.freevibe.service.LIVE_WALLPAPER_COLORS_ENABLED_PREF
import com.freevibe.service.LIVE_WALLPAPER_DIM_ENABLED_PREF
import com.freevibe.service.LIVE_WALLPAPER_SHADER_PRESET_PREF
import com.freevibe.service.PARALLAX_WALLPAPER_PREFS_NAME
import com.freevibe.service.VIDEO_WALLPAPER_PREFS_NAME
import com.freevibe.service.VIDEO_WALLPAPER_DEFAULT_CANVAS_COLOR_PREF
import com.freevibe.service.VIDEO_WALLPAPER_DEFAULT_CANVAS_MODE_PREF
import com.freevibe.service.VIDEO_WALLPAPER_DEFAULT_SCALE_MODE_PREF
import com.freevibe.service.REDUCE_ANIMATIONS_PREF
import com.freevibe.service.TOUCH_EFFECT_STRENGTH_PREF
import com.freevibe.service.WEATHER_WALLPAPER_PREFS_NAME
import com.freevibe.service.WEATHER_VFX_EFFECT_PREF
import com.freevibe.service.VIDEO_AUTO_BATTERY_SAVER_PREF
import com.freevibe.service.VIDEO_AUTO_BATTERY_SAVER_CHANGED_ACTION
import com.freevibe.service.VIDEO_FPS_LIMIT_PREF
import com.freevibe.service.VIDEO_FPS_OVERLAY_PREF
import com.freevibe.service.VIDEO_PLAYBACK_SPEED_PREF
import com.freevibe.service.VIDEO_PREFS_NAME
import com.freevibe.service.VIDEO_STATS_PREFS_NAME
import com.freevibe.service.sanitizeVideoFpsLimit
import com.freevibe.service.WALLPAPER_CLOCK_OVERLAY_ENABLED_PREF
import com.freevibe.service.WALLPAPER_CLOCK_OVERLAY_MODE_PREF
import com.freevibe.service.WALLPAPER_CLOCK_OVERLAY_POSITION_PREF
import com.freevibe.service.WALLPAPER_CLOCK_OVERLAY_PREFS_NAME
import com.freevibe.service.WallpaperClockOverlayMode
import com.freevibe.service.WallpaperClockOverlayPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("freevibe_prefs")

private const val LEGACY_REDDIT_RSS_PAGE_PREFIX = "reddit_rss_page_v2_"
internal const val MAX_REDDIT_RSS_PAGE_METADATA_ENTRIES = 64
internal const val MAX_CONFIGURED_SUBREDDITS = 12
internal const val DEFAULT_REDDIT_WALLPAPER_SUBREDDITS =
    "iWallpaper,Amoledbackgrounds,MobileWallpaper,AnimePhoneWallpapers,phonewallpapers," +
        "iphonewallpapers,mobilewallpapers,Verticalwallpapers,WQHD_Wallpaper,MinimalWallpaper,iphonexwallpapers"
internal const val DEFAULT_REDDIT_VIDEO_SUBREDDITS =
    "livewallpapers,Cinemagraphs,perfectloops,phonewallpapers,AnimatedPixelArt,LivingBackgrounds,wallpaperengine"
internal const val SCHEDULER_DAY_NIGHT_MODE_SINGLE = "single"
internal const val SCHEDULER_DAY_NIGHT_MODE_CLOCK = "clock"
internal const val SCHEDULER_DAY_NIGHT_MODE_SYSTEM_THEME = "system_theme"
private val REDDIT_RSS_CURSOR_TOKEN = Regex("[a-zA-Z0-9]{1,64}")
private val REDDIT_SUBREDDIT_NAME = Regex("[A-Za-z0-9_]{2,40}")
private const val APP_PREFERENCES_NAME = "freevibe_app"
private const val ONBOARDING_COMPLETE_KEY = "onboarding_complete"

data class VideoBatteryStatsSnapshot(
    val lastSeenMs: Long,
    val batteryPercent: Int?,
    val charging: Boolean,
    val requestedFps: Int,
    val effectiveFps: Int,
    val lowBatterySaverActive: Boolean,
    val systemPowerSaveMode: Boolean,
    val motionPausedForPowerSave: Boolean,
    val visible: Boolean,
    val mediaType: String,
    val scaleMode: String,
)

internal data class RedditSubredditListValidation(
    val subreddits: List<String>,
    val invalidEntries: List<String>,
    val exceedsLimit: Boolean,
) {
    val isValid: Boolean get() = invalidEntries.isEmpty() && !exceedsLimit
    val normalized: String get() = subreddits.joinToString(",")
}

internal fun validateRedditSubredditList(raw: String): RedditSubredditListValidation {
    val entries = raw
        .split(Regex("[,\\r\\n]+"))
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { entry ->
            if (entry.startsWith("r/", ignoreCase = true)) entry.drop(2) else entry
        }
    val invalidEntries = entries.filterNot(REDDIT_SUBREDDIT_NAME::matches).distinct()
    val validEntries = entries
        .filter(REDDIT_SUBREDDIT_NAME::matches)
        .distinctBy { it.lowercase(java.util.Locale.ROOT) }
    return RedditSubredditListValidation(
        subreddits = validEntries.take(MAX_CONFIGURED_SUBREDDITS),
        invalidEntries = invalidEntries,
        exceedsLimit = validEntries.size > MAX_CONFIGURED_SUBREDDITS,
    )
}

internal fun normalizeRedditSubredditPreference(raw: String, defaults: String): String {
    val validation = validateRedditSubredditList(raw)
    return validation.normalized.takeIf { validation.isValid && it.isNotBlank() } ?: defaults
}

internal data class RedditRssPageMetadataEntry(
    val feedHash: Int,
    val requestToken: String,
    val nextCursor: String,
)

internal fun decodeRedditRssPageMetadata(raw: String?): List<RedditRssPageMetadataEntry> = raw
    .orEmpty()
    .lineSequence()
    .mapNotNull { line ->
        val fields = line.split('\t')
        if (fields.size != 3) return@mapNotNull null
        val feedHash = fields[0].toIntOrNull() ?: return@mapNotNull null
        val requestToken = fields[1].takeIf { it == "root" || REDDIT_RSS_CURSOR_TOKEN.matches(it) }
            ?: return@mapNotNull null
        val nextCursor = fields[2].takeIf {
            it == "__END__" ||
                (it.startsWith("t3_") && REDDIT_RSS_CURSOR_TOKEN.matches(it.removePrefix("t3_")))
        } ?: return@mapNotNull null
        RedditRssPageMetadataEntry(feedHash, requestToken, nextCursor)
    }
    .toList()
    .takeLast(MAX_REDDIT_RSS_PAGE_METADATA_ENTRIES)

internal fun updateRedditRssPageMetadata(
    raw: String?,
    feedHash: Int,
    requestAfter: String?,
    nextCursor: String,
): String {
    val requestToken = requestAfter
        ?.removePrefix("t3_")
        ?.takeIf(REDDIT_RSS_CURSOR_TOKEN::matches)
        ?: "root"
    val normalizedNextCursor = nextCursor.trim().takeIf {
        it == "__END__" ||
            (it.startsWith("t3_") && REDDIT_RSS_CURSOR_TOKEN.matches(it.removePrefix("t3_")))
    } ?: return decodeRedditRssPageMetadata(raw).encodeRedditRssPageMetadata()
    return decodeRedditRssPageMetadata(raw)
        .filterNot { it.feedHash == feedHash && it.requestToken == requestToken }
        .plus(RedditRssPageMetadataEntry(feedHash, requestToken, normalizedNextCursor))
        .takeLast(MAX_REDDIT_RSS_PAGE_METADATA_ENTRIES)
        .encodeRedditRssPageMetadata()
}

internal fun removeLegacyRedditRssPageMetadata(preferences: MutablePreferences): Int {
    val legacyKeys = preferences.asMap().keys
        .filter { it.name.startsWith(LEGACY_REDDIT_RSS_PAGE_PREFIX) }
    legacyKeys.forEach { key -> preferences.remove(key) }
    return legacyKeys.size
}

private fun List<RedditRssPageMetadataEntry>.encodeRedditRssPageMetadata(): String =
    joinToString("\n") { entry ->
        "${entry.feedHash}\t${entry.requestToken}\t${entry.nextCursor}"
    }

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        fun readVideoBatteryStats(context: Context): VideoBatteryStatsSnapshot {
            val stats = context.getSharedPreferences(VIDEO_STATS_PREFS_NAME, Context.MODE_PRIVATE)
            return VideoBatteryStatsSnapshot(
                lastSeenMs = stats.getLong("last_seen_ms", 0L),
                batteryPercent = if (stats.contains("battery_percent")) {
                    stats.getInt("battery_percent", -1).takeIf { it >= 0 }
                } else {
                    null
                },
                charging = stats.getBoolean("charging", false),
                requestedFps = stats.getInt("requested_fps", 30),
                effectiveFps = stats.getInt("effective_fps", 30),
                lowBatterySaverActive = stats.getBoolean("low_battery_saver_active", false),
                systemPowerSaveMode = stats.getBoolean("system_power_save_mode", false),
                motionPausedForPowerSave = stats.getBoolean("motion_paused_for_power_save", false),
                visible = stats.getBoolean("visible", false),
                mediaType = stats.getString("media_type", "none") ?: "none",
                scaleMode = stats.getString("scale_mode", "zoom") ?: "zoom",
            )
        }

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
    private val providerCredentialStore = ProviderCredentialStore(context)
    private val redditRssMetadataMigrationMutex = Mutex()
    @Volatile private var redditRssMetadataMigrated = false
    private val providerCredentialRevision = MutableStateFlow(0)
    private val providerCredentialStatusLock = Any()
    private val temporarilyUnavailableCredentialKeys = mutableSetOf<String>()
    private val _providerCredentialStorageUnavailable = MutableStateFlow(false)
    val providerCredentialStorageUnavailable: StateFlow<Boolean> =
        _providerCredentialStorageUnavailable.asStateFlow()
    private val _providerCredentialReentryRequired = MutableStateFlow(
        providerCredentialStore.hasReentryRequiredCredentials(),
    )
    val providerCredentialReentryRequired: StateFlow<Boolean> =
        _providerCredentialReentryRequired.asStateFlow()
    private val _providerCredentialReentryKeys = MutableStateFlow(
        providerCredentialStore.reentryRequiredKeys(),
    )
    val providerCredentialReentryKeys: StateFlow<Set<String>> =
        _providerCredentialReentryKeys.asStateFlow()

    // ── API Keys (optional, for higher rate limits) ────────────────

    val wallhavenApiKey: Flow<String> =
        providerCredential(ProviderCredentialKey.WALLHAVEN, Keys.WALLHAVEN_KEY, "")
    val pexelsApiKey: Flow<String> =
        providerCredential(ProviderCredentialKey.PEXELS, Keys.PEXELS_KEY, com.freevibe.BuildConfig.PEXELS_API_KEY)
    val pixabayApiKey: Flow<String> =
        providerCredential(ProviderCredentialKey.PIXABAY, Keys.PIXABAY_KEY, com.freevibe.BuildConfig.PIXABAY_API_KEY)
    val freesoundApiKey: Flow<String> =
        providerCredential(ProviderCredentialKey.FREESOUND, Keys.FREESOUND_KEY, com.freevibe.BuildConfig.FREESOUND_API_KEY)
    val generatedWallpaperProviderKey: Flow<String> = generatedWallpaperProviderKeyForFlavor()
    val generatedContentProviderEnabled: Flow<Boolean> = get(
        Keys.GENERATED_CONTENT_PROVIDER_ENABLED,
        DEFAULT_GENERATED_CONTENT_PROVIDER_ENABLED,
    )
    val generatedContentDisclosureAccepted: Flow<Boolean> = get(Keys.GENERATED_CONTENT_DISCLOSURE_ACCEPTED, false)
    // Reddit leads fresh-install wallpaper and motion feeds. Wallhaven, Bing, Pexels, and Pixabay
    // remain user choices in Settings; public release builds do not embed Pexels or Pixabay keys.
    val wallhavenProviderEnabled: Flow<Boolean> = get(
        Keys.WALLHAVEN_PROVIDER_ENABLED,
        providerCapability(ContentSource.WALLHAVEN).enabledByDefault,
    )
    val bingProviderEnabled: Flow<Boolean> = get(
        Keys.BING_PROVIDER_ENABLED,
        providerCapability(ContentSource.BING).enabledByDefault,
    )
    val pexelsProviderEnabled: Flow<Boolean> = get(
        Keys.PEXELS_PROVIDER_ENABLED,
        providerCapability(ContentSource.PEXELS).enabledByDefault,
    )
    val pixabayProviderEnabled: Flow<Boolean> = get(
        Keys.PIXABAY_PROVIDER_ENABLED,
        providerCapability(ContentSource.PIXABAY).enabledByDefault,
    )
    val communityProviderEnabled: Flow<Boolean> =
        if (com.freevibe.BuildConfig.FOSS_BUILD) {
            MutableStateFlow(false)
        } else {
            get(
                Keys.COMMUNITY_PROVIDER_ENABLED,
                DEFAULT_COMMUNITY_PROVIDER_ENABLED,
            )
        }
    val communityGuidelinesAcceptedVersion: Flow<Int> = get(Keys.COMMUNITY_GUIDELINES_ACCEPTED_VERSION, 0)
    val communityGuidelinesAccepted: Flow<Boolean> =
        communityGuidelinesAcceptedVersion.map(::hasAcceptedCommunityGuidelinesVersion)

    // Sanitize API keys: strip surrounding whitespace and reject any control chars (including CR/LF
    // which OkHttp would throw on when placed in a request header — prefer to drop them here with
    // a clean user-facing validation instead of a crash at request time).
    private fun sanitizeApiKey(key: String): String =
        key.trim().filter { it.code >= 0x20 && it.code != 0x7F }

    suspend fun setWallhavenKey(key: String) =
        setProviderCredential(ProviderCredentialKey.WALLHAVEN, Keys.WALLHAVEN_KEY, key)
    suspend fun setPexelsKey(key: String) =
        setProviderCredential(ProviderCredentialKey.PEXELS, Keys.PEXELS_KEY, key)
    suspend fun setPixabayKey(key: String) =
        setProviderCredential(ProviderCredentialKey.PIXABAY, Keys.PIXABAY_KEY, key)
    suspend fun setFreesoundKey(key: String) =
        setProviderCredential(ProviderCredentialKey.FREESOUND, Keys.FREESOUND_KEY, key)

    /** Re-runs active encrypted credential reads after a temporary Keystore failure. */
    fun retryProviderCredentials() {
        providerCredentialRevision.update { it + 1 }
    }

    /** Removes credentials for providers that can no longer originate requests. */
    suspend fun retireLegacyProviderCredentials() {
        providerCredentialStore.clear(ProviderCredentialKey.FREESOUND)
        recordProviderCredentialSuccess(ProviderCredentialKey.FREESOUND)
        dataStore.edit { it.remove(Keys.FREESOUND_KEY) }
        providerCredentialRevision.update { it + 1 }
    }
    suspend fun setGeneratedWallpaperProviderKey(key: String) =
        setGeneratedWallpaperProviderKeyForFlavor(key)
    suspend fun setGeneratedContentProviderEnabled(enabled: Boolean) {
        if (!com.freevibe.BuildConfig.FOSS_BUILD) {
            set(Keys.GENERATED_CONTENT_PROVIDER_ENABLED, enabled)
        }
    }
    suspend fun setGeneratedContentDisclosureAccepted(accepted: Boolean) =
        set(Keys.GENERATED_CONTENT_DISCLOSURE_ACCEPTED, accepted)
    suspend fun setWallhavenProviderEnabled(enabled: Boolean) = set(Keys.WALLHAVEN_PROVIDER_ENABLED, enabled)
    suspend fun setBingProviderEnabled(enabled: Boolean) = set(Keys.BING_PROVIDER_ENABLED, enabled)
    suspend fun setPexelsProviderEnabled(enabled: Boolean) = set(Keys.PEXELS_PROVIDER_ENABLED, enabled)
    suspend fun setPixabayProviderEnabled(enabled: Boolean) = set(Keys.PIXABAY_PROVIDER_ENABLED, enabled)
    suspend fun setCommunityProviderEnabled(enabled: Boolean) {
        if (!com.freevibe.BuildConfig.FOSS_BUILD) {
            set(Keys.COMMUNITY_PROVIDER_ENABLED, enabled)
        }
    }
    suspend fun acceptCommunityGuidelines() =
        set(Keys.COMMUNITY_GUIDELINES_ACCEPTED_VERSION, COMMUNITY_GUIDELINES_VERSION)
    suspend fun resetCommunityGuidelines() = set(Keys.COMMUNITY_GUIDELINES_ACCEPTED_VERSION, 0)

    // ── Auto-wallpaper ────────────────────────────────────────────

    val autoWallpaperEnabled: Flow<Boolean> = get(Keys.AUTO_WP_ENABLED, false)
    val autoWallpaperInterval: Flow<Long> = get(Keys.AUTO_WP_INTERVAL, 12L)
    // Default rotation source follows the default-enabled provider (Reddit, normalized to the
    // Reddit-first discover feed) so auto-rotation isn't wired to a source that's off by default.
    val autoWallpaperSource: Flow<String> = get(Keys.AUTO_WP_SOURCE, "reddit")
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
        // Move-to-end instead of append: duplicates would fill the FIFO with copies
        // of one key and evict genuinely-recent entries.
        recent.remove(id)
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
    val autoWallpaperRestartOnManual: Flow<Boolean> = get(Keys.AUTO_WP_RESTART_ON_MANUAL, true)

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
    val autoWallpaperNightVariantEnabled: Flow<Boolean> = get(Keys.AUTO_WP_NIGHT_VARIANT_ENABLED, false)
    val lastNightVariantWallpaperLocator: Flow<String> = get(Keys.LAST_NIGHT_VARIANT_WP_LOCATOR, "")
    val lastNightVariantWallpaperTarget: Flow<String> = get(Keys.LAST_NIGHT_VARIANT_WP_TARGET, "BOTH")
    val lastNightVariantWallpaperDarkenPercent: Flow<Int> = get(Keys.LAST_NIGHT_VARIANT_WP_DARKEN_PERCENT, 0)
    suspend fun setAutoWallpaperNightVariantEnabled(enabled: Boolean) =
        set(Keys.AUTO_WP_NIGHT_VARIANT_ENABLED, enabled)
    suspend fun setLastNightVariantWallpaper(locator: String, target: String, darkenPercent: Int = 0) {
        dataStore.edit { preferences ->
            preferences[Keys.LAST_NIGHT_VARIANT_WP_LOCATOR] = locator.trim()
            preferences[Keys.LAST_NIGHT_VARIANT_WP_TARGET] = target
            preferences[Keys.LAST_NIGHT_VARIANT_WP_DARKEN_PERCENT] = darkenPercent.coerceIn(0, 100)
        }
    }

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
    val soundShufflePoolsJson: Flow<String> = get(Keys.SOUND_SHUFFLE_POOLS_JSON, "")
    suspend fun setSoundShufflePoolsJson(json: String) = set(Keys.SOUND_SHUFFLE_POOLS_JSON, json)
    /** The favorites batch download in progress or last finished; see BatchDownloadStore. */
    val batchDownloadJson: Flow<String> = get(Keys.BATCH_DOWNLOAD_JSON, "")
    suspend fun setBatchDownloadJson(json: String) = set(Keys.BATCH_DOWNLOAD_JSON, json)

    // Wallpaper packs (24H)
    val wallpaperPackEnabled: Flow<Boolean> = get(Keys.WALLPAPER_PACK_ENABLED, false)
    suspend fun setWallpaperPackEnabled(v: Boolean) = set(Keys.WALLPAPER_PACK_ENABLED, v)
    val wallpaperPackJson: Flow<String> = get(Keys.WALLPAPER_PACK_JSON, "")
    suspend fun setWallpaperPackJson(json: String) = set(Keys.WALLPAPER_PACK_JSON, json)
    val wallpaperPackLastAppliedDaypart: Flow<String> = get(Keys.WALLPAPER_PACK_LAST_DAYPART, "")
    suspend fun setWallpaperPackLastAppliedDaypart(daypart: String) = set(Keys.WALLPAPER_PACK_LAST_DAYPART, daypart)

    /** Stores every key a theme-pack import changes in one edit. A null value leaves that key alone. */
    suspend fun applyThemePackImport(
        wallpaperPackJson: String?,
        soundProfilesJson: String?,
        ringtoneUri: String?,
        notificationUri: String?,
        alarmUri: String?,
    ) {
        dataStore.edit { values ->
            wallpaperPackJson?.let {
                values[Keys.WALLPAPER_PACK_JSON] = it
                values[Keys.WALLPAPER_PACK_LAST_DAYPART] = ""
            }
            soundProfilesJson?.let {
                values[Keys.SOUND_PROFILES_JSON] = it
                values[Keys.SOUND_PROFILE_LAST_APPLIED_ID] = ""
            }
            ringtoneUri?.let { values[Keys.LAST_APPLIED_RINGTONE_URI] = it }
            notificationUri?.let { values[Keys.LAST_APPLIED_NOTIFICATION_URI] = it }
            alarmUri?.let { values[Keys.LAST_APPLIED_ALARM_URI] = it }
        }
    }

    // Live wallpaper dimming
    val liveWallpaperDimEnabled: Flow<Boolean> = get(Keys.LIVE_WALLPAPER_DIM_ENABLED, false)
    suspend fun setLiveWallpaperDimEnabled(v: Boolean) {
        writeLiveWallpaperFlag(LIVE_WALLPAPER_DIM_ENABLED_PREF, v)
        set(Keys.LIVE_WALLPAPER_DIM_ENABLED, v)
    }

    // Whether the live-wallpaper engines publish WallpaperColors for system theming
    val liveWallpaperColorsEnabled: Flow<Boolean> =
        get(Keys.LIVE_WALLPAPER_COLORS_ENABLED, LIVE_WALLPAPER_COLORS_ENABLED_DEFAULT)
    suspend fun setLiveWallpaperColorsEnabled(v: Boolean) {
        writeAllLiveWallpaperFlags(LIVE_WALLPAPER_COLORS_ENABLED_PREF, v)
        set(Keys.LIVE_WALLPAPER_COLORS_ENABLED, v)
    }

    val wallpaperClockOverlayEnabled: Flow<Boolean> =
        get(Keys.WALLPAPER_CLOCK_OVERLAY_ENABLED, false)
    val wallpaperClockOverlayMode: Flow<String> =
        get(Keys.WALLPAPER_CLOCK_OVERLAY_MODE, WallpaperClockOverlayMode.TIME_AND_DATE.preferenceValue)
    val wallpaperClockOverlayPosition: Flow<String> =
        get(Keys.WALLPAPER_CLOCK_OVERLAY_POSITION, WallpaperClockOverlayPosition.BOTTOM_RIGHT.preferenceValue)

    suspend fun setWallpaperClockOverlayEnabled(enabled: Boolean) {
        wallpaperClockOverlayPrefs().edit()
            .putBoolean(WALLPAPER_CLOCK_OVERLAY_ENABLED_PREF, enabled)
            .apply()
        set(Keys.WALLPAPER_CLOCK_OVERLAY_ENABLED, enabled)
    }

    suspend fun setWallpaperClockOverlayMode(mode: String) {
        val normalized = WallpaperClockOverlayMode.fromPreference(mode).preferenceValue
        wallpaperClockOverlayPrefs().edit()
            .putString(WALLPAPER_CLOCK_OVERLAY_MODE_PREF, normalized)
            .apply()
        set(Keys.WALLPAPER_CLOCK_OVERLAY_MODE, normalized)
    }

    suspend fun setWallpaperClockOverlayPosition(position: String) {
        val normalized = WallpaperClockOverlayPosition.fromPreference(position).preferenceValue
        wallpaperClockOverlayPrefs().edit()
            .putString(WALLPAPER_CLOCK_OVERLAY_POSITION_PREF, normalized)
            .apply()
        set(Keys.WALLPAPER_CLOCK_OVERLAY_POSITION, normalized)
    }

    private fun wallpaperClockOverlayPrefs() =
        context.getSharedPreferences(WALLPAPER_CLOCK_OVERLAY_PREFS_NAME, Context.MODE_PRIVATE)

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

    val redditSubreddits: Flow<String> = get(
        Keys.REDDIT_SUBS,
        DEFAULT_REDDIT_WALLPAPER_SUBREDDITS,
    )
    val redditVideoSubreddits: Flow<String> = get(
        Keys.REDDIT_VIDEO_SUBS,
        DEFAULT_REDDIT_VIDEO_SUBREDDITS,
    )
    val redditProviderEnabled: Flow<Boolean> = get(
        Keys.REDDIT_PROVIDER_ENABLED,
        providerCapability(ContentSource.REDDIT).enabledByDefault,
    )

    suspend fun setRedditSubreddits(subs: String) = set(
        Keys.REDDIT_SUBS,
        normalizeRedditSubredditPreference(subs, DEFAULT_REDDIT_WALLPAPER_SUBREDDITS),
    )
    suspend fun setRedditVideoSubreddits(subs: String) = set(
        Keys.REDDIT_VIDEO_SUBS,
        normalizeRedditSubredditPreference(subs, DEFAULT_REDDIT_VIDEO_SUBREDDITS),
    )
    suspend fun setRedditProviderEnabled(enabled: Boolean) = set(Keys.REDDIT_PROVIDER_ENABLED, enabled)

    /**
     * Persist Atom page metadata independently from usable wallpaper rows. The final raw Reddit
     * entry can differ from the final accepted image, so the exact request cursor must own the
     * exact next cursor (or terminal marker) across process restarts.
     */
    suspend fun getRedditRssNextCursor(feedHash: Int, requestAfter: String?): String =
        afterRedditRssMetadataMigration {
            val requestToken = requestAfter
                ?.removePrefix("t3_")
                ?.takeIf(REDDIT_RSS_CURSOR_TOKEN::matches)
                ?: "root"
            decodeRedditRssPageMetadata(get(Keys.REDDIT_RSS_PAGE_METADATA_V3, "").first())
                .lastOrNull { it.feedHash == feedHash && it.requestToken == requestToken }
                ?.nextCursor
                .orEmpty()
        }

    suspend fun setRedditRssNextCursor(feedHash: Int, requestAfter: String?, nextCursor: String) {
        afterRedditRssMetadataMigration {
            dataStore.edit { preferences ->
                preferences[Keys.REDDIT_RSS_PAGE_METADATA_V3] = updateRedditRssPageMetadata(
                    raw = preferences[Keys.REDDIT_RSS_PAGE_METADATA_V3],
                    feedHash = feedHash,
                    requestAfter = requestAfter,
                    nextCursor = nextCursor,
                )
            }
        }
    }

    suspend fun getRedditRssNextAllowedAtMs(): Long =
        get(Keys.REDDIT_RSS_NEXT_ALLOWED_AT_MS, 0L).first()

    suspend fun setRedditRssNextAllowedAtMs(timestampMs: Long) =
        set(Keys.REDDIT_RSS_NEXT_ALLOWED_AT_MS, timestampMs.coerceAtLeast(0L))

    private suspend fun <T> afterRedditRssMetadataMigration(block: suspend () -> T): T {
        if (!redditRssMetadataMigrated) {
            redditRssMetadataMigrationMutex.withLock {
                if (!redditRssMetadataMigrated) {
                    dataStore.edit(::removeLegacyRedditRssPageMetadata)
                    redditRssMetadataMigrated = true
                }
            }
        }
        return block()
    }

    // ── YouTube sound search ──────────────────────────────────────

    val ytSoundQueryRingtones: Flow<String> = get(Keys.YT_SOUND_RINGTONES, defaultRingtoneQuery())
    val ytSoundQueryNotifications: Flow<String> = get(Keys.YT_SOUND_NOTIFICATIONS, defaultNotificationQuery())
    val ytSoundQueryAlarms: Flow<String> = get(Keys.YT_SOUND_ALARMS, defaultAlarmQuery())
    val ytSoundBlockedWords: Flow<String> = get(Keys.YT_SOUND_BLOCKED, "compilation,mix,playlist,ranked,tier list,reaction,review,tutorial,how to,podcast,interview,live stream,part,episode")
    val youtubeProviderAvailable: Boolean = isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE)
    val youtubeProviderEnabled: Flow<Boolean> = if (youtubeProviderAvailable) {
        get(
            Keys.YOUTUBE_PROVIDER_ENABLED,
            providerCapability(ContentSource.YOUTUBE).enabledByDefault,
        )
    } else {
        flowOf(false)
    }
    val youtubePoTokenProviderUrl: Flow<String> = get(Keys.YOUTUBE_PO_TOKEN_PROVIDER_URL, "")

    suspend fun setYtSoundQueryRingtones(q: String) = set(Keys.YT_SOUND_RINGTONES, q)
    suspend fun setYtSoundQueryNotifications(q: String) = set(Keys.YT_SOUND_NOTIFICATIONS, q)
    suspend fun setYtSoundQueryAlarms(q: String) = set(Keys.YT_SOUND_ALARMS, q)
    suspend fun setYtSoundBlockedWords(words: String) = set(Keys.YT_SOUND_BLOCKED, words)
    suspend fun setYoutubeProviderEnabled(enabled: Boolean) =
        set(Keys.YOUTUBE_PROVIDER_ENABLED, enabled && youtubeProviderAvailable)
    suspend fun setYoutubePoTokenProviderUrl(url: String) = set(Keys.YOUTUBE_PO_TOKEN_PROVIDER_URL, url)

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
    val schedulerDayNightMode: Flow<String> = get(Keys.SCHEDULER_DAY_NIGHT_MODE, SCHEDULER_DAY_NIGHT_MODE_SINGLE)
    val schedulerDayStartHour: Flow<Int> = get(Keys.SCHEDULER_DAY_START_HOUR, 6)
    val schedulerNightStartHour: Flow<Int> = get(Keys.SCHEDULER_NIGHT_START_HOUR, 18)

    suspend fun setSchedulerEnabled(enabled: Boolean) = set(Keys.SCHEDULER_ENABLED, enabled)
    suspend fun setSchedulerInterval(minutes: Long) = set(Keys.SCHEDULER_INTERVAL, minutes)
    suspend fun setSchedulerSource(source: String) = set(Keys.SCHEDULER_SOURCE, source)
    suspend fun setSchedulerHome(enabled: Boolean) = set(Keys.SCHEDULER_HOME, enabled)
    suspend fun setSchedulerLock(enabled: Boolean) = set(Keys.SCHEDULER_LOCK, enabled)
    suspend fun setSchedulerShuffle(shuffle: Boolean) = set(Keys.SCHEDULER_SHUFFLE, shuffle)
    suspend fun setSchedulerCollection(id: Long) = set(Keys.SCHEDULER_COLLECTION, id)
    suspend fun setSchedulerDaySource(source: String) = set(Keys.SCHEDULER_DAY_SOURCE, source)
    suspend fun setSchedulerNightSource(source: String) = set(Keys.SCHEDULER_NIGHT_SOURCE, source)
    suspend fun setSchedulerDayNightMode(mode: String) = set(
        Keys.SCHEDULER_DAY_NIGHT_MODE,
        mode.takeIf {
            it == SCHEDULER_DAY_NIGHT_MODE_SINGLE ||
                it == SCHEDULER_DAY_NIGHT_MODE_CLOCK ||
                it == SCHEDULER_DAY_NIGHT_MODE_SYSTEM_THEME
        } ?: SCHEDULER_DAY_NIGHT_MODE_SINGLE,
    )
    suspend fun setSchedulerDayStartHour(hour: Int) = set(Keys.SCHEDULER_DAY_START_HOUR, hour.coerceIn(0, 23))
    suspend fun setSchedulerNightStartHour(hour: Int) = set(Keys.SCHEDULER_NIGHT_START_HOUR, hour.coerceIn(0, 23))

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
        context.sendBroadcast(
            Intent(VIDEO_AUTO_BATTERY_SAVER_CHANGED_ACTION).setPackage(context.packageName),
        )
        set(Keys.VIDEO_AUTO_BATTERY_SAVER, enabled)
    }

    val staticWallpaperPresentation: Flow<String> = get(
        Keys.STATIC_WALLPAPER_PRESENTATION,
        WALLPAPER_PRESENTATION_FILL,
    ).map(::normalizeWallpaperPresentation)
    val staticFitCanvasMode: Flow<String> = get(
        Keys.STATIC_FIT_CANVAS_MODE,
        FitCanvasMode.AMOLED_BLACK.preferenceValue,
    ).map { FitCanvasMode.fromPreference(it).preferenceValue }
    val staticFitCanvasColor: Flow<Int> = get(
        Keys.STATIC_FIT_CANVAS_COLOR,
        com.freevibe.data.model.DEFAULT_FIT_CANVAS_COLOR,
    ).map { it or 0xFF000000.toInt() }
    val videoWallpaperPresentation: Flow<String> = get(
        Keys.VIDEO_WALLPAPER_PRESENTATION,
        WALLPAPER_PRESENTATION_FILL,
    ).map(::normalizeWallpaperPresentation)
    val videoFitCanvasMode: Flow<String> = get(
        Keys.VIDEO_FIT_CANVAS_MODE,
        FitCanvasMode.AMOLED_BLACK.preferenceValue,
    ).map { FitCanvasMode.fromPreference(it).preferenceValue }
    val videoFitCanvasColor: Flow<Int> = get(
        Keys.VIDEO_FIT_CANVAS_COLOR,
        com.freevibe.data.model.DEFAULT_FIT_CANVAS_COLOR,
    ).map { it or 0xFF000000.toInt() }

    suspend fun setStaticFitCanvasPreferences(presentation: String, style: FitCanvasStyle) {
        val normalizedStyle = style.normalized()
        dataStore.edit { values ->
            values[Keys.STATIC_WALLPAPER_PRESENTATION] = normalizeWallpaperPresentation(presentation)
            values[Keys.STATIC_FIT_CANVAS_MODE] = normalizedStyle.mode.preferenceValue
            values[Keys.STATIC_FIT_CANVAS_COLOR] = normalizedStyle.customColor
        }
    }

    suspend fun setVideoFitCanvasPreferences(presentation: String, style: FitCanvasStyle) {
        val normalizedPresentation = normalizeWallpaperPresentation(presentation)
        val normalizedStyle = style.normalized()
        context.getSharedPreferences(VIDEO_WALLPAPER_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(VIDEO_WALLPAPER_DEFAULT_SCALE_MODE_PREF, normalizedPresentation)
            .putString(VIDEO_WALLPAPER_DEFAULT_CANVAS_MODE_PREF, normalizedStyle.mode.preferenceValue)
            .putInt(VIDEO_WALLPAPER_DEFAULT_CANVAS_COLOR_PREF, normalizedStyle.customColor)
            .apply()
        dataStore.edit { values ->
            values[Keys.VIDEO_WALLPAPER_PRESENTATION] = normalizedPresentation
            values[Keys.VIDEO_FIT_CANVAS_MODE] = normalizedStyle.mode.preferenceValue
            values[Keys.VIDEO_FIT_CANVAS_COLOR] = normalizedStyle.customColor
        }
    }

    suspend fun fitCanvasPreferencesSnapshot(): FitCanvasPreferences = FitCanvasPreferences(
        staticPresentation = staticWallpaperPresentation.first(),
        staticStyle = FitCanvasStyle(
            mode = FitCanvasMode.fromPreference(staticFitCanvasMode.first()),
            customColor = staticFitCanvasColor.first(),
        ),
        videoPresentation = videoWallpaperPresentation.first(),
        videoStyle = FitCanvasStyle(
            mode = FitCanvasMode.fromPreference(videoFitCanvasMode.first()),
            customColor = videoFitCanvasColor.first(),
        ),
    ).normalized()

    suspend fun restoreFitCanvasPreferences(preferences: FitCanvasPreferences) {
        val normalized = preferences.normalized()
        setStaticFitCanvasPreferences(normalized.staticPresentation, normalized.staticStyle)
        setVideoFitCanvasPreferences(normalized.videoPresentation, normalized.videoStyle)
    }

    // ── Effects / adaptive settings ─────────────────────────────

    val adaptiveTintEnabled: Flow<Boolean> = get(Keys.ADAPTIVE_TINT, false)
    val adaptiveTintIntensity: Flow<Float> = get(Keys.ADAPTIVE_TINT_INTENSITY, 0.3f)
    val weatherEffectsEnabled: Flow<Boolean> = get(Keys.WEATHER_EFFECTS, false)
    val reduceAnimations: Flow<Boolean> = get(Keys.REDUCE_ANIMATIONS, false)
    val darkModeAutoSwitch: Flow<Boolean> = get(Keys.DARK_MODE_SWITCH, false)
    val darkModeWallpaperId: Flow<String> = get(Keys.DARK_WALLPAPER_ID, "")
    val lightModeWallpaperId: Flow<String> = get(Keys.LIGHT_WALLPAPER_ID, "")
    val liveWallpaperShaderPreset: Flow<String> = get(
        Keys.LIVE_WALLPAPER_SHADER_PRESET,
        AgslShaderGallery.NONE_ID,
    ).map(AgslShaderGallery::sanitizeId)

    // WeatherWallpaperService reads these keys from SharedPreferences only, so the SP write
    // must land first. These bridges used to live in SettingsViewModel and wrote DataStore
    // first: leaving Settings cancelled viewModelScope between the two writes, so the live
    // wallpaper kept the old value permanently while the toggle read as changed.
    suspend fun setAdaptiveTintEnabled(enabled: Boolean) {
        writeLiveWallpaperFlag(ADAPTIVE_TINT_ENABLED_PREF, enabled)
        set(Keys.ADAPTIVE_TINT, enabled)
    }

    suspend fun setAdaptiveTintIntensity(intensity: Float) {
        weatherWallpaperPrefs().edit().putFloat(ADAPTIVE_TINT_INTENSITY_PREF, intensity).apply()
        set(Keys.ADAPTIVE_TINT_INTENSITY, intensity)
    }

    suspend fun setWeatherEffectsEnabled(enabled: Boolean) = set(Keys.WEATHER_EFFECTS, enabled)

    suspend fun setReduceAnimations(enabled: Boolean) {
        writeLiveWallpaperFlag(REDUCE_ANIMATIONS_PREF, enabled)
        set(Keys.REDUCE_ANIMATIONS, enabled)
    }
    suspend fun setDarkModeAutoSwitch(enabled: Boolean) = set(Keys.DARK_MODE_SWITCH, enabled)
    suspend fun setDarkModeWallpaperId(id: String) = set(Keys.DARK_WALLPAPER_ID, id)
    suspend fun setLightModeWallpaperId(id: String) = set(Keys.LIGHT_WALLPAPER_ID, id)
    suspend fun setLiveWallpaperShaderPreset(id: String) {
        val sanitized = AgslShaderGallery.sanitizeId(id)
        weatherWallpaperPrefs().edit().putString(LIVE_WALLPAPER_SHADER_PRESET_PREF, sanitized).apply()
        set(Keys.LIVE_WALLPAPER_SHADER_PRESET, sanitized)
    }

    /** Runtime-only settings consumed synchronously by the weather wallpaper service. */
    fun setDailyWallpaperEnabled(enabled: Boolean) {
        weatherWallpaperPrefs().edit().putBoolean(DAILY_WALLPAPER_ENABLED_PREF, enabled).apply()
    }

    fun setWeatherVfxEffect(effect: String) {
        weatherWallpaperPrefs().edit().putString(WEATHER_VFX_EFFECT_PREF, effect).apply()
    }

    fun setTouchEffectStrength(strength: String) {
        weatherWallpaperPrefs().edit().putString(TOUCH_EFFECT_STRENGTH_PREF, strength).apply()
    }

    private fun weatherWallpaperPrefs() =
        context.getSharedPreferences(WEATHER_WALLPAPER_PREFS_NAME, Context.MODE_PRIVATE)

    fun isOnboardingComplete(): Boolean = context
        .getSharedPreferences(APP_PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getBoolean(ONBOARDING_COMPLETE_KEY, false)

    fun setOnboardingComplete() {
        context.getSharedPreferences(APP_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ONBOARDING_COMPLETE_KEY, true)
            .apply()
    }

    fun isDailyWallpaperEnabled(): Boolean =
        weatherWallpaperPrefs().getBoolean(DAILY_WALLPAPER_ENABLED_PREF, false)

    fun weatherVfxEffect(): String =
        weatherWallpaperPrefs().getString(WEATHER_VFX_EFFECT_PREF, "NONE") ?: "NONE"

    fun touchEffectStrength(): String =
        weatherWallpaperPrefs().getString(TOUCH_EFFECT_STRENGTH_PREF, "OFF") ?: "OFF"

    private fun writeLiveWallpaperFlag(key: String, enabled: Boolean) {
        weatherWallpaperPrefs().edit().putBoolean(key, enabled).apply()
    }

    /**
     * Writes a flag every live-wallpaper engine honours.
     *
     * The three engines each read their own preference file, so a setting the UI
     * presents as engine-agnostic has to land in all three or it silently applies
     * to whichever engine happens to own the weather file.
     */
    private fun writeAllLiveWallpaperFlags(key: String, enabled: Boolean) {
        listOf(
            WEATHER_WALLPAPER_PREFS_NAME,
            PARALLAX_WALLPAPER_PREFS_NAME,
            VIDEO_WALLPAPER_PREFS_NAME,
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().putBoolean(key, enabled).apply()
        }
    }

    // ── Personalization ──────────────────────────────────────────

    val userStyles: Flow<String> = get(Keys.USER_STYLES, "")
    val wallpaperStyleLearningJson: Flow<String> = get(Keys.WALLPAPER_STYLE_LEARNING_JSON, "")
    suspend fun setUserStyles(styles: String) = set(Keys.USER_STYLES, styles)
    suspend fun setWallpaperStyleLearningJson(json: String) = set(Keys.WALLPAPER_STYLE_LEARNING_JSON, json)
    suspend fun clearWallpaperStyleLearning() = set(Keys.WALLPAPER_STYLE_LEARNING_JSON, "")

    // ── Generic helpers ───────────────────────────────────────────

    private fun <T> get(key: Preferences.Key<T>, default: T): Flow<T> =
        dataStore.data.catch { emit(emptyPreferences()) }.map { it[key] ?: default }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    internal fun providerCredential(
        credentialKey: ProviderCredentialKey,
        legacyKey: Preferences.Key<String>,
        default: String,
    ): Flow<String> = providerCredentialRevision
        .map { readProviderCredential(credentialKey, legacyKey, default) }
        .distinctUntilChanged()

    private suspend fun readProviderCredential(
        credentialKey: ProviderCredentialKey,
        legacyKey: Preferences.Key<String>,
        default: String,
    ): String {
        val legacyValue = dataStore.data.catch { emit(emptyPreferences()) }.first()[legacyKey]
        if (legacyValue != null) {
            val sanitized = sanitizeApiKey(legacyValue)
            if (writeProviderCredentialValue(credentialKey, sanitized)) {
                dataStore.edit { it.remove(legacyKey) }
            }
            return sanitized.ifBlank { default }
        }
        return readProviderCredentialValue(credentialKey) ?: default
    }

    internal suspend fun setProviderCredential(
        credentialKey: ProviderCredentialKey,
        legacyKey: Preferences.Key<String>,
        key: String,
    ) {
        val sanitized = sanitizeApiKey(key)
        val stored = writeProviderCredentialValue(credentialKey, sanitized)
        if (stored || sanitized.isBlank()) {
            dataStore.edit { it.remove(legacyKey) }
        }
        providerCredentialRevision.update { it + 1 }
    }

    private fun readProviderCredentialValue(credentialKey: ProviderCredentialKey): String? =
        when (val result = providerCredentialStore.read(credentialKey)) {
            is ProviderCredentialReadResult.Available -> {
                recordProviderCredentialSuccess(credentialKey)
                result.value
            }
            ProviderCredentialReadResult.Missing -> {
                recordProviderCredentialSuccess(credentialKey)
                null
            }
            ProviderCredentialReadResult.RetryableFailure -> {
                recordProviderCredentialTemporaryFailure(credentialKey)
                null
            }
            is ProviderCredentialReadResult.ReentryRequired -> {
                recordProviderCredentialSuccess(credentialKey)
                null
            }
        }

    private fun writeProviderCredentialValue(
        credentialKey: ProviderCredentialKey,
        value: String,
    ): Boolean = runCatching {
        providerCredentialStore.set(credentialKey, value)
    }.onSuccess {
        recordProviderCredentialSuccess(credentialKey)
    }.onFailure {
        recordProviderCredentialTemporaryFailure(credentialKey)
    }.isSuccess

    private fun recordProviderCredentialTemporaryFailure(credentialKey: ProviderCredentialKey) {
        synchronized(providerCredentialStatusLock) {
            temporarilyUnavailableCredentialKeys += credentialKey.storageKey
            publishProviderCredentialStatus()
        }
    }

    private fun recordProviderCredentialSuccess(credentialKey: ProviderCredentialKey) {
        synchronized(providerCredentialStatusLock) {
            temporarilyUnavailableCredentialKeys -= credentialKey.storageKey
            publishProviderCredentialStatus()
        }
    }

    private fun publishProviderCredentialStatus() {
        _providerCredentialStorageUnavailable.value = temporarilyUnavailableCredentialKeys.isNotEmpty()
        val reentryKeys = providerCredentialStore.reentryRequiredKeys()
        _providerCredentialReentryKeys.value = reentryKeys
        _providerCredentialReentryRequired.value = reentryKeys.isNotEmpty()
    }

    private object Keys {
        val WALLHAVEN_KEY = stringPreferencesKey("wallhaven_api_key")
        val PEXELS_KEY = stringPreferencesKey("pexels_api_key")
        val PIXABAY_KEY = stringPreferencesKey("pixabay_api_key")
        val FREESOUND_KEY = stringPreferencesKey("freesound_api_key")
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
        val AUTO_WP_RESTART_ON_MANUAL = booleanPreferencesKey("auto_wp_restart_on_manual")
        val AUTO_WP_REQUIRES_CHARGING = booleanPreferencesKey("auto_wp_requires_charging")
        val AUTO_WP_REQUIRES_WIFI = booleanPreferencesKey("auto_wp_requires_wifi")
        val AUTO_WP_REQUIRES_IDLE = booleanPreferencesKey("auto_wp_requires_idle")
        val ROTATE_ON_UNLOCK = booleanPreferencesKey("rotate_on_unlock")
        val ROTATE_ON_SCREEN_OFF = booleanPreferencesKey("rotate_on_screen_off")
        val AUTO_WP_DARKEN_PERCENT = intPreferencesKey("auto_wp_darken_percent")
        val AUTO_WP_NIGHT_VARIANT_ENABLED = booleanPreferencesKey("auto_wp_night_variant_enabled")
        val LAST_NIGHT_VARIANT_WP_LOCATOR = stringPreferencesKey("last_night_variant_wp_locator")
        val LAST_NIGHT_VARIANT_WP_TARGET = stringPreferencesKey("last_night_variant_wp_target")
        val LAST_NIGHT_VARIANT_WP_DARKEN_PERCENT = intPreferencesKey("last_night_variant_wp_darken_percent")
        val PREF_RESOLUTION = stringPreferencesKey("pref_resolution")
        val REDDIT_SUBS = stringPreferencesKey("reddit_subreddits")
        val REDDIT_VIDEO_SUBS = stringPreferencesKey("reddit_video_subreddits")
        val REDDIT_PROVIDER_ENABLED = booleanPreferencesKey("reddit_provider_enabled")
        val REDDIT_RSS_NEXT_ALLOWED_AT_MS = longPreferencesKey("reddit_rss_next_allowed_at_ms")
        val REDDIT_RSS_PAGE_METADATA_V3 = stringPreferencesKey("reddit_rss_page_metadata_v3")
        // YouTube sound search
        val YT_SOUND_RINGTONES = stringPreferencesKey("yt_sound_ringtones")
        val YT_SOUND_NOTIFICATIONS = stringPreferencesKey("yt_sound_notifications")
        val YT_SOUND_ALARMS = stringPreferencesKey("yt_sound_alarms")
        val YT_SOUND_BLOCKED = stringPreferencesKey("yt_sound_blocked")
        val YOUTUBE_PROVIDER_ENABLED = booleanPreferencesKey("youtube_provider_enabled")
        val YOUTUBE_PO_TOKEN_PROVIDER_URL = stringPreferencesKey("youtube_po_token_provider_url")
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
        val SCHEDULER_DAY_NIGHT_MODE = stringPreferencesKey("scheduler_day_night_mode")
        val SCHEDULER_DAY_START_HOUR = intPreferencesKey("scheduler_day_start_hour")
        val SCHEDULER_NIGHT_START_HOUR = intPreferencesKey("scheduler_night_start_hour")
        val AVOID_RECENT_REPEATS = booleanPreferencesKey("avoid_recent_repeats")
        val RECENT_ROTATION_IDS = stringPreferencesKey("recent_rotation_ids")
        // Video wallpaper
        val VIDEO_FPS_LIMIT = intPreferencesKey("video_fps_limit")
        val VIDEO_PLAYBACK_SPEED = floatPreferencesKey("video_playback_speed")
        val VIDEO_FPS_OVERLAY = booleanPreferencesKey("video_fps_overlay_enabled")
        val VIDEO_AUTO_BATTERY_SAVER = booleanPreferencesKey("video_auto_battery_saver")
        val STATIC_WALLPAPER_PRESENTATION = stringPreferencesKey("static_wallpaper_presentation")
        val STATIC_FIT_CANVAS_MODE = stringPreferencesKey("static_fit_canvas_mode")
        val STATIC_FIT_CANVAS_COLOR = intPreferencesKey("static_fit_canvas_color")
        val VIDEO_WALLPAPER_PRESENTATION = stringPreferencesKey("video_wallpaper_presentation")
        val VIDEO_FIT_CANVAS_MODE = stringPreferencesKey("video_fit_canvas_mode")
        val VIDEO_FIT_CANVAS_COLOR = intPreferencesKey("video_fit_canvas_color")
        // Effects / adaptive
        val ADAPTIVE_TINT = booleanPreferencesKey("adaptive_tint_enabled")
        val ADAPTIVE_TINT_INTENSITY = floatPreferencesKey("adaptive_tint_intensity")
        val WEATHER_EFFECTS = booleanPreferencesKey("weather_effects_enabled")
        val REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        val DARK_MODE_SWITCH = booleanPreferencesKey("dark_mode_auto_switch")
        val DARK_WALLPAPER_ID = stringPreferencesKey("dark_mode_wallpaper_id")
        val LIGHT_WALLPAPER_ID = stringPreferencesKey("light_mode_wallpaper_id")
        val LIVE_WALLPAPER_SHADER_PRESET = stringPreferencesKey("live_wallpaper_shader_preset")
        val USER_STYLES = stringPreferencesKey("user_styles")
        val WALLPAPER_STYLE_LEARNING_JSON = stringPreferencesKey("wallpaper_style_learning_json")
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
        val LIVE_WALLPAPER_COLORS_ENABLED = booleanPreferencesKey("live_wallpaper_colors_enabled")
        val WALLPAPER_CLOCK_OVERLAY_ENABLED = booleanPreferencesKey("wallpaper_clock_overlay_enabled")
        val WALLPAPER_CLOCK_OVERLAY_MODE = stringPreferencesKey("wallpaper_clock_overlay_mode")
        val WALLPAPER_CLOCK_OVERLAY_POSITION = stringPreferencesKey("wallpaper_clock_overlay_position")
        val SOUND_PROFILES_ENABLED = booleanPreferencesKey("sound_profiles_enabled")
        val SOUND_PROFILES_JSON = stringPreferencesKey("sound_profiles_json")
        val SOUND_PROFILE_LAST_APPLIED_ID = stringPreferencesKey("sound_profile_last_applied_id")
        val SOUND_SHUFFLE_POOLS_JSON = stringPreferencesKey("sound_shuffle_pools_json")
        val BATCH_DOWNLOAD_JSON = stringPreferencesKey("batch_download_json")
        val LAST_APPLIED_RINGTONE_URI = stringPreferencesKey("last_applied_ringtone_uri")
        val LAST_APPLIED_NOTIFICATION_URI = stringPreferencesKey("last_applied_notification_uri")
        val LAST_APPLIED_ALARM_URI = stringPreferencesKey("last_applied_alarm_uri")
    }
}
