package org.wishy.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View

/**
 * The pointer as a plain Android View sitting on top of the WebView in the same container.
 * Moving it only changes translationX/Y, which the GPU applies without re-drawing, re-laying-out or
 * re-composing anything. (The old pointer was a Compose layer with an endless animation on top of the page,
 * which re-drew every frame and was the main cause of the flicker and the sluggish feel.)
 */
class PointerView(ctx: Context) : View(ctx) {
    private var bmp: Bitmap? = null
    private var hotX = 0f
    private var hotY = 0f
    private var px = 0f
    private var py = 0f
    private var lastMove = 0L
    private var fadeScheduled = false
    private var faded = false

    private val fade = object : Runnable {
        override fun run() {
            fadeScheduled = false
            val idle = SystemClock.uptimeMillis() - lastMove
            if (idle >= IDLE_MS) {
                faded = true
                animate().alpha(0.35f).setDuration(500L).start()
            } else {
                fadeScheduled = true
                postDelayed(this, IDLE_MS - idle)
            }
        }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = INVISIBLE
        setLayerType(LAYER_TYPE_HARDWARE, null)   // moving a cached texture is nearly free
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(bmp?.width ?: 0, bmp?.height ?: 0)
    }

    override fun onDraw(canvas: Canvas) {
        bmp?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    fun setGlyph(b: Bitmap, hx: Float, hy: Float) {
        if (bmp === b && hx == hotX && hy == hotY) return
        val sizeChanged = bmp?.width != b.width || bmp?.height != b.height
        bmp = b; hotX = hx; hotY = hy
        pivotX = hx; pivotY = hy
        if (sizeChanged) requestLayout()
        invalidate()
        translationX = px - hotX
        translationY = py - hotY
    }

    /** x/y are in the page area's pixels: the point that must land on the click spot. */
    fun moveTo(x: Float, y: Float) {
        px = x; py = y
        translationX = x - hotX
        translationY = y - hotY
        lastMove = SystemClock.uptimeMillis()
        wake()
    }

    fun setShown(show: Boolean) {
        val v = if (show) VISIBLE else INVISIBLE
        if (visibility != v) {
            visibility = v
            if (show) { lastMove = SystemClock.uptimeMillis(); wake() }
        }
    }

    /** A quick press animation when OK is clicked. */
    fun pulse() {
        lastMove = SystemClock.uptimeMillis()
        wake()
        animate().cancel()
        alpha = 1f
        animate().scaleX(0.72f).scaleY(0.72f).setDuration(60L).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
        }.start()
    }

    private fun wake() {
        if (faded) { faded = false; animate().cancel(); alpha = 1f }
        if (!fadeScheduled) { fadeScheduled = true; postDelayed(fade, IDLE_MS) }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(fade); fadeScheduled = false
        super.onDetachedFromWindow()
    }

    private companion object { const val IDLE_MS = 4000L }
}
