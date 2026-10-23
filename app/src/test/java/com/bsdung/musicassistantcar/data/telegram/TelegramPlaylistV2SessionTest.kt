package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistSession.OpenResult
import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistSession.ToggleOutcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A file layer that can "lose power" after N successful writes. Counts only writes to files whose path contains [only]. */
private class CrashingFileIo(private val only: String) : PlaylistFileIo {
    var writesAllowed = Int.MAX_VALUE
    var writes = 0
    override fun read(file: File): ByteArray = TestPlainFileIo.read(file)
    override fun write(file: File, bytes: ByteArray) {
        if (file.path.contains(only)) {
            if (writes >= writesAllowed) throw IOException("power lost")
            writes++
        }
        TestPlainFileIo.write(file, bytes)
    }
}

private class FakeIndex(private val items: Collection<PlaylistItemId.Telegram>, private val self: Long) : PlaylistIndex {
    var failing = false
    var calls = 0
    override fun resolveAll(account: TelegramUserId, keys: Collection<Long>): Map<Long, PlaylistItemId.Telegram> {
        calls++
        if (failing) throw IOException("index closed")
        val byKey = items.associateBy { it.songKey(self) }
        return keys.mapNotNull { k -> byKey[k]?.let { k to it } }.toMap()
    }
}

/** E96 P1: migration V1 → V2, durable outbox, V1 copy, conflicts, accounts — real stores, fake cipher and fake power loss. */
class TelegramPlaylistV2SessionTest {
    @get:Rule val folder = TemporaryFolder()

    private val a = TelegramUserId(1001L)
    private val b = TelegramUserId(2002L)

    private val v1Io = CrashingFileIo("tg_playlists_v1")
    private val v2Io = CrashingFileIo("tg_playlists_v2")
    private var deviceCounter = 0

    private fun v1Store(io: PlaylistFileIo = v1Io, cipher: PlaylistCipher = TestAesGcmCipher(1)) =
        TelegramSavedPlaylistStore(folder.root, cipher, io)

    private fun v2Store(io: PlaylistFileIo = v2Io, cipher: PlaylistCipher = TestAesGcmCipher(2)) =
        TelegramPlaylistStoreV2(folder.root, cipher, io)

    private fun session(
        v1: PlaylistPersistence = v1Store(),
        v2: PlaylistStoreV2 = v2Store(),
        index: PlaylistIndex? = null,
    ): TelegramPlaylistV2Session = TelegramPlaylistV2Session(v1, v2) { "dev-${++deviceCounter}" }.also { s ->
        if (index != null) s.attachIndex(index)
    }

    private fun v1File(u: TelegramUserId) =
        File(File(folder.root, "tg_playlists_v1"), TelegramAccountStoragePolicy.directoryName(u) + ".bin")

    private fun v2File(u: TelegramUserId) =
        File(File(folder.root, "tg_playlists_v2"), TelegramAccountStoragePolicy.directoryName(u) + ".bin")

    /** Saved Messages songs: the key is the message id itself (chat = the account). */
    private fun saved(n: Int): Long = n * 4_194_304L
    private fun savedItems(from: Int, to: Int) = (from..to).map { PlaylistItemId.Telegram(a.value, saved(it)) }
    private fun v1Ids(u: TelegramUserId = a): List<Long> = (v1Store().load(u) as PlaylistLoadResult.Ok).ids
    private fun v2State(u: TelegramUserId = a): PlaylistState = (v2Store().load(u) as V2LoadResult.Valid).state
    private fun ready(r: OpenResult) = (r as OpenResult.Ready).ids

    // ---- migration V1 → V2 ----------------------------------------------------------------------

    @Test fun emptyV1MigratesToAnEmptyCompleteV2() {
        v1Store().save(a, emptyList())
        val s = session()
        assertEquals(emptyList<Long>(), ready(s.open(a)))
        val st = v2State()
        assertEquals(MigrationStatus.COMPLETE, st.migration.status)
        assertEquals(0, st.migration.v1Count)
        assertEquals(PlaylistLoadResult.Ok(emptyList()), v1Store().load(a)) // V1 left alone
    }

