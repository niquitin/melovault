package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** Nhiều Playlist có tên, S4: the single-writer session (fake store, then the real store with a fake cipher). */
class NamedPlaylistSessionTest {
    @get:Rule val folder = TemporaryFolder()

    private val a = TelegramUserId(1001L)
    private val b = TelegramUserId(2002L)

    private class MemoryStore : NamedPlaylistPersistence {
        val files = HashMap<Long, NamedPlaylistState>()
        var unreadable: PlaylistUnreadableReason? = null
        var failSaves = false
        var saves = 0

        override fun load(user: TelegramUserId): NamedLoadResult {
            unreadable?.let { return NamedLoadResult.Unreadable(it) }
            return files[user.value]?.let { NamedLoadResult.Valid(it) } ?: NamedLoadResult.Missing
        }

        override fun save(user: TelegramUserId, state: NamedPlaylistState) {
            unreadable?.let { throw PlaylistUnreadableException(it) }
            if (failSaves) throw IOException("disk full")
            saves++
            files[user.value] = state
        }
    }

    private var devices = 0
    private fun session(store: NamedPlaylistPersistence) = NamedPlaylistSession(store) { "dev-${++devices}" }

    private fun tg(n: Long) = PlaylistItemId.Telegram(-100123L, n)
    private fun idOf(outcome: NamedOutcome): ListId = checkNotNull((outcome as NamedOutcome.Ok).listId)

    // ---- open / close ----------------------------------------------------------------------------

    @Test fun openingWritesNothingAndANewDeviceGetsItsOwnId() {
        val store = MemoryStore()
        val s = session(store)
        assertEquals(NamedOpenResult.Ready(emptyList()), s.open(a))
        assertEquals(0, store.saves)
        assertTrue(store.files.isEmpty())
        assertEquals("dev-1", s.view(a)?.state?.deviceId)
        s.createList(a, "Một")
        assertEquals("dev-1", store.files.getValue(a.value).deviceId)
        val again = session(store)
        again.open(a)
        assertEquals("the device id stays after a restart", "dev-1", again.view(a)?.state?.deviceId)
        assertEquals(1, devices)
    }

    @Test fun changesBeforeOpenAfterCloseOrForAnotherAccountAreNotOpen() {
        val s = session(MemoryStore())
        assertEquals(NamedOutcome.NotOpen, s.createList(a, "x"))
        assertTrue(s.snapshot().isEmpty())
        assertNull(s.snapshotFor(a))
        assertNull(s.view(a))
        s.open(a)
        assertEquals(NamedOutcome.NotOpen, s.createList(b, "x"))
        assertNull(s.snapshotFor(b))
        assertNull(s.entries(b, ListId("dev-1", 1L)))
        assertNull(s.listsContaining(b, tg(1)))
        assertTrue(s.createList(a, "x") is NamedOutcome.Ok)
        s.close()
        assertEquals(NamedOutcome.NotOpen, s.createList(a, "y"))
        assertNull(s.snapshotFor(a))
        assertTrue(s.snapshot().isEmpty())
    }

    @Test fun switchingAccountsAtoBtoAneverMixesTheirPlaylists() {
        val store = MemoryStore()
        val s = session(store)
        s.open(a)
        val forA = idOf(s.createList(a, "Của A"))
        s.addTelegram(a, forA, tg(1))
        s.open(b)
        assertEquals(emptyList<NamedListView>(), s.snapshot())
        assertNull("A's session is gone", s.snapshotFor(a))
        assertEquals(NamedOutcome.NotOpen, s.addTelegram(a, forA, tg(2)))
        val forB = idOf(s.createList(b, "Của B"))
        assertEquals(listOf("Của B"), s.snapshot().map { it.name })
        s.open(a)
        assertEquals(listOf("Của A"), s.snapshot().map { it.name })
        assertEquals(listOf<PlaylistItemId>(tg(1)), s.entries(a, forA)?.map { it.item })
        assertTrue("B's playlist is not visible to A", s.entries(a, forB)?.isEmpty() == true)
        assertEquals(setOf(a.value, b.value), store.files.keys)
        assertEquals(a.value, store.files.getValue(a.value).accountId)
        assertEquals(b.value, store.files.getValue(b.value).accountId)
        assertEquals(listOf("Của B"), store.files.getValue(b.value).lists().map { it.name })
    }

    // ---- changes ----------------------------------------------------------------------------------

