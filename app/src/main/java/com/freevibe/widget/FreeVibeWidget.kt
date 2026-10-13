package com.freevibe.widget

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil3.imageLoader
import coil3.request.allowHardware
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.flow.first
import com.freevibe.R
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.repository.WallpaperRepository
import com.freevibe.data.repository.RotationExclusionRepository
import com.freevibe.service.WallpaperApplier
import com.freevibe.service.WallpaperHistoryManager
import com.freevibe.util.appLaunchIntent
import com.freevibe.util.tryStartActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

// ── Colors ────────────────────────────────────────────────────────

private val WidgetBg = ColorProvider(Color(0xFF0A0A0F))
private val Primary = ColorProvider(Color(0xFF7C5CFC))
private val Secondary = ColorProvider(Color(0xFF00D4AA))
private val Tertiary = ColorProvider(Color(0xFFFF6B9D))
private val SurfaceTone = ColorProvider(Color(0xFF1A1A24))
private val TextDim = ColorProvider(Color(0xFF888888))
private val White = ColorProvider(Color.White)
private val Scrim = ColorProvider(Color(0xCC000000))

private const val WIDGET_PREFS = "freevibe_widget"
private const val LAST_SHUFFLE_KEY = "last_shuffle_time"
private const val SHUFFLE_COUNT_KEY = "shuffle_count"
private const val OPEN_FAILED_AT_KEY = "open_failed_at"
internal const val WIDGET_OPEN_FAILED_VISIBLE_MS = 10 * 60 * 1000L

// ── Widget ────────────────────────────────────────────────────────

class FreeVibeWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(200.dp, 110.dp)
        private val LARGE = DpSize(200.dp, 200.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))
    override val previewSizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val lastShuffle = prefs.getLong(LAST_SHUFFLE_KEY, 0)
        val shuffleCount = prefs.getInt(SHUFFLE_COUNT_KEY, 0)
        val openFailedMessage = context.getString(R.string.widget_open_failed)
            .takeIf { widgetOpenFailedVisible(prefs.getLong(OPEN_FAILED_AT_KEY, 0L), System.currentTimeMillis()) }

        // Load most-recent wallpaper thumbnail as the widget background. Null if the user
        // hasn't applied anything yet (fresh install) — widget falls back to the dark
        // gradient in that case.
        val bgBitmap = loadCurrentWallpaperThumbnail(context)

        // Adaptive tinting — palette cached by WallpaperHistoryManager on every apply.
        // Falls back to the static brand palette when a component would otherwise be black
        // (= no palette cached yet).
        val tintVibrant = prefs.getInt("tint_vibrant", 0)
            .takeIf { it != 0 }?.let { ColorProvider(Color(it)) } ?: Primary
        // Prefer the smart-fallback accent (added v6.11.0) — guards against
        // dim/gray vibrant_light on cartoon images. Falls back to the legacy
        // vibrant_light key for wallpapers cached before the upgrade, then
        // Secondary for first-launch / no-palette state.
        val tintAccent = (prefs.getInt("tint_accent", 0)
            .takeIf { it != 0 }
            ?: prefs.getInt("tint_vibrant_light", 0).takeIf { it != 0 })
            ?.let { ColorProvider(Color(it)) } ?: Secondary

        provideContent {
            WidgetContent(
                size = LocalSize.current,
                lastShuffleTime = lastShuffle,
                shuffleCount = shuffleCount,
                backgroundBitmap = bgBitmap,
                primaryTint = tintVibrant,
                accentTint = tintAccent,
                openFailedMessage = openFailedMessage,
            )
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            WidgetContent(
                size = LocalSize.current,
                lastShuffleTime = 0L,
                shuffleCount = 12,
                backgroundBitmap = null,
                primaryTint = Primary,
                accentTint = Secondary,
            )
        }
    }

    private suspend fun loadCurrentWallpaperThumbnail(context: Context): Bitmap? {
        return try {
            val ep = getEntryPoint(context)
            val entry = ep.wallpaperHistoryManager().mostRecent().first() ?: return null
            val url = entry.thumbnailUrl.ifBlank { entry.fullUrl }
            if (url.isBlank()) return null
            // Widget RemoteViews have a ~5 MB payload limit; downsample hard.
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(WIDGET_BG_MAX_PX)
                .allowHardware(false) // hardware bitmaps can't cross process boundaries
                .build()
            val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
            result.image.toBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

private const val WIDGET_BG_MAX_PX = 400

@Composable
private fun WidgetContent(
    size: DpSize,
    lastShuffleTime: Long,
    shuffleCount: Int,
    backgroundBitmap: Bitmap? = null,
    primaryTint: ColorProvider = Primary,
    accentTint: ColorProvider = Secondary,
    openFailedMessage: String? = null,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(12.dp),
    ) {
        // Current wallpaper as the widget background (with a dark scrim for text legibility).
        // Tap the image area to jump into the detail screen for that wallpaper.
        if (backgroundBitmap != null) {
            Image(
                provider = ImageProvider(backgroundBitmap),
                contentDescription = "Current wallpaper",
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(12.dp)
                    .clickable(actionRunCallback<OpenCurrentWallpaperAction>()),
            )
            // Dark scrim so the buttons + text remain readable on bright wallpapers.
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Scrim)
                    .cornerRadius(12.dp),
            ) {}
        }
        Box(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    text = "Aura",
                    style = TextStyle(
                        color = accentTint,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                )
                if (shuffleCount > 0 && size.width >= 200.dp) {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = "$shuffleCount shuffled",
                        style = TextStyle(color = TextDim, fontSize = 10.sp),
                    )
                }
            }

            Spacer(GlanceModifier.height(6.dp))

            // A tap that couldn't open Aura has no other place to say so.
            if (openFailedMessage != null) {
                Text(
                    text = openFailedMessage,
                    style = TextStyle(color = Tertiary, fontSize = 10.sp),
                    maxLines = 2,
                )
                Spacer(GlanceModifier.height(4.dp))
            }

            // Last shuffle timestamp — honor the device's 12/24-hour setting.
            if (lastShuffleTime > 0 && size.height >= 200.dp) {
                val timeStr = android.text.format.DateFormat.getTimeFormat(LocalContext.current)
                    .format(Date(lastShuffleTime))
                Text(
                    text = "Last: $timeStr",
                    style = TextStyle(color = TextDim, fontSize = 10.sp),
                )
                Spacer(GlanceModifier.height(4.dp))
            }

            // Shuffle button
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(primaryTint)
                    .cornerRadius(12.dp)
                    .clickable(actionRunCallback<ShuffleWallpaperAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Shuffle",
                    style = TextStyle(
                        color = White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    ),
                )
            }

            // Home/Lock/Skip quick actions for wider widgets
            if (size.width >= 200.dp) {
                Spacer(GlanceModifier.height(6.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                ) {
                    QuickActionBtn("Home", GlanceModifier.defaultWeight(), actionRunCallback<ApplyHomeAction>())
                    Spacer(GlanceModifier.width(4.dp))
                    QuickActionBtn("Lock", GlanceModifier.defaultWeight(), actionRunCallback<ApplyLockAction>())
                    Spacer(GlanceModifier.width(4.dp))
                    QuickActionBtn("Faves", GlanceModifier.defaultWeight(), actionRunCallback<OpenFavoritesAction>(), Tertiary)
                }
                Spacer(GlanceModifier.height(4.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                ) {
                    QuickActionBtn("Pixabay", GlanceModifier.defaultWeight(), actionRunCallback<ShufflePixabayAction>(), ColorProvider(Color(0xFF00AB6C)))
                    Spacer(GlanceModifier.width(4.dp))
                    QuickActionBtn("Bing", GlanceModifier.defaultWeight(), actionRunCallback<ShuffleBingAction>(), ColorProvider(Color(0xFF2B7FFF)))
                }
            }

            // Open app link for taller widgets
            if (size.height >= 200.dp) {
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    text = "Tap to open app",
                    style = TextStyle(color = TextDim, fontSize = 11.sp),
                    modifier = GlanceModifier.clickable(actionRunCallback<OpenAppAction>()),
                )
            }
        }
        }
    }
}

