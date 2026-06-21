package com.freevibe.service

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.MotionEvent

/**
 * Adds Muzei-style dimming to live wallpapers: the wallpaper renders at reduced
 * brightness by default, and a double-tap temporarily reveals the full image
 * for [REVEAL_DURATION_MS] before re-dimming.
 *
 * Usage in a WallpaperService.Engine:
 * 1. Call [onTouchEvent] from the engine's onTouchEvent
 * 2. Before drawing, call [dimPaint] to get a Paint with the current dim level
 * 3. Call [isRevealing] to check if the wallpaper should be shown at full brightness
 * 4. Call [tick] on each draw to advance the reveal timer
 */
class LiveWallpaperDimming(
    private val dimAmount: Float = DEFAULT_DIM_AMOUNT,
    private val onRevealChanged: () -> Unit = {},
) {
    private var lastTapTimeMs = 0L
    private var revealUntilMs = 0L
    private var dimPaint: Paint? = null

    val isRevealing: Boolean
        get() = System.currentTimeMillis() < revealUntilMs

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return false
        val now = System.currentTimeMillis()
        if (now - lastTapTimeMs < DOUBLE_TAP_TIMEOUT_MS) {
            revealUntilMs = now + REVEAL_DURATION_MS
            lastTapTimeMs = 0L
            onRevealChanged()
            return true
        }
        lastTapTimeMs = now
        return false
    }

    fun tick() {
        if (revealUntilMs > 0 && System.currentTimeMillis() >= revealUntilMs) {
            revealUntilMs = 0
            onRevealChanged()
        }
    }

    fun dimPaint(): Paint? {
        if (isRevealing) return null
        if (dimPaint == null) {
            val matrix = ColorMatrix()
            matrix.setScale(1f - dimAmount, 1f - dimAmount, 1f - dimAmount, 1f)
            dimPaint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }
        }
        return dimPaint
    }

    fun drawDimOverlay(canvas: Canvas, width: Int, height: Int) {
        if (isRevealing) return
        canvas.drawColor(
            android.graphics.Color.argb(
                (dimAmount * 255).toInt().coerceIn(0, 200),
                0, 0, 0,
            ),
        )
    }

    companion object {
        const val DEFAULT_DIM_AMOUNT = 0.25f
        const val DOUBLE_TAP_TIMEOUT_MS = 300L
        const val REVEAL_DURATION_MS = 3_000L
    }
}