    @Test fun noV1AtAllAlsoGivesAnEmptyV2() {
        assertEquals(emptyList<Long>(), ready(session().open(a)))
        assertEquals(MigrationStatus.COMPLETE, v2State().migration.status)
        assertFalse("migration itself never writes V1", v1File(a).exists())
    }

    @Test fun oneSongMigratesWithItsRealSourceWhenTheIndexKnowsIt() {
        v1Store().save(a, listOf(saved(7)))
        val s = session(index = FakeIndex(savedItems(7, 7), a.value))
        assertEquals(listOf(saved(7)), ready(s.open(a)))
        val st = v2State()
        assertEquals(listOf(PlaylistItemId.Telegram(a.value, saved(7))), st.orSet.ordered().map { it.first })
        assertEquals(1, st.outbox.size)
        assertEquals(OutboxStatus.PENDING, st.outbox.single().status)
        assertTrue(st.legacy.isEmpty())
    }

    @Test fun threeHundredSongsMigrateInOrderAndV1IsNotTouched() {
        val ids = (1..300).map { saved(it) }.reversed() // saved order is NOT numeric order
        v1Store().save(a, ids)
        val v1Before = v1File(a).readBytes()
        val s = session(index = FakeIndex(savedItems(1, 300), a.value))
        assertEquals(ids, ready(s.open(a)))
        assertEquals(ids, v2State().presentSongKeys(a.value))
        assertEquals(300, v2State().outbox.size)
        assertArrayEquals("V1 bytes unchanged by migration", v1Before, v1File(a).readBytes())
    }

    @Test fun reopeningManyTimesNeverDuplicatesAnything() {
        v1Store().save(a, listOf(saved(1), saved(2), saved(3)))
        val index = FakeIndex(savedItems(1, 3), a.value)
        assertEquals(3, ready(session(index = index).open(a)).size)
        val first = v2State()
        repeat(3) {
            val again = session(index = index)
            assertEquals(listOf(saved(1), saved(2), saved(3)), ready(again.open(a)))
        }
        val last = v2State()
        assertEquals(first.outbox, last.outbox)
        assertEquals(first.opCounter, last.opCounter)
        assertEquals(first.deviceId, last.deviceId)
        assertEquals("the device id was minted once, by the first open", 1, deviceCounter)
    }

    @Test fun powerLostBeforeV2IsCommittedLeavesNothingAndRetriesCleanly() {
        v1Store().save(a, listOf(saved(1), saved(2)))
        v2Io.writesAllowed = 0
        assertTrue(session().open(a) is OpenResult.Blocked)
        assertFalse(v2File(a).exists())
        assertEquals(listOf(saved(1), saved(2)), v1Ids())
        v2Io.writesAllowed = Int.MAX_VALUE
        assertEquals(listOf(saved(1), saved(2)), ready(session().open(a)))
        assertEquals(MigrationStatus.COMPLETE, v2State().migration.status)
    }

    @Test fun powerLostAfterV2IsCommittedButBeforeItIsMarkedCompleteFinishesLater() {
        v1Store().save(a, listOf(saved(1), saved(2)))
        v2Io.writesAllowed = 1 // the first write (IN_PROGRESS) lands, the second (COMPLETE) does not
        val index = FakeIndex(savedItems(1, 2), a.value)
        val first = session(index = index).open(a)
        assertEquals(listOf(saved(1), saved(2)), ready(first)) // data is committed and usable
        assertEquals(MigrationStatus.IN_PROGRESS, v2State().migration.status)
        v2Io.writesAllowed = Int.MAX_VALUE
        val second = session(index = index)
        assertEquals(listOf(saved(1), saved(2)), ready(second.open(a)))
        assertEquals(MigrationStatus.COMPLETE, v2State().migration.status)
        assertEquals("no duplicate from finishing", 2, v2State().outbox.size)
    }

