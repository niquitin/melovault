package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.PlaylistSyncStatusPolicy.Kind
import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistSession.OpenResult
import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistSession.ToggleOutcome
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * E96 P4-02/04/05: the numbers behind the status line come from the PERSISTED V2 state (so they survive a restart or a
 * crash), stay correct for 300+ songs, a full or unreadable store, an old app's V1 file and an account switch, and none
 * of the UI paths (Save, status read, Retry) reaches TDLib while the build switch is off. Real stores, fake cipher.
 */
class PlaylistStatusFactsTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val other = TelegramUserId(2002L)
    private val chat = -1_001_000_000_777L
    private val rigs = ArrayList<SyncRig>()

    @After fun tearDown() = rigs.forEach { it.stop() }

    private fun device(seed: Int = 7, root: File = folder.newFolder()) =
        FakeSyncDevice("dev-a", root, acct, FakeTelegramChannel(), seed)

    private fun facts(d: FakeSyncDevice) = d.session.statusFacts(acct)!!

    private val optedIn = SyncConsent(true, true, true, SyncConsent.WARNING_VERSION)

    private fun line(f: PlaylistStatusFacts?, c: SyncContext = SyncContext(false, SyncConsent())) =
        PlaylistSyncStatusPolicy.evaluate(acct.value, f, c)

    // ---- source of truth: the stored V2 state -------------------------------------------------------------------------

    @Test fun f01_theCountsComeFromTheCommittedStateAndFollowEverySave() {
        val d = device()
        assertEquals(0, facts(d).savedCount)
        assertTrue(d.toggle(chat, songId(1)) is ToggleOutcome.Added)
        assertTrue(d.toggle(chat, songId(2)) is ToggleOutcome.Added)
        val f = facts(d)
        assertEquals(2, f.savedCount)
        assertEquals(2, f.pendingCount)
        assertEquals(2L, f.opCounter)
        assertTrue(f.channelBound)
        assertTrue(d.toggle(chat, songId(1)) is ToggleOutcome.Removed)
        assertEquals(1, facts(d).savedCount)
        assertEquals(3, facts(d).pendingCount)
    }

    @Test fun f02_afterARestartOrCrashThePendingOperationsAndTheStatusAreStillThere() {
        val d = device()
        d.toggle(chat, songId(1)); d.toggle(chat, songId(2))
        val before = facts(d)
        d.restart() // everything in memory is thrown away, the files are reopened
        val after = facts(d)
        assertEquals(before, after)
        assertEquals(Kind.LOCAL_SYNC_OFF, line(after).kind)
        // With sync opted in the same persisted queue reads "waiting".
        assertEquals(Kind.LOCAL_PENDING, line(after, SyncContext(true, optedIn)).kind)
    }

    @Test fun f03_threeHundredAndMoreSavedSongsAreKeptAndCountedNothingIsHiddenAtTheV1Cap() {
        val d = device()
        val view = d.session.view(acct)!!
        val result = d.session.update(acct, view.epoch) { s ->
            var next = s
            for (i in 1..350) next = next.addTelegram(PlaylistItemId.Telegram(chat, songId(i))).state
            next
        }
        assertTrue(result is UpdateResult.Committed)
        val f = facts(d)
        assertEquals(350, f.savedCount)
        assertTrue(f.projectionTruncated)
        d.restart()
        assertEquals("a restart does not lose song 301..350", 350, facts(d).savedCount)
        assertEquals(350, d.present().size)
        assertTrue(line(facts(d)).fullDetail().contains("350 bài đã lưu"))
        // The copy for older apps holds the newest 300 only.
        assertEquals(TelegramPlaylistPolicy.MAX_IDS, TelegramPlaylistV2Session.projectionIds(d.stored(), acct.value).size)
    }

    @Test fun f04_removingTheLastSongLeavesAValidEmptyEncryptedFileNotAMissingOne() {
        val d = device()
        d.toggle(chat, songId(1))
        assertTrue(d.toggle(chat, songId(1)) is ToggleOutcome.Removed)
        assertTrue(d.storedFile().isFile)
        assertEquals(0, facts(d).savedCount)
        assertEquals(0, d.present().size)
        d.restart()
        assertEquals("an emptied playlist reopens as empty, not as 'missing'", 0, facts(d).savedCount)
        assertEquals("the removal is still waiting for the channel", 2, facts(d).pendingCount)
    }

    @Test fun f05_aFullOutboxRefusesTheNewSaveAndTheStatusStillShowsTheTrueNumbers() {
        val d = device()
        val view = d.session.view(acct)!!
        val entries = (1..PlaylistState.MAX_OUTBOX).map { OutboxEntry(addOp("old", it.toLong(), chat, songId(it)), OutboxStatus.PENDING, 0) }
        assertTrue(d.session.update(acct, view.epoch) { it.copy(outbox = entries) } is UpdateResult.Committed)
        assertEquals(ToggleOutcome.OutboxFull, d.toggle(chat, songId(99_999)))
        val f = facts(d)
        assertEquals(PlaylistState.MAX_OUTBOX, f.pendingCount)
        assertEquals("the refused save left no trace", 0, f.savedCount)
        assertTrue(line(f, SyncContext(true, optedIn)).detail.contains("${PlaylistState.MAX_OUTBOX} thay đổi"))
    }

    @Test fun f06_anUnreadableV2FileIsReportedNeverReplacedAndSaveIsRefused() {
        val root = folder.newFolder()
        val d = device(root = root)
        d.toggle(chat, songId(1))
        val file = d.storedFile()
        val garbage = ByteArray(200) { (it * 7).toByte() }
        file.writeBytes(garbage)
        val s = TelegramPlaylistV2Session(
            TelegramSavedPlaylistStore(root, TestAesGcmCipher(7), TestPlainFileIo),
            TelegramPlaylistStoreV2(root, TestAesGcmCipher(107), TestPlainFileIo),
        ) { "dev-a" }
        assertTrue(s.open(acct) is OpenResult.Blocked)
        val f = s.statusFacts(acct)!!
        assertNotNull(f.unreadable)
        val shown = line(f)
        assertEquals(Kind.UNREADABLE, shown.kind)
        assertTrue(s.toggle(acct, PlaylistTarget(TelegramSongKey.forMessage(chat, songId(2), acct.value), chat, songId(2))) is ToggleOutcome.Blocked)
        assertArrayEquals("the damaged file was not touched", garbage, file.readBytes())
        assertEquals(MeloSavePolicy.Availability.PLAYLIST_UNREADABLE,
            MeloSavePolicy.availability(true, false, 5L, true, chat, songId(2), acct.value, playlistUnreadable = true, playlistLoaded = false))
    }

    @Test fun f07_changesAnOldAppMadeToV1ThatCouldNotBeImportedAreWarnedAboutAndKeepTheListVisible() {
        val root = folder.newFolder()
        val newSong = PlaylistItemId.Telegram(chat, songId(500))
        val newKey = TelegramSongKey.forMessage(chat, songId(500), acct.value)
        val index = object : PlaylistIndex {
            override fun resolveAll(account: TelegramUserId, keys: Collection<Long>) =
                keys.filter { it == newKey }.associateWith { newSong }
        }
        val d = FakeSyncDevice("dev-a", root, acct, FakeTelegramChannel(), 7, index = index)
        d.toggle(chat, songId(1))
        val view = d.session.view(acct)!!
        val entries = (1..PlaylistState.MAX_OUTBOX).map { OutboxEntry(addOp("old", it.toLong(), chat, songId(it + 1_000)), OutboxStatus.PENDING, 0) }
        d.session.update(acct, view.epoch) { it.copy(outbox = entries) }
        // An older APK appends a song to the V1 file; the library can name it, so importing it needs one outbox slot.
        val v1 = TelegramSavedPlaylistStore(root, TestAesGcmCipher(7), TestPlainFileIo)
        val old = (v1.load(acct) as PlaylistLoadResult.Ok).ids
        v1.save(acct, old + newKey)
        d.restart()
        val f = facts(d)
        assertEquals(PlaylistWarning.IMPORT_DEFERRED_OUTBOX_FULL, f.importWarning)
        val shown = line(f)
        assertEquals(Kind.IMPORT_DEFERRED, shown.kind)
        assertEquals("the stored playlist is still shown", 1, f.savedCount)
        assertTrue(shown.detail.contains("Không bài nào bị mất"))
    }

    @Test fun f08_accountAThenBThenAKeepsEachAccountsOwnNumbersAndShowsNoneOfTheOthers() {
        val root = folder.newFolder()
        val d = device(root = root)
        d.toggle(chat, songId(1)); d.toggle(chat, songId(2))
        val a1 = facts(d)
        assertEquals(2, a1.savedCount)
        assertNull("nothing is shown for an account that is not open", d.session.statusFacts(other))
        d.session.close()
        assertNull(d.session.statusFacts(acct))
        assertTrue(d.session.open(other) is OpenResult.Ready)
        val b = d.session.statusFacts(other)!!
        assertEquals(0, b.savedCount)
        assertEquals(other.value, b.accountId)
        d.session.close()
        assertTrue(d.session.open(acct) is OpenResult.Ready)
        assertEquals("A is back exactly as it was", a1.savedCount, d.session.statusFacts(acct)!!.savedCount)
    }

    @Test fun f09_aLateStatusOfTheOldAccountIsIgnoredAfterTheSwitch() {
        val d = device()
        d.toggle(chat, songId(1))
        val ofA = facts(d)
        // B is now signed in; A's late callback arrives and must not show a count.
        val shown = PlaylistSyncStatusPolicy.evaluate(other.value, ofA, SyncContext(false, SyncConsent()))
        assertEquals(Kind.LOADING, shown.kind)
        assertFalse(shown.fullDetail().contains("1 "))
    }

    // ---- never "synced" without proof ------------------------------------------------------------------------------------

    private fun rig(build: Boolean): SyncRig = SyncRig(
        "ui-dev", folder.newFolder(), FakeTdlibWorld(), acct, channelId = 0L, seed = 21, buildEnabled = build, consent = true,
    ).also { rigs.add(it) }

    @Test fun f10_aSendWhoseAnswerNeverArrivedIsNotSynced() {
        val r = rig(build = true)
        r.device.bindChannel(r.world.createSyncChannel(acct.value).id)
        r.device.toggle(chat, songId(1))
        r.tdlib.gateway.defaultSendMode = SendMode.NO_REPLY_NOT_STORED
        val report = r.run()
        val f = r.device.session.statusFacts(acct)!!
        val shown = PlaylistSyncStatusPolicy.evaluate(
            acct.value, f, SyncContext(true, SyncConsent(true, true, true, SyncConsent.WARNING_VERSION), false, LastSyncRun.of(acct.value, Gated.Done(report), f.opCounter))
        )
        assertNotEquals("provisional TDLib answer or silence is not an ACK", Kind.SYNCED, shown.kind)
        assertTrue(f.pendingCount > 0)
    }

    @Test fun f11_aSavedChangeAfterAnOkRunTurnsSyncedIntoWaitingAgain() {
        val r = rig(build = true)
        val channel = r.world.createSyncChannel(acct.value)
        r.device.bindChannel(channel.id)
        r.device.toggle(chat, songId(1))
        val report = r.run()
        assertEquals(SyncStatus.OK, report.status)
        val ctx = SyncContext(true, optedIn, false, LastSyncRun.of(acct.value, Gated.Done(report), r.device.session.statusFacts(acct)!!.opCounter))
        assertEquals(Kind.SYNCED, PlaylistSyncStatusPolicy.evaluate(acct.value, r.device.session.statusFacts(acct), ctx).kind)
        r.device.toggle(chat, songId(2)) // a new local change
        assertEquals(Kind.LOCAL_PENDING, PlaylistSyncStatusPolicy.evaluate(acct.value, r.device.session.statusFacts(acct), ctx).kind)
    }

    // ---- build switch OFF: no UI path reaches TDLib ----------------------------------------------------------------------

    @Test fun f12_withSyncOffSavingReadingTheStatusAndRetryNeverSendATdlibRequest() {
        val r = rig(build = false)
        val before = r.tdlib.gateway.requests.size
        // Launch / rotate / resume: reading the status (what the activity does) and building the settings screen.
        repeat(3) { PlaylistSyncStatusPolicy.evaluate(acct.value, r.device.session.statusFacts(acct), SyncContext(false, r.consentStore.load(acct))) }
        assertTrue(r.device.toggle(chat, songId(1)) is ToggleOutcome.Added) // a UI Save
        assertTrue(r.device.toggle(chat, songId(1)) is ToggleOutcome.Removed)
        assertTrue(r.service.runSync(acct) is Gated.Denied) // a Retry
        assertEquals(before, r.tdlib.gateway.requests.size)
        assertEquals(0, r.tdlib.gateway.count("sendMessage"))
        assertEquals(0, r.tdlib.gateway.count("createNewSupergroupChat"))
    }

    @Test fun f13_productFeatureRequiresConsentAndExplicitKillSwitchStillWorks() {
        assertTrue(TelegramPlaylistFlags.SYNC_ENABLED)
        assertTrue(TelegramPlaylistFlags.V2_ENABLED)
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), DisabledSyncActions.run(acct, SyncMode.INCREMENTAL))
        assertEquals(SyncDenied.BUILD_OFF, SyncGate(buildEnabled = false, store = InMemorySyncConsentStore()).mayRun(acct))
    }

    @Test fun f14_v1OnlySessionsHaveNoStatusFactsAndNeverCrashTheLine() {
        val v1 = TelegramPlaylistSession(TelegramSavedPlaylistStore(folder.newFolder(), TestAesGcmCipher(3), TestPlainFileIo))
        v1.open(acct)
        assertNull(v1.statusFacts(acct))
        assertEquals(Kind.LOADING, line(null).kind)
    }
}
