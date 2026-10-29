package com.bsdung.musicassistantcar.data.telegram

import kotlin.math.roundToInt

/**
 * Shared teal design tokens from docs/ui-spec.md (colors, text and control sizes,
 * four size profiles). Pure JVM: ARGB literals and pixel math only.
 *
 * Phone/tablet profiles are specified in dp/sp. Car Mode is specified in real FYT
 * pixels at the panel's physical density, so it converts through millimetres and
 * stays the same physical size whatever density the firmware reports.
 */
internal object MeloDesignTokens {

    data class Palette(
        val background: Int,
        val surface: Int,
        val surfaceRaised: Int,
        val border: Int,
        val accent: Int,
        val accentSoft: Int,
        val text: Int,
        val textSecondary: Int,
        val onAccent: Int,
        val warning: Int,
        /** E98 teal theme only: lower end of the background / accent gradients; equal to the flat colour elsewhere. */
        val backgroundEnd: Int = background,
        val accentEnd: Int = accent,
        /** E98 teal theme only: the "synced" dot and the downloaded tick. */
        val statusOk: Int = accent
    )

    val PALETTE = Palette(
        background = 0xFF0B1622.toInt(),    // Nền toàn màn
        surface = 0xFF12212E.toInt(),       // Thẻ, dòng bài hát, ô tìm kiếm
        surfaceRaised = 0xFF1A2C3A.toInt(), // Nút phụ, chip chưa chọn
        border = 0xFF24394A.toInt(),        // Viền thẻ, đường kẻ
        accent = 0xFF2FD3B0.toInt(),        // Nút Phát, chip chọn, tiến trình, Đã tải
        accentSoft = 0xFF14463F.toInt(),    // Nền dòng đang phát
        text = 0xFFEAF2F5.toInt(),          // Tên bài, tiêu đề
        textSecondary = 0xFF9FB3BF.toInt(), // Tên kênh, thời lượng, ghi chú
        onAccent = 0xFF062019.toInt(),      // Biểu tượng trên nút teal
        warning = 0xFFFF7A6B.toInt()        // Tải lỗi, mất kết nối
    )

    /** Car Mode raises secondary-text contrast for sunlight. */
    val CAR_PALETTE = PALETTE.copy(textSecondary = 0xFFC4D3DB.toInt())

    fun palette(profile: Profile): Palette = if (profile == Profile.CAR) CAR_PALETTE else PALETTE

    /** E98 "Teal sáng": about one step brighter than [PALETTE], with gradient ends for the background and accent. */
    val TEAL_PALETTE = Palette(
        background = 0xFF10263A.toInt(),
        surface = 0xFF16334A.toInt(),
        surfaceRaised = 0xFF1D4058.toInt(),
        border = 0xFF2C5670.toInt(),
        accent = 0xFF36E2C2.toInt(),
        accentSoft = 0xFF1A5552.toInt(),
        text = 0xFFF3FAFC.toInt(),
        textSecondary = 0xFFB8CCD6.toInt(),
        onAccent = 0xFF05231D.toInt(),
        warning = 0xFFFF8B7B.toInt(),
        backgroundEnd = 0xFF0C1C2B.toInt(),
        accentEnd = 0xFF22B8A6.toInt(),
        statusOk = 0xFF3DDC97.toInt()
    )

    /** Car Mode and Gọn never take the teal theme; every other profile gets it only when asked. */
    fun palette(profile: Profile, theme: MeloThemePolicy.ColorTheme): Palette =
        if (theme == MeloThemePolicy.ColorTheme.TEAL_BRIGHT && (profile == Profile.PHONE || profile == Profile.TABLET)) TEAL_PALETTE
        else palette(profile)

    /** Local Phone/Tablet row opt-in: all small text on the highlight meets 4.5:1. */
    fun libraryHighlightedPalette(base: Palette): Palette =
        base.copy(accentSoft = base.surfaceRaised, textSecondary = base.text)

