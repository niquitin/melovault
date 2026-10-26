package com.bsdung.musicassistantcar.data.telegram

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.bsdung.musicassistantcar.data.telegram.MeloSettingsPolicy.MenuId
import com.bsdung.musicassistantcar.data.telegram.MeloSettingsPolicy.MenuItem
import com.bsdung.musicassistantcar.data.telegram.MeloSettingsPolicy.SyncButton
import com.bsdung.musicassistantcar.data.telegram.MeloSettingsPolicy.SyncCard
import com.bsdung.musicassistantcar.data.telegram.MeloSettingsPolicy.SyncState

/**
 * E102: the ⋮ menu of the library as one popup: the sync card on top, then a few rows, then
 * "Cài đặt". It only draws what [MeloSettingsPolicy] returns and reports taps; the host runs the
 * actions, so this class never touches TDLib, the database or the player.
 */
internal class MeloOverflowMenu(private val activity: Activity) {

    private var popup: PopupWindow? = null

    val isShowing: Boolean get() = popup?.isShowing == true

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    fun show(
        anchor: View,
        card: SyncCard,
        items: List<MenuItem>,
        onSync: (SyncButton) -> Unit,
        onItem: (MenuId) -> Unit
    ) {
        dismiss()
        val components = MeloComponents(activity, MeloUiLabActivity.settingsSizes(activity))
        val s = components.sizes
        val p = components.palette
        val density = activity.resources.displayMetrics.density
        val window = anchor.rootView
        val windowW = window.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        val windowH = window.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels
        val margin = s.screenMargin
        val width = minOf((MENU_WIDTH_DP * density).toInt(), windowW - 2 * margin)

        lateinit var self: PopupWindow
        fun close() = self.dismiss()

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(s.gap, s.gap, s.gap, s.gap)
        }
        if (card.visible) column.addView(syncCard(components, card) { button -> close(); onSync(button) })
        items.forEach { item ->
            if (item.id == MenuId.SETTINGS) column.addView(divider(components))
            column.addView(row(components, item) { close(); onItem(item.id) })
        }

        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        scroll.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val height = minOf(scroll.measuredHeight, windowH - 2 * margin)

        val onScreen = IntArray(2)
        anchor.getLocationInWindow(onScreen)
        // Right edge on the anchor's right edge, kept inside the window; below the anchor when it fits, else pushed up.
        val x = (onScreen[0] + anchor.width - width).coerceIn(margin, maxOf(margin, windowW - width - margin))
        val y = (onScreen[1] + anchor.height).coerceAtMost(windowH - height - margin).coerceAtLeast(margin)

        self = PopupWindow(scroll, width, height, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(components.roundedBackground(p.surface, stroke = p.border))
            elevation = 8 * density
            setOnDismissListener { if (popup === this) popup = null }
        }
        popup = self
        self.showAtLocation(window, Gravity.TOP or Gravity.START, x, y)
    }

    private fun syncCard(c: MeloComponents, card: SyncCard, onButton: (SyncButton) -> Unit): View {
        val s = c.sizes
        val p = c.palette
        val dotColor = when (card.state) {
            SyncState.RUNNING -> p.accent
            SyncState.PAUSED -> p.warning
            SyncState.IDLE_SYNCED -> p.statusOk
            else -> p.textSecondary
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = c.roundedBackground(p.surfaceRaised)
            setPadding(s.gap * 2, s.gap * 3 / 2, s.gap * 2, s.gap * 3 / 2)
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(View(activity).apply {
                    val d = maxOf(s.gap, 8)
                    layoutParams = LinearLayout.LayoutParams(d, d).apply { rightMargin = s.gap }
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(dotColor) }
                })
                addView(c.text(card.line1, s.titleText, p.text, bold = true, maxLines = 2).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
            })
            card.line2?.let { line ->
                addView(c.text(line, s.secondaryText, p.textSecondary, maxLines = 2).apply {
                    setPadding(0, s.gap / 2, 0, 0)
                })
            }
            if (card.primary != null) {
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, s.gap, 0, 0)
                    addView(cardButton(c, card.primary, primary = true, onClick = onButton))
                    card.secondary?.let { addView(cardButton(c, it, primary = false, onClick = onButton)) }
                })
            }
        }
    }

    private fun cardButton(c: MeloComponents, button: SyncButton, primary: Boolean, onClick: (SyncButton) -> Unit): View {
        val s = c.sizes
        val p = c.palette
        return TextView(activity).apply {
            text = button.label
            gravity = Gravity.CENTER
            maxLines = 1
            setTextSize(TypedValue.COMPLEX_UNIT_PX, s.secondaryText)
            setTextColor(if (primary) p.onAccent else p.text)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = if (primary) c.pill(p.accent) else c.pill(p.surface, p.border)
            minHeight = s.minTouch
            setPadding(s.gap, 0, s.gap, 0)
            isClickable = true
            isFocusable = true
            contentDescription = button.label
            layoutParams = LinearLayout.LayoutParams(0, s.minTouch, 1f).apply {
                if (!primary) leftMargin = s.gap
            }
            setOnClickListener { onClick(button) }
        }
    }

    private fun divider(c: MeloComponents): View = View(activity).apply {
        setBackgroundColor(c.palette.border)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = c.sizes.gap
            bottomMargin = c.sizes.gap / 2
        }
    }

    private fun row(c: MeloComponents, item: MenuItem, onClick: () -> Unit): View {
        val s = c.sizes
        val p = c.palette
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            setPadding(s.gap * 3 / 2, s.gap / 2, s.gap * 3 / 2, s.gap / 2)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), c.roundedBackground(p.accentSoft))
                addState(intArrayOf(), ColorDrawable(0))
            }
            isClickable = item.enabled
            isFocusable = item.enabled
            isEnabled = item.enabled
            alpha = if (item.enabled) 1f else 0.4f
            contentDescription = listOfNotNull(item.label, item.subtitle).joinToString(". ")
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(c.text(item.label, s.titleText, p.text, maxLines = 2))
            item.subtitle?.let { addView(c.text(it, s.secondaryText, p.textSecondary, maxLines = 2)) }
            if (item.enabled) setOnClickListener { onClick() }
        }
    }

    private companion object {
        const val MENU_WIDTH_DP = 312
    }
}
