package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

/**
 * Right-edge A–Z strip: touch or drag to pick a letter. Draws '#' and A–Z (every other letter
 * when the bar is too short for 24dp cells); dragging still reaches every letter.
 * [onLetter] gets (letter, final): final is false while the finger moves, true on lift.
 */
internal class MeloAlphabetBar(
    context: Context,
    private val textColor: Int,
    private val activeColor: Int,
    private val onTouching: (Char?) -> Unit,
    private val onLetter: (Char, Boolean) -> Unit
) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * context.resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT_BOLD
    }
    private var active: Char? = null
    private var touching: Char? = null

    val barWidthPx: Int get() = (28 * density).toInt()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Thanh chữ cái A đến Z. Chạm hoặc kéo để nhảy tới chữ."
    }

    fun setActive(letter: Char?) {
        if (active != letter) {
            active = letter
            invalidate()
        }
    }

    private fun letters(): List<Char> = MeloAlphabetPolicy.visibleLetters(height, (24 * density).toInt())

    override fun onDraw(canvas: Canvas) {
        val list = letters()
        val cell = height.toFloat() / list.size
        val metrics = paint.fontMetrics
        list.forEachIndexed { i, ch ->
            val selected = ch == (touching ?: active)
            paint.color = if (selected) activeColor else textColor
            val y = cell * i + cell / 2 - (metrics.ascent + metrics.descent) / 2
            canvas.drawText(ch.toString(), width / 2f, y, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val ch = MeloAlphabetPolicy.letterAt(event.y, height)
                if (ch != touching) {
                    touching = ch
                    onTouching(ch)
                    invalidate()
                    onLetter(ch, false)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val ch = touching ?: MeloAlphabetPolicy.letterAt(event.y, height)
                touching = null
                onTouching(null)
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP) onLetter(ch, true)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
