package com.freevibe.ui.screens.wallpapers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.freevibe.R
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperAction
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.model.canUseWallpaperAction
import com.freevibe.data.model.FitCanvasMode
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.WALLPAPER_PRESENTATION_FIT
import com.freevibe.ui.components.FitCanvasControls
import com.freevibe.ui.components.FitCanvasMedia
import com.freevibe.ui.theme.AuraShapes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Full-screen mockup of the user's home / lock screen with this wallpaper behind it, so they
 * can decide whether to commit before actually calling WallpaperManager.setBitmap().
 *
 * Design:
 * - Wallpaper fills the background.
 * - A segmented "Lock / Home" toggle at the top switches between two mock layouts.
 * - Lock mock: large clock + date + lock icon.
 * - Home mock: status bar, 5×5 placeholder icon grid, a dock row.
 * - Clock / icon tint is pulled from ColorExtractor so it stays legible on any wallpaper.
 * - Bottom bar: Cancel, Apply to Lock, Apply to Home, Apply to Both.
 *
 * We deliberately do NOT render real app icons (PackageManager noise, attribution concerns).
 * Placeholder silhouettes give a faithful impression of layout without pretending to be
 * the user's actual home screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPreviewScreen(
    wallpaper: Wallpaper,
    onBack: () -> Unit,
    onApply: (WallpaperTarget, FitCanvasStyle?) -> Unit,
    viewModel: WallpapersViewModel = hiltViewModel(),
) {
    val palette by viewModel.colorPalette.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val presentation by viewModel.staticWallpaperPresentation.collectAsStateWithLifecycle()
    val canvasMode by viewModel.staticFitCanvasMode.collectAsStateWithLifecycle()
    val canvasColor by viewModel.staticFitCanvasColor.collectAsStateWithLifecycle()
    val canvasStyle = remember(canvasMode, canvasColor) {
        FitCanvasStyle(FitCanvasMode.fromPreference(canvasMode), canvasColor).normalized()
    }
    val canApply = remember(wallpaper) { canApplyFromWallpaperPreview(wallpaper) }
    LaunchedEffect(wallpaper.fullUrl) {
        viewModel.extractColors(wallpaper.fullUrl)
    }

    var mode by remember { mutableStateOf(PreviewMode.LOCK) }
    val wallpaperPreviewContentDescription = wallpaper.category.ifBlank {
        stringResource(R.string.preview_wallpaper_cd)
    }

    // Tint for mock overlay text — prefer a light tint on dark wallpapers and dark-on-light
    // when the wallpaper is bright. The ColorExtractor dominant color gives us that signal.
    val overlayTint = overlayTintFor(palette?.dominantColor)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                title = { Text(stringResource(R.string.detail_preview)) },
                actions = {
                    // Lock / Home segmented toggle
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(AuraShapes.Medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(2.dp)
                            .semantics { selectableGroup() },
                    ) {
                        PreviewModeChip(label = stringResource(R.string.common_lock), active = mode == PreviewMode.LOCK) { mode = PreviewMode.LOCK }
                        PreviewModeChip(label = stringResource(R.string.common_home), active = mode == PreviewMode.HOME) { mode = PreviewMode.HOME }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            PreviewApplyBar(
                isApplying = state.isApplying,
                canApply = canApply,
                presentation = presentation,
                canvasStyle = canvasStyle,
                onPresentationChange = { selected ->
                    viewModel.setStaticFitCanvasPreferences(selected, canvasStyle)
                },
                onCanvasStyleChange = { selected ->
                    viewModel.setStaticFitCanvasPreferences(presentation, selected)
                },
                onApply = onApply,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            // Wallpaper fills the full screen, ignoring the scaffold padding, so we see it
            // behind the translucent top/bottom bars.
            FitCanvasMedia(
                model = wallpaper.fullUrl,
                contentDescription = wallpaperPreviewContentDescription,
                presentation = presentation,
                style = canvasStyle,
                // Fit Canvas derives its preview swatch with the same sampler
                // used by apply. The separate Material You palette still powers
                // the mock system UI below.
                dominantColor = null,
                modifier = Modifier.fillMaxSize(),
                onLoaded = { viewModel.onWallpaperImageLoaded(wallpaper.fullUrl) },
            )
            // Light vignette so the mock text stays readable.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.25f),
                            0.4f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.25f),
                        ),
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (mode) {
                    PreviewMode.LOCK -> LockMock(overlayTint = overlayTint)
                    PreviewMode.HOME -> HomeMock(overlayTint = overlayTint)
                }
            }
        }
    }
}

private enum class PreviewMode { LOCK, HOME }

