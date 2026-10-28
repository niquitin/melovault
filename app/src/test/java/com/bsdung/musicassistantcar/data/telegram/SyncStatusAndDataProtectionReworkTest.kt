package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.PlaylistSyncStatusPolicy.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * E96 P5B3 REWORK-01 T22 + T23: the new states say what they are (and are never "synchronised"), and a read-only or
 * blocked run on a state like the Sony's (many saved songs, five operations of doubt) changes nothing on disk.
 */
class SyncStatusAndDataProtectionReworkTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)

    private fun facts(pending: Int = 5, retry: Int = 5, saved: Int = 350) =
        PlaylistStatusFacts(1001L, saved, pending, retry, 0, true, 3L, false, false, null, null)

    private val consent = SyncConsent(enabled = true, mayCreateChannel = false, mayTransmitInitial = true, acceptedWarningVersion = SyncConsent.WARNING_VERSION)

    private fun line(run: LastSyncRun?, f: PlaylistStatusFacts = facts()) =
        PlaylistSyncStatusPolicy.evaluate(1001L, f, SyncContext(true, consent, false, run))

    // ---- T22 ----------------------------------------------------------------------------------------------------------------

    @Test fun t22_theNewStatesHaveTheirOwnSentenceAndAreNeverSynced() {
        val limit = line(LastSyncRun(1001L, SyncStatus.SEND_LIMIT_REACHED, pendingLeft = 2, opCounterAtRun = 3L), facts(pending = 2, retry = 0))
        val doubt = line(LastSyncRun(1001L, SyncStatus.DELIVERY_UNCONFIRMED, stage = SyncStage.SEND_ACK_TIMEOUT, opCounterAtRun = 3L))
        val rejected = line(LastSyncRun(1001L, SyncStatus.RECOVERY_REQUIRED, RecoveryReason.REJECTED_BY_TELEGRAM, opCounterAtRun = 3L))
        val blocked = line(LastSyncRun(1001L, SyncStatus.RECOVERY_REQUIRED, RecoveryReason.DEPENDENCY_BLOCKED, opCounterAtRun = 3L))
        assertEquals(Kind.SEND_LIMIT, limit.kind)
        assertEquals(Kind.DELIVERY_UNCONFIRMED, doubt.kind)
        assertEquals(Kind.RECOVERY_REJECTED, rejected.kind)
        assertEquals(Kind.RECOVERY_HISTORY, blocked.kind)
        val texts = listOf(limit.text, doubt.text, rejected.text, blocked.text)
        assertEquals("each state has its own sentence", texts.size, texts.toSet().size)
        for (l in listOf(limit, doubt, rejected, blocked)) {
            assertNotEquals(Kind.SYNCED, l.kind)
            assertFalse(l.text.contains("Đã đồng bộ"))
        }
        assertTrue(doubt.fullDetail().contains("KHÔNG gửi lại"))
        assertTrue(doubt.fullDetail().contains("Mã chẩn đoán: SEND_ACK_TIMEOUT"))
        assertTrue(limit.fullDetail().contains("tự gửi tiếp khi đang mở"))
        assertTrue(limit.detail.contains("2 thay đổi"))
    }

    @Test fun onlyAnOkRunWithNothingWaitingCanEverBeSynced() {
        for (status in SyncStatus.values()) {
            val synced = line(LastSyncRun(1001L, status, opCounterAtRun = 3L), facts(pending = 0, retry = 0)).kind == Kind.SYNCED
            assertEquals("status $status", status == SyncStatus.OK, synced)
        }
        // five operations of doubt keep even an OK report from reading as synchronised
        assertNotEquals(Kind.SYNCED, line(LastSyncRun(1001L, SyncStatus.OK, opCounterAtRun = 3L), facts(pending = 5, retry = 5)).kind)
        assertTrue(line(LastSyncRun(1001L, SyncStatus.OK, opCounterAtRun = 3L), facts(pending = 5, retry = 5)).fullDetail().contains("không tự gửi lại"))
    }

    @Test fun aLockedCreateIsStatedAsSuchAndNotAsAMissingConsent() {
        val l = line(LastSyncRun(1001L, denied = SyncDenied.CREATE_LOCKED), facts(pending = 0, retry = 0))
        assertEquals(Kind.CREATE_LOCKED, l.kind)
        assertTrue(l.detail.contains("không tạo kênh Telegram mới"))
    }

    // ---- T23 ----------------------------------------------------------------------------------------------------------------

    private fun digestOf(root: File): Map<String, String> {
        val out = java.util.TreeMap<String, String>()
        root.walkTopDown().filter { it.isFile }.forEach {
            val d = MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) }
            out[it.relativeTo(root).path.replace('\\', '/')] = d
        }
        return out
    }

    @Test fun t23_readOnlyAndBlockedRunsLeaveTheSonysKindOfStateByteForByteAlone() {
        val channel = FakeTelegramChannel()
        val root = folder.newFolder("sony")
        val a = FakeSyncDevice("dev-a", root, acct, channel, seed = 31)
        for (n in 1..60) a.toggle(acct.value, n * 4_194_304L)
        a.toggle(acct.value, 1 * 4_194_304L) // a removal: a tombstone
        a.toggle(acct.value, 2 * 4_194_304L)
        // five of the operations are in doubt, as the Sony's were
        a.session.update(acct, a.session.view(acct)!!.epoch) { s -> s.outbox.take(5).fold(s) { acc, e -> acc.markRetryRequired(e.op.opId) } }
        // the first read-only look catches the cursor up; after it nothing in the channel is new
        assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, a.coordinator.runOnce(acct, SyncMode.INCREMENTAL, 0).status)
        a.nowMs += 3_600_000L

        val before = a.stored()
        val files = digestOf(root)
        val presentBefore = before.presentSongKeys(acct.value)

        for (budget in listOf(0, 3, -7, TEST_SEND_BUDGET)) {
            for (mode in listOf(SyncMode.INCREMENTAL, SyncMode.FULL_REPLAY)) {
                a.nowMs += 3_600_000L
                a.coordinator.runOnce(acct, mode, budget)
            }
        }

        val after = a.stored()
        assertEquals(0, channel.sendCalls)
        assertEquals("saved songs", presentBefore, after.presentSongKeys(acct.value))
        assertEquals("outbox: ids, order, status, attempts", before.outbox, after.outbox)
        assertEquals("legacy list", before.legacy, after.legacy)
        assertEquals("tags and tombstones", before.orSet, after.orSet)
        assertEquals("the owner's V1 file for older APKs and V2 file are the very same bytes", files, digestOf(root))
        assertEquals(5, after.outbox.count { it.status == OutboxStatus.RETRY_REQUIRED })
        assertEquals(62, after.outbox.size)
    }
}
