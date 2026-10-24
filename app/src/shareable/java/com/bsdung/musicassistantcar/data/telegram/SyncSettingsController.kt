package com.bsdung.musicassistantcar.data.telegram

/**
 * E96 P4/P5-A: the four calls the Settings > Đồng bộ Playlist screen may make into P3. Production code obtains a port only
 * from [SyncServiceProvider.actions], which answers [DisabledSyncActions] for as long as the build switch is off. Every
 * call blocks: worker thread only. [close] releases whatever a call bound (the TDLib sink) and stops unfinished work.
 */
internal interface SyncActionsPort {
    fun discover(account: TelegramUserId): Gated<ChannelDiscovery>
    fun create(account: TelegramUserId): Gated<CreateOutcome>
    fun bind(account: TelegramUserId, chatId: Long): Gated<BindResult>
    /** [sendBudget] = how many OPERATIONS the run may send. Zero (the default) reads and acknowledges only. There is no "unlimited". */
    fun run(account: TelegramUserId, mode: SyncMode, sendBudget: Int = 0): Gated<SyncReport>
    fun close() = Unit
}

/** The shipped P4 wiring: nothing reaches TDLib, every answer is "the build has sync off". */
internal object DisabledSyncActions : SyncActionsPort {
    override fun discover(account: TelegramUserId): Gated<ChannelDiscovery> = Gated.Denied(SyncDenied.BUILD_OFF)
    override fun create(account: TelegramUserId): Gated<CreateOutcome> = Gated.Denied(SyncDenied.BUILD_OFF)
    override fun bind(account: TelegramUserId, chatId: Long): Gated<BindResult> = Gated.Denied(SyncDenied.BUILD_OFF)
    override fun run(account: TelegramUserId, mode: SyncMode, sendBudget: Int): Gated<SyncReport> = Gated.Denied(SyncDenied.BUILD_OFF)
}

internal object SyncPrivacyText {
    const val TITLE = "Đồng bộ Playlist qua Telegram"

    /** The warning whose acceptance is recorded as [SyncConsent.WARNING_VERSION]. Change the text = bump the version. */
    val WARNING_LINES = listOf(
        "Playlist được lưu trong một kênh Telegram RIÊNG của chính bạn. Kênh này KHÔNG được mã hóa đầu-cuối (end-to-end).",
        "Chỉ có thông tin bài đã lưu (mã nhóm/tin nhắn Telegram và nhật ký thêm/bỏ). Không có âm thanh, không có nhạc trên máy, " +
            "không có mã đăng nhập hay khóa, không có tệp hay đường dẫn tải về.",
        "Dùng cùng tài khoản Telegram trên các máy để thấy cùng danh sách. Dữ liệu nằm trên đám mây Telegram cho tới khi bạn tự xóa kênh hoặc tin nhắn.",
        "Chỉ đồng bộ danh sách. Nhạc không tự tải hay phát sang máy khác.",
    )

    const val CONSENT_ENABLE = "Tôi đã đọc và đồng ý. Bật đồng bộ Playlist"
    const val CONSENT_CREATE = "Cho phép tạo kênh Telegram riêng (khi chưa có)"
    const val CONSENT_TRANSMIT = "Cho phép gửi danh sách lên kênh lần đầu"
    const val GRANT_NOTE = "Mỗi lần chỉ gửi tối đa ${SyncSendBudget.PRODUCTION_MAX} thay đổi, do bạn cho phép từng lần. Không có gửi tự động. " +
        "Nếu một thay đổi đã gửi mà chưa biết có tới nơi hay chưa, ứng dụng KHÔNG gửi lại."
    const val OFF_NOTE = "Tắt đồng bộ chỉ dừng việc gửi/nhận mới. Playlist, hàng đợi và kênh trên Telegram được giữ nguyên."
}

internal enum class ControlState {
    /** The build has cloud sync switched off: shown, never usable. */
    BUILD_OFF,
    /** Needs something else first (consent, a bound channel, a discovery). */
    LOCKED,
    AVAILABLE,
    /** A consent that is currently given. */
    ACTIVE,
}

