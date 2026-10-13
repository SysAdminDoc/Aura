package com.freevibe.ui.screens.editor

import android.content.ComponentName
import androidx.annotation.StringRes
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.service.DepthBackgroundStyle
import com.freevibe.service.DepthFrameStyle
import com.freevibe.service.ParallaxWallpaperService
import com.freevibe.ui.LiveWallpaperLaunchMode
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStatusBanner
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.launchLiveWallpaperPicker
import com.freevibe.ui.theme.AuraShapes
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperEditorScreen(
    wallpaperId: String,
    fallbackWallpaper: Wallpaper? = null,
    onBack: () -> Unit,
    recoveryViewModel: com.freevibe.ui.screens.wallpapers.WallpapersViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    viewModel: WallpaperEditorViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Reading a string off LocalContext is not a composition read. LocalResources is.
    val resources = LocalResources.current
    val editorIdentityKey = remember(wallpaperId, fallbackWallpaper?.source, fallbackWallpaper?.fullUrl) {
        listOf(
            wallpaperId,
            fallbackWallpaper?.source?.name.orEmpty(),
            fallbackWallpaper?.fullUrl.orEmpty(),
        ).joinToString("|")
    }
    var selectedFilter by remember(editorIdentityKey) { mutableIntStateOf(R.string.editor_filter_brightness) }
    val snackbarHostState = remember { SnackbarHostState() }
    var selectionResolved by remember(editorIdentityKey) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(wallpaperId, fallbackWallpaper?.source, fallbackWallpaper?.fullUrl) {
        val wallpaper = fallbackWallpaper?.let {
            recoveryViewModel.resolveWallpaper(
                id = wallpaperId,
                source = it.source,
                fullUrl = it.fullUrl,
            ) ?: it
        } ?: recoveryViewModel.resolveWallpaper(wallpaperId)
        selectionResolved = wallpaper?.let { viewModel.loadWallpaper(it) } ?: false
    }

    LaunchedEffect(state.success) {
        state.success?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(resources.getString(R.string.common_error_format, it))
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbarHostState.showSnackbar(it); viewModel.clearNotice() }
    }
    val parallaxDirectMessage = stringResource(R.string.settings_feedback_parallax_direct)
    val parallaxChooserMessage = stringResource(R.string.settings_feedback_parallax_chooser)
    val parallaxManualMessage = stringResource(R.string.settings_feedback_parallax_manual)
    LaunchedEffect(state.pendingParallaxLaunch) {
        if (state.pendingParallaxLaunch) {
            val message = when (
                launchLiveWallpaperPicker(
                    context = context,
                    serviceComponent = ComponentName(context, ParallaxWallpaperService::class.java),
                    tag = "WallpaperEditorDepth",
                )
            ) {
                LiveWallpaperLaunchMode.DIRECT -> parallaxDirectMessage
                LiveWallpaperLaunchMode.CHOOSER -> parallaxChooserMessage
                null -> parallaxManualMessage
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearPendingParallaxLaunch()
        }
    }

    data class EditorPreset(@StringRes val nameRes: Int, val b: Float, val c: Float, val s: Float, val bl: Float,
                             val v: Float = 0f, val g: Float = 0f, val a: Float = 0f, val w: Float = 0f)
    val presets = listOf(
        EditorPreset(R.string.editor_preset_amoled, -20f, 1.3f, 1.1f, 0f, v = 0.3f, a = 0.7f),
        EditorPreset(R.string.editor_preset_warm, 15f, 1.1f, 1.3f, 0f, w = 25f),
        EditorPreset(R.string.editor_preset_cool, -10f, 1.1f, 0.8f, 0f, w = -20f),
        EditorPreset(R.string.editor_preset_vivid, 5f, 1.3f, 1.6f, 0f),
        EditorPreset(R.string.editor_preset_cinematic, -5f, 1.4f, 0.7f, 0f, v = 0.4f, g = 0.15f, w = 10f),
        EditorPreset(R.string.editor_preset_dreamy, 20f, 0.9f, 1.1f, 8f, v = 0.2f),
        EditorPreset(R.string.editor_preset_bw, 0f, 1.2f, 0f, 0f),
        EditorPreset(R.string.editor_preset_noir, -15f, 1.5f, 0f, 0f, v = 0.5f, g = 0.2f, a = 0.4f),
        EditorPreset(R.string.editor_preset_film, 5f, 1.1f, 0.9f, 0f, g = 0.25f, v = 0.15f, w = 8f),
        EditorPreset(R.string.editor_preset_moody, -10f, 1.2f, 0.6f, 2f, v = 0.35f, w = -10f),
    )

    val filters = listOf(
        FilterControl(R.string.editor_filter_brightness, Icons.Default.BrightnessHigh, state.brightness, -100f..100f) { viewModel.updateBrightness(it) },
        FilterControl(R.string.editor_filter_contrast, Icons.Default.Contrast, state.contrast, 0.5f..2f) { viewModel.updateContrast(it) },
        FilterControl(R.string.editor_filter_saturation, Icons.Default.ColorLens, state.saturation, 0f..2f) { viewModel.updateSaturation(it) },
        FilterControl(R.string.editor_filter_warmth, Icons.Default.Thermostat, state.warmth, -50f..50f) { viewModel.updateWarmth(it) },
        FilterControl(R.string.editor_filter_blur, Icons.Default.BlurOn, state.blurRadius, 0f..25f) { viewModel.updateBlur(it) },
        FilterControl(R.string.editor_filter_amoled, Icons.Default.DarkMode, state.amoledCrush, 0f..1f) { viewModel.updateAmoledCrush(it) },
        FilterControl(R.string.editor_filter_vignette, Icons.Default.Vignette, state.vignette, 0f..1f) { viewModel.updateVignette(it) },
        FilterControl(R.string.editor_filter_grain, Icons.Default.Grain, state.grain, 0f..1f) { viewModel.updateGrain(it) },
    )

    // NX-13: unsaved-changes guard. Editor filters are non-trivial work; backing
    // out unintentionally costs the user a careful tuning pass. Default-state
    // detection runs against the same defaults declared in [EditorState].
    val hasUnsavedChanges = state.brightness != 0f ||
        state.contrast != 1f ||
        state.saturation != 1f ||
        state.warmth != 0f ||
        state.blurRadius != 0f ||
        state.amoledCrush != 0f ||
        state.vignette != 0f ||
        state.grain != 0f ||
        state.depthBackgroundStyle != DepthBackgroundStyle.BLUR ||
        state.depthFrameStyle != DepthFrameStyle.NONE ||
        state.depthSubjectScale != 1f ||
        state.overlayLayers.isNotEmpty() ||
        (state.editedBitmap != null && state.editedBitmap !== state.originalBitmap)
    var showDiscardConfirm by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = hasUnsavedChanges && !state.isApplying) {
        showDiscardConfirm = true
    }
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.editor_wallpaper_discard_title)) },
            text = { Text(stringResource(R.string.editor_wallpaper_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    viewModel.resetAll()
                    onBack()
                }) { Text(stringResource(R.string.common_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.common_keep_editing)) }
            },
            shape = AuraShapes.Medium,
        )
    }

    Scaffold(
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_wallpaper_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges && !state.isApplying) showDiscardConfirm = true else onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetAll() }) {
                        Text(stringResource(R.string.common_reset), color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (selectionResolved == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.editor_wallpaper_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        if (selectionResolved == false) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                AuraStateCard(
                    icon = Icons.Default.BrokenImage,
                    title = stringResource(R.string.editor_wallpaper_unavailable_title),
                    description = stringResource(R.string.editor_wallpaper_unavailable_body),
                    tone = MaterialTheme.colorScheme.tertiary,
                    primaryAction = AuraStateAction(stringResource(R.string.editor_wallpaper_unavailable_action), Icons.AutoMirrored.Filled.ArrowBack, onBack),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLoadingImage -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.editor_wallpaper_loading_image), color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    state.editedBitmap != null -> {
                        val editedBitmap = state.editedBitmap ?: return@Box
                        WallpaperEditorPreview(
                            bitmap = editedBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.editor_wallpaper_edited_cd),
                            modifier = Modifier.fillMaxSize(),
                            bitmapWidth = editedBitmap.width,
                            bitmapHeight = editedBitmap.height,
                            overlays = state.overlayLayers,
                            selectedOverlayId = state.selectedOverlayId,
                            onSelectOverlay = viewModel::selectOverlay,
                            onMoveOverlay = viewModel::moveOverlay,
                            onResizeOverlay = viewModel::scaleOverlay,
                            onDeleteOverlay = viewModel::deleteOverlay,
                        )
                    }
                    state.error != null && state.originalBitmap == null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BrokenImage, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.editor_wallpaper_load_failed), color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (state.isProcessing || state.isDepthProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            state.qualityWarning?.let { warning ->
                AuraStatusBanner(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.editor_wallpaper_quality_warning_title),
                    message = warning,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    tone = MaterialTheme.colorScheme.tertiary,
                )
            }

            // Preset chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { preset ->
                    SuggestionChip(
                        onClick = {
                            viewModel.applyPreset(preset.b, preset.c, preset.s, preset.bl, preset.v, preset.g, preset.a, preset.w)
                        },
                        label = { Text(stringResource(preset.nameRes), style = MaterialTheme.typography.labelSmall) },
                        shape = AuraShapes.Medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
            }

            // Filter selector — two rows so all 8 filters are visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEach { filter ->
                    FilterChip(
                    selected = selectedFilter == filter.titleRes,
                    onClick = { selectedFilter = filter.titleRes },
                    label = { Text(stringResource(filter.titleRes), style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(filter.icon, null, modifier = Modifier.size(14.dp))
                        },
                        shape = AuraShapes.Medium,
                    )
                }
                FilterChip(
                    selected = selectedFilter == R.string.editor_wallpaper_depth_chip,
                    onClick = { selectedFilter = R.string.editor_wallpaper_depth_chip },
                    label = {
                        Text(
                            stringResource(R.string.editor_wallpaper_depth_chip),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Layers, null, modifier = Modifier.size(14.dp))
                    },
                    shape = AuraShapes.Medium,
                )
                FilterChip(
                    selected = selectedFilter == R.string.editor_wallpaper_layers_chip,
                    onClick = { selectedFilter = R.string.editor_wallpaper_layers_chip },
                    label = {
                        Text(
                            stringResource(R.string.editor_wallpaper_layers_chip),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Layers, null, modifier = Modifier.size(14.dp))
                    },
                    shape = AuraShapes.Medium,
                )
            }

            when (selectedFilter) {
                R.string.editor_wallpaper_depth_chip -> DepthPortraitControls(state = state, viewModel = viewModel)
                R.string.editor_wallpaper_layers_chip -> WallpaperLayerControls(state = state, viewModel = viewModel)
                else -> {
                    // Active slider
                    filters.find { it.titleRes == selectedFilter }?.let { active ->
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(active.titleRes), style = MaterialTheme.typography.labelMedium)
                                Text(String.format(java.util.Locale.ROOT, "%.1f", active.value), style = MaterialTheme.typography.labelSmall)
                            }
                            Slider(
                                value = active.value,
                                onValueChange = active.onChange,
                                valueRange = active.range,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            }

            // Apply buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Apply reads the source bitmap, so it must wait for the decode to
                // finish rather than firing while the URL is still downloading.
                val canApply = state.isSourceReady && !state.isApplying
                OutlinedButton(
                    onClick = { viewModel.apply(WallpaperTarget.HOME) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = canApply,
                    shape = AuraShapes.Medium,
                ) { Text(stringResource(R.string.common_home)) }
                OutlinedButton(
                    onClick = { viewModel.apply(WallpaperTarget.LOCK) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = canApply,
                    shape = AuraShapes.Medium,
                ) { Text(stringResource(R.string.common_lock)) }
                Button(
                    onClick = { viewModel.apply(WallpaperTarget.BOTH) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = canApply,
                    shape = AuraShapes.Medium,
                ) {
                    if (state.isApplying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.common_both))
                }
            }
        }
    }
}

