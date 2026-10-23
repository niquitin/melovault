package com.bsdung.musicassistantcar.data.telegram

/** A consistent look at the open playlist: [epoch] changes whenever the account is opened, switched or closed. */
internal data class SyncView(val epoch: Long, val state: PlaylistState)

internal sealed class UpdateResult {
    data class Committed(val state: PlaylistState) : UpdateResult()
    /** The account was switched or closed since [SyncView] was taken: nothing was changed. */
    object Stale : UpdateResult()
    object NotOpen : UpdateResult()
    /** The change could not be written. The stored playlist and the in-memory one are as before. */
    object Failed : UpdateResult()
}

/**
 * The only door the sync core uses into the playlist. Implemented by the V2 session, which stays the
 * single writer: [update] runs [transform] on the CURRENT state inside the session lock and stores the
 * result in one atomic V2 write (state + outbox + cursor together) before it reports [UpdateResult.Committed].
 * Network calls never happen inside it.
 */
internal interface PlaylistSyncHost {
    fun view(account: TelegramUserId): SyncView?
    fun update(account: TelegramUserId, epoch: Long, transform: (PlaylistState) -> PlaylistState): UpdateResult
}

internal enum class SyncMode {
    /** Read only what is newer than the stored cursor. */
    INCREMENTAL,
    /** Read the whole history again from the start. Always safe (applying is idempotent); picks up anything that showed up late. */
    FULL_REPLAY,
}

internal enum class SyncStatus {
    OK, NOT_OPEN, NO_CHANNEL,
    /** Called again before the wait is over: no transport call was made. */
    BACKOFF_ACTIVE,
    /** Temporary trouble (network, timeout, FLOOD_WAIT, lost answer). Retry after [SyncReport.retryAfterMs]. */
    BACKOFF,
    /** Something needs a person or a later phase (channel gone, access revoked, damaged log). Nothing was deleted. */
    RECOVERY_REQUIRED,
    /** The account changed while the run was in flight; the run stopped without touching the new account. */
    STALE_SESSION,
    SAVE_FAILED,
}

internal enum class RecoveryReason {
    CHANNEL_MISSING, UNAUTHORIZED, SESSION_REVOKED, CORRUPT_MESSAGE, FUTURE_PROTOCOL, WRONG_ACCOUNT, OP_TOO_LARGE
}

internal data class SyncReport(
    val status: SyncStatus,
    val recovery: RecoveryReason? = null,
    val protocolError: PlaylistSyncProtocol.Error? = null,
    val badMessageId: Long = 0L,
    val pagesFetched: Int = 0,
    val messagesRead: Int = 0,
    val foreignSkipped: Int = 0,
    val opsApplied: Int = 0,
    val opsAcknowledged: Int = 0,
    val envelopesSent: Int = 0,
    val cursor: Long = 0L,
    val pendingLeft: Int = 0,
    val retryAfterMs: Long = 0L,
)

internal data class SyncConfig(
    val pageSize: Int = 100,
    val maxPagesPerRun: Int = 100,
    val maxEnvelopesPerRun: Int = 50,
)

/** Applies what other devices (or our own earlier sends) wrote, acknowledges our own, and moves the cursor - as one new state. */
internal fun PlaylistState.applyRemoteBatch(ops: List<PlaylistOp>, ackIds: Set<String>, lastGoodId: Long): PlaylistState {
    var next = this
    for (op in ops) {
        next = next.applyRemote(op)
        // If this device's own earlier work comes back with a higher counter than the stored one (an older
        // file was restored), keep going from there: a tag must never be handed out twice.
        val own = Dot.parse(op.opId)
        if (own != null && own.device == next.deviceId && own.counter > next.opCounter) next = next.copy(opCounter = own.counter)
    }
    for (id in ackIds) next = next.acknowledge(id)
    return next.copy(checkpoint = next.checkpoint.copy(lastAppliedServerId = maxOf(next.checkpoint.lastAppliedServerId, lastGoodId)))
}

