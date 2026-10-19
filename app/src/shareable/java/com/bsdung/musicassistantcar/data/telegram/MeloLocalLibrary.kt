package com.bsdung.musicassistantcar.data.telegram

import android.util.Log
import com.bsdung.musicassistantcar.data.LocalMediaRepository
import com.bsdung.musicassistantcar.data.telegram.MeloLocalSourcePolicy.LocalSong
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

/**
 * State of the "Nhạc trên máy" list (docs/plans/E94-LOCAL-MUSIC.md). Read-only: it only calls
 * [LocalMediaRepository.hasPermission], [LocalMediaRepository.getAllTracks] and
 * [LocalMediaRepository.invalidateCache], always on [executor] (the repository holds one lock
 * for the whole MediaStore query). Results come back through [post] (the main thread) and are
 * dropped when the library was cleared/shut down or a newer request replaced them. Everything
 * the screens read ([count], [shown], [visible], [version], …) is touched on the main thread.
 * It knows nothing about the Activity, MediaController, TDLib or the Telegram index, and logs
 * only numbers (never song names, paths or URIs).
 */
internal class MeloLocalLibrary(
    private val repository: LocalMediaRepository,
    private val countQuery: () -> Int?,
    private val executor: Executor,
    private val post: (Runnable) -> Unit,
    private val clock: () -> Long,
    private val log: (String) -> Unit = { Log.w(TAG, it) }
) {
    private class Shown(val songs: List<LocalSong>)

    private val epoch = AtomicLong(0L)

    @Volatile private var closed = false
    @Volatile private var all: List<LocalSong> = emptyList()
    @Volatile private var byId: Map<Long, LocalSong> = emptyMap()
    @Volatile private var view: Shown = Shown(emptyList())
    @Volatile private var keysCache: Pair<Shown, List<String>>? = null
    @Volatile private var countValue: Int? = null
    @Volatile private var visibleCount = MeloLocalSourcePolicy.PAGE_SIZE
    @Volatile private var loadSeq = 0
    @Volatile private var viewSeq = 0

    var loading: Boolean = false
        private set

    /** Clock value when the last load was applied; -1 = never loaded. */
    var loadedAt: Long = -1L
        private set

    var query: String = ""
        private set

    var sort: SongSort = SongSort.NEWEST
        private set

    /** Grows every time the list on screen changes (new data, query, sort, more rows). */
    var version: Int = 0
        private set

    val hasLoaded: Boolean get() = loadedAt >= 0L

    /** Never loaded, or loaded more than [MeloLocalSourcePolicy.RELOAD_AFTER_MS] ago. */
    fun needsLoad(): Boolean = !hasLoaded || clock() - loadedAt > MeloLocalSourcePolicy.RELOAD_AFTER_MS

    /** Songs for the source card: null = unknown (no permission yet / query failed). */
    fun count(): Int? = countValue

    /** Songs read by the last load, before search (0 until a load finished). */
    val loadedCount: Int get() = all.size

    /** Whole list after search and sort. */
    fun shown(): List<LocalSong> = view.songs

    /** The part of [shown] the screen currently holds (pages of [MeloLocalSourcePolicy.PAGE_SIZE]). */
    fun visible(): List<LocalSong> {
        val list = view.songs
        return if (visibleCount >= list.size) list else list.subList(0, visibleCount)
    }

    fun byId(id: Long): LocalSong? = byId[id]

    /** Quick MediaStore count (no permission prompt); a failure or missing permission gives null. */
    fun refreshCount(onDone: () -> Unit = {}) {
        if (closed) return
        val stamp = epoch.get()
        submit {
            val value = try {
                if (repository.hasPermission()) countQuery() else null
            } catch (e: Throwable) {
                null
            }
            post(Runnable {
                if (!closed && stamp == epoch.get()) {
                    countValue = value
                    onDone()
                }
            })
        }
    }

    /** Reads the whole MediaStore list ([force] drops the repository cache first). */
    fun load(force: Boolean, onDone: () -> Unit = {}) {
        if (closed) return
        val stamp = epoch.get()
        val seq = ++loadSeq
        loading = true
        val startQuery = query
        val startSort = sort
        submit {
            val started = clock()
            var songs: List<LocalSong>? = null
            var granted = false
            try {
                if (force) repository.invalidateCache()
                granted = repository.hasPermission()
                songs = if (!granted) emptyList() else repository.getAllTracks().mapNotNull {
                    MeloLocalSourcePolicy.songOf(
                        it.id, it.title, it.artist, it.album, it.relativePath, it.durationMs, it.sizeBytes, it.mimeType
                    )
                }
            } catch (e: Throwable) {
                log("stage=load_failed error=${e.javaClass.simpleName} gen=$stamp")
            }
            val loaded = songs
            val computed = loaded?.let { Shown(computeView(it, startQuery, startSort)) }
            val ms = clock() - started
            if (loaded != null) {
                log("stage=load count=${loaded.size} ms=$ms gen=$stamp")
                log("stage=formats ${MeloLocalSourcePolicy.formatCounts(loaded.map { it.mimeType })}")
            }
            post(Runnable {
                if (closed || stamp != epoch.get() || seq != loadSeq) return@Runnable
                loading = false
                if (loaded != null && computed != null) {
                    all = loaded
                    byId = loaded.associateBy { it.mediaStoreId }
                    countValue = if (granted) loaded.size else null
                    loadedAt = clock()
                    if (query == startQuery && sort == startSort) {
                        applyView(computed)
                    } else {
                        applyView(Shown(computeView(loaded, query, sort)))
                    }
                }
                onDone()
            })
        }
    }

    fun setQuery(newQuery: String, onDone: () -> Unit = {}) {
        if (closed || newQuery == query) return
        query = newQuery
        rebuild(onDone)
    }

    fun setSort(newSort: SongSort, onDone: () -> Unit = {}) {
        if (closed || newSort == sort) return
        sort = newSort
        rebuild(onDone)
    }

    /** Shows one more page; false when everything is already shown. */
    fun loadMore(): Boolean {
        val size = view.songs.size
        if (visibleCount >= size) return false
        visibleCount = minOf(size, visibleCount + MeloLocalSourcePolicy.PAGE_SIZE)
        version++
        return true
    }

    /**
     * Index of the first song whose sort key is >= [lowerBound] (null = start) while sorted by
     * name; the visible part grows to hold it. Always a valid index (0 when the list is empty or
     * not sorted by name).
     */
    fun jumpTo(lowerBound: String?): Int {
        val songs = view.songs
        if (songs.isEmpty() || sort != SongSort.NAME) return 0
        val current = view
        val keys = keysCache?.takeIf { it.first === current }?.second
            ?: songs.map { it.alphaKey }.also { keysCache = current to it }
        val index = MeloAlphabetPolicy.firstIndexAtOrAfter(keys, lowerBound).coerceIn(0, songs.size - 1)
        if (index >= visibleCount) {
            val page = MeloLocalSourcePolicy.PAGE_SIZE
            visibleCount = minOf(songs.size, (index / page + 1) * page)
            version++
        }
        return index
    }

    /** Forgets everything now (logout, account switch, leaving local music); pending work is dropped. */
    fun clear() {
        epoch.incrementAndGet()
        loadSeq++
        viewSeq++
        loading = false
        all = emptyList()
        byId = emptyMap()
        countValue = null
        loadedAt = -1L
        query = ""
        view = Shown(emptyList())
        keysCache = null
        visibleCount = MeloLocalSourcePolicy.PAGE_SIZE
        version++
        submit { repository.invalidateCache() }
    }

    fun shutdown() {
        closed = true
        epoch.incrementAndGet()
        (executor as? ExecutorService)?.shutdownNow()
    }

    private fun rebuild(onDone: () -> Unit) {
        val stamp = epoch.get()
        val seq = ++viewSeq
        val source = all
        val wantedQuery = query
        val wantedSort = sort
        submit {
            val computed = Shown(computeView(source, wantedQuery, wantedSort))
            post(Runnable {
                if (closed || stamp != epoch.get() || seq != viewSeq) return@Runnable
                // A load that finished in between replaced [all]; recompute from the fresh data.
                applyView(if (source === all) computed else Shown(computeView(all, query, sort)))
                onDone()
            })
        }
    }

    private fun applyView(newView: Shown) {
        view = newView
        keysCache = null
        visibleCount = MeloLocalSourcePolicy.PAGE_SIZE
        version++
    }

    private fun computeView(songs: List<LocalSong>, query: String, sort: SongSort): List<LocalSong> =
        MeloLocalSourcePolicy.sorted(MeloLocalSourcePolicy.filtered(songs, query), sort)

    private fun submit(block: () -> Unit) {
        try {
            executor.execute(Runnable { block() })
        } catch (e: RejectedExecutionException) {
            loading = false
        }
    }

    companion object {
        private const val TAG = "MeloVaultLocal"
    }
}
