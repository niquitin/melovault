package com.bsdung.musicassistantcar.playback

import org.junit.Assert.*
import org.junit.Test

class RapidNextBufferPolicyTest {
    @Test fun noRecoveryForOrdinarySingleTrackBuffering() {
        val p = RapidSkipBufferPolicy()
        p.recordSeek(1000L)
        assertFalse(p.canWatch(1001L, "drive-file:1"))
        assertFalse(p.shouldRecover(50_000, 1000, "drive-file:1", "drive-file:1", false, true, false))
    }

    @Test fun fiveRapidSeeksEnableOnlyOneBoundedRecovery() {
        val p = RapidSkipBufferPolicy()
        for (i in 0 until 5) p.recordSeek(1000L + i * 200L)
        assertTrue(p.canWatch(1900, "drive-file:60"))
        assertFalse(p.shouldRecover(20_000, 1900, "drive-file:60", "drive-file:60", true, true, false))
        assertTrue(p.shouldRecover(26_901, 1900, "drive-file:60", "drive-file:60", true, true, false))
        p.markRecovery("drive-file:60")
        assertFalse(p.canWatch(1950, "drive-file:60"))
        assertFalse(p.shouldRecover(40_000, 1900, "drive-file:60", "drive-file:60", true, true, false))
    }

    @Test fun pauseNewTrackOrRecoveryShutdownAlwaysCancel() {
        val p = RapidSkipBufferPolicy()
        repeat(8) { p.recordSeek(it * 300L) }
        assertFalse(p.shouldRecover(30_000, 2000, "drive-file:61", "drive-file:62", true, true, false))
        assertFalse(p.shouldRecover(30_000, 2000, "drive-file:61", "drive-file:61", false, true, false))
        assertFalse(p.shouldRecover(30_000, 2000, "drive-file:61", "drive-file:61", true, false, false))
        assertFalse(p.shouldRecover(30_000, 2000, "drive-file:61", "drive-file:61", true, true, true))
    }

    @Test fun burstAgesOutAndNoWatchForSlowHumanNextPresses() {
        val p = RapidSkipBufferPolicy()
        repeat(5) { p.recordSeek(it * 5000L) }
        assertFalse(p.canWatch(20_000, "drive-file:8"))
        assertFalse(p.canWatch(20_000, null))
    }

    @Test fun metadataEnrichmentNeverTouchesStaleOrBufferingCurrentItem() {
        assertFalse(DriveMetadataUpdatePolicy.shouldApply(false, 45, 45, true))
        assertFalse(DriveMetadataUpdatePolicy.shouldApply(true, 45, 48, true))
        assertFalse(DriveMetadataUpdatePolicy.shouldApply(true, 48, 45, true))
        assertFalse(DriveMetadataUpdatePolicy.shouldApply(true, 45, 45, false))
        assertTrue(DriveMetadataUpdatePolicy.shouldApply(true, 45, 45, true))
        assertTrue(DriveMetadataUpdatePolicy.shouldApply(true, 46, 45, false))
    }
}
