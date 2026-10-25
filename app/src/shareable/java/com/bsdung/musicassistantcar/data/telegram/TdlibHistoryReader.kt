package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject
import java.util.TreeMap

internal data class HistoryLimits(
    /** TDLib's own maximum per getChatHistory. */
    val pageLimit: Int = 100,
    /** More unread messages than this cannot be proven with the memory budget: fail closed. */
    val maxMessages: Int = 20_000,
    val maxChars: Long = 12_000_000L,
    /** One call of [TdlibHistoryReader.fetchAfter] stops after this many TDLib requests (BACKOFF); the scan resumes where it stood. */
    val maxRequestsPerCall: Int = 300,
    /** Consecutive answers that show nothing older before the scan gives up (BACKOFF). */
    val maxNoProgress: Int = 3,
    val requestTimeoutMs: Long = 20_000L,
    val snapshotTtlMs: Long = 120_000L,
) {
    init {
        require(pageLimit in 1..100 && maxMessages >= 1 && maxRequestsPerCall >= 1 && maxNoProgress >= 1)
    }
}

/**
 * E96 P3 - the history adapter, the data-safety heart of the TDLib transport.
 *
 * P2's transport contract ([PlaylistSyncTransport.fetchAfter]) asks for the OLDEST complete prefix of the messages
 * after a cursor, with a proof. TDLib's getChatHistory answers NEWEST first (decreasing message id) and may return
 * fewer messages than asked even when more exist. So a page can never be turned around and called complete.
 *
 * What this adapter does instead:
 *  1. **Reverse scan.** From the newest message (from_message_id 0) it walks down: each next request starts at the
 *     lowest id seen. It continues while it makes progress - a short page is NOT the end.
 *  2. **Proven lower boundary.** The scan is finished only when it has SEEN a message at or below the cursor (everything
 *     between that message and the newest one is then in hand), or - for a cursor of 0 - a message at or below
 *     [FIRST_CHANNEL_MESSAGE_ID] (a channel's very first possible message: server id 1 shifted by 20 bits).
 *     An empty or non-progressing answer is never taken for the beginning of the history: after
 *     [HistoryLimits.maxNoProgress] such answers in a row the call fails CLOSED with a BACKOFF.
 *  3. **Snapshot.** The proven messages (ascending) are kept in memory, bounded by [HistoryLimits], so the rest of the
 *     run is served from them without walking the whole history again for every page. Only the oldest [limit]
 *     above the requested cursor are returned, `completeThroughId` is the id of the last one (or the newest scanned id
 *     when nothing more is known), and `hasMore` says whether the snapshot holds more. A snapshot ends with the
 *     coordinator's pass (or after [HistoryLimits.snapshotTtlMs]); the next run scans again, so messages that
 *     arrived meanwhile are never claimed as read.
 *  4. **Independent cross-check.** When a snapshot is made, and on every page of one (the coordinator's own "second
 *     look"), a forward query (from the cursor, negative offset) is made. A real message of the channel in
 *     (cursor, newest scanned] that the reverse scan did not contain proves the scan has a hole: the snapshot is
 *     dropped and the page is UNPROVEN, so the coordinator refuses it without moving the cursor. The look has
 *     three typed outcomes: VERIFIED (serve), CONFLICT (UNPROVEN) and INCONCLUSIVE (an error, a timeout or a
 *     malformed answer: Failed, nothing served, the unverified snapshot is kept and asked about again). A look that
 *     could not be made is never taken for "no conflict".
 *  5. **Fails closed** on: another chat's message, a malformed or null entry, an id of 0, an edited sync message,
 *     a pending deletion/edit alert ([ContinuityAlerts]), more unread messages than the memory budget.
 *
 * Cost: one TDLib request per ~page of unread messages per scan (plus one getChat and one cross-check); for the
 * usual incremental run that is two or three requests. A first read of a history of N messages costs about N/100
 * requests ONCE. Not provable by this adapter (and so DEFERRED to a durable reverse checkpoint): more than
 * [HistoryLimits.maxMessages] unread messages in one scan.
 *
 * Runs on a worker thread only (it blocks on [TdlibSyncHub.call]).
 */
