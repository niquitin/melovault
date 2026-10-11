package com.bsdung.musicassistantcar.ui.util

import org.junit.Assert.*
import org.junit.Test

class PlaylistBatchWriterTest {
    @Test fun emptyBatchDoesNotCallNetwork() {
        var calls = 0
        val result = PlaylistBatchWriter.write(emptyList()) {
            calls++
            PlaylistBatchWriter.Counts(it.size, 0)
        }
        assertEquals(0, calls)
        assertEquals(0, result.added)
        assertEquals(0, result.completedBatches)
        assertFalse(result.failed)
    }

    @Test fun normalPlaylist101TracksIsSavedInTwoOrderedBatches() {
        val ids = (1..101).map { "track:$it" }
        val batches = mutableListOf<List<String>>()
        val result = PlaylistBatchWriter.write(ids) {
            batches += it
            PlaylistBatchWriter.Counts(it.size, 0)
        }
        assertEquals(listOf(100, 1), batches.map { it.size })
        assertEquals(ids, batches.flatten())
        assertEquals(101, result.requested)
        assertEquals(101, result.added)
        assertEquals(2, result.completedBatches)
        assertFalse(result.failed)
    }

    @Test fun countsSkippedWhenBackendDeduplicates() {
        val outcome = PlaylistBatchWriter.write(listOf("track:1", "drive-file:2")) {
            PlaylistBatchWriter.Counts(1, 1)
        }
        assertEquals(1, outcome.added)
        assertEquals(1, outcome.skipped)
        assertFalse(outcome.failed)
    }

    @Test fun failureStopsSubsequentRequestsAndReportsPartialSave() {
        val ids = (1..250).map { "track:$it" }
        var calls = 0
        val result = PlaylistBatchWriter.write(ids) {
            calls++
            if (calls == 2) error("offline")
            PlaylistBatchWriter.Counts(it.size, 0)
        }
        assertEquals(2, calls)
        assertEquals(250, result.requested)
        assertEquals(100, result.added)
        assertEquals(1, result.completedBatches)
        assertTrue(result.failed)
    }

    @Test fun maxSupported2000IsTwentyBatches() {
        val ids = (1..2000).map { "track:$it" }
        val outcome = PlaylistBatchWriter.write(ids) {
            assertTrue(it.size <= 100)
            PlaylistBatchWriter.Counts(it.size, 0)
        }
        assertEquals(2000, outcome.added)
        assertEquals(20, outcome.completedBatches)
        assertFalse(outcome.failed)
    }
}
