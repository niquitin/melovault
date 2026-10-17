package com.bsdung.musicassistantcar.data.telegram

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.DisplaySpec
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.Emulation
import java.util.Locale

/**
 * "Cài đặt giao diện (thử nghiệm)": screen measurements and device emulation
 * (docs/ui-spec.md step 1). Reads and writes only its own preferences file;
 * never touches login, library, playlists or cache.
 */
class MeloUiLabActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SONG_COUNT = "melo_song_count"

        /** Real screen: full display resolution, the app window, and the panel's physical density. */
        internal fun realMeasurements(activity: Activity): Measurements {
            val metrics = activity.resources.displayMetrics
            val config = activity.resources.configuration
            val (displayW, displayH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = activity.windowManager.maximumWindowMetrics.bounds
                b.width() to b.height()
            } else {
                val real = DisplayMetrics()
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.getRealMetrics(real)
                real.widthPixels to real.heightPixels
            }
            val (windowW, windowH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = activity.windowManager.currentWindowMetrics.bounds
                b.width() to b.height()
            } else {
                metrics.widthPixels to metrics.heightPixels
            }
            val display = DisplaySpec(
                widthPx = maxOf(displayW, 1), heightPx = maxOf(displayH, 1),
                densityDpi = metrics.densityDpi, xdpi = metrics.xdpi, ydpi = metrics.ydpi,
                fontScale = config.fontScale
            )
            return Measurements(
                display = display,
                windowWidthPx = windowW,
                windowHeightPx = windowH,
                windowWidthDp = config.screenWidthDp,
                windowHeightDp = config.screenHeightDp,
                smallestWidthDp = config.smallestScreenWidthDp
            )
        }
    }

    internal data class Measurements(
        val display: DisplaySpec,
        val windowWidthPx: Int,
        val windowHeightPx: Int,
        val windowWidthDp: Int,
        val windowHeightDp: Int,
        val smallestWidthDp: Int
    )

    private lateinit var components: MeloComponents
    private lateinit var content: LinearLayout
    private val prefs by lazy { getSharedPreferences(MeloScreenPolicy.PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_MusicAssistantCar)
        title = "Cài đặt giao diện"
        render()
    }

    private fun render() {
        val m = realMeasurements(this)
        val layout = MeloScreenPolicy.chooseLayout(m.windowWidthDp, m.windowHeightDp, carMode = false)
        val profile = MeloScreenPolicy.profileFor(layout, m.smallestWidthDp)
        // The settings screen itself always uses the tablet/phone sizes of the real device, never Car pixels.
        val sizes = MeloDesignTokens.sizes(
            if (profile == MeloDesignTokens.Profile.CAR) MeloDesignTokens.Profile.TABLET else profile,
            m.display.density, m.display.fontScale, m.display.physicalPpi
        )
        components = MeloComponents(this, sizes)
        val p = components.palette

        val scroll = ScrollView(this).apply {
            setBackgroundColor(p.background)
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sizes.screenMargin, sizes.gap, sizes.screenMargin, sizes.screenMargin)
        }
        scroll.addView(content)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        content.addView(header())
        content.addView(components.sectionTitle("Thông tin màn hình"))
        val info = infoLines(m, layout)
        content.addView(infoCard(info))
        content.addView(components.spacer(sizes.gap))
        content.addView(actionButton("Sao chép số đo") { copyMeasurements(info) })
        content.addView(note(
            "Mở màn này trên S25 Ultra và Y700 (cầm dọc và ngang), bấm \"Sao chép số đo\" rồi gửi lại để chốt ngưỡng bố cục."
        ))

        content.addView(components.sectionTitle("Bố cục Gọn (Shanling M3 Ultra)"))
        content.addView(note(
            "Thư viện, Telegram và Đang phát rút gọn cho máy nhỏ, dùng nhạc thật của bạn. " +
                "\"Thử trong khung Shanling\" vẽ bản Gọn đúng 768 × 1280 điểm ảnh trên máy này. " +
                "Chỉ hiện sau khi đã đăng nhập Telegram. Quay lại màn chính để thấy thay đổi."
        ))
        val compactSetting = MeloCompactPolicy.loadSetting(prefs)
        MeloCompactPolicy.CompactSetting.entries.forEach { setting ->
            content.addView(choiceRow(setting.displayName, setting == compactSetting) {
                MeloCompactPolicy.saveSetting(prefs, setting)
                render()
            })
        }

        content.addView(components.sectionTitle("Giả lập thiết bị (thử nghiệm)"))
        content.addView(note(
            "Vẽ giao diện mới trong khung đúng số điểm ảnh và mật độ của máy được chọn. " +
                "Chỉ để kiểm tra bố cục, không đánh giá độ dễ đọc khi lái. " +
                "Không động tới đăng nhập, thư viện, playlist hay cache."
        ))
        val current = MeloScreenPolicy.loadEmulation(prefs)
        Emulation.entries.forEach { emulation ->
            content.addView(choiceRow(emulation.displayName, emulation == current) {
                MeloScreenPolicy.saveEmulation(prefs, emulation)
                render()
            })
        }
        val carPreview = MeloScreenPolicy.loadPreviewCarMode(prefs)
        content.addView(choiceRow("Xem như Car Mode (chỉ trong khung xem trước)", carPreview, checkbox = true) {
            MeloScreenPolicy.savePreviewCarMode(prefs, !carPreview)
            render()
        })
        content.addView(components.spacer(sizes.gap))
        content.addView(actionButton("Mở khung xem trước thành phần", primary = true) {
            startActivity(Intent(this, MeloUiPreviewActivity::class.java).putExtras(intent))
        })
        content.addView(note(
            "Trong khung: thanh tìm kiếm, chip lọc, dòng bài hát với 5 trạng thái, mini player. " +
                "Dữ liệu trong khung là dữ liệu mẫu. Bấm ✕ hoặc nút Quay lại để thoát."
        ))
        setContentView(scroll)
    }

    private fun infoLines(m: Measurements, layout: MeloScreenPolicy.Layout): List<Pair<String, String>> {
        val d = m.display
        val ppiNote = if (d.physicalPpiReliable) "" else " (máy báo số không hợp lệ, tạm dùng mật độ Android)"
        return listOf(
            "Độ phân giải màn" to "${d.widthPx} × ${d.heightPx} px",
            "Cửa sổ app" to "${m.windowWidthPx} × ${m.windowHeightPx} px",
            "Độ rộng × cao (dp)" to "${m.windowWidthDp} × ${m.windowHeightDp} dp",
            "Cạnh ngắn nhất" to "${m.smallestWidthDp} dp",
            "Mật độ Android" to "${d.densityDpi} dpi (${MeloScreenPolicy.densityBucket(d.densityDpi)}, ×${fmt(d.density, 2)})",
            "Mật độ vật lý (xdpi × ydpi)" to "${fmt(d.xdpi, 1)} × ${fmt(d.ydpi, 1)} ppi$ppiNote",
            "Kích thước ước tính" to "${fmt(d.widthMm, 0)} × ${fmt(d.heightMm, 0)} mm · ${fmt(d.diagonalInches, 1)}\"",
            "1 dp trên màn này" to "${fmt(d.dpInMm, 3)} mm (48 dp ≈ ${fmt(d.dpInMm * 48, 1)} mm)",
            "Cỡ chữ hệ thống" to "×${fmt(d.fontScale, 2)}",
            "Hướng màn" to if (m.windowWidthPx > m.windowHeightPx) "Ngang" else "Dọc",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Máy" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Bố cục sẽ chọn" to layout.displayName
        )
    }

    private fun fmt(value: Float, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)

    private fun header(): View {
        val s = components.sizes
        val p = components.palette
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            addView(components.iconButton("←", "Quay lại") { finish() }.apply { background = null })
            addView(components.text("Cài đặt giao diện (thử nghiệm)", s.titleText * 1.15f, p.text, bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun infoCard(lines: List<Pair<String, String>>): View {
        val s = components.sizes
        val p = components.palette
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = components.roundedBackground(p.surface, stroke = p.border)
            setPadding(s.gap * 2, s.gap, s.gap * 2, s.gap)
            lines.forEach { (label, value) ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, s.gap / 2, 0, s.gap / 2)
                    addView(components.text(label, s.secondaryText, p.textSecondary, maxLines = 2).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f)
                    })
                    addView(components.text(value, s.secondaryText, p.text, bold = true, maxLines = 3).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.55f)
                        setTextIsSelectable(true)
                    })
                })
            }
        }
    }

    private fun note(value: String): TextView =
        components.text(value, components.sizes.secondaryText, components.palette.textSecondary, maxLines = 6).apply {
            setPadding(0, components.sizes.gap, 0, components.sizes.gap)
        }

    private fun actionButton(label: String, primary: Boolean = false, onClick: () -> Unit): TextView {
        val s = components.sizes
        val p = components.palette
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, s.titleText)
            setTextColor(if (primary) p.onAccent else p.text)
            typeface = Typeface.DEFAULT_BOLD
            background = if (primary) components.pill(p.accent) else components.pill(p.surfaceRaised, p.border)
            minHeight = s.minTouch
            setPadding(s.gap * 2, 0, s.gap * 2, 0)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.minTouch)
            setOnClickListener { onClick() }
        }
    }

    private fun choiceRow(label: String, selected: Boolean, checkbox: Boolean = false, onClick: () -> Unit): View {
        val s = components.sizes
        val p = components.palette
        val mark = when {
            checkbox && selected -> "☑"
            checkbox -> "☐"
            selected -> "◉"
            else -> "○"
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            setPadding(s.gap, 0, s.gap, 0)
            background = components.roundedBackground(if (selected) p.accentSoft else p.surface)
            isClickable = true
            isFocusable = true
            contentDescription = label + if (selected) ", đang chọn" else ""
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = s.gap / 2
            }
            addView(components.text(mark, s.titleText * 1.2f, if (selected) p.accent else p.textSecondary).apply {
                setPadding(0, 0, s.gap * 3 / 2, 0)
            })
            addView(components.text(label, s.titleText, p.text, maxLines = 2).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            setOnClickListener { onClick() }
        }
    }

    private fun copyMeasurements(lines: List<Pair<String, String>>) {
        val text = "MeloVault – Thông tin màn hình\n" + lines.joinToString("\n") { "${it.first}: ${it.second}" }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MeloVault screen info", text))
        Toast.makeText(this, "Đã sao chép số đo", Toast.LENGTH_SHORT).show()
    }
}
