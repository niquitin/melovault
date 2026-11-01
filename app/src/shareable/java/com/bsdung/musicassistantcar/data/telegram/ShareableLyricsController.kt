package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Candidate
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.FetchDecision
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Meta
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Pick
import com.bsdung.musicassistantcar.lyrics.LrcParser
import com.bsdung.musicassistantcar.lyrics.LyricsLine
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/** What the lyrics pane shows. Built from the cache or the network; never carries player state (section 4 of the plan). */
internal sealed class LyricsState {
    object Off : LyricsState()
    object NeedConsent : LyricsState()
    object MissingMetadata : LyricsState()
    object Loading : LyricsState()
    data class Synced(val lines: List<LyricsLine>, val fromCache: Boolean, val manual: Boolean) : LyricsState()
    data class Plain(val text: String, val fromCache: Boolean, val manual: Boolean) : LyricsState()
    object Instrumental : LyricsState()

    /** Nothing found. [canSearch] = the manual search button may be offered (always true today). */
    data class NoMatch(val canSearch: Boolean) : LyricsState()
    data class Ambiguous(val candidates: List<Candidate>) : LyricsState()
    data class TemporaryError(val retryAfterMs: Long?) : LyricsState()
}

/** The user's answer to the first-time consent box; the controller only reads it. */
internal enum class LyricsConsent { UNDECIDED, GRANTED, DENIED }

/** Result of a manual search typed by the user. */
internal sealed class LyricsManualSearch {
    data class Results(val candidates: List<Candidate>) : LyricsManualSearch()
    data class TemporaryError(val retryAfterMs: Long?) : LyricsManualSearch()
}

/**
 * Order of work for one song: consent -> cache -> network -> publish. [executor] must run tasks one at a
 * time off the main thread; [post] hands a result to the main thread. A result is published only if the
 * owner, generation and song signature are still the current ones, so a slow answer for an old song,
 * another account, or a closed tab is dropped. The controller has no reference to the player or queue,
 * and any failure becomes [LyricsState.TemporaryError].
 */