    @Test fun powerLostAfterV2CommitButBeforeTheV1CopyIsRepairedOnTheNextStart() {
        v1Store().save(a, listOf(saved(1)))
        val s = session(index = FakeIndex(savedItems(1, 2), a.value))
        s.open(a)
        v1Io.writesAllowed = 0 // V1 can no longer be written
        val out = s.toggle(a, PlaylistTarget(saved(2), a.value, saved(2)))
        assertEquals(ToggleOutcome.Added(listOf(saved(1), saved(2))), out)
        assertEquals("V2 has it", listOf(saved(1), saved(2)), v2State().presentSongKeys(a.value))
        assertEquals("V1 copy is stale", listOf(saved(1)), v1Ids())
        v1Io.writesAllowed = Int.MAX_VALUE
        val next = session(index = FakeIndex(savedItems(1, 2), a.value))
        assertEquals(listOf(saved(1), saved(2)), ready(next.open(a)))
        assertEquals("V1 copy repaired", listOf(saved(1), saved(2)), v1Ids())
    }

    @Test fun aV1CopyThatFailsNeverRollsBackOrDamagesV2() {
        v1Store().save(a, listOf(saved(1)))
        val s = session(index = FakeIndex(savedItems(1, 3), a.value))
        s.open(a)
        v1Io.writesAllowed = 0
        s.toggle(a, PlaylistTarget(saved(2), a.value, saved(2)))
        s.toggle(a, PlaylistTarget(saved(3), a.value, saved(3)))
        assertEquals(listOf(saved(1), saved(2), saved(3)), s.snapshot())
        assertEquals(listOf(saved(1), saved(2), saved(3)), v2State().presentSongKeys(a.value))
        assertEquals("every change is still waiting in the outbox", 3, v2State().outbox.size)
    }

    // ---- unreadable files are never overwritten ---------------------------------------------------

    @Test fun anUnreadableV1StopsMigrationAndLeavesEveryFileAlone() {
        v1Store().save(a, listOf(saved(1)))
        val damaged = v1File(a).readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        v1File(a).writeBytes(damaged)
        val s = session()
        assertEquals(OpenResult.Blocked(PlaylistUnreadableReason.DECRYPT), s.open(a))
        assertFalse("no V2 is made from a V1 that cannot be read", v2File(a).exists())
        assertEquals(ToggleOutcome.Blocked(PlaylistUnreadableReason.DECRYPT), s.toggle(a, PlaylistTarget(saved(9), a.value, saved(9))))
        assertArrayEquals(damaged, v1File(a).readBytes())
        assertFalse(v2File(a).exists())
    }

    @Test fun anUnreadableV2IsNeverOverwrittenAndV1IsNotUsedInItsPlace() {
        v1Store().save(a, listOf(saved(1)))
        session(index = FakeIndex(savedItems(1, 1), a.value)).open(a)
        val damaged = v2File(a).readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        v2File(a).writeBytes(damaged)
        val v1Before = v1File(a).readBytes()
        val s = session()
        assertEquals(OpenResult.Blocked(PlaylistUnreadableReason.DECRYPT), s.open(a))
        assertEquals(ToggleOutcome.Blocked(PlaylistUnreadableReason.DECRYPT), s.toggle(a, PlaylistTarget(saved(9), a.value, saved(9))))
        assertEquals(emptyList<Long>(), s.snapshot())
        assertArrayEquals(damaged, v2File(a).readBytes())
        assertArrayEquals(v1Before, v1File(a).readBytes())
    }

    @Test fun aMissingV2KeyDoesNotDestroyEitherFileOrMintAKey() {
        v1Store().save(a, listOf(saved(1)))
        session(index = FakeIndex(savedItems(1, 1), a.value)).open(a)
        val v2Before = v2File(a).readBytes()
        val v1Before = v1File(a).readBytes()
        var encrypts = 0
        val keyGone = object : PlaylistCipher {
            override fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray { encrypts++; return ByteArray(40) }
            override fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray = throw IllegalStateException("Playlist key is not available")
        }
        val s = session(v2 = v2Store(cipher = keyGone))
        assertTrue(s.open(a) is OpenResult.Blocked)
        assertTrue(s.toggle(a, PlaylistTarget(saved(5), a.value, saved(5))) is ToggleOutcome.Blocked)
        assertEquals(0, encrypts)
        assertArrayEquals(v2Before, v2File(a).readBytes())
        assertArrayEquals(v1Before, v1File(a).readBytes())
        assertEquals("V1 still reads with its own key", PlaylistLoadResult.Ok(listOf(saved(1))), v1Store().load(a))
    }