    @Test fun everyChangeIsStoredBeforeItIsAdoptedAndSurvivesARestart() {
        val store = MemoryStore()
        val s = session(store)
        s.open(a)
        val id = idOf(s.createList(a, "Gym"))
        assertEquals(NamedOutcome.Ok(id, true), s.addTelegram(a, id, tg(1)))
        s.addTelegram(a, id, tg(2))
        s.moveItem(a, id, tg(2), 0)
        s.addLocal(a, id, 9L, LocalFingerprint.of("x", 1, 1))
        s.renameList(a, id, "Gym 2")
        assertEquals(5 + 1, store.saves)
        val restarted = session(store)
        assertEquals(NamedOpenResult.Ready(listOf(NamedListView(id, "Gym 2", 3))), restarted.open(a))
        assertEquals(
            listOf<PlaylistItemId>(tg(2), tg(1), PlaylistItemId.Local(9L)),
            restarted.entries(a, id)?.map { it.item }
        )
        s.removeLocal(a, id, 9L)
        s.removeTelegram(a, id, tg(1))
        s.deleteList(a, id)
        assertEquals(emptyList<NamedListView>(), s.snapshot())
        assertEquals(emptyList<NamedListView>(), store.files.getValue(a.value).lists())
    }

    @Test fun aChangeThatChangesNothingIsOkWithoutAWrite() {
        val store = MemoryStore()
        val s = session(store)
        s.open(a)
        val id = idOf(s.createList(a, "Gym"))
        s.addTelegram(a, id, tg(1))
        val saves = store.saves
        assertEquals(NamedOutcome.Ok(id, false), s.addTelegram(a, id, tg(1)))
        assertEquals(NamedOutcome.Ok(id, false), s.renameList(a, id, " Gym "))
        assertEquals(NamedOutcome.Ok(id, false), s.removeTelegram(a, id, tg(7)))
        assertEquals(NamedOutcome.Ok(id, false), s.removeLocal(a, id, 7L))
        assertEquals(NamedOutcome.Ok(id, false), s.moveItem(a, id, tg(1), 0))
        assertEquals(saves, store.saves)
    }

    @Test fun refusalsComeBackAsTheirOwnOutcomesAndWriteNothing() {
        val store = MemoryStore()
        val s = session(store)
        s.open(a)
        val id = idOf(s.createList(a, "Gym"))
        val saves = store.saves
        assertEquals(NamedOutcome.DuplicateName, s.createList(a, "gym"))
        assertEquals(NamedOutcome.Refused(NamedRefusal.BAD_NAME), s.createList(a, "  "))
        assertEquals(NamedOutcome.Refused(NamedRefusal.NO_SUCH_LIST), s.deleteList(a, ListId("dev-9", 9L)))
        assertEquals(NamedOutcome.Refused(NamedRefusal.NO_SUCH_ITEM), s.moveItem(a, id, tg(5), 0))
        for (n in 2..NamedPlaylistState.MAX_LISTS) s.createList(a, "Playlist $n")
        val full = store.saves
        assertEquals(NamedOutcome.Full(NamedRefusal.LIST_LIMIT), s.createList(a, "One too many"))
        assertEquals(full, store.saves)
        assertTrue(saves < full)
    }

    @Test fun aFullSongLimitAndAFullOutboxAreReportedNotCutSilently() {
        val store = MemoryStore()
        val s = session(store)
        s.open(a)
        val id = idOf(s.createList(a, "Big"))
        for (n in 1L..NamedPlaylistState.MAX_ITEMS.toLong()) assertTrue(s.addTelegram(a, id, tg(n)) is NamedOutcome.Ok)
        assertEquals(NamedOutcome.Full(NamedRefusal.ITEM_LIMIT), s.addTelegram(a, id, tg(5000)))
        assertEquals(NamedPlaylistState.MAX_ITEMS, s.snapshot().single().count)
        // 1001 operations are queued: fill the queue up to its limit through the sync door, then ask for one more
        val view = checkNotNull(s.view(a))
        val filler = view.state.outbox.first()
        val update = s.update(a, view.epoch) { it.copy(outbox = List(NamedPlaylistState.MAX_OUTBOX) { filler }) }
        assertTrue(update is NamedUpdateResult.Committed)
        val before = store.files.getValue(a.value)
        assertEquals(NamedOutcome.OutboxFull, s.renameList(a, id, "Another name"))
        assertEquals(NamedOutcome.OutboxFull, s.removeTelegram(a, id, tg(1)))
        assertEquals(before, store.files.getValue(a.value))
        assertEquals("Big", s.snapshot().single().name)
        // songs of the device itself need no outbox
        assertEquals(NamedOutcome.Full(NamedRefusal.ITEM_LIMIT), s.addLocal(a, id, 1L, LocalFingerprint.of("x", 1, 1)))
    }

