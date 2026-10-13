package com.freevibe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.freevibe.R
import com.freevibe.data.model.FitCanvasMode
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.WALLPAPER_PRESENTATION_FILL
import com.freevibe.data.model.WALLPAPER_PRESENTATION_FIT
import com.freevibe.data.model.normalizeWallpaperPresentation
import com.freevibe.data.model.resolveFitCanvas
import com.freevibe.service.FitCanvasRenderer
import com.freevibe.ui.theme.AuraShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FitCanvasControls(
    presentation: String,
    style: FitCanvasStyle,
    onPresentationChange: (String) -> Unit,
    onStyleChange: (FitCanvasStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showColorDialog by remember { mutableStateOf(false) }
    val canvasModeListState = rememberLazyListState()
    LaunchedEffect(presentation, style.mode) {
        if (normalizeWallpaperPresentation(presentation) == WALLPAPER_PRESENTATION_FIT) {
            canvasModeListState.scrollToItem(FitCanvasMode.entries.indexOf(style.mode))
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FitCanvasChoiceChip(
                selected = normalizeWallpaperPresentation(presentation) == WALLPAPER_PRESENTATION_FILL,
                onClick = { onPresentationChange(WALLPAPER_PRESENTATION_FILL) },
                icon = Icons.Default.CropFree,
                label = stringResource(R.string.fit_canvas_fill),
                modifier = Modifier.weight(1f),
            )
            FitCanvasChoiceChip(
                selected = normalizeWallpaperPresentation(presentation) == WALLPAPER_PRESENTATION_FIT,
                onClick = { onPresentationChange(WALLPAPER_PRESENTATION_FIT) },
                icon = Icons.Default.FitScreen,
                label = stringResource(R.string.fit_canvas_fit),
                modifier = Modifier.weight(1f),
            )
        }
        if (normalizeWallpaperPresentation(presentation) == WALLPAPER_PRESENTATION_FIT) {
            LazyRow(
                state = canvasModeListState,
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(FitCanvasMode.entries, key = { it.preferenceValue }) { mode ->
                    val icon = when (mode) {
                        FitCanvasMode.AMOLED_BLACK -> Icons.Default.DarkMode
                        FitCanvasMode.CUSTOM_COLOR -> Icons.Default.ColorLens
                        FitCanvasMode.DOMINANT_COLOR -> Icons.Default.Palette
                        FitCanvasMode.BLURRED_EDGE -> Icons.Default.BlurOn
                    }
                    val label = stringResource(
                        when (mode) {
                            FitCanvasMode.AMOLED_BLACK -> R.string.fit_canvas_amoled
                            FitCanvasMode.CUSTOM_COLOR -> R.string.fit_canvas_custom
                            FitCanvasMode.DOMINANT_COLOR -> R.string.fit_canvas_dominant
                            FitCanvasMode.BLURRED_EDGE -> R.string.fit_canvas_blur
                        },
                    )
                    FitCanvasChoiceChip(
                        selected = style.mode == mode,
                        onClick = {
                            if (mode == FitCanvasMode.CUSTOM_COLOR) showColorDialog = true
                            else onStyleChange(style.copy(mode = mode))
                        },
                        icon = icon,
                        label = label,
                        swatch = style.customColor.takeIf { mode == FitCanvasMode.CUSTOM_COLOR },
                    )
                }
            }
        }
    }

    if (showColorDialog) {
        FitCanvasColorDialog(
            initialColor = style.customColor,
            onDismiss = { showColorDialog = false },
            onConfirm = { color ->
                onStyleChange(FitCanvasStyle(FitCanvasMode.CUSTOM_COLOR, color))
                showColorDialog = false
            },
        )
    }
}

@Composable
private fun FitCanvasChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    swatch: Int? = null,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            if (swatch != null) {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(Color(swatch), AuraShapes.Small),
                )
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
        label = { Text(label, maxLines = 1) },
        modifier = modifier,
    )
}

@Composable
private fun FitCanvasColorDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember(initialColor) {
        mutableStateOf("#%06X".format(initialColor and 0xFFFFFF))
    }
    val parsed = remember(value) { parseFitCanvasColor(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fit_canvas_custom_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(9) },
                label = { Text(stringResource(R.string.fit_canvas_custom)) },
                singleLine = true,
                isError = value.isNotBlank() && parsed == null,
                supportingText = { Text(stringResource(R.string.fit_canvas_custom_hint)) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(Color(parsed ?: initialColor), AuraShapes.Small),
                    )
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text(stringResource(R.string.common_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

internal fun parseFitCanvasColor(value: String): Int? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val rgb = hex.takeLast(6).toLongOrNull(16)?.toInt() ?: return null
    return rgb or 0xFF000000.toInt()
}

@Composable
fun FitCanvasMedia(
    model: Any?,
    presentation: String,
    style: FitCanvasStyle,
    dominantColor: Int?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    /** Called when the main image has loaded, so a caller can read it back from Coil's cache. */
    onLoaded: () -> Unit = {},
) {
    val context = LocalContext.current
    var extractedDominant by remember(model) { mutableStateOf<Int?>(null) }
    LaunchedEffect(model, dominantColor, style.mode) {
        if (
            dominantColor != null ||
            (style.mode != FitCanvasMode.DOMINANT_COLOR && style.mode != FitCanvasMode.BLURRED_EDGE) ||
            model == null
        ) {
            extractedDominant = null
            return@LaunchedEffect
        }
        extractedDominant = withContext(Dispatchers.IO) {
            val bitmap = when (model) {
                is android.graphics.Bitmap -> model
                else -> {
                    val request = ImageRequest.Builder(context)
                        .data(model)
                        .size(128)
                        .allowHardware(false)
                        .build()
                    (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
                }
            } ?: return@withContext null
            // Coil owns bitmaps returned from its memory cache. Sampling is
            // read-only, so recycling here could corrupt another image request.
            FitCanvasRenderer.dominantOpaqueColor(bitmap)
        }
    }
    val previewDominant = dominantColor ?: extractedDominant
    val previewCanvas = resolveFitCanvas(
        style = style,
        dominantColor = previewDominant,
        posterAvailable = model != null,
        isHdr = (model as? android.graphics.Bitmap)?.config == android.graphics.Bitmap.Config.RGBA_F16,
    )
    Box(modifier.clipToBounds().background(Color.Black)) {
        if (normalizeWallpaperPresentation(presentation) == WALLPAPER_PRESENTATION_FILL) {
            FitCanvasImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onLoaded = onLoaded,
            )
            return@Box
        }

        when (previewCanvas.mode) {
            FitCanvasMode.AMOLED_BLACK -> Unit
            FitCanvasMode.CUSTOM_COLOR -> Box(
                Modifier.fillMaxSize().background(Color(previewCanvas.color)),
            )
            FitCanvasMode.DOMINANT_COLOR -> Box(
                Modifier.fillMaxSize().background(Color(previewCanvas.color)),
            )
            FitCanvasMode.BLURRED_EDGE -> FitCanvasImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 1.12f, scaleY = 1.12f)
                    .blur(30.dp, BlurredEdgeTreatment.Unbounded),
            )
        }
        if (previewCanvas.mode == FitCanvasMode.BLURRED_EDGE) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 42f / 255f)))
        }
        FitCanvasImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onLoaded = onLoaded,
        )
    }
}

@Composable
private fun FitCanvasImage(
    model: Any?,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier,
    onLoaded: () -> Unit = {},
) {
    if (model is android.graphics.Bitmap) {
        Image(
            bitmap = model.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            onSuccess = { onLoaded() },
        )
    }
}
