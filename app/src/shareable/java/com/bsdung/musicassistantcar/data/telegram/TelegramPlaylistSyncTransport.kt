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
    /** Telegram refused the request itself (a 400 nobody here knows). Asking again changes nothing: it is not "the network". */
    REJECTED,
}

/**
 * Where a sync run stopped, as a closed list. This is the ONLY diagnostic that is ever shown or logged: no chat, message
 * or user id, no text, no song name, no TDLib message. Names carry no data on purpose.
 */
internal enum class SyncStage {
    NONE,
    FETCH_SCAN, FETCH_CROSSCHECK, FETCH_CORRUPT,
    SEND_BUDGET_ZERO, SEND_PLAN_BLOCKED,
    SEND_PRECHECK_CONSENT, SEND_PRECHECK_IDENTITY, SEND_PRECHECK_CONNECTION, SEND_PRECHECK_TEXT,
    /** The doubt could not be written down BEFORE dispatch, so nothing was sent. */
    SEND_PRECHECK_PERSIST,
    SEND_DISPATCH_ERROR, SEND_DISPATCH_TIMEOUT, SEND_DISPATCH_ABORTED,
    SEND_PROVISIONAL_MISMATCH_TYPE, SEND_PROVISIONAL_MISMATCH_CHAT, SEND_PROVISIONAL_MISMATCH_TEXT,
    SEND_ACK_TIMEOUT, SEND_ACK_FAILED, SEND_ACK_MISMATCH, SEND_IDENTITY_LOST,
    READBACK_ABSENT,
}

/** The class of a TDLib/Telegram error code, never the code's message. */
internal enum class ErrorBucket {
    NONE, E400, E401, E403, E404, E429, E5XX, OTHER;

    companion object {
        fun of(code: Int): ErrorBucket = when (code) {
            400 -> E400
            401 -> E401
            403 -> E403
            404 -> E404
            429 -> E429
            in 500..599 -> E5XX
            else -> OTHER
        }
    }
}

/** How long the send call took. Coarse on purpose. */
internal enum class DurationBucket {
    UNDER_1S, UNDER_20S, UNDER_60S, OVER_60S;

    companion object {
        fun of(ms: Long): DurationBucket = when {
            ms < 1_000L -> UNDER_1S
            ms < 20_000L -> UNDER_20S
            ms < 60_000L -> UNDER_60S
            else -> OVER_60S
        }
    }
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

    /**
     * Clearly NOT stored: the request was refused before anything was handed over, or Telegram/TDLib answered with a
     * definite 4xx error. The operations stay PENDING (unchanged) and nothing about them is doubtful. Anything that is
     * not that clear is [Unknown]. [stage] and [bucket] are diagnostics only and take no part in equality.
     */
    class Failed(
        val kind: SyncErrorKind,
        val retryAfterSeconds: Int = 0,
        val stage: SyncStage = SyncStage.SEND_DISPATCH_ERROR,
        val bucket: ErrorBucket = ErrorBucket.NONE,
    ) : SendResult() {
        override fun equals(other: Any?): Boolean = other is Failed && other.kind == kind && other.retryAfterSeconds == retryAfterSeconds
        override fun hashCode(): Int = 31 * kind.hashCode() + retryAfterSeconds
        override fun toString(): String = "Failed($kind, $retryAfterSeconds, $stage)"
    }

    /**
     * Handed over but no usable answer (timeout, dropped reply, a text that is not ours, a late identity change): the
     * server may or may not have it. Never counts as sent and NEVER lets the same operations be sent again by itself.
     * [stage] is the sanitised reason, a diagnostic that takes no part in equality.
     */
    class Unknown(val stage: SyncStage = SyncStage.SEND_DISPATCH_TIMEOUT, val bucket: ErrorBucket = ErrorBucket.NONE) : SendResult() {
        override fun equals(other: Any?): Boolean = other is Unknown
        override fun hashCode(): Int = 1
        override fun toString(): String = "Unknown($stage)"
    }
}

internal sealed class FetchResult {
    /**
     * The NEXT part of the history after the position that was asked for, with a proof of completeness.
     *
     * [completeThroughId] is the transport's promise that [messages] holds EVERY visible message of the
     * channel whose server id is greater than the requested `afterServerId` and not greater than
     * [completeThroughId]. The coordinator moves its cursor to it and never past it, so a page that is not
     * the oldest unread part (for example the newest messages first) must NOT claim a proof; it must say
     * [UNPROVEN] (or the position it asked for) and will then be refused without the cursor moving.
     * Ids may have gaps, the order inside [messages] does not matter, repeats are tolerated, and messages
     * above [completeThroughId] may be present but count for nothing. [hasMore] says whether messages
     * above [completeThroughId] exist; a page that claims more but proves no progress is refused.
     */
    data class Page(
        val messages: List<ServerMessage>,
        val hasMore: Boolean,
        val completeThroughId: Long = UNPROVEN,
    ) : FetchResult() {
        companion object {
            /** "I cannot prove this page is the next part of the history." */
            const val UNPROVEN = Long.MIN_VALUE
        }
    }

    data class Failed(val kind: SyncErrorKind, val retryAfterSeconds: Int = 0) : FetchResult()
}

internal interface PlaylistSyncTransport {
    /** Appends one text message. Never edits or deletes anything. */
    fun send(channelId: Long, text: String): SendResult

    /**
     * The OLDEST part of the history after [afterServerId]: at most [limit] messages with a server id
     * greater than it, starting from the lowest such id, with no visible message left out below
     * [FetchResult.Page.completeThroughId]. It is never the newest messages first. The same position
     * asked twice answers the same start. See [FetchResult.Page] for the exact promise.
     */
    fun fetchAfter(channelId: Long, afterServerId: Long, limit: Int): FetchResult
}

/** How long to wait before the next attempt. Pure arithmetic on a given time: it never sleeps or reads a clock. */
internal class SyncBackoff(
    private val baseMs: Long = 5_000L,
    private val maxMs: Long = 300_000L,
) {
    var failures: Int = 0
        private set
    var notBeforeMs: Long = 0L
        private set

    fun onSuccess() {
        failures = 0
        notBeforeMs = 0L
    }

    /**
     * Returns the delay chosen. A wait the server asked for (FLOOD_WAIT) is obeyed exactly as given - never
     * capped, never shortened - with a floor of one second against a zero or negative value. Seconds are an
     * Int, so the milliseconds always fit a Long; the point in time saturates instead of wrapping.
     */
    fun onFailure(nowMs: Long, kind: SyncErrorKind, retryAfterSeconds: Int): Long {
        failures++
        val exponential = minOf(maxMs, baseMs shl minOf(failures - 1, 20))
        val delay = if (kind == SyncErrorKind.FLOOD_WAIT) maxOf(1_000L, retryAfterSeconds.toLong() * 1000L) else exponential
        notBeforeMs = try {
            Math.addExact(nowMs, delay)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
        return delay
    }

    fun reset() = onSuccess()
}
