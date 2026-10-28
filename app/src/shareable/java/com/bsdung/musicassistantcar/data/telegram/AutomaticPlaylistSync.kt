package com.bsdung.musicassistantcar.data.telegram

/** A cancellable foreground timer. The production implementation serializes all network work. */
internal fun interface SyncScheduledTask { fun cancel() }

/** Immutable review of the exact operations a person is about to allow, in outbox order. */
internal data class PendingPlaylistReview(
    val account: TelegramUserId,
    val authEpoch: Long,
    val playlistEpoch: Long,
    val opCounter: Long,
    val entries: List<OutboxEntry>,
)

/**
 * Foreground-only automatic sync. Owns discovery, setup, manual requests and polling, so they cannot race.
 * No new client, background service or persistence format. Local changes stay durable in the existing outbox.
 */
internal class AutomaticPlaylistSync(
    private val buildEnabled: Boolean,
    private val actions: SyncActionsPort,
    private val store: SyncConsentStore,
    private val host: PlaylistSyncHost,
    private val account: () -> TelegramUserId?,
    private val authEpoch: () -> Long,
    private val schedule: (Long, () -> Unit) -> SyncScheduledTask,
    private val clock: () -> Long = System::currentTimeMillis,
    private val runLog: SyncRunLog = SyncRunLog(),
    private val changed: () -> Unit = {},
) {
    companion object {
        const val POLL_MS = 15_000L
        const val SAVE_DELAY_MS = 500L
        const val CONTINUE_MS = 1_000L
    }

    private data class Scope(val who: TelegramUserId, val auth: Long, val playlist: Long, val generation: Long)
    private var foreground = false
    private var generation = 0L
    private var scheduled: SyncScheduledTask? = null
    private var running = false
    private var requested = false
    // A failed disk write must not revive sending for this account after A -> B -> A or a new auth epoch.
    private val stoppedAccounts = HashSet<Long>()
    private val disableRetryAccounts = HashSet<Long>()
    private val inhibited: Boolean get() = account()?.value in stoppedAccounts
    private var notBefore = 0L
    private var retryAccount: Long? = null
    private var observedAuth: Pair<Long, Long>? = null
    @Volatile var notice: String? = null
        private set
    val needsDisableRetry: Boolean get() = synchronized(this) { account()?.value in disableRetryAccounts }

    @Synchronized fun isRunning(): Boolean = running

    fun setForeground(visible: Boolean) {
        synchronized(this) {
            if (!buildEnabled || foreground == visible) return
            observeAuth()
            foreground = visible
            generation++
            scheduled?.cancel(); scheduled = null
            requested = false
            if (visible) enqueue(0)
        }
        if (!visible) actions.close()
        changed()
    }

    /** Call after the local write committed, never before it. */
    fun afterSave() = request(SAVE_DELAY_MS)
    fun requestNow() = request(0)

    @Synchronized private fun request(delay: Long) {
        if (!buildEnabled || !foreground) return
        observeAuth()
        if (inhibited) return
        // Queue on the serial worker even while an obsolete generation is finishing. Its finally must not
        // resurrect a cancelled scope, nor swallow the next session's explicit playlist-ready notification.
        if (running) requested = true
        enqueue(delay)
    }

    private fun enqueue(delay: Long) {
        scheduled?.cancel()
        val token = generation
        val wait = maxOf(delay, (notBefore - clock()).coerceAtLeast(0))
        scheduled = schedule(wait) { run(token) }
    }

    private fun scope(token: Long): Scope? {
        if (!buildEnabled || !foreground || generation != token) return null
        observeAuth()
        val who = account() ?: return null
        val epoch = authEpoch()
        val view = host.view(who) ?: return null
        if (account() != who || authEpoch() != epoch) return null
        return Scope(who, epoch, view.epoch, token)
    }

    private fun current(s: Scope): Boolean = foreground && generation == s.generation &&
        account() == s.who && authEpoch() == s.auth && host.view(s.who)?.epoch == s.playlist

    private fun observeAuth() {
        val scope = account()?.let { it.value to authEpoch() }
        if (scope != observedAuth) {
            observedAuth = scope
            runLog.clear(); notice = null; notBefore = 0; retryAccount = null
        }
    }

    fun pendingReview(): PendingPlaylistReview? = synchronized(this) {
        val s = scope(generation) ?: return null
        val view = host.view(s.who) ?: return null
        PendingPlaylistReview(s.who, s.auth, s.playlist, view.state.opCounter,
            view.state.outbox.filter { it.status != OutboxStatus.ACKNOWLEDGED }.toList())
    }

    private fun matches(review: PendingPlaylistReview, s: Scope): Boolean {
        val state = host.view(s.who)?.state ?: return false
        return current(s) && review.account == s.who && review.authEpoch == s.auth &&
            review.playlistEpoch == s.playlist && review.opCounter == state.opCounter &&
            review.entries == state.outbox.filter { it.status != OutboxStatus.ACKNOWLEDGED }
    }

    /** A single explicit confirmation covers the v3 warning AND this exact pending review. */
    fun enable(review: PendingPlaylistReview, confirmed: Boolean, completed: (Boolean, String?) -> Unit) {
        synchronized(this) {
            if (!confirmed || !buildEnabled || !foreground || running) {
                completed(false, "Chưa thể bật đồng bộ. Hãy thử lại khi Playlist sẵn sàng.")
                return
            }
            if (clock() < notBefore) {
                completed(false, "Telegram đang yêu cầu chờ. Hãy thử bật lại sau ít phút.")
                return
            }
            scheduled?.cancel(); scheduled = null
            val token = generation
            running = true
            schedule(0) { setup(token, review, completed) }
        }
        changed()
    }

    private fun setup(token: Long, review: PendingPlaylistReview, completed: (Boolean, String?) -> Unit) {
        var success = false
        var message: String? = "Thiết lập đã dừng; chưa bật tự đồng bộ."
        try {
            val s = synchronized(this) { scope(token) } ?: return
            synchronized(this) {
                if (!matches(review, s)) { message = "Danh sách chờ gửi đã đổi. Hãy xem lại trước khi bật."; return }
                // Discovery needs read consent. This intermediate state NEVER authorizes automatic sending.
                if (!store.save(s.who, SyncConsent(enabled = true, acceptedWarningVersion = SyncConsent.WARNING_VERSION))) {
                    message = "Chưa lưu được quyền đồng bộ."; return
                }
            }
            val discovery = actions.discover(s.who)
            if (!synchronized(this) { current(s) }) return
            val found = (discovery as? Gated.Done)?.value as? ChannelDiscovery.Found
            if (found == null) {
                synchronized(this) { rememberDiscoveryWait(discovery) }
                message = discoveryMessage(discovery); return
            }
            val bound = host.view(s.who)?.state?.checkpoint?.channelId ?: return
            if (bound != 0L && bound != found.chatId) { message = "Kênh xác minh không khớp kênh đã gắn."; return }
            if (bound == 0L) {
                val result = actions.bind(s.who, found.chatId)
                if (result != Gated.Done(BindResult.Bound)) { message = "Chưa gắn được kênh đồng bộ."; return }
            }
            synchronized(this) {
                if (!matches(review, s)) { message = "Danh sách chờ gửi đã đổi. Hãy xem lại trước khi bật."; return }
                success = store.save(s.who, SyncConsent(enabled = true, mayTransmitInitial = true,
                    acceptedWarningVersion = SyncConsent.WARNING_VERSION))
                if (success) {
                    stoppedAccounts.remove(s.who.value); disableRetryAccounts.remove(s.who.value)
                    notBefore = 0; notice = null; message = null
                }
                else message = "Chưa lưu được quyền gửi. Tự đồng bộ vẫn tắt."
            }
        } catch (_: Exception) {
            message = "Không hoàn tất thiết lập. Tự đồng bộ vẫn tắt."
        } finally {
            synchronized(this) {
                running = false
                if (generation == token) notice = message
                if (foreground && success) enqueue(0)
            }
            completed(success, message)
            changed()
        }
    }

    /** Stop first, even if saving the disabled consent later fails. Existing data is never deleted. */
    fun turnOff(): Boolean {
        val who: TelegramUserId
        val epoch: Long
        synchronized(this) {
            who = account() ?: return false
            epoch = authEpoch()
            stoppedAccounts.add(who.value)
            disableRetryAccounts.add(who.value)
            generation++
            scheduled?.cancel(); scheduled = null
            requested = false
        }
        actions.close()
        val saved = synchronized(this) {
            if (account() != who || authEpoch() != epoch) return@synchronized false
            val result = store.save(who, SyncConsent())
            if (result) disableRetryAccounts.remove(who.value)
            notice = if (result) null else "Đã dừng trong phiên này nhưng chưa lưu được trạng thái tắt. Hãy thử tắt lại."
            result
        }
        changed()
        return saved
    }

    /** Logout/account reset invalidates callbacks without changing consent or playlist data. */
    fun invalidate() {
        synchronized(this) {
            generation++
            scheduled?.cancel(); scheduled = null
            requested = false
            retryAccount = null; notBefore = 0; notice = null
            runLog.clear()
        }
        actions.close()
        changed()
    }

    fun context(who: TelegramUserId?): SyncContext = synchronized(this) {
        observeAuth()
        val c = if (buildEnabled && who != null && who == account()) store.load(who) else SyncConsent()
        SyncContext(buildEnabled, if (inhibited) SyncConsent() else c, running,
            who?.takeIf { it == account() }?.let { runLog.last(it.value) })
    }

    private fun run(token: Long) {
        val s: Scope
        synchronized(this) {
            if (token != generation) return
            scheduled = null
            if (running) return
            s = scope(token) ?: return
            if (inhibited) return
            if (!store.load(s.who).automatic) return
            if (retryAccount != s.who.value) { retryAccount = s.who.value; notBefore = 0 }
            if (clock() < notBefore) { enqueue(0); return }
            running = true
            requested = false
        }
        changed()
        var delay = POLL_MS
        var stop = false
        try {
            val before = host.view(s.who)?.state ?: return
            val discovery = actions.discover(s.who)
            if (!synchronized(this) { current(s) }) return
            val found = (discovery as? Gated.Done)?.value as? ChannelDiscovery.Found
            if (found == null || found.chatId != before.checkpoint.channelId) {
                synchronized(this) {
                    if (!current(s)) return
                    rememberDiscoveryWait(discovery)
                    val d = (discovery as? Gated.Done)?.value
                    stop = d !is ChannelDiscovery.Failed && d !is ChannelDiscovery.Incomplete
                    notice = if (found == null) discoveryMessage(discovery) else "Kênh không khớp. Cần kiểm tra lại thiết lập."
                    // Never display an old successful report after verification failed.
                    runLog.record(LastSyncRun(s.who.value, SyncStatus.NO_CHANNEL, opCounterAtRun = before.opCounter))
                }
                return
            }
            if (!synchronized(this) { current(s) && store.load(s.who).automatic }) return
            val mode = if (before.checkpoint.replayPosition != SyncCheckpoint.NO_REPLAY) SyncMode.FULL_REPLAY else SyncMode.INCREMENTAL
            val answer = actions.run(s.who, mode, SyncSendBudget.PRODUCTION_MAX)
            synchronized(this) {
                if (!current(s)) return
                val after = host.view(s.who)?.state ?: return
                runLog.record(LastSyncRun.of(s.who.value, answer, after.opCounter))
                notice = null
                val report = (answer as? Gated.Done)?.value
                if (report != null) {
                    val progress = report.opsAcknowledged > 0 || report.opsApplied > 0 ||
                        report.cursor > before.checkpoint.lastAppliedServerId ||
                        report.replayPosition > before.checkpoint.replayPosition
                    if (report.status in setOf(SyncStatus.OUTBOX_INCOMPLETE, SyncStatus.SEND_LIMIT_REACHED,
                            SyncStatus.CATCH_UP_INCOMPLETE) && progress) delay = CONTINUE_MS
                    if (report.retryAfterMs > 0) {
                        notBefore = clock() + report.retryAfterMs
                        delay = maxOf(POLL_MS, report.retryAfterMs)
                    }
                    stop = report.status in setOf(SyncStatus.RECOVERY_REQUIRED, SyncStatus.SAVE_FAILED, SyncStatus.STALE_SESSION)
                }
            }
        } catch (_: Exception) {
            synchronized(this) {
                if (current(s)) {
                    notice = "Chưa đồng bộ được. Sẽ thử lại khi ứng dụng đang mở."
                    runLog.record(LastSyncRun(s.who.value, SyncStatus.BACKOFF))
                    notBefore = clock() + POLL_MS
                }
            }
        } finally {
            synchronized(this) {
                running = false
                if (current(s) && !inhibited && !stop) enqueue(if (requested) minOf(delay, SAVE_DELAY_MS) else delay)
            }
            changed()
        }
    }

    private fun rememberDiscoveryWait(answer: Gated<ChannelDiscovery>) {
        val failure = (answer as? Gated.Done)?.value as? ChannelDiscovery.Failed ?: return
        notBefore = maxOf(notBefore, clock() + maxOf(POLL_MS, failure.retryAfterSeconds.toLong() * 1_000))
    }

    private fun discoveryMessage(answer: Gated<ChannelDiscovery>): String = when (val d = (answer as? Gated.Done)?.value) {
        is ChannelDiscovery.Unsafe -> ChannelView.Unsafe(d.reason).text()
        is ChannelDiscovery.Ambiguous -> ChannelView.Ambiguous(d.count).text()
        is ChannelDiscovery.Incomplete -> ChannelView.Incomplete(d.reason).text()
        is ChannelDiscovery.Failed -> ChannelView.Failed(d.kind).text()
        ChannelDiscovery.NotFound -> ChannelView.NotFound.text()
        else -> "Chưa xác minh được kênh. Cần đăng nhập và thử lại."
    }
}
