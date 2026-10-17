package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.DisplaySpec

/**
 * Test-only device emulation shared by the UI lab preview and the Gọn layout:
 * a context reporting the emulated density/dp size, and a frame with the device's
 * exact pixels, centered on a dimmed backdrop, scaled down only when it does not fit.
 */
internal object MeloEmulation {

    const val DIMMED = 0xFF03070B.toInt()

    /** Resources inside the frame report the emulated density and dp size, like the real device. */
    fun emulatedContext(base: Context, spec: DisplaySpec): Context {
        val config = Configuration(base.resources.configuration).apply {
            densityDpi = spec.densityDpi
            screenWidthDp = spec.widthDp
            screenHeightDp = spec.heightDp
            smallestScreenWidthDp = spec.smallestWidthDp
            fontScale = spec.fontScale
            orientation = if (spec.isLandscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        return ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_MusicAssistantCar)
    }

    /**
     * [frameSpec] null = fill the real window. Otherwise the frame is exactly
     * frameSpec.widthPx × heightPx and a notice appears when it has to shrink.
     */
    class FrameHost(hostContext: Context, frameContext: Context, private val frameSpec: DisplaySpec?, frameBackground: Int) {
        val root: FrameLayout = FrameLayout(hostContext).apply { setBackgroundColor(DIMMED) }
        val frame: FrameLayout = FrameLayout(frameContext).apply { setBackgroundColor(frameBackground) }
        private val scaleNote = TextView(hostContext).apply {
            setTextColor(0xFFFFD27A.toInt())
            textSize = 14f
            setBackgroundColor(0xCC000000.toInt())
            setPadding(24, 12, 24, 12)
            visibility = View.GONE
        }

        init {
            if (frameSpec != null) {
                root.addView(frame, FrameLayout.LayoutParams(frameSpec.widthPx, frameSpec.heightPx, Gravity.CENTER))
            } else {
                root.addView(frame, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            root.addView(scaleNote, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ))
            root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> fit(r - l, b - t) }
        }

        private fun fit(availableW: Int, availableH: Int) {
            val spec = frameSpec ?: return
            val scale = MeloScreenPolicy.fitScale(spec.widthPx, spec.heightPx, availableW, availableH)
            if (frame.scaleX == scale && frame.scaleY == scale) return
            frame.pivotX = spec.widthPx / 2f
            frame.pivotY = spec.heightPx / 2f
            frame.scaleX = scale
            frame.scaleY = scale
            if (scale < 1f) {
                scaleNote.text = "Khung ${spec.widthPx}×${spec.heightPx} lớn hơn màn này: đang thu nhỏ còn ${(scale * 100).toInt()}%. Xoay máy để xem đúng 1:1."
                scaleNote.visibility = View.VISIBLE
            } else {
                scaleNote.visibility = View.GONE
            }
        }
    }
}