/**
 * E96 P2: one synchronisation run, as a plain state machine over a [PlaylistSyncTransport] and a
 * [PlaylistSyncHost]. It runs on the caller's thread, one run at a time, never sleeps, never loops
 * forever, starts no thread/worker/service and touches no TDLib. It is started by hand (tests); nothing
 * in the app starts it yet.
 *
 * A run is: FETCH (apply others' operations, acknowledge our own that came back, advance the cursor) then
 * SEND (append our waiting operations), then prune acknowledged entries.
 *
 * Rules that make it safe:
 *  - ACK only on evidence: the server's own answer to the send ([SendResult.Sent]) or finding the very same
 *    operation id again in the fetched history. A send whose answer was lost is never an ACK; its
 *    operations stay waiting (RETRY_REQUIRED) and keep their ids, so the retry is the identical message.
 *  - FETCH comes before SEND, so an operation the server already has is acknowledged, not sent twice.
 *  - A page is committed as ONE write: applied operations + acknowledgements + cursor. If that write fails,
 *    nothing and no cursor moved. The cursor never passes a message that was not applied or deliberately
 *    skipped (foreign text); a damaged protocol message stops the run before it, after the good messages
 *    before it were committed.
 *  - Nothing waiting is ever dropped; only ACKNOWLEDGED entries are pruned, in a write of their own.
 *  - Every step re-checks that the same account/session is still open; a late result of account A is
 *    never applied to or sent for account B.
 *  - Failures set a backoff (obeying FLOOD_WAIT as given); calling again too early makes no transport call.
 *  - The clock only decides WHEN to try again. It never decides what is in the playlist.
 */
