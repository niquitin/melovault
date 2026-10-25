package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** E96 P1: the OR-Set, the logical order and the durable outbox as pure data. No clock, no I/O. */
class TelegramPlaylistModelTest {
    private val account = 1001L

    private fun item(chat: Long, message: Long) = PlaylistItemId.Telegram(chat, message)
    private fun fresh(device: String) = PlaylistState.empty(account, device)
    private fun keys(s: PlaylistState) = s.presentSongKeys(account)

    private val x = item(account, 4_194_304L)
    private val y = item(account, 8_388_608L)
    private val z = item(-100123L, 77L)

    // ---- identity -------------------------------------------------------------------------------

    @Test fun sameRawMessageIdInTwoChatsAreTwoDifferentSongs() {
        val a = item(-100111L, 5L)
        val b = item(-100222L, 5L)
        assertNotEquals(a, b)
        assertNotEquals(a.songKey(account), b.songKey(account))
        val s = fresh("dev-a").addTelegram(a).state.addTelegram(b).state
        assertEquals(2, keys(s).size)
        val afterRemove = s.removeTelegram(a).state
        assertEquals(listOf(b.songKey(account)), keys(afterRemove))
    }

    @Test fun savedMessagesKeepTheirOriginalNumberAsUiKey() {
        assertEquals(4_194_304L, x.songKey(account))
        assertTrue(z.songKey(account) > (1L shl 62))
    }

    @Test fun localMusicIsNotSyncableAndLegacyIsDistinct() {
        assertFalse(PlaylistItemId.Local(9L).isSyncable)
        assertFalse(PlaylistItemId.Legacy(9L).isSyncable)
        assertTrue(x.isSyncable)
        for (id in listOf<PlaylistItemId>(x, z, PlaylistItemId.Legacy(7L), PlaylistItemId.Local(3L))) {
            assertEquals(id, PlaylistItemId.parse(id.key))
        }
        assertNull(PlaylistItemId.parse("tg:0:5"))
        assertNull(PlaylistItemId.parse("nonsense"))
    }

    @Test fun anUnverifiedLegacyKeyNeverCreatesAnOperationOrOutboxEntry() {
        val s = fresh("dev-a").addLegacy(123L).addLegacy(456L)
        assertEquals(listOf(123L, 456L), keys(s))
        assertTrue(s.outbox.isEmpty())
        assertEquals(0L, s.opCounter)
        assertTrue(s.orSet.knownItems().isEmpty())
    }

    // ---- add / remove ---------------------------------------------------------------------------

    @Test fun addingTheSameSongTwiceMakesOneEntryAndOneOperation() {
        val once = fresh("dev-a").addTelegram(x)
        val twice = once.state.addTelegram(x)
        assertNull(twice.op)
        assertEquals(once.state, twice.state)
        assertEquals(1, keys(twice.state).size)
        assertEquals(1, twice.state.outbox.size)
    }

    @Test fun addAndRemoveInOrderAndReAddGivesANewTag() {
        val s1 = fresh("dev-a").addTelegram(x).state
        val s2 = s1.removeTelegram(x).state
        assertTrue(keys(s2).isEmpty())
        val s3 = s2.addTelegram(x).state
        assertEquals(listOf(x.songKey(account)), keys(s3))
        assertEquals(3, s3.outbox.size)
        // the second add has its own tag, so the first remove cannot cover it
        assertEquals(setOf(Dot("dev-a", 1)), s3.outbox[1].op.dots)
        assertEquals(setOf(Dot("dev-a", 3)), s3.outbox[2].op.dots)
    }

    @Test fun removingASongThatIsNotThereDoesNothing() {
        val s = fresh("dev-a")
        val m = s.removeTelegram(x)
        assertNull(m.op)
        assertEquals(s, m.state)
    }

    @Test fun aRemoveCarriesExactlyTheTagsItObserved() {
        val s1 = fresh("dev-a").addTelegram(x).state
        val removal = s1.removeTelegram(x).op!!
        assertEquals(OpType.REMOVE, removal.type)
        assertEquals(setOf(Dot("dev-a", 1)), removal.dots)
    }

    @Test fun anAddThatArrivesAfterItsRemoveStaysRemoved() {
        // Out-of-order delivery: tombstone first, add second.
        val a = fresh("dev-a")
        val added = a.addTelegram(x)
        val removed = added.state.removeTelegram(x)
        var late = fresh("dev-b")
        late = late.applyRemote(removed.op!!)
        late = late.applyRemote(added.op!!)
        assertTrue(keys(late).isEmpty())
    }

    // ---- two clients ----------------------------------------------------------------------------

    @Test fun concurrentAddsOnTwoClientsBothSurviveAndOrderMatches() {
        val a0 = fresh("dev-a")
        val b0 = fresh("dev-b")
        val (a1, opA) = a0.addTelegram(x)
        val (b1, opB) = b0.addTelegram(y)
        val a2 = a1.applyRemote(opB!!)
        val b2 = b1.applyRemote(opA!!)
        assertEquals(a2.orSet, b2.orSet)
        assertEquals(keys(a2), keys(b2))
        assertEquals(setOf(x.songKey(account), y.songKey(account)), keys(a2).toSet())
    }

