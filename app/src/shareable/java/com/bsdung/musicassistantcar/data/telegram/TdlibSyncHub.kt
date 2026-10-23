package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Anonymous counters only: never a message, an id, an account or a path. */
internal class SyncMetrics {
    enum class Event {
        REQUEST_SENT, REQUEST_TIMEOUT, REQUEST_ABORTED, LATE_RESPONSE_DROPPED, EVENT_OVERFLOW,
        SEND_ACKED, SEND_FAILED, SEND_UNKNOWN, SEND_PAYLOAD_MISMATCH,
        SCAN_STARTED, SCAN_COMPLETED, SCAN_FAILED_CLOSED, SNAPSHOT_SERVED, CROSS_CHECK_CONFLICT,
        CONTINUITY_ALERT, RUN_STARTED, RUN_FINISHED, GATE_REFUSED,
    }

    private val counts = Array(Event.values().size) { AtomicInteger(0) }
    @Volatile var lastRunDurationMs: Long = 0L
        private set
    @Volatile var lastRunAttempt: Int = 0
        private set

    fun count(event: Event) {
        counts[event.ordinal].incrementAndGet()
    }

    fun get(event: Event): Int = counts[event.ordinal].get()

    fun runFinished(durationMs: Long, attempt: Int) {
        lastRunDurationMs = durationMs
        lastRunAttempt = attempt
    }
}

/** Evidence that the channel's append-only log may no longer be what it was. Cleared only by an explicit [acknowledge]. */
internal enum class ContinuityAlert { MESSAGES_DELETED, MESSAGE_EDITED }

internal class ContinuityAlerts {
    private val current = AtomicReference<ContinuityAlert?>(null)
    fun raise(alert: ContinuityAlert) {
        current.compareAndSet(null, alert)
    }

    fun pending(): ContinuityAlert? = current.get()

    /** A person (P4 UI) or a test looked at it. Does not repair anything. */
    fun acknowledge() {
        current.set(null)
    }
}

/** What went wrong with a TDLib request, in the terms of the sync core. */
internal object TdlibSyncErrors {
    const val DEFAULT_FLOOD_SECONDS = 30
    private val flood = Regex("(?i)(?:retry after|FLOOD_WAIT_|FLOOD_PREMIUM_WAIT_)\\s*(\\d+)")

    fun kindOf(code: Int, message: String): Pair<SyncErrorKind, Int> {
        val upper = message.uppercase()
        if (code == 429 || "FLOOD_WAIT" in upper || "FLOOD_PREMIUM_WAIT" in upper) {
            val asked = flood.find(message)?.groupValues?.get(1)?.toLongOrNull()
            // Obey the server exactly; only when it names no number is a default used.
            val seconds = asked?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: DEFAULT_FLOOD_SECONDS
            return SyncErrorKind.FLOOD_WAIT to seconds
        }
        if (code == 401 || listOf("AUTH_KEY", "SESSION_REVOKED", "SESSION_EXPIRED", "USER_DEACTIVATED").any { it in upper }) {
            return SyncErrorKind.SESSION_REVOKED to 0
        }
        if (listOf(
                "CHAT NOT FOUND", "CHAT_ID_INVALID", "CHANNEL_PRIVATE", "CHANNEL_INVALID", "PEER_ID_INVALID",
                "CHAT_WRITE_FORBIDDEN", "CHAT_ADMIN_REQUIRED", "CHAT_RESTRICTED", "CHANNEL_BANNED", "USER_BANNED_IN_CHANNEL",
            ).any { it in upper }
        ) {
            return SyncErrorKind.CHANNEL_MISSING to 0
        }
        if (code == 403) return SyncErrorKind.UNAUTHORIZED to 0
        return SyncErrorKind.NETWORK to 0
    }
}

internal sealed class SendOutcome {
    class Succeeded(val message: JSONObject) : SendOutcome()
    class Failed(val code: Int, val message: String) : SendOutcome()
}

/**
 * E96 P3: request/answer correlation and event fan-in for the playlist sync, on top of the single TDLib owner.
 *
 *  - [call] is a bounded blocking request for a WORKER thread. It never runs on the native receive thread (the owner
 *    hands events over through a bounded queue to the dispatch thread that calls [onEvent]) and refuses to run on it.
 *  - Answers are matched by the unique `@extra` tag, which also carries the auth epoch. An answer that arrives after
 *    the account was switched, logged out, logged in again (even as the same account) or closed is thrown away.
 *  - [SendWatch]es collect updateMessageSendSucceeded/Failed, also when they come BEFORE the sendMessage answer.
 *  - Nothing here logs, stores or forwards a payload.
 */
