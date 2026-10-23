package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.PlaylistSyncStatusPolicy.Kind
import com.bsdung.musicassistantcar.data.telegram.PlaylistSyncStatusPolicy.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** E96 P4-02: every distinct truth the Playlist status line may tell, and the cases where it must NOT say "Đã đồng bộ". */
class PlaylistSyncStatusPolicyTest {
    private val acct = 1001L

    private fun facts(
        saved: Int = 3, pending: Int = 0, retry: Int = 0, legacy: Int = 0, bound: Boolean = true, ops: Long = 5L,
        truncated: Boolean = false, stale: Boolean = false, warning: PlaylistWarning? = null,
    ) = PlaylistStatusFacts(acct, saved, pending, retry, legacy, bound, ops, truncated, stale, warning, null)

    private val optedIn = SyncConsent(enabled = true, mayCreateChannel = true, mayTransmitInitial = true, acceptedWarningVersion = SyncConsent.WARNING_VERSION)
    private fun ctx(
        build: Boolean = true, consent: SyncConsent = optedIn, running: Boolean = false, run: LastSyncRun? = null,
    ) = SyncContext(build, consent, running, run)

    private fun run(
        status: SyncStatus? = SyncStatus.OK, recovery: RecoveryReason? = null, denied: SyncDenied? = null,
        retryMs: Long = 0, left: Int = 0, ops: Long = 5L, account: Long = acct,
    ) = LastSyncRun(account, status, recovery, denied, retryMs, left, ops)

    private fun eval(f: PlaylistStatusFacts? = facts(), c: SyncContext = ctx(), who: Long = acct) =
        PlaylistSyncStatusPolicy.evaluate(who, f, c)

    @Test fun s01_withTheBuildSwitchOffTheListIsSavedOnTheDeviceAndSaysSyncIsNotOn() {
        val line = eval(c = ctx(build = false, consent = optedIn))
        assertEquals(Kind.LOCAL_SYNC_OFF, line.kind)
        assertEquals("Đã lưu trên máy · Chưa bật đồng bộ", line.text)
        assertTrue(line.detail.contains(PlaylistSyncStatusPolicy.BUILD_OFF_NOTICE))
    }

    @Test fun s02_withoutConsentTheLineIsLocalOnlyEvenIfOperationsAreWaiting() {
        val line = eval(facts(pending = 4), ctx(consent = SyncConsent()))
        assertEquals(Kind.LOCAL_SYNC_OFF, line.kind)
        assertFalse("'Chờ đồng bộ' is only for an opted-in sync", line.text.contains("Chờ đồng bộ"))
    }

    @Test fun s03_aWarningThatWasNeverAcceptedKeepsItOff() {
        val line = eval(c = ctx(consent = SyncConsent(enabled = true, acceptedWarningVersion = 0)))
        assertEquals(Kind.LOCAL_SYNC_OFF, line.kind)
        assertTrue(line.detail.contains("cảnh báo"))
    }

    @Test fun s04_waitingOperationsWithSyncOnReadWaitingToSync() {
        val line = eval(facts(pending = 3), ctx(run = null))
        assertEquals(Kind.LOCAL_PENDING, line.kind)
        assertEquals("Đã lưu trên máy · Chờ đồng bộ", line.text)
        assertTrue("the count is the verified outbox count", line.detail.contains("3 thay đổi"))
    }

    @Test fun s05_nothingEverSyncedIsNotSynced() {
        val line = eval(facts(pending = 0), ctx(run = null))
        assertEquals(Kind.NEVER_SYNCED, line.kind)
        assertNotEquals("Đã đồng bộ", line.text)
    }

    @Test fun s06_aCompleteReportWithNothingWaitingAndNoNewerChangeIsSynced() {
        val line = eval(facts(pending = 0, ops = 5), ctx(run = run(SyncStatus.OK, ops = 5)))
        assertEquals(Kind.SYNCED, line.kind)
        assertEquals("Đã đồng bộ", line.text)
        assertEquals(Severity.OK, line.severity)
        assertTrue("only metadata is promised", line.detail.contains("không tải hay phát nhạc"))
    }

