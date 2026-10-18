package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bsdung.musicassistantcar.data.telegram.MeloQualityChipPolicy.Chip
import com.bsdung.musicassistantcar.data.telegram.MeloQualityChipPolicy.Kind

/**
 * The row of quality pills under the title (docs/ui-spec.md "Hàng viên thông số chất lượng").
 * Which pills exist is decided by [MeloQualityChipPolicy]; this view only draws them and wraps
 * to a second row when a narrow column cannot hold them all. Informational: not clickable.
 */
internal class MeloQualityChipRow(
    context: Context,
    private val c: MeloComponents,
    /** Pill height in px (spec: 32dp). */
    private val chipHeight: Int,
    private val centered: Boolean = false
) : ViewGroup(context) {

    private var shown: List<Chip> = emptyList()

    val isEmpty: Boolean get() = shown.isEmpty()

    /** Returns true when the set of pills changed (the 1 s refresh tick then costs nothing). */
    fun setChips(chips: List<Chip>): Boolean {
        if (chips == shown) return false
        shown = chips
        removeAllViews()
        chips.forEach { addView(pill(it)) }
        contentDescription = chips.joinToString(", ") { if (it.kind == Kind.HI_RES) MeloQualityChipPolicy.HI_RES_DESCRIPTION else it.text }
            .takeIf { chips.isNotEmpty() }
        importantForAccessibility = if (chips.isEmpty()) IMPORTANT_FOR_ACCESSIBILITY_NO else IMPORTANT_FOR_ACCESSIBILITY_YES
        requestLayout()
        return true
    }

    private fun pill(chip: Chip): TextView {
        val s = c.sizes
        val p = c.palette
        val hiRes = chip.kind == Kind.HI_RES
        return TextView(context).apply {
            text = chip.text
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, s.secondaryText)
            setTextColor(if (hiRes) p.onAccent else p.text)
            typeface = if (hiRes) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            background = if (hiRes) c.pill(p.accent) else c.pill(p.surfaceRaised, p.accentSoft)
            setPadding(s.gap * 2, 0, s.gap * 2, 0)
            // The row itself is announced once; the pills are not separate focus stops.
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private val gap get() = c.sizes.gap

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthSpec).takeIf { MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED } ?: Int.MAX_VALUE
        measureChildrenFor(maxWidth)
        val rows = placeRows(maxWidth)
        val width = rows.maxOfOrNull { it.width } ?: 0
        val height = if (rows.isEmpty()) 0 else rows.size * chipHeight + (rows.size - 1) * gap / 2
        setMeasuredDimension(
            resolveSize(if (MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY) maxWidth else width, widthSpec),
            resolveSize(height, heightSpec)
        )
    }

    private fun measureChildrenFor(maxWidth: Int) {
        val limit = if (maxWidth == Int.MAX_VALUE) maxWidth else maxWidth.coerceAtLeast(0)
        for (i in 0 until childCount) {
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(limit, if (limit == Int.MAX_VALUE) MeasureSpec.UNSPECIFIED else MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(chipHeight, MeasureSpec.EXACTLY)
            )
        }
    }

    private class Row(val first: Int, var last: Int, var width: Int)

    private fun placeRows(maxWidth: Int): List<Row> {
        val rows = mutableListOf<Row>()
        var current: Row? = null
        for (i in 0 until childCount) {
            val w = getChildAt(i).measuredWidth
            val row = current
            if (row == null || row.width + gap + w > maxWidth) {
                current = Row(i, i, w).also { rows.add(it) }
            } else {
                row.last = i
                row.width += gap + w
            }
        }
        return rows
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l
        val rows = placeRows(maxWidth)
        var top = 0
        for (row in rows) {
            var left = if (centered) (maxWidth - row.width) / 2 else 0
            for (i in row.first..row.last) {
                val child: View = getChildAt(i)
                child.layout(left, top, left + child.measuredWidth, top + chipHeight)
                left += child.measuredWidth + gap
            }
            top += chipHeight + gap / 2
        }
    }
}
