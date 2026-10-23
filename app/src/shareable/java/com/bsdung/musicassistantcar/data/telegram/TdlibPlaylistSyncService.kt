package com.bsdung.musicassistantcar.data.telegram

import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock

internal sealed class Gated<out T> {
    data class Denied(val reason: SyncDenied) : Gated<Nothing>()
    data class Done<T>(val value: T) : Gated<T>()
}

/**
 * E96 P3: the one entry point P4 will use. Nothing in the app constructs it yet, and with the build switch off
 * ([TelegramPlaylistFlags.SYNC_ENABLED] = false) every method answers [SyncDenied.BUILD_OFF] before it touches TDLib.
 *
 * Each call binds the sync sink to the existing TDLib owner only for the duration of the call (or uses a binding made by
 * [attach]), checks the account and auth epoch, and unbinds again. It starts no thread of its own (the owner's dispatch
 * thread lives only while bound), no service, no receiver and no timer: every run is started by a caller, one at a time.
 * Blocking: call it from a worker thread (see [SyncTrigger]).
 */
internal class TdlibPlaylistSyncService(
    private val owner: SyncOwnerPort,
    private val host: PlaylistSyncHost,
    private val gate: SyncGate,
    private val clock: () -> Long = System::currentTimeMillis,
    private val config: SyncConfig = SyncConfig(),
    private val timeouts: TdlibTransportTimeouts = TdlibTransportTimeouts(),
    private val historyLimits: HistoryLimits = HistoryLimits(),
    private val discoveryLimits: DiscoveryLimits = DiscoveryLimits(),
    val metrics: SyncMetrics = SyncMetrics(),
    val alerts: ContinuityAlerts = ContinuityAlerts(),
) {
    private val hub = TdlibSyncHub(owner, metrics, alerts)
    private val lock = ReentrantLock()
    private val backoffs = HashMap<Long, SyncBackoff>()
    @Volatile private var activeReader: TdlibHistoryReader? = null
    private var attached = false

    /** Keeps the sink bound between calls (for a P4 screen that stays open). It never keeps the client alive. */
    fun attach(account: TelegramUserId): Gated<Boolean> {
        gate.mayRun(account)?.let { return denied(it) }
        if (!hub.open()) return Gated.Denied(SyncDenied.SINK_BUSY)
        // Deletions/edits in the bound channel are continuity evidence from now on.
        host.view(account)?.state?.checkpoint?.channelId?.takeIf { it != 0L }?.let { hub.watchChannel(it) }
        attached = true
        return Gated.Done(true)
    }

    fun detach() {
        attached = false
        hub.close()
    }

    /** Stops the unfinished work at its next step (a scan, a wait). */
    fun cancel() {
        activeReader?.cancel()
        hub.abortAll()
    }

    /** A person looked at a deleted/edited-message alert. The next read is allowed again; nothing is repaired or deleted. */
    fun acknowledgeContinuity() = alerts.acknowledge()

    fun continuityAlert(): ContinuityAlert? = alerts.pending()

    private fun denied(reason: SyncDenied): Gated.Denied {
        metrics.count(SyncMetrics.Event.GATE_REFUSED)
        return Gated.Denied(reason)
    }

    private inline fun <T> session(account: TelegramUserId, denyIf: () -> SyncDenied?, body: (epoch: Long) -> T): Gated<T> {
        denyIf()?.let { return denied(it) }
        if (!lock.tryLock()) return Gated.Denied(SyncDenied.BUSY)
        var openedHere = false
        try {
            if (owner.authorizedAccount() != account.value) return denied(SyncDenied.NOT_AUTHORIZED)
            val epoch = owner.authEpoch()
            if (!hub.isBound) {
                if (!hub.open()) return Gated.Denied(SyncDenied.SINK_BUSY)
                openedHere = true
            }
            return Gated.Done(body(epoch))
        } finally {
            if (openedHere && !attached) hub.close()
            lock.unlock()
        }
    }

    private fun directory(account: TelegramUserId, epoch: Long) =
        TdlibChannelDirectory(hub, account.value, epoch, timeouts.requestMs, discoveryLimits)

    /** The channel the checkpoint already names (0 = none): it is verified directly and no other is ever created. */
    private fun boundChannel(account: TelegramUserId): Long = host.view(account)?.state?.checkpoint?.channelId ?: 0L

    fun discoverChannel(account: TelegramUserId): Gated<ChannelDiscovery> =
        session(account, { gate.mayRun(account) }) { epoch ->
            directory(account, epoch).discover(boundChannel(account))
        }

    /** Needs consent (b) as well as (a). Creates nothing if a channel already exists or is ambiguous. */
    fun createChannel(account: TelegramUserId): Gated<CreateOutcome> =
        session(account, { gate.mayCreate(account) }) { epoch ->
            directory(account, epoch).create(boundChannel(account))
        }

    fun bindChannel(account: TelegramUserId, chatId: Long): Gated<BindResult> =
        session(account, { gate.mayRun(account) }) { epoch ->
            directory(account, epoch).bind(host, account, chatId)
        }

    /**
     * One synchronisation run of the P2 coordinator over TDLib. Manual: [SyncMode.FULL_REPLAY] is the hook for the
     * "late message below the cursor" policy (P4 decides when). Without consent (c) and with something waiting to be
     * sent, nothing is called at all.
     */
    fun runSync(account: TelegramUserId, mode: SyncMode = SyncMode.INCREMENTAL): Gated<SyncReport> {
        gate.mayRun(account)?.let { return denied(it) }
        val view = host.view(account) ?: return denied(SyncDenied.NOT_OPEN)
        val channel = view.state.checkpoint.channelId
        if (channel == 0L) return Gated.Done(SyncReport(SyncStatus.NO_CHANNEL)) // never creates one, never touches TDLib
        val waiting = view.state.outbox.any { it.status != OutboxStatus.ACKNOWLEDGED }
        if (waiting && gate.mayTransmit(account) != null) return denied(SyncDenied.TRANSMIT_NOT_ALLOWED)
        return session(account, { null }) { epoch ->
            metrics.count(SyncMetrics.Event.RUN_STARTED)
            val started = clock()
            val backoff = backoffs.getOrPut(account.value) { SyncBackoff() }
            hub.watchChannel(channel)
            val reader = TdlibHistoryReader(hub, account.value, epoch, channel, historyLimits, clock)
            activeReader = reader
            try {
                val transport = TdlibPlaylistSyncTransport(
                    hub, account.value, epoch, channel, reader, timeouts,
                    sendAllowed = { gate.mayTransmit(account) == null },
                )
                val report = PlaylistSyncCoordinator(host, transport, clock, config, backoff).runOnce(account, mode)
                metrics.runFinished(clock() - started, backoff.failures)
                metrics.count(SyncMetrics.Event.RUN_FINISHED)
                report
            } finally {
                reader.forget()
                activeReader = null
            }
        }
    }
}