@Composable
internal fun WallpaperEditorPreview(
    bitmap: ImageBitmap,
    contentDescription: String,
    modifier: Modifier = Modifier,
    bitmapWidth: Int,
    bitmapHeight: Int,
    overlays: List<WallpaperOverlayLayer>,
    selectedOverlayId: Long?,
    onSelectOverlay: (Long?) -> Unit,
    onMoveOverlay: (Long, Float, Float) -> Unit,
    onResizeOverlay: (Long, Float) -> Unit,
    onDeleteOverlay: (Long) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val bitmapAspect = if (bitmapHeight > 0) bitmapWidth.toFloat() / bitmapHeight.toFloat() else 1f
        val containerAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else bitmapAspect
        val imageModifier = if (containerAspect > bitmapAspect) {
            Modifier
                .height(maxHeight)
                .width(maxHeight * bitmapAspect)
        } else {
            Modifier
                .width(maxWidth)
                .height(maxWidth / bitmapAspect.coerceAtLeast(0.01f))
        }

        // One traversal group, so a screen reader reads the picture, then its layers, then the controls.
        Box(
            modifier = imageModifier.semantics { isTraversalGroup = true },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { traversalIndex = -1f },
            )
            OverlayLayerPreview(
                overlays = overlays,
                selectedOverlayId = selectedOverlayId,
                onSelectOverlay = onSelectOverlay,
                onMoveOverlay = onMoveOverlay,
                onResizeOverlay = onResizeOverlay,
                onDeleteOverlay = onDeleteOverlay,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Share of the picture one Move step covers, for the buttons and the screen-reader actions. */
internal const val OVERLAY_MOVE_STEP = 0.04f

/** Scale one Bigger or Smaller step adds or removes. */
internal const val OVERLAY_SCALE_STEP = 0.25f

/**
 * Layers drawn over the preview. Dragging is the fast path; each layer also names itself, says
 * where it sits and how big it is, and offers Move, resize and Delete actions, so TalkBack and
 * Switch Access users can do everything a drag does.
 */
@Composable
private fun OverlayLayerPreview(
    overlays: List<WallpaperOverlayLayer>,
    selectedOverlayId: Long?,
    onSelectOverlay: (Long?) -> Unit,
    onMoveOverlay: (Long, Float, Float) -> Unit,
    onResizeOverlay: (Long, Float) -> Unit,
    onDeleteOverlay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectLabel = stringResource(R.string.editor_wallpaper_layers_select)
    val moveLeft = stringResource(R.string.editor_wallpaper_layers_move_left)
    val moveRight = stringResource(R.string.editor_wallpaper_layers_move_right)
    val moveUp = stringResource(R.string.editor_wallpaper_layers_move_up)
    val moveDown = stringResource(R.string.editor_wallpaper_layers_move_down)
    val bigger = stringResource(R.string.editor_wallpaper_layers_bigger)
    val smaller = stringResource(R.string.editor_wallpaper_layers_smaller)
    val deleteLabel = stringResource(R.string.editor_wallpaper_layers_delete)
    val defaultText = stringResource(R.string.editor_wallpaper_default_overlay_text)
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val textFontSize = with(density) { (widthPx * 0.09f).toSp() }

        overlays.forEach { layer ->
            val isSelected = layer.id == selectedOverlayId
            val baseWidthPx = when (layer.type) {
                WallpaperOverlayType.TEXT -> widthPx * 0.58f
                WallpaperOverlayType.STICKER -> min(widthPx, heightPx) * 0.18f
            }.coerceAtLeast(48f)
            val baseHeightPx = when (layer.type) {
                WallpaperOverlayType.TEXT -> widthPx * 0.16f
                WallpaperOverlayType.STICKER -> min(widthPx, heightPx) * 0.18f
            }.coerceAtLeast(48f)
            val baseWidth = with(density) { baseWidthPx.toDp() }
            val baseHeight = with(density) { baseHeightPx.toDp() }
            val overlayLabel = when (layer.type) {
                WallpaperOverlayType.TEXT ->
                    stringResource(R.string.editor_wallpaper_layers_text_layer_cd, layer.text.ifBlank { defaultText })
                WallpaperOverlayType.STICKER ->
                    stringResource(R.string.editor_wallpaper_layers_sticker_layer_cd, stringResource(stickerLabel(layer.sticker)))
            }
            val geometry = stringResource(
                R.string.editor_wallpaper_layers_geometry,
                (layer.x * 100f).roundToInt(),
                (layer.y * 100f).roundToInt(),
                String.format(java.util.Locale.ROOT, "%.2fx", layer.scale),
            )

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (layer.x * widthPx - baseWidthPx / 2f).roundToInt(),
                            y = (layer.y * heightPx - baseHeightPx / 2f).roundToInt(),
                        )
                    }
                    .size(width = baseWidth, height = baseHeight)
                    .graphicsLayer(
                        rotationZ = layer.rotationDegrees,
                        scaleX = layer.scale,
                        scaleY = layer.scale,
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = AuraShapes.Medium,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .pointerInput(layer.id, widthPx, heightPx) {
                        detectDragGestures(
                            onDragStart = { onSelectOverlay(layer.id) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onMoveOverlay(
                                    layer.id,
                                    dragAmount.x / widthPx,
                                    dragAmount.y / heightPx,
                                )
                            },
                        )
                    }
                    .clickable(onClickLabel = selectLabel) { onSelectOverlay(layer.id) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = overlayLabel
                        stateDescription = geometry
                        selected = isSelected
                        customActions = listOf(
                            CustomAccessibilityAction(moveLeft) { onMoveOverlay(layer.id, -OVERLAY_MOVE_STEP, 0f); true },
                            CustomAccessibilityAction(moveRight) { onMoveOverlay(layer.id, OVERLAY_MOVE_STEP, 0f); true },
                            CustomAccessibilityAction(moveUp) { onMoveOverlay(layer.id, 0f, -OVERLAY_MOVE_STEP); true },
                            CustomAccessibilityAction(moveDown) { onMoveOverlay(layer.id, 0f, OVERLAY_MOVE_STEP); true },
                            CustomAccessibilityAction(bigger) { onResizeOverlay(layer.id, OVERLAY_SCALE_STEP); true },
                            CustomAccessibilityAction(smaller) { onResizeOverlay(layer.id, -OVERLAY_SCALE_STEP); true },
                            CustomAccessibilityAction(deleteLabel) { onDeleteOverlay(layer.id); true },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                when (layer.type) {
                    WallpaperOverlayType.TEXT -> {
                        Text(
                            text = layer.text.ifBlank { defaultText },
                            color = Color(layer.color),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // The layer's description already carries the text; don't read it twice.
                            modifier = Modifier.clearAndSetSemantics {},
                            style = TextStyle(
                                fontSize = textFontSize,
                                fontWeight = FontWeight.Bold,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.72f),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 6f,
                                ),
                            ),
                        )
                    }
                    WallpaperOverlayType.STICKER -> {
                        Icon(
                            imageVector = when (layer.sticker) {
                                WallpaperSticker.STAR -> Icons.Default.Star
                                WallpaperSticker.HEART -> Icons.Default.Favorite
                                WallpaperSticker.SPARKLE -> Icons.Default.AutoAwesome
                            },
                            contentDescription = null,
                            tint = Color(layer.color),
                            modifier = Modifier.fillMaxSize(0.86f),
                        )
                    }
                }
            }
        }
    }
}