internal class TdlibSyncHub(
    private val owner: SyncOwnerPort,
    val metrics: SyncMetrics = SyncMetrics(),
    val alerts: ContinuityAlerts = ContinuityAlerts(),
) : SyncEventSink {
    sealed class Reply {
        class Ok(val json: JSONObject) : Reply()
        class Err(val code: Int, val message: String) : Reply()
        object Timeout : Reply()
        /** Nothing is known about the outcome: the identity changed, the client closed, or the request could not be handed over. */
        object Aborted : Reply()
    }

    private class Pending(val epoch: Long) {
        val latch = CountDownLatch(1)
        val reply = AtomicReference<Reply?>(null)
        fun complete(r: Reply) {
            reply.compareAndSet(null, r)
            latch.countDown()
        }
    }

    private val pending = ConcurrentHashMap<String, Pending>()
    private val watches = CopyOnWriteArrayList<SendWatch>()
    private val seq = AtomicLong(0L)
    private val inEvent = ThreadLocal<Boolean>()
    @Volatile private var blockedEpoch = -1L
    @Volatile private var watchedChat = 0L
    @Volatile private var connectionReady: Boolean? = null
    @Volatile private var bound = false
    @Volatile var onRemoteMessage: (() -> Unit)? = null

    val isBound: Boolean get() = bound

    /** Binds the sink to the owner. False if another sink is already bound. */
    @Synchronized
    fun open(): Boolean {
        if (bound) return true
        if (!owner.bindSync(this)) return false
        bound = true
        connectionReady = null
        return true
    }

    @Synchronized
    fun close() {
        if (!bound) return
        bound = false
        owner.unbindSync(this)
        abortAll()
        watchedChat = 0L
        onRemoteMessage = null
    }

    /** Messages deleted/edited in this chat count as continuity evidence. */
    fun watchChannel(chatId: Long) {
        watchedChat = chatId
    }

    fun connectionIsUsable(): Boolean = connectionReady != false

    /** The identity this request was made for is still the live one. */
    fun identityHolds(account: Long, epoch: Long): Boolean =
        bound && blockedEpoch != owner.authEpoch() && owner.authorizedAccount() == account && owner.authEpoch() == epoch

    /**
     * Why [identityHolds] is false. Another account, or none: it needs the sign-in to be right ([SyncErrorKind.UNAUTHORIZED]).
     * The same account signed in again (new epoch): a later run with a fresh binding is fine, so only [SyncErrorKind.NETWORK].
     */
    fun staleKind(account: Long): SyncErrorKind =
        if (bound && owner.authorizedAccount() == account && blockedEpoch != owner.authEpoch()) SyncErrorKind.NETWORK else SyncErrorKind.UNAUTHORIZED

    /** Worker thread only. */
    fun call(account: Long, epoch: Long, type: String, timeoutMs: Long, configure: ((JSONObject) -> Unit)? = null): Reply {
        check(inEvent.get() != true) { "blocking call on the dispatch thread" }
        if (!identityHolds(account, epoch)) return Reply.Aborted
        val tag = "$SYNC_TAG_PREFIX$type:$epoch:${seq.incrementAndGet()}"
        val request = Pending(epoch)
        pending[tag] = request
        try {
            try {
                owner.send(type, tag, configure)
            } catch (_: Exception) {
                metrics.count(SyncMetrics.Event.REQUEST_ABORTED)
                return Reply.Aborted
            }
            metrics.count(SyncMetrics.Event.REQUEST_SENT)
            val done = try {
                request.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return Reply.Aborted
            }
            val reply = request.reply.get()
            if (!done || reply == null) {
                metrics.count(SyncMetrics.Event.REQUEST_TIMEOUT)
                return Reply.Timeout
            }
            // The gate again, at callback time: an answer for account A must never be used once B (or A again) is live.
            if (!identityHolds(account, epoch)) {
                metrics.count(SyncMetrics.Event.REQUEST_ABORTED)
                return Reply.Aborted
            }
            return reply
        } finally {
            pending.remove(tag)
        }
    }

    fun openSendWatch(chatId: Long): SendWatch = SendWatch(chatId).also { watches.add(it) }

    inner class SendWatch internal constructor(val chatId: Long) {
        private val lock = Object()
        private val outcomes = LinkedHashMap<Long, SendOutcome>()
        private var aborted = false

        internal fun offer(oldMessageId: Long, outcome: SendOutcome) = synchronized(lock) {
            // Evidence that the server stored the message is never overwritten by a later, contradicting failure.
            if (outcomes[oldMessageId] !is SendOutcome.Succeeded) outcomes[oldMessageId] = outcome
            while (outcomes.size > 32) outcomes.remove(outcomes.keys.first())
            lock.notifyAll()
        }

        internal fun abort() = synchronized(lock) {
            aborted = true
            lock.notifyAll()
        }

        /** The outcome the server reported for the provisional message [oldMessageId], or null (timeout / aborted). */
        fun await(oldMessageId: Long, timeoutMs: Long): SendOutcome? {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
            synchronized(lock) {
                while (true) {
                    outcomes.remove(oldMessageId)?.let { return it }
                    if (aborted) return null
                    val left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                    if (left <= 0) return null
                    try { lock.wait(left) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); return null }
                }
            }
        }

        fun close() {
            watches.remove(this)
        }
    }

    fun abortAll() {
        pending.values.forEach { it.complete(Reply.Aborted) }
        watches.forEach { it.abort() }
    }

    override fun onOverflow() {
        metrics.count(SyncMetrics.Event.EVENT_OVERFLOW)
        abortAll()
    }

    override fun onClientClosed() {
        abortAll()
    }

    override fun onEvent(event: JSONObject) {
        inEvent.set(true)
        try {
            route(event)
        } finally {
            inEvent.remove()
        }
    }

    private fun route(event: JSONObject) {
        val tag = event.optString("@extra")
        val type = event.optString("@type")
        if (tag.startsWith(SYNC_TAG_PREFIX)) {
            val request = pending.remove(tag)
            if (request == null) {
                metrics.count(SyncMetrics.Event.LATE_RESPONSE_DROPPED)
                return
            }
            if (request.epoch != owner.authEpoch()) {
                request.complete(Reply.Aborted)
            } else if (type == "error") {
                request.complete(Reply.Err(event.optInt("code"), event.optString("message")))
            } else {
                request.complete(Reply.Ok(event))
            }
            return
        }
        when (type) {
            "updateMessageSendSucceeded" -> {
                val message = event.optJSONObject("message") ?: return
                val old = event.optLong("old_message_id")
                watches.forEach { if (it.chatId == message.optLong("chat_id")) it.offer(old, SendOutcome.Succeeded(message)) }
            }
            "updateMessageSendFailed" -> {
                val message = event.optJSONObject("message") ?: return
                val old = event.optLong("old_message_id")
                val error = event.optJSONObject("error")
                val code = error?.optInt("code") ?: event.optInt("error_code")
                val text = error?.optString("message") ?: event.optString("error_message")
                watches.forEach { if (it.chatId == message.optLong("chat_id")) it.offer(old, SendOutcome.Failed(code, text)) }
            }
            "updateConnectionState" -> {
                val state = event.optJSONObject("state")?.optString("@type").orEmpty()
                connectionReady = state == "connectionStateReady" || state == "connectionStateUpdating"
            }
            "updateAuthorizationState" -> {
                val state = event.optJSONObject("authorization_state")?.optString("@type").orEmpty()
                if (state != "authorizationStateReady") {
                    blockedEpoch = owner.authEpoch()
                    abortAll()
                }
            }
            "updateDeleteMessages" -> {
                if (watchedChat != 0L && event.optLong("chat_id") == watchedChat && event.optBoolean("is_permanent") && !event.optBoolean("from_cache")) {
                    alerts.raise(ContinuityAlert.MESSAGES_DELETED)
                    metrics.count(SyncMetrics.Event.CONTINUITY_ALERT)
                }
            }
            "updateMessageEdited" -> {
                if (watchedChat != 0L && event.optLong("chat_id") == watchedChat) {
                    alerts.raise(ContinuityAlert.MESSAGE_EDITED)
                    metrics.count(SyncMetrics.Event.CONTINUITY_ALERT)
                }
            }
            "updateNewMessage" -> {
                val message = event.optJSONObject("message") ?: return
                // Posts of the same account from ANOTHER device are outgoing too, so is_outgoing says nothing here.
                if (watchedChat != 0L && message.optLong("chat_id") == watchedChat) {
                    runCatching { onRemoteMessage?.invoke() }
                }
            }
        }
    }
}
