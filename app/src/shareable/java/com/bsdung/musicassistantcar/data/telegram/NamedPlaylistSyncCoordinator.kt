package com.bsdung.musicassistantcar.data.telegram

/** Nhiều Playlist có tên, S7: what one production run of the named playlists may send. */
internal object NamedSyncLimits {
    /** ONE message per run (the favourites sync keeps its own budget of [SyncSendBudget.PRODUCTION_MAX] operations). */
    const val PRODUCTION_ENVELOPES = 1

    /** The most operations one message carries. */
    const val OPS_PER_ENVELOPE = NamedPlaylistSyncProtocol.MAX_OPS
}

internal sealed class NamedTransmitPlan {
    /** [ops] is a contiguous prefix of the waiting operations, in outbox order; never empty. */
    data class Planned(val ops: List<NamedOp>) : NamedTransmitPlan()

    /** The budget is spent (or was never given): operations still wait, untouched. */
    object LimitReached : NamedTransmitPlan()

    /** At least one operation was handed over before and nobody knows whether it arrived: nothing at all is sent. */
    object UnconfirmedBlocked : NamedTransmitPlan()

    /** A removal would be sent without the creation or addition it observed (which is not among the chosen operations). */
    object DependencyBlocked : NamedTransmitPlan()
}

/** Same rules as [TransmitPlanner], for [NamedOp]: first k waiting operations in order, nothing while a delivery is in doubt, no removal before its own addition. */
internal object NamedTransmitPlanner {
    fun plan(waiting: List<NamedOutboxEntry>, budget: Int, ownDevice: String): NamedTransmitPlan {
        if (waiting.any { it.status == OutboxStatus.RETRY_REQUIRED }) return NamedTransmitPlan.UnconfirmedBlocked
        val k = minOf(budget.coerceAtLeast(0), waiting.size)
        if (k == 0) return NamedTransmitPlan.LimitReached
        val chosen = waiting.take(k).map { it.op }
        val addIndex = HashMap<Dot, Int>()
        waiting.forEachIndexed { i, entry ->
            if (entry.op.type == NamedOpType.LCREATE || entry.op.type == NamedOpType.IADD) entry.op.dots.forEach { addIndex[it] = i }
        }
        chosen.forEachIndexed { i, op ->
            if (op.type != NamedOpType.LDELETE && op.type != NamedOpType.IREMOVE) return@forEachIndexed
            for (dot in op.dots) {
                if (dot.device != ownDevice) continue
                val at = addIndex[dot] ?: continue // acknowledged earlier: fine
                if (at >= i) return NamedTransmitPlan.DependencyBlocked
            }
        }
        return NamedTransmitPlan.Planned(chosen)
    }
}

/**
 * Applies what other devices (or our own earlier sends) wrote, acknowledges our own, and moves the cursor to [position] - as one
 * new state. Same meaning as [applyRemoteBatch] for Playlist V2.
 */
