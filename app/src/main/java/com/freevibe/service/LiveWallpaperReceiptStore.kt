package com.freevibe.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class LiveWallpaperReceipt(
    val engine: String,
    val lastSurfaceCreatedUtc: String? = null,
    val lastSurfaceDestroyedUtc: String? = null,
    val lastVisibleUtc: String? = null,
    val lastHiddenUtc: String? = null,
    val lastDrawUtc: String? = null,
    val lastErrorUtc: String? = null,
    val lastErrorMessage: String? = null,
    val lastRecoveryUtc: String? = null,
    val lastRecoveryAction: String? = null,
    val mediaPath: String? = null,
    val surfaceRecreationCount: Int = 0,
) {
    val isStale: Boolean
        get() {
            val lastDraw = lastDrawUtc ?: return lastVisibleUtc != null
            val drawMs = parseUtcMs(lastDraw) ?: return false
            val now = System.currentTimeMillis()
            return (now - drawMs) > STALE_THRESHOLD_MS
        }

    private companion object {
        const val STALE_THRESHOLD_MS = 5L * 60 * 1000
    }
}

@Singleton
class LiveWallpaperReceiptStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordSurfaceCreated(engine: String, mediaPath: String? = null) {
        val prefix = keyPrefix(engine)
        val count = prefs.getInt("${prefix}surface_recreation_count", 0)
        prefs.edit()
            .putString("${prefix}last_surface_created_utc", utcNow())
            .putInt("${prefix}surface_recreation_count", count + 1)
            .apply {
                if (mediaPath != null) putString("${prefix}media_path", mediaPath)
            }
            .apply()
    }

    fun recordSurfaceDestroyed(engine: String) {
        val prefix = keyPrefix(engine)
        prefs.edit()
            .putString("${prefix}last_surface_destroyed_utc", utcNow())
            .apply()
    }

    fun recordVisibilityChanged(engine: String, visible: Boolean) {
        val prefix = keyPrefix(engine)
        val key = if (visible) "${prefix}last_visible_utc" else "${prefix}last_hidden_utc"
        prefs.edit().putString(key, utcNow()).apply()
    }

    fun recordDraw(engine: String) {
        val prefix = keyPrefix(engine)
        prefs.edit().putString("${prefix}last_draw_utc", utcNow()).apply()
    }

    fun recordError(engine: String, error: String) {
        val prefix = keyPrefix(engine)
        prefs.edit()
            .putString("${prefix}last_error_utc", utcNow())
            .putString("${prefix}last_error_message", error.take(200))
            .apply()
    }

    fun recordRecovery(engine: String, action: String) {
        val prefix = keyPrefix(engine)
        prefs.edit()
            .putString("${prefix}last_recovery_utc", utcNow())
            .putString("${prefix}last_recovery_action", action.take(200))
            .apply()
    }

    fun read(engine: String): LiveWallpaperReceipt {
        val prefix = keyPrefix(engine)
        return LiveWallpaperReceipt(
            engine = engine,
            lastSurfaceCreatedUtc = prefs.getString("${prefix}last_surface_created_utc", null),
            lastSurfaceDestroyedUtc = prefs.getString("${prefix}last_surface_destroyed_utc", null),
            lastVisibleUtc = prefs.getString("${prefix}last_visible_utc", null),
            lastHiddenUtc = prefs.getString("${prefix}last_hidden_utc", null),
            lastDrawUtc = prefs.getString("${prefix}last_draw_utc", null),
            lastErrorUtc = prefs.getString("${prefix}last_error_utc", null),
            lastErrorMessage = prefs.getString("${prefix}last_error_message", null),
            lastRecoveryUtc = prefs.getString("${prefix}last_recovery_utc", null),
            lastRecoveryAction = prefs.getString("${prefix}last_recovery_action", null),
            mediaPath = prefs.getString("${prefix}media_path", null),
            surfaceRecreationCount = prefs.getInt("${prefix}surface_recreation_count", 0),
        )
    }

    fun readAll(): List<LiveWallpaperReceipt> = ENGINES.map { read(it) }

    private fun keyPrefix(engine: String): String =
        engine.filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "unknown" } + "."

    private fun utcNow(): String = checkNotNull(UTC_FORMAT.get()).format(Date())

    companion object {
        const val ENGINE_VIDEO = "video"
        const val ENGINE_WEATHER = "weather"
        const val ENGINE_PARALLAX = "parallax"
        val ENGINES = listOf(ENGINE_VIDEO, ENGINE_WEATHER, ENGINE_PARALLAX)

        private const val PREFS_NAME = "live_wallpaper_receipts"
        private val UTC_FORMAT: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }

        fun create(context: Context): LiveWallpaperReceiptStore =
            LiveWallpaperReceiptStore(context.applicationContext)
    }
}

private fun parseUtcMs(utc: String): Long? = try {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(utc)?.time
} catch (_: Exception) {
    null
}