internal sealed class ChannelView {
    object Unchecked : ChannelView()
    data class Found(val chatId: Long) : ChannelView()
    object NotFound : ChannelView()
    data class Incomplete(val reason: IncompleteReason) : ChannelView()
    data class Ambiguous(val count: Int) : ChannelView()
    data class Unsafe(val reason: UnsafeChannel) : ChannelView()
    data class Failed(val kind: SyncErrorKind) : ChannelView()
    object Created : ChannelView()
    object Bound : ChannelView()
    /** A create request went out and was not confirmed, or two devices created one: a person looks, nothing repeats. */
    object NeedsPerson : ChannelView()

    fun text(): String = when (this) {
        Unchecked -> "Chưa kiểm tra kênh. Bấm \"Tìm kênh\" (chỉ đọc, không tạo gì)."
        is Found -> "Đã tìm thấy kênh riêng của bạn và xác minh an toàn. Bấm \"Dùng kênh này\" để gắn."
        NotFound -> "Đã quét hết và xác nhận chưa có kênh. Có thể tạo kênh nếu bạn cho phép."
        is Incomplete -> "Chưa quét xong nên chưa chắc kênh có tồn tại hay không. Sẽ KHÔNG tạo kênh mới. Thử tìm lại sau."
        is Ambiguous -> "Có ${count} kênh giống nhau. Ứng dụng không tự chọn hay tạo thêm; cần bạn xử lý."
        is Unsafe -> "Có kênh mang dấu hiệu nhưng không an toàn (${unsafeText(reason)}). Ứng dụng không dùng kênh này."
        is Failed -> "Không kiểm tra được kênh (${errorText(kind)}). Thử lại sau."
        Created -> "Đã tạo kênh riêng. Bấm \"Dùng kênh này\" sau khi tìm lại để gắn."
        Bound -> "Đã gắn kênh đồng bộ."
        NeedsPerson -> "Kênh vừa tạo chưa xác minh được hoặc có kênh trùng. Không tạo thêm; cần bạn kiểm tra."
    }

    companion object {
        fun unsafeText(reason: UnsafeChannel): String = when (reason) {
            UnsafeChannel.NOT_A_CHANNEL -> "không phải kênh"
            UnsafeChannel.PUBLIC -> "kênh công khai"
            UnsafeChannel.NOT_OWNER -> "bạn không phải chủ"
            UnsafeChannel.HAS_OTHER_MEMBERS -> "có thành viên khác"
            UnsafeChannel.AUTO_DELETE_ON -> "bật tự xóa tin nhắn"
            UnsafeChannel.LINKED_CHAT -> "có nhóm thảo luận gắn kèm"
            UnsafeChannel.MARKER_MISSING -> "thiếu dấu nhận diện"
        }

        fun errorText(kind: SyncErrorKind): String = when (kind) {
            SyncErrorKind.NETWORK -> "mạng"
            SyncErrorKind.TIMEOUT -> "hết thời gian chờ"
            SyncErrorKind.FLOOD_WAIT -> "Telegram yêu cầu chờ"
            SyncErrorKind.CHANNEL_MISSING -> "kênh không còn"
            SyncErrorKind.UNAUTHORIZED -> "chưa được phép"
            SyncErrorKind.SESSION_REVOKED -> "phiên đã bị thu hồi"
            SyncErrorKind.REJECTED -> "Telegram từ chối yêu cầu"
        }
    }
}

internal data class SyncSettingsScreen(
    val buildOff: Boolean,
    val buildNotice: String?,
    val consent: SyncConsent,
    val enable: ControlState,
    val allowCreate: ControlState,
    val allowTransmit: ControlState,
    val discover: ControlState,
    val createNow: ControlState,
    val bindNow: ControlState,
    val syncNow: ControlState,
    val fullReplay: ControlState,
    val channel: ChannelView,
    val busy: Boolean,
    /** Operations the person has allowed the NEXT run to send (0 = none: runs only read). In memory; gone when the screen closes. */
    val sendGrant: Int = 0,
    val grantSend: ControlState = ControlState.LOCKED,
)

internal sealed class ActionResult {
    object Done : ActionResult()
    data class Refused(val reason: Reason) : ActionResult()
    data class Denied(val reason: SyncDenied) : ActionResult()
    object NotSaved : ActionResult()