private fun stickerLabel(sticker: WallpaperSticker): Int = when (sticker) {
    WallpaperSticker.STAR -> R.string.editor_wallpaper_layers_sticker_star
    WallpaperSticker.HEART -> R.string.editor_wallpaper_layers_sticker_heart
    WallpaperSticker.SPARKLE -> R.string.editor_wallpaper_layers_sticker_sparkle
}

@Composable
private fun WallpaperLayerControls(
    state: EditorState,
    viewModel: WallpaperEditorViewModel,
) {
    val enabled = state.isSourceReady && !state.isApplying && !state.isExporting && !state.isPreparingParallax
    val selectedLayer = state.overlayLayers.firstOrNull { it.id == state.selectedOverlayId }
    val colorOptions = listOf(
        0xFFFFFFFF.toInt() to R.string.editor_wallpaper_layers_color_white,
        0xFF111111.toInt() to R.string.editor_wallpaper_layers_color_black,
        0xFFFFD54F.toInt() to R.string.editor_wallpaper_layers_color_yellow,
        0xFF4FC3F7.toInt() to R.string.editor_wallpaper_layers_color_blue,
        0xFFFF5C8A.toInt() to R.string.editor_wallpaper_layers_color_pink,
        0xFF81C784.toInt() to R.string.editor_wallpaper_layers_color_green,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.editor_wallpaper_layers_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.editor_wallpaper_layers_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = viewModel::addTextOverlay,
                enabled = enabled,
                modifier = Modifier.widthIn(min = 96.dp).heightIn(min = 58.dp),
                shape = AuraShapes.Medium,
            ) {
                EditorActionButtonContent(Icons.Default.TextFields, stringResource(R.string.editor_wallpaper_layers_add_text))
            }
            OutlinedButton(
                onClick = { viewModel.addStickerOverlay(WallpaperSticker.STAR) },
                enabled = enabled,
                modifier = Modifier.widthIn(min = 104.dp).heightIn(min = 58.dp),
                shape = AuraShapes.Medium,
            ) {
                EditorActionButtonContent(Icons.Default.Star, stringResource(R.string.editor_wallpaper_layers_add_sticker))
            }
            OutlinedButton(
                onClick = viewModel::undoOverlayEdit,
                enabled = state.canUndoOverlay,
                modifier = Modifier.widthIn(min = 88.dp).heightIn(min = 58.dp),
                shape = AuraShapes.Medium,
            ) {
                EditorActionButtonContent(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.editor_wallpaper_layers_undo))
            }
            OutlinedButton(
                onClick = viewModel::deleteSelectedOverlay,
                enabled = selectedLayer != null,
                modifier = Modifier.widthIn(min = 88.dp).heightIn(min = 58.dp),
                shape = AuraShapes.Medium,
            ) {
                EditorActionButtonContent(Icons.Default.Delete, stringResource(R.string.common_delete))
            }
            OutlinedButton(
                onClick = viewModel::exportEditedWallpaper,
                enabled = enabled,
                modifier = Modifier.widthIn(min = 92.dp).heightIn(min = 58.dp),
                shape = AuraShapes.Medium,
            ) {
                if (state.isExporting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    EditorActionButtonContent(Icons.Default.Download, stringResource(R.string.editor_wallpaper_depth_export))
                }
            }
        }

        if (selectedLayer == null) {
            Text(
                stringResource(R.string.editor_wallpaper_layers_empty),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        if (selectedLayer.type == WallpaperOverlayType.TEXT) {
            OutlinedTextField(
                value = selectedLayer.text,
                onValueChange = viewModel::updateSelectedOverlayText,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.editor_wallpaper_layers_text_label)) },
                shape = AuraShapes.Medium,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    WallpaperSticker.STAR to stringResource(R.string.editor_wallpaper_layers_sticker_star),
                    WallpaperSticker.HEART to stringResource(R.string.editor_wallpaper_layers_sticker_heart),
                    WallpaperSticker.SPARKLE to stringResource(R.string.editor_wallpaper_layers_sticker_sparkle),
                ).forEach { (sticker, label) ->
                    FilterChip(
                        selected = selectedLayer.sticker == sticker,
                        onClick = { viewModel.updateSelectedSticker(sticker) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (sticker) {
                                    WallpaperSticker.STAR -> Icons.Default.Star
                                    WallpaperSticker.HEART -> Icons.Default.Favorite
                                    WallpaperSticker.SPARKLE -> Icons.Default.AutoAwesome
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        shape = AuraShapes.Medium,
                    )
                }
            }
        }

        Text(
            stringResource(R.string.editor_wallpaper_layers_color),
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            colorOptions.forEach { (color, nameRes) ->
                val colorName = stringResource(nameRes)
                FilterChip(
                    selected = selectedLayer.color == color,
                    onClick = { viewModel.updateSelectedOverlayColor(color) },
                    // The swatch is the only content, so the chip needs a spoken name.
                    modifier = Modifier.semantics { contentDescription = colorName },
                    label = {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(AuraShapes.Compact)
                                .background(Color(color))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = AuraShapes.Compact,
                                ),
                        )
                    },
                    shape = AuraShapes.Medium,
                )
            }
        }

        OverlaySlider(
            label = stringResource(R.string.editor_wallpaper_layers_scale),
            value = selectedLayer.scale,
            valueLabel = String.format(java.util.Locale.ROOT, "%.2fx", selectedLayer.scale),
            valueRange = 0.5f..2.25f,
            onValueChange = viewModel::updateSelectedOverlayScale,
        )
        OverlaySlider(
            label = stringResource(R.string.editor_wallpaper_layers_rotation),
            value = selectedLayer.rotationDegrees,
            valueLabel = String.format(java.util.Locale.ROOT, "%.0f°", selectedLayer.rotationDegrees),
            valueRange = -180f..180f,
            onValueChange = viewModel::updateSelectedOverlayRotation,
        )

        Text(
            stringResource(R.string.editor_wallpaper_layers_move),
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.scaleOverlay(selectedLayer.id, -OVERLAY_SCALE_STEP) }) {
                Icon(Icons.Default.ZoomOut, stringResource(R.string.editor_wallpaper_layers_smaller))
            }
            IconButton(onClick = { viewModel.moveOverlay(selectedLayer.id, -OVERLAY_MOVE_STEP, 0f) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.editor_wallpaper_layers_move_left))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { viewModel.moveOverlay(selectedLayer.id, 0f, -OVERLAY_MOVE_STEP) }) {
                    Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.editor_wallpaper_layers_move_up))
                }
                IconButton(onClick = { viewModel.moveOverlay(selectedLayer.id, 0f, OVERLAY_MOVE_STEP) }) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.editor_wallpaper_layers_move_down))
                }
            }
            IconButton(onClick = { viewModel.moveOverlay(selectedLayer.id, OVERLAY_MOVE_STEP, 0f) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.editor_wallpaper_layers_move_right))
            }
            IconButton(onClick = { viewModel.scaleOverlay(selectedLayer.id, OVERLAY_SCALE_STEP) }) {
                Icon(Icons.Default.ZoomIn, stringResource(R.string.editor_wallpaper_layers_bigger))
            }
        }
    }
}

