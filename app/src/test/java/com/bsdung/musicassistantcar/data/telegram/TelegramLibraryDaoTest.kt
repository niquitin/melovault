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
}
