package com.freevibe.ui.screens.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.model.stableKey
import com.freevibe.data.remote.RequestPurpose
import com.freevibe.data.remote.purpose
import com.freevibe.service.SmartCropCalculator
import com.freevibe.service.SmartCropDetector
import com.freevibe.service.WallpaperApplyCoordinator
import com.freevibe.service.WallpaperApplyPolicy
import com.freevibe.service.WallpaperApplier
import com.freevibe.service.advertisedLengthExceeds
import com.freevibe.service.MediaIngestionImageFlow
import com.freevibe.service.currentFullUrl
import com.freevibe.service.decodeImageBytesForFlow
import com.freevibe.service.isLocalMediaLocator
import com.freevibe.service.localMediaUri
import com.freevibe.service.readStreamCapped
import com.freevibe.service.ShareOutbox
import com.freevibe.util.displayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

data class CropState(
    val bitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val aspect: CropAspect = CropAspect.FREE,
    val isApplying: Boolean = false,
    val smartCropInProgress: Boolean = false,
    val success: String? = null,
    val error: String? = null,
)

@HiltViewModel
class WallpaperCropViewModel @Inject constructor(
    private val wallpaperApplier: WallpaperApplier,
    private val okHttpClient: OkHttpClient,
    private val smartCropDetector: SmartCropDetector,
    @ApplicationContext private val appContext: Context,
    private val applyCoordinator: WallpaperApplyCoordinator,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _state = MutableStateFlow(CropState())
    val state = _state.asStateFlow()
    private var loadedWallpaperKey: String? = null
    private var viewportWidth = 0
    private var viewportHeight = 0

    /**
     * Identity for the cropped output: the wallpaper the crop was loaded from is
     * what history records, so a crop can be undone like any other apply.
     */
    private var croppedWallpaper: Wallpaper? = null

    fun loadWallpaper(wallpaper: Wallpaper): Boolean {
        croppedWallpaper = wallpaper
        val currentState = _state.value
        val wallpaperKey = wallpaper.stableKey()
        if (loadedWallpaperKey == wallpaperKey && (currentState.bitmap != null || currentState.isLoading)) {
            return true
        }
        // After the process comes back, a saved crop for this same wallpaper is restored, not reset.
        val restoring = loadedWallpaperKey == null && savedStateHandle.get<String>(KEY_WALLPAPER) == wallpaperKey
        loadedWallpaperKey = wallpaperKey
        savedStateHandle[KEY_WALLPAPER] = wallpaperKey
        val (aspect, transform) = if (restoring) savedCrop() else CropAspect.FREE to CropTransform()
        val url = wallpaper.currentFullUrl()
        if (isLocalMediaLocator(url)) {
            loadFromContentUri(localMediaUri(url), aspect, transform)
        } else {
            loadFromUrl(url, aspect, transform)
        }
        return true
    }

    fun loadFromUrl(url: String, aspect: CropAspect = CropAspect.FREE, transform: CropTransform = CropTransform()) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    bitmap = null,
                    isLoading = true,
                    success = null,
                    error = null,
                )
            }
            setCrop(aspect, transform)
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val request = Request.Builder().purpose(RequestPurpose.EDIT).url(url).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception(appContext.getString(com.freevibe.R.string.crop_load_http, response.code))
                        val body = response.body ?: throw Exception(appContext.getString(com.freevibe.R.string.crop_load_empty))
                        val advertised = body.contentLength()
                        if (advertisedLengthExceeds(advertised, MAX_CROP_BYTES)) {
                            throw Exception(appContext.getString(com.freevibe.R.string.crop_load_too_large))
                        }
                        val bytes = readStreamCapped(body.byteStream(), MAX_CROP_BYTES)
                        decodeImageBytesForFlow(
                            bytes = bytes,
                            flow = MediaIngestionImageFlow.EDITOR,
                            declaredMimeType = body.contentType()?.toString(),
                            extension = url.substringBefore('?').substringAfterLast('.', missingDelimiterValue = ""),
                            maxLongEdge = MAX_CROP_LONG_EDGE,
                        )
                    }
                }
                _state.update { it.copy(bitmap = bitmap, isLoading = false) }
                refit()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoading = false, error = e.displayMessage(appContext)) }
            }
        }
    }

    private fun loadFromContentUri(uri: Uri, aspect: CropAspect = CropAspect.FREE, transform: CropTransform = CropTransform()) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    bitmap = null,
                    isLoading = true,
                    success = null,
                    error = null,
                )
            }
            setCrop(aspect, transform)
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val stream = appContext.contentResolver.openInputStream(uri)
                        ?: throw Exception(appContext.getString(com.freevibe.R.string.crop_open_failed))
                    stream.use {
                        val bytes = readStreamCapped(it, MAX_CROP_BYTES)
                        val bitmap = decodeImageBytesForFlow(
                            bytes = bytes,
                            flow = MediaIngestionImageFlow.EDITOR,
                            declaredMimeType = appContext.contentResolver.getType(uri),
                            extension = uri.lastPathSegment?.substringAfterLast('.', missingDelimiterValue = ""),
                            maxLongEdge = MAX_CROP_LONG_EDGE,
                        )
                        ShareOutbox.deleteExternalMedia(appContext, uri)
                        bitmap
                    }
                }
                _state.update { it.copy(bitmap = bitmap, isLoading = false) }
                refit()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoading = false, error = e.displayMessage(appContext)) }
            }
        }
    }

    fun setFromBitmap(bitmap: Bitmap) {
        _state.update { it.copy(bitmap = bitmap) }
    }

    fun updateTransform(scale: Float, offsetX: Float, offsetY: Float) {
        setCrop(_state.value.aspect, CropTransform(scale, offsetX, offsetY))
    }

    /** Pinch and drag. A zoom past the usual ceiling, as a ratio frame can need, only ever comes down. */
    fun applyGesture(zoom: Float, panX: Float, panY: Float) {
        val s = _state.value
        val scale = (s.scale * zoom).coerceIn(MIN_GESTURE_SCALE, max(MAX_GESTURE_SCALE, s.scale))
        setCrop(s.aspect, CropTransform(scale, s.offsetX + panX, s.offsetY + panY))
    }

    fun resetTransform() {
        setCrop(CropAspect.FREE, CropTransform())
    }

    fun selectAspect(aspect: CropAspect) {
        setCrop(aspect, _state.value.transform())
        refit()
    }

    /** The crop frame follows the viewport, so a new size (a rotation, say) re-covers a ratio frame. */
    fun setViewport(width: Int, height: Int) {
        if (width == viewportWidth && height == viewportHeight) return
        viewportWidth = width
        viewportHeight = height
        refit()
    }

    private fun refit() {
        val s = _state.value
        val bmp = s.bitmap ?: return
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        setCrop(s.aspect, transformForAspect(bmp.width, bmp.height, viewportWidth, viewportHeight, s.aspect, s.transform()))
    }

    private fun setCrop(aspect: CropAspect, transform: CropTransform) {
        _state.update {
            it.copy(aspect = aspect, scale = transform.scale, offsetX = transform.offsetX, offsetY = transform.offsetY)
        }
        savedStateHandle[KEY_ASPECT] = aspect.name
        savedStateHandle[KEY_SCALE] = transform.scale
        savedStateHandle[KEY_OFFSET_X] = transform.offsetX
        savedStateHandle[KEY_OFFSET_Y] = transform.offsetY
    }

    private fun savedCrop(): Pair<CropAspect, CropTransform> {
        val aspect = savedStateHandle.get<String>(KEY_ASPECT)
            ?.let { name -> CropAspect.entries.firstOrNull { it.name == name } }
            ?: CropAspect.FREE
        return aspect to CropTransform(
            scale = savedStateHandle.get<Float>(KEY_SCALE) ?: 1f,
            offsetX = savedStateHandle.get<Float>(KEY_OFFSET_X) ?: 0f,
            offsetY = savedStateHandle.get<Float>(KEY_OFFSET_Y) ?: 0f,
        )
    }

    private fun CropState.transform() = CropTransform(scale, offsetX, offsetY)

    fun applyCropped(
        target: WallpaperTarget,
        viewportWidth: Int,
        viewportHeight: Int,
        fitCanvasStyle: FitCanvasStyle? = null,
    ) {
        val bmp = _state.value.bitmap ?: return
        val s = _state.value

        viewModelScope.launch {
            _state.update { it.copy(isApplying = true) }
            try {
                val outputBitmap = if (fitCanvasStyle == null) {
                    withContext(Dispatchers.Default) {
                        cropBitmap(bmp, s.aspect, s.transform(), viewportWidth, viewportHeight)
                    }
                } else {
                    // WallpaperApplier treats its input as borrowed and renders
                    // the Fit Canvas result separately. Reuse the editor bitmap
                    // instead of holding a full-size duplicate during apply.
                    bmp
                }
                val ownsOutputBitmap = outputBitmap !== bmp
                try {
                    // Cropped output goes through the coordinator so it lands in history
                    // and can be undone, exactly like an uncropped apply.
                    applyCoordinator.apply(
                        wallpaper = croppedWallpaper,
                        target = target,
                        policy = WallpaperApplyPolicy.DERIVED,
                    ) { wallpaperApplier.applyFromBitmap(outputBitmap, target, fitCanvasStyle) }
                        .onSuccess { receipt ->
                            _state.update {
                                it.copy(isApplying = false, success = receipt.feedbackMessage ?: "Applied")
                            }
                        }
                        .onFailure { e ->
                            _state.update { it.copy(isApplying = false, error = e.displayMessage(appContext)) }
                        }
                } finally {
                    if (ownsOutputBitmap && !outputBitmap.isRecycled) outputBitmap.recycle()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isApplying = false, error = e.displayMessage(appContext)) }
            }
        }
    }

    /**
     * Smart Crop (ROADMAP NX-3) — runs ML Kit Subject Segmentation against the
     * currently-loaded bitmap, computes a centre-on-subject transform, and
     * publishes the result so the composable can sync local gesture state.
     *
     * Returns the new transform on success or null when no subject is detected
     * or segmentation fails. Errors flow through [CropState.error]; UI-visible
     * "no subject" is surfaced as an error message, not as a thrown exception.
     */
    suspend fun applySmartCrop(viewportWidth: Int, viewportHeight: Int): SmartCropCalculator.Transform? {
        val bmp = _state.value.bitmap ?: return null
        if (viewportWidth <= 0 || viewportHeight <= 0) return null
        _state.update { it.copy(smartCropInProgress = true) }
        return try {
            val subject = withContext(Dispatchers.Default) {
                smartCropDetector.detectSubject(bmp)
            }
            if (subject == null) {
                _state.update {
                    it.copy(
                        smartCropInProgress = false,
                        error = appContext.getString(com.freevibe.R.string.crop_subject_not_found),
                    )
                }
                return null
            }
            // The calculator centers the subject in the crop frame and works in viewport pixels
            // per source pixel. The editor's scale is relative to the fitted image.
            val frame = cropFrame(viewportWidth, viewportHeight, _state.value.aspect)
            val t = SmartCropCalculator.computeTransform(
                bitmapWidth = bmp.width,
                bitmapHeight = bmp.height,
                subject = subject,
                viewportWidth = frame.width.roundToInt(),
                viewportHeight = frame.height.roundToInt(),
            )
            val fit = cropFitScale(bmp.width, bmp.height, viewportWidth, viewportHeight)
            val transform = CropTransform(t.scale / fit, t.offsetX, t.offsetY)
            setCrop(_state.value.aspect, transform)
            _state.update {
                it.copy(
                    smartCropInProgress = false,
                    success = appContext.getString(com.freevibe.R.string.crop_smart_applied),
                )
            }
            SmartCropCalculator.Transform(transform.scale, transform.offsetX, transform.offsetY)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _state.update { it.copy(smartCropInProgress = false, error = e.displayMessage(appContext)) }
            null
        }
    }

    fun clearMessages() = _state.update { it.copy(success = null, error = null) }

    private fun cropBitmap(
        source: Bitmap,
        aspect: CropAspect,
        transform: CropTransform,
        viewWidth: Int,
        viewHeight: Int,
    ): Bitmap {
        val rect = cropSourceRect(source.width, source.height, viewWidth, viewHeight, aspect, transform)
        return Bitmap.createBitmap(source, rect.left, rect.top, rect.width, rect.height)
    }

    private companion object {
        /** Max bytes accepted when downloading a wallpaper for cropping. */
        private const val MAX_CROP_BYTES = 64L * 1024 * 1024
        private const val MAX_CROP_LONG_EDGE = 4096
        private const val MIN_GESTURE_SCALE = 0.5f
        private const val MAX_GESTURE_SCALE = 5f
        private const val KEY_WALLPAPER = "crop_wallpaper"
        private const val KEY_ASPECT = "crop_aspect"
        private const val KEY_SCALE = "crop_scale"
        private const val KEY_OFFSET_X = "crop_offset_x"
        private const val KEY_OFFSET_Y = "crop_offset_y"
    }
}
