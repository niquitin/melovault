package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistSession.OpenResult
import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistSession.ToggleOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** In-memory stand-in for the encrypted file store, with switchable failures. */
private class FakePersistence : PlaylistPersistence {
    val stored = HashMap<TelegramUserId, List<Long>>()
    var unreadable: PlaylistUnreadableReason? = null
    var saveFailure: Exception? = null
    var saves = 0

    @Synchronized
    override fun load(user: TelegramUserId): PlaylistLoadResult {
        unreadable?.let { return PlaylistLoadResult.Unreadable(it) }
        return stored[user]?.let { PlaylistLoadResult.Ok(it) } ?: PlaylistLoadResult.Missing
    }

    @Synchronized
    override fun save(user: TelegramUserId, ids: List<Long>) {
        unreadable?.let { throw PlaylistUnreadableException(it) }
        saveFailure?.let { throw it }
        saves++
        stored[user] = ids
    }
}

class TelegramPlaylistSessionTest {
    private val a = TelegramUserId(1001L)
    private val b = TelegramUserId(2002L)

    private fun ids(n: Int): List<Long> = (1..n).map { it.toLong() }

    @Test fun neverSavedOpensEmptyAndFirstSaveIsStored() {
        val p = FakePersistence()
        val s = TelegramPlaylistSession(p)
        assertEquals(OpenResult.Ready(emptyList()), s.open(a))
        assertEquals(ToggleOutcome.Added(listOf(5L)), s.toggle(a, 5L))
        assertEquals(listOf(5L), p.stored[a])
    }

    @Test fun existingPlaylistIsKeptWhenAddingAndRemoving() {
        val p = FakePersistence().apply { stored[a] = listOf(1L, 2L, 3L) }
        val s = TelegramPlaylistSession(p)
        assertEquals(OpenResult.Ready(listOf(1L, 2L, 3L)), s.open(a))
        assertEquals(ToggleOutcome.Added(listOf(1L, 2L, 3L, 4L)), s.toggle(a, 4L))
        assertEquals(ToggleOutcome.Removed(listOf(1L, 3L, 4L)), s.toggle(a, 2L))
        assertEquals(listOf(1L, 3L, 4L), p.stored[a])
    }

    @Test fun readErrorBlocksSaveAndNeverCallsSave() {
        val p = FakePersistence().apply { stored[a] = ids(5); unreadable = PlaylistUnreadableReason.DECRYPT }
        val s = TelegramPlaylistSession(p)
        assertEquals(OpenResult.Blocked(PlaylistUnreadableReason.DECRYPT), s.open(a))
        assertEquals(ToggleOutcome.Blocked(PlaylistUnreadableReason.DECRYPT), s.toggle(a, 9L))
        assertEquals(ToggleOutcome.Blocked(PlaylistUnreadableReason.DECRYPT), s.toggle(a, 1L))
        assertEquals("no write may be attempted", 0, p.saves)
        assertEquals("the stored list is untouched", ids(5), p.stored[a])
    }

    @Test fun aTransientReadErrorRecoversWithoutLosingTheOldSongs() {
        val p = FakePersistence().apply { stored[a] = ids(5); unreadable = PlaylistUnreadableReason.DECRYPT }
        val s = TelegramPlaylistSession(p)
        assertTrue(s.open(a) is OpenResult.Blocked)
        p.unreadable = null // the Keystore came back
        assertEquals(ToggleOutcome.Added(ids(5) + 9L), s.toggle(a, 9L))
        assertEquals(ids(5) + 9L, p.stored[a])
    }

    @Test fun aSaveThatFailsChangesNothingAndTheNextOneStartsFromTheOldList() {
        val p = FakePersistence().apply { stored[a] = listOf(1L, 2L) }
        val s = TelegramPlaylistSession(p)
        s.open(a)
        p.saveFailure = IOException("disk full")
        assertEquals(ToggleOutcome.Failed, s.toggle(a, 3L))
        assertEquals(listOf(1L, 2L), s.snapshot())
        assertEquals(listOf(1L, 2L), p.stored[a])
        p.saveFailure = null
        assertEquals(ToggleOutcome.Added(listOf(1L, 2L, 3L)), s.toggle(a, 3L))
    }

    @Test fun aFullPlaylistRefusesAndKeepsEverything() {
        val p = FakePersistence().apply { stored[a] = ids(300) }
        val s = TelegramPlaylistSession(p)
        s.open(a)
        assertEquals(ToggleOutcome.Full(300), s.toggle(a, 301L))
        assertEquals(0, p.saves)
        assertEquals(ids(300), p.stored[a])
        // removing is still allowed, and then adding works again
        assertTrue(s.toggle(a, 1L) is ToggleOutcome.Removed)
        assertEquals(ToggleOutcome.Added(ids(300).drop(1) + 301L), s.toggle(a, 301L))
    }