internal fun NamedPlaylistState.applyRemoteBatch(
    ops: List<NamedOp>, ackIds: Set<String>, position: Long, replay: ReplayMark = ReplayMark.UNCHANGED,
): NamedPlaylistState {
    var next = this
    for (op in ops) {
        next = next.applyRemote(op)
        // If this device's own earlier work comes back with a higher counter than the stored one (an older file was
        // restored), keep going from there: a tag must never be handed out twice.
        val own = op.opDot
        if (own.device == next.deviceId && own.counter > next.opCounter) next = next.copy(opCounter = own.counter)
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
 * Nhiều Playlist có tên, S7: one synchronisation run of the named playlists over a [PlaylistSyncTransport] and a
 * [NamedPlaylistSyncHost]. It follows [PlaylistSyncCoordinator] rule for rule and does not change it:
 *  - FETCH (apply others' operations, acknowledge our own that came back, advance the cursor) then SEND (append our waiting
 *    operations), then prune acknowledged entries. A page is committed as ONE write (operations + acknowledgements + cursor).
 *  - The transport owes a COMPLETE PREFIX with a proof; anything else is refused without moving the cursor.
 *  - A `#mvsync` message (and any text that does not start with `#mvlist`) is [NamedPlaylistSyncProtocol.Decoded.Foreign]: skipped,
 *    the cursor moves on. A damaged `#mvlist` message stops the run before it (RECOVERY_REQUIRED), nothing is skipped silently.
 *  - Our own operation must come back exactly as we wrote it (verbatim), otherwise the log is damaged.
 *  - ACK only on evidence (the server's own answer to the send, or the same op id found again in the history). A send whose
 *    answer was lost leaves the operations RETRY_REQUIRED and nothing is sent while any operation is in doubt. The doubt is
 *    written down BEFORE the dispatch (write-ahead) and taken back only for a definite refusal.
 *  - The clock only decides WHEN to try again, never what is in the playlists.
 *
 * The channel is not discovered here: [runNamedOnce] gets the [channelId] of the Playlist V2 sync (already verified by discovery). The
 * first run stores it in this store's own checkpoint; a different one later is RECOVERY_REQUIRED, never a silent switch.
 * [SyncReport] and [SyncStatus] are shared with the favourites sync on purpose, so one status policy reads both.
 */
internal class NamedPlaylistSyncCoordinator(
    private val host: NamedPlaylistSyncHost,
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
        var dispatched = 0
        var sendMs = 0L
        var stage = SyncStage.NONE
        var errorBucket = ErrorBucket.NONE
    }

    private sealed class FetchOutcome {
        class Stop(val report: SyncReport) : FetchOutcome()

        /** [more]: the budget ended with history still unread. */
        class Done(val more: Boolean) : FetchOutcome()
    }

    private sealed class SendOutcome {
        class Stop(val report: SyncReport) : SendOutcome()
        object Drained : SendOutcome()
        class BudgetSpent(val left: Int) : SendOutcome()
        object LimitReached : SendOutcome()
    }

    /** For a person pressing "try again": forget the wait. */
    @Synchronized
    fun resetBackoff() = backoff.reset()

    /**
     * [sendBudget] is the number of OPERATIONS this run may hand to the transport; there is no "unlimited": the default and every
     * number below one mean read-and-acknowledge only.
     */
    @Synchronized
    fun runNamedOnce(account: TelegramUserId, channelId: Long, mode: SyncMode = SyncMode.INCREMENTAL, sendBudget: Int = 0): SyncReport {
        val c = Counters()
        val now = clock()
        if (now < backoff.notBeforeMs) {
            return report(c, account, SyncStatus.BACKOFF_ACTIVE, retryAfterMs = backoff.notBeforeMs - now)
        }
        if (channelId == 0L) return report(c, account, SyncStatus.NO_CHANNEL) // never creates one
        val first = host.view(account) ?: return report(c, account, SyncStatus.NOT_OPEN)
        if (first.state.accountId != account.value) return report(c, account, SyncStatus.NOT_OPEN)
        val epoch = first.epoch
        val bound = first.state.checkpoint.channelId
        if (bound == 0L) {
            when (host.update(account, epoch) { it.copy(checkpoint = it.checkpoint.copy(channelId = channelId)) }) {
                is NamedUpdateResult.Committed -> Unit
                is NamedUpdateResult.Stale -> return report(c, account, SyncStatus.STALE_SESSION)
                else -> return report(c, account, SyncStatus.SAVE_FAILED)
            }
        } else if (bound != channelId) {
            // The playlists were synchronised through another channel: never switch silently.
            return recovery(c, account, RecoveryReason.CHANNEL_MISSING)
        }
        val scope = PlaylistSyncProtocol.scopeOf(account.value)

        when (val fetched = fetchPhase(account, epoch, channelId, scope, mode, c)) {
            is FetchOutcome.Stop -> return fetched.report
            is FetchOutcome.Done -> if (fetched.more) return report(c, account, SyncStatus.CATCH_UP_INCOMPLETE)
        }
        val sent = sendPhase(account, epoch, channelId, scope, sendBudget.coerceAtLeast(0), c)
        if (sent is SendOutcome.Stop) return sent.report
        prune(account, epoch)
        backoff.onSuccess()
        // OK is judged on what is true NOW: it needs the history read AND nothing waiting.
        val waiting = current(account, epoch)?.state?.outbox?.count { it.status != OutboxStatus.ACKNOWLEDGED }
            ?: return report(c, account, SyncStatus.STALE_SESSION)
        if (sent is SendOutcome.LimitReached && waiting > 0) return report(c, account, SyncStatus.SEND_LIMIT_REACHED)
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
            val ops = ArrayList<NamedOp>()
            val ackIds = LinkedHashSet<String>()
            var lastGood = cursor
            var badId = 0L
            var badError: PlaylistSyncProtocol.Error? = null
            for (message in messages) {
                when (val decoded = NamedPlaylistSyncProtocol.decode(message.text, scope)) {
                    is NamedPlaylistSyncProtocol.Decoded.Foreign -> {
                        c.foreign++
                        lastGood = message.id
                    }
                    is NamedPlaylistSyncProtocol.Decoded.Invalid -> {
                        badId = message.id
                        badError = decoded.error
                    }
                    is NamedPlaylistSyncProtocol.Decoded.Ok -> {
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
            // Whole page good: everything up to the proof is done (also across gaps in the ids). A bad message stops us just before it.
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
                    is NamedUpdateResult.Committed -> {
                        c.applied += ops.size
                        c.acked += ackIds.size
                        cursor = maxOf(cursor, newPosition)
                    }
                    is NamedUpdateResult.Stale -> return stale(c, account)
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

    private fun sendPhase(
        account: TelegramUserId, epoch: Long, channel: Long, scope: String, budget: Int, c: Counters,
    ): SendOutcome {
        fun stop(report: SyncReport) = SendOutcome.Stop(report)
        var budgetLeft = budget
        var envelopes = 0
        while (envelopes < config.maxEnvelopesPerRun) {
            val view = current(account, epoch) ?: return stop(report(c, account, SyncStatus.STALE_SESSION))
            val waitingEntries = view.state.outbox.filter { it.status != OutboxStatus.ACKNOWLEDGED }
            if (waitingEntries.isEmpty()) return SendOutcome.Drained
            val planned = when (val plan = NamedTransmitPlanner.plan(waitingEntries, budgetLeft, view.state.deviceId)) {
                // Nothing at all is sent while a delivery is unknown, whatever the budget: the outbox stays as it is.
                NamedTransmitPlan.UnconfirmedBlocked -> {
                    c.stage = SyncStage.READBACK_ABSENT
                    return stop(deliveryUnconfirmed(c, account))
                }
                NamedTransmitPlan.DependencyBlocked -> {
                    c.stage = SyncStage.SEND_PLAN_BLOCKED
                    return stop(recovery(c, account, RecoveryReason.DEPENDENCY_BLOCKED))
                }
                NamedTransmitPlan.LimitReached -> {
                    c.stage = SyncStage.SEND_BUDGET_ZERO
                    return SendOutcome.LimitReached
                }
                is NamedTransmitPlan.Planned -> plan.ops
            }
            val envelope = NamedPlaylistSyncProtocol.planFirstEnvelope(scope, view.state.deviceId, planned)
                ?: return stop(recovery(c, account, RecoveryReason.OP_TOO_LARGE))
            val ids = envelope.ops.map { it.opId }.toSet()
            // WRITE-AHEAD: the doubt is written down BEFORE anything is handed over. If this write does not commit, NOTHING is sent.
            when (host.update(account, epoch) { s -> ids.fold(s) { acc, id -> acc.markRetryRequired(id) } }) {
                is NamedUpdateResult.Committed -> Unit
                is NamedUpdateResult.Stale -> return stop(report(c, account, SyncStatus.STALE_SESSION))
                else -> {
                    c.stage = SyncStage.SEND_PRECHECK_PERSIST
                    return stop(report(c, account, SyncStatus.SAVE_FAILED))
                }
            }
            val startedAt = clock()
            val result = try {
                transport.send(channel, NamedPlaylistSyncProtocol.encode(envelope))
            } catch (_: Exception) {
                SendResult.Unknown(SyncStage.SEND_DISPATCH_ABORTED)
            }
            c.sendMs += (clock() - startedAt).coerceAtLeast(0L)
            envelopes++
            // Charged at the moment of dispatch, whatever the answer. Only a refusal BEFORE anything was handed over is free.
            val refusedBeforeDispatch = result is SendResult.Failed && result.stage in PRE_DISPATCH_STAGES_NAMED
            if (!refusedBeforeDispatch) {
                budgetLeft -= ids.size
                c.dispatched += ids.size
            }
            when (result) {
                is SendResult.Sent -> when (host.update(account, epoch) { s -> ids.fold(s) { acc, id -> acc.acknowledge(id) } }) {
                    is NamedUpdateResult.Committed -> {
                        c.sent++
                        c.acked += ids.size
                    }
                    is NamedUpdateResult.Stale -> return stop(report(c, account, SyncStatus.STALE_SESSION))
                    // The server has them; our note of it was not written. The next run finds the same ids in the history.
                    else -> return stop(report(c, account, SyncStatus.SAVE_FAILED))
                }
                is SendResult.Unknown -> {
                    // Delivery unknown: already flagged on disk (write-ahead), keep their ids and do NOT send them again.
                    c.stage = result.stage
                    c.errorBucket = result.bucket
                    return stop(deliveryUnconfirmed(c, account))
                }
                is SendResult.Failed -> {
                    // A definite refusal: nothing was stored, so the flag is taken back. If that write fails they simply stay flagged.
                    host.update(account, epoch) { s -> ids.fold(s) { acc, id -> acc.revertRefusedDispatch(id) } }
                    c.stage = result.stage
                    c.errorBucket = result.bucket
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

    private fun current(account: TelegramUserId, epoch: Long): NamedSyncView? =
        host.view(account)?.takeIf { it.epoch == epoch && it.state.accountId == account.value }

    private fun stale(c: Counters, account: TelegramUserId): FetchOutcome.Stop =
        FetchOutcome.Stop(report(c, account, SyncStatus.STALE_SESSION))

    /** Delivery of something already handed over is unknown. A short wait before the history is looked at again; never a resend. */
    private fun deliveryUnconfirmed(c: Counters, account: TelegramUserId): SyncReport {
        val delay = backoff.onFailure(clock(), SyncErrorKind.TIMEOUT, 0)
        return report(c, account, SyncStatus.DELIVERY_UNCONFIRMED, retryAfterMs = delay)
    }

    private fun failure(c: Counters, account: TelegramUserId, kind: SyncErrorKind, retryAfterSeconds: Int): SyncReport {
        if (c.stage == SyncStage.NONE) c.stage = SyncStage.FETCH_SCAN
        val delay = backoff.onFailure(clock(), kind, retryAfterSeconds)
        return when (kind) {
            SyncErrorKind.REJECTED -> report(c, account, SyncStatus.RECOVERY_REQUIRED, RecoveryReason.REJECTED_BY_TELEGRAM, retryAfterMs = delay)
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

    private fun contractViolation(c: Counters, account: TelegramUserId): SyncReport {
        c.stage = SyncStage.FETCH_CROSSCHECK
        return recovery(c, account, RecoveryReason.TRANSPORT_CONTRACT)
    }

    private fun corrupt(c: Counters, account: TelegramUserId, messageId: Long, error: PlaylistSyncProtocol.Error): SyncReport {
        c.stage = SyncStage.FETCH_CORRUPT
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
            stage = c.stage, opsDispatched = c.dispatched, errorBucket = c.errorBucket,
            sendDuration = if (c.dispatched > 0) DurationBucket.of(c.sendMs) else null,
        )
    }
}

/** Refusals that happen BEFORE anything is handed to TDLib: they do not use up a send budget. */
private val PRE_DISPATCH_STAGES_NAMED = setOf(
    SyncStage.SEND_PRECHECK_CONSENT, SyncStage.SEND_PRECHECK_IDENTITY, SyncStage.SEND_PRECHECK_CONNECTION, SyncStage.SEND_PRECHECK_TEXT,
)
