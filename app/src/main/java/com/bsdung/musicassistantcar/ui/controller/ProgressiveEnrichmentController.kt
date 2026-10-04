package com.bsdung.musicassistantcar.ui.controller

import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import com.bsdung.musicassistantcar.data.MediaTechnicalSummary
import com.bsdung.musicassistantcar.data.RemoteCatalog
import com.bsdung.musicassistantcar.ui.model.LibraryItem
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicLong

data class CandidateResult(
    val cacheHits: List<Pair<String, MediaTechnicalSummary>>,
    val idsToFetch: List<String>,
)

interface EnrichmentScheduler {
    fun post(runnable: Runnable)
    fun postDelayed(runnable: Runnable, delayMs: Long)
    fun removeCallbacks(runnable: Runnable)
}

class HandlerEnrichmentScheduler(private val handler: Handler) : EnrichmentScheduler {
    override fun post(runnable: Runnable) { handler.post(runnable) }
    override fun postDelayed(runnable: Runnable, delayMs: Long) { handler.postDelayed(runnable, delayMs) }
    override fun removeCallbacks(runnable: Runnable) { handler.removeCallbacks(runnable) }
}

class ProgressiveEnrichmentController(
    private val catalog: RemoteCatalog? = null,
    private val executor: ExecutorService,
    mainHandler: Handler? = null,
    private val maxCacheEntries: Int = 300,
    private val maxNegativeCacheEntries: Int = 500,
    private val debounceMs: Long = 200L,
    private val onUpdateTrack: (mediaId: String, summary: MediaTechnicalSummary) -> Unit,
    private val getCurrentItems: () -> List<LibraryItem>,
    scheduler: EnrichmentScheduler? = null,
    private val batchFetcher: ((List<String>) -> Map<String, MediaTechnicalSummary>?)? = null,
) {
    private val scheduler: EnrichmentScheduler = scheduler ?: run {
        val h = mainHandler ?: Handler(Looper.getMainLooper())
        HandlerEnrichmentScheduler(h)
    }
    private val currentGeneration = AtomicLong(0L)
    private val lock = Any()

    @VisibleForTesting
    val cache = object : LinkedHashMap<String, MediaTechnicalSummary>(maxCacheEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaTechnicalSummary>?): Boolean {
            return size > maxCacheEntries
        }
    }

    @VisibleForTesting
    val negativeCache = object : LinkedHashMap<String, Boolean>(maxNegativeCacheEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > maxNegativeCacheEntries
        }
    }

    private var pendingEnrichmentRunnable: Runnable? = null

    fun getGeneration(): Long = currentGeneration.get()

    fun nextGeneration(): Long {
        cancel()
        return currentGeneration.incrementAndGet()
    }

    fun clearCaches() {
        synchronized(lock) {
            cache.clear()
            negativeCache.clear()
        }
    }

    fun getFromCache(mediaId: String): MediaTechnicalSummary? {
        synchronized(lock) {
            return cache[mediaId]
        }
    }

    fun putInCache(mediaId: String, summary: MediaTechnicalSummary) {
        synchronized(lock) {
            cache[mediaId] = summary
            negativeCache.remove(mediaId)
        }
    }

    fun isInNegativeCache(mediaId: String): Boolean {
        synchronized(lock) {
            return negativeCache.containsKey(mediaId)
        }
    }

    fun putInNegativeCache(mediaId: String) {
        synchronized(lock) {
            negativeCache[mediaId] = true
        }
    }

    fun isTrackSufficientlyEnriched(track: LibraryItem.Track): Boolean {
        return !track.format.isNullOrBlank() &&
                track.sampleRate != null && track.sampleRate > 0 &&
                track.bitrate != null && track.bitrate > 0
    }

    fun hasAnyTechnicalInfo(summary: MediaTechnicalSummary): Boolean {
        return !summary.format.isNullOrBlank() ||
                !summary.codec.isNullOrBlank() ||
                (summary.sampleRate != null && summary.sampleRate > 0) ||
                (summary.bitrate != null && summary.bitrate > 0) ||
                (summary.sizeBytes != null && summary.sizeBytes > 0L)
    }

    fun computeCandidates(
        firstVisible: Int,
        lastVisible: Int,
        items: List<LibraryItem>,
        prefetchMargin: Int = 5,
        maxBatch: Int = 50,
    ): CandidateResult {
        if (items.isEmpty() || firstVisible < 0 || lastVisible < 0) {
            return CandidateResult(emptyList(), emptyList())
        }

        val start = (firstVisible - prefetchMargin).coerceAtLeast(0)
        val end = (lastVisible + prefetchMargin).coerceAtMost(items.size - 1)
        if (start > end) return CandidateResult(emptyList(), emptyList())

        val slice = items.subList(start, end + 1)
        val cacheHits = mutableListOf<Pair<String, MediaTechnicalSummary>>()
        val toFetch = mutableListOf<String>()

        for (item in slice) {
            if (item !is LibraryItem.Track) continue
            val mid = item.id.trim()
            if (mid.isBlank()) continue

            // If item is already enriched in memory/model, skip
            if (isTrackSufficientlyEnriched(item)) continue

            // If in negative cache (known to have no technical info in DB), skip
            if (isInNegativeCache(mid)) continue

            // If in-memory cache hit, return immediately
            val cached = getFromCache(mid)
            if (cached != null) {
                cacheHits.add(mid to cached)
                continue
            }

            // Candidate for batch fetch
            if (!toFetch.contains(mid)) {
                toFetch.add(mid)
                if (toFetch.size >= maxBatch) break
            }
        }

        return CandidateResult(cacheHits, toFetch)
    }

    fun scheduleEnrichment(
        firstVisible: Int,
        lastVisible: Int,
        items: List<LibraryItem>,
        prefetchMargin: Int = 5,
        maxBatch: Int = 50,
    ) {
        val candidates = computeCandidates(firstVisible, lastVisible, items, prefetchMargin, maxBatch)

        // 1. Immediately apply cache hits to visible rows without network
        candidates.cacheHits.forEach { (mid, summary) ->
            onUpdateTrack(mid, summary)
        }

        val idsToFetch = candidates.idsToFetch
        if (idsToFetch.isEmpty()) {
            return
        }

        // 2. Debounce network batch fetch
        pendingEnrichmentRunnable?.let { scheduler.removeCallbacks(it) }
        val requestGen = currentGeneration.get()
        val runnable = Runnable {
            dispatchBatchFetch(requestGen, idsToFetch)
        }
        pendingEnrichmentRunnable = runnable
        scheduler.postDelayed(runnable, debounceMs)
    }

    private fun dispatchBatchFetch(requestGen: Long, idsToFetch: List<String>) {
        executor.submit {
            val response = runCatching {
                batchFetcher?.invoke(idsToFetch) ?: catalog?.getMediaInfoBatch(idsToFetch)
            }.getOrNull()
            scheduler.post {
                handleBatchResponse(requestGen, idsToFetch, response)
            }
        }
    }

    fun handleBatchResponse(
        requestGeneration: Long,
        requestedIds: List<String>,
        response: Map<String, MediaTechnicalSummary>?,
    ) {
        if (response == null) {
            // Network failure: keep basic rows visible, no crash, no toast
            return
        }

        val isStaleGeneration = requestGeneration != currentGeneration.get()
        val currentItems = getCurrentItems()
        val currentIds = currentItems.filterIsInstance<LibraryItem.Track>().map { it.id }.toSet()

        for (mid in requestedIds) {
            val summary = response[mid]
            if (summary != null && hasAnyTechnicalInfo(summary)) {
                putInCache(mid, summary)
                // If current generation or mediaId is still in the active dataset:
                if (!isStaleGeneration || currentIds.contains(mid)) {
                    onUpdateTrack(mid, summary)
                }
            } else {
                // Not in DB or no technical info: add to negative cache
                putInNegativeCache(mid)
            }
        }
    }

    fun cancel() {
        pendingEnrichmentRunnable?.let { scheduler.removeCallbacks(it) }
        pendingEnrichmentRunnable = null
    }
}