internal class TdlibHistoryReader(
    private val hub: TdlibSyncHub,
    private val account: Long,
    private val epoch: Long,
    private val channelId: Long,
    private val limits: HistoryLimits = HistoryLimits(),
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /** Channel message ids are (server id) shl 20, so the first message a channel can have is 1 shl 20. */
        const val FIRST_CHANNEL_MESSAGE_ID = 1L shl 20
    }

    private class Msg(val id: Long, val text: String)

    private class Partial(val floor: Long, val createdAt: Long) {
        val collected = TreeMap<Long, String>()
        var chars = 0L
        var from = 0L
        var lowest = Long.MAX_VALUE
        var noProgress = 0
        var chatKnown = false
    }

    private class Snapshot(val floor: Long, val top: Long, val ids: LongArray, val texts: List<String>, val createdAt: Long) {
        val idSet: Set<Long> by lazy { ids.toHashSet() }
        /** Set only when the independent forward query confirmed that nothing real is missing. */
        var verified = false
    }

    private sealed class Parsed {
        class Ok(val messages: List<Msg>) : Parsed()
        object Malformed : Parsed()
        /** An edited sync message: the append-only log may have been changed. */
        object Tampered : Parsed()
    }

    private var snapshot: Snapshot? = null
    private var partial: Partial? = null
    private var lastServedAfter = Long.MIN_VALUE
    @Volatile private var cancelled = false

    /** Stops an unfinished scan at its next step. The reader is then spent; make a new one. */
    fun cancel() {
        cancelled = true
    }

    @Synchronized
    fun forget() {
        snapshot = null
        partial = null
    }

    @Synchronized
    fun fetchAfter(channel: Long, after: Long, limit: Int): FetchResult {
        if (channel != channelId) return FetchResult.Failed(SyncErrorKind.CHANNEL_MISSING)
        if (limit < 1) return FetchResult.Page(emptyList(), false, after)
        val cap = limit.coerceAtMost(limits.pageLimit)
        if (hub.alerts.pending() != null) return unproven()
        stopIfStale()?.let { return it }

        val now = clockMs()
        var snap = snapshot?.takeIf { usable(it, after, now) }
        if (snap == null) {
            snapshot = null
            when (val scanned = scan(after, now)) {
                is FetchResult.Failed -> return scanned
                is FetchResult.Page -> return scanned // UNPROVEN
                null -> Unit
            }
            snap = snapshot ?: return unproven()
        }
        // A snapshot is served only after an independent forward query has VERIFIED it - once per snapshot, and again for
        // every second look of the coordinator (limit 1). If that query cannot answer, the snapshot is kept but stays
        // unverified: nothing is served, the cursor cannot move, and the next call asks again.
        if (!snap.verified || cap == 1) {
            when (val look = crossCheck(after, snap)) {
                Look.Verified -> snap.verified = true
                Look.Conflict -> return conflict()
                is Look.Inconclusive -> return look.failure
            }
        }
        hub.metrics.count(SyncMetrics.Event.SNAPSHOT_SERVED)
        lastServedAfter = after
        return serve(snap, after, cap)
    }

    private fun usable(s: Snapshot, after: Long, now: Long): Boolean =
        after >= s.floor && now - s.createdAt <= limits.snapshotTtlMs && (after < s.top || after == lastServedAfter)

    private fun serve(s: Snapshot, after: Long, cap: Int): FetchResult.Page {
        var lo = 0
        var hi = s.ids.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (s.ids[mid] > after) hi = mid else lo = mid + 1
        }
        val end = minOf(s.ids.size, lo + cap)
        val page = (lo until end).map { ServerMessage(s.ids[it], s.texts[it]) }
        val hasMore = end < s.ids.size
        val complete = if (hasMore) s.ids[end - 1] else maxOf(after, s.top)
        return FetchResult.Page(page, hasMore, complete)
    }

    private fun unproven(): FetchResult.Page {
        snapshot = null
        partial = null
        hub.metrics.count(SyncMetrics.Event.SCAN_FAILED_CLOSED)
        return FetchResult.Page(emptyList(), false, FetchResult.Page.UNPROVEN)
    }

    private fun conflict(): FetchResult.Page {
        hub.metrics.count(SyncMetrics.Event.CROSS_CHECK_CONFLICT)
        return unproven()
    }

    private fun stopIfStale(): FetchResult.Failed? {
        if (cancelled) {
            snapshot = null
            partial = null
            return FetchResult.Failed(SyncErrorKind.NETWORK)
        }
        if (!hub.identityHolds(account, epoch)) {
            snapshot = null
            partial = null
            return FetchResult.Failed(hub.staleKind(account))
        }
        if (!hub.connectionIsUsable()) return FetchResult.Failed(SyncErrorKind.NETWORK)
        return null
    }

    private fun failed(reply: TdlibSyncHub.Reply): FetchResult.Failed = when (reply) {
        is TdlibSyncHub.Reply.Err -> TdlibSyncErrors.kindOf(reply.code, reply.message).let { FetchResult.Failed(it.first, it.second) }
        TdlibSyncHub.Reply.Timeout -> FetchResult.Failed(SyncErrorKind.TIMEOUT)
        // Aborted: the identity changed or the client went away (another account, a logout, or the same one again).
        else -> FetchResult.Failed(hub.staleKind(account))
    }

    /**
     * Walks down until the lower boundary is proven. Returns null with [snapshot] set when done; otherwise the failure,
     * or an UNPROVEN page.
     */
    private fun scan(after: Long, now: Long): FetchResult? {
        val state = partial?.takeIf { after >= it.floor && now - it.createdAt <= limits.snapshotTtlMs }
            ?: Partial(after, now).also {
                partial = it
                hub.metrics.count(SyncMetrics.Event.SCAN_STARTED)
            }
        if (!state.chatKnown) {
            // Makes TDLib know the chat; a missing/forbidden channel is reported as such (never answered by creating one).
            val chat = hub.call(account, epoch, "getChat", limits.requestTimeoutMs) { it.put("chat_id", channelId) }
            if (chat !is TdlibSyncHub.Reply.Ok) return failed(chat)
            state.chatKnown = true
        }
        val boundary = if (state.floor > 0L) state.floor else FIRST_CHANNEL_MESSAGE_ID
        var requests = 0
        while (true) {
            if (state.lowest <= boundary) break // proven
            if (requests >= limits.maxRequestsPerCall) {
                hub.metrics.count(SyncMetrics.Event.SCAN_FAILED_CLOSED)
                return FetchResult.Failed(SyncErrorKind.TIMEOUT) // resumes from the same place next time
            }
            stopIfStale()?.let { return it }
            requests++
            val reply = hub.call(account, epoch, "getChatHistory", limits.requestTimeoutMs) {
                it.put("chat_id", channelId)
                it.put("from_message_id", state.from)
                it.put("offset", 0)
                it.put("limit", limits.pageLimit)
                it.put("only_local", false)
            }
            if (reply !is TdlibSyncHub.Reply.Ok) return failed(reply)
            val parsed = when (val p = parse(reply.json)) {
                is Parsed.Ok -> p
                else -> return unproven()
            }
            var progressed = false
            for (m in parsed.messages) {
                if (m.id > state.floor && !state.collected.containsKey(m.id)) {
                    state.collected[m.id] = m.text
                    state.chars += m.text.length
                }
                if (m.id < state.lowest) {
                    state.lowest = m.id
                    progressed = true
                }
            }
            if (state.collected.size > limits.maxMessages || state.chars > limits.maxChars) return unproven()
            if (progressed) {
                state.noProgress = 0
                state.from = state.lowest
            } else if (++state.noProgress >= limits.maxNoProgress) {
                // Nothing older came back, and the boundary was never seen: not proof of the beginning.
                return FetchResult.Failed(SyncErrorKind.NETWORK)
            }
        }
        val ids = state.collected.keys.toLongArray()
        snapshot = Snapshot(state.floor, ids.lastOrNull() ?: state.floor, ids, state.collected.values.toList(), now)
        partial = null
        hub.metrics.count(SyncMetrics.Event.SCAN_COMPLETED)
        return null
    }

    /** What the independent forward look said. Only [Verified] allows a page to be served. */
    private sealed class Look {
        /** The forward query answered and every real message it showed in (after, top] is in the snapshot. */
        object Verified : Look()
        /** It showed a real message the scan lacked (a hole), or an edited sync message. */
        object Conflict : Look()
        /** It did not answer, answered an error, or answered nonsense: NOTHING is known, so nothing may be served. */
        class Inconclusive(val failure: FetchResult.Failed) : Look()
    }

    private fun crossCheck(after: Long, snap: Snapshot): Look {
        val reply = hub.call(account, epoch, "getChatHistory", limits.requestTimeoutMs) {
            it.put("chat_id", channelId)
            it.put("from_message_id", maxOf(after, 1L))
            it.put("offset", -99)
            it.put("limit", 100)
            it.put("only_local", false)
        }
        if (reply !is TdlibSyncHub.Reply.Ok) return Look.Inconclusive(failed(reply))
        return when (val parsed = parse(reply.json)) {
            Parsed.Tampered -> Look.Conflict
            Parsed.Malformed -> Look.Inconclusive(FetchResult.Failed(SyncErrorKind.NETWORK))
            is Parsed.Ok ->
                if (parsed.messages.any { it.id > after && it.id <= snap.top && it.id !in snap.idSet }) Look.Conflict else Look.Verified
        }
    }

    private fun parse(json: JSONObject): Parsed {
        if (json.optString("@type") != "messages") return Parsed.Malformed
        val array = json.optJSONArray("messages") ?: return Parsed.Malformed
        val out = ArrayList<Msg>(array.length())
        for (i in 0 until array.length()) {
            val m = array.optJSONObject(i) ?: return Parsed.Malformed
            val id = m.optLong("id", 0L)
            if (id <= 0L || m.optLong("chat_id", 0L) != channelId) return Parsed.Malformed
            val content = m.optJSONObject("content")
            val text = if (content?.optString("@type") == "messageText") content.optJSONObject("text")?.optString("text").orEmpty() else ""
            if (text.startsWith(PlaylistSyncProtocol.MARKER) && m.optInt("edit_date", 0) > 0) {
                hub.alerts.raise(ContinuityAlert.MESSAGE_EDITED)
                hub.metrics.count(SyncMetrics.Event.CONTINUITY_ALERT)
                return Parsed.Tampered
            }
            out.add(Msg(id, text))
        }
        return Parsed.Ok(out)
    }
}
