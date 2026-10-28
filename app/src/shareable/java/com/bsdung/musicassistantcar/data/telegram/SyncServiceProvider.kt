package com.bsdung.musicassistantcar.data.telegram

import java.io.File
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * E96 P5-A: the ONE production place where the P3 sync service is composed. It is inert unless the build switch
 * ([TelegramPlaylistFlags.SYNC_ENABLED]) is on: with it off, [actions] answers [DisabledSyncActions] without calling the
 * owner, the playlist host or the consent store, so no TDLib object is even looked at. It creates no thread, no client,
 * no listener, no background job and no receiver: the service binds the existing [SyncOwnerPort] only for the duration of
 * a call that a person started on the Settings screen.
 *
 * One service per process (so the backoff it keeps cannot be skipped by closing and reopening the Settings screen).
 */
internal class SyncServiceProvider(
    private val buildEnabled: Boolean,
    private val owner: () -> SyncOwnerPort,
    private val host: () -> PlaylistSyncHost?,
    private val store: SyncConsentStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val config: SyncConfig = SyncConfig(),
    private val timeouts: TdlibTransportTimeouts = TdlibTransportTimeouts(),
    private val historyLimits: HistoryLimits = HistoryLimits(),
    private val discoveryLimits: DiscoveryLimits = DiscoveryLimits(),
    /** [TelegramPlaylistFlags.SYNC_CREATE_ALLOWED]. False unless a QA build was started with the extra create flag. */
    private val channelCreationUnlocked: Boolean = false,
    private val isActive: () -> Boolean = { true },
) {
    private var cached: SyncActionsPort? = null

    /** Whether a real service exists yet. False for ever when the switch is off. */
    @Synchronized
    fun created(): Boolean = cached != null

    @Synchronized
    fun actions(): SyncActionsPort {
        if (!buildEnabled) return DisabledSyncActions
        cached?.let { return it }
        // A V1-only playlist session cannot be synchronised: stay inert rather than guess.
        val playlist = host() ?: return DisabledSyncActions
        val service = TdlibPlaylistSyncService(
            owner(), playlist, SyncGate(buildEnabled, store), clock, config, timeouts, historyLimits, discoveryLimits,
            channelCreationUnlocked = channelCreationUnlocked,
            isActive = isActive,
        )
        return TdlibSyncActions(service).also { cached = it }
    }
}

/** Puts the P3 sync service behind the Settings screen's port. Closing releases the TDLib binding and stops unfinished work. */
internal class TdlibSyncActions(private val service: TdlibPlaylistSyncService) : SyncActionsPort {
    override fun discover(account: TelegramUserId) = service.discoverChannel(account)
    override fun create(account: TelegramUserId) = service.createChannel(account)
    override fun bind(account: TelegramUserId, chatId: Long) = service.bindChannel(account, chatId)
    override fun run(account: TelegramUserId, mode: SyncMode, sendBudget: Int) = service.runSync(account, mode, sendBudget)

    override fun close() {
        service.cancel()
        service.detach()
    }
}

/** What the last manual sync run of each account reported, in memory only, so every screen reads the same truth. */
internal class SyncRunLog {
    private val runs = HashMap<Long, LastSyncRun>()

    @Synchronized
    fun record(run: LastSyncRun) {
        runs[run.accountId] = run
    }

    @Synchronized
    fun last(accountId: Long): LastSyncRun? = runs[accountId]

    /** Logout or account switch: no report outlives the session it was made in. */
    @Synchronized
    fun clear() = runs.clear()
}

/** Process-wide holders. Reading a property never touches TDLib; only [SyncServiceProvider.actions] with the switch on does. */
internal object SyncRuntime {
    val runLog = SyncRunLog()
    @Volatile private var foreground = false
    private var auto: AutomaticPlaylistSync? = null
    private val listeners = LinkedHashMap<Any, () -> Unit>()

    @Synchronized fun listen(owner: Any, callback: () -> Unit) { listeners[owner] = callback }
    @Synchronized fun unlisten(owner: Any) { listeners.remove(owner) }
    private fun notifyChanged() {
        val callbacks = synchronized(this) { listeners.values.toList() }
        callbacks.forEach { runCatching { it() } }
    }

    fun setForeground(visible: Boolean, root: File) {
        foreground = visible
        automatic(root).setForeground(visible)
    }

    @Synchronized fun automatic(root: File): AutomaticPlaylistSync {
        auto?.let { return it }
        val worker = ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "MeloVault-AutoPlaylist").apply { isDaemon = true }
        }
        worker.removeOnCancelPolicy = true
        val session = TelegramPlaylistRuntime.session(root) as PlaylistSyncHost
        return AutomaticPlaylistSync(
            TelegramPlaylistFlags.SYNC_ENABLED, provider(root).actions(), FileSyncConsentStore(root), session,
            account = { ShareableTdlibOwner.authorizedAccount()?.let { TelegramUserId(it) } },
            authEpoch = { ShareableTdlibOwner.authEpoch() },
            schedule = { delay, task ->
                val future = worker.schedule({ task() }, delay, TimeUnit.MILLISECONDS)
                SyncScheduledTask { future.cancel(false) }
            },
            runLog = runLog, changed = ::notifyChanged,
        ).also { auto = it }
    }

    @Volatile private var provider: SyncServiceProvider? = null

    @Synchronized
    fun provider(noBackupDir: File): SyncServiceProvider = provider ?: SyncServiceProvider(
        buildEnabled = TelegramPlaylistFlags.SYNC_ENABLED,
        owner = { ShareableTdlibOwner },
        host = { TelegramPlaylistRuntime.session(noBackupDir) as? PlaylistSyncHost },
        store = FileSyncConsentStore(noBackupDir),
        channelCreationUnlocked = TelegramPlaylistFlags.SYNC_CREATE_ALLOWED,
        isActive = { foreground },
    ).also { provider = it }
}
