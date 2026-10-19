package com.bsdung.musicassistantcar.data.telegram

import android.net.Uri
import com.bsdung.musicassistantcar.data.LocalMediaRepository
import com.bsdung.musicassistantcar.data.LocalMediaTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class MeloLocalLibraryTest {

    private fun track(id: Long, title: String, artist: String = "Artist", album: String = "", path: String = "Music/Test") =
        LocalMediaTrack(
            id = id,
            volumeName = "external_primary",
            title = title,
            artist = artist,
            album = album,
            durationMs = 1000L,
            sizeBytes = 2000L,
            mimeType = if (id % 2 == 0L) "audio/flac" else "audio/mpeg",
            relativePath = path,
            contentUri = Uri.parse("content://media/external/audio/media/$id")
        )

    private class Harness(
        tracks: List<LocalMediaTrack>,
        granted: Boolean = true,
        countQuery: () -> Int? = { tracks.size },
        deferExecutor: Boolean = false
    ) {
        val pending = ArrayDeque<Runnable>()
        val logs = mutableListOf<String>()
        var invalidations = 0
        var providerCalls = 0
        var permission = granted
        var now = 1_000L
        val repository = LocalMediaRepository(
            permissionChecker = { permission },
            tracksProvider = { providerCalls++; tracks }
        )
        val executor = Executor { if (deferExecutor) pending.add(it) else it.run() }
        val library = MeloLocalLibrary(
            repository = repository,
            countQuery = countQuery,
            executor = executor,
            post = { it.run() },
            clock = { now },
            log = { logs.add(it) }
        )

        fun runPending() {
            while (pending.isNotEmpty()) pending.removeFirst().run()
        }
    }

    private fun many(n: Int): List<LocalMediaTrack> = (1..n).map { track(it.toLong(), "Bài %04d".format(it)) }

    @Test
    fun noPermissionGivesEmptyListAndNullCount() {
        val h = Harness(many(5), granted = false)
        var done = 0
        h.library.load(force = false) { done++ }
        assertEquals(1, done)
        assertTrue(h.library.shown().isEmpty())
        assertNull(h.library.count())
        assertTrue(h.library.hasLoaded)
        assertFalse(h.library.loading)
        h.library.refreshCount()
        assertNull(h.library.count())
    }

    @Test
    fun withPermissionLoadsEverySongAndSetsCount() {
        val h = Harness(many(37))
        h.library.load(force = false)
        assertEquals(37, h.library.shown().size)
        assertEquals(37, h.library.visible().size)
        assertEquals(37, h.library.count())
        assertEquals(1_000L, h.library.loadedAt)
        // NEWEST by default: highest MediaStore id first.
        assertEquals(37L, h.library.shown().first().mediaStoreId)
        assertTrue(h.logs.any { it.startsWith("stage=load count=37 ms=") })
        assertTrue(h.logs.any { it == "stage=formats audio/mpeg=19 audio/flac=18" })
    }

    @Test
    fun pagingGrowsByPageAndStopsAtTheEnd() {
        val h = Harness(many(450))
        h.library.load(force = false)
        assertEquals(200, h.library.visible().size)
        assertEquals(450, h.library.shown().size)
        val v0 = h.library.version
        assertTrue(h.library.loadMore())
        assertEquals(400, h.library.visible().size)
        assertTrue(h.library.version > v0)
        assertTrue(h.library.loadMore())
        assertEquals(450, h.library.visible().size)
        val v1 = h.library.version
        assertFalse(h.library.loadMore())
        assertEquals(450, h.library.visible().size)
        assertEquals(v1, h.library.version)
    }

    @Test
    fun setQueryFiltersWithoutAccentsAndBumpsVersion() {
        val tracks = listOf(
            track(1, "Sử Thanh Hóa"), track(2, "Biển nhớ"), track(3, "Thanh xuân", artist = "Ai đó")
        )
        val h = Harness(tracks)
        h.library.load(force = false)
        val v0 = h.library.version
        var done = 0
        h.library.setQuery("thanh") { done++ }
        assertEquals(1, done)
        assertEquals(listOf(3L, 1L), h.library.shown().map { it.mediaStoreId })
        assertTrue(h.library.version > v0)
        h.library.setQuery("su thanh hoa")
        assertEquals(listOf(1L), h.library.shown().map { it.mediaStoreId })
        h.library.setQuery("không có")
        assertTrue(h.library.shown().isEmpty())
        h.library.setQuery("")
        assertEquals(3, h.library.shown().size)
        // Same query again does nothing.
        val v1 = h.library.version
        var again = 0
        h.library.setQuery("") { again++ }
        assertEquals(0, again)
        assertEquals(v1, h.library.version)
    }

    @Test
    fun setSortChangesOrder() {
        val tracks = listOf(track(1, "Zebra"), track(2, "Ánh"), track(3, "Bình"))
        val h = Harness(tracks)
        h.library.load(force = false)
        assertEquals(listOf(3L, 2L, 1L), h.library.shown().map { it.mediaStoreId })
        h.library.setSort(SongSort.NAME)
        assertEquals(listOf(2L, 3L, 1L), h.library.shown().map { it.mediaStoreId })
        assertEquals(SongSort.NAME, h.library.sort)
        h.library.setSort(SongSort.NEWEST)
        assertEquals(listOf(3L, 2L, 1L), h.library.shown().map { it.mediaStoreId })
    }

    @Test
    fun jumpToReturnsIndexAndWidensVisible() {
        // 26 letters x 20 songs = 520 songs, name sorted.
        val tracks = ('a'..'z').flatMapIndexed { li, letter ->
            (0 until 20).map { n -> track(li * 20L + n + 1, "$letter song %02d".format(n), artist = "Artist") }
        }
        val h = Harness(tracks)
        h.library.load(force = false)
        h.library.setSort(SongSort.NAME)
        assertEquals(200, h.library.visible().size)
        val index = h.library.jumpTo("m")
        assertEquals(12 * 20, index)
        assertTrue("m" in h.library.shown()[index].alphaKey.take(1))
        assertEquals(400, h.library.visible().size)
        val z = h.library.jumpTo("z")
        assertEquals(25 * 20, z)
        assertEquals(520, h.library.visible().size)
        assertEquals(0, h.library.jumpTo(null))
        // Past the last key: clamped to a valid index.
        assertEquals(519, h.library.jumpTo("zzzz"))
    }

    @Test
    fun jumpToIsNoopWhenNotSortedByNameOrEmpty() {
        val h = Harness(many(10))
        h.library.load(force = false)
        assertEquals(0, h.library.jumpTo("m"))
        val empty = Harness(emptyList())
        empty.library.load(force = false)
        empty.library.setSort(SongSort.NAME)
        assertEquals(0, empty.library.jumpTo("m"))
    }

    @Test
    fun clearDropsALoadThatWasStillRunning() {
        val h = Harness(many(10), deferExecutor = true)
        var done = 0
        h.library.load(force = false) { done++ }
        assertTrue(h.library.loading)
        h.library.clear()
        h.runPending()
        assertEquals(0, done)
        assertTrue(h.library.shown().isEmpty())
        assertNull(h.library.count())
        assertFalse(h.library.hasLoaded)
        assertFalse(h.library.loading)
        // The invalidation queued by clear() ran on the executor too.
        h.library.load(force = false) { done++ }
        h.runPending()
        assertEquals(1, done)
        assertEquals(10, h.library.shown().size)
    }

    @Test
    fun countQueryFailureGivesNullWithoutCrash() {
        val h = Harness(many(4), countQuery = { throw IllegalStateException("boom") })
        h.library.refreshCount()
        assertNull(h.library.count())
        val nullCount = Harness(many(4), countQuery = { null })
        nullCount.library.refreshCount()
        assertNull(nullCount.library.count())
        val ok = Harness(many(4), countQuery = { 4 })
        var done = 0
        ok.library.refreshCount { done++ }
        assertEquals(4, ok.library.count())
        assertEquals(1, done)
    }

    @Test
    fun byIdFindsSongsAndRejectsUnknown() {
        val h = Harness(many(5))
        h.library.load(force = false)
        val song = h.library.byId(3)
        assertNotNull(song)
        assertEquals("Bài 0003", song!!.title)
        assertNull(h.library.byId(99))
        assertNull(h.library.byId(-1))
    }

    @Test
    fun forceLoadReadsAgainAndPlainLoadUsesRepositoryCache() {
        val h = Harness(many(3))
        h.library.load(force = false)
        h.library.load(force = false)
        assertEquals(1, h.providerCalls)
        h.library.load(force = true)
        assertEquals(2, h.providerCalls)
    }

    @Test
    fun needsLoadAfterFiveMinutes() {
        val h = Harness(many(3))
        assertTrue(h.library.needsLoad())
        h.library.load(force = false)
        assertFalse(h.library.needsLoad())
        h.now += MeloLocalSourcePolicy.RELOAD_AFTER_MS
        assertFalse(h.library.needsLoad())
        h.now += 1
        assertTrue(h.library.needsLoad())
    }

    @Test
    fun songsWithInvalidIdsAreSkipped() {
        val h = Harness(listOf(track(0, "zero"), track(-3, "neg"), track(7, "ok")))
        h.library.load(force = false)
        assertEquals(listOf(7L), h.library.shown().map { it.mediaStoreId })
        assertEquals(1, h.library.count())
    }

    @Test
    fun queryTypedWhileLoadingIsAppliedToTheNewData() {
        val h = Harness(listOf(track(1, "Alpha"), track(2, "Beta")), deferExecutor = true)
        h.library.load(force = false)
        h.library.setQuery("beta")
        h.runPending()
        assertEquals(listOf(2L), h.library.shown().map { it.mediaStoreId })
        assertEquals("beta", h.library.query)
        assertFalse(h.library.loading)
    }

    @Test
    fun shutdownStopsEverything() {
        val h = Harness(many(3), deferExecutor = true)
        h.library.load(force = false)
        h.library.shutdown()
        h.runPending()
        assertTrue(h.library.shown().isEmpty())
        h.library.load(force = false)
        h.library.refreshCount()
        h.library.setQuery("x")
        assertTrue(h.pending.isEmpty())
    }

    @Test
    fun logsContainNoSongNamesOrPaths() {
        val h = Harness(listOf(track(1, "Bí Mật Riêng Tư", artist = "Ca sĩ X", path = "Music/Thư mục kín")))
        h.library.load(force = true)
        val joined = h.logs.joinToString("\n")
        assertFalse(joined.contains("Bí Mật"))
        assertFalse(joined.contains("Thư mục"))
        assertFalse(joined.contains("content://"))
        assertFalse(joined.contains("Ca sĩ"))
    }
}
