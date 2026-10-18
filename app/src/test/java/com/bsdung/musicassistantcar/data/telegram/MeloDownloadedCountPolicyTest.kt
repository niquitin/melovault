package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloDownloadedCountPolicy.Decision
import com.bsdung.musicassistantcar.data.telegram.MeloDownloadedCountPolicy.ScanState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloDownloadedCountPolicyTest {

    private val ready = ScanState(
        loggedIn = true, hasSongs = true, syncing = false, playingOrOpening = false,
        powerSave = false, foreground = true, alreadyScannedThisProcess = false, storedAgeMs = null
    )

    @Test fun allConditionsMetWithNoStoredCountScans() {
        assertEquals(Decision.SCAN, MeloDownloadedCountPolicy.decide(ready))
        assertTrue(MeloDownloadedCountPolicy.shouldBackgroundScan(ready))
    }

    @Test fun oldStoredCountStillScans() {
        val old = ready.copy(storedAgeMs = MeloDownloadedCountPolicy.MAX_STORED_AGE_MS)
        assertEquals(Decision.SCAN, MeloDownloadedCountPolicy.decide(old))
    }

    @Test fun eachMissingConditionSkips() {
        assertEquals(Decision.NOT_LOGGED_IN, MeloDownloadedCountPolicy.decide(ready.copy(loggedIn = false)))
        assertEquals(Decision.NO_SONGS, MeloDownloadedCountPolicy.decide(ready.copy(hasSongs = false)))
        assertEquals(Decision.SYNCING, MeloDownloadedCountPolicy.decide(ready.copy(syncing = true)))
        assertEquals(Decision.PLAYING_OR_OPENING, MeloDownloadedCountPolicy.decide(ready.copy(playingOrOpening = true)))
        assertEquals(Decision.POWER_SAVE, MeloDownloadedCountPolicy.decide(ready.copy(powerSave = true)))
        assertEquals(Decision.BACKGROUND, MeloDownloadedCountPolicy.decide(ready.copy(foreground = false)))
        listOf(
            ready.copy(loggedIn = false), ready.copy(hasSongs = false), ready.copy(syncing = true),
            ready.copy(playingOrOpening = true), ready.copy(powerSave = true), ready.copy(foreground = false)
        ).forEach { assertFalse(MeloDownloadedCountPolicy.shouldBackgroundScan(it)) }
    }

    @Test fun alreadyScannedInThisProcessDoesNotScanAgain() {
        assertEquals(Decision.ALREADY_SCANNED, MeloDownloadedCountPolicy.decide(ready.copy(alreadyScannedThisProcess = true)))
    }

    @Test fun freshStoredCountSkipsAndOldOrMissingScans() {
        val fiveMinutes = ready.copy(storedAgeMs = 5L * 60_000L)
        assertEquals(Decision.STORED_FRESH, MeloDownloadedCountPolicy.decide(fiveMinutes))
        val justUnder = ready.copy(storedAgeMs = MeloDownloadedCountPolicy.MAX_STORED_AGE_MS - 1L)
        assertEquals(Decision.STORED_FRESH, MeloDownloadedCountPolicy.decide(justUnder))
        val exactly = ready.copy(storedAgeMs = MeloDownloadedCountPolicy.MAX_STORED_AGE_MS)
        assertEquals(Decision.SCAN, MeloDownloadedCountPolicy.decide(exactly))
        assertEquals(Decision.SCAN, MeloDownloadedCountPolicy.decide(ready.copy(storedAgeMs = null)))
    }

    @Test fun storedAgeIgnoresMissingOrFutureTimestamps() {
        assertEquals(1_000L, MeloDownloadedCountPolicy.storedAge(nowMs = 5_000L, storedAtMs = 4_000L))
        assertNull(MeloDownloadedCountPolicy.storedAge(nowMs = 5_000L, storedAtMs = null))
        assertNull(MeloDownloadedCountPolicy.storedAge(nowMs = 5_000L, storedAtMs = 0L))
        assertNull(MeloDownloadedCountPolicy.storedAge(nowMs = 5_000L, storedAtMs = 9_000L))
        assertFalse(MeloDownloadedCountPolicy.isFresh(null))
        assertFalse(MeloDownloadedCountPolicy.isFresh(-1L))
    }

    @Test fun finishedScanBeatsStoredValue() {
        val stored = MeloDownloadedCountPolicy.Stored(accountId = 7L, value = 60, atMs = 1L)
        assertEquals(67, MeloDownloadedCountPolicy.displayValue(7L, stored, scanned = 67))
        assertEquals(0, MeloDownloadedCountPolicy.displayValue(7L, stored, scanned = 0))
        assertEquals(60, MeloDownloadedCountPolicy.displayValue(7L, stored, scanned = null))
    }

    @Test fun storedValueOfAnotherAccountIsDropped() {
        val stored = MeloDownloadedCountPolicy.Stored(accountId = 7L, value = 60, atMs = 1L)
        assertNull(MeloDownloadedCountPolicy.displayValue(8L, stored, scanned = null))
        assertNull(MeloDownloadedCountPolicy.displayValue(0L, stored.copy(accountId = 0L), scanned = null))
        assertNull(MeloDownloadedCountPolicy.displayValue(7L, null, scanned = null))
        assertNull(MeloDownloadedCountPolicy.displayValue(7L, stored.copy(value = -1), scanned = null))
    }

    @Test fun prefsKeysAreSeparatePerAccount() {
        assertEquals("downloaded_7", MeloDownloadedCountPolicy.valueKey(7L))
        assertEquals("downloaded_at_7", MeloDownloadedCountPolicy.timeKey(7L))
    }
}
