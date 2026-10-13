package com.freevibe.ui.components

import android.net.Uri
import android.os.Build
import android.view.SurfaceView
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.freevibe.R
import com.freevibe.service.PhotoPickerCustomization
import com.freevibe.ui.theme.AuraShapes
import kotlin.math.roundToInt

/** The embedded picker never grows past this; it was a fixed height before it learned to shrink. */
internal const val MAX_EMBEDDED_PICKER_DP = 420

/** Shorter than this, a picker shows about one row of thumbnails, so the sheet offers the full picker instead. */
internal const val MIN_EMBEDDED_PICKER_DP = 180

/**
 * How tall the embedded picker can be in a window [windowHeightDp] tall, or null when it wouldn't be usable.
 * The sheet stops short of the top, and the drag handle, padding and button take a fixed share, while the
 * title and body grow with [fontScale]. Landscape phones and short split-screen windows land on null.
 */
internal fun embeddedPickerHeightDp(windowHeightDp: Int, fontScale: Float): Int? {
    val chrome = 120 + (110 * fontScale.coerceAtLeast(1f)).roundToInt()
    val available = (windowHeightDp * 0.9f).roundToInt() - chrome
    return if (available < MIN_EMBEDDED_PICKER_DP) null else available.coerceAtMost(MAX_EMBEDDED_PICKER_DP)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedImagePickerSheet(
    title: String,
    body: String,
    fallbackLabel: String,
    onDismiss: () -> Unit,
    onFallback: () -> Unit,
    onImagePicked: (Uri) -> Unit,
) {
    val selectedUri = remember { mutableStateOf<Uri?>(null) }
    val windowHeightDp = LocalConfiguration.current.screenHeightDp
    val fontScale = LocalDensity.current.fontScale
    val pickerMaxHeightDp = remember(windowHeightDp, fontScale) { embeddedPickerHeightDp(windowHeightDp, fontScale) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Half-open, a tall phone would show the picker with its button below the fold.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AuraShapes.SheetTop,
    ) {
        EmbeddedImagePickerSheetContent(
            title = title,
            body = body,
            fallbackLabel = fallbackLabel,
            windowHeightDp = windowHeightDp,
            pickerMaxHeightDp = pickerMaxHeightDp,
            onFallback = onFallback,
        ) {
            EmbeddedImagePickerSurface(
                onUriPermissionGranted = { uris ->
                    selectedUri.value = uris.lastOrNull()
                },
                onUriPermissionRevoked = { uris ->
                    if (selectedUri.value in uris) selectedUri.value = null
                },
                onSelectionComplete = {
                    val uri = selectedUri.value
                    if (uri != null) onImagePicked(uri) else onDismiss()
                },
                onSessionError = { onFallback() },
            )
        }
    }
}

/**
 * The sheet's layout, apart from the live picker. The picker takes what's left after the header and the
 * button, up to [pickerMaxHeightDp], so the button is never pushed off a short window. The header scrolls
 * inside its own share rather than the whole sheet, since a scrolling parent would steal the picker's swipes.
 * With no room for a picker ([pickerMaxHeightDp] null) the full picker becomes the main action.
 */
@Composable
internal fun EmbeddedImagePickerSheetContent(
    title: String,
    body: String,
    fallbackLabel: String,
    windowHeightDp: Int,
    pickerMaxHeightDp: Int?,
    onFallback: () -> Unit,
    picker: @Composable () -> Unit,
) {
    val headerShare = if (pickerMaxHeightDp == null) 0.6f else 0.3f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (windowHeightDp * headerShare).dp)
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.ImageSearch,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (pickerMaxHeightDp == null) {
                    Text(
                        stringResource(R.string.embedded_picker_no_room),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (pickerMaxHeightDp != null) {
            Surface(
                shape = AuraShapes.Medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = pickerMaxHeightDp.dp),
            ) {
                picker()
            }
        }
        val buttonModifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        val buttonContent: @Composable () -> Unit = {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(fallbackLabel)
        }
        if (pickerMaxHeightDp == null) {
            Button(
                onClick = onFallback,
                modifier = buttonModifier,
                shape = AuraShapes.Medium,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            ) { buttonContent() }
        } else {
            OutlinedButton(
                onClick = onFallback,
                modifier = buttonModifier,
                shape = AuraShapes.Medium,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { buttonContent() }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun EmbeddedImagePickerSurface(
    onUriPermissionGranted: (List<Uri>) -> Unit,
    onUriPermissionRevoked: (List<Uri>) -> Unit,
    onSelectionComplete: () -> Unit,
    onSessionError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary.toArgb().toLong() and 0xffffffffL
    val controller = remember { mutableStateOf<com.freevibe.service.EmbeddedPhotoPickerController?>(null) }
    val hasOpened = remember { mutableStateOf(false) }
    val hasFailed = remember { mutableStateOf(false) }
    val currentOnGranted = rememberUpdatedState(onUriPermissionGranted)
    val currentOnRevoked = rememberUpdatedState(onUriPermissionRevoked)
    val currentOnComplete = rememberUpdatedState(onSelectionComplete)
    val currentOnError = rememberUpdatedState(onSessionError)

    fun failOnce(error: Throwable) {
        if (!hasFailed.value) {
            hasFailed.value = true
            currentOnError.value(error)
        }
    }

    fun openIfReady(surfaceView: SurfaceView) {
        if (hasOpened.value || hasFailed.value) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            failOnce(IllegalStateException("Embedded photo picker unavailable"))
            return
        }
        @Suppress("DEPRECATION")
        val hostToken = surfaceView.hostToken
        if (!surfaceView.isAttachedToWindow || hostToken == null) return
        if (surfaceView.width <= 0 || surfaceView.height <= 0) return
        val opened = PhotoPickerCustomization.openEmbeddedImagePicker(
            context = context,
            surfaceView = surfaceView,
            widthPx = surfaceView.width,
            heightPx = surfaceView.height,
            accentColor = accentColor,
            onUriPermissionGranted = { currentOnGranted.value(it) },
            onUriPermissionRevoked = { currentOnRevoked.value(it) },
            onSelectionComplete = { currentOnComplete.value() },
            onSessionError = { failOnce(it) },
        )
        if (opened == null) {
            failOnce(IllegalStateException("Embedded photo picker unavailable"))
        } else {
            controller.value = opened
            hasOpened.value = true
        }
    }

    AndroidView(
        factory = { viewContext ->
            SurfaceView(viewContext).apply {
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        openIfReady(v as SurfaceView)
                    }

                    override fun onViewDetachedFromWindow(v: View) = Unit
                })
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    val surfaceView = v as SurfaceView
                    openIfReady(surfaceView)
                    controller.value?.resize(surfaceView.width, surfaceView.height)
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { surfaceView ->
            openIfReady(surfaceView)
            controller.value?.resize(surfaceView.width, surfaceView.height)
        },
    )

    DisposableEffect(Unit) {
        onDispose { controller.value?.close() }
    }
}
