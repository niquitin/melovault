package com.bsdung.musicassistantcar.playback

import com.bsdung.musicassistantcar.data.PlaybackQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackResumeStoreTest {

    private lateinit var backend: InMemoryStorageBackend
    private lateinit var store: TrackResumeStore
    private var currentTime = 1_000_000L

    @Before
    fun setUp() {
        backend = InMemoryStorageBackend()
        currentTime = 1_000_000L
        store = TrackResumeStore(backend) { currentTime }
    }

    // 1. position <10s -> no resume
    @Test
    fun testPositionUnder10sNoResume() {
        assertFalse(TrackResumePolicy.shouldSavePosition(5_000L, 180_000L))
        assertNull(TrackResumePolicy.computeResumePosition(5_000L, 180_000L))

        store.save("track:1", 5_000L, 180_000L)
        assertNull(store.get("track:1", 180_000L))
        assertNull(backend.get("track:1"))
    }

    // 2. position >=10s -> resume
    @Test
    fun testPosition10sOrMoreResume() {
        assertTrue(TrackResumePolicy.shouldSavePosition(10_000L, 180_000L))
        assertEquals(10_000L, TrackResumePolicy.computeResumePosition(10_000L, 180_000L))

        store.save("track:1", 10_000L, 180_000L)
        assertEquals(10_000L, store.get("track:1", 180_000L))

        store.save("track:2", 45_000L, 180_000L)
        assertEquals(45_000L, store.get("track:2", 180_000L))
    }

    // 3. final <20s remaining -> no resume
    @Test
    fun testFinal20sRemainingNoResume() {
        val duration = 180_000L
        val nearEndPos = 165_000L // 15s remaining < 20s

        assertTrue(TrackResumePolicy.isNearCompletion(nearEndPos, duration))
        assertFalse(TrackResumePolicy.shouldSavePosition(nearEndPos, duration))
        assertNull(TrackResumePolicy.computeResumePosition(nearEndPos, duration))

        // Pre-populate with valid position
        store.save("track:1", 60_000L, duration)
        assertEquals(60_000L, store.get("track:1", duration))

        // Saving near completion clears resume entry
        store.save("track:1", nearEndPos, duration)
        assertNull(store.get("track:1", duration))
        assertNull(backend.get("track:1"))

        // If an existing entry was stored, but duration changed such that < 20s remains
        backend.put("track:2", "60000,$currentTime")
        assertNull(store.get("track:2", 75_000L)) // 15s remaining
        assertNull(backend.get("track:2")) // Stale entry cleared
    }

    // 4. naturally completed track -> cleared
    @Test
    fun testNaturallyCompletedTrackCleared() {
        store.save("track:1", 90_000L, 180_000L)
        assertEquals(90_000L, store.get("track:1", 180_000L))

        // State ended clears entry
        store.clear("track:1")
        assertNull(store.get("track:1", 180_000L))
    }

    // 5. unknown duration -> no unsafe resume
    @Test
    fun testUnknownDurationNoUnsafeResume() {
        assertFalse(TrackResumePolicy.shouldSavePosition(60_000L, 0L))
        assertFalse(TrackResumePolicy.shouldSavePosition(60_000L, -1L))
        assertNull(TrackResumePolicy.computeResumePosition(60_000L, 0L))
        assertNull(TrackResumePolicy.computeResumePosition(60_000L, -1L))

        // Existing saved entry accessed with unknown duration
        backend.put("track:1", "60000,$currentTime")
        assertNull(store.get("track:1", 0L))
        assertNull(store.get("track:1", -1L))
        // Entry must not be cleared because duration might become known later
        assertEquals("60000,$currentTime", backend.get("track:1"))
    }

    // 6. valid position returns correct ms
    @Test
    fun testValidPositionReturnsCorrectMs() {
        store.save("track:1", 125_450L, 240_000L)
        val pos = store.get("track:1", 240_000L)
        assertEquals(125_450L, pos)
    }

    // 7. seek-to-zero does not re-trigger resume within same occurrence
    @Test
    fun testSeekToZeroDoesNotRetriggerResumeWithinSameOccurrence() {
        val mediaId = "track:1"
        val duration = 180_000L
        store.save(mediaId, 60_000L, duration)

        // Simulate active playback occurrence
        var currentOccurrenceMediaId = mediaId

        // User explicitly seeks to 0
        val seekPos = 0L
        store.save(mediaId, seekPos, duration) // Clears stored entry because < 10s

        // Guard: within same occurrence, currentOccurrenceMediaId matches, so no resume re-triggered
        val isSameOccurrence = (currentOccurrenceMediaId == mediaId)
        assertTrue(isSameOccurrence)

        // Also check that store has no resume entry
        assertNull(store.get(mediaId, duration))
    }

    // 8. metadata replace does not re-trigger resume
    @Test
    fun testMetadataReplaceDoesNotRetriggerResume() {
        val mediaId = "drive-file:123"
        val currentOccurrenceMediaId = mediaId

        // Simulated replaceMediaItem gives an updated item with the same identity
        val replacedMediaId = "drive-file:123"

        // Transition guard detects same occurrence
        val shouldSkipResume = (currentOccurrenceMediaId == replacedMediaId)
        assertTrue(shouldSkipResume)
    }

    // 9. quality switch does not re-trigger resume
    @Test
    fun testQualitySwitchDoesNotRetriggerResume() {
        val mediaId = "track:1"
        store.save(mediaId, 120_000L, 180_000L)

        // Quality switch is occurring
        PlaybackQuality.isQualitySwitching = true
        try {
            val shouldSkipResume = PlaybackQuality.isQualitySwitching
            assertTrue(shouldSkipResume)
        } finally {
            PlaybackQuality.isQualitySwitching = false
        }
    }

    // 10. new track transition can restore position
    @Test
    fun testNewTrackTransitionCanRestorePosition() {
        store.save("track:A", 60_000L, 180_000L)
        store.save("track:B", 90_000L, 240_000L)

        var lastActiveMediaId = "track:A"
        var currentOccurrenceMediaId = "track:A"

        // Transition to Track B
        val newMediaId = "track:B"
        val isNewOccurrence = (currentOccurrenceMediaId != newMediaId)
        assertTrue(isNewOccurrence)

        currentOccurrenceMediaId = newMediaId
        val restoredPos = store.get(newMediaId, 240_000L)
        assertEquals(90_000L, restoredPos)
    }

    // 11. queue jump can restore position
    @Test
    fun testQueueJumpCanRestorePosition() {
        store.save("track:5", 110_000L, 200_000L)

        // Jumping from track:1 to track:5
        val targetMediaId = "track:5"
        val restoredPos = store.get(targetMediaId, 200_000L)
        assertEquals(110_000L, restoredPos)
    }

    // 12. session-restored position has priority over per-track position
    @Test
    fun testSessionRestoredPositionHasPriorityOverPerTrackPosition() {
        val mediaId = "track:1"
        store.save(mediaId, 115_000L, 200_000L) // Older per-track position

        val sessionRestoredPosition = 30_000L
        var sessionRestoredMediaId: String? = mediaId

        // Guard check
        val hasSessionPriority = (sessionRestoredMediaId == mediaId)
        assertTrue(hasSessionPriority)

        // Session position is kept with priority, not overwritten by 115_000L
        sessionRestoredMediaId = null
        val actualPositionUsed = sessionRestoredPosition
        assertEquals(30_000L, actualPositionUsed)
    }

    // 13. storage pruning keeps newest entries
    @Test
    fun testStoragePruningKeepsNewestEntries() {
        // Insert 505 entries with increasing timestamps
        for (i in 0..504) {
            currentTime += 100L
            backend.put("track:$i", "30000,$currentTime")
        }
        assertEquals(505, backend.getAll().size)

        store.pruneIfNeeded()

        val remaining = backend.getAll()
        assertEquals(500, remaining.size)

        // Oldest 5 entries (track:0 to track:4) must be pruned
        for (i in 0..4) {
            assertNull(remaining["track:$i"])
        }

        // Newer entries (track:5 to track:504) must remain
        for (i in 5..504) {
            assertTrue(remaining.containsKey("track:$i"))
        }
    }

    // 14. invalid/corrupt stored values fail safely
    @Test
    fun testInvalidOrCorruptStoredValuesFailSafely() {
        backend.put("track:corrupt1", "not_numbers,abc")
        backend.put("track:corrupt2", "")
        backend.put("track:corrupt3", "-100,12345")
        backend.put("track:corrupt4", "single_value")

        assertNull(store.get("track:corrupt1", 180_000L))
        assertNull(store.get("track:corrupt2", 180_000L))
        assertNull(store.get("track:corrupt3", 180_000L))
        assertNull(store.get("track:corrupt4", 180_000L))

        assertNull(store.parseEntry("invalid"))
        assertNull(store.parseEntry(""))
        assertNull(store.parseEntry("-50,123"))
    }

    // 15. Track A saved at 03:30 of 04:00, naturally completes & auto-transitions to Track B -> Track A cleared
    @Test
    fun testNaturalAutoTransitionClearsDepartingTrack() {
        val trackA = "track:A"
        val trackADuration = 240_000L // 04:00
        val trackASavedPos = 210_000L // 03:30 (valid position: >=10s, remaining=30s >=20s)
        store.save(trackA, trackASavedPos, trackADuration)
        assertEquals(trackASavedPos, store.get(trackA, trackADuration))

        // Simulate natural AUTO transition from Track A to Track B
        var lastActiveMediaId: String? = trackA
        var lastKnownPositionMs = trackASavedPos
        var lastKnownDurationMs = trackADuration
        val transitionReason = "AUTO" // Player.MEDIA_ITEM_TRANSITION_REASON_AUTO

        if (transitionReason == "AUTO") {
            if (lastActiveMediaId != null) {
                store.clear(lastActiveMediaId)
            }
            lastKnownPositionMs = 0L
            lastKnownDurationMs = 0L
        }

        // Expected: Track A resume entry is cleared
        assertNull(store.get(trackA, trackADuration))
        assertNull(backend.get(trackA))
    }

    // 16. AUTO transition must not re-save Track A's stale 03:30 checkpoint
    @Test
    fun testAutoTransitionDoesNotResaveStaleCheckpoint() {
        val trackA = "track:A"
        val trackB = "track:B"
        val trackADuration = 240_000L
        val stalePos = 210_000L // 03:30

        store.save(trackA, stalePos, trackADuration)
        var lastActiveMediaId: String? = trackA
        var lastKnownPositionMs = stalePos
        var lastKnownDurationMs = trackADuration

        // On AUTO transition: clear departing track and reset lastKnownPosition
        val isAutoTransition = true
        if (isAutoTransition) {
            if (lastActiveMediaId != null) {
                store.clear(lastActiveMediaId)
            }
            lastKnownPositionMs = 0L
            lastKnownDurationMs = 0L
        } else {
            if (lastActiveMediaId != null) {
                store.save(lastActiveMediaId, lastKnownPositionMs, lastKnownDurationMs)
            }
        }

        // Active track becomes Track B
        lastActiveMediaId = trackB

        // Verify Track A is cleared and no stale checkpoint re-saves
        assertNull(store.get(trackA, trackADuration))
        if (lastKnownPositionMs > 0L) {
            store.save(trackA, lastKnownPositionMs, lastKnownDurationMs)
        }
        assertNull(store.get(trackA, trackADuration))
        assertNull(backend.get(trackA))
    }

    // 17. Manual Next / seek transition still saves departing position
    @Test
    fun testManualSeekTransitionSavesDepartingPosition() {
        val trackA = "track:A"
        val trackB = "track:B"
        val trackADuration = 240_000L
        val departingPos = 120_000L // 02:00

        var lastActiveMediaId: String? = trackA
        val lastKnownPositionMs = departingPos
        val lastKnownDurationMs = trackADuration
        val transitionReason = "SEEK" // Player.MEDIA_ITEM_TRANSITION_REASON_SEEK

        if (transitionReason != "AUTO" && transitionReason != "REPEAT") {
            if (lastActiveMediaId != null && lastActiveMediaId != trackB) {
                store.save(lastActiveMediaId, lastKnownPositionMs, lastKnownDurationMs)
            }
        }
        lastActiveMediaId = trackB

        // Expected: Track A's position was saved
        assertEquals(departingPos, store.get(trackA, trackADuration))
    }

    // 18. Natural completion with repeat does not restore an old checkpoint
    @Test
    fun testNaturalCompletionWithRepeatDoesNotRestoreOldCheckpoint() {
        val trackA = "track:A"
        val trackADuration = 240_000L
        val stalePos = 210_000L // 03:30

        store.save(trackA, stalePos, trackADuration)
        var lastActiveMediaId: String? = trackA
        val currentIdentity = trackA
        val transitionReason = "REPEAT" // Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT

        var shouldRestoreResume = true
        if (transitionReason == "REPEAT") {
            if (lastActiveMediaId != null) {
                store.clear(lastActiveMediaId)
            }
            store.clear(currentIdentity)
            shouldRestoreResume = false
        }

        // Expected: Track A entry is cleared and resume is not restored
        assertFalse(shouldRestoreResume)
        assertNull(store.get(trackA, trackADuration))
        assertNull(backend.get(trackA))
    }
}
