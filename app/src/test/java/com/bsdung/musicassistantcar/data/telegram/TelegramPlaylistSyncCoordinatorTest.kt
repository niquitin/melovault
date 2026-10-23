package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistSession.ToggleOutcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Random

/**
 * E96 P2-04: two or three independent devices (own account session, stores, outbox, counters and clock)
 * against one in-memory channel that can fail in every way the sync core must survive.
 * Nothing here touches Telegram, a network, a thread of the app or a real device.
 */
class TelegramPlaylistSyncCoordinatorTest {
    @get:Rule val folder = TemporaryFolder()

    private val acct = TelegramUserId(1001L)
    private lateinit var channel: FakeTelegramChannel

    @Before fun setUp() {
        channel = FakeTelegramChannel()
    }

    private fun saved(n: Int): Long = n * 4_194_304L

    private fun device(
        name: String, account: TelegramUserId = acct, config: SyncConfig = SyncConfig(),
        index: PlaylistIndex? = null, bind: Boolean = true, root: java.io.File = folder.newFolder(name),
    ) = FakeSyncDevice(name, root, account, channel, seed = name.sumOf { it.code } % 90 + 1, config = config, index = index, bindOnFirstBoot = bind)

    /** Saves/unsaves a Saved-Messages song (its UI key is the message id itself). */
    private fun FakeSyncDevice.song(n: Int) = toggle(account.value, saved(n))

    private fun FakeSyncDevice.songElsewhere(chat: Long, message: Long) = toggle(chat, message)

    private fun FakeSyncDevice.waitOut(report: SyncReport) {
        nowMs += report.retryAfterMs
    }

    private fun assertConverged(vararg devices: FakeSyncDevice) {
        val first = devices.first()
        for (other in devices.drop(1)) {
            assertEquals("${first.name} vs ${other.name}: tags and tombstones", first.crdt(), other.crdt())
            assertEquals("${first.name} vs ${other.name}: shown list", first.present(), other.present())
        }
    }

    private fun item(n: Int) = PlaylistItemId.Telegram(acct.value, saved(n))

    // ---- 1-6: the basic story ----------------------------------------------------------------

