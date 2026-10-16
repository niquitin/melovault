package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CockpitLayoutPolicy:
 * - 2-pane eligibility across Y700, FYT proxies, Sony and phone viewports.
 * - Minimum pane width constraints (Left >= 240dp, Right >= 310dp).
 * - Display-only track title cleaning (extensions and underscores).
 * - Truthful duration formatting (mm:ss and h:mm:ss).
 * - Verified audio format badge formatting without fabrication.
 */
class CockpitLayoutPolicyTest {

    // ── 2-Pane Eligibility ───────────────────────────────────────────────────

    @Test
    fun y700_landscape_isEligible() {
        // Lenovo Y700 Gen 3 landscape (e.g. 2560x1600 or 2048x1280 @ 340dpi -> ~963x602 dp)
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(963, 602))
    }

    @Test
    fun y700_portrait_fallsBackToCompact() {
        // Lenovo Y700 portrait (~602x963 dp) -> height > width
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(602, 963))
    }

    @Test
    fun fyt_twoThirds_240dpi_isEligible() {
        // FYT 7870 2/3 pane at 240 dpi -> 853x800 dp
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(853, 800))
    }

    @Test
    fun fyt_twoThirds_320dpi_isEligible() {
        // FYT 7870 2/3 pane at 320 dpi -> 640x600 dp
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(640, 600))
    }

    @Test
    fun fyt_oneHalf_320dpi_fallsBackToCompact() {
        // FYT 7870 1/2 pane at 320 dpi -> 600x750 dp (portrait-ish aspect: height > width)
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(600, 750))
    }

    @Test
    fun fyt_oneThird_320dpi_fallsBackToCompact() {
        // FYT 7870 1/3 pane at 320 dpi -> 400x750 dp (narrow pane, width < 580)
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(400, 750))
    }

    @Test
    fun sony_landscape_isEligible() {
        // Sony Xperia native landscape @ 420 dpi -> 960x411 dp
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(960, 411))
    }

    @Test
    fun sony_portrait_fallsBackToCompact() {
        // Sony Xperia native portrait @ 420 dpi -> 411x960 dp
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(411, 960))
    }

    @Test
    fun short_landscape_window_isEligible() {
        // Short landscape window e.g. 800x360 dp
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(800, 360))
    }

    @Test
    fun boundaryConditions() {
        // Exact width threshold 580
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(580, 400))
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(579, 400))

        // Exact height threshold 320
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(600, 320))
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(600, 319))

        // Square aspect ratio is eligible if >= 580 and >= 320
        assertTrue(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(600, 600))

        // Inverted (height > width) is not eligible
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(580, 581))

        // Non-positive dimensions
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(0, 0))
        assertFalse(CockpitLayoutPolicy.isEligibleForCockpitTwoPane(-100, 500))
    }

    // ── Pane Width Bounds ────────────────────────────────────────────────────

    @Test
    fun paneWidthGuaranteesMinimumsAtThreshold() {
        val totalWidth = CockpitLayoutPolicy.MIN_SPLIT_TOTAL_WIDTH_DP // 580
        val leftWidth = (totalWidth * CockpitLayoutPolicy.LEFT_PANE_WEIGHT).toInt()
        val rightWidth = (totalWidth * CockpitLayoutPolicy.RIGHT_PANE_WEIGHT).toInt()

        assertTrue(
            "Left pane width ($leftWidth) must be >= ${CockpitLayoutPolicy.MIN_LEFT_PANE_WIDTH_DP}",
            leftWidth >= CockpitLayoutPolicy.MIN_LEFT_PANE_WIDTH_DP
        )
        assertTrue(
            "Right pane width ($rightWidth) must be >= ${CockpitLayoutPolicy.MIN_RIGHT_PANE_WIDTH_DP}",
            rightWidth >= CockpitLayoutPolicy.MIN_RIGHT_PANE_WIDTH_DP
        )
    }

    // ── Artwork Sizing ───────────────────────────────────────────────────────

    @Test
    fun cockpitCoverSizeDp_boundedProperly() {
        // Spacious Y700 landscape
        val y700Size = CockpitLayoutPolicy.cockpitCoverSizeDp(963, 602)
        assertTrue(y700Size in 96..360)

        // Short landscape
        val shortSize = CockpitLayoutPolicy.cockpitCoverSizeDp(800, 360)
        assertTrue(shortSize in 96..200)

        // Zero dimensions safe fallback
        assertEquals(160, CockpitLayoutPolicy.cockpitCoverSizeDp(0, 0))
    }

    // ── Title Display Cleaning ───────────────────────────────────────────────

    @Test
    fun cleanTrackTitle_stripsAudioExtensionsCaseInsensitively() {
        assertEquals("Song Title", CockpitLayoutPolicy.cleanTrackTitle("Song Title.mp3"))
        assertEquals("Song Title", CockpitLayoutPolicy.cleanTrackTitle("Song Title.MP3"))
        assertEquals("Lossless Track", CockpitLayoutPolicy.cleanTrackTitle("Lossless Track.flac"))
        assertEquals("Lossless Track", CockpitLayoutPolicy.cleanTrackTitle("Lossless Track.FLAC"))
        assertEquals("Audio File", CockpitLayoutPolicy.cleanTrackTitle("Audio File.m4a"))
        assertEquals("Audio File", CockpitLayoutPolicy.cleanTrackTitle("Audio File.wav"))
        assertEquals("Audio File", CockpitLayoutPolicy.cleanTrackTitle("Audio File.aac"))
        assertEquals("Audio File", CockpitLayoutPolicy.cleanTrackTitle("Audio File.ogg"))
        assertEquals("Audio File", CockpitLayoutPolicy.cleanTrackTitle("Audio File.opus"))
    }

    @Test
    fun cleanTrackTitle_replacesUnderscoresWithSpaces() {
        assertEquals("Artist Track Name", CockpitLayoutPolicy.cleanTrackTitle("Artist_Track_Name.mp3"))
        assertEquals("Artist Track Name", CockpitLayoutPolicy.cleanTrackTitle("Artist___Track__Name.mp3"))
    }

    @Test
    fun cleanTrackTitle_preservesInternalDotsAndUnicode() {
        assertEquals("Mr. Bean Theme", CockpitLayoutPolicy.cleanTrackTitle("Mr._Bean_Theme.mp3"))
        assertEquals("Bài Hát Mùa Thu 2026", CockpitLayoutPolicy.cleanTrackTitle("Bài_Hát_Mùa_Thu_2026.flac"))
        assertEquals("夜に駆ける", CockpitLayoutPolicy.cleanTrackTitle("夜に駆ける.mp3"))
    }

    @Test
    fun cleanTrackTitle_handlesEdgeCases() {
        assertEquals("", CockpitLayoutPolicy.cleanTrackTitle(""))
        assertEquals("", CockpitLayoutPolicy.cleanTrackTitle("   "))
        assertEquals("NoExtensionTrack", CockpitLayoutPolicy.cleanTrackTitle("NoExtensionTrack"))
    }

    // ── Duration Formatting ──────────────────────────────────────────────────

    @Test
    fun formatDuration_underOneHour() {
        assertEquals("05:12", CockpitLayoutPolicy.formatDuration(312L))
        assertEquals("00:45", CockpitLayoutPolicy.formatDuration(45L))
        assertEquals("59:59", CockpitLayoutPolicy.formatDuration(3599L))
    }

    @Test
    fun formatDuration_oneHourOrMore() {
        assertEquals("1:03:58", CockpitLayoutPolicy.formatDuration(3838L))
        assertEquals("2:00:00", CockpitLayoutPolicy.formatDuration(7200L))
        assertEquals("10:15:30", CockpitLayoutPolicy.formatDuration(36930L))
    }

    @Test
    fun formatDuration_nonPositiveReturnsNull() {
        assertNull(CockpitLayoutPolicy.formatDuration(0L))
        assertNull(CockpitLayoutPolicy.formatDuration(-10L))
    }

    @Test
    fun formatDurationMs_formatsCorrectly() {
        assertEquals("05:12", CockpitLayoutPolicy.formatDurationMs(312_000L))
        assertEquals("1:03:58", CockpitLayoutPolicy.formatDurationMs(3838_000L))
        assertEquals("00:00", CockpitLayoutPolicy.formatDurationMs(0L))
        assertEquals("00:00", CockpitLayoutPolicy.formatDurationMs(-1000L))
    }

    // ── Audio Format Badge ───────────────────────────────────────────────────

    @Test
    fun formatAudioBadge_fullMetadata() {
        val badge = CockpitLayoutPolicy.formatAudioBadge(
            sampleMimeType = "audio/mpeg",
            sampleRate = 44100,
            channelCount = 2,
            bitrate = 320000
        )
        assertEquals("MP3 · 44.1 kHz · Stereo · 320 kbps", badge)
    }

    @Test
    fun formatAudioBadge_flacMultichannel() {
        val badge = CockpitLayoutPolicy.formatAudioBadge(
            sampleMimeType = "audio/flac",
            sampleRate = 96000,
            channelCount = 6,
            bitrate = 0
        )
        assertEquals("FLAC · 96.0 kHz · 6 kênh", badge)
    }

    @Test
    fun formatAudioBadge_omitsMissingFieldsWithoutFabrication() {
        val badge = CockpitLayoutPolicy.formatAudioBadge(
            sampleMimeType = "audio/mp4a-latm",
            sampleRate = 0,
            channelCount = 0,
            bitrate = 256000
        )
        assertEquals("AAC · 256 kbps", badge)

        val emptyBadge = CockpitLayoutPolicy.formatAudioBadge(
            sampleMimeType = null,
            sampleRate = 0,
            channelCount = 0,
            bitrate = 0
        )
        assertNull(emptyBadge)
    }

    // ── Song Row Fidelity and Touch Target Constraints ───────────────────────

    @Test
    fun touchTargetAndRowHeightConstants_satisfyAccessibilityGuidelines() {
        assertTrue("Min touch target must be at least 48dp", CockpitLayoutPolicy.MIN_TOUCH_TARGET_DP >= 48)
        assertTrue("Min song row height must be at least 64dp", CockpitLayoutPolicy.MIN_SONG_ROW_HEIGHT_DP >= 64)
    }

    @Test
    fun resolveSongStatus_returnsCorrectStatus() {
        assertEquals(CockpitLayoutPolicy.SongPlaybackStatus.PLAYING, CockpitLayoutPolicy.resolveSongStatus(isCurrent = true, isDownloaded = true))
        assertEquals(CockpitLayoutPolicy.SongPlaybackStatus.PLAYING, CockpitLayoutPolicy.resolveSongStatus(isCurrent = true, isDownloaded = false))
        assertEquals(CockpitLayoutPolicy.SongPlaybackStatus.DOWNLOADED, CockpitLayoutPolicy.resolveSongStatus(isCurrent = false, isDownloaded = true))
        assertEquals(CockpitLayoutPolicy.SongPlaybackStatus.CLOUD, CockpitLayoutPolicy.resolveSongStatus(isCurrent = false, isDownloaded = false))
    }

    @Test
    fun statusGlyph_returnsExpectedSymbols() {
        assertEquals("▶", CockpitLayoutPolicy.statusGlyph(CockpitLayoutPolicy.SongPlaybackStatus.PLAYING))
        assertEquals("✓", CockpitLayoutPolicy.statusGlyph(CockpitLayoutPolicy.SongPlaybackStatus.DOWNLOADED))
        assertEquals("☁", CockpitLayoutPolicy.statusGlyph(CockpitLayoutPolicy.SongPlaybackStatus.CLOUD))
    }
}
