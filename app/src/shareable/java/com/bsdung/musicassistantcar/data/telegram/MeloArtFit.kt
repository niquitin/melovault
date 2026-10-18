package com.bsdung.musicassistantcar.data.telegram

import android.view.View
import android.view.ViewGroup

/** Measures a player column so the cover can take exactly the height the other rows leave. */
internal object MeloArtFit {

    /**
     * Height of everything in [column] except [art] (children measured at [columnWidthPx], with
     * their margins), plus the column padding and the margins around [art] itself.
     */
    fun restHeight(column: ViewGroup, art: View, columnWidthPx: Int): Int {
        val inner = (columnWidthPx - column.paddingLeft - column.paddingRight).coerceAtLeast(0)
        val parentWidth = View.MeasureSpec.makeMeasureSpec(inner, View.MeasureSpec.EXACTLY)
        val parentHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        var sum = column.paddingTop + column.paddingBottom
        for (i in 0 until column.childCount) {
            val child = column.getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as ViewGroup.MarginLayoutParams
            sum += lp.topMargin + lp.bottomMargin
            if (child === art) continue
            child.measure(
                ViewGroup.getChildMeasureSpec(parentWidth, lp.leftMargin + lp.rightMargin, lp.width),
                ViewGroup.getChildMeasureSpec(parentHeight, 0, lp.height)
            )
            sum += child.measuredHeight
        }
        return sum
    }
}