    @Test fun at299OneMoreFitsThenTheNextIsRefused() {
        val p = FakePersistence().apply { stored[a] = ids(299) }
        val s = TelegramPlaylistSession(p)
        s.open(a)
        assertEquals(ToggleOutcome.Added(ids(299) + 300L), s.toggle(a, 300L))
        assertEquals(ToggleOutcome.Full(300), s.toggle(a, 301L))
    }

    @Test fun anotherAccountCannotWriteToTheOpenPlaylist() {
        val p = FakePersistence().apply { stored[a] = listOf(1L) }
        val s = TelegramPlaylistSession(p)
        s.open(a)
        assertEquals(ToggleOutcome.NotOpen, s.toggle(b, 2L))
        assertEquals(0, p.saves)
    }

    @Test fun switchingAccountsNeverMixesPlaylists() {
        val p = FakePersistence().apply { stored[a] = listOf(1L, 2L); stored[b] = listOf(9L) }
        val s = TelegramPlaylistSession(p)
        s.open(a)
        s.toggle(a, 3L)
        assertEquals(OpenResult.Ready(listOf(9L)), s.open(b))
        assertEquals(ToggleOutcome.NotOpen, s.toggle(a, 4L))
        assertEquals(ToggleOutcome.Added(listOf(9L, 10L)), s.toggle(b, 10L))
        assertEquals(OpenResult.Ready(listOf(1L, 2L, 3L)), s.open(a))
        assertEquals(listOf(9L, 10L), p.stored[b])
    }

    @Test fun afterCloseNothingCanBeWritten() {
        val p = FakePersistence().apply { stored[a] = listOf(1L) }
        val s = TelegramPlaylistSession(p)
        s.open(a)
        s.close()
        assertEquals(ToggleOutcome.NotOpen, s.toggle(a, 2L))
        assertEquals(emptyList<Long>(), s.snapshot())
        assertEquals(listOf(1L), p.stored[a])
    }

    @Test fun removeThenAddAgainWorksAndGoesToTheEnd() {
        val p = FakePersistence().apply { stored[a] = listOf(1L, 2L, 3L) }
        val s = TelegramPlaylistSession(p)
        s.open(a)
        assertEquals(ToggleOutcome.Removed(listOf(2L, 3L)), s.toggle(a, 1L))
        assertEquals(ToggleOutcome.Added(listOf(2L, 3L, 1L)), s.toggle(a, 1L))
    }

    @Test fun rapidSaveUnsaveOfOneSongAlternatesAndLosesNothing() {
        val p = FakePersistence().apply { stored[a] = listOf(1L, 2L) }
        val s = TelegramPlaylistSession(p)
        s.open(a)
        repeat(101) { s.toggle(a, 50L) }
        assertEquals("odd number of taps leaves it saved", listOf(1L, 2L, 50L), p.stored[a])
        assertEquals(101, p.saves)
        s.toggle(a, 50L)
        assertEquals(listOf(1L, 2L), p.stored[a])
    }

    @Test fun twoThreadsTappingAtOnceLoseNoUpdate() {
        val p = FakePersistence()
        val s = TelegramPlaylistSession(p)
        s.open(a)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val failures = AtomicInteger()
        for (base in listOf(1000L, 2000L)) {
            Thread {
                start.await()
                for (i in 1..100L) {
                    if (s.toggle(a, base + i) !is ToggleOutcome.Added) failures.incrementAndGet()
                }
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(0, failures.get())
        assertEquals(200, s.snapshot().size)
        assertEquals(200, p.stored[a]!!.size)
        assertEquals((1..100L).map { 1000L + it }, p.stored[a]!!.filter { it < 2000L })
        assertEquals((1..100L).map { 2000L + it }, p.stored[a]!!.filter { it >= 2000L })
    }

    @Test fun theWriterThreadRunsTasksInTheOrderTheyWereSubmitted() {
        val seen = java.util.Collections.synchronizedList(ArrayList<Int>())
        val done = CountDownLatch(1)
        for (i in 1..200) TelegramPlaylistRuntime.submit { seen.add(i) }
        TelegramPlaylistRuntime.submit { done.countDown() }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals((1..200).toList(), seen.toList())
    }

    @Test fun aTaskThatThrowsDoesNotStopTheWriterThread() {
        val done = CountDownLatch(1)
        TelegramPlaylistRuntime.submit { throw IllegalStateException("boom") }
        TelegramPlaylistRuntime.submit { done.countDown() }
        assertTrue(done.await(10, TimeUnit.SECONDS))
    }
}
