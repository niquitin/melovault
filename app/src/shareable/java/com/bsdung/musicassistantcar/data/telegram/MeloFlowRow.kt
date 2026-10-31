package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * UI-07: a row of chips that moves a chip to the next line when it no longer fits (large system
 * font), instead of cutting it off at the edge. With room for everything it is one plain row.
 * Children keep their own margins; [rowGapPx] separates the lines.
 */
internal class MeloFlowRow(context: Context, private val rowGapPx: Int) : ViewGroup(context) {

    /** Where a child goes: top-left corner inside the row, and the row's total height. */
    class Placement(val xs: IntArray, val ys: IntArray, val height: Int)

    companion object {
        /**
         * [widths] and [heights] include each child's margins; [trailing] is the part of the width after the
         * visible edge (its right margin), which does not have to fit on the line.
         */
        fun place(widths: IntArray, heights: IntArray, trailing: IntArray, maxWidth: Int, rowGapPx: Int): Placement {
            val xs = IntArray(widths.size)
            val ys = IntArray(widths.size)
            var x = 0
            var y = 0
            var lineHeight = 0
            for (i in widths.indices) {
                if (x > 0 && x + widths[i] - trailing[i] > maxWidth) {
                    y += lineHeight + rowGapPx
                    x = 0
                    lineHeight = 0
                }
                xs[i] = x
                ys[i] = y
                x += widths[i]
                lineHeight = maxOf(lineHeight, heights[i])
            }
            return Placement(xs, ys, if (widths.isEmpty()) 0 else y + lineHeight)
        }
    }

    private var placement: Placement? = null

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams
    override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(p: LayoutParams): LayoutParams =
        if (p is MarginLayoutParams) MarginLayoutParams(p) else MarginLayoutParams(p)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val unbounded = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
        val maxWidth = if (unbounded) Int.MAX_VALUE else (MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val shown = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }
        val widths = IntArray(shown.size)
        val heights = IntArray(shown.size)
        val trailing = IntArray(shown.size)
        shown.forEachIndexed { i, child ->
            val lp = child.layoutParams as MarginLayoutParams
            val margins = lp.leftMargin + lp.rightMargin
            child.measure(
                if (unbounded) MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                else MeasureSpec.makeMeasureSpec((maxWidth - margins).coerceAtLeast(0), MeasureSpec.AT_MOST),
                getChildMeasureSpec(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0, lp.height)
            )
            widths[i] = child.measuredWidth + margins
            heights[i] = child.measuredHeight + lp.topMargin + lp.bottomMargin
            trailing[i] = lp.rightMargin
        }
        val result = place(widths, heights, trailing, maxWidth, rowGapPx)
        placement = result
        val usedWidth = shown.indices.maxOfOrNull { result.xs[it] + widths[it] - trailing[it] } ?: 0
        setMeasuredDimension(
            resolveSize(usedWidth + paddingLeft + paddingRight, widthSpec),
            resolveSize(result.height + paddingTop + paddingBottom, heightSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val result = placement ?: return
        var index = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as MarginLayoutParams
            val left = paddingLeft + result.xs[index] + lp.leftMargin
            val top = paddingTop + result.ys[index] + lp.topMargin
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
            index++
        }
    }
}
