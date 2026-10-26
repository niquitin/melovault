package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloResumePolicyTest {

    @Test fun prefsKeysCarryTheAccountId() {
        assertTrue(MeloResumePolicy.songKey(42L).endsWith("42"))
        assertTrue(MeloResumePolicy.posKey(42L).endsWith("42"))
        assertTrue(MeloResumePolicy.atKey(42L).endsWith("42"))
        assertNotEquals(MeloResumePolicy.songKey(1L), MeloResumePolicy.songKey(2L))
        assertNotEquals(MeloResumePolicy.posKey(1L), MeloResumePolicy.posKey(2L))
        assertNotEquals(MeloResumePolicy.atKey(1L), MeloResumePolicy.atKey(2L))
        assertEquals("melovault_resume", MeloResumePolicy.PREFS_NAME)
    }

    @Test fun recordNeedsAnAccountAndASongKey() {
        assertNull(MeloResumePolicy.record(0L, 5L, 1000L, 1L))
        assertNull(MeloResumePolicy.record(-3L, 5L, 1000L, 1L))
        assertNull(MeloResumePolicy.record(7L, null, 1000L, 1L))
        assertNull(MeloResumePolicy.record(7L, 0L, 1000L, 1L))
        val r = MeloResumePolicy.record(7L, 99L, 30_000L, 123L)
        assertNotNull(r)
        assertEquals(MeloResumePolicy.Record(7L, 99L, 30_000L, 123L), r)
    }

    @Test fun recordNeverKeepsANegativePosition() {
        assertEquals(0L, MeloResumePolicy.record(7L, 99L, -5L, 1L)!!.positionMs)
    }

    @Test fun songKeyOnlyReadsTelegramMediaIds() {
        assertEquals(123L, MeloResumePolicy.songKeyOf("telegram:123"))
        assertEquals(-9L, MeloResumePolicy.songKeyOf("telegram:-9"))
        assertNull(MeloResumePolicy.songKeyOf("telegram:abc"))
        assertNull(MeloResumePolicy.songKeyOf("telegram:"))
        assertNull(MeloResumePolicy.songKeyOf("telegram:0"))
        assertNull(MeloResumePolicy.songKeyOf("picked:audio"))
        assertNull(MeloResumePolicy.songKeyOf(MeloLocalSourcePolicy.MEDIA_ID_PREFIX + "55"))
        assertNull(MeloResumePolicy.songKeyOf(null))
    }

    @Test fun shouldWriteNeedsReadyAuthAnAccountAndATelegramSong() {
        assertTrue(MeloResumePolicy.shouldWrite(true, 7L, "telegram:123"))
        assertFalse(MeloResumePolicy.shouldWrite(false, 7L, "telegram:123"))
        assertFalse(MeloResumePolicy.shouldWrite(true, 0L, "telegram:123"))
        assertFalse(MeloResumePolicy.shouldWrite(true, 7L, "picked:audio"))
        assertFalse(MeloResumePolicy.shouldWrite(true, 7L, "local:55"))
        assertFalse(MeloResumePolicy.shouldWrite(true, 7L, null))
    }

    @Test fun anIncompleteFileAlwaysStartsFromTheBeginning() {
        assertEquals(0L, MeloResumePolicy.startPositionMs(60_000L, 200, fileComplete = false))
    }

    @Test fun lessThanFiveSecondsStartsFromTheBeginning() {
        assertEquals(0L, MeloResumePolicy.startPositionMs(0L, 200, true))
        assertEquals(0L, MeloResumePolicy.startPositionMs(4_999L, 200, true))
        assertEquals(5_000L, MeloResumePolicy.startPositionMs(5_000L, 200, true))
    }

    @Test fun theLastTenSecondsStartFromTheBeginning() {
        assertEquals(189_000L, MeloResumePolicy.startPositionMs(189_000L, 200, true))
        assertEquals(190_000L, MeloResumePolicy.startPositionMs(190_000L, 200, true))
        assertEquals(0L, MeloResumePolicy.startPositionMs(190_001L, 200, true))
    }

    @Test fun anUnknownDurationKeepsTheSavedPosition() {
        assertEquals(500_000L, MeloResumePolicy.startPositionMs(500_000L, 0, true))
    }
}