@Composable
private fun PreviewModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = AuraShapes.Medium,
        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = Modifier.semantics { selected = active; role = androidx.compose.ui.semantics.Role.Tab },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LockMock(overlayTint: Color) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            val msUntilNextMinute = (60 - cal.get(Calendar.SECOND)) * 1000L - cal.get(Calendar.MILLISECOND)
            kotlinx.coroutines.delay(msUntilNextMinute.coerceAtLeast(1000L))
            now = Date()
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val statusText = stringResource(R.string.preview_mock_status_100)
    val use24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val timeFormat = remember(use24Hour) {
        SimpleDateFormat(if (use24Hour) "H:mm" else "h:mm", Locale.getDefault())
    }
    val dateFormat = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        // Status bar faux row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = timeFormat.format(now).take(5),
                style = MaterialTheme.typography.labelSmall,
                color = overlayTint,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = overlayTint,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(80.dp))
        // Giant clock
        Text(
            text = timeFormat.format(now),
            color = overlayTint,
            fontSize = 92.sp,
            fontWeight = FontWeight.Thin,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = dateFormat.format(now),
            color = overlayTint.copy(alpha = 0.85f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeMock(overlayTint: Color) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            val msUntilNextMinute = (60 - cal.get(Calendar.SECOND)) * 1000L - cal.get(Calendar.MILLISECOND)
            kotlinx.coroutines.delay(msUntilNextMinute.coerceAtLeast(1000L))
            now = Date()
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val use24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val statusText = stringResource(R.string.preview_mock_status_100)
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Status bar with a small time readout on the left.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val timeFormat = remember(use24Hour) {
                SimpleDateFormat(if (use24Hour) "H:mm" else "h:mm", Locale.getDefault())
            }
            Text(
                text = timeFormat.format(now),
                style = MaterialTheme.typography.labelSmall,
                color = overlayTint,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = overlayTint,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.weight(0.3f))
        // Page indicator dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == 1) 8.dp else 6.dp)
                        .clip(AuraShapes.Hairline)
                        .background(overlayTint.copy(alpha = if (i == 1) 0.8f else 0.4f)),
                )
            }
        }
        // Icon grid (4 rows × 5 columns). Each cell is a silhouette dot + a short label.
        val mockLabels = stringArrayResource(R.array.wallpaper_preview_mock_apps)
        repeat(4) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(5) { col ->
                    MockIcon(
                        tint = overlayTint.copy(alpha = 0.88f),
                        label = mockLabels[(row * 5 + col) % mockLabels.size],
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // Dock row (5 icons on a translucent background)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .clip(AuraShapes.Medium)
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(5) {
                    MockIcon(tint = overlayTint, label = null)
                }
            }
        }
    }
}

@Composable
private fun MockIcon(tint: Color, label: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(AuraShapes.Medium)
                .background(tint.copy(alpha = 0.28f))
                .padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(AuraShapes.Medium)
                    .background(tint.copy(alpha = 0.55f)),
            )
        }
        if (!label.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = tint,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PreviewApplyBar(
    isApplying: Boolean,
    canApply: Boolean,
    presentation: String,
    canvasStyle: FitCanvasStyle,
    onPresentationChange: (String) -> Unit,
    onCanvasStyleChange: (FitCanvasStyle) -> Unit,
    onApply: (WallpaperTarget, FitCanvasStyle?) -> Unit,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                stringResource(R.string.fit_canvas_controls_title),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
            )
            FitCanvasControls(
                presentation = presentation,
                style = canvasStyle,
                onPresentationChange = onPresentationChange,
                onStyleChange = onCanvasStyleChange,
            )
            if (presentation == WALLPAPER_PRESENTATION_FIT) {
                Text(
                    stringResource(R.string.fit_canvas_fit_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.66f),
                )
                Spacer(Modifier.height(6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val appliedStyle = canvasStyle.takeIf { presentation == WALLPAPER_PRESENTATION_FIT }
                ApplyActionButton(
                    label = stringResource(R.string.common_lock),
                    onClick = { onApply(WallpaperTarget.LOCK, appliedStyle) },
                    enabled = canApply && !isApplying,
                    tonal = true,
                    modifier = Modifier.weight(1f),
                )
                ApplyActionButton(
                    label = stringResource(R.string.common_home),
                    onClick = { onApply(WallpaperTarget.HOME, appliedStyle) },
                    enabled = canApply && !isApplying,
                    tonal = true,
                    modifier = Modifier.weight(1f),
                )
                ApplyActionButton(
                    label = stringResource(R.string.common_both),
                    onClick = { onApply(WallpaperTarget.BOTH, appliedStyle) },
                    enabled = canApply && !isApplying,
                    tonal = false,
                    modifier = Modifier.weight(1.2f),
                )
            }
        }
    }
}

internal fun canApplyFromWallpaperPreview(wallpaper: Wallpaper): Boolean =
    wallpaper.canUseWallpaperAction(WallpaperAction.APPLY)

@Composable
private fun ApplyActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    tonal: Boolean,
    modifier: Modifier = Modifier,
) {
    if (tonal) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            shape = AuraShapes.Medium,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            shape = AuraShapes.Medium,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Pick a legible overlay tint (white on dark wallpapers, near-black on light ones).
 * Falls back to white if we haven't extracted a dominant color yet.
 */
private fun overlayTintFor(dominant: Int?): Color {
    if (dominant == null) return Color.White
    val r = (dominant shr 16) and 0xFF
    val g = (dominant shr 8) and 0xFF
    val b = dominant and 0xFF
    // Perceived luminance (Rec. 601).
    val luma = 0.299 * r + 0.587 * g + 0.114 * b
    return if (luma < 140) Color.White else Color(0xFF1A1A1A)
}