@Composable
private fun OverlaySlider(
    label: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            // The label and value sit in a separate row, so give the slider its own name and reading.
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = valueLabel
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun DepthPortraitControls(
    state: EditorState,
    viewModel: WallpaperEditorViewModel,
) {
    val enabled = state.isSourceReady &&
        !state.isDepthProcessing &&
        !state.isExporting &&
        !state.isPreparingParallax
    val backgroundOptions = listOf(
        DepthBackgroundStyle.BLUR to stringResource(R.string.editor_wallpaper_depth_background_blur),
        DepthBackgroundStyle.TINT to stringResource(R.string.editor_wallpaper_depth_background_tint),
        DepthBackgroundStyle.AMOLED to stringResource(R.string.editor_wallpaper_depth_background_amoled),
    )
    val frameOptions = listOf(
        DepthFrameStyle.NONE to stringResource(R.string.editor_wallpaper_depth_frame_none),
        DepthFrameStyle.SOFT_HALO to stringResource(R.string.editor_wallpaper_depth_frame_halo),
        DepthFrameStyle.POSTER_BORDER to stringResource(R.string.editor_wallpaper_depth_frame_poster),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.editor_wallpaper_depth_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.editor_wallpaper_depth_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            stringResource(R.string.editor_wallpaper_depth_background),
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            backgroundOptions.forEach { (style, label) ->
                FilterChip(
                    selected = state.depthBackgroundStyle == style,
                    onClick = { viewModel.updateDepthBackgroundStyle(style) },
                    enabled = enabled,
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        val icon = when (style) {
                            DepthBackgroundStyle.BLUR -> Icons.Default.BlurOn
                            DepthBackgroundStyle.TINT -> Icons.Default.ColorLens
                            DepthBackgroundStyle.AMOLED -> Icons.Default.DarkMode
                        }
                        Icon(icon, null, modifier = Modifier.size(14.dp))
                    },
                    shape = AuraShapes.Medium,
                )
            }
        }

        Text(
            stringResource(R.string.editor_wallpaper_depth_frame),
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            frameOptions.forEach { (style, label) ->
                FilterChip(
                    selected = state.depthFrameStyle == style,
                    onClick = { viewModel.updateDepthFrameStyle(style) },
                    enabled = enabled,
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        val icon = when (style) {
                            DepthFrameStyle.NONE -> Icons.Default.CropFree
                            DepthFrameStyle.SOFT_HALO -> Icons.Default.AutoAwesome
                            DepthFrameStyle.POSTER_BORDER -> Icons.Default.CropSquare
                        }
                        Icon(icon, null, modifier = Modifier.size(14.dp))
                    },
                    shape = AuraShapes.Medium,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.editor_wallpaper_depth_subject_scale),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                String.format(java.util.Locale.ROOT, "%.2fx", state.depthSubjectScale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = state.depthSubjectScale,
            onValueChange = viewModel::updateDepthSubjectScale,
            valueRange = 0.92f..1.18f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = viewModel::composeDepthPortrait,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                enabled = enabled,
                shape = AuraShapes.Medium,
            ) {
                if (state.isDepthProcessing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    EditorActionButtonContent(
                        icon = Icons.Default.Wallpaper,
                        label = stringResource(R.string.editor_wallpaper_depth_compose),
                    )
                }
            }
            OutlinedButton(
                onClick = viewModel::prepareDepthParallax,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                enabled = enabled,
                shape = AuraShapes.Medium,
            ) {
                if (state.isPreparingParallax) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    EditorActionButtonContent(
                        icon = Icons.Default.Layers,
                        label = stringResource(R.string.editor_wallpaper_depth_parallax),
                    )
                }
            }
            OutlinedButton(
                onClick = viewModel::exportDepthPortrait,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                enabled = enabled,
                shape = AuraShapes.Medium,
            ) {
                if (state.isExporting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    EditorActionButtonContent(
                        icon = Icons.Default.Download,
                        label = stringResource(R.string.editor_wallpaper_depth_export),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorActionButtonContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

private data class FilterControl(
    @StringRes val titleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val onChange: (Float) -> Unit,
)
