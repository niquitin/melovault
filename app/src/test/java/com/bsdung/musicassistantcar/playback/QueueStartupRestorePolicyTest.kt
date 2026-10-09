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

    @Test fun duplicateTracksRestoreSelectedOccurrenceRatherThanFirstCopy() {
        assertEquals(2, QueueStartupRestorePolicy.targetIndex(
            listOf("track:1", "track:2", "track:1"),
            2, listOf("track:1", "track:2", "track:1"),
        ))
    }

    @Test fun selectedDuplicateAfterUnavailableTrackKeepsCorrectOccurrenceAndPosition() {
        assertEquals(
            QueueRestoreTarget(1, 54_321L),
            QueueStartupRestorePolicy.selectTarget(2, 54_321L, listOf(0, 2)),
        )
    }

    @Test fun missingCurrentTrackUsesNextSongWithoutLeakingPreviousPosition() {
        assertEquals(
            QueueRestoreTarget(1, 0L),
            QueueStartupRestorePolicy.selectTarget(1, 98_765L, listOf(0, 2)),
        )
    }

    @Test fun missingLastSongUsesPreviousSurvivorFromStart() {
        assertEquals(
            QueueRestoreTarget(1, 0L),
            QueueStartupRestorePolicy.selectTarget(3, 80_000L, listOf(0, 2)),
        )
    }

    @Test fun emptyRestoreAndMalformedPositionAreSafelyClamped() {
        assertEquals(
            QueueRestoreTarget(0, 0L),
            QueueStartupRestorePolicy.selectTarget(0, 34_567L, emptyList()),
        )
        assertEquals(
            QueueRestoreTarget(0, 0L),
            QueueStartupRestorePolicy.selectTarget(0, -4_000L, listOf(0)),
        )
    }
}
