package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Random

/** Nhiều Playlist có tên, S2: the pure model (CRDT convergence, names, order, limits, local songs). */
class NamedPlaylistModelTest {
    private val account = 1001L

    /** One device with the operations it made, so tests can hand them to other devices in any order. */
    private class Replica(device: String, account: Long) {
        var s: NamedPlaylistState = NamedPlaylistState.empty(account, device)
        val made = ArrayList<NamedOp>()

        fun run(m: NamedMutation): NamedMutation {
            assertNull("refused: ${m.refusal}", m.refusal)
            s = m.state
            made += m.ops
            return m
        }

        fun receive(ops: List<NamedOp>) {
            for (op in ops) s = s.applyRemote(op)
        }
    }

    private fun replica(device: String) = Replica(device, account)
    private fun tg(n: Long) = PlaylistItemId.Telegram(-1001234L, n)
    private fun order(s: NamedPlaylistState, id: ListId) = s.entries(id).map { it.item }

    private fun names(s: NamedPlaylistState) = s.lists().map { it.name }

    // ---- ids, names, fingerprints -----------------------------------------------------------------

    @Test fun listIdRoundTripsAndRejectsBadText() {
        val id = ListId("dev-aaa", 12L)
        assertEquals("dev-aaa.12", id.key)
        assertEquals(id, ListId.parse(id.key))
        for (bad in listOf("", "dev-aaa", "dev-aaa.", ".5", "dev-aaa.0", "dev-aaa.-1", "dev aaa.1", "dev-aaa.x")) {
            assertNull("bad=$bad", ListId.parse(bad))
        }
    }

    @Test fun namesAreTrimmedAndLimitedTo60CodePoints() {
        assertEquals("Nhạc Xưa", NamedPlaylistNames.clean("  Nhạc Xưa \n"))
        assertEquals("🎵".repeat(60), NamedPlaylistNames.clean("🎵".repeat(60)))
        assertNull(NamedPlaylistNames.clean("🎵".repeat(61)))
        assertEquals("a".repeat(60), NamedPlaylistNames.clean("a".repeat(60)))
        assertNull(NamedPlaylistNames.clean("a".repeat(61)))
        assertNull(NamedPlaylistNames.clean(""))
        assertNull(NamedPlaylistNames.clean("   "))
        assertNull(NamedPlaylistNames.clean("a\tb"))
        assertNull(NamedPlaylistNames.clean("a\nb"))
        assertNull(NamedPlaylistNames.clean("a\u0000b"))
        assertNull("unpaired surrogate", NamedPlaylistNames.clean("a\uD83Db"))
    }

    @Test fun nameComparisonIgnoresAccentsCaseAndExtraSpaces() {
        val key = NamedPlaylistNames.comparisonKey("Đêm  Nhạc Xưa")
        assertEquals(key, NamedPlaylistNames.comparisonKey("dem nhac xua"))
        assertEquals(key, NamedPlaylistNames.comparisonKey("  ĐÊM NHẠC XƯA "))
        assertNotEquals(key, NamedPlaylistNames.comparisonKey("dem nhac"))
    }

    @Test fun localFingerprintIsStableShortAndDependsOnEveryFact() {
        val base = LocalFingerprint.of("Bài 1.mp3", 215_000L, 5_123_456L)
        assertEquals(base, LocalFingerprint.of("Bài 1.mp3", 215_000L, 5_123_456L))
        assertTrue(LocalFingerprint.isValid(base))
        assertEquals(16, base.length)
        assertNotEquals(base, LocalFingerprint.of("Bài 2.mp3", 215_000L, 5_123_456L))
        assertNotEquals(base, LocalFingerprint.of("Bài 1.mp3", 215_001L, 5_123_456L))
        assertNotEquals(base, LocalFingerprint.of("Bài 1.mp3", 215_000L, 5_123_457L))
        assertFalse(LocalFingerprint.isValid(""))
        assertFalse(LocalFingerprint.isValid("/storage/emulated/0/Music/a.mp3"))
    }