    @Test fun aTransientReadErrorRecoversWithoutLosingAnything() {
        v1Store().save(a, listOf(saved(1)))
        session(index = FakeIndex(savedItems(1, 2), a.value)).open(a)
        val good = TestAesGcmCipher(2)
        var down = true
        val flaky = object : PlaylistCipher {
            override fun encrypt(aad: ByteArray, plain: ByteArray) = good.encrypt(aad, plain)
            override fun decrypt(aad: ByteArray, blob: ByteArray) =
                if (down) throw GeneralSecurityExceptionForTest() else good.decrypt(aad, blob)
        }
        val s = session(v2 = v2Store(cipher = flaky), index = FakeIndex(savedItems(1, 2), a.value))
        assertTrue(s.open(a) is OpenResult.Blocked)
        down = false
        assertEquals(ToggleOutcome.Added(listOf(saved(1), saved(2))), s.toggle(a, PlaylistTarget(saved(2), a.value, saved(2))))
    }

    private class GeneralSecurityExceptionForTest : java.security.GeneralSecurityException("keystore not ready")

    // ---- accounts -------------------------------------------------------------------------------

    @Test fun twoAccountsNeverMixAndOnePendingOutboxStaysWithItsAccount() {
        val s = session(index = FakeIndex(savedItems(1, 5) + listOf(PlaylistItemId.Telegram(b.value, saved(9))), a.value))
        s.open(a)
        s.toggle(a, PlaylistTarget(saved(1), a.value, saved(1)))
        s.toggle(a, PlaylistTarget(saved(2), a.value, saved(2)))
        val pendingA = v2State(a).outbox
        assertEquals(2, pendingA.size)

        assertEquals(emptyList<Long>(), ready(s.open(b)))
        assertEquals("A can no longer write through B's session", ToggleOutcome.NotOpen, s.toggle(a, PlaylistTarget(saved(3), a.value, saved(3))))
        val addB = s.toggle(b, PlaylistTarget(saved(9), b.value, saved(9)))
        assertTrue(addB is ToggleOutcome.Added)
        assertEquals(listOf(saved(9)), s.snapshot())
        assertEquals(1, v2State(b).outbox.size)

        assertEquals(listOf(saved(1), saved(2)), ready(s.open(a)))
        assertEquals("A's waiting operations survived the switch", pendingA, v2State(a).outbox)
        assertTrue("different devices ids are fine; files are separate", v2File(a).path != v2File(b).path)
    }

    // ---- identity ------------------------------------------------------------------------------

    @Test fun twoChatsWithTheSameRawMessageIdStayTwoSongs() {
        val s = session()
        s.open(a)
        val one = s.toggle(a, PlaylistTarget(TelegramSongKey.forMessage(-100111L, 5L, a.value), -100111L, 5L))
        val two = s.toggle(a, PlaylistTarget(TelegramSongKey.forMessage(-100222L, 5L, a.value), -100222L, 5L))
        assertTrue(one is ToggleOutcome.Added)
        assertEquals(2, (two as ToggleOutcome.Added).ids.size)
        val items = v2State().orSet.ordered().map { it.first }
        assertEquals(setOf(PlaylistItemId.Telegram(-100111L, 5L), PlaylistItemId.Telegram(-100222L, 5L)), items.toSet())
        val removed = s.toggle(a, PlaylistTarget(TelegramSongKey.forMessage(-100111L, 5L, a.value), -100111L, 5L))
        assertEquals(listOf(TelegramSongKey.forMessage(-100222L, 5L, a.value)), (removed as ToggleOutcome.Removed).ids)
    }

    @Test fun aKeyThatDoesNotMatchTheStatedSourceIsNotTrustedAndStaysLegacy() {
        val s = session()
        s.open(a)
        val wrong = s.toggle(a, PlaylistTarget(saved(3), -100999L, 5L)) // chat/message say something else
        assertTrue(wrong is ToggleOutcome.Added)
        val st = v2State()
        assertTrue(st.orSet.knownItems().isEmpty())
        assertEquals(listOf(saved(3)), st.legacy.map { it.songKey })
        assertTrue("an unverified key never reaches the outbox", st.outbox.isEmpty())
    }

