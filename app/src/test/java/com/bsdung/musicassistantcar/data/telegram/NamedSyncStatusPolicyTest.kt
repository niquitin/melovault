package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nhiều Playlist có tên, S9: the status line and the "Thay đổi chờ gửi" rows. Pure text rules. */
class NamedSyncStatusPolicyTest {
    private val account = 1001L
    private fun tg(n: Long) = PlaylistItemId.Telegram(-1001234L, n)

    private class Made(var s: NamedPlaylistState) {
        fun run(m: NamedMutation): NamedMutation { s = m.state; return m }
    }

    private fun everyKind(): Pair<NamedPlaylistState, ListId> {
        val made = Made(NamedPlaylistState.empty(account, "dev-a"))
        val id = checkNotNull(made.run(made.s.createList("Nhạc đi xe")).listId)
        made.run(made.s.addTelegram(id, tg(1)))
        made.run(made.s.addTelegram(id, tg(2)))
        made.run(made.s.moveItem(id, tg(2), 0))
        made.run(made.s.removeTelegram(id, tg(1)))
        made.run(made.s.renameList(id, "Đi xa"))
        made.run(made.s.deleteList(id))
        return made.s to id
    }

    // ---- the rows ---------------------------------------------------------------------------------

    @Test fun everyKindOfChangeHasItsOwnRowWithNameAndSongAndNoIds() {
        val (state, id) = everyKind()
        val names = NamedPendingReviewPolicy.namesOf(state)
        assertEquals("Đi xa", names[id])
        val rows = NamedPendingReviewPolicy.rows(state.outbox, names) { if (it.messageId == 1L) "Bài Một" else null }
        assertEquals(
            listOf(
                "Tạo Playlist ‘Đi xa’",
                "Thêm vào ‘Đi xa’: Bài Một",
                "Thêm vào ‘Đi xa’: ${NamedPendingReviewPolicy.UNKNOWN_SONG}",
                "Đổi vị trí trong ‘Đi xa’: ${NamedPendingReviewPolicy.UNKNOWN_SONG}",
                "Bỏ khỏi ‘Đi xa’: Bài Một",
                "Đổi tên Playlist thành ‘Đi xa’",
                "Xóa Playlist ‘Đi xa’",
            ),
            rows
        )
        for (row in rows) {
            assertFalse(row, row.contains("dev-a"))
            assertFalse(row, row.contains('#'))
            assertFalse(row, row.contains("tg:"))
            assertFalse(row, row.contains("1001234"))
            assertFalse(row, row.contains("local"))
        }
    }

    @Test fun anUncertainDeliveryIsSaidSoAndAcknowledgedEntriesAreLeftOut() {
        val (state, _) = everyKind()
        val first = state.outbox.first().op.opId
        val second = state.outbox[1].op.opId
        val marked = state.markRetryRequired(first).acknowledge(second)
        val rows = NamedPendingReviewPolicy.rows(marked.outbox, NamedPendingReviewPolicy.namesOf(state)) { null }
        assertEquals(state.outbox.size - 1, rows.size)
        assertEquals("Tạo Playlist ‘Đi xa’\n${NamedPendingReviewPolicy.UNCERTAIN_NOTE}", rows.first())
        assertEquals("only the flagged one carries the note", 1, rows.count { it.contains(NamedPendingReviewPolicy.UNCERTAIN_NOTE) })
    }

    @Test fun anUnknownPlaylistNameAndANoNameSongHaveHonestFallbacks() {
        val (state, id) = everyKind()
        val rows = NamedPendingReviewPolicy.rows(state.outbox.drop(1).take(1), emptyMap()) { " " }
        assertEquals(listOf("Thêm vào ‘${NamedPendingReviewPolicy.UNKNOWN_LIST}’: ${NamedPendingReviewPolicy.UNKNOWN_SONG}"), rows)
        assertEquals(emptyList<String>(), NamedPendingReviewPolicy.rows(emptyList(), mapOf(id to "x")) { "y" })
    }

    @Test fun songsOfListsEachWaitingSongOnceAndNeverALocalOne() {
        val made = Made(NamedPlaylistState.empty(account, "dev-a"))
        val id = checkNotNull(made.run(made.s.createList("L")).listId)
        made.run(made.s.addTelegram(id, tg(1)))
        made.run(made.s.addLocal(id, 9L, LocalFingerprint.of("x", 1, 1)))
        made.run(made.s.moveItem(id, tg(1), 0))
        made.run(made.s.addTelegram(id, tg(2)))
        assertEquals(listOf(tg(1), tg(2)), NamedPendingReviewPolicy.songsOf(made.s.outbox))
        assertTrue(made.s.outbox.none { it.op.item is PlaylistItemId.Local })
    }

    // ---- the status line --------------------------------------------------------------------------

    private fun facts(pending: Int = 0, retry: Int = 0) = PlaylistStatusFacts(
        accountId = account, savedCount = 3, pendingCount = pending, retryRequiredCount = retry, legacyCount = 0,
        channelBound = true, opCounter = 5L, projectionTruncated = false, projectionStale = false, importWarning = null, unreadable = null,
    )

    private val consent = SyncConsent(enabled = true, mayTransmitInitial = true, acceptedWarningVersion = SyncConsent.WARNING_VERSION)
    private fun okRun(opCounter: Long = 5L) = LastSyncRun(account, SyncStatus.OK, opCounterAtRun = opCounter)
    private fun context(run: LastSyncRun? = okRun()) = SyncContext(true, consent, false, run)
    private fun line(f: PlaylistStatusFacts, c: SyncContext = context()) = PlaylistSyncStatusPolicy.evaluate(account, f, c)

