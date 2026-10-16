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
}