internal class ShareableLyricsController(
    private val cache: ShareableLyricsCache,
    private val provider: ShareableLyricsProvider,
    private val executor: Executor,
    private val post: (Runnable) -> Unit,
    private val consent: () -> LyricsConsent,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {}
) {

    private class Current(val owner: String, val signature: String, val generation: Int)

    private val counter = AtomicInteger(0)

    @Volatile
    private var current: Current? = null

    /**
     * Ask for the lyrics of the song that is now playing. Call on the main thread; [publish] is always called on it.
     * Any earlier request is cancelled by the new generation.
     */
    fun request(ownerId: String, meta: Meta, publish: (LyricsState) -> Unit) {
        val signature = MeloLyricsPolicy.signature(meta)
        val now = Current(ownerId, signature, counter.incrementAndGet())
        current = now
        when (consent()) {
            LyricsConsent.UNDECIDED -> return publish(LyricsState.NeedConsent)
            LyricsConsent.DENIED -> return publish(LyricsState.Off)
            LyricsConsent.GRANTED -> Unit
        }
        publish(LyricsState.Loading)
        executor.execute {
            val state = guarded { resolve(ownerId, signature, meta) }
            deliver(now) { publish(state) }
        }
    }

    /** Tab closed, account changed or logout: drop whatever is in flight. */
    fun cancel() {
        counter.incrementAndGet()
        current = null
    }

    /** Manual picker search (LD10). Dropped like any other result if the song changed meanwhile. */
    fun searchManual(ownerId: String, meta: Meta, query: String, publish: (LyricsManualSearch) -> Unit) {
        val signature = MeloLyricsPolicy.signature(meta)
        val now = current?.takeIf { it.owner == ownerId && it.signature == signature } ?: return
        if (consent() != LyricsConsent.GRANTED) return
        executor.execute {
            val result = try {
                when (val found = provider.searchText(query)) {
                    is ShareableLyricsProvider.SearchResult.Results ->
                        LyricsManualSearch.Results(found.candidates.take(MeloLyricsPolicy.MAX_SEARCH_RESULTS))
                    is ShareableLyricsProvider.SearchResult.TemporaryError -> LyricsManualSearch.TemporaryError(found.retryAfterMs)
                }
            } catch (e: Exception) {
                log("stage=lyrics_error where=search")
                LyricsManualSearch.TemporaryError(null)
            }
            deliver(now) { publish(result) }
        }
    }

    /** The user picked [candidate] for this song: remember it for good and show it. */
    fun choose(ownerId: String, meta: Meta, candidate: Candidate): LyricsState {
        val signature = MeloLyricsPolicy.signature(meta)
        val entry = ShareableLyricsCache.Entry(
            lrclibId = candidate.id,
            synced = candidate.syncedLyrics,
            plain = candidate.plainLyrics,
            instrumental = candidate.instrumental,
            fetchedAtMs = nowMs(),
            manual = true
        )
        cache.put(ownerId, signature, entry)
        return stateOf(entry, fromCache = false) ?: LyricsState.NoMatch(canSearch = true)
    }

    /** "Bỏ lời đã chọn". */
    fun clearChoice(ownerId: String, meta: Meta) {
        cache.remove(ownerId, MeloLyricsPolicy.signature(meta))
    }

    /** Logout of one account (LD7). */
    fun clearOwner(ownerId: String) {
        cancel()
        cache.clearOwner(ownerId)
    }

    private fun deliver(request: Current, action: () -> Unit) {
        post(Runnable {
            val live = current
            if (live != null && live.generation == request.generation && live.owner == request.owner && live.signature == request.signature) {
                action()
            } else {
                log("stage=lyrics_drop")
            }
        })
    }

    private fun guarded(block: () -> LyricsState): LyricsState = try {
        block()
    } catch (e: Exception) {
        log("stage=lyrics_error where=resolve")
        LyricsState.TemporaryError(null)
    }

    private fun resolve(ownerId: String, signature: String, meta: Meta): LyricsState {
        cache.get(ownerId, signature)?.let { hit ->
            log("stage=lyrics_cache hit=true")
            return if (hit.noMatch) LyricsState.NoMatch(canSearch = true) else stateOf(hit, fromCache = true) ?: LyricsState.NoMatch(canSearch = true)
        }
        when (MeloLyricsPolicy.shouldAutoFetch(meta)) {
            FetchDecision.MISSING_METADATA -> return LyricsState.MissingMetadata
            FetchDecision.TOO_LONG -> return LyricsState.NoMatch(canSearch = true)
            FetchDecision.FETCH -> Unit
        }
        when (val exact = provider.get(meta)) {
            is ShareableLyricsProvider.GetResult.Found -> return store(ownerId, signature, exact.candidate)
            is ShareableLyricsProvider.GetResult.TemporaryError -> return LyricsState.TemporaryError(exact.retryAfterMs)
            ShareableLyricsProvider.GetResult.NoMatch -> Unit
        }
        return when (val found = provider.search(meta)) {
            is ShareableLyricsProvider.SearchResult.TemporaryError -> LyricsState.TemporaryError(found.retryAfterMs)
            is ShareableLyricsProvider.SearchResult.Results -> when (val pick = MeloLyricsPolicy.pickCandidate(found.candidates, meta.durationSec)) {
                is Pick.Chosen -> store(ownerId, signature, pick.candidate)
                is Pick.Ambiguous -> LyricsState.Ambiguous(pick.candidates)
                Pick.None -> {
                    cache.putNoMatch(ownerId, signature)
                    LyricsState.NoMatch(canSearch = true)
                }
            }
        }
    }

    private fun store(ownerId: String, signature: String, candidate: Candidate): LyricsState {
        val entry = ShareableLyricsCache.Entry(
            lrclibId = candidate.id,
            synced = candidate.syncedLyrics,
            plain = candidate.plainLyrics,
            instrumental = candidate.instrumental,
            fetchedAtMs = nowMs(),
            manual = false
        )
        val state = stateOf(entry, fromCache = false)
        if (state == null) {
            cache.putNoMatch(ownerId, signature)
            return LyricsState.NoMatch(canSearch = true)
        }
        cache.put(ownerId, signature, entry)
        return state
    }

    /** Instrumental only when the record says so (LD6); synced wins over plain; a record with no text is no match. */
    private fun stateOf(entry: ShareableLyricsCache.Entry, fromCache: Boolean): LyricsState? {
        if (entry.instrumental) return LyricsState.Instrumental
        val lines = LrcParser.parse(entry.synced)
        if (lines.isNotEmpty()) return LyricsState.Synced(lines, fromCache, entry.manual)
        val plain = entry.plain?.trim()
        if (!plain.isNullOrEmpty()) return LyricsState.Plain(plain, fromCache, entry.manual)
        return null
    }
}
