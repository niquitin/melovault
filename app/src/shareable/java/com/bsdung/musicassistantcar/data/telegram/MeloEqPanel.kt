package com.bsdung.musicassistantcar.data.telegram

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.bsdung.musicassistantcar.data.telegram.MeloEqPolicy.Preset

/**
 * The equalizer sheet opened from the Studio Deck "EQ" chip: On/Off switch, preset chips, ten
 * horizontal bars (−12…+12 dB), a note and "Đặt lại". It only writes the eq_* preferences; the
 * playback service watches them and re-tunes [MeloEqAudioProcessor] on the fly, so nothing here
 * touches the player.
 */
internal class MeloEqPanel(private val activity: Activity, private val c: MeloComponents) {
    private val s get() = c.sizes
    private val p get() = c.palette
    private val prefs = activity.getSharedPreferences(MeloEqPolicy.PREFS, Context.MODE_PRIVATE)
    private var settings = MeloEqPolicy.load(prefs)
    private val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar)

    private lateinit var enableSwitch: Switch
    private lateinit var body: LinearLayout
    private val presetHolder = FrameLayout(activity)
    private val bars = ArrayList<SeekBar>()
    private val valueLabels = ArrayList<TextView>()

    /** [onClosed] runs after the sheet is dismissed so the caller can redraw its EQ chip. */
    fun show(onClosed: () -> Unit) {
        if (dialog.isShowing) return
        dialog.setContentView(buildRoot())
        dialog.setOnDismissListener { onClosed() }
        dialog.window?.let { w ->
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.BOTTOM)
            w.setBackgroundDrawable(ColorDrawable(0))
        }
        dialog.show()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun buildRoot(): View {
        val titleBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            addView(c.text("Bộ chỉnh âm (EQ)", s.titleText, p.text, bold = true, maxLines = 2),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(c.iconButton("✕", "Đóng bộ chỉnh âm") { dialog.dismiss() })
        }

        enableSwitch = Switch(activity).apply {
            text = "Bật EQ"
            setTextColor(p.text)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, s.titleText)
            minHeight = s.minTouch
            isChecked = settings.enabled
            setOnCheckedChangeListener { _, checked -> change(settings.copy(enabled = checked)) }
        }

        body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        presetHolder.addView(presetRow())
        body.addView(presetHolder, matchWrap().apply { topMargin = s.gap })
        MeloEqPolicy.BANDS_HZ.forEachIndexed { index, hz -> body.addView(bandRow(index, hz), matchWrap()) }
        body.addView(c.text(
            "EQ xử lý lại âm thanh trong app. Tắt EQ để nghe nguyên bản. " +
                "Âm lượng tự giảm khi tăng dải để không vỡ tiếng.",
            s.secondaryText, p.textSecondary, maxLines = 4,
        ), matchWrap().apply { topMargin = s.gap })
        body.addView(c.chip("Đặt lại", false) { change(MeloEqPolicy.reset(settings)) }.apply {
            contentDescription = "Đặt lại bộ chỉnh âm về phẳng"
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, s.minTouch).apply {
            gravity = Gravity.END; topMargin = s.gap
        })

        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(enableSwitch, matchWrap())
                addView(body, matchWrap())
            })
        }
        render()

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = c.roundedBackground(p.background, s.corner * 2)
            setPadding(s.gap * 2, s.gap, s.gap * 2, s.gap * 2)
            addView(titleBar, matchWrap())
            addView(scroll, matchWrap())
        }
    }

    private fun presetRow(): View {
        val labels = Preset.entries.map { it.label }
        return c.chipRow(labels, Preset.entries.indexOf(settings.preset)) { index ->
            change(settings.copy(preset = Preset.entries[index]), rebuildPresets = false)
        }
    }

    private fun bandRow(index: Int, hz: Int): View {
        val label = c.text(MeloEqPolicy.bandLabel(hz), s.secondaryText, p.textSecondary).apply {
            minWidth = (s.secondaryText * 4.6f).toInt()
        }
        val value = c.text("", s.secondaryText, p.text, bold = true).apply {
            gravity = Gravity.END
            minWidth = (s.secondaryText * 4.2f).toInt()
        }
        val bar = SeekBar(activity).apply {
            max = MeloEqPolicy.MAX_DB - MeloEqPolicy.MIN_DB
            progressTintList = ColorStateList.valueOf(p.accent)
            thumbTintList = ColorStateList.valueOf(p.accent)
            progressBackgroundTintList = ColorStateList.valueOf(p.border)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) change(MeloEqPolicy.onBandDragged(settings, index, progress + MeloEqPolicy.MIN_DB))
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        bars.add(bar)
        valueLabels.add(value)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(bar, LinearLayout.LayoutParams(0, s.minTouch, 1f))
            addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** One entry for every edit: remember it, store it (the service picks it up), redraw. */
    private fun change(next: MeloEqPolicy.Settings, rebuildPresets: Boolean = true) {
        if (next == settings) return
        val presetChanged = next.preset != settings.preset
        settings = next
        MeloEqPolicy.save(prefs.edit(), settings).apply()
        render()
        if (rebuildPresets && presetChanged) {
            presetHolder.removeAllViews()
            presetHolder.addView(presetRow())
        }
    }

    private fun render() {
        if (enableSwitch.isChecked != settings.enabled) enableSwitch.isChecked = settings.enabled
        // Off keeps the bars editable (so a preset can be prepared) but visibly inactive.
        body.alpha = if (settings.enabled) 1f else 0.5f
        val gains = settings.gains()
        MeloEqPolicy.BANDS_HZ.forEachIndexed { i, hz ->
            val progress = gains[i] - MeloEqPolicy.MIN_DB
            if (bars[i].progress != progress) bars[i].progress = progress
            valueLabels[i].text = MeloEqPolicy.gainLabel(gains[i])
            bars[i].contentDescription = MeloEqPolicy.bandDescription(hz, gains[i])
        }
    }
}
