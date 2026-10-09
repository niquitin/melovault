package com.bsdung.musicassistantcar.playback

import org.junit.Assert.*
import org.junit.Test

class QueueStartupRestorePolicyTest {
    @Test fun emptyLiveQueueMayRestoreWhenSessionAlive() {
        assertTrue(QueueStartupRestorePolicy.shouldApply(false, true, 0))
    }

    @Test fun neverReplaceNewQueueOrDestroyedSession() {
        assertFalse(QueueStartupRestorePolicy.shouldApply(false, true, 1))
        assertFalse(QueueStartupRestorePolicy.shouldApply(false, true, 3))
        assertFalse(QueueStartupRestorePolicy.shouldApply(true, true, 0))
        assertFalse(QueueStartupRestorePolicy.shouldApply(false, false, 0))
    }

    @Test fun preservedIndexWhenEverySavedSongResolves() {
        assertEquals(2, QueueStartupRestorePolicy.targetIndex(
            listOf("track:1", "track:2", "track:3"),
            2, listOf("track:1", "track:2", "track:3"),
        ))
    }

    @Test fun indexAdaptsWhenUnavailableSongsAreSkipped() {
        assertEquals(1, QueueStartupRestorePolicy.targetIndex(
            listOf("track:1", "track:2", "track:3"),
            2, listOf("track:1", "track:3"),
        ))
    }

    @Test fun missingTargetFallsBackToFirstResolvableSong() {
        assertEquals(0, QueueStartupRestorePolicy.targetIndex(
            listOf("track:1", "track:2", "track:3"),
            1, listOf("track:3"),
        ))
    }

    @Test fun malformedSavedIndexAndEmptyQueueStaySafe() {
        assertEquals(0, QueueStartupRestorePolicy.targetIndex(listOf("track:1"), 999, listOf("track:1")))
        assertEquals(0, QueueStartupRestorePolicy.targetIndex(listOf("track:1"), -1, emptyList()))
    }
}
