package com.freevibe.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.FitCanvasMode
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.WALLPAPER_PRESENTATION_FIT
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.components.FitCanvasControls
import com.freevibe.ui.components.FitCanvasMedia
import com.freevibe.ui.theme.AuraShapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperCropScreen(
    wallpaperId: String,
    fallbackWallpaper: Wallpaper? = null,
    onBack: () -> Unit,
    recoveryViewModel: com.freevibe.ui.screens.wallpapers.WallpapersViewModel = hiltViewModel(),
    viewModel: WallpaperCropViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val presentation by recoveryViewModel.staticWallpaperPresentation.collectAsStateWithLifecycle()
    val savedCanvasMode by recoveryViewModel.staticFitCanvasMode.collectAsStateWithLifecycle()
    val savedCanvasColor by recoveryViewModel.staticFitCanvasColor.collectAsStateWithLifecycle()
    val canvasStyle = remember(savedCanvasMode, savedCanvasColor) {
        FitCanvasStyle(FitCanvasMode.fromPreference(savedCanvasMode), savedCanvasColor).normalized()
    }
    val context = LocalContext.current
    // Reading a string off LocalContext is not a composition read. LocalResources is.
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val cropIdentityKey = remember(wallpaperId, fallbackWallpaper?.source, fallbackWallpaper?.fullUrl) {
        listOf(
            wallpaperId,
            fallbackWallpaper?.source?.name.orEmpty(),
            fallbackWallpaper?.fullUrl.orEmpty(),
        ).joinToString("|")
    }
    var selectionResolved by remember(cropIdentityKey) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(state.success) {
        state.success?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(resources.getString(R.string.common_error_format, it))
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(wallpaperId, fallbackWallpaper?.source, fallbackWallpaper?.fullUrl) {
        // The view model owns the crop, so a rotation or a restored process keeps it.
        val wallpaper = fallbackWallpaper?.let {
            recoveryViewModel.resolveWallpaper(
                id = wallpaperId,
                source = it.source,
                fullUrl = it.fullUrl,
            ) ?: it
        } ?: recoveryViewModel.resolveWallpaper(wallpaperId)
        selectionResolved = wallpaper?.let { viewModel.loadWallpaper(it) } ?: false
    }

    Scaffold(
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_crop_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::resetTransform) {
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
                    Text(stringResource(R.string.editor_crop_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    title = stringResource(R.string.editor_crop_unavailable_title),
                    description = stringResource(R.string.editor_crop_unavailable_body),
                    tone = MaterialTheme.colorScheme.tertiary,
                    primaryAction = AuraStateAction(stringResource(R.string.editor_crop_unavailable_action), Icons.AutoMirrored.Filled.ArrowBack, onBack),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Crop viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .clipToBounds()
                    .onSizeChanged {
                        viewportSize = it
                        viewModel.setViewport(it.width, it.height)
                    }
                    .pointerInput(presentation) {
                        if (presentation != WALLPAPER_PRESENTATION_FIT) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                viewModel.applyGesture(zoom, pan.x, pan.y)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                state.bitmap?.let { bitmap ->
                    if (presentation == WALLPAPER_PRESENTATION_FIT) {
                        FitCanvasMedia(
                            model = bitmap,
                            presentation = presentation,
                            style = canvasStyle,
                            dominantColor = null,
                            contentDescription = stringResource(R.string.editor_crop_image_cd),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.editor_crop_image_cd),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = state.scale,
                                    scaleY = state.scale,
                                    translationX = state.offsetX,
                                    translationY = state.offsetY,
                                ),
                        )
                    }
                }

                // The crop frame: the whole viewport for Free, the selected ratio otherwise, with
                // everything that won't be exported dimmed.
                val frameColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                val frameAspect = if (presentation == WALLPAPER_PRESENTATION_FIT) CropAspect.FREE else state.aspect
                Canvas(Modifier.fillMaxSize()) {
                    val frame = cropFrame(size.width.toInt(), size.height.toInt(), frameAspect)
                    val scrim = Color.Black.copy(alpha = 0.55f)
                    val frameRight = frame.left + frame.width
                    val frameBottom = frame.top + frame.height
                    drawRect(scrim, Offset.Zero, Size(size.width, frame.top))
                    drawRect(scrim, Offset(0f, frameBottom), Size(size.width, size.height - frameBottom))
                    drawRect(scrim, Offset(0f, frame.top), Size(frame.left, frame.height))
                    drawRect(scrim, Offset(frameRight, frame.top), Size(size.width - frameRight, frame.height))
                    drawRect(
                        frameColor,
                        Offset(frame.left, frame.top),
                        Size(frame.width, frame.height),
                        style = Stroke(2.dp.toPx()),
                    )
                }

                if (state.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            FitCanvasControls(
                presentation = presentation,
                style = canvasStyle,
                onPresentationChange = { selected ->
                    recoveryViewModel.setStaticFitCanvasPreferences(selected, canvasStyle)
                },
                onStyleChange = { selected ->
                    recoveryViewModel.setStaticFitCanvasPreferences(presentation, selected)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Zoom info + aspect ratio presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        if (presentation == WALLPAPER_PRESENTATION_FIT) {
                            R.string.fit_canvas_fit_help
                        } else {
                            R.string.editor_crop_gesture_hint
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (presentation != WALLPAPER_PRESENTATION_FIT) CropAspectIndicator(state.aspect)
                Text(
                    String.format(java.util.Locale.ROOT, "%.0f%%", state.scale * 100),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Aspect ratio quick presets + Smart Crop (NX-3)
            if (presentation != WALLPAPER_PRESENTATION_FIT) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = false,
                    enabled = !state.smartCropInProgress && state.bitmap != null && viewportSize != IntSize.Zero,
                    onClick = {
                        if (viewportSize == IntSize.Zero) return@FilterChip
                        scope.launch { viewModel.applySmartCrop(viewportSize.width, viewportSize.height) }
                    },
                    leadingIcon = {
                        if (state.smartCropInProgress) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    },
                    label = {
                        Text(
                            stringResource(if (state.smartCropInProgress) R.string.editor_crop_detecting else R.string.editor_crop_smart_crop),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    shape = AuraShapes.Medium,
                    modifier = Modifier.heightIn(min = 40.dp),
                )
                CropAspectChips(
                    selected = state.aspect,
                    enabled = state.bitmap != null,
                    onSelect = viewModel::selectAspect,
                )
            }

            // Apply buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.applyCropped(
                            WallpaperTarget.HOME,
                            viewportSize.width,
                            viewportSize.height,
                            canvasStyle.takeIf { presentation == WALLPAPER_PRESENTATION_FIT },
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = !state.isApplying && state.bitmap != null,
                    shape = AuraShapes.Medium,
                ) { Text(stringResource(R.string.common_home)) }
                OutlinedButton(
                    onClick = {
                        viewModel.applyCropped(
                            WallpaperTarget.LOCK,
                            viewportSize.width,
                            viewportSize.height,
                            canvasStyle.takeIf { presentation == WALLPAPER_PRESENTATION_FIT },
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = !state.isApplying && state.bitmap != null,
                    shape = AuraShapes.Medium,
                ) { Text(stringResource(R.string.common_lock)) }
                Button(
                    onClick = {
                        viewModel.applyCropped(
                            WallpaperTarget.BOTH,
                            viewportSize.width,
                            viewportSize.height,
                            canvasStyle.takeIf { presentation == WALLPAPER_PRESENTATION_FIT },
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = !state.isApplying && state.bitmap != null,
                    shape = AuraShapes.Medium,
                ) {
                    if (state.isApplying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.common_both))
                }
            }
        }
    }
}

/** The current frame ratio. TalkBack reads the change politely when a chip or Smart Crop switches it. */
@Composable
internal fun CropAspectIndicator(aspect: CropAspect) {
    val aspectLabel = cropAspectLabel(aspect)
    val aspectAnnouncement = stringResource(R.string.editor_crop_ratio_selected, aspectLabel)
    Text(
        aspectLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = aspectAnnouncement
            },
    )
}

/** One chip per frame ratio. Emitted straight into the caller's row so they line up with Smart Crop. */
@Composable
internal fun CropAspectChips(selected: CropAspect, enabled: Boolean, onSelect: (CropAspect) -> Unit) {
    CropAspect.entries.forEach { aspect ->
        FilterChip(
            selected = selected == aspect,
            enabled = enabled,
            onClick = { onSelect(aspect) },
            label = { Text(cropAspectLabel(aspect), style = MaterialTheme.typography.labelSmall) },
            shape = AuraShapes.Medium,
            modifier = Modifier.heightIn(min = 40.dp),
        )
    }
}

@Composable
private fun cropAspectLabel(aspect: CropAspect): String =
    aspect.label ?: stringResource(R.string.editor_crop_ratio_free)