    /** WCAG relative luminance of an ARGB colour (alpha ignored). */
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val v = ((argb shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** WCAG contrast ratio between two colours, 1.0 .. 21.0. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    enum class Profile(val key: String, val displayName: String) {
        COMPACT("compact", "Gọn"),
        PHONE("phone", "Điện thoại"),
        TABLET("tablet", "Tablet"),
        CAR("car", "Car Mode")
    }

    /** Every value is in device pixels, ready for setTextSize(COMPLEX_UNIT_PX) and LayoutParams. */
    data class Sizes(
        val profile: Profile,
        val titleText: Float,
        val playerTitleText: Float,
        val secondaryText: Float,
        val rowArt: Int,
        val rowHeight: Int,
        val playButtonWidth: Int,
        val playButtonHeight: Int,
        val transportHeight: Int,
        val minTouch: Int,
        val corner: Int,
        val gap: Int,
        val screenMargin: Int,
        val miniPlayerHeight: Int,
        val progressHeight: Int,
        val progressThumb: Int,
        val navColumnWidth: Int,
        val playerArt: Int,
        val pageButtonHeight: Int
    )

    /** Physical density (ppi) the Car Mode pixel table in the spec was drawn at: 1920x1200 on 13.1". */
    const val CAR_REFERENCE_PPI = 172.8f

    private data class DpProfile(
        val titleSp: Float, val secondarySp: Float, val artDp: Int, val rowDp: Int,
        val playDp: Int, val marginDp: Int, val navColumnDp: Int
    )

    private val DP_PROFILES = mapOf(
        Profile.COMPACT to DpProfile(16f, 13f, 40, 52, 56, 16, 0),
        Profile.PHONE to DpProfile(16f, 13f, 56, 72, 64, 16, 0),
        Profile.TABLET to DpProfile(17f, 14f, 64, 80, 72, 24, 80)
    )

    const val MIN_TOUCH_DP = 48
    const val CORNER_DP = 16
    const val GAP_DP = 8

    /** Spec table for FYT 7870, pixels at [CAR_REFERENCE_PPI]. */
    object CarReferencePx {
        const val NAV_COLUMN = 160
        const val PLAYER_PANE = 760
        const val LIST_PANE = 1000
        const val PLAYER_TITLE = 48
        const val TITLE = 40
        const val SECONDARY = 30
        const val PLAYER_ART = 360
        const val ROW = 160
        const val ROW_ART = 128
        const val TRANSPORT = 160
        const val PLAY_WIDTH = 240
        const val PLAY_HEIGHT = 160
        const val PROGRESS = 16
        const val PROGRESS_THUMB = 48
        const val PAGE_BUTTON = 140
        const val MIN_TOUCH = 100
        const val CORNER = 16
        const val GAP = 8
        const val MARGIN = 24
        const val ROWS_PER_PAGE = 5
    }

    fun mmToPx(mm: Float, physicalPpi: Float): Float = mm * physicalPpi / 25.4f

    fun pxToMm(px: Float, physicalPpi: Float): Float = px * 25.4f / physicalPpi

    /** Car pixel from the spec table, rescaled to the real panel's physical density (same millimetres). */
    fun carPx(referencePx: Int, physicalPpi: Float): Int =
        (referencePx * physicalPpi / CAR_REFERENCE_PPI).roundToInt().coerceAtLeast(1)

    /**
     * @param density Android density scale (densityDpi / 160).
     * @param fontScale system font scale, applied to sp text only.
     * @param physicalPpi sanitized physical density; used only by [Profile.CAR].
     */
    fun sizes(profile: Profile, density: Float, fontScale: Float, physicalPpi: Float): Sizes {
        require(density > 0f && fontScale > 0f && physicalPpi > 0f)
        if (profile == Profile.CAR) return carSizes(physicalPpi)
        val p = DP_PROFILES.getValue(profile)
        fun dp(v: Int) = (v * density).roundToInt()
        fun sp(v: Float) = v * density * fontScale
        val play = dp(p.playDp)
        return Sizes(
            profile = profile,
            titleText = sp(p.titleSp),
            playerTitleText = sp(p.titleSp + 4f),
            secondaryText = sp(p.secondarySp),
            rowArt = dp(p.artDp),
            rowHeight = dp(p.rowDp),
            playButtonWidth = play,
            playButtonHeight = play,
            transportHeight = maxOf(dp(MIN_TOUCH_DP), dp(p.playDp - 8)),
            minTouch = dp(MIN_TOUCH_DP),
            corner = dp(CORNER_DP),
            gap = dp(GAP_DP),
            screenMargin = dp(p.marginDp),
            // The mini player holds the Play button, so it is never shorter than it (Shanling: 56dp).
            miniPlayerHeight = play,
            progressHeight = dp(3),
            progressThumb = dp(16),
            navColumnWidth = dp(p.navColumnDp),
            playerArt = 0,
            pageButtonHeight = dp(MIN_TOUCH_DP)
        )
    }

    private fun carSizes(ppi: Float): Sizes {
        fun px(v: Int) = carPx(v, ppi)
        return Sizes(
            profile = Profile.CAR,
            titleText = px(CarReferencePx.TITLE).toFloat(),
            playerTitleText = px(CarReferencePx.PLAYER_TITLE).toFloat(),
            secondaryText = px(CarReferencePx.SECONDARY).toFloat(),
            rowArt = px(CarReferencePx.ROW_ART),
            rowHeight = px(CarReferencePx.ROW),
            playButtonWidth = px(CarReferencePx.PLAY_WIDTH),
            playButtonHeight = px(CarReferencePx.PLAY_HEIGHT),
            transportHeight = px(CarReferencePx.TRANSPORT),
            minTouch = px(CarReferencePx.MIN_TOUCH),
            corner = px(CarReferencePx.CORNER),
            gap = px(CarReferencePx.GAP),
            screenMargin = px(CarReferencePx.MARGIN),
            miniPlayerHeight = px(CarReferencePx.ROW),
            progressHeight = px(CarReferencePx.PROGRESS),
            progressThumb = px(CarReferencePx.PROGRESS_THUMB),
            navColumnWidth = px(CarReferencePx.NAV_COLUMN),
            playerArt = px(CarReferencePx.PLAYER_ART),
            pageButtonHeight = px(CarReferencePx.PAGE_BUTTON)
        )
    }
}
