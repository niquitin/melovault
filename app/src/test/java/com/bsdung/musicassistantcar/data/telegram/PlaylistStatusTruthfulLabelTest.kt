package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.PlaylistSyncStatusPolicy.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E96 P5B3 REWORK-03: the in-memory [SyncRunLog] is empty in every new process. "No run in this session" is NOT "never
 * synchronised" (the playlist may have been synchronised yesterday), and it is never "synchronised" either.
 */
class PlaylistStatusTruthfulLabelTest {
    private val consent = SyncConsent(enabled = true, mayCreateChannel = false, mayTransmitInitial = false, acceptedWarningVersion = SyncConsent.WARNING_VERSION)

    private fun facts(pending: Int = 0, retry: Int = 0) =
        PlaylistStatusFacts(1001L, 2, pending, retry, 0, true, 9L, false, false, null, null)

    private fun line(run: LastSyncRun?, f: PlaylistStatusFacts = facts()) =
        PlaylistSyncStatusPolicy.evaluate(1001L, f, SyncContext(true, consent, false, run))

    @Test fun r3_afterANewProcessTheLabelDoesNotClaimThePlaylistWasNeverSynced() {
        val l = line(run = null)
        assertEquals(Kind.NEVER_SYNCED, l.kind) // the kind keeps its name; only the words change
        assertFalse("must not say never synced: ${l.text}", l.text.contains("Chưa đồng bộ lần nào"))
        assertFalse(l.detail.contains("Chưa có lần đồng bộ nào"))
        assertEquals("Đã lưu trên máy · Chưa kiểm tra đồng bộ trong phiên này", l.text)
    }

    @Test fun r3_andItNeverClaimsSyncedEither() {
        val l = line(run = null)
        assertNotEquals(Kind.SYNCED, l.kind)
        assertFalse(l.text.contains("Đã đồng bộ"))
        assertTrue("it still tells the person what to do", l.detail.contains("Đồng bộ ngay"))
    }

    @Test fun r3_aRealRunInThisSessionStillSpeaksForItself() {
        assertEquals(Kind.SYNCED, line(LastSyncRun(1001L, SyncStatus.OK, opCounterAtRun = 9L)).kind)
        val withC = SyncConsent(enabled = true, mayCreateChannel = false, mayTransmitInitial = true, acceptedWarningVersion = SyncConsent.WARNING_VERSION)
        assertEquals(Kind.LOCAL_PENDING, PlaylistSyncStatusPolicy.evaluate(1001L, facts(pending = 2), SyncContext(true, withC, false, null)).kind)
        assertEquals(Kind.LOCAL_PENDING, line(LastSyncRun(1001L, SyncStatus.OK, opCounterAtRun = 3L)).kind)
    }
}