@Composable
private fun QuickActionBtn(
    label: String,
    modifier: GlanceModifier,
    action: androidx.glance.action.Action,
    color: ColorProvider = Secondary,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .background(SurfaceTone)
            .cornerRadius(8.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(color = color, fontSize = 11.sp),
        )
    }
}

// ── Hilt EntryPoint ───────────────────────────────────────────────

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun wallpaperRepository(): WallpaperRepository
    fun wallpaperApplier(): WallpaperApplier
    fun wallpaperHistoryManager(): WallpaperHistoryManager
    fun rotationExclusionRepository(): RotationExclusionRepository
}

private fun getEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

private fun updateWidgetStats(context: Context) {
    val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
    prefs.edit()
        .putLong(LAST_SHUFFLE_KEY, System.currentTimeMillis())
        .putInt(SHUFFLE_COUNT_KEY, prefs.getInt(SHUFFLE_COUNT_KEY, 0) + 1)
        .apply()
}

// ── Actions ───────────────────────────────────────────────────────

class ShuffleWallpaperAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (applyRandom(context, WallpaperTarget.BOTH)) {
            updateWidgetStats(context)
            FreeVibeWidget().updateAll(context)
        }
    }
}

class ApplyHomeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (applyRandom(context, WallpaperTarget.HOME)) {
            updateWidgetStats(context)
            FreeVibeWidget().updateAll(context)
        }
    }
}

class ApplyLockAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (applyRandom(context, WallpaperTarget.LOCK)) {
            updateWidgetStats(context)
            FreeVibeWidget().updateAll(context)
        }
    }
}

class OpenFavoritesAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        openFromWidget(context, appLaunchIntent(context).putExtra("navigate_to", "favorites"), "favorites")
    }
}

/**
 * Tap-on-current-wallpaper → deep-link into the detail screen for that wallpaper.
 * Uses the same EXTRA_DAILY_WALLPAPER_* extras that MainActivity already parses for the
 * daily wallpaper notification, so no new intent-handling plumbing is needed.
 */
class OpenCurrentWallpaperAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = try {
            getEntryPoint(context).wallpaperHistoryManager().mostRecent().first()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
        val intent = appLaunchIntent(context)
        if (entry != null) {
            intent.putExtra("daily_wallpaper_id", entry.wallpaperId)
            intent.putExtra("daily_wallpaper_url", entry.fullUrl)
            intent.putExtra("daily_wallpaper_thumb", entry.thumbnailUrl)
            intent.putExtra("daily_wallpaper_source", entry.source)
            intent.putExtra("daily_wallpaper_width", entry.width)
            intent.putExtra("daily_wallpaper_height", entry.height)
        }
        openFromWidget(context, intent, "current wallpaper")
    }
}

class ShufflePixabayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (applyFromSource(context, "pixabay", WallpaperTarget.BOTH)) {
            updateWidgetStats(context)
            FreeVibeWidget().updateAll(context)
        }
    }
}

class ShuffleBingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (applyFromSource(context, "bing", WallpaperTarget.BOTH)) {
            updateWidgetStats(context)
            FreeVibeWidget().updateAll(context)
        }
    }
}

class OpenAppAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        openFromWidget(context, appLaunchIntent(context), "app")
    }
}

/** Opens Aura from a widget tap and refreshes the widget when its failure line appears or clears. */
private suspend fun openFromWidget(context: Context, intent: Intent, tag: String) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = context.tryStartActivity(intent, "widget $tag")
    val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
    if (recordWidgetLaunch(prefs, opened, System.currentTimeMillis())) {
        FreeVibeWidget().updateAll(context)
    }
}

/**
 * A widget has no snackbar and a Toast from a background tap is easy to miss, so a failed open is
 * recorded for the widget to show, and the next open that works clears it. Returns true when the
 * widget's visible state changed.
 */