    @Test fun s07_aNewLocalChangeAfterTheLastOkReportIsNeverShownAsSynced() {
        val line = eval(facts(pending = 1, ops = 6), ctx(run = run(SyncStatus.OK, ops = 5)))
        assertEquals(Kind.LOCAL_PENDING, line.kind)
        // Even if the outbox looks empty, the counter proves a change newer than the report.
        val stale = eval(facts(pending = 0, ops = 6), ctx(run = run(SyncStatus.OK, ops = 5)))
        assertNotEquals(Kind.SYNCED, stale.kind)
    }

    @Test fun s08_aReportWithOperationsStillLeftOrRetryRequiredIsNotSynced() {
        assertNotEquals(Kind.SYNCED, eval(facts(), ctx(run = run(SyncStatus.OK, left = 2))).kind)
        assertNotEquals(Kind.SYNCED, eval(facts(retry = 1), ctx(run = run(SyncStatus.OK))).kind)
    }

    @Test fun s09_readingTheHistoryIsDistinctFromSendingTheOutbox() {
        val catching = eval(facts(), ctx(run = run(SyncStatus.CATCH_UP_INCOMPLETE)))
        val sending = eval(facts(pending = 7), ctx(run = run(SyncStatus.OUTBOX_INCOMPLETE, left = 7)))
        assertEquals(Kind.CATCHING_UP, catching.kind)
        assertEquals(Kind.SENDING_OUTBOX, sending.kind)
        assertTrue(sending.text.contains("7"))
        assertNotEquals(catching.text, sending.text)
        assertNotEquals(Kind.SYNCED, catching.kind)
    }

    @Test fun s10_whileARunIsInProgressTheLineSaysSo() {
        assertEquals(Kind.SYNCING, eval(c = ctx(running = true)).kind)
        assertEquals(Kind.CATCHING_UP, eval(c = ctx(running = true, run = run(SyncStatus.CATCH_UP_INCOMPLETE))).kind)
        assertEquals(Kind.SENDING_OUTBOX, eval(facts(pending = 2), ctx(running = true, run = run(SyncStatus.OUTBOX_INCOMPLETE, left = 2))).kind)
    }

    @Test fun s11_backoffSaysTryLaterAndShowsNoTimeWhenItIsUnknown() {
        val unknown = eval(c = ctx(run = run(SyncStatus.BACKOFF, retryMs = 0)))
        assertEquals(Kind.OFFLINE_BACKOFF, unknown.kind)
        assertTrue(unknown.text.contains("Thử lại sau"))
        assertFalse("no invented time", unknown.detail.contains("Khoảng"))
        val known = eval(c = ctx(run = run(SyncStatus.BACKOFF_ACTIVE, retryMs = 35_000)))
        assertTrue(known.detail.contains("Khoảng 35 giây"))
        val minutes = eval(c = ctx(run = run(SyncStatus.BACKOFF, retryMs = 300_000)))
        assertTrue(minutes.detail.contains("5 phút"))
    }

    @Test fun s12_noChannelAndMissingConsentToSendAreDistinct() {
        val none = eval(facts(bound = false), ctx())
        assertEquals(Kind.NO_CHANNEL, none.kind)
        val needsSend = eval(facts(pending = 2), ctx(consent = optedIn.copy(mayTransmitInitial = false)))
        assertEquals(Kind.NEEDS_TRANSMIT_CONSENT, needsSend.kind)
        val needsCreate = eval(facts(bound = false), ctx(consent = optedIn.copy(mayCreateChannel = false)))
        assertTrue(needsCreate.detail.contains("cho phép tạo kênh"))
    }

