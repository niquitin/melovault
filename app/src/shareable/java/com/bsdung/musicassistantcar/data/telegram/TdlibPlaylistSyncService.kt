package com.bsdung.musicassistantcar.data.telegram

import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.atomic.AtomicLong

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
    /** The most operations ONE run may send, whatever a caller asks for. Production keeps the default; only a unit fake raises it. */
    private val sendBudgetCap: Int = SyncSendBudget.PRODUCTION_MAX,
    /** Creating a Telegram channel is LOCKED unless the build explicitly unlocks it (never in Release, off by default in QA). */
    private val channelCreationUnlocked: Boolean = false,
    private val isActive: () -> Boolean = { true },
    /** Nhiều Playlist có tên: the host of the named playlists, or null when they are not wired (then [runNamedSync] is denied). */
    private val namedHost: NamedPlaylistSyncHost? = null,
) {
    private val hub = TdlibSyncHub(owner, metrics, alerts)
    private val lock = ReentrantLock()
    private val backoffs = HashMap<Long, SyncBackoff>()

    /** Its own wait per account: a failure of the named playlists never delays the favourites, except through the scheduler. */
    private val namedBackoffs = HashMap<Long, SyncBackoff>()
    @Volatile private var activeReader: TdlibHistoryReader? = null
    private var attached = false
    private val cancellation = AtomicLong()

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
        cancellation.incrementAndGet()
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
        if (!isActive()) return denied(SyncDenied.NOT_OPEN)
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
        session(account, { if (!channelCreationUnlocked) SyncDenied.CREATE_LOCKED else gate.mayCreate(account) }) { epoch ->
            directory(account, epoch).create(boundChannel(account))
        }

    fun bindChannel(account: TelegramUserId, chatId: Long): Gated<BindResult> {
        val token = cancellation.get()
        return session(account, { gate.mayRun(account) }) { epoch ->
            directory(account, epoch).bind(guardedHost { activeScope(account, epoch, token) }, account, chatId)
        }
    }

    private fun activeScope(account: TelegramUserId, epoch: Long, token: Long) =
        isActive() && cancellation.get() == token && owner.authorizedAccount() == account.value &&
            owner.authEpoch() == epoch && gate.mayRun(account) == null

    /** Late network answers cannot commit after foreground, consent or auth ownership ended. */
    private fun guardedHost(valid: () -> Boolean) = object : PlaylistSyncHost {
        override fun view(account: TelegramUserId): SyncView? = if (valid()) host.view(account) else null
        override fun update(account: TelegramUserId, epoch: Long, transform: (PlaylistState) -> PlaylistState): UpdateResult {
            if (!valid()) return UpdateResult.Stale
            return host.update(account, epoch) { state ->
                check(valid()) { "Sync scope ended" }
                transform(state)
            }
        }
    }

    /**
     * One synchronisation run of the P2 coordinator over TDLib. Manual: [SyncMode.FULL_REPLAY] is the hook for the
     * "late message below the cursor" policy (P4 decides when). A run that may SEND ([sendBudget] above zero) with
     * something waiting needs consent (c): without it nothing is called at all. A read-only run (budget 0) needs only (a)
     * and a bound channel; it reads the history and acknowledges what is already there.
     */
    fun runSync(account: TelegramUserId, mode: SyncMode = SyncMode.INCREMENTAL, sendBudget: Int = 0): Gated<SyncReport> {
        val cancellationAtStart = cancellation.get()
        gate.mayRun(account)?.let { return denied(it) }
        // Never unlimited: no number, zero or a negative number is a read-and-acknowledge run; a big number is cut to the cap.
        val budget = SyncSendBudget.clamp(sendBudget, sendBudgetCap)
        val view = host.view(account) ?: return denied(SyncDenied.NOT_OPEN)
        val channel = view.state.checkpoint.channelId
        if (channel == 0L) return Gated.Done(SyncReport(SyncStatus.NO_CHANNEL)) // never creates one, never touches TDLib
        val waiting = view.state.outbox.any { it.status != OutboxStatus.ACKNOWLEDGED }
        // Only a run that may SEND needs consent (c). A read-only run (budget 0) needs just (a) and a bound channel: it reads the
        // history and acknowledges what is already there, which is how the doubt about earlier sends is ended. Consent (c) is
        // never switched on by this, and the transport checks it again on every dispatch.
        if (waiting && budget > 0 && gate.mayTransmit(account) != null) return denied(SyncDenied.TRANSMIT_NOT_ALLOWED)
        return session(account, { null }) { epoch ->
            fun valid() = activeScope(account, epoch, cancellationAtStart)
            val scopedHost = guardedHost(::valid)
            metrics.count(SyncMetrics.Event.RUN_STARTED)
            val started = clock()
            val backoff = backoffs.getOrPut(account.value) { SyncBackoff() }
            hub.watchChannel(channel)
            val reader = TdlibHistoryReader(hub, account.value, epoch, channel, historyLimits, clock)
            activeReader = reader
            try {
                val transport = TdlibPlaylistSyncTransport(
                    hub, account.value, epoch, channel, reader, timeouts,
                    sendAllowed = { valid() && gate.mayTransmit(account) == null },
                )
                val report = PlaylistSyncCoordinator(scopedHost, transport, clock, config, backoff).runOnce(account, mode, budget)
                metrics.runFinished(clock() - started, backoff.failures)
                metrics.count(SyncMetrics.Event.RUN_FINISHED)
                report
            } finally {
                reader.forget()
                activeReader = null
            }
        }
    }

    private fun guardedNamedHost(source: NamedPlaylistSyncHost, valid: () -> Boolean) = object : NamedPlaylistSyncHost {
        override fun view(account: TelegramUserId): NamedSyncView? = if (valid()) source.view(account) else null
        override fun update(account: TelegramUserId, epoch: Long, transform: (NamedPlaylistState) -> NamedPlaylistState): NamedUpdateResult {
            if (!valid()) return NamedUpdateResult.Stale
            return source.update(account, epoch) { state ->
                check(valid()) { "Sync scope ended" }
                transform(state)
            }
        }
    }

    /**
     * Nhiều Playlist có tên: one run of the named playlists over the SAME verified channel as the favourites (the channel the
     * favourites' checkpoint names; nothing is discovered or created here). The consent, the build switch, the foreground and
     * identity gates are exactly those of [runSync]. At most [NamedSyncLimits.PRODUCTION_ENVELOPES] message per run, whatever
     * a caller asks for; zero reads and acknowledges only. A named run that is interrupted by a replay in progress resumes it.
     */
    fun runNamedSync(
        account: TelegramUserId,
        mode: SyncMode = SyncMode.INCREMENTAL,
        maxEnvelopes: Int = NamedSyncLimits.PRODUCTION_ENVELOPES,
    ): Gated<SyncReport> {
        val named = namedHost ?: return denied(SyncDenied.NOT_OPEN)
        val cancellationAtStart = cancellation.get()
        gate.mayRun(account)?.let { return denied(it) }
        val envelopes = maxEnvelopes.coerceIn(0, NamedSyncLimits.PRODUCTION_ENVELOPES)
        val budget = envelopes * NamedSyncLimits.OPS_PER_ENVELOPE
        val channel = (host.view(account) ?: return denied(SyncDenied.NOT_OPEN)).state.checkpoint.channelId
        if (channel == 0L) return Gated.Done(SyncReport(SyncStatus.NO_CHANNEL)) // never creates one, never touches TDLib
        val view = named.view(account) ?: return denied(SyncDenied.NOT_OPEN)
        val waiting = view.state.outbox.any { it.status != OutboxStatus.ACKNOWLEDGED }
        if (waiting && budget > 0 && gate.mayTransmit(account) != null) return denied(SyncDenied.TRANSMIT_NOT_ALLOWED)
        val effectiveMode =
            if (mode == SyncMode.INCREMENTAL && view.state.checkpoint.replayPosition != SyncCheckpoint.NO_REPLAY) SyncMode.FULL_REPLAY else mode
        return session(account, { null }) { epoch ->
            fun valid() = activeScope(account, epoch, cancellationAtStart)
            val scopedHost = guardedNamedHost(named, ::valid)
            metrics.count(SyncMetrics.Event.RUN_STARTED)
            val started = clock()
            val backoff = namedBackoffs.getOrPut(account.value) { SyncBackoff() }
            hub.watchChannel(channel)
            val reader = TdlibHistoryReader(hub, account.value, epoch, channel, historyLimits, clock)
            activeReader = reader
            try {
                val transport = TdlibPlaylistSyncTransport(
                    hub, account.value, epoch, channel, reader, timeouts,
                    sendAllowed = { valid() && gate.mayTransmit(account) == null },
                )
                val report = NamedPlaylistSyncCoordinator(
                    scopedHost, transport, clock, config.copy(maxEnvelopesPerRun = maxOf(1, envelopes)), backoff,
                ).runNamedOnce(account, channel, effectiveMode, budget)
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
