package com.bsdung.musicassistantcar.data.telegram

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Scrubber that draws the real waveform of the current file (played part in the
 * accent color). Without peaks it is a plain thick progress bar. Drag or tap to seek.
 */
internal class MeloWaveformView(
    context: Context,
    private val accent: Int,
    private val track: Int,
    private val onSeek: (fraction: Float) -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var peaks: FloatArray? = null
    private var progress = 0f
    private var dragging = false
    private var dragFraction = 0f

    init {
        contentDescription = "Thanh tua theo sóng âm"
        isClickable = true
        isFocusable = true
    }

    fun setPeaks(value: FloatArray?) {
        peaks = value?.takeIf { it.isNotEmpty() }
        contentDescription = if (peaks != null) "Thanh tua theo sóng âm" else "Thanh tua"
        invalidate()
    }

    fun setProgress(fraction: Float) {
        if (dragging) return
        progress = fraction.coerceIn(0f, 1f)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val f = MeloNowPlayingPolicy.seekFraction(event.x, width.toFloat())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                dragFraction = f
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> { dragFraction = f; invalidate() }
            MotionEvent.ACTION_UP -> {
                dragging = false
                progress = f
                onSeek(f)
                performClick()
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; invalidate() }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val shown = if (dragging) dragFraction else progress
        val bars = peaks
        if (bars == null) {
            val barH = maxOf(4f, h / 6f)
            val top = (h - barH) / 2f
            paint.color = track
            rect.set(0f, top, w, top + barH)
            canvas.drawRoundRect(rect, barH / 2, barH / 2, paint)
            paint.color = accent
            rect.set(0f, top, w * shown, top + barH)
            canvas.drawRoundRect(rect, barH / 2, barH / 2, paint)
            canvas.drawCircle(w * shown, h / 2f, barH * 1.4f, paint)
            return
        }
        val slot = w / bars.size
        val barW = maxOf(1f, slot * 0.6f)
        bars.forEachIndexed { i, value ->
            val barH = maxOf(2f, value * h * 0.92f)
            val left = i * slot + (slot - barW) / 2f
            paint.color = if ((i + 0.5f) / bars.size <= shown) accent else track
            rect.set(left, (h - barH) / 2f, left + barW, (h + barH) / 2f)
            canvas.drawRoundRect(rect, barW / 2, barW / 2, paint)
        }
        paint.color = accent
        canvas.drawRect(w * shown - 1.5f, 0f, w * shown + 1.5f, h, paint)
    }
}
