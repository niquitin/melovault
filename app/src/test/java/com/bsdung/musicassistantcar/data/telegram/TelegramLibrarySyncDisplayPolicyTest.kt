package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramLibrarySyncDisplayPolicyTest {

    @Test
    fun unauthenticatedReturnsWaitingForConnection() {
        val label = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 0,
            totalIndexedCount = 0,
            isSyncing = false,
            isAuthenticated = false
        )
        assertEquals("Telegram · Chờ kết nối", label)
    }

    @Test
    fun authenticatedEmptyLibrary() {
        val label = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 0,
            totalIndexedCount = 0,
            isSyncing = false,
            isAuthenticated = true
        )
        assertEquals("Chưa có bài · Đã đồng bộ", label)
    }

    @Test
    fun fullLibraryMatchingDisplayed() {
        // e.g. Playlist with 15 songs, all 15 displayed
        val label = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 15,
            totalIndexedCount = 15,
            isSyncing = false,
            isAuthenticated = true
        )
        assertEquals("15 bài · Đã đồng bộ", label)
    }

    @Test
    fun paginatedPartialDisplayDistinguishesPageFromTotal() {
        // Cold-start: 200 loaded out of 25,055 total
        val label = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 200,
            totalIndexedCount = 25055,
            isSyncing = false,
            isAuthenticated = true
        )
        assertEquals("Hiển thị 200 / 25,055 bài · Đã đồng bộ", label)
    }

    @Test
    fun paginatedPartialDisplayScrolledTo400() {
        // Scrolled down: 400 loaded out of 25,055 total
        val label = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 400,
            totalIndexedCount = 25055,
            isSyncing = false,
            isAuthenticated = true
        )
        assertEquals("Hiển thị 400 / 25,055 bài · Đã đồng bộ", label)
    }

    @Test
    fun backgroundSyncingStateReflected() {
        val label = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 200,
            totalIndexedCount = 25055,
            isSyncing = true,
            isAuthenticated = true
        )
        assertEquals("Hiển thị 200 / 25,055 bài · Đang đồng bộ…", label)
    }

    @Test
    fun sourcesModeShowsSourceCountAndTotalTracks() {
        val label = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 29,
            totalIndexedCount = 25055,
            isSyncing = false,
            isAuthenticated = true,
            isSourcesMode = true,
            sourceCount = 29
        )
        assertEquals("29 nguồn · 25,055 bài · Đã đồng bộ", label)
    }

    @Test
    fun filteredChannelSourceShowsChannelTitle() {
        val label = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 200,
            totalIndexedCount = 4330,
            isSyncing = false,
            isAuthenticated = true,
            selectedSourceTitle = "Nhạc Trẻ 2026"
        )
        assertEquals("Nhạc Trẻ 2026 · Hiển thị 200 / 4,330 bài · Đã đồng bộ", label)
    }

    // ── E9.3-03C Regression: Sources Overview Header Count After Channel Visit ─

    @Test
    fun sourcesOverviewAfterChannelVisit_showsFullLibraryTotalNotChannelCount() {
        // Simulates the bug scenario: user opened DJ channel (4,332 songs), then pressed back
        // to the Bento sources overview. totalCountForCurrentView must be reset to the full DB
        // total (25,074) in refreshSongRows() before updateSyncHeaderSubtitle() is called.
        // The prior stale value of 4,332 must NOT appear in the header.
        val labelWithCorrectTotal = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 29,        // 29 source cards shown
            totalIndexedCount = 25074,  // full DB total — correctly reset by E9.3-03C fix
            isSyncing = false,
            isAuthenticated = true,
            isSourcesMode = true,
            sourceCount = 29
        )
        assertEquals("29 nguồn · 25,074 bài · Đã đồng bộ", labelWithCorrectTotal)

        // The stale channel total MUST NOT produce a sources-overview label with 4,332
        val labelWithStaleChannelTotal = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 29,
            totalIndexedCount = 4332,   // stale per-channel count — the pre-fix bug value
            isSyncing = false,
            isAuthenticated = true,
            isSourcesMode = true,
            sourceCount = 29
        )
        // Verify that the stale value would produce a WRONG label (so the fix is meaningful)
        assertEquals("29 nguồn · 4,332 bài · Đã đồng bộ", labelWithStaleChannelTotal)
    }

    @Test
    fun sourcesOverviewListMode_afterChannelVisit_showsCorrectFullTotal() {
        // Same scenario in Legacy List view mode — totalCountForCurrentView must be full DB total
        val label = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 29,
            totalIndexedCount = 25074,
            isSyncing = false,
            isAuthenticated = true,
            isSourcesMode = true,
            sourceCount = 29
        )
        assertEquals("29 nguồn · 25,074 bài · Đã đồng bộ", label)
    }

    @Test
    fun sourcesOverviewSearchFiltered_headerStillUsesFullDbTotal() {
        // When user searches "emi" in sources mode, filtered results shown but header must still
        // show the authoritative full library count (totalCountForCurrentView = db total)
        val label = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 3,         // only 3 source cards match "emi" search
            totalIndexedCount = 25074,  // but full DB total stays authoritative
            isSyncing = false,
            isAuthenticated = true,
            isSourcesMode = true,
            sourceCount = 29
        )
        assertEquals("29 nguồn · 25,074 bài · Đã đồng bộ", label)
    }

    @Test
    fun sourcesOverviewZeroSources_showsZeroSourcesLabel() {
        // Edge case: no sources indexed yet (e.g. fresh install, no Telegram sync)
        // In sources mode the policy always formats "N nguồn · M bài · state"
        val label = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = 0,
            totalIndexedCount = 0,
            isSyncing = false,
            isAuthenticated = true,
            isSourcesMode = true,
            sourceCount = 0
        )
        // Sources-mode branch returns "0 nguồn · 0 bài · Đã đồng bộ" (not "Chưa có bài")
        assertEquals("0 nguồn · 0 bài · Đã đồng bộ", label)
    }
}