    @Test fun unresolvedLegacyKeysSurviveMigrationAndAreNamedLaterInTheSamePlace() {
        val hashKey = TelegramSongKey.forMessage(-100500L, 321L, a.value)
        v1Store().save(a, listOf(saved(1), hashKey, saved(2)))
        val known = FakeIndex(savedItems(1, 2), a.value) // does not know hashKey yet
        val s = session(index = known)
        assertEquals(listOf(saved(1), hashKey, saved(2)), ready(s.open(a)))
        var st = v2State()
        assertEquals(listOf(hashKey), st.legacy.map { it.songKey })
        assertEquals("only the two verified songs are in the outbox", 2, st.outbox.size)

        assertFalse("nothing to name while the index does not know it", s.resolveLegacyKeys(a))
        assertEquals(listOf(saved(1), hashKey, saved(2)), s.snapshot())

        val nowKnown = FakeIndex(savedItems(1, 2) + PlaylistItemId.Telegram(-100500L, 321L), a.value)
        s.attachIndex(nowKnown)
        assertTrue(s.resolveLegacyKeys(a))
        st = v2State()
        assertTrue(st.legacy.isEmpty())
        assertEquals("same position, same UI key", listOf(saved(1), hashKey, saved(2)), st.presentSongKeys(a.value))
        assertEquals(3, st.outbox.size)
        assertEquals(listOf(saved(1), hashKey, saved(2)), v1Ids())
    }

    @Test fun anIndexThatFailsOrLiesNeverPromotesAKey() {
        val hashKey = TelegramSongKey.forMessage(-100500L, 321L, a.value)
        v1Store().save(a, listOf(hashKey))
        val index = FakeIndex(emptyList(), a.value).also { it.failing = true }
        val s = session(index = index)
        assertEquals(listOf(hashKey), ready(s.open(a)))
        assertEquals(1, v2State().legacy.size)
        val liar = object : PlaylistIndex {
            override fun resolveAll(account: TelegramUserId, keys: Collection<Long>) =
                keys.associateWith { PlaylistItemId.Telegram(-1L, 1L) } // does not match the keys
        }
        s.attachIndex(liar)
        assertFalse(s.resolveLegacyKeys(a))
        assertEquals(1, v2State().legacy.size)
    }

    // ---- save / unsave -------------------------------------------------------------------------

    @Test fun twoChangesInARowBothStickAndKeepTheirOrder() {
        val s = session()
        s.open(a)
        s.toggle(a, PlaylistTarget(saved(1), a.value, saved(1)))
        s.toggle(a, PlaylistTarget(saved(2), a.value, saved(2)))
        assertEquals(listOf(saved(1), saved(2)), v2State().presentSongKeys(a.value))
        assertEquals(listOf("dev-1#1", "dev-1#2"), v2State().outbox.map { it.op.opId })
        s.toggle(a, PlaylistTarget(saved(1), a.value, saved(1)))
        assertEquals(listOf(saved(2)), v2State().presentSongKeys(a.value))
        assertEquals(3, v2State().outbox.size)
    }

    @Test fun theOutboxIsStillThereAfterARestartWithTheSameOperationIds() {
        val s = session()
        s.open(a)
        s.toggle(a, PlaylistTarget(saved(1), a.value, saved(1)))
        s.toggle(a, PlaylistTarget(saved(1), a.value, saved(1)))
        val before = v2State().outbox
        val restarted = session()
        restarted.open(a)
        assertEquals(before, v2State().outbox)
        assertEquals(listOf("dev-1#1", "dev-1#2"), v2State().outbox.map { it.op.opId })
        assertTrue(v2State().outbox.all { it.status == OutboxStatus.PENDING })
        // nothing is "synced" by P1: no entry is acknowledged by itself
        assertFalse(v2State().outbox.any { it.status == OutboxStatus.ACKNOWLEDGED })
    }