internal class PlaylistSyncCoordinator(
    private val host: PlaylistSyncHost,
    private val transport: PlaylistSyncTransport,
    private val clock: () -> Long,
    private val config: SyncConfig = SyncConfig(),
    private val backoff: SyncBackoff = SyncBackoff(),
) {
    private class Counters {
        var pages = 0
        var read = 0
        var foreign = 0
        var applied = 0
        var acked = 0
        var sent = 0
    }

    /** For a person pressing "try again": forget the wait. */
    @Synchronized
    fun resetBackoff() = backoff.reset()

    @Synchronized
    fun runOnce(account: TelegramUserId, mode: SyncMode = SyncMode.INCREMENTAL): SyncReport {
        val c = Counters()
        val now = clock()
        if (now < backoff.notBeforeMs) {
            return report(c, account, SyncStatus.BACKOFF_ACTIVE, retryAfterMs = backoff.notBeforeMs - now)
        }
        val first = host.view(account) ?: return report(c, account, SyncStatus.NOT_OPEN)
        if (first.state.accountId != account.value) return report(c, account, SyncStatus.NOT_OPEN)
        val epoch = first.epoch
        val channel = first.state.checkpoint.channelId
        if (channel == 0L) return report(c, account, SyncStatus.NO_CHANNEL) // never creates one
        val scope = PlaylistSyncProtocol.scopeOf(account.value)

        fetchPhase(account, epoch, channel, scope, mode, c)?.let { return it }
        sendPhase(account, epoch, channel, scope, c)?.let { return it }
        prune(account, epoch)
        backoff.onSuccess()
        return report(c, account, SyncStatus.OK)
    }

    // ---- fetch ---------------------------------------------------------------------------------

    private fun fetchPhase(
        account: TelegramUserId, epoch: Long, channel: Long, scope: String, mode: SyncMode, c: Counters,
    ): SyncReport? {
        var cursor = if (mode == SyncMode.FULL_REPLAY) 0L
        else current(account, epoch)?.state?.checkpoint?.lastAppliedServerId ?: return report(c, account, SyncStatus.STALE_SESSION)
        var pages = 0
        while (pages < config.maxPagesPerRun) {
            val view = current(account, epoch) ?: return report(c, account, SyncStatus.STALE_SESSION)
            val result = try {
                transport.fetchAfter(channel, cursor, config.pageSize)
            } catch (_: Exception) {
                FetchResult.Failed(SyncErrorKind.NETWORK)
            }
            pages++
            c.pages++
            val page = when (result) {
                is FetchResult.Failed -> return failure(c, account, result.kind, result.retryAfterSeconds)
                is FetchResult.Page -> result
            }
            // Do not trust the order, the repeats or the ids of a page: sort, drop repeats, ignore what is behind us.
            val messages = page.messages.distinctBy { it.id }.sortedBy { it.id }.filter { it.id > cursor }
            c.read += messages.size
            if (messages.isEmpty()) break // nothing new, even if hasMore is claimed: never spin

            val ownDevice = view.state.deviceId
            val ownOutbox = view.state.outbox.associateBy { it.op.opId }
            val ops = ArrayList<PlaylistOp>()
            val ackIds = LinkedHashSet<String>()
            var lastGood = cursor
            var badId = 0L
            var badError: PlaylistSyncProtocol.Error? = null
            for (message in messages) {
                when (val decoded = PlaylistSyncProtocol.decode(message.text, scope)) {
                    is PlaylistSyncProtocol.Decoded.Foreign -> {
                        c.foreign++
                        lastGood = message.id
                    }
                    is PlaylistSyncProtocol.Decoded.Invalid -> {
                        badId = message.id
                        badError = decoded.error
                    }
                    is PlaylistSyncProtocol.Decoded.Ok -> {
                        val env = decoded.envelope
                        val mine = env.sender == ownDevice
                        // Our own operation must come back exactly as we wrote it.
                        if (mine && env.ops.any { op -> ownOutbox[op.opId]?.let { it.op != op } == true }) {
                            badId = message.id
                            badError = PlaylistSyncProtocol.Error.BAD_FORMAT
                        } else {
                            ops.addAll(env.ops)
                            if (mine) env.ops.forEach { if (ownOutbox.containsKey(it.opId)) ackIds.add(it.opId) }
                            lastGood = message.id
                        }
                    }
                }
                if (badError != null) break
            }
            if (lastGood > cursor) {
                // One write: operations + acknowledgements + cursor, for the good messages before any bad one.
                when (host.update(account, epoch) { it.applyRemoteBatch(ops, ackIds, lastGood) }) {
                    is UpdateResult.Committed -> {
                        c.applied += ops.size
                        c.acked += ackIds.size
                        cursor = lastGood
                    }
                    is UpdateResult.Stale -> return report(c, account, SyncStatus.STALE_SESSION)
                    else -> return report(c, account, SyncStatus.SAVE_FAILED)
                }
            }
            badError?.let { return corrupt(c, account, badId, it) }
            if (!page.hasMore) break
        }
        return null
    }

    // ---- send ----------------------------------------------------------------------------------

    private fun sendPhase(account: TelegramUserId, epoch: Long, channel: Long, scope: String, c: Counters): SyncReport? {
        var envelopes = 0
        while (envelopes < config.maxEnvelopesPerRun) {
            val view = current(account, epoch) ?: return report(c, account, SyncStatus.STALE_SESSION)
            val waiting = view.state.outbox.filter { it.status != OutboxStatus.ACKNOWLEDGED }.map { it.op }
            if (waiting.isEmpty()) return null
            val envelope = PlaylistSyncProtocol.planFirstEnvelope(scope, view.state.deviceId, waiting)
                ?: return recovery(c, account, RecoveryReason.OP_TOO_LARGE)
            val ids = envelope.ops.map { it.opId }.toSet()
            val result = try {
                transport.send(channel, PlaylistSyncProtocol.encode(envelope))
            } catch (_: Exception) {
                SendResult.Unknown
            }
            envelopes++
            when (result) {
                is SendResult.Sent -> when (host.update(account, epoch) { s -> ids.fold(s) { acc, id -> acc.acknowledge(id) } }) {
                    is UpdateResult.Committed -> {
                        c.sent++
                        c.acked += ids.size
                    }
                    is UpdateResult.Stale -> return report(c, account, SyncStatus.STALE_SESSION)
                    // The server has them; our note of it was not written. The next run finds the same ids in the history.
                    else -> return report(c, account, SyncStatus.SAVE_FAILED)
                }
                is SendResult.Unknown -> {
                    host.update(account, epoch) { s -> ids.fold(s) { acc, id -> acc.markRetryRequired(id) } }
                    return failure(c, account, SyncErrorKind.TIMEOUT, 0)
                }
                is SendResult.Failed -> {
                    if (result.kind == SyncErrorKind.NETWORK || result.kind == SyncErrorKind.TIMEOUT || result.kind == SyncErrorKind.FLOOD_WAIT) {
                        host.update(account, epoch) { s -> ids.fold(s) { acc, id -> acc.markRetryRequired(id) } }
                    }
                    return failure(c, account, result.kind, result.retryAfterSeconds)
                }
            }
        }
        return null
    }

    /** Acknowledged entries are removed in a write of their own, after the acknowledgements are durable. */
    private fun prune(account: TelegramUserId, epoch: Long) {
        val view = current(account, epoch) ?: return
        if (view.state.outbox.none { it.status == OutboxStatus.ACKNOWLEDGED }) return
        host.update(account, epoch) { it.pruneAcknowledged() }
    }

    // ---- reports -------------------------------------------------------------------------------

    private fun current(account: TelegramUserId, epoch: Long): SyncView? =
        host.view(account)?.takeIf { it.epoch == epoch && it.state.accountId == account.value }

    private fun failure(c: Counters, account: TelegramUserId, kind: SyncErrorKind, retryAfterSeconds: Int): SyncReport {
        val delay = backoff.onFailure(clock(), kind, retryAfterSeconds)
        return when (kind) {
            SyncErrorKind.NETWORK, SyncErrorKind.TIMEOUT, SyncErrorKind.FLOOD_WAIT ->
                report(c, account, SyncStatus.BACKOFF, retryAfterMs = delay)
            SyncErrorKind.CHANNEL_MISSING -> report(c, account, SyncStatus.RECOVERY_REQUIRED, RecoveryReason.CHANNEL_MISSING, retryAfterMs = delay)
            SyncErrorKind.UNAUTHORIZED -> report(c, account, SyncStatus.RECOVERY_REQUIRED, RecoveryReason.UNAUTHORIZED, retryAfterMs = delay)
            SyncErrorKind.SESSION_REVOKED -> report(c, account, SyncStatus.RECOVERY_REQUIRED, RecoveryReason.SESSION_REVOKED, retryAfterMs = delay)
        }
    }

    private fun recovery(c: Counters, account: TelegramUserId, reason: RecoveryReason): SyncReport {
        val delay = backoff.onFailure(clock(), SyncErrorKind.NETWORK, 0)
        return report(c, account, SyncStatus.RECOVERY_REQUIRED, reason, retryAfterMs = delay)
    }

    private fun corrupt(c: Counters, account: TelegramUserId, messageId: Long, error: PlaylistSyncProtocol.Error): SyncReport {
        val delay = backoff.onFailure(clock(), SyncErrorKind.NETWORK, 0)
        val reason = when (error) {
            PlaylistSyncProtocol.Error.FUTURE_VERSION -> RecoveryReason.FUTURE_PROTOCOL
            PlaylistSyncProtocol.Error.WRONG_ACCOUNT -> RecoveryReason.WRONG_ACCOUNT
            else -> RecoveryReason.CORRUPT_MESSAGE
        }
        return report(c, account, SyncStatus.RECOVERY_REQUIRED, reason, error, messageId, delay)
    }

    private fun report(
        c: Counters, account: TelegramUserId, status: SyncStatus,
        recovery: RecoveryReason? = null, error: PlaylistSyncProtocol.Error? = null,
        badId: Long = 0L, retryAfterMs: Long = 0L,
    ): SyncReport {
        val state = host.view(account)?.state
        return SyncReport(
            status = status, recovery = recovery, protocolError = error, badMessageId = badId,
            pagesFetched = c.pages, messagesRead = c.read, foreignSkipped = c.foreign,
            opsApplied = c.applied, opsAcknowledged = c.acked, envelopesSent = c.sent,
            cursor = state?.checkpoint?.lastAppliedServerId ?: 0L,
            pendingLeft = state?.outbox?.count { it.status != OutboxStatus.ACKNOWLEDGED } ?: 0,
            retryAfterMs = retryAfterMs,
        )
    }
}
