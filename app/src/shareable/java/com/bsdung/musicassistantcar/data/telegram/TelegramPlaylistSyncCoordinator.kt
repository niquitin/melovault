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
    /**
     * Read the whole history again from the start. Always safe (applying is idempotent); picks up anything that
     * showed up late. A replay longer than one run is CONTINUED by calling this again - after a pause or a
     * restart too - from the position stored in the checkpoint, until a run reports [SyncStatus.OK].
     */
    FULL_REPLAY,
}

internal enum class SyncStatus {
    /**
     * The whole history of the channel was read AND nothing is waiting to be sent (judged at the end of the run).
     * The only status that means "synchronised".
     */
    OK,
    NOT_OPEN, NO_CHANNEL,
    /**
     * The run's page budget ran out while more history remained. Progress was committed (see [SyncReport.cursor] and
     * [SyncReport.replayPosition]); nothing was sent, and the playlist is NOT known to be up to date. Call again.
     */
    CATCH_UP_INCOMPLETE,
    /**
     * The history was read, but the run's send budget ran out (or new work arrived meanwhile) with operations still
     * waiting - see [SyncReport.pendingLeft]. Everything sent was acknowledged; what is left is untouched, in order,
     * with the same ids. Not synchronised: call again. Distinct from [CATCH_UP_INCOMPLETE], which is about reading.
     */
    OUTBOX_INCOMPLETE,
    /** Called again before the wait is over: no transport call was made. */
    BACKOFF_ACTIVE,
    /** Temporary trouble (network, timeout, FLOOD_WAIT, lost answer). Retry after [SyncReport.retryAfterMs]. */
    BACKOFF,
    /** Something needs a person or a later phase (channel gone, access revoked, damaged log, broken transport). Nothing was deleted. */
    RECOVERY_REQUIRED,
    /** The account changed while the run was in flight; the run stopped without touching the new account. */
    STALE_SESSION,
    SAVE_FAILED,
}

internal enum class RecoveryReason {
    CHANNEL_MISSING, UNAUTHORIZED, SESSION_REVOKED, CORRUPT_MESSAGE, FUTURE_PROTOCOL, WRONG_ACCOUNT, OP_TOO_LARGE,
    /** A page was not the proven next part of the history. Refused without moving the cursor. */
    TRANSPORT_CONTRACT,
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
    /** The durable cursor: every message up to here was applied. */
    val cursor: Long = 0L,
    /** Where a FULL_REPLAY stands, or [SyncCheckpoint.NO_REPLAY]. With [SyncStatus.CATCH_UP_INCOMPLETE]: the next run continues here. */
    val replayPosition: Long = SyncCheckpoint.NO_REPLAY,
    val pendingLeft: Int = 0,
    val retryAfterMs: Long = 0L,
)

internal data class SyncConfig(
    val pageSize: Int = 100,
    val maxPagesPerRun: Int = 100,
    val maxEnvelopesPerRun: Int = 50,
    /** Asks once more, with a page of one, whether the oldest unread message is really the first one of the page. */
    val verifyPageStart: Boolean = true,
) {
    init {
        // A budget that cannot do any work would let a run report success having done nothing.
        require(pageSize >= 1) { "pageSize must be at least 1" }
        require(maxPagesPerRun >= 1) { "maxPagesPerRun must be at least 1" }
        require(maxEnvelopesPerRun >= 1) { "maxEnvelopesPerRun must be at least 1" }
    }
}

internal enum class ReplayMark { UNCHANGED, SET, CLEAR }

/**
 * Applies what other devices (or our own earlier sends) wrote, acknowledges our own, and moves the cursor to
 * [position] - as one new state. For a FULL_REPLAY, [replay] also records where the replay stands (or that it ended).
 */
