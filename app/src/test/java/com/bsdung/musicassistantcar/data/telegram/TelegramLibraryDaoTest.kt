package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TelegramLibraryDaoTest {

    private lateinit var dao: TelegramLibraryDao

    @Before
    fun setUp() {
        dao = InMemoryTelegramLibraryDao()
    }

    private fun sampleSong(
        accountId: Long = 100L,
        chatId: Long = 200L,
        messageId: Long = 1000L,
        songKey: Long = 1000L,
        title: String = "Test Song",
        date: Long = 1700000000L,
        mediaType: String = "audio",
        sourceTitle: String? = "Test Channel"
    ): IndexedTelegramSong {
        return IndexedTelegramSong(
            accountId = accountId,
            chatId = chatId,
            messageId = messageId,
            songKey = songKey,
            fileId = 42,
            title = title,
            filename = "$title.mp3",
            performer = "Artist",
            durationSeconds = 240,
            fileSize = 10_000_000L,
            mimeType = "audio/mpeg",
            mediaType = mediaType,
            date = date,
            lastSeenAtMs = System.currentTimeMillis(),
            sourceTitle = sourceTitle,
            isDeleted = false
        )
    }

    @Test
    fun testUpsertAndRetrieveSongs() {
        val s1 = sampleSong(messageId = 1L, title = "Song A")
        val s2 = sampleSong(messageId = 2L, title = "Song B")
        assertEquals(2, dao.upsertSongs(listOf(s1, s2)))

        val count = dao.getSongCount(accountId = 100L)
        assertEquals(2, count)

        val retrieved = dao.getSongs(accountId = 100L)
        assertEquals(2, retrieved.size)
    }

    @Test
    fun testCompositeKeyPreventsCrossChatCollision() {
        // Two distinct chats have the same raw message ID (e.g. 555L)
        val s1 = sampleSong(chatId = 201L, messageId = 555L, songKey = 10555L, title = "Chat 201 Track")
        val s2 = sampleSong(chatId = 202L, messageId = 555L, songKey = 20555L, title = "Chat 202 Track")
        dao.upsertSongs(listOf(s1, s2))

        assertEquals(2, dao.getSongCount(accountId = 100L))
        val chat201Songs = dao.getSongs(accountId = 100L, chatId = 201L)
        assertEquals(1, chat201Songs.size)
        assertEquals("Chat 201 Track", chat201Songs[0].title)

        val chat202Songs = dao.getSongs(accountId = 100L, chatId = 202L)
        assertEquals(1, chat202Songs.size)
        assertEquals("Chat 202 Track", chat202Songs[0].title)
    }

    @Test
    fun testAccountIsolation() {
        val acc1Song = sampleSong(accountId = 100L, messageId = 1L, title = "Account 100 Private Song")
        val acc2Song = sampleSong(accountId = 200L, messageId = 1L, title = "Account 200 Private Song")
        dao.upsertSongs(listOf(acc1Song, acc2Song))

        assertEquals(1, dao.getSongCount(accountId = 100L))
        assertEquals(1, dao.getSongCount(accountId = 200L))

        val acc1List = dao.getSongs(accountId = 100L)
        assertEquals(1, acc1List.size)
        assertEquals("Account 100 Private Song", acc1List[0].title)

        val acc2List = dao.getSongs(accountId = 200L)
        assertEquals(1, acc2List.size)
        assertEquals("Account 200 Private Song", acc2List[0].title)
    }

    @Test
    fun testOrderingByDateDescending() {
        val older = sampleSong(messageId = 1L, title = "Older", date = 1000L)
        val newer = sampleSong(messageId = 2L, title = "Newer", date = 2000L)
        val newest = sampleSong(messageId = 3L, title = "Newest", date = 3000L)
        dao.upsertSongs(listOf(older, newest, newer))

        val list = dao.getSongs(accountId = 100L)
        assertEquals("Newest", list[0].title)
        assertEquals("Newer", list[1].title)
        assertEquals("Older", list[2].title)
    }

    @Test
    fun testSearchFilterCaseInsensitive() {
        val s1 = sampleSong(messageId = 1L, title = "Nonstop Van Tuan Mix")
        val s2 = sampleSong(messageId = 2L, title = "Chillout Acoustic")
        val s3 = sampleSong(messageId = 3L, title = "NONSTOP DJ UIBEO")
        dao.upsertSongs(listOf(s1, s2, s3))

        val results = dao.getSongs(accountId = 100L, searchQuery = "nonstop")
        assertEquals(2, results.size)
        assertTrue(results.any { it.title == "Nonstop Van Tuan Mix" })
        assertTrue(results.any { it.title == "NONSTOP DJ UIBEO" })
        assertFalse(results.any { it.title == "Chillout Acoustic" })

        assertEquals(2, dao.getSongCount(accountId = 100L, searchQuery = "nonstop"))
        assertEquals(1, dao.getSongCount(accountId = 100L, searchQuery = "acoustic"))
    }

    @Test
    fun testPagination() {
        val songs = (1..15).map { sampleSong(messageId = it.toLong(), title = "Song $it", date = it.toLong() * 100) }
        dao.upsertSongs(songs)

        val page1 = dao.getSongs(accountId = 100L, offset = 0, limit = 5)
        assertEquals(5, page1.size)
        assertEquals("Song 15", page1[0].title)

        val page2 = dao.getSongs(accountId = 100L, offset = 5, limit = 5)
        assertEquals(5, page2.size)
        assertEquals("Song 10", page2[0].title)

        val page3 = dao.getSongs(accountId = 100L, offset = 10, limit = 10)
        assertEquals(5, page3.size)
        assertEquals("Song 5", page3[0].title)
    }

    @Test
    fun testSourceAggregationAndTrackCounts() {
        val s1 = sampleSong(chatId = 201L, messageId = 1L, sourceTitle = "Track DJ")
        val s2 = sampleSong(chatId = 201L, messageId = 2L, sourceTitle = "Track DJ")
        val s3 = sampleSong(chatId = 202L, messageId = 3L, sourceTitle = "Lao Cai Music")
        dao.upsertSongs(listOf(s1, s2, s3))
        dao.recomputeSourceTrackCounts(accountId = 100L)

        val sources = dao.getSources(accountId = 100L)
        assertEquals(2, sources.size)
        // Ordered by trackCount descending
        assertEquals(201L, sources[0].chatId)
        assertEquals(2, sources[0].trackCount)
        assertEquals(202L, sources[1].chatId)
        assertEquals(1, sources[1].trackCount)

        // Test title update
        dao.updateSourceTitle(accountId = 100L, chatId = 201L, title = "Track DJ Official")
        val updatedSources = dao.getSources(accountId = 100L)
        assertEquals("Track DJ Official", updatedSources.first { it.chatId == 201L }.title)
    }

    @Test
    fun testSyncStateLifecycle() {
        val initial = TelegramSyncState(
            accountId = 100L,
            syncScope = "global",
            phase = "audio",
            nextOffset = "cursor_abc",
            pagesSynced = 5,
            totalItemsSynced = 500,
            status = "syncing",
            highWaterDate = 1710000000L
        )
        dao.updateSyncState(initial)

        val retrieved = dao.getSyncState(accountId = 100L, scope = "global")
        assertNotNull(retrieved)
        assertEquals("audio", retrieved!!.phase)
        assertEquals("cursor_abc", retrieved.nextOffset)
        assertEquals(5, retrieved.pagesSynced)
        assertEquals(500, retrieved.totalItemsSynced)
        assertEquals("syncing", retrieved.status)
        assertEquals(1710000000L, retrieved.highWaterDate)

        // Resumption / phase advancement
        val completed = retrieved.copy(
            phase = "idle",
            nextOffset = "",
            status = "complete",
            lastSyncCompletedAtMs = 1710000500L
        )
        dao.updateSyncState(completed)

        val updated = dao.getSyncState(accountId = 100L, scope = "global")
        assertNotNull(updated)
        assertEquals("idle", updated!!.phase)
        assertEquals("complete", updated.status)
        assertEquals(1710000500L, updated.lastSyncCompletedAtMs)
    }

    @Test
    fun testHighWaterDateCalculation() {
        val s1 = sampleSong(messageId = 1L, date = 1000L, mediaType = "audio")
        val s2 = sampleSong(messageId = 2L, date = 5000L, mediaType = "audio")
        val s3 = sampleSong(messageId = 3L, date = 3000L, mediaType = "document")
        dao.upsertSongs(listOf(s1, s2, s3))

        assertEquals(5000L, dao.getLatestSongDate(accountId = 100L))
        assertEquals(5000L, dao.getLatestSongDate(accountId = 100L, mediaType = "audio"))
        assertEquals(3000L, dao.getLatestSongDate(accountId = 100L, mediaType = "document"))
    }

    @Test
    fun testTombstoneAndAccountPurge() {
        val s1 = sampleSong(chatId = 200L, messageId = 10L, songKey = 10L)
        val s2 = sampleSong(chatId = 200L, messageId = 20L, songKey = 20L)
        dao.upsertSongs(listOf(s1, s2))

        assertEquals(2, dao.getSongCount(accountId = 100L))

        // Mark s1 unavailable (tombstone)
        dao.markSongsUnavailable(accountId = 100L, listOf(Pair(200L, 10L)))
        assertEquals(1, dao.getSongCount(accountId = 100L))
        val active = dao.getSongs(accountId = 100L)
        assertEquals(1, active.size)
        assertEquals(20L, active[0].messageId)

        // Purge account data
        dao.deleteAccountData(accountId = 100L)
        assertEquals(0, dao.getSongCount(accountId = 100L))
        assertNull(dao.getSongByKey(accountId = 100L, songKey = 20L))
    }

    @Test
    fun testCommitPageAndCheckpointAtomic() {
        val checkpoint = TelegramSyncState(
            accountId = 100L,
            syncScope = "global",
            phase = "audio",
            nextOffset = "cur_123",
            pagesSynced = 1,
            totalItemsSynced = 2,
            status = TelegramSyncState.STATUS_SYNCING
        )
        val s1 = sampleSong(accountId = 100L, messageId = 1L, title = "Atomic Song 1")
        val s2 = sampleSong(accountId = 100L, messageId = 2L, title = "Atomic Song 2")

        val committed = dao.commitPageAndCheckpoint(
            accountId = 100L,
            phase = "audio",
            cursor = "cur_123",
            items = listOf(s1, s2),
            checkpoint = checkpoint
        )

        assertEquals(2, committed)
        assertEquals(2, dao.getSongCount(accountId = 100L))
        val state = dao.getSyncState(accountId = 100L, scope = "global")
        assertNotNull(state)
        assertEquals("cur_123", state?.nextOffset)
        assertEquals(TelegramSyncState.STATUS_SYNCING, state?.status)
    }

    @Test
    fun testLargeCatalogBeyond20kItems() {
        val totalSongs = 25_000
        val numSources = 25
        val batchSize = 1000
        val accountId = 100L

        // Generate 25,000 songs across 25 channels
        val songList = ArrayList<IndexedTelegramSong>(batchSize)
        for (i in 1..totalSongs) {
            val chatId = 2000L + (i % numSources)
            val isTarget = (i == 24_500)
            val title = if (isTarget) "Special Rare Hidden Gem In 24500" else "Track Index $i"
            val song = sampleSong(
                accountId = accountId,
                chatId = chatId,
                messageId = i.toLong(),
                songKey = chatId * 1_000_000L + i,
                title = title,
                date = 1_000_000L + i,
                sourceTitle = "Channel $chatId"
            )
            songList.add(song)
            if (songList.size == batchSize) {
                dao.upsertSongs(songList)
                songList.clear()
            }
        }
        if (songList.isNotEmpty()) {
            dao.upsertSongs(songList)
            songList.clear()
        }

        // 1. Verify total count
        assertEquals(totalSongs, dao.getSongCount(accountId = accountId))

        // 2. Verify paging beyond 6,000
        val pageAt6k = dao.getSongs(accountId = accountId, offset = 6000, limit = 200)
        assertEquals(200, pageAt6k.size)

        // 3. Verify paging beyond 20,000
        val pageAt20k = dao.getSongs(accountId = accountId, offset = 20_000, limit = 200)
        assertEquals(200, pageAt20k.size)

        // 4. Verify search finding specific item near end of collection
        val searchResults = dao.getSongs(accountId = accountId, searchQuery = "Rare Hidden Gem")
        assertEquals(1, searchResults.size)
        assertEquals("Special Rare Hidden Gem In 24500", searchResults[0].title)

        // 5. Verify search count
        val searchCount = dao.getSongCount(accountId = accountId, searchQuery = "Rare Hidden Gem")
        assertEquals(1, searchCount)

        // 6. Verify sources recomputation across 25 channels
        dao.recomputeSourceTrackCounts(accountId = accountId)
        val sources = dao.getSources(accountId = accountId)
        assertEquals(numSources, sources.size)
        val totalSourceTracks = sources.sumOf { it.trackCount }
        assertEquals(totalSongs, totalSourceTracks)
    }

    @Test
    fun updateSongFileId_updatesFileIdSuccessfully() {
        val accountId = 100L
        val chatId = 200L
        val messageId = 1000L
        val songKey = 5555L
        val initialSong = sampleSong(
            accountId = accountId,
            chatId = chatId,
            messageId = messageId,
            songKey = songKey
        ).copy(fileId = 1762)
        dao.upsertSongs(listOf(initialSong))

        val stored = dao.getSongByKey(accountId, songKey)
        assertNotNull(stored)
        assertEquals(1762, stored?.fileId)

        // Stale fileId resolution: update to fresh session fileId 9999
        dao.updateSongFileId(accountId, chatId, messageId, 9999)

        val updated = dao.getSongByKey(accountId, songKey)
        assertNotNull(updated)
        assertEquals(9999, updated?.fileId)
    }

    @Test
    fun testAccentInsensitiveSearchAndPagination() {
        val songs = (1..5).map { sampleSong(messageId = it.toLong(), title = "Sử Thanh Hóa $it", date = it.toLong()) } +
            sampleSong(messageId = 9L, title = "Khac")
        dao.upsertSongs(songs)
        assertEquals(5, dao.getSongCount(accountId = 100L, searchQuery = "su thanh hoa"))
        assertEquals(5, dao.getSongCount(accountId = 100L, searchQuery = "HOA SU"))
        val page = dao.getSongs(accountId = 100L, searchQuery = "su thanh hoa", offset = 2, limit = 2)
        assertEquals(listOf("Sử Thanh Hóa 3", "Sử Thanh Hóa 2"), page.map { it.title })
        assertEquals(1, dao.getSongCount(accountId = 100L, searchQuery = "artist khac"))
    }

    // ---- getLiveSourceCounts (E95 F05): read-only truth about songs per chat ------------------

    @Test
    fun liveSourceCountsCountOnlyLiveSongsPerChatAndAccount() {
        dao.upsertSongs(
            listOf(
                sampleSong(chatId = 10L, messageId = 1L),
                sampleSong(chatId = 10L, messageId = 2L),
                sampleSong(chatId = 10L, messageId = 3L).copy(isDeleted = true),
                sampleSong(chatId = 20L, messageId = 4L),
                sampleSong(chatId = 30L, messageId = 5L).copy(isDeleted = true),
                sampleSong(accountId = 999L, chatId = 10L, messageId = 6L)
            )
        )
        val live = dao.getLiveSourceCounts(accountId = 100L)
        assertEquals(mapOf(10L to 2, 20L to 1), live)
        assertEquals(mapOf(10L to 1), dao.getLiveSourceCounts(accountId = 999L))
        assertTrue(dao.getLiveSourceCounts(accountId = 12345L).isEmpty())
    }

    @Test
    fun liveSourceCountsDoNotNeedASourceRowAndNeverWrite() {
        // Songs exist but no telegram_sources row and stored count 0: the live map still knows them.
        dao.upsertSources(listOf(IndexedTelegramSource(accountId = 100L, chatId = 10L, title = "Kênh A", trackCount = 0)))
        dao.upsertSongs(listOf(sampleSong(chatId = 10L, messageId = 1L), sampleSong(chatId = 77L, messageId = 2L)))
        val before = dao.getSources(100L)
        assertEquals(mapOf(10L to 1, 77L to 1), dao.getLiveSourceCounts(100L))
        // Read-only: the stored counts and the source rows are exactly as they were.
        assertEquals(before, dao.getSources(100L))
        assertEquals(0, dao.getSources(100L).single().trackCount)
    }
}