    enum class Reason {
        BUILD_OFF, NO_ACCOUNT, WARNING_NOT_ACCEPTED, NOT_ENABLED, NOT_CONFIRMED, NEEDS_DISCOVERY, NO_CHANNEL, BUSY,
        /** A send grant needs consent (c) first. */
        TRANSMIT_NOT_ALLOWED,
        /** A send grant is a whole number from 1 to [SyncSendBudget.PRODUCTION_MAX]. */
        BAD_SEND_BUDGET,
    }
}

/**
 * E96 P4: the state machine behind Settings > Đồng bộ Playlist. Pure JVM; it holds no Android type and starts no thread
 * (the screen calls it from a worker). The rules, each enforced here and tested:
 *  - with the build switch off NOTHING is persisted and nothing reaches [SyncActionsPort];
 *  - the three consents are separate; none implies another; turning sync off clears all three and deletes nothing;
 *  - a channel is created only after a discovery that PROVED absence ([ChannelView.NotFound]), with consent (b), and
 *    only for a call that carries a fresh `confirmed = true` - never on opening the screen;
 *  - "Đồng bộ ngay" needs consent (a) and a bound channel and cannot skip the service's own backoff and gates.
 */
internal class SyncSettingsController(
    private val store: SyncConsentStore,
    private val buildEnabled: Boolean,
    private val actions: SyncActionsPort,
    private val account: () -> TelegramUserId?,
    private val facts: () -> PlaylistStatusFacts?,
    /** Where the last manual run of each account is kept, shared with the screens that show the Playlist status line. */
    private val runLog: SyncRunLog = SyncRunLog(),
    /** [TelegramPlaylistFlags.SYNC_CREATE_ALLOWED]: false = the screen can find and bind an existing channel, never create one. */
    private val channelCreationUnlocked: Boolean = false,
    /** The live sign-in epoch of the TDLib owner: a new login (even of the same account) takes the send grant away. */
    private val epoch: () -> Long = { 0L },
) {
    private var viewAccount: Long = 0L
    private var viewEpoch: Long = epoch()
    private var channel: ChannelView = ChannelView.Unchecked
    private val lastRun: LastSyncRun? get() = runLog.last(viewAccount)
    private var running = false
    /**
     * How many OPERATIONS the next run may send; 0 = none (runs only read and acknowledge). Memory only, for the account
     * on screen: it is never written anywhere, it is zeroed by turning sync off, withdrawing consent (c), any account
     * change or logout, and it is gone with the screen and with the process.
     */
    private var sendGrant = 0

    /** Screen closed: release the TDLib binding of the sync and stop unfinished work. Deletes and sends nothing. */
    fun close() = actions.close()

    /** Forget everything learned about another account's channel or runs. Called before each use. */
    @Synchronized
    private fun scope(current: TelegramUserId?) {
        val id = current?.value ?: 0L
        val liveEpoch = epoch()
        if (liveEpoch != viewEpoch) {
            viewEpoch = liveEpoch
            sendGrant = 0
        }
        if (id != viewAccount) {
            viewAccount = id
            channel = ChannelView.Unchecked
            running = false
            sendGrant = 0
        }
    }

    @Synchronized
    fun consent(): SyncConsent {
        val current = account()
        scope(current)
        return if (current == null) SyncConsent() else store.load(current)
    }

    @Synchronized
    fun syncContext(): SyncContext {
        val current = account()
        scope(current)
        val c = if (current == null) SyncConsent() else store.load(current)
        return SyncContext(buildEnabled, c, running, lastRun)
    }

    @Synchronized
    fun screen(): SyncSettingsScreen {
        val current = account()
        scope(current)
        val c = if (current == null) SyncConsent() else store.load(current)
        val opted = buildEnabled && c.enabled && c.warningAccepted && current != null
        val bound = facts()?.takeIf { it.accountId == viewAccount }?.channelBound == true
        fun gated(whenOpen: Boolean, active: Boolean = false): ControlState = when {
            !buildEnabled -> ControlState.BUILD_OFF
            active -> ControlState.ACTIVE
            whenOpen -> ControlState.AVAILABLE
            else -> ControlState.LOCKED
        }
        return SyncSettingsScreen(
            buildOff = !buildEnabled,
            buildNotice = if (buildEnabled) null else PlaylistSyncStatusPolicy.BUILD_OFF_NOTICE,
            consent = c,
            enable = gated(current != null, active = opted),
            allowCreate = gated(opted, active = opted && c.mayCreateChannel),
            allowTransmit = gated(opted, active = opted && c.mayTransmitInitial),
            discover = gated(opted && !running),
            // Locked for good unless the build unlocks it: a channel already exists (REWORK-01) and no other is ever made.
            createNow = if (!channelCreationUnlocked && buildEnabled) ControlState.LOCKED
            else gated(opted && c.mayCreateChannel && channel == ChannelView.NotFound && !bound && !running),
            bindNow = gated(opted && channel is ChannelView.Found && !bound && !running),
            syncNow = gated(opted && bound && !running),
            fullReplay = gated(opted && bound && !running),
            channel = if (bound) ChannelView.Bound else channel,
            busy = running,
            sendGrant = if (opted && c.mayTransmitInitial) sendGrant else 0,
            grantSend = gated(opted && c.mayTransmitInitial && bound && !running, active = opted && c.mayTransmitInitial && sendGrant > 0),
        )
    }

    // ---- consent -----------------------------------------------------------------------------------

    private fun guarded(block: (TelegramUserId) -> ActionResult): ActionResult {
        if (!buildEnabled) return ActionResult.Refused(ActionResult.Reason.BUILD_OFF)
        val current = account()
        synchronized(this) { scope(current) }
        current ?: return ActionResult.Refused(ActionResult.Reason.NO_ACCOUNT)
        return block(current)
    }

    private fun saved(ok: Boolean): ActionResult = if (ok) ActionResult.Done else ActionResult.NotSaved

    /** (a). [warningShownAndAccepted] must be true: the screen shows the warning and the person ticks it in the same action. */
    fun enable(warningShownAndAccepted: Boolean): ActionResult = guarded { who ->
        if (!warningShownAndAccepted) ActionResult.Refused(ActionResult.Reason.WARNING_NOT_ACCEPTED)
        else saved(store.save(who, store.load(who).copy(enabled = true, acceptedWarningVersion = SyncConsent.WARNING_VERSION)))
    }

    private fun optedIn(c: SyncConsent) = c.enabled && c.warningAccepted

    /** (b). Never sets (c). */
    fun setAllowCreate(allowed: Boolean): ActionResult = guarded { who ->
        val c = store.load(who)
        when {
            !allowed -> saved(store.save(who, c.copy(mayCreateChannel = false)))
            !optedIn(c) -> ActionResult.Refused(ActionResult.Reason.NOT_ENABLED)
            else -> saved(store.save(who, c.copy(mayCreateChannel = true)))
        }
    }

    /** (c). Never sets (b). */
    fun setAllowTransmit(allowed: Boolean): ActionResult = guarded { who ->
        val c = store.load(who)
        when {
            !allowed -> {
                synchronized(this) { sendGrant = 0 }
                saved(store.save(who, c.copy(mayTransmitInitial = false)))
            }
            !optedIn(c) -> ActionResult.Refused(ActionResult.Reason.NOT_ENABLED)
            else -> saved(store.save(who, c.copy(mayTransmitInitial = true)))
        }
    }

    /** Turns everything off. Deletes no playlist, outbox or cloud message. */
    fun turnOff(): ActionResult = guarded { who ->
        // The grant goes first and unconditionally: even if the consent file cannot be written, nothing more is sent.
        synchronized(this) { sendGrant = 0 }
        val ok = store.save(who, SyncConsent())
        if (ok) synchronized(this) { channel = ChannelView.Unchecked }
        saved(ok)
    }

    /** The send grant, for the screen. */
    @Synchronized
    fun sendGrant(): Int = sendGrant

    /**
     * Allows the NEXT run(s) to send up to [operations] (1..[SyncSendBudget.PRODUCTION_MAX]) outbox OPERATIONS in total.
     * [confirmed] must be a fresh tap after the person saw how many changes wait. Needs consent (a) and (c) and a bound
     * channel. It replaces any earlier grant (it never adds up), is charged at dispatch and never carries over.
     */
    fun grantSend(operations: Int, confirmed: Boolean): ActionResult = guarded { who ->
        optedIn(who)?.let { return@guarded it }
        if (!confirmed) return@guarded ActionResult.Refused(ActionResult.Reason.NOT_CONFIRMED)
        if (operations !in 1..SyncSendBudget.PRODUCTION_MAX) return@guarded ActionResult.Refused(ActionResult.Reason.BAD_SEND_BUDGET)
        if (!store.load(who).mayTransmitInitial) return@guarded ActionResult.Refused(ActionResult.Reason.TRANSMIT_NOT_ALLOWED)
        val snapshot = facts()?.takeIf { it.accountId == who.value }
        if (snapshot?.channelBound != true) return@guarded ActionResult.Refused(ActionResult.Reason.NO_CHANNEL)
        synchronized(this) { sendGrant = operations }
        ActionResult.Done
    }

    /** Takes the grant back. */
    fun revokeSendGrant(): ActionResult = guarded { _ ->
        synchronized(this) { sendGrant = 0 }
        ActionResult.Done
    }

    // ---- channel ---------------------------------------------------------------------------------------

    private fun begin(): Boolean = synchronized(this) {
        if (running) false else { running = true; true }
    }

    private fun end() = synchronized(this) { running = false }

    private fun optedIn(who: TelegramUserId): ActionResult? {
        val c = store.load(who)
        return if (c.enabled && c.warningAccepted) null else ActionResult.Refused(ActionResult.Reason.NOT_ENABLED)
    }

    /** Read-only look at the account's chats. Creates nothing. */
    fun discover(): ActionResult = guarded { who ->
        optedIn(who)?.let { return@guarded it }
        if (!begin()) return@guarded ActionResult.Refused(ActionResult.Reason.BUSY)
        try {
            when (val answer = actions.discover(who)) {
                is Gated.Denied -> ActionResult.Denied(answer.reason)
                is Gated.Done -> {
                    synchronized(this) { if (viewAccount == who.value) channel = viewOf(answer.value) }
                    ActionResult.Done
                }
            }
        } finally {
            end()
        }
    }

    /** Creates the private channel. [confirmed] must be a fresh explicit tap; the last discovery must have PROVEN absence. */
    fun createChannel(confirmed: Boolean): ActionResult = guarded { who ->
        optedIn(who)?.let { return@guarded it }
        // Before anything else: this build never creates a channel (the person's one real channel already exists).
        if (!channelCreationUnlocked) return@guarded ActionResult.Denied(SyncDenied.CREATE_LOCKED)
        if (!confirmed) return@guarded ActionResult.Refused(ActionResult.Reason.NOT_CONFIRMED)
        if (!store.load(who).mayCreateChannel) return@guarded ActionResult.Denied(SyncDenied.CREATE_NOT_ALLOWED)
        if (synchronized(this) { channel } != ChannelView.NotFound) return@guarded ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY)
        if (!begin()) return@guarded ActionResult.Refused(ActionResult.Reason.BUSY)
        try {
            // Whatever happens next, this confirmation is spent: another create needs a new discovery and a new tap.
            synchronized(this) { channel = ChannelView.Unchecked }
            when (val answer = actions.create(who)) {
                is Gated.Denied -> ActionResult.Denied(answer.reason)
                is Gated.Done -> {
                    val view = when (val outcome = answer.value) {
                        is CreateOutcome.Created -> ChannelView.Created
                        is CreateOutcome.Exists -> viewOf(outcome.discovery)
                        is CreateOutcome.NeedsPerson -> ChannelView.NeedsPerson
                        CreateOutcome.Unknown -> ChannelView.NeedsPerson
                        is CreateOutcome.DiscoveryIncomplete -> ChannelView.Incomplete(outcome.discovery.reason)
                        is CreateOutcome.AlreadyBound -> ChannelView.Bound
                        is CreateOutcome.Failed -> ChannelView.Failed(outcome.kind)
                    }
                    synchronized(this) { if (viewAccount == who.value) channel = view }
                    ActionResult.Done
                }
            }
        } finally {
            end()
        }
    }

    /** Binds the channel the last discovery found and verified. [confirmed] = a fresh explicit tap. */
    fun bindFoundChannel(confirmed: Boolean): ActionResult = guarded { who ->
        optedIn(who)?.let { return@guarded it }
        if (!confirmed) return@guarded ActionResult.Refused(ActionResult.Reason.NOT_CONFIRMED)
        val found = synchronized(this) { channel } as? ChannelView.Found
            ?: return@guarded ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY)
        if (!begin()) return@guarded ActionResult.Refused(ActionResult.Reason.BUSY)
        try {
            when (val answer = actions.bind(who, found.chatId)) {
                is Gated.Denied -> ActionResult.Denied(answer.reason)
                is Gated.Done -> {
                    synchronized(this) {
                        if (viewAccount == who.value) channel = if (answer.value == BindResult.Bound) ChannelView.Bound else ChannelView.Unchecked
                    }
                    if (answer.value == BindResult.Bound) ActionResult.Done else ActionResult.NotSaved
                }
            }
        } finally {
            end()
        }
    }

    private fun viewOf(d: ChannelDiscovery): ChannelView = when (d) {
        ChannelDiscovery.NotFound -> ChannelView.NotFound
        is ChannelDiscovery.Found -> ChannelView.Found(d.chatId)
        is ChannelDiscovery.Ambiguous -> ChannelView.Ambiguous(d.count)
        is ChannelDiscovery.Unsafe -> ChannelView.Unsafe(d.reason)
        is ChannelDiscovery.Incomplete -> ChannelView.Incomplete(d.reason)
        is ChannelDiscovery.Failed -> ChannelView.Failed(d.kind)
    }

    // ---- manual sync -------------------------------------------------------------------------------------

    /** "Đồng bộ ngay": one run, started by this tap. The service applies its own gates and backoff; nothing here bypasses them. */
    fun syncNow(): ActionResult = run(SyncMode.INCREMENTAL, confirmed = true)

    /** Reads the whole history again. Explicit, behind its own confirmation; it deletes nothing. */
    fun fullReplay(confirmed: Boolean): ActionResult = run(SyncMode.FULL_REPLAY, confirmed)

    private fun run(mode: SyncMode, confirmed: Boolean): ActionResult = guarded { who ->
        optedIn(who)?.let { return@guarded it }
        if (!confirmed) return@guarded ActionResult.Refused(ActionResult.Reason.NOT_CONFIRMED)
        val snapshot = facts()?.takeIf { it.accountId == who.value }
        if (snapshot?.channelBound != true) return@guarded ActionResult.Refused(ActionResult.Reason.NO_CHANNEL)
        if (!begin()) return@guarded ActionResult.Refused(ActionResult.Reason.BUSY)
        try {
            // The whole grant is taken out BEFORE the call and only what was not dispatched is put back afterwards: if the
            // call dies half way nothing comes back, so an unexpected failure can never leave a grant to be used again.
            val startEpoch = epoch()
            val granted = synchronized(this) { sendGrant.also { sendGrant = 0 } }
            val answer = actions.run(who, mode, granted)
            // What is left is put back only if the very same sign-in epoch is still live: A -> B -> A during the call (a new
            // epoch of the same account) must not bring a grant back to life.
            if (viewAccount == who.value && epoch() == startEpoch && store.load(who).mayTransmitInitial) {
                val spent = when (answer) {
                    is Gated.Denied -> 0 // refused at the gate: nothing was handed to Telegram
                    is Gated.Done -> answer.value.opsDispatched
                }
                synchronized(this) { if (viewAccount == who.value) sendGrant = (granted - spent).coerceAtLeast(0) }
            }
            val opCounterNow = facts()?.takeIf { it.accountId == who.value }?.opCounter ?: snapshot.opCounter
            synchronized(this) {
                if (viewAccount == who.value) runLog.record(LastSyncRun.of(who.value, answer, opCounterNow))
            }
            if (answer is Gated.Denied) ActionResult.Denied(answer.reason) else ActionResult.Done
        } finally {
            end()
        }
    }
}
