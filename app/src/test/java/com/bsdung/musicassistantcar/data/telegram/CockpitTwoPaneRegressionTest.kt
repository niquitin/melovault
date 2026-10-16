package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.test.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression and boundary test suite for Theme 02: Cockpit 2-Pane Split.
 *
 * Covers:
 * - Theme selection & safe revert across all 3 presets.
 * - Empty-state truthfulness (no fabricated duration, fake codec or fake title).
 * - Display-only title cleaner preserving data model integrity.
 * - Comprehensive FYT head-unit viewport matrix (Full, 2/3, 1/2, 1/3 at 240 & 320 dpi).
 * - Lenovo Y700 Gen 3 landscape, portrait, and taskbar-reduced boundaries.
 * - Short-height landscape window edge cases.
 * - Responsive companion routing in portrait/narrow panes.
 */
class CockpitTwoPaneRegressionTest {

    // ── Theme Selection & Revert ─────────────────────────────────────────────

    @Test
    fun themeSelectionAndRevertSequence() {
        val prefs = FakeSharedPreferences()

        // 1. Fresh installation defaults to Portrait Edge-to-Edge Pro
        assertEquals(UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO, UiThemePolicy.getSavedPreset(prefs))

        // 2. User selects Cockpit 2-Pane Split
        UiThemePolicy.savePreset(prefs, UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE)
        assertEquals(UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE, UiThemePolicy.getSavedPreset(prefs))

        // 3. User selects Classic
        UiThemePolicy.savePreset(prefs, UiThemePolicy.ThemePreset.CLASSIC)
        assertEquals(UiThemePolicy.ThemePreset.CLASSIC, UiThemePolicy.getSavedPreset(prefs))

        // 4. One-action revert back to Theme 01 Portrait Edge-to-Edge Pro
        UiThemePolicy.savePreset(prefs, UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO)
        assertEquals(UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO, UiThemePolicy.getSavedPreset(prefs))
    }

    // ── Empty-State Metadata Truthfulness ────────────────────────────────────

    @Test
    fun emptyStateHasNoFabricatedInformation() {
        // When no track is playing, audio format badge must be null
        val badge = CockpitLayoutPolicy.formatAudioBadge(
            sampleMimeType = null,
            sampleRate = 0,
            channelCount = 0,
            bitrate = 0
        )
        assertNull("Empty state format badge must be null, never fabricated", badge)

        // Duration for non-playing / zero must be null
        assertNull(CockpitLayoutPolicy.formatDuration(0L))
        assertNull(CockpitLayoutPolicy.formatDuration(-1L))

        // Seekbar time label for 0 ms must be "00:00"
        assertEquals("00:00", CockpitLayoutPolicy.formatDurationMs(0L))
    }

    // ── Display-Only Cleaner & Model Integrity ───────────────────────────────

    @Test
    fun displayCleanerPreservesUnderlyingSongModel() {
        val rawFilename = "01_Mùa_Thu_Hà_Nội___Vũ_Thành_An.flac"
        val rawTitle = "01_Mùa_Thu_Hà_Nội___Vũ_Thành_An"
        val originalSong = IndexedTelegramSong(
            accountId = 123456789L,
            chatId = -1001234567890L,
            messageId = 8888L,
            songKey = 9999L,
            fileId = 12345,
            title = rawTitle,
            filename = rawFilename,
            performer = "Vũ Thành An",
            durationSeconds = 285,
            fileSize = 45_000_000L,
            sourceTitle = "Nhạc Trữ Tình Lossless"
        )

        // Cleaned display label removes extensions and compresses underscores
        val cleaned = CockpitLayoutPolicy.cleanTrackTitle(
            TelegramSongDisplayPolicy.label(originalSong.title, originalSong.filename)
        )
        assertEquals("01 Mùa Thu Hà Nội Vũ Thành An", cleaned)

        // Crucial verification: original entity fields must remain 100% untouched for playback, cache & DB
        assertEquals(rawTitle, originalSong.title)
        assertEquals(rawFilename, originalSong.filename)
        assertEquals(12345, originalSong.fileId)
        assertEquals(8888L, originalSong.messageId)
        assertEquals(-1001234567890L, originalSong.chatId)
    }

    // ── FYT 7870 Head-Unit Viewport Matrix ───────────────────────────────────

    @Test
    fun fyt_viewportMatrix_240dpi() {
        // Full screen 1920x1200 @ 240 dpi -> 1280x800 dp
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(1280, 800))

        // 2/3 pane ~1280x1200 @ 240 dpi -> 853x800 dp
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(853, 800))

        // 1/2 pane ~960x1200 @ 240 dpi -> 640x800 dp (height > width: portrait-ish aspect)
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(640, 800))

        // 1/3 pane ~640x1200 @ 240 dpi -> 426x800 dp (width < 580)
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(426, 800))
    }

    @Test
    fun fyt_viewportMatrix_320dpi() {
        // Full screen 1920x1200 @ 320 dpi -> 960x600 dp
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(960, 600))

        // 2/3 pane ~1280x1200 @ 320 dpi -> 640x600 dp
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(640, 600))

        // 1/2 pane ~960x1200 @ 320 dpi -> 600x750 dp (height > width: portrait-ish aspect)
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(600, 750))

        // 1/3 pane ~640x1200 @ 320 dpi -> 400x750 dp (width < 580)
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(400, 750))
    }

    // ── Lenovo Y700 Gen 3 Tablet Boundaries ──────────────────────────────────

    @Test
    fun lenovoY700_landscapeWithAndWithoutTaskbar() {
        // Full landscape: 2048x1280 @ ~340 dpi (or ~2.125 density) -> ~963x602 dp
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(963, 602))

        // Landscape with Android taskbar insets (height reduced by ~48-56 dp -> ~546 dp)
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(963, 546))

        // Full portrait: ~602x963 dp (height > width)
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(602, 963))

        // Portrait with taskbar: ~602x907 dp (height > width)
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(602, 907))
    }

    // ── Short Landscape Window Boundaries ────────────────────────────────────

    @Test
    fun shortLandscapeWindowBoundaries() {
        // 1200x640 px @ 240 dpi -> 800x426 dp
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(800, 426))

        // Compact short landscape e.g. 700x320 dp (exact height boundary 320)
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(700, 320))

        // Vertical height too short: 700x319 dp (< 320 min height)
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(700, 319))
    }

    // ── Responsive Companion Verification ────────────────────────────────────

    @Test
    fun cockpitResponsiveCompanionInPortraitUsesBottomNav() {
        // When user chooses Cockpit 2-Pane, but window is portrait (NARROW or MEDIUM):
        // It must route navigation to bottom navigation bar as responsive companion
        assertTrue(
            ShortWindowLayoutPolicy.shouldUseBottomNav(
                UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE,
                CarPaneLayoutPolicy.Mode.NARROW
            )
        )
        assertTrue(
            ShortWindowLayoutPolicy.shouldUseBottomNav(
                UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE,
                CarPaneLayoutPolicy.Mode.MEDIUM
            )
        )

        // In WIDE mode, bottom nav is not used (handled by 2-pane top nav)
        assertFalse(
            ShortWindowLayoutPolicy.shouldUseBottomNav(
                UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE,
                CarPaneLayoutPolicy.Mode.WIDE
            )
        )
    }

    // ── Pane Width Proportions ───────────────────────────────────────────────

    @Test
    fun paneWeightsAddUpToUnity() {
        assertEquals(
            1.0f,
            CockpitLayoutPolicy.LEFT_PANE_WEIGHT + CockpitLayoutPolicy.RIGHT_PANE_WEIGHT,
            0.001f
        )
    }
}