    @Test fun s13_everyRecoveryReasonHasItsOwnPlainMessageAndNeverSuggestsDeletingAnything() {
        val kinds = RecoveryReason.entries.associateWith { r -> eval(c = ctx(run = run(SyncStatus.RECOVERY_REQUIRED, recovery = r))) }
        assertEquals(Kind.RECOVERY_CHANNEL_MISSING, kinds.getValue(RecoveryReason.CHANNEL_MISSING).kind)
        assertEquals(Kind.RECOVERY_ACCESS, kinds.getValue(RecoveryReason.UNAUTHORIZED).kind)
        assertEquals(Kind.RECOVERY_ACCESS, kinds.getValue(RecoveryReason.SESSION_REVOKED).kind)
        assertEquals(Kind.RECOVERY_WRONG_ACCOUNT, kinds.getValue(RecoveryReason.WRONG_ACCOUNT).kind)
        assertEquals(Kind.RECOVERY_FUTURE_PROTOCOL, kinds.getValue(RecoveryReason.FUTURE_PROTOCOL).kind)
        for (r in listOf(RecoveryReason.CORRUPT_MESSAGE, RecoveryReason.OP_TOO_LARGE, RecoveryReason.TRANSPORT_CONTRACT)) {
            assertEquals(Kind.RECOVERY_HISTORY, kinds.getValue(r).kind)
        }
        for ((reason, line) in kinds) {
            assertEquals(reason.name, Severity.ERROR, line.severity)
            assertFalse(reason.name, line.detail.contains("xóa dữ liệu"))
        }
        assertTrue(kinds.getValue(RecoveryReason.CHANNEL_MISSING).detail.contains("không tự tạo kênh mới"))
    }

    @Test fun s14_gatedDenialsMapToTheRightLine() {
        fun denied(reason: SyncDenied) = eval(c = ctx(run = run(status = null, denied = reason)))
        assertEquals(Kind.NEEDS_LOGIN, denied(SyncDenied.NOT_AUTHORIZED).kind)
        assertEquals(Kind.NEEDS_TRANSMIT_CONSENT, denied(SyncDenied.TRANSMIT_NOT_ALLOWED).kind)
        assertEquals(Kind.NO_CHANNEL, denied(SyncDenied.CREATE_NOT_ALLOWED).kind)
        assertEquals(Kind.BUSY, denied(SyncDenied.BUSY).kind)
        assertEquals(Kind.BUSY, denied(SyncDenied.SINK_BUSY).kind)
        assertEquals(Kind.LOCAL_SYNC_OFF, denied(SyncDenied.BUILD_OFF).kind)
    }

    @Test fun s15_otherAccountsNumbersAndReportsAreNeverShown() {
        // Facts of account 2002 while 1001 is signed in: a neutral "loading" line without any count.
        val other = PlaylistStatusFacts(2002L, 99, 9, 0, 0, true, 1L, false, false, null, null)
        val line = PlaylistSyncStatusPolicy.evaluate(acct, other, ctx())
        assertEquals(Kind.LOADING, line.kind)
        assertFalse(line.text.contains("99") || line.detail.contains("99") || line.detail.contains("9 "))
        // A sync report of another account is ignored: this account was never synced.
        val foreign = eval(facts(pending = 0, ops = 5), ctx(run = run(SyncStatus.OK, ops = 5, account = 2002L)))
        assertEquals(Kind.NEVER_SYNCED, foreign.kind)
        assertEquals(Kind.LOADING, PlaylistSyncStatusPolicy.evaluate(acct, null, ctx()).kind)
    }

    @Test fun s16_anUnreadablePlaylistIsAnErrorThatPromisesNotToOverwrite() {
        for (reason in PlaylistUnreadableReason.entries) {
            val line = PlaylistSyncStatusPolicy.evaluate(acct, PlaylistStatusFacts.unreadable(acct, reason), ctx())
            assertEquals(reason.name, Kind.UNREADABLE, line.kind)
            assertEquals(Severity.ERROR, line.severity)
            assertTrue(reason.name, line.detail.contains("ghi đè"))
        }
    }

    @Test fun s17_aV1ImportThatHadToWaitIsAWarningAndKeepsTheListVisible() {
        for (w in PlaylistWarning.entries) {
            val line = eval(facts(warning = w))
            assertEquals(Kind.IMPORT_DEFERRED, line.kind)
            assertEquals(Severity.WARN, line.severity)
            assertTrue(line.detail.contains("Không bài nào bị mất"))
        }
    }

