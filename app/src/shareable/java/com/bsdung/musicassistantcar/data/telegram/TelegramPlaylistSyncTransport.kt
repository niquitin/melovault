package com.bsdung.musicassistantcar.data.telegram

/**
 * E96 P2: what the sync core needs from "the channel", and nothing else. In P2 only a fake implements it
 * (in tests); P3 will put TDLib behind it. This interface knows no TDLib type, opens no network and
 * creates no channel: [channelId] always comes from the stored checkpoint.
 */
internal enum class SyncErrorKind {
    NETWORK, TIMEOUT, FLOOD_WAIT,
    /** The channel is gone. Never answered by creating another one. */
    CHANNEL_MISSING,
    UNAUTHORIZED,
    SESSION_REVOKED,
}

/** A message of the channel with the id the SERVER gave it. */
internal data class ServerMessage(val id: Long, val text: String) {
    init { require(id > 0L) }
}

internal sealed class SendResult {
    /** The server stored the message and said so ([serverMessageId] is its own id for it). This is the evidence for an ACK. */
    data class Sent(val serverMessageId: Long) : SendResult() {
        init { require(serverMessageId > 0L) }
    }

    /** Clearly not stored. */
    data class Failed(val kind: SyncErrorKind, val retryAfterSeconds: Int = 0) : SendResult()

    /** Handed over but no answer (timeout, dropped reply): the server may or may not have it. Never counts as sent. */
    object Unknown : SendResult()
}

internal sealed class FetchResult {
    data class Page(val messages: List<ServerMessage>, val hasMore: Boolean) : FetchResult()
    data class Failed(val kind: SyncErrorKind, val retryAfterSeconds: Int = 0) : FetchResult()
}

internal interface PlaylistSyncTransport {
    /** Appends one text message. Never edits or deletes anything. */
    fun send(channelId: Long, text: String): SendResult

    /**
     * Up to [limit] messages whose server id is greater than [afterServerId], in ANY order, possibly
     * with repeats and gaps in the ids. [Page.hasMore] says whether more exist beyond this page.
     */
    fun fetchAfter(channelId: Long, afterServerId: Long, limit: Int): FetchResult
}

/** How long to wait before the next attempt. Pure arithmetic on a given time: it never sleeps or reads a clock. */
internal class SyncBackoff(
    private val baseMs: Long = 5_000L,
    private val maxMs: Long = 300_000L,
    private val maxFloodMs: Long = 24L * 60 * 60 * 1000,
) {
    var failures: Int = 0
        private set
    var notBeforeMs: Long = 0L
        private set

    fun onSuccess() {
        failures = 0
        notBeforeMs = 0L
    }

    /** Returns the delay chosen. A server-given wait (FLOOD_WAIT) is obeyed as given, not shortened. */
    fun onFailure(nowMs: Long, kind: SyncErrorKind, retryAfterSeconds: Int): Long {
        failures++
        val exponential = minOf(maxMs, baseMs shl minOf(failures - 1, 20))
        val delay = if (kind == SyncErrorKind.FLOOD_WAIT) {
            maxOf(1_000L, minOf(maxFloodMs, retryAfterSeconds * 1000L))
        } else exponential
        notBeforeMs = nowMs + delay
        return delay
    }

    fun reset() = onSuccess()
}
