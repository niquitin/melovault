package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramVaultBentoPolicyTest {

    // ── Grid Column Calculation Tests ────────────────────────────────────────

    @Test
    fun calculateGridColumns_narrowPhonePortrait_returnsOneColumn() {
        // e.g. Sony 1080px / 2.625 density = ~411dp
        val cols = TelegramVaultBentoPolicy.calculateGridColumns(usableWidthDp = 360, isCockpitRightPane = false)
        assertEquals(1, cols)
    }

    @Test
    fun calculateGridColumns_midWidthTabletPortrait_returnsTwoColumns() {
        // e.g. Y700 in portrait: ~600dp usable width
        val cols = TelegramVaultBentoPolicy.calculateGridColumns(usableWidthDp = 600, isCockpitRightPane = false)
        assertEquals(2, cols)
    }

    @Test
    fun calculateGridColumns_wideScreenLandscape_returnsThreeOrFourColumns() {
        // e.g. Wide desktop/tablet > 800dp
        val cols3 = TelegramVaultBentoPolicy.calculateGridColumns(usableWidthDp = 750, isCockpitRightPane = false)
        assertEquals(3, cols3)

        val cols4 = TelegramVaultBentoPolicy.calculateGridColumns(usableWidthDp = 1000, isCockpitRightPane = false)
        assertEquals(4, cols4)
    }

    @Test
    fun calculateGridColumns_cockpitRightPane_constrainedToMaxTwoColumns() {
        // In Theme 02 Cockpit right pane, columns must not exceed 2 to avoid squeezing cards
        val cols = TelegramVaultBentoPolicy.calculateGridColumns(usableWidthDp = 900, isCockpitRightPane = true)
        assertEquals(2, cols)

        val narrowCols = TelegramVaultBentoPolicy.calculateGridColumns(usableWidthDp = 320, isCockpitRightPane = true)
        assertEquals(1, narrowCols)
    }

    @Test
    fun calculateGridColumns_zeroOrNegativeWidth_returnsOneColumnFallback() {
        assertEquals(1, TelegramVaultBentoPolicy.calculateGridColumns(usableWidthDp = 0))
        assertEquals(1, TelegramVaultBentoPolicy.calculateGridColumns(usableWidthDp = -50))
    }

    // ── Source Filtering Tests ───────────────────────────────────────────────

    @Test
    fun filterSources_emptyQuery_returnsAllSources() {
        val sources = listOf(
            IndexedTelegramSource(accountId = 1L, chatId = 101L, title = "Nhạc Trẻ Remix", trackCount = 50),
            IndexedTelegramSource(accountId = 1L, chatId = 102L, title = "Lossless Audiophile", trackCount = 120)
        )
        val filtered = TelegramVaultBentoPolicy.filterSources(sources, "")
        assertEquals(2, filtered.size)

        val filteredSpaces = TelegramVaultBentoPolicy.filterSources(sources, "   ")
        assertEquals(2, filteredSpaces.size)
    }

    @Test
    fun filterSources_matchingQuery_filtersCaseInsensitive() {
        val sources = listOf(
            IndexedTelegramSource(accountId = 1L, chatId = 101L, title = "Nhạc Trẻ Remix", trackCount = 50),
            IndexedTelegramSource(accountId = 1L, chatId = 102L, title = "Lossless Audiophile", trackCount = 120),
            IndexedTelegramSource(accountId = 1L, chatId = 103L, title = "Trance & EDM Mix", trackCount = 30)
        )
        val filtered = TelegramVaultBentoPolicy.filterSources(sources, "lossless")
        assertEquals(1, filtered.size)
        assertEquals(102L, filtered[0].chatId)

        val filteredVietnamese = TelegramVaultBentoPolicy.filterSources(sources, "remix")
        assertEquals(1, filteredVietnamese.size)
        assertEquals(101L, filteredVietnamese[0].chatId)
    }

    @Test
    fun filterSources_nonMatchingQuery_returnsEmptyList() {
        val sources = listOf(
            IndexedTelegramSource(accountId = 1L, chatId = 101L, title = "Nhạc Trẻ Remix", trackCount = 50)
        )
        val filtered = TelegramVaultBentoPolicy.filterSources(sources, "Classical Symphonies")
        assertTrue(filtered.isEmpty())
    }

    // ── Source Partitioning (Active vs. Zero Tracks) ─────────────────────────

    @Test
    fun partitionSources_separatesPositiveAndZeroTrackChannels() {
        val sources = listOf(
            IndexedTelegramSource(accountId = 1L, chatId = 101L, title = "Active Channel A", trackCount = 100),
            IndexedTelegramSource(accountId = 1L, chatId = 102L, title = "Empty Channel B", trackCount = 0),
            IndexedTelegramSource(accountId = 1L, chatId = 103L, title = "Active Channel C", trackCount = 25),
            IndexedTelegramSource(accountId = 1L, chatId = 104L, title = "Unscanned Channel D", trackCount = -1)
        )
        val (active, empty) = TelegramVaultBentoPolicy.partitionSources(sources)

        assertEquals(2, active.size)
        assertEquals(listOf(101L, 103L), active.map { it.chatId })

        assertEquals(2, empty.size)
        assertEquals(listOf(102L, 104L), empty.map { it.chatId })
    }

    // ── Deduplication by Stable Chat ID ──────────────────────────────────────

    @Test
    fun distinctSourcesByChatId_resolvesDuplicatesByChatIdKeepingHighestCount() {
        val sources = listOf(
            IndexedTelegramSource(accountId = 1L, chatId = 200L, title = "EDM Club (Old)", trackCount = 10),
            IndexedTelegramSource(accountId = 1L, chatId = 200L, title = "EDM Club (New)", trackCount = 85),
            IndexedTelegramSource(accountId = 1L, chatId = 300L, title = "Chillout Lounge", trackCount = 42)
        )
        val distinct = TelegramVaultBentoPolicy.distinctSourcesByChatId(sources)

        assertEquals(2, distinct.size)
        val chat200 = distinct.first { it.chatId == 200L }
        assertEquals(85, chat200.trackCount)
        assertEquals("EDM Club (New)", chat200.title)
    }

    @Test
    fun distinctSourcesByChatId_allowsIdenticalTitlesWithDifferentChatIds() {
        val sources = listOf(
            IndexedTelegramSource(accountId = 1L, chatId = 401L, title = "Music Assistant", trackCount = 30),
            IndexedTelegramSource(accountId = 1L, chatId = 402L, title = "Music Assistant", trackCount = 60)
        )
        val distinct = TelegramVaultBentoPolicy.distinctSourcesByChatId(sources)

        assertEquals(2, distinct.size)
        assertEquals(setOf(401L, 402L), distinct.map { it.chatId }.toSet())
    }

    // ── Formatting and Truthful Labels ───────────────────────────────────────

    @Test
    fun formatTrackCount_formatsCorrectlyWithThousandsSeparator() {
        assertEquals("0 bài", TelegramVaultBentoPolicy.formatTrackCount(0))
        assertEquals("0 bài", TelegramVaultBentoPolicy.formatTrackCount(-5))
        assertEquals("1 bài", TelegramVaultBentoPolicy.formatTrackCount(1))
        assertEquals("200 bài", TelegramVaultBentoPolicy.formatTrackCount(200))
        assertEquals("1,245 bài", TelegramVaultBentoPolicy.formatTrackCount(1245))
        assertEquals("25,067 bài", TelegramVaultBentoPolicy.formatTrackCount(25067))
    }

    @Test
    fun formatHeaderTitle_excludesAggregateAndReportsTrueSourceCount() {
        assertEquals("NGUỒN NHẠC TELEGRAM  ·  29 nhóm/kênh/chat", TelegramVaultBentoPolicy.formatHeaderTitle(29))
        assertEquals("NGUỒN NHẠC TELEGRAM  ·  0 nhóm/kênh/chat", TelegramVaultBentoPolicy.formatHeaderTitle(0))
    }

    @Test
    fun formatHeaderSubtitle_combinesSourceCountAndTotalTracks() {
        val subtitle = TelegramVaultBentoPolicy.formatHeaderSubtitle(sourceCount = 29, totalIndexedTracks = 25067)
        assertEquals("29 nguồn  ·  25,067 bài  ·  Đã đồng bộ", subtitle)
    }

    @Test
    fun shouldShowHeroCard_onlyWhenSearchQueryIsBlank() {
        assertTrue(TelegramVaultBentoPolicy.shouldShowHeroCard(""))
        assertTrue(TelegramVaultBentoPolicy.shouldShowHeroCard("   "))
        assertFalse(TelegramVaultBentoPolicy.shouldShowHeroCard("chill"))
        assertFalse(TelegramVaultBentoPolicy.shouldShowHeroCard("EDM"))
    }

    // ── Source Sorting Tests ─────────────────────────────────────────────────

    @Test
    fun sortSources_ordersByTrackCountDescendingWithStableTieBreak() {
        val sources = listOf(
            IndexedTelegramSource(accountId = 1L, chatId = 101L, title = "Beta Songs", trackCount = 50),
            IndexedTelegramSource(accountId = 1L, chatId = 102L, title = "Alpha Tracks", trackCount = 100),
            IndexedTelegramSource(accountId = 1L, chatId = 103L, title = "Gamma Beats", trackCount = 50),
            IndexedTelegramSource(accountId = 1L, chatId = 104L, title = "Beta Remix", trackCount = 50)
        )
        val sorted = TelegramVaultBentoPolicy.sortSources(sources)

        assertEquals(102L, sorted[0].chatId) // 100 tracks
        assertEquals(104L, sorted[1].chatId) // 50 tracks, "beta remix" < "beta songs"
        assertEquals(101L, sorted[2].chatId) // 50 tracks, "beta songs"
        assertEquals(103L, sorted[3].chatId) // 50 tracks, "gamma beats"
    }

    // ── Initial Grapheme Extraction Tests ────────────────────────────────────

    @Test
    fun extractInitialGrapheme_extractsFirstDisplayableCharacterCorrectly() {
        assertEquals("N", TelegramVaultBentoPolicy.extractInitialGrapheme("Nhạc Trẻ"))
        assertEquals("A", TelegramVaultBentoPolicy.extractInitialGrapheme("audiophile"))
        assertEquals("Đ", TelegramVaultBentoPolicy.extractInitialGrapheme("Điện Tử Remix"))
        assertEquals("♫", TelegramVaultBentoPolicy.extractInitialGrapheme(""))
        assertEquals("♫", TelegramVaultBentoPolicy.extractInitialGrapheme("   "))
        assertEquals("🔥", TelegramVaultBentoPolicy.extractInitialGrapheme("🔥 Hot Hits"))
    }

    // ── Display Mode Key and Default ─────────────────────────────────────────

    @Test
    fun sourceDisplayMode_defaultsToLegacyList() {
        assertEquals(TelegramVaultBentoPolicy.SourceDisplayMode.LIST, TelegramVaultBentoPolicy.SourceDisplayMode.DEFAULT)
        assertEquals(TelegramVaultBentoPolicy.SourceDisplayMode.LIST, TelegramVaultBentoPolicy.SourceDisplayMode.fromKey(null))
        assertEquals(TelegramVaultBentoPolicy.SourceDisplayMode.LIST, TelegramVaultBentoPolicy.SourceDisplayMode.fromKey("unknown"))
        assertEquals(TelegramVaultBentoPolicy.SourceDisplayMode.BENTO, TelegramVaultBentoPolicy.SourceDisplayMode.fromKey("bento"))
    }
}