    @Test fun aRapidBurstFromTwoThreadsLosesNothing() {
        val s = session()
        s.open(a)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        for (base in listOf(1, 1001)) {
            Thread {
                start.await()
                for (n in 0 until 40) {
                    val key = saved(base + n)
                    s.toggle(a, PlaylistTarget(key, a.value, key))
                }
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue(done.await(60, TimeUnit.SECONDS))
        assertEquals(80, v2State().presentSongKeys(a.value).size)
        assertEquals(80, v2State().outbox.size)
        assertEquals(80, s.snapshot().size)
    }

    @Test fun aFullPlaylistRefusesAndAnEmptiedSlotWorksAgain() {
        v1Store().save(a, (1..300).map { saved(it) })
        val s = session(index = FakeIndex(savedItems(1, 400), a.value))
        s.open(a)
        assertEquals(ToggleOutcome.Full(300), s.toggle(a, PlaylistTarget(saved(301), a.value, saved(301))))
        assertEquals(300, v2State().presentSongKeys(a.value).size)
        assertTrue(s.toggle(a, PlaylistTarget(saved(1), a.value, saved(1))) is ToggleOutcome.Removed)
        assertTrue(s.toggle(a, PlaylistTarget(saved(301), a.value, saved(301))) is ToggleOutcome.Added)
    }

    @Test fun theOutboxLimitRefusesLoudlyAndKeepsEverythingWaiting() {
        val s = session()
        s.open(a)
        s.toggle(a, PlaylistTarget(saved(1), a.value, saved(1)))
        val waiting = (1..PlaylistState.MAX_OUTBOX).map { n ->
            val dot = Dot("dev-1", 100L + n)
            OutboxEntry(PlaylistOp(dot.key, OpType.ADD, PlaylistItemId.Telegram(a.value, saved(n)), setOf(dot), n.toLong()), OutboxStatus.PENDING, 0)
        }
        v2Store().save(a, v2State().copy(outbox = waiting, opCounter = 100_000L))
        val again = session()
        again.open(a)
        assertEquals(ToggleOutcome.OutboxFull, again.toggle(a, PlaylistTarget(saved(2), a.value, saved(2))))
        assertEquals(PlaylistState.MAX_OUTBOX, v2State().outbox.size)
        assertEquals(listOf(saved(1)), v2State().presentSongKeys(a.value))
    }

    // ---- more than the old limits ---------------------------------------------------------------

    @Test fun moreThan300SongsInV2AreKeptWholeAndTheV1CopyIsCappedOnPurpose() {
        // A V2 that holds 350 (what a future merge from another device can produce); built with the model.
        var st = PlaylistState.empty(a.value, "dev-big")
        for (n in 1..350) st = st.addTelegram(PlaylistItemId.Telegram(a.value, saved(n))).state
        val emptyV1 = TelegramPlaylistCodec.listDigest(emptyList())
        st = st.copy(
            migration = MigrationRecord(MigrationStatus.COMPLETE, emptyV1, 0),
            projection = ProjectionRecord(emptyV1, emptyV1, ProjectionStatus.IN_SYNC, false, 0, 0, 0),
        )
        v2Store().save(a, st)
        v1Store().save(a, emptyList())
        val s = session()
        val shown = ready(s.open(a))
        assertEquals("nothing is cut from V2", 350, shown.size)
        assertEquals((1..350).map { saved(it) }, shown)
        assertEquals("V1 copy = the 300 newest, for older apps that cut at 300", (51..350).map { saved(it) }, v1Ids())
        // removing one keeps the other 349; adding is refused while 300 or more
        assertEquals(349, (s.toggle(a, PlaylistTarget(saved(1), a.value, saved(1))) as ToggleOutcome.Removed).ids.size)
        assertTrue("the limit of the old copy is recorded", v2State().projection.truncated)
        assertEquals((51..350).map { saved(it) }, v1Ids())
        assertEquals(ToggleOutcome.Full(349), s.toggle(a, PlaylistTarget(saved(999), a.value, saved(999))))
        assertEquals(349, v2State().presentSongKeys(a.value).size)
    }

    @Test fun aListLongerThanTheRamWindowIsReturnedInFull() {
        v1Store().save(a, (1..250).map { saved(it) })
        val s = session(index = FakeIndex(savedItems(1, 250), a.value))
        assertEquals(250, ready(s.open(a)).size)
        assertEquals((1..250).map { saved(it) }, s.snapshot())
    }

    // ---- V1 changed by an older app ---------------------------------------------------------------

    @Test fun v1ChangedByAnOlderAppIsDetectedMergedWithoutResurrectionAndNothingIsLost() {
        val index = FakeIndex(savedItems(1, 10), a.value)
        v1Store().save(a, listOf(saved(1), saved(2), saved(3)))
        var s = session(index = index)
        s.open(a)
        s.toggle(a, PlaylistTarget(saved(3), a.value, saved(3))) // user removed 3 with the new app
        assertEquals(listOf(saved(1), saved(2)), v1Ids())

        // The user went back to an older APK, which rewrote V1: removed 2, added 4, and "re-added" the removed 3.
        v1Store().save(a, listOf(saved(1), saved(3), saved(4)))

        s = session(index = index)
        val shown = ready(s.open(a))
        assertEquals("4 was added by the old app and is kept; 3 is not brought back; 2 stays", listOf(saved(1), saved(2), saved(4)), shown)
        val p = v2State().projection
        assertEquals(1, p.conflictImported)
        assertEquals(1, p.conflictSkipped)
        assertEquals(1, p.conflictIgnoredRemovals)
        assertEquals("V1 copy is now the projection of the merged V2", listOf(saved(1), saved(2), saved(4)), v1Ids())

        // Idempotent: another start finds nothing new.
        val again = session(index = index)
        assertEquals(shown, ready(again.open(a)))
        assertEquals(1, v2State().projection.conflictImported)
        assertEquals(listOf(saved(1), saved(2), saved(4)), v2State().presentSongKeys(a.value))
    }

    @Test fun aV1CopyThatIsOnlyStaleBecauseOurOwnWriteFailedIsRepairedNotTreatedAsAConflict() {
        v1Store().save(a, listOf(saved(1)))
        val index = FakeIndex(savedItems(1, 3), a.value)
        var s = session(index = index)
        s.open(a)
        v1Io.writesAllowed = 0
        s.toggle(a, PlaylistTarget(saved(2), a.value, saved(2)))
        s.toggle(a, PlaylistTarget(saved(1), a.value, saved(1))) // removes 1, V1 copy still shows [1]
        v1Io.writesAllowed = Int.MAX_VALUE
        s = session(index = index)
        assertEquals(listOf(saved(2)), ready(s.open(a)))
        assertEquals("the removed song did not come back from the stale copy", listOf(saved(2)), v1Ids())
        assertEquals(0, v2State().projection.conflictImported)
        assertEquals(0, v2State().projection.conflictSkipped)
    }

    @Test fun anUnreadableV1CopyIsLeftAloneAndV2KeepsWorking() {
        v1Store().save(a, listOf(saved(1)))
        val index = FakeIndex(savedItems(1, 2), a.value)
        session(index = index).open(a)
        val damaged = v1File(a).readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        v1File(a).writeBytes(damaged)
        val s = session(index = index)
        assertEquals(listOf(saved(1)), ready(s.open(a)))
        assertEquals(ToggleOutcome.Added(listOf(saved(1), saved(2))), s.toggle(a, PlaylistTarget(saved(2), a.value, saved(2))))
        assertArrayEquals("the unreadable V1 copy is never replaced", damaged, v1File(a).readBytes())
        assertEquals(listOf(saved(1), saved(2)), v2State().presentSongKeys(a.value))
    }

    @Test fun v1MissingWhileV2HasSongsIsRebuiltNotReadAsEmptyAndNeverWipesV2() {
        v1Store().save(a, listOf(saved(1), saved(2)))
        val index = FakeIndex(savedItems(1, 2), a.value)
        session(index = index).open(a)
        v1File(a).delete()
        val s = session(index = index)
        assertEquals(listOf(saved(1), saved(2)), ready(s.open(a)))
        assertEquals(listOf(saved(1), saved(2)), v1Ids())
    }

    @Test fun theCodeLevelSwitchStartsOn() {
        assertTrue(TelegramPlaylistFlags.V2_ENABLED)
    }
}