internal enum class SyncReason { MANUAL, RESUME, AFTER_SAVE, FULL_REPLAY }

/**
 * Bounded trigger bridge for P4: "start now", "app resumed", "after a Save", "full replay". At most one run at a time on the
 * executor the caller gives; requests that arrive during a run are merged into ONE more run. It schedules nothing by itself
 * (no timer, no WorkManager, no boot receiver) and stops re-running after a failing result, so it cannot loop.
 */
internal class SyncTrigger(
    private val service: TdlibPlaylistSyncService,
    private val executor: Executor,
    private val onResult: (SyncReason, Gated<SyncReport>) -> Unit = { _, _ -> },
) {
    private var running = false
    private var again: SyncReason? = null

    fun request(account: TelegramUserId, reason: SyncReason) {
        synchronized(this) {
            if (running) {
                again = if (again == SyncReason.FULL_REPLAY || reason == SyncReason.FULL_REPLAY) SyncReason.FULL_REPLAY else reason
                return
            }
            running = true
        }
        executor.execute { loop(account, reason) }
    }

    private fun loop(account: TelegramUserId, first: SyncReason) {
        var reason = first
        while (true) {
            val result = try {
                service.runSync(account, if (reason == SyncReason.FULL_REPLAY) SyncMode.FULL_REPLAY else SyncMode.INCREMENTAL)
            } catch (_: Exception) {
                Gated.Denied(SyncDenied.BUSY)
            }
            runCatching { onResult(reason, result) }
            val retryable = (result as? Gated.Done)?.value?.status.let {
                it == SyncStatus.OK || it == SyncStatus.CATCH_UP_INCOMPLETE || it == SyncStatus.OUTBOX_INCOMPLETE
            }
            val next = synchronized(this) {
                val pendingAgain = again
                again = null
                if (pendingAgain == null || !retryable) {
                    running = false
                    null
                } else pendingAgain
            }
            reason = next ?: return
        }
    }
}