    @Test fun s18_unresolvedLegacySongsAndATruncatedV1CopyAreStatedAsNotes() {
        val line = eval(facts(saved = 450, legacy = 3, truncated = true, stale = true, retry = 2), ctx(consent = SyncConsent()))
        val full = line.fullDetail()
        assertTrue(full.contains("3 bài chưa xác định nguồn Telegram"))
        assertTrue("nothing past 300 is hidden: the full count stays", full.contains("450 bài đã lưu"))
        assertTrue(full.contains("${TelegramPlaylistPolicy.MAX_IDS} bài mới nhất"))
        assertTrue(full.contains("chưa cập nhật kịp"))
        assertTrue(full.contains("2 thay đổi cần gửi lại"))
    }

    @Test fun s19_aLocalSongIsNeverCloudSyncedAndAnUnsavedSongSaysSo() {
        val synced = eval(facts(pending = 0, ops = 5), ctx(run = run(SyncStatus.OK, ops = 5)))
        assertEquals(Kind.SYNCED, synced.kind)
        val local = PlaylistSyncStatusPolicy.songLine(MeloSavePolicy.Availability.LOCAL_ONLY, saved = false, overall = synced)
        assertEquals(Kind.LOCAL_SONG, local.kind)
        val notSaved = PlaylistSyncStatusPolicy.songLine(MeloSavePolicy.Availability.READY, saved = false, overall = synced)
        assertEquals(Kind.NOT_SAVED, notSaved.kind)
        assertEquals("Chưa lưu", notSaved.text)
        val legacy = PlaylistSyncStatusPolicy.songLine(MeloSavePolicy.Availability.LEGACY_ONLY, saved = true, overall = synced)
        assertEquals(Kind.SAVED_DEVICE_ONLY, legacy.kind)
        assertEquals(Kind.SYNCED, PlaylistSyncStatusPolicy.songLine(MeloSavePolicy.Availability.READY, saved = true, overall = synced).kind)
    }

    @Test fun s20_theProductWordingNeverPromisesRemotePlaybackOrDownload() {
        val all = Kind.entries.size
        assertTrue(all > 20)
        val sample = listOf(
            eval(c = ctx(build = false)), eval(facts(pending = 1)), eval(c = ctx(run = run(SyncStatus.OK))),
            eval(c = ctx(running = true)), eval(c = ctx(run = run(SyncStatus.BACKOFF))),
        )
        for (line in sample) {
            val text = (line.text + " " + line.detail).lowercase()
            assertFalse(text, text.contains("phát trên máy khác") || text.contains("tải về máy khác"))
        }
        assertTrue(PlaylistSyncStatusPolicy.METADATA_ONLY.contains("không tải hay phát nhạc sang máy khác"))
    }

    @Test fun s21_libraryScreensShowThePlaylistStateOnlyOnThePlaylistTab() {
        val line = eval(c = ctx(build = false))
        assertEquals("☆ Đã lưu trên máy · Chưa bật đồng bộ", PlaylistSyncStatusPolicy.libraryLine(line, "☁ 10 bài · Đã đồng bộ"))
        assertEquals("☁ 10 bài · Đã đồng bộ", PlaylistSyncStatusPolicy.libraryLine(null, "☁ 10 bài · Đã đồng bộ"))
    }

    @Test fun s22_lastRunIsBuiltFromTheGatedResultAndRecordsTheCounterAtTheEnd() {
        val done = LastSyncRun.of(acct, Gated.Done(SyncReport(SyncStatus.BACKOFF, retryAfterMs = 5_000L, pendingLeft = 2)), opCounterNow = 9L)
        assertEquals(SyncStatus.BACKOFF, done.status)
        assertEquals(9L, done.opCounterAtRun)
        assertEquals(5_000L, done.retryAfterMs)
        val denied = LastSyncRun.of(acct, Gated.Denied(SyncDenied.BUILD_OFF), opCounterNow = 9L)
        assertEquals(SyncDenied.BUILD_OFF, denied.denied)
        assertEquals(null, denied.status)
    }
}