    @Test fun operationsRefuseBadShapes() {
        val id = ListId("dev-a", 1L)
        val dot = Dot("dev-a", 1L)
        fun bad(block: () -> Unit) {
            try {
                block()
                fail("should have been refused")
            } catch (_: IllegalArgumentException) {
            }
        }
        NamedOp("dev-a#1", NamedOpType.LCREATE, id, null, 1L, setOf(dot), "Tên", null)
        bad { NamedOp("dev-a#1", NamedOpType.LCREATE, id, null, 1L, setOf(dot), "", null) }
        bad { NamedOp("dev-a#1", NamedOpType.LCREATE, id, null, 1L, setOf(Dot("dev-a", 2L)), "Tên", null) }
        bad { NamedOp("dev-a#1", NamedOpType.LCREATE, ListId("dev-a", 2L), null, 1L, setOf(dot), "Tên", null) }
        bad { NamedOp("dev-a#1", NamedOpType.LCREATE, id, tg(1), 1L, setOf(dot), "Tên", null) }
        bad { NamedOp("dev-a#1", NamedOpType.LRENAME, id, null, 1L, setOf(dot), "Tên", null) }
        bad { NamedOp("dev-a#1", NamedOpType.LDELETE, id, null, 1L, emptySet(), null, null) }
        bad { NamedOp("dev-a#1", NamedOpType.IADD, id, tg(1), 1L, setOf(dot), null, "10") }
        bad { NamedOp("dev-a#1", NamedOpType.IADD, id, null, 1L, setOf(dot), null, "V") }
        bad { NamedOp("dev-a#1", NamedOpType.IREMOVE, id, tg(1), 1L, emptySet(), null, null) }
        bad { NamedOp("dev-a#1", NamedOpType.IMOVE, id, tg(1), 1L, setOf(dot), null, "V") }
        bad { NamedOp("dev-a#1", NamedOpType.IMOVE, id, tg(1), 0L, emptySet(), null, "V") }
        bad { NamedOp("dev-a#x", NamedOpType.IMOVE, id, tg(1), 1L, emptySet(), null, "V") }
        bad { NamedOp("dev-a#1", NamedOpType.LDELETE, id, null, 1L, (1L..33L).map { Dot("dev-a", it) }.toSet(), null, null) }
    }

    // ---- one device: create, rename, delete ------------------------------------------------------

    @Test fun createRenameDeleteOnOneDevice() {
        val a = replica("dev-a")
        val created = a.run(a.s.createList("  Nhạc đi xe "))
        val id = checkNotNull(created.listId)
        assertEquals(1, created.ops.size)
        assertEquals(NamedOpType.LCREATE, created.ops[0].type)
        assertEquals(listOf("Nhạc đi xe"), names(a.s))
        assertEquals(1, a.s.outbox.size)
        assertEquals(OutboxStatus.PENDING, a.s.outbox[0].status)
        a.run(a.s.renameList(id, "Nhạc đường dài"))
        assertEquals(listOf("Nhạc đường dài"), names(a.s))
        assertEquals("Nhạc đường dài", a.s.nameOf(id))
        a.run(a.s.deleteList(id))
        assertTrue(a.s.lists().isEmpty())
        assertFalse(a.s.exists(id))
        assertNull(a.s.nameOf(id))
        assertEquals(3, a.s.outbox.size)
        assertEquals(listOf(1L, 2L, 3L), a.s.outbox.map { it.op.seq })
    }

