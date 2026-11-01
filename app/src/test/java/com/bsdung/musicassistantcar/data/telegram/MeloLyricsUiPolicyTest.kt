package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Candidate
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Meta
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsUiPolicy.Action
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsUiPolicy.Tab
import com.bsdung.musicassistantcar.lyrics.LyricsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloLyricsUiPolicyTest {

    private fun candidate(duration: Double, synced: String? = null, plain: String? = null, instrumental: Boolean = false, name: String = "Lạc Trôi", artist: String = "Sơn Tùng") =
        Candidate(1, name, artist, null, duration, instrumental, plain, synced)

    // --- consent box (LD2) ------------------------------------------------------------------

    @Test
    fun consentBoxSaysWhatIsSentAndOffersYesAndNo() {
        val p = MeloLyricsUiPolicy.presentation(LyricsState.NeedConsent)
        assertEquals(MeloLyricsUiPolicy.CONSENT_TEXT, p.text)
        assertEquals(Action.ENABLE, p.primary)
        assertEquals(Action.DECLINE, p.secondary)
        assertTrue(p.text!!.contains("LRCLIB"))
        assertTrue(p.text!!.contains("tên bài, nghệ sĩ, album và thời lượng"))
    }

    @Test
    fun offStateCanBeSwitchedOnAgain() {
        val p = MeloLyricsUiPolicy.presentation(LyricsState.Off)
        assertEquals("Lời nhạc đang tắt.", p.text)
        assertEquals(Action.ENABLE_AGAIN, p.primary)
    }

    // --- states -----------------------------------------------------------------------------

    @Test
    fun lyricsStatesCarryNoMessageAndOnlyManualOnesOfferToClear() {
        val lines = listOf(LyricsLine(0, "a"))
        assertNull(MeloLyricsUiPolicy.presentation(LyricsState.Synced(lines, fromCache = false, manual = false)).text)
        assertNull(MeloLyricsUiPolicy.presentation(LyricsState.Synced(lines, false, false)).primary)
        assertEquals(Action.CLEAR_CHOICE, MeloLyricsUiPolicy.presentation(LyricsState.Synced(lines, true, true)).primary)
        assertEquals(Action.CLEAR_CHOICE, MeloLyricsUiPolicy.presentation(LyricsState.Plain("x", true, true)).primary)
        assertNull(MeloLyricsUiPolicy.presentation(LyricsState.Plain("x", false, false)).primary)
    }

    @Test
    fun missingMetadataAndNoMatchOfferManualSearch() {
        assertEquals(Action.SEARCH, MeloLyricsUiPolicy.presentation(LyricsState.MissingMetadata).primary)
        assertEquals("Thiếu tên bài hoặc nghệ sĩ để tìm lời.", MeloLyricsUiPolicy.presentation(LyricsState.MissingMetadata).text)
        assertEquals(Action.SEARCH, MeloLyricsUiPolicy.presentation(LyricsState.NoMatch(canSearch = true)).primary)
        assertNull(MeloLyricsUiPolicy.presentation(LyricsState.NoMatch(canSearch = false)).primary)
    }

    @Test
    fun instrumentalAndLoadingHaveNoButtons() {
        assertNull(MeloLyricsUiPolicy.presentation(LyricsState.Instrumental).primary)
        assertNull(MeloLyricsUiPolicy.presentation(LyricsState.Loading).primary)
        assertEquals("Đang tìm lời…", MeloLyricsUiPolicy.presentation(LyricsState.Loading).text)
    }

    @Test
    fun noSongPlayingMeansNoButtons() {
        val p = MeloLyricsUiPolicy.presentation(null)
        assertEquals("Chưa có bài đang phát.", p.text)
        assertNull(p.primary)
    }

    @Test
    fun ambiguousAsksWhichSongAndStillAllowsSearch() {
        val p = MeloLyricsUiPolicy.presentation(LyricsState.Ambiguous(listOf(candidate(200.0))))
        assertEquals("Chưa chắc bài nào. Chọn đúng bài:", p.text)
        assertEquals(Action.SEARCH, p.primary)
    }

    @Test
    fun temporaryErrorOffersRetryAndRoundsTheWaitUp() {
        assertEquals("Chưa tải được lời. Thử lại sau.", MeloLyricsUiPolicy.presentation(LyricsState.TemporaryError(null)).text)
        assertEquals("Chưa tải được lời. Thử lại sau khoảng 8 giây.", MeloLyricsUiPolicy.presentation(LyricsState.TemporaryError(7_400)).text)
        assertEquals(Action.RETRY, MeloLyricsUiPolicy.presentation(LyricsState.TemporaryError(null)).primary)
    }

    // --- picker rows ------------------------------------------------------------------------

    @Test
    fun candidateLabelShowsNameArtistLengthAndKind() {
        assertEquals("Lạc Trôi — Sơn Tùng · 3:53 · đồng bộ", MeloLyricsUiPolicy.candidateLabel(candidate(233.2, synced = "[00:01.00]a")))
        assertEquals("Lạc Trôi — Sơn Tùng · 4:05 · thường", MeloLyricsUiPolicy.candidateLabel(candidate(245.0, plain = "a")))
        assertEquals("Lạc Trôi — Sơn Tùng · 0:09 · nhạc cụ", MeloLyricsUiPolicy.candidateLabel(candidate(9.0, instrumental = true)))
        assertEquals("Lạc Trôi — Sơn Tùng · không có lời", MeloLyricsUiPolicy.candidateLabel(candidate(0.0)))
    }

    @Test
    fun candidateLabelSurvivesBlankNames() {
        assertEquals("(không tên) · thường", MeloLyricsUiPolicy.candidateLabel(candidate(0.0, plain = "a", name = " ", artist = "")))
    }

    // --- search box prefill (LD10) ----------------------------------------------------------

    @Test
    fun prefillIsTitleAndArtistWhenBothKnown() {
        assertEquals("Lạc Trôi Sơn Tùng", MeloLyricsUiPolicy.searchPrefill(Meta("Lạc Trôi", "Sơn Tùng", null, 233), "x.mp3"))
    }

    @Test
    fun prefillFallsBackToFileNameWithoutExtension() {
        assertEquals("Bài lạ 01", MeloLyricsUiPolicy.searchPrefill(Meta("Bài lạ", null, null, 100), "Bài_lạ__01.flac"))
        assertEquals("Bài lạ 01", MeloLyricsUiPolicy.searchPrefill(null, "Bài_lạ__01.flac"))
    }

    @Test
    fun prefillFallsBackToWhateverIsKnown() {
        assertEquals("Chỉ có tên", MeloLyricsUiPolicy.searchPrefill(Meta("Chỉ có tên", null, null, null), null))
        assertEquals("Chỉ nghệ sĩ", MeloLyricsUiPolicy.searchPrefill(Meta(null, "Chỉ nghệ sĩ", null, null), ""))
        assertEquals("", MeloLyricsUiPolicy.searchPrefill(null, null))
    }

    // --- scrolling --------------------------------------------------------------------------

    @Test
    fun jumpButtonOnlyWhenScrolledAwayWhileALineIsActive() {
        assertTrue(MeloLyricsUiPolicy.showJumpButton(autoFollow = false, activeIndex = 3))
        assertFalse(MeloLyricsUiPolicy.showJumpButton(autoFollow = true, activeIndex = 3))
        assertFalse(MeloLyricsUiPolicy.showJumpButton(autoFollow = false, activeIndex = -1))
    }

    @Test
    fun activeLineIsCentredButNeverAboveTheTop() {
        assertEquals(350, MeloLyricsUiPolicy.scrollTarget(lineTop = 800, lineHeight = 100, paneHeight = 1000))
        assertEquals(0, MeloLyricsUiPolicy.scrollTarget(lineTop = 100, lineHeight = 100, paneHeight = 1000))
    }

    // --- sizes (large text, S6-style check) ------------------------------------------------

    @Test
    fun portraitPaneNeverShrinksBelowSixTouchTargets() {
        assertEquals(1200, MeloLyricsUiPolicy.portraitPaneHeight(windowHeightPx = 2400, minTouchPx = 120))
        assertEquals(720, MeloLyricsUiPolicy.portraitPaneHeight(windowHeightPx = 900, minTouchPx = 120))
    }

    @Test
    fun controlsKeepTheTouchMinimumAndGrowWithBigText() {
        assertEquals(144, MeloLyricsUiPolicy.controlHeightPx(minTouchPx = 144, textPx = 40f, fontScale = 1.0f))
        assertTrue(MeloLyricsUiPolicy.controlHeightPx(144, 40f, 1.3f) >= 144)
        assertEquals(73, MeloLyricsUiPolicy.controlHeightPx(minTouchPx = 48, textPx = 40f, fontScale = 1.3f))
        assertTrue(MeloLyricsUiPolicy.controlHeightPx(48, 60f, 2.0f) > 48)
    }

    // --- stuck "Đang tìm lời…" (B1) ---------------------------------------------------------

    @Test
    fun loadingIsOnlyStuckAfterFortySeconds() {
        assertFalse(MeloLyricsUiPolicy.loadingStuck(sinceMs = 1_000, nowMs = 1_000))
        assertFalse(MeloLyricsUiPolicy.loadingStuck(sinceMs = 1_000, nowMs = 40_999))
        assertTrue(MeloLyricsUiPolicy.loadingStuck(sinceMs = 1_000, nowMs = 41_000))
    }

    @Test
    fun stuckLimitCoversAGetPlusASearchAtTheirTimeouts() {
        // connect 5 s + read 8 s for each of the two requests
        assertTrue(MeloLyricsUiPolicy.LOADING_STUCK_MS > 2 * (5_000 + 8_000))
    }

    // --- stored choices ---------------------------------------------------------------------

    @Test
    fun storedConsentMapsAndUnknownMeansUndecided() {
        assertEquals(LyricsConsent.GRANTED, MeloLyricsUiPolicy.consentOf("granted"))
        assertEquals(LyricsConsent.DENIED, MeloLyricsUiPolicy.consentOf("denied"))
        assertEquals(LyricsConsent.UNDECIDED, MeloLyricsUiPolicy.consentOf(null))
        assertEquals(LyricsConsent.UNDECIDED, MeloLyricsUiPolicy.consentOf("yes"))
    }

    @Test
    fun storedTabDefaultsToQueue() {
        assertEquals(Tab.QUEUE, MeloLyricsUiPolicy.tabOf(null))
        assertEquals(Tab.QUEUE, MeloLyricsUiPolicy.tabOf("garbage"))
        assertEquals(Tab.LYRICS, MeloLyricsUiPolicy.tabOf("LYRICS"))
        assertEquals("Tiếp theo", Tab.QUEUE.label)
    }
}
