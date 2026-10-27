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
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bsdung.musicassistantcar.BuildConfig
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.DisplaySpec
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.Emulation
import com.bsdung.musicassistantcar.data.telegram.MeloSettingsPolicy.Page
import java.util.Locale

/**
 * "Cài đặt" (E102): a main page with grouped rows and three sub pages (Giao diện & màu sắc, Thiết bị & màn hình,
 * Nâng cao). Screen measurements and device emulation (docs/ui-spec.md step 1) live on the last one.
 * Every choice writes through its old save function and old preferences file;
 * it never touches login, library, playlists or cache. Class name and manifest entry are unchanged.
 */
class MeloUiLabActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SONG_COUNT = "melo_song_count"

        /** Library numbers from the host (counts only, no names): live songs, channels by stored / real count, last full sync. */
        const val EXTRA_LIB_LIVE_SONGS = "melo_lib_live_songs"
        const val EXTRA_LIB_STORED_CHANNELS = "melo_lib_stored_channels"
        const val EXTRA_LIB_LIVE_CHANNELS = "melo_lib_live_channels"
        const val EXTRA_LIB_LAST_SYNC_MS = "melo_lib_last_sync_ms"

        /** The host is signed in: shows the account card and "Đăng xuất Telegram" (no TDLib call is made here). */
        const val EXTRA_SIGNED_IN = "melo_signed_in"

        /** Optional page to open instead of the main page: a [MeloSettingsPolicy.Page] name. */
        const val EXTRA_PAGE = "melo_page"

        /** Result code: the owner confirmed "Đăng xuất" here; the host checks it is still signed in and logs out. */
        const val RESULT_LOGOUT = Activity.RESULT_FIRST_USER

        private const val STATE_PAGE = "page"
        private const val STATE_INFO_EXPANDED = "info_expanded"

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
    private var infoExpanded = false
    private var page = Page.HOME
    private var lastInsets: WindowInsetsCompat? = null
    private val prefs by lazy { getSharedPreferences(MeloScreenPolicy.PREFS_NAME, Context.MODE_PRIVATE) }

    /** Theme preset and source display mode live in the library's own preferences file, as before. */
    private val uiPrefs by lazy { getSharedPreferences(UiThemePolicy.PREFS_NAME, Context.MODE_PRIVATE) }
    private val signedIn: Boolean get() = intent.getBooleanExtra(EXTRA_SIGNED_IN, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_MeloVault_Shareable)
        title = "Cài đặt"
        page = Page.fromName(savedInstanceState?.getString(STATE_PAGE) ?: intent.getStringExtra(EXTRA_PAGE))
        infoExpanded = savedInstanceState?.getBoolean(STATE_INFO_EXPANDED, false) ?: false
        // A sub page goes back to the main page; only the main page closes the screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PAGE, page.name)
        outState.putBoolean(STATE_INFO_EXPANDED, infoExpanded)
    }

    private fun goBack() {
        if (page.isSubPage) openPage(Page.HOME) else finish()
    }

    private fun openPage(target: Page) {
        page = target
        render()
    }

    /** Draws [page]. [keepScroll] keeps the place on the page after a choice was changed. */
    private fun render(keepScroll: Boolean = false) {
        val oldScrollY = if (keepScroll) rootScroll?.scrollY ?: 0 else 0
        val m = realMeasurements(this)
        val layout = MeloScreenPolicy.chooseLayout(m.windowWidthDp, m.windowHeightDp, carMode = false, panelCompact = m.panelCompact)
        val sizes = settingsSizes(this)
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
        rootScroll = scroll
        infoHolder = null

        when (page) {
            Page.HOME -> renderHome()
            Page.APPEARANCE -> renderAppearance()
            Page.DEVICES -> renderDevices(m, layout)
            Page.ADVANCED -> renderAdvanced(m, scroll)
        }
        setContentView(scroll)
        if (oldScrollY > 0) {
            scroll.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    scroll.viewTreeObserver.removeOnPreDrawListener(this)
                    scroll.scrollTo(0, oldScrollY)
                    return true
                }
            })
        }
    }

    // ---- Main page ----

    private fun renderHome() {
        val s = components.sizes
        content.addView(header("Cài đặt"))
        if (signedIn) content.addView(accountCard())

        content.addView(groupTitle("GIAO DIỆN"))
        content.addView(settingRow(
            "Giao diện & màu sắc",
            value = null,
            subtitle = MeloSettingsPolicy.homeSummary(
                MeloLibraryPolicy.loadStyle(prefs),
                MeloThemePolicy.load(prefs),
                TelegramVaultBentoPolicy.getSavedSourceDisplayMode(uiPrefs)
            )
        ) { openPage(Page.APPEARANCE) })
        content.addView(settingRow(
            "Màn Đang phát",
            subtitle = MeloSettingsPolicy.nowPlayingName(MeloNowPlayingPolicy.loadStyle(prefs))
        ) { openPage(Page.APPEARANCE) })

        content.addView(groupTitle("THƯ VIỆN & ĐỒNG BỘ"))
        content.addView(settingRow(
            "Đồng bộ Playlist giữa các máy",
            subtitle = "Đồng bộ thủ công qua kênh Telegram có sẵn",
            badge = "Thử nghiệm"
        ) { startActivity(Intent(this, MeloSyncSettingsActivity::class.java)) })

        content.addView(groupTitle("THIẾT BỊ & MÀN HÌNH"))
        content.addView(settingRow("Bố cục Gọn (máy nhỏ)", value = MeloSettingsPolicy.compactName(MeloCompactPolicy.loadSetting(prefs))) {
            openPage(Page.DEVICES)
        })
        content.addView(settingRow("Car Mode (FYT 7870)", value = MeloSettingsPolicy.carName(MeloCarPolicy.loadSetting(prefs))) {
            openPage(Page.DEVICES)
        })
        content.addView(settingRow("Chừa chỗ nút ••• hệ thống", value = WindowInsetsPolicy.loadHandleBand(prefs).displayName) {
            openPage(Page.DEVICES)
        })

        content.addView(groupTitle("NÂNG CAO"))
        content.addView(settingRow("Thông tin màn hình & giả lập") { openPage(Page.ADVANCED) })

        if (signedIn) {
            content.addView(groupTitle("TÀI KHOẢN"))
            content.addView(settingRow(
                "Đăng xuất Telegram",
                subtitle = "Luôn hỏi xác nhận trước khi đăng xuất",
                danger = true,
                chevron = false
            ) { confirmLogout() })
        }

        content.addView(components.text(
            "MeloVault ${BuildConfig.VERSION_NAME} · vc${BuildConfig.VERSION_CODE}",
            s.secondaryText, components.palette.textSecondary
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, s.gap * 2, 0, s.gap)
        })
    }

    /** Account card: counts and the last sync time the host passed in, no TDLib call and no user name. */
    private fun accountCard(): View {
        val s = components.sizes
        val p = components.palette
        val diag = libraryDiag()
        val subtitle = MeloSettingsPolicy.accountSubtitle(
            diag?.liveSongs?.takeIf { it >= 0 },
            diag?.liveChannels?.takeIf { it >= 0 }
        )
        val syncLine = diag?.let { MeloSettingsPolicy.accountSyncLine(it.lastSyncCompletedMs, System.currentTimeMillis()) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = components.roundedBackground(p.surface, stroke = p.border)
            setPadding(s.gap * 3 / 2, s.gap * 3 / 2, s.gap * 3 / 2, s.gap * 3 / 2)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = s.gap
            }
            contentDescription = listOfNotNull("Tài khoản Telegram", subtitle, syncLine).joinToString(". ")
            addView(TextView(context).apply {
                text = "T"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, s.titleText * 1.2f)
                setTextColor(p.onAccent)
                typeface = Typeface.DEFAULT_BOLD
                background = components.pill(p.accent)
                layoutParams = LinearLayout.LayoutParams(s.minTouch, s.minTouch).apply { rightMargin = s.gap * 3 / 2 }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(components.text("Tài khoản Telegram", s.titleText, p.text, bold = true, maxLines = 2))
                addView(components.text(subtitle, s.secondaryText, p.textSecondary, maxLines = 2))
                syncLine?.let { addView(components.text(it, s.secondaryText, p.textSecondary, maxLines = 2)) }
            })
        }
    }

    private fun confirmLogout() {
        // Same words as the old menu; the host (library screen) does the logout when it gets RESULT_LOGOUT.
        AlertDialog.Builder(this, R.style.Theme_MeloVault_Dialog)
            .setTitle("Đăng xuất tài khoản Telegram?")
            .setMessage("Bài đã tải và phiên đăng nhập trên thiết bị sẽ thay đổi.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Đăng xuất") { _, _ ->
                setResult(RESULT_LOGOUT)
                finish()
            }
            .show()
    }

    // ---- Giao diện & màu sắc ----

    private fun renderAppearance() {
        content.addView(header("Giao diện & màu sắc"))

        content.addView(components.sectionTitle("Kiểu thư viện"))
        val libraryStyle = MeloLibraryPolicy.loadStyle(prefs)
        content.addView(choiceRow("Mới", libraryStyle == MeloLibraryPolicy.LibraryStyle.NEW,
            subtitle = "Đầu trang gọn, chip lọc, “Phát hết”") {
            MeloLibraryPolicy.saveStyle(prefs, MeloLibraryPolicy.LibraryStyle.NEW)
            render(keepScroll = true)
        })
        content.addView(choiceRow("Cũ", libraryStyle == MeloLibraryPolicy.LibraryStyle.LEGACY,
            subtitle = "Cockpit / Portrait / Cổ điển") {
            MeloLibraryPolicy.saveStyle(prefs, MeloLibraryPolicy.LibraryStyle.LEGACY)
            render(keepScroll = true)
        })
        content.addView(note("Bản Gọn (Shanling) và Car Mode có màn riêng, không đổi theo mục này."))
        if (MeloSettingsPolicy.presetVisible(libraryStyle)) {
            val preset = UiThemePolicy.getSavedPreset(uiPrefs)
            UiThemePolicy.ThemePreset.entries.forEach { option ->
                content.addView(choiceRow(option.displayName, option == preset) {
                    UiThemePolicy.savePreset(uiPrefs, option)
                    render(keepScroll = true)
                })
            }
        }

        content.addView(components.sectionTitle("Kiểu màu"))
        val colorTheme = MeloThemePolicy.load(prefs)
        content.addView(choiceRow("Hiện tại", colorTheme == MeloThemePolicy.ColorTheme.CURRENT,
            subtitle = "Xanh đêm, ô chữ cái") {
            MeloThemePolicy.save(prefs, MeloThemePolicy.ColorTheme.CURRENT)
            render(keepScroll = true)
        })
        content.addView(choiceRow("Teal sáng", colorTheme == MeloThemePolicy.ColorTheme.TEAL_BRIGHT,
            subtitle = "Sáng hơn, ảnh bìa nhỏ, menu có biểu tượng") {
            MeloThemePolicy.save(prefs, MeloThemePolicy.ColorTheme.TEAL_BRIGHT)
            render(keepScroll = true)
        })
        val rowArtOn = MeloThemePolicy.rowArtSwitch(prefs)
        content.addView(components.spacer(components.sizes.gap / 2))
        content.addView(switchRow("Tải ảnh bìa từ Telegram", "Tắt để dùng ô chữ cái, tiết kiệm mạng", rowArtOn) {
            MeloThemePolicy.setRowArtSwitch(prefs, !rowArtOn)
            render(keepScroll = true)
        })

        content.addView(components.sectionTitle("Hiển thị nguồn Telegram"))
        val sourceModes = TelegramVaultBentoPolicy.SourceDisplayMode.entries
        val sourceMode = TelegramVaultBentoPolicy.getSavedSourceDisplayMode(uiPrefs)
        content.addView(segmented(sourceModes.map { it.displayName }, sourceModes.indexOf(sourceMode)) { index ->
            TelegramVaultBentoPolicy.saveSourceDisplayMode(uiPrefs, sourceModes[index])
            render(keepScroll = true)
        })

        content.addView(components.sectionTitle("Màn Đang phát"))
        val nowStyle = MeloNowPlayingPolicy.loadStyle(prefs)
        content.addView(choiceRow("Studio Deck", nowStyle == MeloNowPlayingPolicy.NowPlayingStyle.STUDIO_DECK,
            subtitle = "Ảnh bìa lớn, −30s / +60s, hẹn giờ tắt, hàng đợi") {
            MeloNowPlayingPolicy.saveStyle(prefs, MeloNowPlayingPolicy.NowPlayingStyle.STUDIO_DECK)
            render(keepScroll = true)
        })
        content.addView(choiceRow("Màn cũ", nowStyle == MeloNowPlayingPolicy.NowPlayingStyle.LEGACY,
            subtitle = "Hộp thoại Đang phát trước đây") {
            MeloNowPlayingPolicy.saveStyle(prefs, MeloNowPlayingPolicy.NowPlayingStyle.LEGACY)
            render(keepScroll = true)
        })
    }

    // ---- Thiết bị & màn hình ----

    private fun renderDevices(m: Measurements, layout: MeloScreenPolicy.Layout) {
        val s = components.sizes
        content.addView(header("Thiết bị & màn hình"))
        content.addView(infoBox(
            "Máy này đang dùng: ${effectiveLayoutName(m, layout)}. Thay đổi có hiệu lực khi quay lại thư viện."
        ))

        content.addView(components.sectionTitle("Bố cục Gọn"))
        val compact = MeloCompactPolicy.loadSetting(prefs)
        val compactChoices = listOf(
            MeloCompactPolicy.CompactSetting.AUTO to "Tự động",
            MeloCompactPolicy.CompactSetting.ON to "Luôn bật",
            MeloCompactPolicy.CompactSetting.OFF to "Tắt"
        )
        content.addView(segmented(compactChoices.map { it.second }, compactChoices.indexOfFirst { it.first == compact }) { index ->
            MeloCompactPolicy.saveSetting(prefs, compactChoices[index].first)
            render(keepScroll = true)
        })
        content.addView(note(MeloSettingsPolicy.compactHint(compact)))
        val emulatingShanling = compact == MeloCompactPolicy.CompactSetting.EMULATE_SHANLING
        content.addView(actionButton(
            (if (emulatingShanling) "✓ Đang thử trong khung Shanling (768 × 1280)" else "Thử trong khung Shanling (768 × 1280)"),
            primary = emulatingShanling
        ) {
            MeloCompactPolicy.saveSetting(prefs, MeloCompactPolicy.CompactSetting.EMULATE_SHANLING)
            render(keepScroll = true)
        })

        content.addView(components.sectionTitle("Car Mode (FYT 7870)"))
        val car = MeloCarPolicy.loadSetting(prefs)
        val carChoices = listOf(MeloCarPolicy.CarSetting.OFF to "Tắt", MeloCarPolicy.CarSetting.ON to "Bật")
        content.addView(segmented(carChoices.map { it.second }, carChoices.indexOfFirst { it.first == car }) { index ->
            MeloCarPolicy.saveSetting(prefs, carChoices[index].first)
            render(keepScroll = true)
        })
        content.addView(note(
            "Nút to, chữ lớn, 5 bài mỗi trang, tua −30s / +60s, ẩn thanh hệ thống; dùng nhạc thật. " +
                "Car Mode đè lên mọi bố cục khác. Chỉ hiện sau khi đã đăng nhập Telegram."
        ))
        listOf(
            MeloCarPolicy.CarSetting.EMULATE_FYT_160 to "Thử khung · mật độ 160",
            MeloCarPolicy.CarSetting.EMULATE_FYT_240 to "Thử khung · mật độ 240"
        ).forEach { (setting, label) ->
            val on = car == setting
            content.addView(actionButton(if (on) "✓ Đang ${label.replaceFirstChar { it.lowercase() }}" else label, primary = on) {
                MeloCarPolicy.saveSetting(prefs, setting)
                render(keepScroll = true)
            }.apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = s.gap / 2
            })
        }
        content.addView(note("Khung thử vẽ đúng 1920 × 1200 điểm ảnh, nên cầm máy ngang."))

        content.addView(components.sectionTitle("Chừa chỗ nút ••• hệ thống"))
        val band = WindowInsetsPolicy.loadHandleBand(prefs)
        val bands = WindowInsetsPolicy.HandleBand.entries
        content.addView(segmented(bands.map { it.displayName }, bands.indexOf(band)) { index ->
            WindowInsetsPolicy.saveHandleBand(prefs, bands[index])
            render(keepScroll = true)
        })
        content.addView(note(MeloSettingsPolicy.bandHint(band)))
    }

    // ---- Nâng cao ----

    private fun renderAdvanced(m: Measurements, scroll: ScrollView) {
        val s = components.sizes
        content.addView(header("Nâng cao"))
        content.addView(note("Dành cho việc chỉnh bố cục. Không động tới đăng nhập, thư viện, playlist hay cache."))

        content.addView(components.sectionTitle("Thông tin màn hình"))
        infoLinesNow = infoLines(m, scroll)
        val holder = FrameLayout(this).apply { addView(infoCard(shownInfo())) }
        infoHolder = holder
        content.addView(holder)
        content.addView(components.spacer(s.gap))
        content.addView(actionButton(if (infoExpanded) "Thu gọn" else "Xem tất cả số đo") {
            infoExpanded = !infoExpanded
            render(keepScroll = true)
        })
        content.addView(components.spacer(s.gap / 2))
        content.addView(actionButton("Sao chép số đo") { copyMeasurements(infoLinesNow) })
        content.addView(note(
            "Mở màn này trên S25 Ultra và Y700 (cầm dọc và ngang), bấm \"Sao chép số đo\" rồi gửi lại để chốt ngưỡng bố cục. " +
                "Nút này luôn sao chép đủ mọi số đo."
        ))

        content.addView(components.sectionTitle("Giả lập thiết bị (thử nghiệm)"))
        content.addView(note(
            "Vẽ giao diện mới trong khung đúng số điểm ảnh và mật độ của máy được chọn. " +
                "Chỉ để kiểm tra bố cục, không đánh giá độ dễ đọc khi lái."
        ))
        val current = MeloScreenPolicy.loadEmulation(prefs)
        Emulation.entries.forEach { emulation ->
            val name = emulation.displayName.substringBefore(" (")
            val detail = if (emulation.displayName.contains(" (")) emulation.displayName.substringAfter(" (").removeSuffix(")") else null
            content.addView(choiceRow(name, emulation == current, subtitle = detail) {
                MeloScreenPolicy.saveEmulation(prefs, emulation)
                render(keepScroll = true)
            })
        }
        val carPreview = MeloScreenPolicy.loadPreviewCarMode(prefs)
        content.addView(components.spacer(s.gap / 2))
        content.addView(switchRow("Xem như Car Mode (chỉ trong khung)", null, carPreview) {
            MeloScreenPolicy.savePreviewCarMode(prefs, !carPreview)
            render(keepScroll = true)
        })
        content.addView(components.spacer(s.gap))
        content.addView(actionButton("Mở khung xem trước thành phần", primary = true) {
            startActivity(Intent(this, MeloUiPreviewActivity::class.java).putExtras(intent))
        })
        content.addView(note(
            "Trong khung: thanh tìm kiếm, chip lọc, dòng bài hát với 5 trạng thái, mini player. " +
                "Dữ liệu trong khung là dữ liệu mẫu. Bấm ✕ hoặc nút Quay lại để thoát."
        ))
    }

    /** The short list by default, the whole list after "Xem tất cả số đo". */
    private fun shownInfo(): List<Pair<String, String>> =
        if (infoExpanded) infoLinesNow else MeloSettingsPolicy.shortInfo(infoLinesNow)

    private fun refreshInfo() {
        val root = rootScroll ?: return
        val holder = infoHolder ?: return
        val lines = infoLines(realMeasurements(this), root)
        if (lines == infoLinesNow) return
        infoLinesNow = lines
        holder.removeAllViews()
        holder.addView(infoCard(shownInfo()))
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

    // ---- Small builders ----

    private fun header(title: String): View {
        val s = components.sizes
        val p = components.palette
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            addView(components.iconButton("←", "Quay lại") { goBack() }.apply { background = null })
            addView(components.text(title, s.titleText * 1.15f, p.text, bold = true, maxLines = 2).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun groupTitle(value: String): TextView =
        components.text(value, components.sizes.secondaryText, components.palette.textSecondary, bold = true).apply {
            setPadding(components.sizes.gap, components.sizes.gap * 2, 0, components.sizes.gap / 2)
        }

    private fun infoBox(value: String): View {
        val s = components.sizes
        val p = components.palette
        return components.text(value, s.secondaryText, p.text, maxLines = 6).apply {
            background = components.roundedBackground(p.accentSoft)
            setPadding(s.gap * 3 / 2, s.gap, s.gap * 3 / 2, s.gap)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = s.gap
            }
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
            setPadding(s.gap * 2, s.gap / 2, s.gap * 2, s.gap / 2)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { onClick() }
        }
    }

    /** A row of the main page: title (+ small badge), optional line under it and the current value; opens a page or a dialog. */
    private fun settingRow(
        title: String,
        subtitle: String? = null,
        value: String? = null,
        badge: String? = null,
        danger: Boolean = false,
        chevron: Boolean = true,
        onClick: () -> Unit
    ): View {
        val s = components.sizes
        val p = components.palette
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            setPadding(s.gap * 3 / 2, s.gap, s.gap * 3 / 2, s.gap)
            background = components.roundedBackground(p.surface, stroke = p.border)
            isClickable = true
            isFocusable = true
            contentDescription = listOfNotNull(title, badge, value, subtitle).joinToString(". ")
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = s.gap / 2
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(components.text(title, s.titleText, if (danger) p.warning else p.text, bold = danger, maxLines = 2).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    badge?.let {
                        addView(components.text(it, s.secondaryText * 0.85f, p.accent, bold = true).apply {
                            background = components.pill(p.accentSoft)
                            setPadding(s.gap, s.gap / 4, s.gap, s.gap / 4)
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                leftMargin = s.gap
                            }
                        })
                    }
                })
                subtitle?.let { addView(components.text(it, s.secondaryText, p.textSecondary, maxLines = 3)) }
            })
            value?.let {
                addView(components.text(it, s.secondaryText, p.textSecondary, maxLines = 2).apply {
                    maxWidth = (resources.displayMetrics.widthPixels * 0.34f).toInt()
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        leftMargin = s.gap
                    }
                })
            }
            if (chevron) {
                addView(components.text("›", s.titleText * 1.2f, p.textSecondary).apply {
                    setPadding(s.gap, 0, 0, 0)
                })
            }
            setOnClickListener { onClick() }
        }
    }

    /** Equal-width buttons in one strip; [selectedIndex] −1 means none of them is the current value. */
    private fun segmented(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit): View {
        val s = components.sizes
        val p = components.palette
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = components.roundedBackground(p.surface, stroke = p.border)
            setPadding(s.gap / 3, s.gap / 3, s.gap / 3, s.gap / 3)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = s.gap / 2
            }
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                addView(TextView(context).apply {
                    text = label
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, s.secondaryText)
                    setTextColor(if (selected) p.onAccent else p.text)
                    typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    background = if (selected) components.roundedBackground(p.accent) else null
                    minHeight = s.minTouch
                    setPadding(s.gap / 2, 0, s.gap / 2, 0)
                    isClickable = true
                    isFocusable = true
                    contentDescription = label + if (selected) ", đang chọn" else ""
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { if (!selected) onSelect(index) }
                })
            }
        }
    }

    /** On/off row with a small track on the right. */
    private fun switchRow(label: String, subtitle: String?, on: Boolean, onToggle: () -> Unit): View {
        val s = components.sizes
        val p = components.palette
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            setPadding(s.gap * 3 / 2, s.gap, s.gap * 3 / 2, s.gap)
            background = components.roundedBackground(p.surface, stroke = p.border)
            isClickable = true
            isFocusable = true
            contentDescription = listOfNotNull(label, if (on) "đang bật" else "đang tắt", subtitle).joinToString(". ")
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = s.gap / 2
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(components.text(label, s.titleText, p.text, maxLines = 2))
                subtitle?.let { addView(components.text(it, s.secondaryText, p.textSecondary, maxLines = 3)) }
            })
            addView(TextView(context).apply {
                text = if (on) "BẬT" else "TẮT"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, s.secondaryText)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (on) p.onAccent else p.textSecondary)
                background = if (on) components.pill(p.accent) else components.pill(p.surfaceRaised, p.border)
                setPadding(s.gap * 3 / 2, s.gap / 2, s.gap * 3 / 2, s.gap / 2)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = s.gap
                }
            })
            setOnClickListener { onToggle() }
        }
    }

    /** One choice of a group: a filled mark, the label and an optional line under it. */
    private fun choiceRow(label: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit): View {
        val s = components.sizes
        val p = components.palette
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            setPadding(s.gap, s.gap / 2, s.gap, s.gap / 2)
            background = components.roundedBackground(if (selected) p.accentSoft else p.surface, stroke = if (selected) p.accent else null)
            isClickable = true
            isFocusable = true
            contentDescription = listOfNotNull(label, subtitle).joinToString(". ") + if (selected) ", đang chọn" else ""
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = s.gap / 2
            }
            addView(components.text(if (selected) "◉" else "○", s.titleText * 1.2f, if (selected) p.accent else p.textSecondary).apply {
                setPadding(0, 0, s.gap * 3 / 2, 0)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(components.text(label, s.titleText, p.text, maxLines = 2))
                subtitle?.let { addView(components.text(it, s.secondaryText, p.textSecondary, maxLines = 3)) }
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
