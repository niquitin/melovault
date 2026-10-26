package com.bsdung.musicassistantcar.data.telegram

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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

        /** Library numbers from the host (counts only, no names): live songs, channels by stored / real count, last full sync. */
        const val EXTRA_LIB_LIVE_SONGS = "melo_lib_live_songs"
        const val EXTRA_LIB_STORED_CHANNELS = "melo_lib_stored_channels"
        const val EXTRA_LIB_LIVE_CHANNELS = "melo_lib_live_channels"
        const val EXTRA_LIB_LAST_SYNC_MS = "melo_lib_last_sync_ms"

        /** Insets worth reading on a head unit: where its own bars and the card handle press into the window. */
        private val INSET_TYPES = listOf(
            "Inset thanh trạng thái" to WindowInsetsCompat.Type.statusBars(),
            "Inset thanh điều hướng" to WindowInsetsCompat.Type.navigationBars(),
            "Inset thanh tiêu đề (captionBar)" to WindowInsetsCompat.Type.captionBar(),
            "Inset khuyết màn (displayCutout)" to WindowInsetsCompat.Type.displayCutout(),
            "Inset cử chỉ hệ thống" to WindowInsetsCompat.Type.systemGestures(),
            "Inset phần chạm được (tappable)" to WindowInsetsCompat.Type.tappableElement()
        )

        /**
         * Real screen: full display resolution, the app window, the panel's physical density and
         * the physical PANEL size. On a head unit that cuts the screen into app cards,
         * `maximumWindowMetrics` is only the card, so the panel is the largest plausible size among
         * the current display mode, the default display's mode and the window metrics (E95 F03a).
         */
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
            val panel = MeloScreenPolicy.panelSizePx(
                listOf(displayW to displayH) + displayModes(activity).mapNotNull { it?.let { m -> m.physicalWidth to m.physicalHeight } }
            ) ?: (minOf(display.widthPx, display.heightPx) to maxOf(display.widthPx, display.heightPx))
            return Measurements(
                display = display,
                windowWidthPx = windowW,
                windowHeightPx = windowH,
                windowWidthDp = config.screenWidthDp,
                windowHeightDp = config.screenHeightDp,
                smallestWidthDp = config.smallestScreenWidthDp,
                panelShortPx = panel.first,
                panelLongPx = panel.second,
                inMultiWindow = isInMultiWindow(activity)
            )
        }

        /**
         * Sizes for the settings screens and the ⋮ menu: the real device's (Gọn on Shanling, else the same
         * phone/tablet rule as the library and the deck), never Car pixels.
         */
        internal fun settingsSizes(activity: Activity): MeloDesignTokens.Sizes {
            val m = realMeasurements(activity)
            val layout = MeloScreenPolicy.chooseLayout(m.windowWidthDp, m.windowHeightDp, carMode = false, panelCompact = m.panelCompact)
            val profile = if (layout == MeloScreenPolicy.Layout.COMPACT) MeloDesignTokens.Profile.COMPACT
            else MeloLibraryPolicy.profileFor(m.smallestWidthDp)
            return MeloDesignTokens.sizes(profile, m.display.density, m.display.fontScale, m.display.physicalPpi)
        }

        /**
         * Extra top room for the head unit's "•••" card handle at [view]'s window (E95 F04): the
         * policy's answer from where the window really sits on the screen and the owner's
         * "Chừa chỗ nút •••" setting. 0 on a phone's full screen, on Shanling and in the lower
         * window of a top/bottom split. Works inside a Dialog too (the root view is the dialog's).
         */
        internal fun handleBandPx(view: View, insets: WindowInsetsCompat): Int {
            val context = view.context
            val density = context.resources.displayMetrics.density
            val onScreen = IntArray(2)
            runCatching { view.rootView.getLocationOnScreen(onScreen) }
            val activity = activityOf(context)
            return WindowInsetsPolicy.handleBandPx(
                topDp = (onScreen[1] / density).toInt(),
                inMultiWindow = activity != null && isInMultiWindow(activity),
                statusTopPx = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                captionTopPx = insets.getInsets(WindowInsetsCompat.Type.captionBar()).top,
                setting = WindowInsetsPolicy.loadHandleBand(context.getSharedPreferences(MeloScreenPolicy.PREFS_NAME, Context.MODE_PRIVATE)),
                bandPx = (WindowInsetsPolicy.HANDLE_BAND_DP * density).toInt()
            )
        }

        private tailrec fun activityOf(context: Context): Activity? = when (context) {
            is Activity -> context
            is ContextWrapper -> activityOf(context.baseContext)
            else -> null
        }

        /** (display this window is on, the default display); each may be null when firmware refuses. */
        private fun displayModes(activity: Activity): List<Display.Mode?> = listOf(
            runCatching { currentDisplay(activity)?.mode }.getOrNull(),
            runCatching {
                (activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)?.mode
            }.getOrNull()
        )

        @Suppress("DEPRECATION")
        private fun currentDisplay(activity: Activity): Display? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else activity.windowManager.defaultDisplay

        private fun isInMultiWindow(activity: Activity): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && runCatching { activity.isInMultiWindowMode }.getOrDefault(false)
    }

    internal data class Measurements(
        val display: DisplaySpec,
        val windowWidthPx: Int,
        val windowHeightPx: Int,
        val windowWidthDp: Int,
        val windowHeightDp: Int,
        val smallestWidthDp: Int,
        /** Physical panel, short side first, in px (see [realMeasurements]). */
        val panelShortPx: Int = minOf(display.widthPx, display.heightPx),
        val panelLongPx: Int = maxOf(display.widthPx, display.heightPx),
        /** The app shares the screen with another app (split screen / head-unit cards). */
        val inMultiWindow: Boolean = false
    ) {
        val panelShortDp: Int get() = (panelShortPx / display.density).toInt()
        val panelLongDp: Int get() = (panelLongPx / display.density).toInt()

        /** Small panel, not shared: the only case that may use the Gọn layout (E95 F03). */
        val panelCompact: Boolean get() = MeloScreenPolicy.panelCompact(panelShortDp, panelLongDp, inMultiWindow)
    }

    private lateinit var components: MeloComponents
    private lateinit var content: LinearLayout
    private var infoHolder: FrameLayout? = null
    private var rootScroll: ScrollView? = null
    private var infoLinesNow: List<Pair<String, String>> = emptyList()
    private var lastInsets: WindowInsetsCompat? = null
    private val prefs by lazy { getSharedPreferences(MeloScreenPolicy.PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_MeloVault_Shareable)
        title = "Cài đặt giao diện"
        render()
    }

    private fun render() {
        val m = realMeasurements(this)
        val layout = MeloScreenPolicy.chooseLayout(m.windowWidthDp, m.windowHeightDp, carMode = false, panelCompact = m.panelCompact)
        // The settings screen itself always uses the sizes of the real device (Gọn on Shanling, else the
        // same phone/tablet rule as the library and the deck), never Car pixels.
        val profile = if (layout == MeloScreenPolicy.Layout.COMPACT) MeloDesignTokens.Profile.COMPACT
        else MeloLibraryPolicy.profileFor(m.smallestWidthDp)
        val sizes = MeloDesignTokens.sizes(profile, m.display.density, m.display.fontScale, m.display.physicalPpi)
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
            lastInsets = insets
            v.post { refreshInfo() }
            insets
        }
        // The window can change size without this screen being rebuilt: measure again.
        scroll.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) refreshInfo()
        }

        content.addView(header())
        content.addView(components.sectionTitle("Đồng bộ Playlist"))
        content.addView(note(
            "Playlist luôn được lưu trên máy. Đồng bộ đám mây giữa các máy (qua kênh Telegram riêng) chưa được kích hoạt trong bản thử nghiệm; " +
                "màn này cho xem quyền riêng tư và trạng thái."
        ))
        content.addView(actionButton("Mở Đồng bộ Playlist") {
            startActivity(Intent(this, MeloSyncSettingsActivity::class.java))
        })
        content.addView(components.sectionTitle("Thông tin màn hình"))
        rootScroll = scroll
        infoLinesNow = infoLines(m, scroll)
        val holder = FrameLayout(this).apply { addView(infoCard(infoLinesNow)) }
        infoHolder = holder
        content.addView(holder)
        content.addView(components.spacer(sizes.gap))
        content.addView(actionButton("Sao chép số đo") { copyMeasurements(infoLinesNow) })
        content.addView(note(
            "Mở màn này trên S25 Ultra và Y700 (cầm dọc và ngang), bấm \"Sao chép số đo\" rồi gửi lại để chốt ngưỡng bố cục."
        ))

        content.addView(components.sectionTitle("Thư viện"))
        content.addView(note(
            "Mới: Điện thoại dọc (S25), Tablet dọc và Hai khung ngang (Y700) theo đặc tả — đầu trang gọn, chip lọc, " +
                "\"Phát hết\", lưới kênh Telegram. Chọn \"Cũ\" để quay về Cockpit / Portrait trước đây. " +
                "Bản Gọn và Car Mode giữ màn riêng."
        ))
        val libraryStyle = MeloLibraryPolicy.loadStyle(prefs)
        MeloLibraryPolicy.LibraryStyle.entries.forEach { style ->
            content.addView(choiceRow(style.displayName, style == libraryStyle) {
                MeloLibraryPolicy.saveStyle(prefs, style)
                render()
            })
        }

        content.addView(components.sectionTitle("Kiểu màu"))
        content.addView(note(
            "Hiện tại: giao diện như trước. Teal sáng · ảnh bìa: màu sáng hơn, mỗi bài có ảnh bìa nhỏ, hàng menu có biểu tượng. " +
                "Chỉ áp dụng cho Điện thoại dọc, Tablet dọc và Hai khung ngang. Bản Gọn giữ màu cũ và luôn có ảnh bìa nhỏ (tắt bằng công tắc bên dưới); Car Mode không đổi."
        ))
        val colorTheme = MeloThemePolicy.load(prefs)
        MeloThemePolicy.ColorTheme.entries.forEach { theme ->
            content.addView(choiceRow(theme.displayName, theme == colorTheme) {
                MeloThemePolicy.save(prefs, theme)
                render()
            })
        }
        val rowArtOn = MeloThemePolicy.rowArtSwitch(prefs)
        content.addView(choiceRow("Tải ảnh bìa từ Telegram", rowArtOn, checkbox = true) {
            MeloThemePolicy.setRowArtSwitch(prefs, !rowArtOn)
            render()
        })
        content.addView(note(
            "Tắt công tắc này nếu muốn dừng tải ảnh bìa; các dòng sẽ hiện ô chữ cái như cũ."
        ))

        content.addView(components.sectionTitle("Màn Đang phát"))
        content.addView(note(
            "Studio Deck: ảnh bìa lớn, −30s / +60s, hàng nút điều khiển luôn nằm cuối màn, hẹn giờ tắt, " +
                "hàng đợi có trạng thái từng bài và công tắc Tải trước bài kế. Chọn \"Màn cũ\" nếu cần quay về. " +
                "Bản Gọn và Car Mode có màn Đang phát riêng."
        ))
        val nowStyle = MeloNowPlayingPolicy.loadStyle(prefs)
        MeloNowPlayingPolicy.NowPlayingStyle.entries.forEach { style ->
            content.addView(choiceRow(style.displayName, style == nowStyle) {
                MeloNowPlayingPolicy.saveStyle(prefs, style)
                render()
            })
        }

        content.addView(components.sectionTitle("Chừa chỗ nút ••• hệ thống"))
        content.addView(note(
            "Màn ô tô (FYT) vẽ nút ••• của thẻ cửa sổ đè lên đầu app. Tự động: chừa một dải khi app đang chạy trong thẻ chia màn " +
                "và Android không báo thanh trạng thái hay thanh tiêu đề ở đó. Nếu chữ đầu trang vẫn bị che, chọn Luôn chừa; " +
                "nếu thấy dải trống thừa, chọn Không chừa. Quay lại màn chính để thấy thay đổi."
        ))
        val handleBand = WindowInsetsPolicy.loadHandleBand(prefs)
        WindowInsetsPolicy.HandleBand.entries.forEach { setting ->
            content.addView(choiceRow(setting.displayName, setting == handleBand) {
                WindowInsetsPolicy.saveHandleBand(prefs, setting)
                render()
            })
        }

        content.addView(components.sectionTitle("Car Mode (FYT 7870)"))
        content.addView(note(
            "Nút to, chữ lớn, 5 bài mỗi trang, tua −30s / +60s, ẩn thanh hệ thống; dùng nhạc thật. " +
                "Car Mode đè lên mọi bố cục khác. \"Thử trong khung FYT\" vẽ đúng 1920 × 1200 điểm ảnh, " +
                "nên cầm máy ngang. Chỉ hiện sau khi đã đăng nhập Telegram."
        ))
        val carSetting = MeloCarPolicy.loadSetting(prefs)
        MeloCarPolicy.CarSetting.entries.forEach { setting ->
            content.addView(choiceRow(setting.displayName, setting == carSetting) {
                MeloCarPolicy.saveSetting(prefs, setting)
                render()
            })
        }

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

    private fun refreshInfo() {
        val root = rootScroll ?: return
        val holder = infoHolder ?: return
        val lines = infoLines(realMeasurements(this), root)
        if (lines == infoLinesNow) return
        infoLinesNow = lines
        holder.removeAllViews()
        holder.addView(infoCard(lines))
    }

    private fun infoLines(m: Measurements, root: View): List<Pair<String, String>> {
        val d = m.display
        val ppiNote = if (d.physicalPpiReliable) "" else " (máy báo số không hợp lệ, tạm dùng mật độ Android)"
        val modes = displayModes(this)
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) windowManager.currentWindowMetrics.bounds else null
        val onScreen = IntArray(2).also { runCatching { root.getLocationOnScreen(it) } }
        val insets = lastInsets ?: ViewCompat.getRootWindowInsets(root)
        val layout = MeloScreenPolicy.chooseLayout(m.windowWidthDp, m.windowHeightDp, carMode = false, panelCompact = m.panelCompact)
        return listOf(
            "Tấm nền (vật lý)" to "${m.panelShortPx} × ${m.panelLongPx} px · ${m.panelShortDp} × ${m.panelLongDp} dp",
            "Cửa sổ tối đa (maximumWindowMetrics)" to "${d.widthPx} × ${d.heightPx} px",
            "Cửa sổ app" to "${m.windowWidthPx} × ${m.windowHeightPx} px",
            "Độ rộng × cao (dp)" to "${m.windowWidthDp} × ${m.windowHeightDp} dp",
            "Vị trí cửa sổ (bounds)" to (bounds?.let { "trái ${it.left} · trên ${it.top} px" } ?: "không rõ (Android dưới 11)") +
                " · view gốc ở ${onScreen[0]},${onScreen[1]}",
            "Chế độ nhiều cửa sổ" to if (m.inMultiWindow) "Có (app dùng chung màn hình)" else "Không",
            "Display ID" to (runCatching { currentDisplay(this)?.displayId }.getOrNull()?.toString() ?: "không rõ"),
            "Chế độ màn hiện tại" to modeText(modes[0]),
            "Chế độ màn mặc định" to modeText(modes[1]),
            "Cạnh ngắn nhất" to "${m.smallestWidthDp} dp",
            "Mật độ Android" to "${d.densityDpi} dpi (${MeloScreenPolicy.densityBucket(d.densityDpi)}, ×${fmt(d.density, 2)})",
            "Mật độ vật lý (xdpi × ydpi)" to "${fmt(d.xdpi, 1)} × ${fmt(d.ydpi, 1)} ppi$ppiNote",
            "Kích thước ước tính" to "${fmt(d.widthMm, 0)} × ${fmt(d.heightMm, 0)} mm · ${fmt(d.diagonalInches, 1)}\"",
            "1 dp trên màn này" to "${fmt(d.dpInMm, 3)} mm (48 dp ≈ ${fmt(d.dpInMm * 48, 1)} mm)",
            "Cỡ chữ hệ thống" to "×${fmt(d.fontScale, 2)}",
            "Hướng màn" to if (m.windowWidthPx > m.windowHeightPx) "Ngang" else "Dọc",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Máy" to "${Build.MANUFACTURER} ${Build.MODEL}"
        ) + INSET_TYPES.map { (label, type) -> label to insetsText(insets, type) } + listOf(
            "Panel nhỏ (đủ điều kiện Gọn)" to if (m.panelCompact) "Có" else "Không",
            "Bố cục thực tế đang dùng" to effectiveLayoutName(m, layout),
            "Màn Đang phát (Studio Deck)" to
                if (MeloNowPlayingPolicy.deckLayout(m.windowWidthDp, m.windowHeightDp) == MeloNowPlayingPolicy.DeckLayout.LANDSCAPE) "Hai cột" else "Một cột"
        ) + MeloLibraryPolicy.libraryDiagLines(libraryDiag())
    }

    /** The host's library numbers, or null when the screen was opened before any channel list was built. */
    private fun libraryDiag(): MeloLibraryPolicy.LibraryDiag? {
        if (!intent.hasExtra(EXTRA_LIB_LIVE_SONGS)) return null
        return MeloLibraryPolicy.LibraryDiag(
            liveSongs = intent.getIntExtra(EXTRA_LIB_LIVE_SONGS, -1),
            storedChannels = intent.getIntExtra(EXTRA_LIB_STORED_CHANNELS, -1),
            liveChannels = intent.getIntExtra(EXTRA_LIB_LIVE_CHANNELS, -1),
            lastSyncCompletedMs = intent.getLongExtra(EXTRA_LIB_LAST_SYNC_MS, 0L)
        )
    }

    private fun modeText(mode: Display.Mode?): String =
        mode?.let { "${it.physicalWidth} × ${it.physicalHeight} px · ${fmt(it.refreshRate, 0)} Hz" } ?: "không rõ"

    private fun insetsText(insets: WindowInsetsCompat?, type: Int): String {
        if (insets == null) return "chưa có (view chưa gắn vào cửa sổ)"
        val i = insets.getInsets(type)
        return "trên ${i.top} · dưới ${i.bottom} · trái ${i.left} · phải ${i.right} px" + if (insets.isVisible(type)) "" else " (ẩn)"
    }

    /** The screen the app really shows now, mirroring TelegramPreviewActivity.applyMeloLayer (signed in assumed). */
    private fun effectiveLayoutName(m: Measurements, layout: MeloScreenPolicy.Layout): String {
        if (MeloCarPolicy.shouldUseCar(MeloCarPolicy.loadSetting(prefs), true)) return MeloScreenPolicy.Layout.CAR.displayName
        if (MeloCompactPolicy.shouldUseCompact(MeloCompactPolicy.loadSetting(prefs), m.panelCompact, true)) {
            return MeloScreenPolicy.Layout.COMPACT.displayName
        }
        val newLayer = MeloLibraryPolicy.newLayer(MeloLibraryPolicy.loadStyle(prefs), layout, true)
        return if (newLayer == null) "Giao diện cũ (${layout.displayName})" else layout.displayName
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