    @Test fun renamingToTheSameNameMakesNoOperationAndCaseChangeIsAllowed() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("Rock")).listId)
        val same = a.s.renameList(id, " Rock ")
        assertNull(same.refusal)
        assertTrue(same.ops.isEmpty())
        assertEquals(a.s, same.state)
        val recased = a.run(a.s.renameList(id, "ROCK"))
        assertEquals(1, recased.ops.size)
        assertEquals(listOf("ROCK"), names(a.s))
    }

    @Test fun duplicateNameIsRefusedIgnoringAccentsAndCase() {
        val a = replica("dev-a")
        a.run(a.s.createList("Đêm nhạc"))
        assertEquals(NamedRefusal.DUPLICATE_NAME, a.s.createList("dem NHAC").refusal)
        val other = checkNotNull(a.run(a.s.createList("Khác")).listId)
        assertEquals(NamedRefusal.DUPLICATE_NAME, a.s.renameList(other, "ĐÊM  nhạc").refusal)
        assertEquals(NamedRefusal.BAD_NAME, a.s.createList("   ").refusal)
        assertEquals(NamedRefusal.BAD_NAME, a.s.renameList(other, "x".repeat(61)).refusal)
        assertEquals(listOf("Đêm nhạc", "Khác"), names(a.s))
    }

    @Test fun aDeletedPlaylistFreesItsNameAndAMadeAgainPlaylistIsANewOne() {
        val a = replica("dev-a")
        val first = checkNotNull(a.run(a.s.createList("Gym")).listId)
        a.run(a.s.deleteList(first))
        val second = checkNotNull(a.run(a.s.createList("Gym")).listId)
        assertNotEquals(first, second)
        assertTrue(a.s.exists(second))
        assertFalse(a.s.exists(first))
    }

    @Test fun unknownOrDeletedPlaylistIsRefused() {
        val a = replica("dev-a")
        val ghost = ListId("dev-zzz", 9L)
        assertEquals(NamedRefusal.NO_SUCH_LIST, a.s.renameList(ghost, "x").refusal)
        assertEquals(NamedRefusal.NO_SUCH_LIST, a.s.deleteList(ghost).refusal)
        assertEquals(NamedRefusal.NO_SUCH_LIST, a.s.addTelegram(ghost, tg(1)).refusal)
        assertEquals(NamedRefusal.NO_SUCH_LIST, a.s.removeTelegram(ghost, tg(1)).refusal)
        assertEquals(NamedRefusal.NO_SUCH_LIST, a.s.moveItem(ghost, tg(1), 0).refusal)
        assertEquals(NamedRefusal.NO_SUCH_LIST, a.s.addLocal(ghost, 1L, LocalFingerprint.of("a", 1, 1)).refusal)
        assertEquals(NamedRefusal.NO_SUCH_LIST, a.s.rebalance(ghost).refusal)
        assertTrue(a.s.entries(ghost).isEmpty())
    }

    // ---- one device: songs and order -------------------------------------------------------------

    @Test fun addingKeepsOrderAndNeverDuplicates() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        for (n in 1L..5L) a.run(a.s.addTelegram(id, tg(n)))
        assertEquals((1L..5L).map { tg(it) }, order(a.s, id))
        val again = a.s.addTelegram(id, tg(3))
        assertNull(again.refusal)
        assertTrue(again.ops.isEmpty())
        assertEquals(a.s, again.state)
        assertEquals(5, a.s.itemCount(id))
        assertTrue(a.s.contains(id, tg(3)))
        assertFalse(a.s.contains(id, tg(9)))
    }

    @Test fun removeThenAddAgainWorksAndRemovingAnAbsentSongDoesNothing() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        a.run(a.s.addTelegram(id, tg(1)))
        a.run(a.s.addTelegram(id, tg(2)))
        val removed = a.run(a.s.removeTelegram(id, tg(1)))
        assertEquals(NamedOpType.IREMOVE, removed.ops.single().type)
        assertEquals(listOf<PlaylistItemId>(tg(2)), order(a.s, id))
        val absent = a.s.removeTelegram(id, tg(1))
        assertTrue(absent.ops.isEmpty())
        a.run(a.s.addTelegram(id, tg(1)))
        assertEquals(listOf<PlaylistItemId>(tg(2), tg(1)), order(a.s, id))
    }

    @Test fun moveItemPlacesAtTheGivenIndex() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        for (n in 1L..4L) a.run(a.s.addTelegram(id, tg(n)))
        val toFront = a.run(a.s.moveItem(id, tg(4), 0))
        assertEquals(1, toFront.ops.size)
        assertEquals(NamedOpType.IMOVE, toFront.ops[0].type)
        assertEquals(listOf<PlaylistItemId>(tg(4), tg(1), tg(2), tg(3)), order(a.s, id))
        a.run(a.s.moveItem(id, tg(1), 2))
        assertEquals(listOf<PlaylistItemId>(tg(4), tg(2), tg(1), tg(3)), order(a.s, id))
        a.run(a.s.moveItem(id, tg(4), 99))
        assertEquals(listOf<PlaylistItemId>(tg(2), tg(1), tg(3), tg(4)), order(a.s, id))
        a.run(a.s.moveItem(id, tg(3), -5))
        assertEquals(listOf<PlaylistItemId>(tg(3), tg(2), tg(1), tg(4)), order(a.s, id))
    }

    @Test fun movingToWhereItAlreadyIsMakesNoOperationAndUnknownSongIsRefused() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        for (n in 1L..3L) a.run(a.s.addTelegram(id, tg(n)))
        val same = a.s.moveItem(id, tg(2), 1)
        assertTrue(same.ops.isEmpty())
        assertEquals(a.s, same.state)
        assertEquals(NamedRefusal.NO_SUCH_ITEM, a.s.moveItem(id, tg(9), 0).refusal)
        assertEquals(NamedRefusal.NO_SUCH_ITEM, a.s.moveLocal(id, 9L, 0).refusal)
    }

    @Test fun veryManyInsertsInOneSpotRespaceTheListAndKeepTheOrder() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        a.run(a.s.addTelegram(id, tg(1)))
        a.run(a.s.addTelegram(id, tg(2)))
        for (n in 3L..300L) {
            a.run(a.s.addTelegram(id, tg(n)))
            a.run(a.s.moveItem(id, tg(n), 1))
        }
        // 1 stays first, every newer song is put right after it, and 2 ends last.
        val want = listOf<PlaylistItemId>(tg(1)) + (300L downTo 3L).map { tg(it) } + tg(2)
        assertEquals(want, order(a.s, id))
        assertTrue("a respace must have happened", a.made.count { it.type == NamedOpType.IMOVE } > 300 + 100)
        assertTrue(a.s.entries(id).all { FractionalIndex.isValid(it.pos) })
        val b = replica("dev-b")
        b.receive(a.made)
        assertEquals(want, order(b.s, id))
    }

    // ---- songs of the device itself --------------------------------------------------------------

    @Test fun localSongsLiveOnThisDeviceOnlyAndMakeNoOperation() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        a.run(a.s.addTelegram(id, tg(1)))
        val counter = a.s.opCounter
        val lamport = a.s.lamport
        val outbox = a.s.outbox
        val fp = LocalFingerprint.of("Bài máy", 1000, 2000)
        val added = a.run(a.s.addLocal(id, 77L, fp))
        assertTrue(added.ops.isEmpty())
        val localMove = a.run(a.s.moveLocal(id, 77L, 0))
        assertTrue(localMove.ops.isEmpty())
        assertEquals(PlaylistItemId.Local(77L), order(a.s, id).first())
        assertEquals(fp, a.s.entries(id).first().fingerprint)
        assertNull(a.s.entries(id).first { it.item is PlaylistItemId.Telegram }.fingerprint)
        assertEquals(counter, a.s.opCounter)
        assertEquals(lamport, a.s.lamport)
        assertEquals(outbox, a.s.outbox)
        assertTrue(a.s.outbox.all { it.op.item !is PlaylistItemId.Local })
        assertTrue(a.made.none { it.item is PlaylistItemId.Local })
        val removed = a.run(a.s.removeLocal(id, 77L))
        assertTrue(removed.ops.isEmpty())
        assertFalse(a.s.contains(id, PlaylistItemId.Local(77L)))
        assertTrue(a.s.localItems.isEmpty())
    }

    @Test fun localSongsInterleaveWithTelegramSongsByPositionAndOtherDevicesNeverSeeThem() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        a.run(a.s.addTelegram(id, tg(1)))
        a.run(a.s.addLocal(id, 5L, LocalFingerprint.of("x", 1, 1)))
        a.run(a.s.addTelegram(id, tg(2)))
        a.run(a.s.addLocal(id, 6L, LocalFingerprint.of("y", 2, 2)))
        assertEquals(
            listOf<PlaylistItemId>(tg(1), PlaylistItemId.Local(5L), tg(2), PlaylistItemId.Local(6L)),
            order(a.s, id)
        )
        a.run(a.s.moveLocal(id, 6L, 1))
        assertEquals(
            listOf<PlaylistItemId>(tg(1), PlaylistItemId.Local(6L), PlaylistItemId.Local(5L), tg(2)),
            order(a.s, id)
        )
        assertEquals(4, a.s.itemCount(id))
        val b = replica("dev-b")
        b.receive(a.made)
        assertEquals(listOf<PlaylistItemId>(tg(1), tg(2)), order(b.s, id))
        assertEquals(2, b.s.itemCount(id))
        assertTrue(b.s.localItems.isEmpty())
    }

    @Test fun addingALocalSongTwiceIsOneEntryAndDeletingThePlaylistDropsItsLocalSongs() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        val fp = LocalFingerprint.of("x", 1, 1)
        a.run(a.s.addLocal(id, 5L, fp))
        val again = a.s.addLocal(id, 5L, fp)
        assertEquals(a.s, again.state)
        assertEquals(1, a.s.localItems[id]?.size)
        a.run(a.s.deleteList(id))
        assertTrue(a.s.localItems.isEmpty())
    }

    @Test fun aRemoteDeleteDropsLocalSongsOfThatPlaylist() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        b.receive(a.made)
        b.run(b.s.addLocal(id, 5L, LocalFingerprint.of("x", 1, 1)))
        assertEquals(1, b.s.itemCount(id))
        a.run(a.s.deleteList(id))
        b.receive(a.made)
        assertTrue(b.s.localItems.isEmpty())
        assertEquals(0, b.s.itemCount(id))
    }

    // ---- limits ---------------------------------------------------------------------------------

    @Test fun atMost100PlaylistsAreAlive() {
        val a = replica("dev-a")
        val ids = ArrayList<ListId>()
        for (n in 1..NamedPlaylistState.MAX_LISTS) ids += checkNotNull(a.run(a.s.createList("Playlist $n")).listId)
        val over = a.s.createList("Playlist 101")
        assertEquals(NamedRefusal.LIST_LIMIT, over.refusal)
        assertEquals(a.s, over.state)
        a.run(a.s.deleteList(ids[10]))
        a.run(a.s.createList("Playlist 101"))
        assertEquals(NamedPlaylistState.MAX_LISTS, a.s.listCount())
    }

    @Test fun atMost1000SongsPerPlaylistCountingLocalOnes() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("Big")).listId)
        for (n in 1L..999L) a.run(a.s.addTelegram(id, tg(n)))
        a.run(a.s.addLocal(id, 1L, LocalFingerprint.of("x", 1, 1)))
        assertEquals(NamedPlaylistState.MAX_ITEMS, a.s.itemCount(id))
        val overT = a.s.addTelegram(id, tg(5000))
        assertEquals(NamedRefusal.ITEM_LIMIT, overT.refusal)
        assertEquals(a.s, overT.state)
        assertEquals(NamedRefusal.ITEM_LIMIT, a.s.addLocal(id, 2L, LocalFingerprint.of("y", 1, 1)).refusal)
        assertEquals("a song already inside is not an addition", null, a.s.addTelegram(id, tg(7)).refusal)
        a.run(a.s.removeTelegram(id, tg(7)))
        a.run(a.s.addTelegram(id, tg(5000)))
        assertEquals(NamedPlaylistState.MAX_ITEMS, a.s.itemCount(id))
    }

    @Test fun aFullOutboxRefusesLoudlyAndChangesNothing() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        a.run(a.s.addTelegram(id, tg(1)))
        val filler = a.s.outbox.first()
        val full = a.s.copy(outbox = List(NamedPlaylistState.MAX_OUTBOX) { filler })
        val attempts = listOf<(NamedPlaylistState) -> Any>(
            { it.createList("Another") },
            { it.renameList(id, "Renamed") },
            { it.deleteList(id) },
            { it.addTelegram(id, tg(2)) },
            { it.removeTelegram(id, tg(1)) },
            { it.addTelegram(id, tg(3)).state.moveItem(id, tg(1), 1) },
        )
        for ((i, attempt) in attempts.withIndex()) {
            try {
                attempt(full)
                fail("attempt $i must throw")
            } catch (_: OutboxFullException) {
            }
        }
        assertEquals(NamedPlaylistState.MAX_OUTBOX, full.outbox.size)
        assertEquals(1, full.itemCount(id))
        // Local songs do not need the outbox.
        assertNull(full.addLocal(id, 1L, LocalFingerprint.of("x", 1, 1)).refusal)
    }

    // ---- two or three devices: convergence ------------------------------------------------------

    private fun converge(vararg parts: List<NamedOp>): NamedPlaylistState {
        var s = NamedPlaylistState.empty(account, "dev-observer")
        for (part in parts) for (op in part) s = s.applyRemote(op)
        return s
    }

    private fun assertSameContent(x: NamedPlaylistState, y: NamedPlaylistState) {
        assertEquals(x.lists, y.lists)
        assertEquals(x.lists().map { it.id to it.name }, y.lists().map { it.id to it.name })
        for (v in x.lists()) assertEquals(x.entries(v.id).map { it.item }, y.entries(v.id).map { it.item })
    }

    @Test fun twoDevicesThatExchangeOperationsHaveTheSameContent() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val id = checkNotNull(a.run(a.s.createList("Chung")).listId)
        a.run(a.s.addTelegram(id, tg(1)))
        b.receive(a.made)
        b.run(b.s.addTelegram(id, tg(2)))
        val bFresh = b.made.toList()
        a.run(a.s.addTelegram(id, tg(3)))
        a.receive(bFresh)
        b.receive(a.made)
        assertSameContent(a.s, b.s)
        assertEquals(setOf<PlaylistItemId>(tg(1), tg(2), tg(3)), order(a.s, id).toSet())
        assertEquals(order(a.s, id), order(b.s, id))
    }

    @Test fun applyingTheSameOperationsAgainOrInAnyOrderGivesTheSameResult() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val id = checkNotNull(a.run(a.s.createList("Chung")).listId)
        b.receive(a.made)
        a.run(a.s.addTelegram(id, tg(1)))
        a.run(a.s.addTelegram(id, tg(2)))
        a.run(a.s.moveItem(id, tg(2), 0))
        b.run(b.s.addTelegram(id, tg(3)))
        b.run(b.s.renameList(id, "Đã đổi"))
        b.run(b.s.removeTelegram(id, tg(3)))
        val once = converge(a.made, b.made.drop(0))
        val reversed = converge((a.made + b.made).reversed())
        val duplicated = converge(a.made, b.made, a.made, b.made, (a.made + b.made).reversed())
        assertSameContent(once, reversed)
        assertSameContent(once, duplicated)
        // applying an operation already applied changes nothing at all
        val again = once.applyRemote(a.made.first())
        assertEquals(once.lists, again.lists)
    }

    @Test fun addRemoveAddWithAConcurrentRemoveKeepsTheNewerAdd() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        a.run(a.s.addTelegram(id, tg(1)))
        b.receive(a.made)
        // concurrently: A removes and adds the song again (a new tag); B removes the old tag only
        a.run(a.s.removeTelegram(id, tg(1)))
        a.run(a.s.addTelegram(id, tg(1)))
        b.run(b.s.removeTelegram(id, tg(1)))
        a.receive(b.made)
        b.receive(a.made)
        assertTrue(a.s.contains(id, tg(1)))
        assertTrue(b.s.contains(id, tg(1)))
        assertSameContent(a.s, b.s)
    }

    @Test fun twoDevicesThatBothRemoveTheSameSongEndWithoutIt() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        a.run(a.s.addTelegram(id, tg(1)))
        b.receive(a.made)
        a.run(a.s.removeTelegram(id, tg(1)))
        b.run(b.s.removeTelegram(id, tg(1)))
        a.receive(b.made)
        b.receive(a.made)
        assertFalse(a.s.contains(id, tg(1)))
        assertSameContent(a.s, b.s)
    }

    @Test fun concurrentRenamesAreDecidedBySeqThenDeviceOnBothSides() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val id = checkNotNull(a.run(a.s.createList("Gốc")).listId)
        b.receive(a.made)
        a.run(a.s.renameList(id, "Từ A"))
        b.run(b.s.renameList(id, "Từ B"))
        assertEquals(a.made.last().seq, b.made.last().seq) // a real tie in seq
        a.receive(b.made)
        b.receive(a.made)
        assertEquals("Từ B", a.s.nameOf(id)) // "dev-b" > "dev-a"
        assertEquals("Từ B", b.s.nameOf(id))
        // a later rename (higher seq) wins over any tie
        a.run(a.s.renameList(id, "Mới nhất"))
        b.receive(a.made.takeLast(1))
        assertEquals("Mới nhất", b.s.nameOf(id))
    }

    @Test fun aRenameNeverBringBackADeletedPlaylistWhateverTheOrder() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        b.receive(a.made)
        a.run(a.s.deleteList(id))
        b.run(b.s.renameList(id, "Đổi tên"))
        val all = a.made + b.made
        for (permutation in listOf(all, all.reversed(), all.sortedBy { it.type.ordinal }, all.sortedByDescending { it.type.ordinal })) {
            val s = converge(permutation)
            assertFalse(s.exists(id))
            assertTrue(s.lists().isEmpty())
        }
    }

    @Test fun deleteDuringConcurrentAddKeepsThePlaylistDeletedAndTheSongHidden() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        b.receive(a.made)
        a.run(a.s.deleteList(id))
        b.run(b.s.addTelegram(id, tg(1)))
        a.receive(b.made)
        b.receive(a.made)
        for (s in listOf(a.s, b.s)) {
            assertFalse(s.exists(id))
            assertTrue(s.entries(id).isEmpty())
            assertEquals(0, s.itemCount(id))
            assertTrue("the song is kept in the data, only hidden", s.lists[id]?.items?.containsKey(tg(1)) == true)
        }
        assertSameContent(a.s, b.s)
    }

    @Test fun concurrentMovesOfOneSongAreDecidedBySeqThenDeviceOnBothSides() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        for (n in 1L..3L) a.run(a.s.addTelegram(id, tg(n)))
        b.receive(a.made)
        a.run(a.s.moveItem(id, tg(3), 0))
        b.run(b.s.moveItem(id, tg(3), 1))
        a.receive(b.made)
        b.receive(a.made)
        assertSameContent(a.s, b.s)
        assertEquals(listOf<PlaylistItemId>(tg(1), tg(3), tg(2)), order(a.s, id)) // B ("dev-b") wins the tie
    }

    @Test fun concurrentAppendsAtTheTailKeepAStableOrderAndASongCanBeMovedBetweenThem() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        a.run(a.s.addTelegram(id, tg(1)))
        b.receive(a.made)
        a.run(a.s.addTelegram(id, tg(10)))
        b.run(b.s.addTelegram(id, tg(11)))
        a.receive(b.made)
        b.receive(a.made)
        assertEquals(listOf<PlaylistItemId>(tg(1), tg(10), tg(11)), order(a.s, id))
        assertEquals(order(a.s, id), order(b.s, id))
        // tg(10) and tg(11) share a position now; a song put between them respaces the whole playlist
        a.run(a.s.addTelegram(id, tg(12)))
        val moved = a.run(a.s.moveItem(id, tg(12), 2))
        assertTrue(moved.ops.size > 1)
        assertTrue(moved.ops.all { it.type == NamedOpType.IMOVE })
        assertEquals(listOf<PlaylistItemId>(tg(1), tg(10), tg(12), tg(11)), order(a.s, id))
        b.receive(a.made)
        assertEquals(order(a.s, id), order(b.s, id))
        assertSameContent(a.s, b.s)
    }

    @Test fun listsAreShownInCreationOrderOnEveryDevice() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val first = checkNotNull(a.run(a.s.createList("Một")).listId)
        b.receive(a.made)
        val second = checkNotNull(b.run(b.s.createList("Hai")).listId)
        a.receive(b.made)
        val thirdA = checkNotNull(a.run(a.s.createList("Ba A")).listId)
        val thirdB = checkNotNull(b.run(b.s.createList("Ba B")).listId)
        a.receive(b.made)
        b.receive(a.made)
        assertEquals(a.s.lists().map { it.id }, b.s.lists().map { it.id })
        assertEquals(listOf(first, second), a.s.lists().map { it.id }.take(2))
        assertEquals(setOf(thirdA, thirdB), a.s.lists().map { it.id }.drop(2).toSet())
    }

    @Test fun listsContainingTellsWhichPlaylistsHoldASong() {
        val a = replica("dev-a")
        val one = checkNotNull(a.run(a.s.createList("Một")).listId)
        val two = checkNotNull(a.run(a.s.createList("Hai")).listId)
        a.run(a.s.addTelegram(one, tg(1)))
        a.run(a.s.addTelegram(two, tg(1)))
        a.run(a.s.addTelegram(two, tg(2)))
        a.run(a.s.addLocal(two, 8L, LocalFingerprint.of("x", 1, 1)))
        assertEquals(setOf(one, two), a.s.listsContaining(tg(1)))
        assertEquals(setOf(two), a.s.listsContaining(tg(2)))
        assertEquals(setOf(two), a.s.listsContaining(PlaylistItemId.Local(8L)))
        a.run(a.s.deleteList(two))
        assertEquals(setOf(one), a.s.listsContaining(tg(1)))
    }

    @Test fun randomWorkOnThreeDevicesAlwaysConverges() {
        for (seed in 1L..40L) {
            val random = Random(seed)
            val devices = listOf(replica("dev-a"), replica("dev-b"), replica("dev-c"))
            val sent = devices.map { 0 }.toMutableList()
            repeat(60) {
                val who = random.nextInt(devices.size)
                val r = devices[who]
                val lists = r.s.lists().map { it.id }
                val pick = if (lists.isEmpty()) null else lists[random.nextInt(lists.size)]
                val song = tg(1L + random.nextInt(8))
                val m = when (random.nextInt(8)) {
                    0 -> r.s.createList("Playlist ${random.nextInt(6)}")
                    1 -> if (pick != null) r.s.renameList(pick, "Tên ${random.nextInt(6)}") else null
                    2 -> if (pick != null && random.nextInt(4) == 0) r.s.deleteList(pick) else null
                    3, 4 -> if (pick != null) r.s.addTelegram(pick, song) else null
                    5 -> if (pick != null) r.s.removeTelegram(pick, song) else null
                    6 -> if (pick != null) r.s.moveItem(pick, song, random.nextInt(6)) else null
                    else -> {
                        // a partial exchange: this device hears everything the others made so far
                        for ((i, o) in devices.withIndex()) if (i != who) r.receive(o.made)
                        null
                    }
                }
                if (m != null && m.refusal == null) {
                    r.s = m.state
                    r.made += m.ops
                }
            }
            val everything = devices.flatMap { it.made }
            val expected = converge(everything)
            val shuffled = everything.shuffled(Random(seed * 31))
            val partsInOtherOrder = converge(devices[2].made, devices[0].made, devices[1].made, devices[0].made)
            assertSameContent(expected, converge(shuffled + everything.take(everything.size / 3)))
            assertSameContent(expected, converge(everything.reversed()))
            assertSameContent(expected, partsInOtherOrder)
            for (d in devices) {
                for (o in devices) d.receive(o.made)
                assertSameContent(expected, d.s)
            }
        }
    }

    @Test fun seqNeverGoesBackwardsAndOpIdsAreUnique() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        a.run(a.s.addTelegram(id, tg(1)))
        b.receive(a.made)
        assertEquals(a.s.lamport, b.s.lamport)
        b.run(b.s.addTelegram(id, tg(2)))
        assertTrue(b.made.single().seq > a.s.lamport)
        a.run(a.s.addTelegram(id, tg(3)))
        val ids = (a.made + b.made).map { it.opId }
        assertEquals(ids.size, ids.toSet().size)
    }

    // ---- sync bookkeeping -----------------------------------------------------------------------

    @Test fun outboxBookkeepingFollowsTheSameContractAsThePlaylistV2() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        a.run(a.s.addTelegram(id, tg(1)))
        val (op1, op2) = a.made
        a.s = a.s.markRetryRequired(op1.opId)
        assertEquals(OutboxStatus.RETRY_REQUIRED, a.s.outbox[0].status)
        assertEquals(1, a.s.outbox[0].attempts)
        a.s = a.s.revertRefusedDispatch(op1.opId)
        assertEquals(OutboxStatus.PENDING, a.s.outbox[0].status)
        assertEquals(0, a.s.outbox[0].attempts)
        assertEquals("revert only touches retry entries", a.s, a.s.revertRefusedDispatch(op2.opId))
        a.s = a.s.acknowledge(op1.opId)
        a.s = a.s.markRetryRequired(op1.opId)
        assertEquals("an acknowledged entry is not flagged again", OutboxStatus.ACKNOWLEDGED, a.s.outbox[0].status)
        a.s = a.s.markRetryRequired(op2.opId)
        a.s = a.s.pruneAcknowledged()
        assertEquals(listOf(op2.opId), a.s.outbox.map { it.op.opId })
        assertEquals(OutboxStatus.RETRY_REQUIRED, a.s.outbox[0].status)
    }

    @Test fun applyingARemoteOperationDoesNotTouchTheOutboxAndRaisesLamport() {
        val a = replica("dev-a")
        val b = replica("dev-b")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        for (n in 1L..4L) a.run(a.s.addTelegram(id, tg(n)))
        val before = b.s.outbox
        b.receive(a.made)
        assertEquals(before, b.s.outbox)
        assertEquals(a.s.lamport, b.s.lamport)
        assertEquals(0L, b.s.opCounter)
    }

    @Test fun rebalanceKeepsTheOrderAndShortensKeys() {
        val a = replica("dev-a")
        val id = checkNotNull(a.run(a.s.createList("L")).listId)
        for (n in 1L..40L) a.run(a.s.addTelegram(id, tg(n)))
        val before = order(a.s, id)
        val m = a.run(a.s.rebalance(id))
        assertTrue(m.ops.all { it.type == NamedOpType.IMOVE })
        assertEquals(before, order(a.s, id))
        assertTrue(a.s.entries(id).all { it.pos.length <= 4 })
        val b = replica("dev-b")
        b.receive(a.made)
        assertEquals(before, order(b.s, id))
        val again = a.s.rebalance(id)
        assertTrue("already even: nothing to do", again.ops.isEmpty())
    }

    @Test fun theModelIsPureJvmWithNoAndroidNoClockNoFileAccess() {
        val file = listOf(
            "src/shareable/java/com/bsdung/musicassistantcar/data/telegram/NamedPlaylistModel.kt",
            "app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/NamedPlaylistModel.kt",
        ).map { File(it) }.firstOrNull { it.isFile }
        assertNotNull("model source not found from ${File(".").absolutePath}", file)
        val code = file!!.readLines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") || it.trimStart().startsWith("//") }
            .joinToString("\n")
        for (forbidden in listOf("import android", "System.currentTimeMillis", "nanoTime", "java.io.", "java.nio.", "kotlin.io", "Random", "UUID", "Instant", "Clock")) {
            assertFalse("model must not use $forbidden", code.contains(forbidden))
        }
    }
}