internal fun PlaylistState.applyRemoteBatch(
    ops: List<PlaylistOp>, ackIds: Set<String>, position: Long, replay: ReplayMark = ReplayMark.UNCHANGED,
): PlaylistState {
    var next = this
    for (op in ops) {
        next = next.applyRemote(op)
        // If this device's own earlier work comes back with a higher counter than the stored one (an older
        // file was restored), keep going from there: a tag must never be handed out twice.
        val own = Dot.parse(op.opId)
        if (own != null && own.device == next.deviceId && own.counter > next.opCounter) next = next.copy(opCounter = own.counter)
    }
    for (id in ackIds) next = next.acknowledge(id)
    val cp = next.checkpoint
    return next.copy(
        checkpoint = cp.copy(
            lastAppliedServerId = maxOf(cp.lastAppliedServerId, position),
            replayPosition = when (replay) {
                ReplayMark.UNCHANGED -> cp.replayPosition
                ReplayMark.SET -> maxOf(cp.replayPosition, position)
                ReplayMark.CLEAR -> SyncCheckpoint.NO_REPLAY
            },
        )
    )
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
 *  - The transport owes a COMPLETE PREFIX: each page is the next part of the history after the cursor, with
 *    a proof ([FetchResult.Page.completeThroughId]). The cursor moves to that proof and never past it. A page
 *    without proof, with a proof below the cursor, that claims more but makes no progress, or whose oldest
 *    message is not the oldest unread one (checked with a second look), is REFUSED: nothing applied, cursor
 *    unmoved, RECOVERY_REQUIRED(TRANSPORT_CONTRACT). A newest-first page can therefore never skip anything.
 *  - Running out of the page budget with history left is [SyncStatus.CATCH_UP_INCOMPLETE], never OK, and
 *    nothing is sent until the history is read. A FULL_REPLAY keeps its position in the checkpoint, so it
 *    resumes across runs and restarts instead of reading the same first pages again.
 *  - Running out of the SEND budget with operations still waiting is [SyncStatus.OUTBOX_INCOMPLETE], never OK
 *    (also when a Save arrived while the run was in flight). Everything sent was acknowledged; what is left is
 *    not touched - same ids, same order, still waiting - and the next run continues with it. OK needs the
 *    history read AND an empty queue. A budget of zero or less is refused when the configuration is made.
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
 *  - Failures set a backoff (a FLOOD_WAIT is obeyed exactly as given); calling again too early makes no
 *    transport call.
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

    private sealed class FetchOutcome {
        class Stop(val report: SyncReport) : FetchOutcome()
        /** [more]: the budget ended with history still unread. */
        class Done(val more: Boolean) : FetchOutcome()
    }

    private sealed class SendOutcome {
        class Stop(val report: SyncReport) : SendOutcome()
        /** Nothing waits any more. */
        object Drained : SendOutcome()
        /** The send budget is spent and [left] operations still wait (untouched). */
        class BudgetSpent(val left: Int) : SendOutcome()
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

        when (val fetched = fetchPhase(account, epoch, channel, scope, mode, c)) {
            is FetchOutcome.Stop -> return fetched.report
            is FetchOutcome.Done -> if (fetched.more) return report(c, account, SyncStatus.CATCH_UP_INCOMPLETE)
        }
        val sent = sendPhase(account, epoch, channel, scope, c)
        if (sent is SendOutcome.Stop) return sent.report
        prune(account, epoch)
        backoff.onSuccess()
        // OK is judged on what is true NOW, not on what this run planned: it needs the history read AND nothing
        // waiting. A spent budget, or a Save that arrived while the run was in flight, leaves operations waiting.
        val waiting = current(account, epoch)?.state?.outbox?.count { it.status != OutboxStatus.ACKNOWLEDGED }
            ?: return report(c, account, SyncStatus.STALE_SESSION)
        return report(c, account, if (sent is SendOutcome.BudgetSpent || waiting > 0) SyncStatus.OUTBOX_INCOMPLETE else SyncStatus.OK)
    }

    // ---- fetch ---------------------------------------------------------------------------------

    private fun fetchPhase(
        account: TelegramUserId, epoch: Long, channel: Long, scope: String, mode: SyncMode, c: Counters,
    ): FetchOutcome {
        val start = current(account, epoch) ?: return stale(c, account)
        val replaying = mode == SyncMode.FULL_REPLAY
        // A replay resumes where it stood; an incremental read starts at the durable cursor.
        var cursor = if (replaying) maxOf(0L, start.state.checkpoint.replayPosition) else start.state.checkpoint.lastAppliedServerId
        var pages = 0
        var more = false
        while (pages < config.maxPagesPerRun) {
            val view = current(account, epoch) ?: return stale(c, account)
            val result = try {
                transport.fetchAfter(channel, cursor, config.pageSize)
            } catch (_: Exception) {
                FetchResult.Failed(SyncErrorKind.NETWORK)
            }
            pages++
            c.pages++
            val page = when (result) {
                is FetchResult.Failed -> return FetchOutcome.Stop(failure(c, account, result.kind, result.retryAfterSeconds))
                is FetchResult.Page -> result
            }
            // The page must prove it is the next part of the history, and must move us forward if it says there is more.
            val proof = page.completeThroughId
            if (proof == FetchResult.Page.UNPROVEN || proof < cursor || (page.hasMore && proof <= cursor)) {
                return FetchOutcome.Stop(contractViolation(c, account))
            }
            // Do not trust the order, the repeats or the ids of a page: sort, drop repeats, keep what is proven.
            val messages = page.messages.distinctBy { it.id }.sortedBy { it.id }.filter { it.id > cursor && it.id <= proof }
            if (config.verifyPageStart && messages.isNotEmpty()) {
                val probe = try {
                    transport.fetchAfter(channel, cursor, 1)
                } catch (_: Exception) {
                    FetchResult.Failed(SyncErrorKind.NETWORK)
                }
                when (probe) {
                    is FetchResult.Failed -> return FetchOutcome.Stop(failure(c, account, probe.kind, probe.retryAfterSeconds))
                    is FetchResult.Page -> {
                        val oldest = probe.messages.filter { it.id > cursor }.minOfOrNull { it.id }
                        if (oldest != messages.first().id) return FetchOutcome.Stop(contractViolation(c, account))
                    }
                }
            }
            c.read += messages.size

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
            // Whole page good: everything up to the proof is done (also across gaps in the ids). A bad message
            // stops us just before it.
            val newPosition = if (badError == null) maxOf(proof, lastGood) else lastGood
            val finishing = !page.hasMore && badError == null
            val replayMark = when {
                !replaying -> ReplayMark.UNCHANGED
                finishing -> ReplayMark.CLEAR
                else -> ReplayMark.SET
            }
            val replayToClear = replaying && finishing && view.state.checkpoint.replayPosition != SyncCheckpoint.NO_REPLAY
            if (newPosition > cursor || replayToClear) {
                // One write: operations + acknowledgements + cursor (+ replay position), for the good messages before any bad one.
                when (host.update(account, epoch) { it.applyRemoteBatch(ops, ackIds, newPosition, replayMark) }) {
                    is UpdateResult.Committed -> {
                        c.applied += ops.size
                        c.acked += ackIds.size
                        cursor = maxOf(cursor, newPosition)
                    }
                    is UpdateResult.Stale -> return stale(c, account)
                    else -> return FetchOutcome.Stop(report(c, account, SyncStatus.SAVE_FAILED))
                }
            }
            badError?.let { return FetchOutcome.Stop(corrupt(c, account, badId, it)) }
            more = page.hasMore
            if (!page.hasMore) break
        }
        return FetchOutcome.Done(more)
    }

    // ---- send ----------------------------------------------------------------------------------

    private fun sendPhase(account: TelegramUserId, epoch: Long, channel: Long, scope: String, c: Counters): SendOutcome {
        fun stop(report: SyncReport) = SendOutcome.Stop(report)
        var envelopes = 0
        while (envelopes < config.maxEnvelopesPerRun) {
            val view = current(account, epoch) ?: return stop(report(c, account, SyncStatus.STALE_SESSION))
            val waiting = view.state.outbox.filter { it.status != OutboxStatus.ACKNOWLEDGED }.map { it.op }
            if (waiting.isEmpty()) return SendOutcome.Drained
            val envelope = PlaylistSyncProtocol.planFirstEnvelope(scope, view.state.deviceId, waiting)
                ?: return stop(recovery(c, account, RecoveryReason.OP_TOO_LARGE))
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
                    is UpdateResult.Stale -> return stop(report(c, account, SyncStatus.STALE_SESSION))
                    // The server has them; our note of it was not written. The next run finds the same ids in the history.
                    else -> return stop(report(c, account, SyncStatus.SAVE_FAILED))
                }
                is SendResult.Unknown -> {
                    host.update(account, epoch) { s -> ids.fold(s) { acc, id -> acc.markRetryRequired(id) } }
                    return stop(failure(c, account, SyncErrorKind.TIMEOUT, 0))
                }
                is SendResult.Failed -> {
                    if (result.kind == SyncErrorKind.NETWORK || result.kind == SyncErrorKind.TIMEOUT || result.kind == SyncErrorKind.FLOOD_WAIT) {
                        host.update(account, epoch) { s -> ids.fold(s) { acc, id -> acc.markRetryRequired(id) } }
                    }
                    return stop(failure(c, account, result.kind, result.retryAfterSeconds))
                }
            }
        }
        // The budget is spent. Look once more: the last message may just have emptied the queue.
        val left = current(account, epoch)?.state?.outbox?.count { it.status != OutboxStatus.ACKNOWLEDGED }
            ?: return stop(report(c, account, SyncStatus.STALE_SESSION))
        return if (left == 0) SendOutcome.Drained else SendOutcome.BudgetSpent(left)
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

    private fun stale(c: Counters, account: TelegramUserId): FetchOutcome.Stop =
        FetchOutcome.Stop(report(c, account, SyncStatus.STALE_SESSION))

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

    private fun contractViolation(c: Counters, account: TelegramUserId): SyncReport =
        recovery(c, account, RecoveryReason.TRANSPORT_CONTRACT)

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
            replayPosition = state?.checkpoint?.replayPosition ?: SyncCheckpoint.NO_REPLAY,
            pendingLeft = state?.outbox?.count { it.status != OutboxStatus.ACKNOWLEDGED } ?: 0,
            retryAfterMs = retryAfterMs,
        )
    }
}