    @Test fun t01_aSavesASongAndBSeesIt() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1)
        assertEquals(SyncStatus.OK, a.sync().status)
        val r = b.sync()
        assertEquals(SyncStatus.OK, r.status)
        assertEquals(listOf(saved(1)), b.present())
        assertEquals(1, r.opsApplied)
        assertConverged(a, b)
        assertEquals("A's note is acknowledged and gone", emptyList<String>(), a.pendingIds())
    }

    @Test fun t02_bRemovesTheSongAndAHasItGone() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); a.sync(); b.sync()
        assertTrue(b.song(1) is ToggleOutcome.Removed)
        b.sync(); a.sync()
        assertEquals(emptyList<Long>(), a.present())
        assertEquals(emptyList<Long>(), b.present())
        assertConverged(a, b)
        assertTrue("the tombstone is kept", a.stored().orSet.wasRemoved(item(1)))
    }

    @Test fun t03_offlineAddsOnBothDevicesBothSurvive() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); b.song(2) // both offline
        a.sync(); b.sync(); a.sync()
        assertEquals(setOf(saved(1), saved(2)), a.present().toSet())
        assertConverged(a, b)
    }

    @Test fun t04_theSameSongOnTwoDevicesIsOneRow() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); b.song(1)
        a.sync(); b.sync(); a.sync()
        assertEquals(listOf(saved(1)), a.present())
        assertEquals(listOf(saved(1)), b.present())
        assertEquals("two tags, one row", 2, a.stored().orSet.liveDots(item(1)).size)
        assertConverged(a, b)
    }

    @Test fun t05_anAddWinsOverARemoveThatNeverSawIt() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); a.sync(); b.sync()
        b.song(1) // B removes (old tag), offline
        a.song(1); a.song(1) // A removes, then saves again: a NEW tag
        a.sync()
        b.sync(); a.sync()
        assertEquals(listOf(saved(1)), a.present())
        assertEquals(listOf(saved(1)), b.present())
        assertConverged(a, b)
        assertEquals("only the new tag is alive", 1, b.stored().orSet.liveDots(item(1)).size)
    }

    @Test fun t06_aRemoveThatSawEveryAddMakesTheSongDisappearEverywhere() {
        val a = device("dev-a"); val b = device("dev-b"); val c = device("dev-c")
        a.song(1); a.sync(); b.sync(); c.sync()
        b.song(1); b.sync()
        a.sync(); c.sync()
        assertEquals(emptyList<Long>(), a.present() + b.present() + c.present())
        assertConverged(a, b, c)
        val late = device("dev-d"); late.sync() // joins afterwards
        assertEquals(emptyList<Long>(), late.present())
    }

    // ---- 7-9: order, repeats, lost answers ----------------------------------------------------

    @Test fun t07_aRemoveThatArrivesBeforeItsAddStillHolds() {
        val add = addOp("dev-x", 1, acct.value, saved(1))
        val remove = removeOp("dev-x", 2, acct.value, saved(1), setOf(Dot("dev-x", 1)))
        channel.inject(envelopeText(acct.value, "dev-x", remove)) // the server order is remove, then add
        channel.inject(envelopeText(acct.value, "dev-x", add))
        val b = device("dev-b")
        assertEquals(SyncStatus.OK, b.sync().status)
        assertEquals(emptyList<Long>(), b.present())
        assertTrue(b.stored().orSet.wasRemoved(item(1)))
        assertEquals(setOf(Dot("dev-x", 1)), b.stored().orSet.allAdds()[item(1)]!!.keys)
    }

    @Test fun t08_theSameMessagesReplayedManyTimesChangeNothing() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); a.song(2); a.sync(); a.song(1); a.sync()
        channel.duplicateInPage = true
        b.sync()
        val settled = b.stored()
        repeat(5) {
            assertEquals(SyncStatus.OK, b.sync(SyncMode.FULL_REPLAY).status)
        }
        assertEquals(settled.orSet, b.stored().orSet)
        assertEquals(settled.presentSongKeys(acct.value), b.stored().presentSongKeys(acct.value))
        assertEquals(listOf(saved(2)), b.present())
        // and the channel itself repeated every message in every page
        assertConverged(a.also { it.sync() }, b)
    }

    @Test fun t09_aLostAnswerAfterTheServerAcceptedIsNotAnAckAndTheRetryIsTheSameMessage() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1)
        val opId = a.stored().outbox.single().op.opId
        channel.sendFaults.add(FakeTelegramChannel.SendFault.LOST_ACK)
        val r1 = a.sync()
        assertEquals(SyncStatus.BACKOFF, r1.status)
        assertEquals(1, channel.count())
        assertEquals("not acknowledged by a send whose answer was lost", OutboxStatus.RETRY_REQUIRED, a.stored().outbox.single().status)
        assertEquals(opId, a.stored().outbox.single().op.opId)

        a.waitOut(r1)
        val r2 = a.sync() // fetch first: finds its own operation id -> acknowledged without a second send
        assertEquals(SyncStatus.OK, r2.status)
        assertEquals(1, r2.opsAcknowledged)
        assertEquals("no second message", 1, channel.sendCalls)
        assertEquals(emptyList<String>(), a.pendingIds())
        b.sync()
        assertEquals(listOf(saved(1)), b.present())
    }

    @Test fun t09b_ifTheStoredCopyCannotBeSeenYetTheRetryIsAnIdenticalDuplicateAndStillOneSong() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1)
        channel.lostAckHiddenFetches = 2
        channel.sendFaults.add(FakeTelegramChannel.SendFault.LOST_ACK)
        a.waitOut(a.sync())
        val r2 = a.sync() // the stored copy is still invisible: sent again with the same operation id
        assertEquals(SyncStatus.OK, r2.status)
        assertEquals(2, channel.count())
        assertEquals("the two messages are the very same text", channel.texts()[0], channel.texts()[1])
        b.sync()
        assertEquals(listOf(saved(1)), b.present())
        assertEquals("one tag, not two", 1, b.stored().orSet.liveDots(item(1)).size)
        assertEquals(emptyList<String>(), a.pendingIds())
    }

    // ---- 10-14: crash at every boundary ------------------------------------------------------

    @Test fun t10_powerLostBeforeTheLocalCommitLeavesNothingBehind() {
        val a = device("dev-a")
        val before = a.storedFile().readBytes()
        a.v2Io.failNextWrite()
        assertEquals(ToggleOutcome.Failed, a.song(1))
        a.v2Io.heal()
        a.restart()
        assertEquals(emptyList<Long>(), a.present())
        assertArrayEquals(before, a.storedFile().readBytes())
        a.sync()
        assertEquals("nothing to send", 0, channel.count())
    }

    @Test fun t11_powerLostAfterTheLocalCommitBeforeSendingSendsOnTheNextStart() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1)
        a.restart() // killed before any sync
        assertEquals(1, a.pendingIds().size)
        a.sync(); b.sync()
        assertEquals(listOf(saved(1)), b.present())
        assertConverged(a, b)
    }

    @Test fun t12_powerLostAfterSendingBeforeTheAckDoesNotSendAgain() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1)
        a.hooks.afterStoreBeforeReply = { throw SimulatedCrash() }
        try {
            a.sync()
            fail("the process should have died")
        } catch (_: SimulatedCrash) {
        }
        a.hooks.afterStoreBeforeReply = null
        a.restart()
        assertEquals("still waiting: nothing was acknowledged", 1, a.pendingIds().size)
        assertEquals(1, channel.count())
        a.sync()
        assertEquals("the history showed it: acknowledged, not sent twice", 1, channel.sendCalls)
        assertEquals(emptyList<String>(), a.pendingIds())
        b.sync()
        assertEquals(listOf(saved(1)), b.present())
    }

    @Test fun t13_powerLostWhileApplyingMovesNeitherTheDataNorTheCursor() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); a.sync()
        val before = b.storedFile().readBytes()
        b.v2Io.failNextWrite() // the one write that holds operations + acknowledgements + cursor
        val r = b.sync()
        assertEquals(SyncStatus.SAVE_FAILED, r.status)
        assertEquals(0L, b.stored().checkpoint.lastAppliedServerId)
        assertEquals(emptyList<Long>(), b.present())
        assertArrayEquals(before, b.storedFile().readBytes())
        b.v2Io.heal()
        b.restart()
        assertEquals(SyncStatus.OK, b.sync().status)
        assertEquals(listOf(saved(1)), b.present())
        assertConverged(a, b)
    }

    @Test fun t14_afterTheCursorIsCommittedARestartContinuesFromIt() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); a.song(2); a.sync()
        b.sync()
        val cursor = b.stored().checkpoint.lastAppliedServerId
        assertEquals(channel.maxId(), cursor)
        b.restart()
        assertEquals(SyncStatus.OK, b.sync().status)
        assertEquals("asked only for what is newer than the cursor", cursor, channel.lastAfterId)
        assertEquals(setOf(saved(1), saved(2)), b.present().toSet())
    }

    // ---- 15-16: history shapes ---------------------------------------------------------------

    @Test fun t15_moreThan250MessagesArriveInPagesWithoutLoss() {
        for (n in 1..270) channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", n.toLong(), acct.value, saved(n))))
        val b = device("dev-b")
        val r = b.sync()
        assertEquals(SyncStatus.OK, r.status)
        assertEquals(3, r.pagesFetched)
        assertEquals(270, r.messagesRead)
        assertEquals(270, b.present().size)
        assertEquals(channel.maxId(), b.stored().checkpoint.lastAppliedServerId)
        assertEquals((1..270).map { saved(it) }.toSet(), b.present().toSet())
    }

    @Test fun t15b_aRunLimitStopsEarlyAndTheNextRunContinuesWithoutLoss() {
        for (n in 1..45) channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", n.toLong(), acct.value, saved(n))))
        val b = device("dev-b", config = SyncConfig(pageSize = 10, maxPagesPerRun = 2))
        assertEquals(20, b.sync().messagesRead)
        assertEquals(20, b.present().size)
        assertEquals(20, b.sync().messagesRead)
        assertEquals(5, b.sync().messagesRead)
        assertEquals(45, b.present().size)
    }

    @Test fun t16_shuffledSparseFragmentedAndRepeatedHistoryStillGivesTheRightSet() {
        for (n in 1..40) channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", n.toLong(), acct.value, saved(n))))
        channel.inject(envelopeText(acct.value, "dev-y", removeOp("dev-y", 1, acct.value, saved(5), setOf(Dot("dev-x", 5)))))
        channel.shufflePages = true
        channel.duplicateInPage = true
        channel.maxPageSize = 7
        val b = device("dev-b")
        assertEquals(SyncStatus.OK, b.sync().status)
        assertEquals((1..40).filter { it != 5 }.map { saved(it) }.toSet(), b.present().toSet())
        assertEquals(channel.maxId(), b.stored().checkpoint.lastAppliedServerId)
    }

    @Test fun t16b_aMessageThatShowsUpLateBelowTheCursorIsPickedUpByAFullReplay() {
        for (n in 1..5) channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", n.toLong(), acct.value, saved(n))))
        val lateId = channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", 6, acct.value, saved(6))), hiddenFetches = 1_000)
        for (n in 7..9) channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", n.toLong(), acct.value, saved(n))))
        val b = device("dev-b")
        b.sync()
        assertEquals((1..9).filter { it != 6 }.map { saved(it) }.toSet(), b.present().toSet())
        channel.reveal(lateId)
        b.sync() // incremental: the late one is below the cursor
        assertFalse(saved(6) in b.present())
        b.sync(SyncMode.FULL_REPLAY)
        assertTrue(saved(6) in b.present())
        assertEquals(9, b.present().size)
    }

    @Test fun t16c_aFlakyServerReturningAnEmptyPageThatClaimsMoreNeverSpins() {
        val a = device("dev-a")
        var calls = 0
        val spinning = object : PlaylistSyncTransport {
            override fun send(channelId: Long, text: String) = SendResult.Sent(1)
            override fun fetchAfter(channelId: Long, afterServerId: Long, limit: Int): FetchResult {
                calls++
                return FetchResult.Page(emptyList(), hasMore = true)
            }
        }
        val c = PlaylistSyncCoordinator(a.session, spinning, { 0L })
        assertEquals(SyncStatus.OK, c.runOnce(acct).status)
        assertEquals(1, calls)
    }

    @Test fun t16d_textThatIsNotOursIsSkippedAndTheCursorPassesIt() {
        val first = channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", 1, acct.value, saved(1))))
        val chatter = channel.inject("Hello, this is me typing in my own channel")
        val last = channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", 2, acct.value, saved(2))))
        val b = device("dev-b")
        val r = b.sync()
        assertEquals(SyncStatus.OK, r.status)
        assertEquals(1, r.foreignSkipped)
        assertEquals(setOf(saved(1), saved(2)), b.present().toSet())
        assertEquals(last, b.stored().checkpoint.lastAppliedServerId)
        assertTrue(first < chatter && chatter < last)
    }

    // ---- 17-18: accounts ----------------------------------------------------------------------

    @Test fun t17_anotherAccountsMessageStopsTheReaderBeforeItAndNothingIsSent() {
        val m1 = channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", 1, acct.value, saved(1))))
        val bad = channel.inject(envelopeText(2002L, "dev-q", addOp("dev-q", 1, 2002L, saved(7))))
        channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", 2, acct.value, saved(2))))
        val b = device("dev-b")
        b.song(9)
        val r = b.sync()
        assertEquals(SyncStatus.RECOVERY_REQUIRED, r.status)
        assertEquals(RecoveryReason.WRONG_ACCOUNT, r.recovery)
        assertEquals(PlaylistSyncProtocol.Error.WRONG_ACCOUNT, r.protocolError)
        assertEquals(bad, r.badMessageId)
        assertEquals("the good message before it was applied", setOf(saved(1), saved(9)), b.present().toSet())
        assertEquals("the cursor stops at the last good message", m1, b.stored().checkpoint.lastAppliedServerId)
        assertEquals("nothing was sent into a channel that holds someone else's data", 0, channel.sendCalls)
        assertEquals(1, b.pendingIds().size)
    }

    @Test fun t17b_aDeviceOfAnotherAccountOnTheSameChannelIsRejectedAndChangesNothing() {
        val a = device("dev-a"); a.song(1); a.sync()
        val stranger = device("dev-q", account = TelegramUserId(2002L))
        stranger.song(3)
        val before = channel.texts()
        val r = stranger.sync()
        assertEquals(RecoveryReason.WRONG_ACCOUNT, r.recovery)
        assertEquals(before, channel.texts())
        assertEquals(emptyList<Long>() + saved(3), stranger.present())
        assertEquals(setOf(saved(3)), stranger.present().toSet())
    }

    @Test fun t18_accountSwitchAToBBlocksTheLateResultOfA() {
        val other = TelegramUserId(2002L)
        channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", 1, acct.value, saved(1))))
        val a = device("dev-a")
        a.song(2)
        a.hooks.beforeFetch = { a.session.close(); a.session.open(other) } // logout + login as B while A's fetch is in flight
        val r = a.coordinator.runOnce(acct)
        assertEquals(SyncStatus.STALE_SESSION, r.status)
        a.hooks.beforeFetch = null
        val b = a.session.view(other)!!.state
        assertTrue("B received nothing of A", b.orSet.knownItems().isEmpty() && b.outbox.isEmpty() && b.checkpoint.lastAppliedServerId == 0L)
        assertEquals(null, a.session.view(acct))
        assertEquals("A can no longer be applied through B's session", UpdateResult.Stale,
            a.session.update(acct, 1L) { it })
        assertEquals(0, channel.sendCalls)
        // back to A: nothing of A was lost
        a.session.open(acct)
        assertEquals(1, a.pendingIds().size)
        assertEquals(SyncStatus.OK, a.sync().status)
        assertEquals(setOf(saved(1), saved(2)), a.present().toSet())
    }

    @Test fun t18c_aLateResultOfAnEarlierSessionOfTheSameAccountIsRefusedAfterALogoutAndLoginAgain() {
        val other = TelegramUserId(2002L)
        channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", 1, acct.value, saved(1))))
        val a = device("dev-a")
        a.song(2)
        // A -> B -> A while A's fetch is in flight: same account, but a different session
        a.hooks.beforeFetch = { a.session.close(); a.session.open(other); a.session.close(); a.session.open(acct) }
        val r = a.coordinator.runOnce(acct)
        assertEquals(SyncStatus.STALE_SESSION, r.status)
        a.hooks.beforeFetch = null
        assertEquals("the late page was not applied", 0L, a.stored().checkpoint.lastAppliedServerId)
        assertEquals(listOf(saved(2)), a.present())
        assertEquals(1, a.pendingIds().size)
        assertEquals(0, channel.sendCalls)
        assertEquals(SyncStatus.OK, a.sync().status) // the next run, with the new session, works
        assertEquals(setOf(saved(1), saved(2)), a.present().toSet())
    }

    @Test fun t18b_theAnswerToASendThatArrivesAfterALogoutIsNotAppliedToTheNewAccount() {
        val other = TelegramUserId(2002L)
        val a = device("dev-a")
        a.song(1)
        a.hooks.afterStoreBeforeReply = { a.session.close(); a.session.open(other) }
        val r = a.coordinator.runOnce(acct)
        assertEquals(SyncStatus.STALE_SESSION, r.status)
        a.hooks.afterStoreBeforeReply = null
        val b = a.session.view(other)!!.state
        assertTrue(b.outbox.isEmpty() && b.orSet.knownItems().isEmpty())
        a.session.open(acct)
        assertEquals("the operation is still waiting: the answer was never applied", 1, a.pendingIds().size)
        a.sync()
        assertEquals("found again in the history, not sent twice", 1, channel.sendCalls)
        assertEquals(emptyList<String>(), a.pendingIds())
    }

    // ---- 19-21: identity and what never leaves the device -------------------------------------

    @Test fun t19_twoChatsWithTheSameRawMessageIdStayTwoSongsAcrossDevices() {
        val a = device("dev-a"); val b = device("dev-b")
        a.songElsewhere(-100111L, 5L); a.songElsewhere(-100222L, 5L)
        a.sync(); b.sync()
        assertEquals(2, b.present().size)
        assertEquals(setOf(PlaylistItemId.Telegram(-100111L, 5L), PlaylistItemId.Telegram(-100222L, 5L)), b.stored().orSet.knownItems())
        b.songElsewhere(-100111L, 5L); b.sync(); a.sync()
        assertEquals(listOf(TelegramSongKey.forMessage(-100222L, 5L, acct.value)), a.present())
        assertConverged(a, b)
    }

    @Test fun t20_anUnverifiedLegacyKeyNeverLeavesTheDevice() {
        val root = folder.newFolder("dev-a")
        val hashKey = TelegramSongKey.forMessage(-100500L, 321L, acct.value)
        val seed = "dev-a".sumOf { it.code } % 90 + 1
        TelegramSavedPlaylistStore(root, TestAesGcmCipher(seed), TestPlainFileIo).save(acct, listOf(hashKey))
        val a = device("dev-a", root = root) // no index: the key cannot be named
        assertEquals(listOf(hashKey), a.present())
        a.song(1)
        a.sync()
        val everything = channel.texts().joinToString("\n")
        assertFalse(everything.contains(hashKey.toString()))
        assertFalse(everything.contains("legacy"))
        assertEquals(1, channel.count())
        val b = device("dev-b"); b.sync()
        assertEquals(listOf(saved(1)), b.present())
        assertEquals(listOf(hashKey, saved(1)), a.present())
    }

    @Test fun t21_localOnlyAndUnnamedSongsNeverEnterTheOutbox() {
        val a = device("dev-a")
        val unnamed = TelegramSongKey.forMessage(-100500L, 321L, acct.value)
        assertTrue(a.session.toggle(acct, PlaylistTarget(unnamed)) is ToggleOutcome.Added) // chat/message unknown -> kept local
        assertTrue(a.stored().outbox.isEmpty())
        assertEquals(0, a.sync().envelopesSent)
        assertEquals(0, channel.count())
        // and the wire format cannot even express such an item
        val local = "#mvsync v1\nscope\t${PlaylistSyncProtocol.scopeOf(acct.value)}\nsender\tdev-a\nbatch\tdev-a-1\nop\tdev-a#1\tADD\tlocal:5\t1\tdev-a#1\n"
        val sealed = local + "sum\t" + TelegramPlaylistCodec.sha256Hex(local)
        assertTrue(PlaylistSyncProtocol.decode(sealed, PlaylistSyncProtocol.scopeOf(acct.value)) is PlaylistSyncProtocol.Decoded.Invalid)
    }

    // ---- 22-23: damaged logs and lost channels -------------------------------------------------

    private fun damagedTexts(): Map<String, String> {
        val good = envelopeText(acct.value, "dev-x", addOp("dev-x", 9, acct.value, saved(9)))
        return mapOf(
            "bad checksum" to good.dropLast(3) + "000",
            "future version" to good.replaceFirst("#mvsync v1", "#mvsync v2"),
            "marker only" to "#mvsync v1\ngarbage",
            "oversized" to "#mvsync v1\n" + "x".repeat(5000),
        )
    }

    @Test fun t22_aDamagedMessageStopsTheCursorWithoutSwallowingAnythingAndBlocksSending() {
        for ((name, text) in damagedTexts()) {
            channel = FakeTelegramChannel()
            val goodFirst = channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", 1, acct.value, saved(1))))
            val bad = channel.inject(text)
            channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", 2, acct.value, saved(2))))
            val b = device("dev-b-" + name.replace(' ', '-'))
            b.song(7)
            val before = b.pendingIds()
            val r = b.sync()
            assertEquals(name, SyncStatus.RECOVERY_REQUIRED, r.status)
            assertEquals(name, bad, r.badMessageId)
            assertEquals(name, goodFirst, b.stored().checkpoint.lastAppliedServerId)
            assertEquals(name, setOf(saved(1), saved(7)), b.present().toSet()) // the later good message is NOT applied
            assertEquals(name, 0, channel.sendCalls)
            assertEquals(name, before, b.pendingIds())
            // asking again: still stopped at the same place, nothing deleted, nothing moved
            b.waitOut(r)
            val again = b.sync()
            assertEquals(name, SyncStatus.RECOVERY_REQUIRED, again.status)
            assertEquals(name, goodFirst, b.stored().checkpoint.lastAppliedServerId)
            assertTrue(name, b.storedFile().isFile)
        }
    }

    @Test fun t22b_aFutureProtocolIsReportedAsSuchAndANewerReaderLaterCanContinue() {
        val bad = channel.inject(damagedTexts().getValue("future version"))
        val b = device("dev-b")
        val r = b.sync()
        assertEquals(RecoveryReason.FUTURE_PROTOCOL, r.recovery)
        assertEquals(PlaylistSyncProtocol.Error.FUTURE_VERSION, r.protocolError)
        assertEquals(bad, r.badMessageId)
        assertEquals(0L, b.stored().checkpoint.lastAppliedServerId)
    }

    @Test fun t22c_ourOwnOperationComingBackDifferentFromWhatWeWroteIsCorruption() {
        val a = device("dev-a")
        a.song(1) // op dev-a#1 waits in the outbox
        channel.inject(envelopeText(acct.value, "dev-a", addOp("dev-a", 1, acct.value, saved(2)))) // same id, other song
        val r = a.sync()
        assertEquals(RecoveryReason.CORRUPT_MESSAGE, r.recovery)
        assertEquals(listOf(saved(1)), a.present())
        assertEquals(1, a.pendingIds().size)
        assertEquals(0, channel.sendCalls)
    }

    @Test fun t23_aDeletedOrForbiddenChannelIsNeverRecreatedAndNothingIsLost() {
        val cases = mapOf(
            "deleted" to ({ c: FakeTelegramChannel, on: Boolean -> c.deleted = on } to RecoveryReason.CHANNEL_MISSING),
            "unauthorized" to ({ c: FakeTelegramChannel, on: Boolean -> c.unauthorized = on } to RecoveryReason.UNAUTHORIZED),
            "revoked" to ({ c: FakeTelegramChannel, on: Boolean -> c.revoked = on } to RecoveryReason.SESSION_REVOKED),
        )
        for ((name, case) in cases) {
            channel = FakeTelegramChannel()
            val a = device("dev-a-$name")
            a.song(1)
            val before = a.storedFile().readBytes()
            case.first(channel, true)
            val r = a.sync()
            assertEquals(name, SyncStatus.RECOVERY_REQUIRED, r.status)
            assertEquals(name, case.second, r.recovery)
            assertArrayEquals(name, before, a.storedFile().readBytes())
            assertEquals(name, channel.fetchCalls, 1)
            assertEquals(name, FakeTelegramChannel().channelId, a.stored().checkpoint.channelId) // same channel id, no new channel
            // immediately again: waits, no call at all
            assertEquals(name, SyncStatus.BACKOFF_ACTIVE, a.sync().status)
            assertEquals(name, 1, channel.fetchCalls)
            // access restored and the user presses "try again"
            case.first(channel, false)
            a.coordinator.resetBackoff()
            assertEquals(name, SyncStatus.OK, a.sync().status)
            assertEquals(name, emptyList<String>(), a.pendingIds())
        }
    }

    // ---- 24: network flapping, timeouts, 429 ----------------------------------------------------

    @Test fun t24_networkFlapTimeoutAnd429BackOffWithoutBusyLooping() {
        val a = device("dev-a")
        a.song(1)
        channel.fetchFaults.add(FetchResult.Failed(SyncErrorKind.NETWORK))
        val r1 = a.sync()
        assertEquals(SyncStatus.BACKOFF, r1.status)
        assertEquals(5_000L, r1.retryAfterMs)
        repeat(20) { assertEquals(SyncStatus.BACKOFF_ACTIVE, a.sync().status) }
        assertEquals("twenty early calls made no call to the channel", 1, channel.fetchCalls)
        a.nowMs += 4_999
        assertEquals(SyncStatus.BACKOFF_ACTIVE, a.sync().status)
        a.nowMs += 1

        channel.fetchFaults.add(FetchResult.Failed(SyncErrorKind.FLOOD_WAIT, 35))
        val r2 = a.sync()
        assertEquals("the server's 35 seconds are obeyed exactly", 35_000L, r2.retryAfterMs)
        a.waitOut(r2)

        channel.sendFaults.add(FakeTelegramChannel.SendFault.TIMEOUT_NOT_STORED)
        val r3 = a.sync()
        assertEquals(SyncStatus.BACKOFF, r3.status)
        assertTrue("grows with repeated failures", r3.retryAfterMs > 5_000L)
        assertEquals(0, channel.count())
        assertEquals(OutboxStatus.RETRY_REQUIRED, a.stored().outbox.single().status)
        a.waitOut(r3)

        assertEquals(SyncStatus.OK, a.sync().status)
        assertEquals(1, channel.count())
        // a success forgets the failures
        channel.fetchFaults.add(FetchResult.Failed(SyncErrorKind.NETWORK))
        assertEquals(5_000L, a.sync().retryAfterMs)
    }

    @Test fun t24b_backoffArithmeticIsExponentialCappedAndObeysTheServer() {
        val b = SyncBackoff(baseMs = 1_000, maxMs = 8_000)
        assertEquals(1_000L, b.onFailure(0, SyncErrorKind.NETWORK, 0))
        assertEquals(2_000L, b.onFailure(0, SyncErrorKind.TIMEOUT, 0))
        assertEquals(4_000L, b.onFailure(0, SyncErrorKind.NETWORK, 0))
        assertEquals(8_000L, b.onFailure(0, SyncErrorKind.NETWORK, 0))
        assertEquals(8_000L, b.onFailure(0, SyncErrorKind.NETWORK, 0))
        assertEquals(120_000L, b.onFailure(1_000, SyncErrorKind.FLOOD_WAIT, 120))
        assertEquals(121_000L, b.notBeforeMs)
        b.onSuccess()
        assertEquals(1_000L, b.onFailure(0, SyncErrorKind.NETWORK, 0))
    }

    // ---- 25-26: backpressure --------------------------------------------------------------------

    @Test fun t25_aFullOutboxKeepsEveryWaitingOperationAndDrainsInOrder() {
        val a = device("dev-a", config = SyncConfig(maxEnvelopesPerRun = 5))
        val ops = (1..PlaylistState.MAX_OUTBOX).map { n -> addOp("dev-a", n.toLong(), acct.value, saved(n)) }
        val view = a.session.view(acct)!!
        assertTrue(a.session.update(acct, view.epoch) { s ->
            s.copy(
                opCounter = ops.size.toLong(), lamport = ops.size.toLong(),
                outbox = ops.map { OutboxEntry(it, OutboxStatus.PENDING, 0) },
                orSet = ops.fold(s.orSet) { acc, op -> acc.apply(op) },
            )
        } is UpdateResult.Committed)
        assertEquals("a change that needs a slot is refused, loudly", ToggleOutcome.OutboxFull, a.song(1))
        assertEquals(PlaylistState.MAX_OUTBOX, a.pendingIds().size)

        val r = a.sync()
        assertEquals(SyncStatus.OK, r.status)
        assertEquals(5, r.envelopesSent)
        assertEquals("100 operations went out, were acknowledged, and only then were pruned", PlaylistState.MAX_OUTBOX - 100, a.pendingIds().size)
        assertEquals("the oldest went first, in order", ops.take(100).map { it.opId }, a.sentOpIds().take(100))
        assertEquals("nothing that was waiting was dropped", ops.drop(100).map { it.opId }, a.pendingIds())
        assertTrue("now there is room again", a.song(1) is ToggleOutcome.Removed)
    }

    private fun FakeSyncDevice.sentOpIds(): List<String> =
        channel.texts().flatMap { text ->
            (PlaylistSyncProtocol.decode(text, PlaylistSyncProtocol.scopeOf(account.value)) as PlaylistSyncProtocol.Decoded.Ok)
                .envelope.ops.map { it.opId }
        }

    @Test fun t26_acknowledgingAndPruningNeverRemoveWaitingOperations() {
        val ack = OutboxEntry(addOp("dev-a", 1, acct.value, saved(1)), OutboxStatus.ACKNOWLEDGED, 0)
        val pending = OutboxEntry(addOp("dev-a", 2, acct.value, saved(2)), OutboxStatus.PENDING, 0)
        val retry = OutboxEntry(addOp("dev-a", 3, acct.value, saved(3)), OutboxStatus.RETRY_REQUIRED, 4)
        val s = PlaylistState.empty(acct.value, "dev-a").copy(outbox = listOf(ack, pending, retry))
        assertEquals(listOf(pending, retry), s.pruneAcknowledged().outbox)
        // and in a real run whose send fails nothing is pruned and nothing is lost
        val a = device("dev-a2")
        a.song(1); a.song(2)
        channel.sendFaults.add(FakeTelegramChannel.SendFault.NETWORK)
        a.sync()
        assertEquals(2, a.pendingIds().size)
        assertTrue(a.stored().outbox.all { it.status == OutboxStatus.RETRY_REQUIRED })
    }

    // ---- 27-28: long offline, joining late, no snapshot ---------------------------------------

    @Test fun t27_aDeviceOfflineForMonthsConvergesAndKeepsItsOwnWork() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); a.sync(); b.sync()
        // B is away for three months, with its own unsynced change
        b.song(50)
        for (round in 1..30) {
            a.song(round % 7 + 1); a.sync()
        }
        b.nowMs += 90L * 24 * 3600 * 1000
        b.restart()
        assertEquals(SyncStatus.OK, b.sync().status)
        a.sync()
        assertConverged(a, b)
        assertTrue(saved(50) in a.present())
        assertEquals(emptyList<String>(), b.pendingIds())
    }

    @Test fun t28_snapshotsAreNotImplementedSoALateDeviceRebuildsExactlyFromTheWholeLog() {
        val a = device("dev-a")
        a.song(1); a.song(2); a.song(3); a.sync()
        a.song(1); a.sync() // 1 removed
        val before = channel.count()
        val late = device("dev-late")
        assertEquals(SyncStatus.OK, late.sync().status)
        assertEquals(setOf(saved(2), saved(3)), late.present().toSet())
        assertEquals(a.crdt(), late.crdt())
        assertTrue("the removed song's tombstone came with the log", late.stored().orSet.wasRemoved(item(1)))
        assertEquals("a reader never truncates or rewrites the log", before, channel.count())
        assertTrue(channel.texts().none { it.startsWith("#mvsync snapshot") })
    }

    // ---- 29-30: properties and exact comparison -------------------------------------------------

    @Test fun t29_randomInterleavingsOfThreeFlakyDevicesAlwaysConverge() {
        val chats = longArrayOf(acct.value, -100200L, -100300L)
        for (seed in 1L..12L) {
            channel = FakeTelegramChannel(seed)
            val random = Random(seed)
            val devices = listOf("dev-a", "dev-b", "dev-c").map { device("$it-s$seed") }
            repeat(70) {
                val d = devices[random.nextInt(devices.size)]
                when (random.nextInt(10)) {
                    in 0..3 -> d.toggle(chats[random.nextInt(chats.size)], (1 + random.nextInt(6)) * 10_000L)
                    in 4..7 -> {
                        val mode = if (random.nextInt(10) == 0) SyncMode.FULL_REPLAY else SyncMode.INCREMENTAL
                        d.waitOut(d.sync(mode))
                    }
                    8 -> d.restart()
                    else -> when (random.nextInt(4)) {
                        0 -> channel.sendFaults.add(FakeTelegramChannel.SendFault.LOST_ACK)
                        1 -> channel.sendFaults.add(FakeTelegramChannel.SendFault.NETWORK)
                        2 -> channel.fetchFaults.add(FetchResult.Failed(SyncErrorKind.FLOOD_WAIT, 10))
                        else -> channel.shufflePages = !channel.shufflePages
                    }
                }
            }
            channel.sendFaults.clear(); channel.fetchFaults.clear(); channel.shufflePages = false
            repeat(4) {
                for (d in devices) {
                    d.nowMs += 10_000_000L
                    d.coordinator.resetBackoff()
                    assertEquals("seed=$seed", SyncStatus.OK, d.sync(SyncMode.FULL_REPLAY).status)
                }
            }
            assertConverged(devices[0], devices[1], devices[2])
            for (d in devices) assertEquals("seed=$seed ${d.name}", emptyList<String>(), d.pendingIds())
            // the shared set equals the plain fold of every valid message the server holds
            val scope = PlaylistSyncProtocol.scopeOf(acct.value)
            val folded = channel.texts().flatMap { t -> (PlaylistSyncProtocol.decode(t, scope) as PlaylistSyncProtocol.Decoded.Ok).envelope.ops }
                .fold(PlaylistOrSet.EMPTY) { acc, op -> acc.apply(op) }
            assertEquals("seed=$seed", folded, devices[0].stored().orSet)
        }
    }

    @Test fun t30_everyPartOfTheStateMatchesNotJustTheCounts() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); a.song(2); b.song(2); b.song(3)
        a.sync(); b.sync(); a.sync(); b.sync()
        val sa = a.stored(); val sb = b.stored()
        assertEquals(sa.orSet.allAdds(), sb.orSet.allAdds())
        assertEquals(sa.orSet.allRemoved().filterValues { it.isNotEmpty() }, sb.orSet.allRemoved().filterValues { it.isNotEmpty() })
        assertEquals(sa.presentSongKeys(acct.value), sb.presentSongKeys(acct.value))
        assertEquals(emptyList<OutboxEntry>(), sa.outbox)
        assertEquals(emptyList<OutboxEntry>(), sb.outbox)
        assertEquals(sa.checkpoint, sb.checkpoint)
        assertEquals(channel.maxId(), sa.checkpoint.lastAppliedServerId)
        assertNotEquals("each device keeps its own identity", sa.deviceId, sb.deviceId)
        assertEquals(acct.value, sa.accountId); assertEquals(acct.value, sb.accountId)
    }

    // ---- more edges ------------------------------------------------------------------------------

    @Test fun t31_aRestoredOlderFileCannotHandOutTheSameTagTwice() {
        val a = device("dev-a")
        val old = a.storedFile().readBytes() // counter 0
        a.song(1); a.sync() // dev-a#1 is on the server
        a.storedFile().writeBytes(old) // an older copy of the file comes back
        a.dropV1Copy()
        a.restart()
        assertEquals(0L, a.stored().opCounter)
        a.sync() // our own earlier operation comes back from the server
        assertEquals(1L, a.stored().opCounter)
        assertEquals(listOf(saved(1)), a.present())
        a.song(2)
        assertEquals("a new tag, never #1 again", listOf("dev-a#2"), a.pendingIds())
    }

    @Test fun t32_ifOnlyTheNoteOfASuccessfulSendIsNotWrittenTheHistoryFixesItWithoutResending() {
        val a = device("dev-a")
        a.song(1)
        a.hooks.afterStoreBeforeReply = { a.v2Io.failNextWrite() } // the server has it; the next write (the ack) fails
        val r1 = a.sync()
        assertEquals(SyncStatus.SAVE_FAILED, r1.status)
        assertEquals(1, channel.count())
        assertEquals(1, a.pendingIds().size)
        a.hooks.afterStoreBeforeReply = null
        a.v2Io.heal()
        assertEquals(SyncStatus.OK, a.sync().status)
        assertEquals("not sent twice", 1, channel.sendCalls)
        assertEquals(emptyList<String>(), a.pendingIds())
    }

    @Test fun t33_anOperationTooLargeToSendStaysWaitingAndIsReported() {
        val a = device("dev-a")
        a.song(1)
        val view = a.session.view(acct)!!
        val many = (1..40).map { addOp("dev-r$it", 1, acct.value, saved(1)) }
        a.session.update(acct, view.epoch) { s -> many.fold(s) { acc, op -> acc.applyRemote(op) } }
        a.sync() // sends the first (ADD) fine
        assertTrue(a.song(1) is ToggleOutcome.Removed) // observes 41 tags -> cannot fit a message
        val r = a.sync()
        assertEquals(RecoveryReason.OP_TOO_LARGE, r.recovery)
        assertEquals(1, a.pendingIds().size)
        assertEquals("nothing else was sent behind it", 1, channel.count())
    }

    @Test fun t34_aDeviceWithoutAChannelDoesNothingAndNeverMakesOne() {
        val a = device("dev-a", bind = false)
        a.song(1)
        val r = a.sync()
        assertEquals(SyncStatus.NO_CHANNEL, r.status)
        assertEquals(0, channel.fetchCalls + channel.sendCalls)
        assertEquals(0L, a.stored().checkpoint.channelId)
        assertEquals(1, a.pendingIds().size)
    }

    @Test fun t35_anAccountThatIsNotOpenIsNotSynced() {
        val a = device("dev-a")
        assertEquals(SyncStatus.NOT_OPEN, a.coordinator.runOnce(TelegramUserId(777L)).status)
        a.session.close()
        assertEquals(SyncStatus.NOT_OPEN, a.sync().status)
        assertEquals(0, channel.fetchCalls)
    }

    @Test fun t36_resolvedLegacyKeysJoinTheOutboxAndReachTheOtherDevice() {
        val root = folder.newFolder("dev-a")
        val hashKey = TelegramSongKey.forMessage(-100500L, 321L, acct.value)
        val seed = "dev-a".sumOf { it.code } % 90 + 1
        TelegramSavedPlaylistStore(root, TestAesGcmCipher(seed), TestPlainFileIo).save(acct, listOf(hashKey))
        val a = device("dev-a", root = root)
        a.sync()
        assertEquals("unverified: stays local", 0, channel.count())
        a.session.attachIndex(object : PlaylistIndex {
            override fun resolveAll(account: TelegramUserId, keys: Collection<Long>) =
                keys.filter { it == hashKey }.associateWith { PlaylistItemId.Telegram(-100500L, 321L) }
        })
        assertTrue(a.session.resolveLegacyKeys(acct))
        a.sync()
        val b = device("dev-b"); b.sync()
        assertEquals(listOf(hashKey), b.present())
    }
}