    @Test fun theSameSongAddedOnTwoClientsIsOneEntryWithTwoTags() {
        val (a1, opA) = fresh("dev-a").addTelegram(x)
        val (b1, opB) = fresh("dev-b").addTelegram(x)
        val a2 = a1.applyRemote(opB!!)
        val b2 = b1.applyRemote(opA!!)
        assertEquals(1, keys(a2).size)
        assertEquals(a2.orSet, b2.orSet)
        assertEquals(2, a2.orSet.liveDots(x).size)
    }

    @Test fun aRemoveAfterObservingTheAddRemovesItEverywhere() {
        val (a1, addOp) = fresh("dev-a").addTelegram(x)
        val b1 = fresh("dev-b").applyRemote(addOp!!)
        val (b2, removeOp) = b1.removeTelegram(x)
        val a2 = a1.applyRemote(removeOp!!)
        assertTrue(keys(a2).isEmpty())
        assertTrue(keys(b2).isEmpty())
        assertEquals(a2.orSet, b2.orSet)
    }

    @Test fun anAddThatTheRemoverNeverSawIsNotLost() {
        // Both have x (tag a#1). B removes it and puts it back (new tag); A removes the old tag concurrently.
        val (a1, addOp) = fresh("dev-a").addTelegram(x)
        val b1 = fresh("dev-b").applyRemote(addOp!!)
        val (a2, aRemove) = a1.removeTelegram(x)
        val (b2, bRemove) = b1.removeTelegram(x)
        val (b3, bReAdd) = b2.addTelegram(x)
        val a3 = a2.applyRemote(bRemove!!).applyRemote(bReAdd!!)
        val b4 = b3.applyRemote(aRemove!!)
        assertEquals(listOf(x.songKey(account)), keys(a3))
        assertEquals(listOf(x.songKey(account)), keys(b4))
        assertEquals(a3.orSet, b4.orSet)
    }

    @Test fun orderFollowsALogicalCounterAndKeepsTheMigratedOrder() {
        var s = fresh("dev-a")
        for (n in listOf(30L, 10L, 20L)) s = s.addTelegram(item(account, n * 1_000_000L)).state
        assertEquals(listOf(30_000_000L, 10_000_000L, 20_000_000L), keys(s))
        // a song added by another device whose logical counter is behind (a "late clock") is never lost,
        // and every replica that gets it shows the very same order
        val behind = fresh("dev-b")
        val late = behind.addTelegram(y).op!!
        val merged = s.applyRemote(late)
        assertEquals(4, keys(merged).size)
        assertTrue(y.songKey(account) in keys(merged))
        val other = fresh("dev-c")
        val replay = s.outbox.fold(other) { acc, entry -> acc.applyRemote(entry.op) }.applyRemote(late)
        assertEquals(keys(merged), keys(replay))
    }

    // ---- algebraic properties ------------------------------------------------------------------

    private fun randomOps(device: String, seed: Long, count: Int, pool: List<PlaylistItemId.Telegram>): List<PlaylistOp> {
        val random = Random(seed)
        var s = fresh(device)
        val ops = ArrayList<PlaylistOp>()
        repeat(count) {
            val target = pool[random.nextInt(pool.size)]
            val m = if (s.orSet.contains(target)) s.removeTelegram(target) else s.addTelegram(target)
            s = m.state
            m.op?.let { ops.add(it) }
        }
        return ops
    }

    private fun fold(ops: List<PlaylistOp>): PlaylistOrSet = ops.fold(PlaylistOrSet.EMPTY) { acc, op -> acc.apply(op) }

    private val pool = (1L..6L).map { item(-100L - it, it * 10L) }

    @Test fun mergeIsCommutativeAssociativeAndIdempotent() {
        for (seed in 1L..25L) {
            val a = fold(randomOps("dev-a", seed, 30, pool))
            val b = fold(randomOps("dev-b", seed + 100, 30, pool))
            val c = fold(randomOps("dev-c", seed + 200, 30, pool))
            assertEquals("commutative seed=$seed", a.merge(b), b.merge(a))
            assertEquals("associative seed=$seed", a.merge(b).merge(c), a.merge(b.merge(c)))
            assertEquals("idempotent seed=$seed", a.merge(a), a)
            assertEquals("idempotent after merge seed=$seed", a.merge(b).merge(b), a.merge(b))
        }
    }

    @Test fun everyDeliveryOrderWithRepeatsConvergesToTheSameSetAndOrder() {
        val ops = randomOps("dev-a", 11, 40, pool) + randomOps("dev-b", 12, 40, pool) + randomOps("dev-c", 13, 40, pool)
        val reference = fold(ops)
        val referenceOrder = reference.ordered()
        for (seed in 1L..30L) {
            val random = Random(seed)
            val delivered = ArrayList<PlaylistOp>(ops)
            repeat(25) { delivered.add(ops[random.nextInt(ops.size)]) } // retries / duplicates
            delivered.shuffle(random)
            val result = fold(delivered)
            assertEquals("set seed=$seed", reference, result)
            assertEquals("order seed=$seed", referenceOrder, result.ordered())
        }
    }