    @Test fun withNamedCountsOnlyWhatStillWaitsAndNullAddsNothing() {
        val (state, _) = everyKind()
        val acknowledged = state.acknowledge(state.outbox[0].op.opId).acknowledge(state.outbox[1].op.opId)
        val withRetry = acknowledged.markRetryRequired(state.outbox[2].op.opId)
        val f = facts(pending = 2, retry = 1).withNamed(withRetry)
        assertEquals(state.outbox.size - 2, f.namedPendingCount)
        assertEquals(1, f.namedRetryRequiredCount)
        assertEquals(2 + state.outbox.size - 2, f.allPendingCount)
        assertEquals(2, f.allRetryRequiredCount)
        assertEquals(facts(pending = 2, retry = 1), facts(pending = 2, retry = 1).withNamed(null))
        assertEquals(0, facts().withNamed(null).allPendingCount)
        assertEquals("the old constructor calls keep working", 0, facts().namedPendingCount)
    }

    @Test fun withoutNamedPlaylistsTheLineIsExactlyTheOldOne() {
        for (f in listOf(facts(), facts(pending = 2), facts(retry = 1))) {
            for (c in listOf(context(), context(null), context(okRun(4L)), SyncContext(true, SyncConsent()))) {
                assertEquals(line(f, c), line(f.withNamed(null), c))
            }
        }
        assertEquals(PlaylistSyncStatusPolicy.Kind.SYNCED, line(facts()).kind)
    }

    @Test fun anOkRunIsNotSyncedWhileNamedChangesWait() {
        val (state, _) = everyKind()
        val waiting = facts().withNamed(state)
        val l = line(waiting)
        assertEquals(PlaylistSyncStatusPolicy.Kind.LOCAL_PENDING, l.kind)
        assertTrue(l.detail, l.detail.contains("Có ${state.outbox.size} thay đổi chờ gửi"))
        assertTrue(l.notes.toString(), l.notes.contains("Trong đó ${state.outbox.size} thay đổi của các Playlist có tên."))
        assertNotEquals(PlaylistSyncStatusPolicy.Kind.SYNCED, l.kind)
        val done = state.outbox.fold(state) { s, e -> s.acknowledge(e.op.opId) }
        assertEquals(PlaylistSyncStatusPolicy.Kind.SYNCED, line(facts().withNamed(done)).kind)
    }

    @Test fun namedChangesAddToTheCountsOfEveryWaitingState() {
        val (state, _) = everyKind()
        val n = state.outbox.size
        val f = facts(pending = 2).withNamed(state)
        assertTrue(line(f, context(null)).detail.contains("Có ${2 + n} thay đổi chờ gửi"))
        val noTransmit = SyncContext(true, SyncConsent(enabled = true, acceptedWarningVersion = SyncConsent.WARNING_VERSION))
        val consentLine = line(f, noTransmit)
        assertEquals(PlaylistSyncStatusPolicy.Kind.NEEDS_TRANSMIT_CONSENT, consentLine.kind)
        assertTrue(consentLine.detail, consentLine.detail.contains("Có ${2 + n} thay đổi chờ gửi"))
        val onlyNamed = line(facts().withNamed(state), noTransmit)
        assertEquals("a playlist change alone also asks for the consent to send", PlaylistSyncStatusPolicy.Kind.NEEDS_TRANSMIT_CONSENT, onlyNamed.kind)
        val sending = line(f, context(LastSyncRun(account, SyncStatus.OUTBOX_INCOMPLETE, pendingLeft = 2, opCounterAtRun = 5L)))
        assertEquals(PlaylistSyncStatusPolicy.Kind.SENDING_OUTBOX, sending.kind)
        assertTrue(sending.text, sending.text.contains("còn ${2 + n}"))
        val limit = line(f, context(LastSyncRun(account, SyncStatus.SEND_LIMIT_REACHED, pendingLeft = 2, opCounterAtRun = 5L)))
        assertEquals(PlaylistSyncStatusPolicy.Kind.SEND_LIMIT, limit.kind)
        assertTrue(limit.detail, limit.detail.contains("Còn ${2 + n} thay đổi"))
    }

    @Test fun anUnconfirmedNamedDeliveryIsNeverCalledSyncedAndIsExplained() {
        val (state, _) = everyKind()
        val doubtful = state.copy(outbox = state.outbox.take(1).map { it.copy(status = OutboxStatus.RETRY_REQUIRED, attempts = 1) })
        val f = facts().withNamed(doubtful)
        val l = line(f)
        assertNotEquals(PlaylistSyncStatusPolicy.Kind.SYNCED, l.kind)
        assertTrue(l.notes.toString(), l.notes.any { it.contains("1 thay đổi của Playlist có tên đã gửi nhưng chưa rõ đã tới kênh chưa") && it.contains("không tự gửi lại") })
    }

    @Test fun otherAccountsFactsAreStillNeverShownWhateverIsNamed() {
        val (state, _) = everyKind()
        val l = PlaylistSyncStatusPolicy.evaluate(2002L, facts().withNamed(state), context())
        assertEquals(PlaylistSyncStatusPolicy.Kind.LOADING, l.kind)
        assertFalse(l.detail.contains("thay đổi"))
    }
}