internal fun recordWidgetLaunch(prefs: SharedPreferences, opened: Boolean, nowMs: Long): Boolean {
    val failedAt = prefs.getLong(OPEN_FAILED_AT_KEY, 0L)
    if (opened) {
        if (failedAt == 0L) return false
        prefs.edit().remove(OPEN_FAILED_AT_KEY).apply()
        return true
    }
    prefs.edit().putLong(OPEN_FAILED_AT_KEY, nowMs).apply()
    return true
}

internal fun widgetOpenFailedVisible(failedAtMs: Long, nowMs: Long): Boolean =
    failedAtMs > 0L && nowMs - failedAtMs in 0..WIDGET_OPEN_FAILED_VISIBLE_MS

private suspend fun applyFromSource(context: Context, source: String, target: WallpaperTarget): Boolean {
    // Immediate feedback — a widget tap that kicks off a 3–10 s network call with no visible
    // response used to feel unresponsive. Show the toast before the IO hop so the user knows
    // the tap was registered.
    withContext(Dispatchers.Main) { Toast.makeText(context, "Shuffling…", Toast.LENGTH_SHORT).show() }
    return withContext(Dispatchers.IO) {
        try {
            val ep = getEntryPoint(context)
            val items = when (source) {
                "pixabay" -> ep.wallpaperRepository().getPixabay(page = (1..5).random()).items
                "bing" -> ep.wallpaperRepository().getBingDaily(page = 1).items
                else -> ep.wallpaperRepository().getDiscover(page = 1).items
            }
            val available = ep.rotationExclusionRepository().filter(items)
            val wp = available.candidates.randomOrNull()
            if (wp == null) {
                val message = if (available.allExcluded) {
                    context.getString(com.freevibe.R.string.rotation_all_excluded_short)
                } else {
                    context.getString(com.freevibe.R.string.rotation_no_wallpapers_available)
                }
                withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
                return@withContext false
            }
            ep.wallpaperApplier().applyFromUrl(wp.fullUrl, target).fold(
                onSuccess = {
                    ep.wallpaperHistoryManager().record(wp, target)
                    com.freevibe.service.AutoWallpaperWorker.restartCountdownAfterManualChange(context)
                    true
                },
                onFailure = { error ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed: ${error.message?.take(50)}", Toast.LENGTH_SHORT).show()
                    }
                    false
                },
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            withContext(Dispatchers.Main) { Toast.makeText(context, "Failed: ${e.message?.take(50)}", Toast.LENGTH_SHORT).show() }
            false
        }
    }
}

private suspend fun applyRandom(context: Context, target: WallpaperTarget): Boolean {
    withContext(Dispatchers.Main) { Toast.makeText(context, "Shuffling…", Toast.LENGTH_SHORT).show() }
    return withContext(Dispatchers.IO) {
        try {
            val ep = getEntryPoint(context)
            val available = ep.rotationExclusionRepository().filter(
                ep.wallpaperRepository().getWallhaven(page = (1..5).random()).items,
            )
            val wp = available.candidates.randomOrNull()
            if (wp == null) {
                val message = if (available.allExcluded) {
                    context.getString(com.freevibe.R.string.rotation_all_excluded_short)
                } else {
                    context.getString(com.freevibe.R.string.rotation_no_wallpapers_available)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }
            ep.wallpaperApplier().applyFromUrl(wp.fullUrl, target).fold(
                onSuccess = {
                    ep.wallpaperHistoryManager().record(wp, target)
                    com.freevibe.service.AutoWallpaperWorker.restartCountdownAfterManualChange(context)
                    true
                },
                onFailure = { error ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Shuffle failed: ${error.message?.take(50)}", Toast.LENGTH_SHORT).show()
                    }
                    false
                },
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Shuffle failed: ${e.message?.take(50)}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }
}

// ── Receiver ──────────────────────────────────────────────────────

class FreeVibeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FreeVibeWidget()
}