    @Test fun mergingTheReplicasOfAllDevicesEqualsApplyingEveryOperation() {
        val opsA = randomOps("dev-a", 5, 30, pool)
        val opsB = randomOps("dev-b", 6, 30, pool)
        assertEquals(fold(opsA + opsB), fold(opsA).merge(fold(opsB)))
    }

    // ---- durable outbox (model) -----------------------------------------------------------------

    @Test fun everyNewOperationStartsPendingWithAUniqueStableId() {
        var s = fresh("dev-a")
        s = s.addTelegram(x).state.addTelegram(y).state.removeTelegram(x).state
        assertEquals(3, s.outbox.size)
        assertTrue(s.outbox.all { it.status == OutboxStatus.PENDING && it.attempts == 0 })
        assertEquals(3, s.outbox.map { it.op.opId }.toSet().size)
        assertEquals(listOf("dev-a#1", "dev-a#2", "dev-a#3"), s.outbox.map { it.op.opId })
    }

    @Test fun retryKeepsTheOperationIdAndNeverDropsAnUnacknowledgedOperation() {
        val s0 = fresh("dev-a").addTelegram(x).state
        val original = s0.outbox.single().op
        val retry = s0.markRetryRequired(original.opId).markRetryRequired(original.opId)
        assertEquals(original, retry.outbox.single().op)
        assertEquals(OutboxStatus.RETRY_REQUIRED, retry.outbox.single().status)
        assertEquals(2, retry.outbox.single().attempts)
        assertEquals(1, retry.pruneAcknowledged().outbox.size) // only acknowledged ones may go
    }

    @Test fun acknowledgedOperationsStayUntilPrunedAndPruneKeepsTheRest() {
        var s = fresh("dev-a").addTelegram(x).state.addTelegram(y).state
        s = s.acknowledge("dev-a#1")
        assertEquals(OutboxStatus.ACKNOWLEDGED, s.outbox[0].status)
        assertEquals(2, s.outbox.size)
        val pruned = s.pruneAcknowledged()
        assertEquals(listOf("dev-a#2"), pruned.outbox.map { it.op.opId })
        // an acknowledged entry is not pulled back to retry
        assertEquals(OutboxStatus.ACKNOWLEDGED, s.markRetryRequired("dev-a#1").outbox[0].status)
    }

    @Test fun aFullOutboxRefusesNewChangesInsteadOfDroppingOldOnes() {
        val filler = fresh("dev-a").addTelegram(x).state.outbox.single()
        val full = fresh("dev-a").copy(outbox = List(PlaylistState.MAX_OUTBOX) { filler }, opCounter = 10)
        try {
            full.addTelegram(y)
            org.junit.Assert.fail("must refuse")
        } catch (_: OutboxFullException) {
        }
        assertEquals(PlaylistState.MAX_OUTBOX, full.outbox.size)
    }

    @Test fun twoChangesInARowBothCountAndKeepTheirOwnCounter() {
        val s = fresh("dev-a").addTelegram(x).state.addTelegram(y).state
        assertEquals(2L, s.opCounter)
        assertEquals(listOf(x.songKey(account), y.songKey(account)), keys(s))
        assertEquals(2, s.outbox.size)
    }

    @Test fun aRemoteChangeNeverTouchesThisDevicesOutboxOrCounter() {
        val (_, op) = fresh("dev-b").addTelegram(y)
        val s = fresh("dev-a").addTelegram(x).state
        val after = s.applyRemote(op!!)
        assertEquals(s.outbox, after.outbox)
        assertEquals(s.opCounter, after.opCounter)
        assertTrue(after.lamport >= op.seq)
    }

    @Test fun upgradingALegacyKeyKeepsItsPositionAndMakesAnAddOperation() {
        var s = fresh("dev-a").addLegacy(111L).addTelegram(x).state.addLegacy(222L)
        assertEquals(listOf(111L, x.songKey(account), 222L), keys(s))
        val legacy = s.legacy.first { it.songKey == 222L }
        val real = item(-100500L, 9L)
        val up = s.upgradeLegacy(legacy, real)
        assertEquals(listOf(111L, x.songKey(account), real.songKey(account)), keys(up.state))
        assertEquals(OpType.ADD, up.op!!.type)
        assertTrue(up.state.legacy.none { it.songKey == 222L })
        assertFalse(222L in up.state.legacyRemoved) // an upgrade is not a user deletion
        s = up.state
        assertEquals(2, s.outbox.size)
    }

    @Test fun removingALegacyKeyIsRememberedSoItIsNotImportedBack() {
        val s = fresh("dev-a").addLegacy(5L).removeLegacy(5L)
        assertTrue(keys(s).isEmpty())
        assertTrue(5L in s.legacyRemoved)
        assertTrue(s.outbox.isEmpty())
    }
}
