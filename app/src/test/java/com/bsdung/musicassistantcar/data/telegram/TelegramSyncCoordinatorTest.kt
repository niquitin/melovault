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
    fun testStalledNextOffsetReturnsPartialFailed() {
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
        // Must detect stalled cursor, return Failed with canResume=true, and mark STATUS_PARTIAL (not complete!)
        assertTrue(action3 is TelegramSyncAction.Failed)
        val failed = action3 as TelegramSyncAction.Failed
        assertEquals(-2, failed.errorCode)
        assertTrue(failed.canResume)

        val state = dao.getSyncState(100L)
        assertNotNull(state)
        assertEquals(TelegramSyncState.STATUS_PARTIAL, state!!.status)
        assertTrue(state.lastErrorMessage?.contains("stalled", ignoreCase = true) == true)
        assertFalse(coordinator.isBusy())
    }

    @Test
    fun testResumeAfterProcessDeathWhenStatusSyncing() {
        // Abrupt process death left durable state as STATUS_SYNCING with document cursor
        val crashedState = TelegramSyncState(
            accountId = 100L,
            syncScope = "global",
            phase = TelegramSyncState.PHASE_DOCUMENT,
            nextOffset = "doc_cursor_99",
            pagesSynced = 4,
            totalItemsSynced = 400,
            status = TelegramSyncState.STATUS_SYNCING
        )
        dao.updateSyncState(crashedState)

        // Reconstruct coordinator after process recreation
        val newCoordinator = TelegramSyncCoordinator(dao)
        val action = newCoordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY, forceFullRestart = false)

        assertTrue(action is TelegramSyncAction.RequestPage)
        val req = action as TelegramSyncAction.RequestPage
        assertEquals(TelegramSyncState.PHASE_DOCUMENT, req.phase)
        assertEquals("doc_cursor_99", req.offset)
    }

    @Test
    fun testPhaseSpecificIncrementalSyncDoesNotTerminatePrematurely() {
        // Seed audio track with recent date 5000L
        val audioSong = sampleSong(messageId = 10L, date = 5000L, title = "New Audio").copy(mediaType = "audio")
        // Seed document track with older date 2000L
        val docSong = sampleSong(messageId = 20L, date = 2000L, title = "Old Doc").copy(mediaType = "document")
        dao.upsertSongs(listOf(audioSong, docSong))

        // Check phase-specific dates
        assertEquals(5000L, dao.getLatestSongDate(100L, "audio"))
        assertEquals(2000L, dao.getLatestSongDate(100L, "document"))

        val action1 = coordinator.startSync(100L, TelegramSyncMode.INCREMENTAL)
        val req1 = action1 as TelegramSyncAction.RequestPage
        assertEquals(TelegramSyncState.PHASE_AUDIO, req1.phase)

        // Finish audio phase
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

        // Incoming document has date 3000L (older than audio 5000L, but newer than document 2000L)
        // With phase-specific high-water date, this must NOT be counted as overlap!
        val newDoc = sampleSong(messageId = 30L, date = 3000L, title = "Newly Added Document").copy(mediaType = "document")
        val action3 = coordinator.onPageReceived(
            accountId = 100L,
            generation = req2.generation,
            phase = TelegramSyncState.PHASE_DOCUMENT,
            songs = listOf(newDoc),
            nextOffset = "doc_page_2"
        )
        // Coordinator continues document scanning!
        assertTrue(action3 is TelegramSyncAction.RequestPage)
        val req3 = action3 as TelegramSyncAction.RequestPage
        assertEquals(TelegramSyncState.PHASE_DOCUMENT, req3.phase)
        assertEquals("doc_page_2", req3.offset)
    }

    @Test
    fun testRateLimitFloodWaitBackoffRetriesAndMaxLimit() {
        val action = coordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY)
        val req = action as TelegramSyncAction.RequestPage

        // Attempt 1: FLOOD_WAIT_15
        val action1 = coordinator.onError(
            accountId = 100L,
            generation = req.generation,
            errorCode = 429,
            errorMessage = "FLOOD_WAIT_15"
        )
        assertTrue(action1 is TelegramSyncAction.BackoffScheduled)
        val b1 = action1 as TelegramSyncAction.BackoffScheduled
        assertEquals(15, b1.retryAfterSeconds)
        assertEquals(1, b1.attempt)

        // Attempt 2: FLOOD_WAIT_30
        val action2 = coordinator.onError(
            accountId = 100L,
            generation = req.generation,
            errorCode = 429,
            errorMessage = "FLOOD_WAIT_30"
        )
        assertTrue(action2 is TelegramSyncAction.BackoffScheduled)
        val b2 = action2 as TelegramSyncAction.BackoffScheduled
        assertEquals(30, b2.retryAfterSeconds)
        assertEquals(2, b2.attempt)

        // Attempt 3: FLOOD_WAIT_60
        val action3 = coordinator.onError(
            accountId = 100L,
            generation = req.generation,
            errorCode = 429,
            errorMessage = "FLOOD_WAIT_60"
        )
        assertTrue(action3 is TelegramSyncAction.BackoffScheduled)
        val b3 = action3 as TelegramSyncAction.BackoffScheduled
        assertEquals(60, b3.retryAfterSeconds)
        assertEquals(3, b3.attempt)

        // Attempt 4: Exceeds MAX_BACKOFF_RETRIES (3) -> returns Failed
        val action4 = coordinator.onError(
            accountId = 100L,
            generation = req.generation,
            errorCode = 429,
            errorMessage = "FLOOD_WAIT_60"
        )
        assertTrue(action4 is TelegramSyncAction.Failed)
        val failed = action4 as TelegramSyncAction.Failed
        assertEquals(429, failed.errorCode)
        assertTrue(failed.canResume)
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

    @Test
    fun testForceFullRestartResetsAudioCursorAndStartsFullDiscovery() {
        // Store an existing checkpoint at document phase
        val checkpoint = TelegramSyncState(
            accountId = 100L,
            syncScope = "global",
            phase = TelegramSyncState.PHASE_DOCUMENT,
            nextOffset = "doc_offset_88",
            pagesSynced = 10,
            totalItemsSynced = 1000,
            status = TelegramSyncState.STATUS_SYNCING
        )
        dao.updateSyncState(checkpoint)

        // Force full restart must NOT resume the document cursor; it must start fresh from audio offset=""
        val action = coordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY, forceFullRestart = true)
        assertTrue(action is TelegramSyncAction.RequestPage)
        val req = action as TelegramSyncAction.RequestPage
        assertEquals(TelegramSyncState.PHASE_AUDIO, req.phase)
        assertEquals("", req.offset)

        // Verify state was reset in DAO
        val resetState = dao.getSyncState(100L)
        assertNotNull(resetState)
        assertEquals(TelegramSyncState.PHASE_AUDIO, resetState?.phase)
        assertEquals("", resetState?.nextOffset)
        assertEquals(0, resetState?.pagesSynced)
    }

    @Test
    fun testRapidAccountOrGenerationChangeDiscardsStalePage() {
        val action1 = coordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY)
        assertTrue(action1 is TelegramSyncAction.RequestPage)
        val req1 = action1 as TelegramSyncAction.RequestPage

        // User switches account or restarts sync
        val action2 = coordinator.startSync(100L, TelegramSyncMode.FULL_DISCOVERY, forceFullRestart = true)
        assertTrue(action2 is TelegramSyncAction.RequestPage)
        val req2 = action2 as TelegramSyncAction.RequestPage
        assertTrue(req2.generation > req1.generation)

        // Late response arrives with old generation -> must be DiscardedStale
        val staleAction = coordinator.onPageReceived(
            accountId = 100L,
            generation = req1.generation,
            phase = TelegramSyncState.PHASE_AUDIO,
            songs = listOf(sampleSong(messageId = 1L)),
            nextOffset = "page_1"
        )
        assertEquals(TelegramSyncAction.DiscardedStale, staleAction)
    }
}

