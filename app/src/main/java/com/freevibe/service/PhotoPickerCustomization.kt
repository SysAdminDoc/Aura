package com.freevibe.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import android.view.SurfaceView
import android.widget.photopicker.EmbeddedPhotoPickerClient
import android.widget.photopicker.EmbeddedPhotoPickerFeatureInfo
import android.widget.photopicker.EmbeddedPhotoPickerProviderFactory
import android.widget.photopicker.EmbeddedPhotoPickerSession
import android.widget.photopicker.PhotoPickerSelectionParams
import android.widget.photopicker.PhotoPickerUiCustomizationParams
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension

/**
 * Typed entry points for the embedded Photo Picker and its portrait grid.
 *
 * The embedded picker reaches Android 14 and newer through SDK Extensions, and the feature
 * builder gained its newer setters in later extension levels, so each call below is gated on
 * the level that introduced it. Keep every check in here so callers stay free of them.
 */
object PhotoPickerCustomization {
    private const val TAG = "PhotoPickerCustomization"
    private const val EMBEDDED_PICKER_MIN_EXTENSION = 15
    private const val EXPANDED_STATE_MIN_EXTENSION = 21
    private const val CUSTOMIZATION_MIN_EXTENSION = 22
    private const val IMAGE_MIME_TYPE = "image/*"

    /**
     * Mutates [intent] to attach the 9:16 Photo Picker grid. The intent extra is Android 17
     * only, unlike the embedded builder setter, so this is a no-op below it.
     */
    @SuppressLint("NewApi", "InlinedApi") // Gated on API 37 on the first line.
    fun apply9x16AspectRatio(intent: Intent) {
        if (Build.VERSION.SDK_INT < 37) return
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_UI_CUSTOMIZATION_PARAMS, portraitGridCustomization())
    }

    /**
     * Embedded picker availability from Android's documented gate:
     * Android 14/API 34 with SDK Extensions version 15 or newer.
     */
    @SuppressLint("NewApi") // create() is only reached after the extension gate above it.
    fun isEmbeddedImagePickerAvailable(context: Context): Boolean {
        if (extensionVersion() < EMBEDDED_PICKER_MIN_EXTENSION) return false
        return runCatching { EmbeddedPhotoPickerProviderFactory.create(context) }.isSuccess
    }

    @SuppressLint("NewApi") // Every picker call runs behind isEmbeddedImagePickerAvailable.
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun openEmbeddedImagePicker(
        context: Context,
        surfaceView: SurfaceView,
        widthPx: Int,
        heightPx: Int,
        accentColor: Long?,
        onUriPermissionGranted: (List<Uri>) -> Unit,
        onUriPermissionRevoked: (List<Uri>) -> Unit,
        onSelectionComplete: () -> Unit,
        onSessionError: (Throwable) -> Unit,
    ): EmbeddedPhotoPickerController? {
        if (!isEmbeddedImagePickerAvailable(context)) return null
        @Suppress("DEPRECATION")
        val hostToken = surfaceView.hostToken ?: return null
        if (widthPx <= 0 || heightPx <= 0) return null

        return runCatching {
            val provider = EmbeddedPhotoPickerProviderFactory.create(context)
            val controller = EmbeddedPhotoPickerController()
            val client = object : EmbeddedPhotoPickerClient {
                override fun onSessionOpened(session: EmbeddedPhotoPickerSession) {
                    // An async platform callback, past the outer runCatching. A failure here
                    // must reach onSessionError (scoped-picker fallback), not the main thread.
                    runCatching {
                        surfaceView.setChildSurfacePackage(session.surfacePackage)
                        controller.attach(session)
                        controller.resize(widthPx, heightPx)
                        controller.setVisible(true)
                    }.onFailure(onSessionError)
                }

                override fun onUriPermissionGranted(uris: List<Uri>) = onUriPermissionGranted(uris)

                override fun onUriPermissionRevoked(uris: List<Uri>) = onUriPermissionRevoked(uris)

                override fun onSelectionComplete() = onSelectionComplete()

                override fun onSessionError(cause: Throwable) = onSessionError(cause)
            }
            provider.openSession(
                hostToken,
                surfaceView.display?.displayId ?: Display.DEFAULT_DISPLAY,
                widthPx,
                heightPx,
                buildEmbeddedFeatureInfo(accentColor),
                context.mainExecutor,
                client,
            )
            controller
        }.onFailure { e ->
            Log.d(TAG, "embedded picker skipped: ${e.message}")
            onSessionError(e)
        }.getOrNull()
    }

    @SuppressLint("NewApi") // Setters past level 15 sit behind the level that added them.
    private fun buildEmbeddedFeatureInfo(accentColor: Long?): EmbeddedPhotoPickerFeatureInfo {
        val builder = EmbeddedPhotoPickerFeatureInfo.Builder()
            .setMaxSelectionLimit(1)
            .setMimeTypes(listOf(IMAGE_MIME_TYPE))
            .setOrderedSelection(false)
        accentColor?.let(builder::setAccentColor)
        val extension = extensionVersion()
        if (extension >= EXPANDED_STATE_MIN_EXTENSION) builder.setPickerLaunchedInExpandedState(true)
        if (extension >= CUSTOMIZATION_MIN_EXTENSION) {
            builder.setSelectionParams(
                PhotoPickerSelectionParams.Builder().setMimeTypes(listOf(IMAGE_MIME_TYPE)).build(),
            )
            builder.setUiCustomizationParams(portraitGridCustomization())
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = CUSTOMIZATION_MIN_EXTENSION)
    private fun portraitGridCustomization(): PhotoPickerUiCustomizationParams =
        PhotoPickerUiCustomizationParams.Builder()
            .setAspectRatio(PhotoPickerUiCustomizationParams.ASPECT_RATIO_PORTRAIT_9_16)
            .build()

    private fun extensionVersion(): Int =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            0
        } else {
            runCatching { SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) }.getOrDefault(0)
        }
}

@SuppressLint("NewApi") // A session only arrives from the picker, which is already gated.
class EmbeddedPhotoPickerController internal constructor() {
    private var session: EmbeddedPhotoPickerSession? = null
    private var closed = false
    private var pendingWidth = 0
    private var pendingHeight = 0

    internal fun attach(session: EmbeddedPhotoPickerSession) {
        this.session = session
        if (closed) {
            close()
        } else if (pendingWidth > 0 && pendingHeight > 0) {
            resize(pendingWidth, pendingHeight)
        }
    }

    fun resize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        pendingWidth = widthPx
        pendingHeight = heightPx
        withSession { notifyResized(widthPx, heightPx) }
    }

    fun setVisible(visible: Boolean) {
        withSession { notifyVisibilityChanged(visible) }
    }

    fun close() {
        closed = true
        withSession { close() }
        session = null
    }

    private inline fun withSession(call: EmbeddedPhotoPickerSession.() -> Unit) {
        val activeSession = session ?: return
        runCatching { activeSession.call() }
    }
}