    @Test fun aFailedWriteKeepsTheOldStateInMemoryAndOnDisk() {
        val store = MemoryStore()
        val s = session(store)
        s.open(a)
        val id = idOf(s.createList(a, "Gym"))
        val before = store.files.getValue(a.value)
        store.failSaves = true
        assertEquals(NamedOutcome.Failed, s.addTelegram(a, id, tg(1)))
        assertEquals(NamedOutcome.Failed, s.renameList(a, id, "Other"))
        assertEquals(before, store.files.getValue(a.value))
        assertEquals(0, s.snapshot().single().count)
        assertEquals("Gym", s.snapshot().single().name)
        store.failSaves = false
        assertTrue(s.addTelegram(a, id, tg(1)) is NamedOutcome.Ok)
        assertEquals(listOf<PlaylistItemId>(tg(1)), s.entries(a, id)?.map { it.item })
        // the operation of the failed attempt was not used up: the counters did not move
        assertEquals(listOf("dev-1#1", "dev-1#2"), store.files.getValue(a.value).outbox.map { it.op.opId })
    }

    // ---- a file that can not be read ---------------------------------------------------------------

    @Test fun anUnreadableFileBlocksEveryWriteAndIsNotReplaced() {
        val store = MemoryStore()
        store.unreadable = PlaylistUnreadableReason.DECRYPT
        val s = session(store)
        assertEquals(NamedOpenResult.Blocked(PlaylistUnreadableReason.DECRYPT), s.open(a))
        assertTrue(s.snapshot().isEmpty())
        assertNull(s.snapshotFor(a))
        assertNull(s.view(a))
        val id = ListId("dev-1", 1L)
        for (outcome in listOf(
            s.createList(a, "x"), s.renameList(a, id, "x"), s.deleteList(a, id), s.addTelegram(a, id, tg(1)),
            s.removeTelegram(a, id, tg(1)), s.moveItem(a, id, tg(1), 0), s.addLocal(a, id, 1L, LocalFingerprint.of("x", 1, 1)),
            s.removeLocal(a, id, 1L), s.moveLocal(a, id, 1L, 0), s.rebalance(a, id),
        )) {
            assertEquals(NamedOutcome.Blocked(PlaylistUnreadableReason.DECRYPT), outcome)
        }
        assertEquals(0, store.saves)
        assertTrue(store.files.isEmpty())
    }

    @Test fun aReadErrorThatWasOnlyTransientCanBeRetriedByTheNextChange() {
        val store = MemoryStore()
        store.unreadable = PlaylistUnreadableReason.IO
        val s = session(store)
        assertTrue(s.open(a) is NamedOpenResult.Blocked)
        assertEquals(NamedOutcome.Blocked(PlaylistUnreadableReason.IO), s.createList(a, "x"))
        store.unreadable = null
        assertTrue(s.createList(a, "x") is NamedOutcome.Ok)
        assertEquals(listOf("x"), s.snapshot().map { it.name })
    }

    @Test fun aFileThatTurnsUnreadableDuringAWriteBlocksTheSession() {
        val store = MemoryStore()
        val s = session(store)
        s.open(a)
        s.createList(a, "Một")
        store.unreadable = PlaylistUnreadableReason.FORMAT
        assertEquals(NamedOutcome.Blocked(PlaylistUnreadableReason.FORMAT), s.createList(a, "Hai"))
        assertNull(s.snapshotFor(a))
        assertEquals(NamedOutcome.Blocked(PlaylistUnreadableReason.FORMAT), s.createList(a, "Ba"))
    }

    @Test fun withTheRealStoreADamagedFileStaysByteForByteAndEverythingIsRefused() {
        val cipher = TestAesGcmCipher(5)
        val store = NamedPlaylistStore(folder.root, cipher, TestPlainFileIo)
        val first = session(store)
        first.open(a)
        first.createList(a, "Một")
        val file = File(File(folder.root, "tg_named_playlists_v1"), TelegramAccountStoragePolicy.directoryName(a) + ".bin")
        val damaged = file.readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        file.writeBytes(damaged)
        val second = session(NamedPlaylistStore(folder.root, cipher, TestPlainFileIo))
        assertEquals(NamedOpenResult.Blocked(PlaylistUnreadableReason.DECRYPT), second.open(a))
        assertEquals(NamedOutcome.Blocked(PlaylistUnreadableReason.DECRYPT), second.createList(a, "Hai"))
        assertEquals(NamedOutcome.Blocked(PlaylistUnreadableReason.DECRYPT), second.deleteList(a, ListId("dev-1", 1L)))
        assertArrayEquals(damaged, file.readBytes())
        // the Yêu thích files are not part of this: no V1/V2 directory was created
        assertFalse(File(folder.root, "tg_playlists_v2").exists())
        assertFalse(File(folder.root, "tg_playlists_v1").exists())
    }

