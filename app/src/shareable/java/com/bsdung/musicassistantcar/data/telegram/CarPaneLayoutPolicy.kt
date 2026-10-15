package com.bsdung.musicassistantcar.data.telegram

/**
 * Responsive layout policy based on the *available app window*, not the physical
 * FYT LCD or host-device display size.
 *
 * FYT 7870 head-unit is 1920×1200 px. Approximate app-pane pixel widths:
 *   2/3 pane ≈ 1280×1200 px  →  853×800 dp @240 dpi  |  640×600 dp @320 dpi
 *   1/2 pane ≈  960×1200 px  →  600×750 dp @320 dpi
 *   1/3 pane ≈  640×1200 px  →  400×750 dp @320 dpi
 *
 * The previous policy required widthDp ≥ 1.15 × heightDp for WIDE, which
 * excluded the 2/3 pane (ratio ≈ 1.067). E9.1-T.1 replaces that ratio guard
 * with an aspect-ratio check (width ≥ height) combined with a minimum
 * column-viable width so that genuine narrow-portrait panes stay MEDIUM/NARROW.
 *
 * Mode assignment:
 *   WIDE   — widthDp ≥ 540 AND widthDp ≥ heightDp   (landscape-ish or wide-pane)
 *   MEDIUM — widthDp ≥ 450                           (portrait tablet / half-pane)
 *   NARROW — everything else                          (narrow pane or small phone)
 *
 * Boundary rationale:
 *   - 450 dp preserves the legacy "car nav narrow pane" (≤420 dp) as NARROW.
 *   - FYT 1/2 pane at 600 dp → MEDIUM ✓
 *   - FYT 1/3 pane at 400 dp → NARROW ✓
 *   - Sony portrait at 549 dp → MEDIUM ✓
 *   - Phone portrait at 360–430 dp → NARROW ✓
 */
internal object CarPaneLayoutPolicy {
    enum class Mode { NARROW, MEDIUM, WIDE }

    fun modeFor(widthDp: Int, heightDp: Int): Mode {
        if (widthDp <= 0 || heightDp <= 0) return Mode.MEDIUM
        // WIDE: sufficiently wide AND landscape-oriented (width >= height).
        // Minimum 540 dp ensures both columns have viable content width.
        if (widthDp >= 540 && widthDp >= heightDp) return Mode.WIDE
        // MEDIUM: tablet portrait, FYT 1/2 pane, Sony portrait, medium phones.
        if (widthDp >= 450) return Mode.MEDIUM
        return Mode.NARROW
    }

    fun compactFor(widthDp: Int, heightDp: Int): Boolean =
        modeFor(widthDp, heightDp) == Mode.NARROW && heightDp in 1..739

    /**
     * Square artwork size in dp. Never stretched into a banner — FIT_CENTER/
     * CENTER_INSIDE preserves aspect ratio in the ImageView scaleType.
     *
     * WIDE: cap at 45% of window width so the right column has ≥55% for
     * title, transport controls and queue — handles the FYT 2/3 pane safely
     * at both 240 dpi (853 dp wide → art ≤ 383 dp) and 320 dpi (640 dp
     * wide → art ≤ 288 dp).
     */
    fun coverSizeDp(widthDp: Int, heightDp: Int): Int {
        if (widthDp <= 0 || heightDp <= 0) return 180
        val limit = when (modeFor(widthDp, heightDp)) {
            Mode.WIDE   -> minOf(widthDp * 45 / 100, heightDp - 120, 420)
            Mode.MEDIUM -> minOf(widthDp - 50, heightDp * 2 / 5, 340)
            Mode.NARROW -> minOf(widthDp - 28, heightDp * 2 / 5, 330)
        }
        return limit.coerceAtLeast(96)
    }
}
