package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TelegramSyncCoordinatorTest {

    private lateinit var dao: TelegramLibraryDao
    private lateinit var coordinator: TelegramSyncCoordinator

    @Before
    fun setUp() {
        dao = InMemoryTelegramLibraryDao()
        coordinator = TelegramSyncCoordinator(dao)
    }

    private fun sampleSong(
        accountId: Long = 100L,
        chatId: Long = 200L,
        messageId: Long = 1L,
        date: Long = 1000L,
        title: String = "Test Song"
    ): IndexedTelegramSong {
        return IndexedTelegramSong(
            accountId = accountId,
            chatId = chatId,
            messageId = messageId,
            songKey = messageId,
            fileId = 1,
            title = title,
            filename = "$title.mp3",
            date = date,
            lastSeenAtMs = System.currentTimeMillis()
        )
    }

    @Test
    fun testFullDiscoveryFlowBothPhasesComplete() {
        val action1 = coordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY)
        assertTrue(action1 is TelegramSyncAction.RequestPage)
        val req1 = action1 as TelegramSyncAction.RequestPage
        assertEquals(TelegramSyncState.PHASE_AUDIO, req1.phase)
        assertEquals("", req1.offset)

        // Page 1 audio: 2 tracks, next offset "audio_page_2"
        val songsPage1 = listOf(
            sampleSong(messageId = 1L, title = "Audio 1"),
            sampleSong(messageId = 2L, title = "Audio 2")
        )
        val action2 = coordinator.onPageReceived(
            accountId = 100L,
            generation = req1.generation,
            phase = TelegramSyncState.PHASE_AUDIO,
            songs = songsPage1,
            nextOffset = "audio_page_2"
        )
        assertTrue(action2 is TelegramSyncAction.RequestPage)
        val req2 = action2 as TelegramSyncAction.RequestPage
        assertEquals(TelegramSyncState.PHASE_AUDIO, req2.phase)
        assertEquals("audio_page_2", req2.offset)

        // Page 2 audio: 1 track, next offset "" (audio phase exhausted)
        val songsPage2 = listOf(sampleSong(messageId = 3L, title = "Audio 3"))
        val action3 = coordinator.onPageReceived(
            accountId = 100L,
            generation = req2.generation,
            phase = TelegramSyncState.PHASE_AUDIO,
            songs = songsPage2,
            nextOffset = ""
        )
        // Automatically advances to document phase!
        assertTrue(action3 is TelegramSyncAction.RequestPage)
        val req3 = action3 as TelegramSyncAction.RequestPage
        assertEquals(TelegramSyncState.PHASE_DOCUMENT, req3.phase)
        assertEquals("", req3.offset)

        // Page 1 document: 1 track, next offset "" (document phase exhausted)
        val docSongs = listOf(sampleSong(messageId = 4L, title = "Doc Audio 4"))
        val action4 = coordinator.onPageReceived(
            accountId = 100L,
            generation = req3.generation,
            phase = TelegramSyncState.PHASE_DOCUMENT,
            songs = docSongs,
            nextOffset = ""
        )
        // Discovery is now complete!
        assertTrue(action4 is TelegramSyncAction.Completed)
        val completed = action4 as TelegramSyncAction.Completed
        assertEquals(4, completed.totalTracksIndexed)
        assertFalse(completed.isIncremental)

        // Verify state in DAO
        val state = dao.getSyncState(100L)
        assertNotNull(state)
        assertEquals(TelegramSyncState.STATUS_COMPLETE, state!!.status)
        assertEquals(TelegramSyncState.PHASE_IDLE, state.phase)
        assertEquals(4, dao.getSongCount(100L))
    }

    @Test
    fun testIncrementalSyncStopsAtHighWaterMark() {
        // Pre-populate with high water mark at date = 5000L
        val oldSong = sampleSong(messageId = 10L, date = 5000L, title = "Old Song")
        dao.upsertSongs(listOf(oldSong))

        val action1 = coordinator.startSync(100L, TelegramSyncMode.INCREMENTAL)
        assertTrue(action1 is TelegramSyncAction.RequestPage)
        val req1 = action1 as TelegramSyncAction.RequestPage

        // Send page with new tracks (date 8000L, 7000L) + overlap tracks with date <= 5000L
        val incoming = mutableListOf<IndexedTelegramSong>()
        incoming.add(sampleSong(messageId = 101L, date = 8000L, title = "Brand New Track"))
        incoming.add(sampleSong(messageId = 102L, date = 7000L, title = "Another New Track"))
        // Add 10 overlap tracks (boundary tolerance threshold)
        for (i in 1..10) {
            incoming.add(sampleSong(messageId = (200 + i).toLong(), date = 4000L, title = "Overlap $i"))
        }

        // Even though TDLib returned a non-empty next_offset "more_history", incremental boundary is reached!
        val action2 = coordinator.onPageReceived(
            accountId = 100L,
            generation = req1.generation,
            phase = TelegramSyncState.PHASE_AUDIO,
            songs = incoming,
            nextOffset = "more_history"
        )
        // Should advance to document phase rather than scanning deeper audio history
        assertTrue(action2 is TelegramSyncAction.RequestPage)
        val req2 = action2 as TelegramSyncAction.RequestPage
        assertEquals(TelegramSyncState.PHASE_DOCUMENT, req2.phase)
    }

    @Test
    fun testResumeFromStoredCheckpointAfterRestart() {
        // Mock a saved partial sync state in DAO
        val checkpoint = TelegramSyncState(
            accountId = 100L,
            syncScope = "global",
            phase = TelegramSyncState.PHASE_DOCUMENT,
            nextOffset = "doc_cursor_99",
            pagesSynced = 5,
            totalItemsSynced = 500,
            status = TelegramSyncState.STATUS_PARTIAL
        )
        dao.updateSyncState(checkpoint)

        // Starting sync should resume from document cursor 99
        val action = coordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY, forceFullRestart = false)
        assertTrue(action is TelegramSyncAction.RequestPage)
        val req = action as TelegramSyncAction.RequestPage
        assertEquals(TelegramSyncState.PHASE_DOCUMENT, req.phase)
        assertEquals("doc_cursor_99", req.offset)
    }

    @Test
    fun testStalledNextOffsetBreaksLoop() {
        val action1 = coordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY)
        val req1 = action1 as TelegramSyncAction.RequestPage

        // Page 1 returns nextOffset = "cursor_X"
        val action2 = coordinator.onPageReceived(
            accountId = 100L,
            generation = req1.generation,
            phase = TelegramSyncState.PHASE_AUDIO,
            songs = listOf(sampleSong(messageId = 1L)),
            nextOffset = "cursor_X"
        )
        val req2 = action2 as TelegramSyncAction.RequestPage
        assertEquals("cursor_X", req2.offset)

        // Page 2 returns the EXACT SAME nextOffset "cursor_X" (stalled cursor)
        val action3 = coordinator.onPageReceived(
            accountId = 100L,
            generation = req2.generation,
            phase = TelegramSyncState.PHASE_AUDIO,
            songs = listOf(sampleSong(messageId = 2L)),
            nextOffset = "cursor_X"
        )
        // Must detect stalled cursor and advance to document phase instead of infinite loop!
        assertTrue(action3 is TelegramSyncAction.RequestPage)
        val req3 = action3 as TelegramSyncAction.RequestPage
        assertEquals(TelegramSyncState.PHASE_DOCUMENT, req3.phase)
        assertEquals("", req3.offset)
    }

    @Test
    fun testAccountSwitchDiscardsStaleCallbacks() {
        // Start sync for account 100
        val actionAcc1 = coordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY)
        val reqAcc1 = actionAcc1 as TelegramSyncAction.RequestPage
        val gen1 = reqAcc1.generation

        // User switches to account 200
        val actionAcc2 = coordinator.startSync(200L, TelegramSyncMode.FULL_DISCOVERY)
        val reqAcc2 = actionAcc2 as TelegramSyncAction.RequestPage
        val gen2 = reqAcc2.generation

        assertTrue(gen2 > gen1)
        assertEquals(200L, coordinator.getActiveAccountId())

        // Late callback from account 100 arrives
        val lateAction = coordinator.onPageReceived(
            accountId = 100L,
            generation = gen1,
            phase = TelegramSyncState.PHASE_AUDIO,
            songs = listOf(sampleSong(accountId = 100L, messageId = 999L)),
            nextOffset = "next_acc1"
        )
        assertEquals(TelegramSyncAction.DiscardedStale, lateAction)
        assertEquals(0, dao.getSongCount(200L))
    }

    @Test
    fun testRateLimit429MarksPartialCanResume() {
        val action = coordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY)
        val req = action as TelegramSyncAction.RequestPage

        val errAction = coordinator.onError(
            accountId = 100L,
            generation = req.generation,
            errorCode = 429,
            errorMessage = "FLOOD_WAIT_30"
        )
        assertTrue(errAction is TelegramSyncAction.Failed)
        val failed = errAction as TelegramSyncAction.Failed
        assertEquals(429, failed.errorCode)
        assertTrue(failed.canResume)

        val state = dao.getSyncState(100L)
        assertNotNull(state)
        assertEquals(TelegramSyncState.STATUS_PARTIAL, state!!.status)
        assertFalse(coordinator.isBusy())
    }

    @Test
    fun testPauseAndResumeSync() {
        val action = coordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY)
        val req = action as TelegramSyncAction.RequestPage

        // Advance to offset "page_3"
        coordinator.onPageReceived(
            accountId = 100L,
            generation = req.generation,
            phase = TelegramSyncState.PHASE_AUDIO,
            songs = listOf(sampleSong(messageId = 1L)),
            nextOffset = "page_3"
        )

        val pausedAction = coordinator.pauseSync()
        assertNotNull(pausedAction)
        assertTrue(pausedAction is TelegramSyncAction.Paused)
        assertFalse(coordinator.isBusy())

        val state = dao.getSyncState(100L)
        assertEquals(TelegramSyncState.STATUS_PAUSED, state!!.status)
        assertEquals("page_3", state.nextOffset)

        // Resuming sync
        val resumedAction = coordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY)
        assertTrue(resumedAction is TelegramSyncAction.RequestPage)
        val reqResume = resumedAction as TelegramSyncAction.RequestPage
        assertEquals("page_3", reqResume.offset)
    }

    @Test
    fun testEmptyLibraryTerminatesCleanly() {
        val action1 = coordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY)
        val req1 = action1 as TelegramSyncAction.RequestPage

        // Audio phase returns 0 tracks and empty offset
        val action2 = coordinator.onPageReceived(
            accountId = 100L,
            generation = req1.generation,
            phase = TelegramSyncState.PHASE_AUDIO,
            songs = emptyList(),
            nextOffset = ""
        )
        assertTrue(action2 is TelegramSyncAction.RequestPage)
        val req2 = action2 as TelegramSyncAction.RequestPage
        assertEquals(TelegramSyncState.PHASE_DOCUMENT, req2.phase)

        // Document phase returns 0 tracks and empty offset
        val action3 = coordinator.onPageReceived(
            accountId = 100L,
            generation = req2.generation,
            phase = TelegramSyncState.PHASE_DOCUMENT,
            songs = emptyList(),
            nextOffset = ""
        )
        assertTrue(action3 is TelegramSyncAction.Completed)
        val completed = action3 as TelegramSyncAction.Completed
        assertEquals(0, completed.totalTracksIndexed)
        assertEquals(0, dao.getSongCount(100L))
        assertEquals(TelegramSyncState.STATUS_COMPLETE, dao.getSyncState(100L)!!.status)
    }
}