    @Test fun withTheRealStoreOpeningAnEmptyAccountCreatesNoFile() {
        val s = session(NamedPlaylistStore(folder.root, TestAesGcmCipher(5), TestPlainFileIo))
        s.open(a)
        assertFalse(File(folder.root, "tg_named_playlists_v1").exists() && File(folder.root, "tg_named_playlists_v1").listFiles().orEmpty().isNotEmpty())
        s.createList(a, "Một")
        assertEquals(1, File(folder.root, "tg_named_playlists_v1").listFiles().orEmpty().size)
    }

    // ---- the sync door -----------------------------------------------------------------------------

    @Test fun aStaleUpdateIsRefusedWhateverHappenedSinceTheView() {
        val store = MemoryStore()
        val s = session(store)
        s.open(a)
        val id = idOf(s.createList(a, "Một"))
        val view = checkNotNull(s.view(a))
        val remote = NamedOp("dev-9#1", NamedOpType.LCREATE, ListId("dev-9", 1L), null, 5L, setOf(Dot("dev-9", 1L)), "Từ xa", null)
        // the same account is opened again: the epoch moves on
        s.open(a)
        val saves = store.saves
        assertEquals(NamedUpdateResult.Stale, s.update(a, view.epoch) { it.applyRemote(remote) })
        assertEquals(saves, store.saves)
        assertEquals(listOf("Một"), s.snapshot().map { it.name })
        // another account, or a closed session, is stale too
        val view2 = checkNotNull(s.view(a))
        s.open(b)
        assertEquals(NamedUpdateResult.Stale, s.update(a, view2.epoch) { it.applyRemote(remote) })
        s.open(a)
        s.close()
        assertEquals(NamedUpdateResult.Stale, s.update(a, s.currentEpoch() - 1) { it })
        assertEquals(NamedUpdateResult.Stale, s.update(a, s.currentEpoch()) { it }) // user is null after close
        assertNotEquals(view.epoch, s.currentEpoch())
    }

    @Test fun anUpdateOfTheCurrentViewIsStoredThenAdopted() {
        val store = MemoryStore()
        val s = session(store)
        s.open(a)
        s.createList(a, "Một")
        val view = checkNotNull(s.view(a))
        val remote = NamedOp("dev-9#1", NamedOpType.LCREATE, ListId("dev-9", 1L), null, 9L, setOf(Dot("dev-9", 1L)), "Từ xa", null)
        val result = s.update(a, view.epoch) { it.applyRemote(remote) }
        assertTrue(result is NamedUpdateResult.Committed)
        assertEquals(listOf("Một", "Từ xa"), s.snapshot().map { it.name })
        assertEquals(store.files.getValue(a.value), (result as NamedUpdateResult.Committed).state)
        val saves = store.saves
        val noChange = s.update(a, view.epoch) { it }
        assertEquals("a no-change update writes nothing", saves, store.saves)
        assertEquals(NamedUpdateResult.Committed(result.state), noChange)
    }

    @Test fun anUpdateThatThrowsOrCanNotBeWrittenChangesNothing() {
        val store = MemoryStore()
        val s = session(store)
        s.open(a)
        s.createList(a, "Một")
        val view = checkNotNull(s.view(a))
        val onDisk = store.files.getValue(a.value)
        assertEquals(NamedUpdateResult.Failed, s.update(a, view.epoch) { throw IllegalStateException("boom") })
        store.failSaves = true
        assertEquals(NamedUpdateResult.Failed, s.update(a, view.epoch) { it.copy(lamport = it.lamport + 1) })
        store.failSaves = false
        assertEquals(onDisk, store.files.getValue(a.value))
        assertEquals(view.state, s.view(a)?.state)
        // a state of another account is never stored
        assertEquals(NamedUpdateResult.Failed, s.update(a, view.epoch) { it.copy(accountId = b.value) })
        assertEquals(onDisk, store.files.getValue(a.value))
    }

    @Test fun anUpdateOnAnUnreadableFileBlocksTheSession() {
        val store = MemoryStore()
        val s = session(store)
        s.open(a)
        s.createList(a, "Một")
        val view = checkNotNull(s.view(a))
        store.unreadable = PlaylistUnreadableReason.DECRYPT
        assertEquals(NamedUpdateResult.Failed, s.update(a, view.epoch) { it.copy(lamport = it.lamport + 1) })
        assertNull(s.view(a))
        assertEquals(NamedUpdateResult.NotOpen, s.update(a, view.epoch) { it })
    }

    // ---- wiring -----------------------------------------------------------------------------------

    @Test fun theRuntimeGivesOneNamedSessionPerProcessAndLeavesTheOldOnesAlone() {
        val one = TelegramPlaylistRuntime.namedSession(folder.root)
        assertSame(one, TelegramPlaylistRuntime.namedSession(folder.root))
        assertTrue(one.snapshot().isEmpty())
        assertEquals(0, folder.root.listFiles().orEmpty().size)
    }
}
