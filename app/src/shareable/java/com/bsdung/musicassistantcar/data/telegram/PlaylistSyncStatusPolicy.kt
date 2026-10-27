package com.bsdung.musicassistantcar.data.telegram

/**
 * E96 P4: the numbers the status line needs, read from the PERSISTED Playlist V2 state on the playlist thread
 * (never on the UI thread). Nothing here is a guess: counts come from the outbox/legacy list, the channel from the
 * checkpoint. [opCounter] changes with every new local change, so a sync report older than it proves nothing.
 */
internal data class PlaylistStatusFacts(
    val accountId: Long,
    val savedCount: Int,
    /** Operations not yet acknowledged by the channel (PENDING + RETRY_REQUIRED). */
    val pendingCount: Int,
    val retryRequiredCount: Int,
    /** Saved keys whose Telegram chat/message could not be named: they live on this device only. */
    val legacyCount: Int,
    val channelBound: Boolean,
    val opCounter: Long,
    /** The V1 copy for older APKs holds only the newest 300; the V2 list shown here is complete. */
    val projectionTruncated: Boolean,
    val projectionStale: Boolean,
    val importWarning: PlaylistWarning?,
    val unreadable: PlaylistUnreadableReason?,
) {
    companion object {
        fun of(state: PlaylistState, importWarning: PlaylistWarning?): PlaylistStatusFacts = PlaylistStatusFacts(
            accountId = state.accountId,
            savedCount = state.presentSongKeys(state.accountId).size,
            pendingCount = state.outbox.count { it.status != OutboxStatus.ACKNOWLEDGED },
            retryRequiredCount = state.outbox.count { it.status == OutboxStatus.RETRY_REQUIRED },
            legacyCount = state.legacy.size,
            channelBound = state.checkpoint.channelId != 0L,
            opCounter = state.opCounter,
            projectionTruncated = state.projection.truncated,
            projectionStale = state.projection.status == ProjectionStatus.STALE,
            importWarning = importWarning,
            unreadable = null,
        )

        fun unreadable(accountId: Long, reason: PlaylistUnreadableReason): PlaylistStatusFacts = PlaylistStatusFacts(
            accountId, 0, 0, 0, 0, false, 0L, false, false, null, reason,
        )
    }
}

/** What happened in the last manual sync run of THIS app session; null = none has run (nothing ever runs by itself). */
internal data class LastSyncRun(
    val accountId: Long,
    val status: SyncStatus? = null,
    val recovery: RecoveryReason? = null,
    val denied: SyncDenied? = null,
    val retryAfterMs: Long = 0L,
    val pendingLeft: Int = 0,
    /** [PlaylistStatusFacts.opCounter] when the run ended. */
    val opCounterAtRun: Long = 0L,
    /** Sanitised diagnostic of where the run stopped (an enum name, never data). */
    val stage: SyncStage = SyncStage.NONE,
) {
    companion object {
        fun of(accountId: Long, result: Gated<SyncReport>, opCounterNow: Long): LastSyncRun = when (result) {
            is Gated.Denied -> LastSyncRun(accountId, denied = result.reason, opCounterAtRun = opCounterNow)
            is Gated.Done -> LastSyncRun(
                accountId, result.value.status, result.value.recovery, null,
                result.value.retryAfterMs, result.value.pendingLeft, opCounterNow, result.value.stage,
            )
        }
    }
}

internal data class SyncContext(
    /** [TelegramPlaylistFlags.SYNC_ENABLED]. */
    val buildEnabled: Boolean,
    val consent: SyncConsent,
    val running: Boolean = false,
    val lastRun: LastSyncRun? = null,
) {
    /** The person turned sync on and saw the warning, and the build allows it. */
    val optedIn: Boolean get() = buildEnabled && consent.enabled && consent.warningAccepted
}

/**
 * E96 P4: the one honest answer to "is my Playlist saved, and where?". It is derived from the persisted Playlist V2
 * state, the consent and the LAST sync report - there is no stored "synced" Boolean. "Đã đồng bộ" needs ALL of:
 * opted in, a channel bound, a last report of [SyncStatus.OK] for this account, nothing waiting to be sent and no
 * local change newer than that report. A provisional answer of a send call never reaches this class.
 */
internal object PlaylistSyncStatusPolicy {

    enum class Kind {
        LOADING, UNREADABLE, IMPORT_DEFERRED,
        LOCAL_SYNC_OFF, LOCAL_PENDING, NEVER_SYNCED,
        SYNCING, CATCHING_UP, SENDING_OUTBOX, SYNCED,
        /** Operations wait and this run's send grant (a number of operations) is spent or was never given. */
        SEND_LIMIT,
        /** Something was sent and nobody knows if it arrived: kept, never sent again by itself. */
        DELIVERY_UNCONFIRMED,
        OFFLINE_BACKOFF, NO_CHANNEL, NEEDS_TRANSMIT_CONSENT, NEEDS_LOGIN, BUSY, ATTENTION,
        RECOVERY_CHANNEL_MISSING, RECOVERY_ACCESS, RECOVERY_HISTORY, RECOVERY_WRONG_ACCOUNT, RECOVERY_FUTURE_PROTOCOL,
        RECOVERY_REJECTED, CREATE_LOCKED,
        /** Per song only. */
        NOT_SAVED, LOCAL_SONG, SAVED_DEVICE_ONLY,
    }

    enum class Severity { INFO, OK, WARN, ERROR }

    data class Line(
        val kind: Kind,
        val text: String,
        val detail: String,
        val severity: Severity,
        val notes: List<String> = emptyList(),
    ) {
        /** The text for "tap to see more": the explanation and every extra fact, one per line. */
        fun fullDetail(): String = (listOf(detail) + notes).filter { it.isNotBlank() }.joinToString("\n")
    }

    const val BUILD_OFF_NOTICE = "Đồng bộ đám mây chưa được kích hoạt trong bản thử nghiệm"
    const val METADATA_ONLY = "Chỉ đồng bộ danh sách bài đã lưu (thông tin bài hát), không tải hay phát nhạc sang máy khác."

    fun evaluate(currentAccount: Long, facts: PlaylistStatusFacts?, sync: SyncContext): Line {
        if (currentAccount <= 0L) {
            return Line(Kind.NEEDS_LOGIN, "Cần đăng nhập Telegram",
                "Hãy đăng nhập Telegram trước khi đồng bộ Playlist.", Severity.WARN)
        }
        // Another account's numbers are never shown, not even a count.
        if (facts == null || facts.accountId != currentAccount) {
            return Line(Kind.LOADING, "Đang đọc Playlist…", "", Severity.INFO)
        }
        facts.unreadable?.let { return unreadable(it) }
        val run = sync.lastRun?.takeIf { it.accountId == facts.accountId }
        val notes = notesOf(facts) + diagnosticNote(run)
        facts.importWarning?.let { warning ->
            return Line(
                Kind.IMPORT_DEFERRED, "Đã lưu trên máy · Chưa nhập hết thay đổi từ bản cũ",
                when (warning) {
                    PlaylistWarning.IMPORT_DEFERRED_OUTBOX_FULL ->
                        "Bản ứng dụng cũ đã thêm bài nhưng hàng đợi thay đổi đang đầy. Không bài nào bị mất; ứng dụng thử lại ở lần mở sau."
                    PlaylistWarning.IMPORT_NOT_SAVED ->
                        "Chưa ghi được các thay đổi từ bản ứng dụng cũ. Không bài nào bị mất; hãy mở lại ứng dụng để thử lại."
                },
                Severity.WARN, notes,
            )
        }
        if (!sync.optedIn) {
            val why = when {
                !sync.buildEnabled -> BUILD_OFF_NOTICE
                !sync.consent.enabled -> "Bạn chưa bật đồng bộ trong Cài đặt > Đồng bộ Playlist."
                else -> "Cần đọc và đồng ý cảnh báo trong Cài đặt > Đồng bộ Playlist."
            }
            return Line(Kind.LOCAL_SYNC_OFF, "Đã lưu trên máy · Chưa bật đồng bộ", "$why Playlist vẫn dùng bình thường trên máy này.", Severity.INFO, notes)
        }
        if (sync.running) {
            return when (run?.status) {
                SyncStatus.CATCH_UP_INCOMPLETE -> catchingUp(notes)
                SyncStatus.OUTBOX_INCOMPLETE -> sending(facts.pendingCount, notes)
                else -> Line(Kind.SYNCING, "Đang đồng bộ…", METADATA_ONLY, Severity.INFO, notes)
            }
        }
        if (!facts.channelBound) {
            val consentHint = if (sync.consent.mayCreateChannel) "" else " Cần bạn cho phép tạo kênh riêng."
            return Line(Kind.NO_CHANNEL, "Đã lưu trên máy · Chưa có kênh đồng bộ", "Chưa có kênh Telegram riêng để đồng bộ.$consentHint", Severity.WARN, notes)
        }
        run?.denied?.let { return denied(it, notes) }
        if (facts.pendingCount > 0 && !sync.consent.mayTransmitInitial) {
            return Line(
                Kind.NEEDS_TRANSMIT_CONSENT, "Đã lưu trên máy · Chưa cho phép gửi",
                "Có ${facts.pendingCount} thay đổi chờ gửi. Cần bạn cho phép gửi dữ liệu lần đầu trong Cài đặt.", Severity.WARN, notes,
            )
        }
        if (run == null) {
            return if (facts.pendingCount > 0) pending(facts, notes) else
                Line(Kind.NEVER_SYNCED, "Đã lưu trên máy · Chưa kiểm tra đồng bộ trong phiên này", "Phiên này chưa chạy lần đồng bộ nào nên chưa biết Playlist có khớp kênh hay không (lần trước có thể đã đồng bộ). Bấm \"Đồng bộ ngay\" trong Cài đặt để kiểm tra.", Severity.INFO, notes)
        }
        val attention = Line(Kind.ATTENTION, "Chưa đồng bộ được · Thử lại", "Lần đồng bộ vừa rồi không hoàn tất. Playlist trên máy vẫn nguyên vẹn.", Severity.WARN, notes)
        return when (run.status ?: return attention) {
            SyncStatus.OK ->
                if (facts.pendingCount == 0 && facts.retryRequiredCount == 0 && run.pendingLeft == 0 && run.opCounterAtRun == facts.opCounter) {
                    Line(Kind.SYNCED, "Đã đồng bộ", "Danh sách khớp với kênh Telegram riêng của bạn. $METADATA_ONLY", Severity.OK, notes)
                } else pending(facts, notes)
            SyncStatus.BACKOFF, SyncStatus.BACKOFF_ACTIVE -> {
                val wait = if (run.retryAfterMs > 0L) " Khoảng ${waitText(run.retryAfterMs)}." else ""
                Line(Kind.OFFLINE_BACKOFF, "Đã lưu trên máy · Thử lại sau", "Mạng hoặc Telegram đang bận.$wait Playlist trên máy không bị ảnh hưởng.", Severity.WARN, notes)
            }
            SyncStatus.CATCH_UP_INCOMPLETE -> catchingUp(notes)
            SyncStatus.OUTBOX_INCOMPLETE -> sending(maxOf(run.pendingLeft, facts.pendingCount), notes)
            SyncStatus.SEND_LIMIT_REACHED -> Line(
                Kind.SEND_LIMIT, "Đã lưu trên máy · Chưa gửi hết",
                "Còn ${maxOf(run.pendingLeft, facts.pendingCount)} thay đổi chờ gửi. Lượt gửi bạn cho phép đã dùng hết hoặc chưa được cấp; " +
                    "ứng dụng không tự gửi thêm. Cho phép lượt mới trong Cài đặt nếu muốn gửi tiếp.", Severity.WARN, notes,
            )
            SyncStatus.DELIVERY_UNCONFIRMED -> Line(
                Kind.DELIVERY_UNCONFIRMED, "Đã lưu trên máy · Chưa xác nhận đã tới kênh",
                "Một số thay đổi đã được gửi nhưng chưa biết có tới kênh Telegram hay chưa. Ứng dụng giữ nguyên chúng " +
                    "và KHÔNG gửi lại; chỉ khi đọc thấy chúng trong kênh thì mới tính là đã đồng bộ. Playlist trên máy không bị ảnh hưởng.",
                Severity.WARN, notes,
            )
            SyncStatus.NO_CHANNEL ->
                Line(Kind.NO_CHANNEL, "Đã lưu trên máy · Chưa có kênh đồng bộ", "Chưa có kênh Telegram riêng để đồng bộ.", Severity.WARN, notes)
            SyncStatus.RECOVERY_REQUIRED -> recovery(run.recovery, notes)
            SyncStatus.NOT_OPEN, SyncStatus.STALE_SESSION, SyncStatus.SAVE_FAILED -> attention
        }
    }

    /** Library screens: on the Playlist tab the sync line is the Playlist state; everywhere else it stays the library line. */
    fun libraryLine(playlistState: Line?, libraryLine: String): String =
        if (playlistState != null) "☆ " + playlistState.text else libraryLine

    /** The line for the song on screen: its own truth first, the overall state only for a song that is really in the Playlist. */
    fun songLine(availability: MeloSavePolicy.Availability, saved: Boolean, overall: Line): Line = when {
        availability == MeloSavePolicy.Availability.LOCAL_ONLY ->
            Line(Kind.LOCAL_SONG, "Nhạc trên máy · không đồng bộ", MeloSavePolicy.HINT_LOCAL_ONLY, Severity.INFO)
        !saved -> Line(Kind.NOT_SAVED, "Chưa lưu", "Bài này chưa nằm trong Playlist.", Severity.INFO)
        availability == MeloSavePolicy.Availability.LEGACY_ONLY ->
            Line(Kind.SAVED_DEVICE_ONLY, "Đã lưu trên máy · chưa rõ nguồn Telegram", MeloSavePolicy.HINT_LEGACY, Severity.WARN)
        else -> overall
    }

    private fun pending(facts: PlaylistStatusFacts, notes: List<String>) = Line(
        Kind.LOCAL_PENDING, "Đã lưu trên máy · Chờ đồng bộ",
        (if (facts.pendingCount > 0) "Có ${facts.pendingCount} thay đổi chờ gửi lên kênh Telegram riêng."
        else "Playlist có thay đổi mới hơn lần đồng bộ gần nhất.") + " $METADATA_ONLY", Severity.INFO, notes,
    )

    private fun catchingUp(notes: List<String>) = Line(
        Kind.CATCHING_UP, "Đang lấy lịch sử · chưa đầy đủ",
        "Đã đọc một phần lịch sử của kênh; Playlist chưa chắc đã mới nhất. Bấm đồng bộ để đọc tiếp.", Severity.INFO, notes,
    )

    private fun sending(left: Int, notes: List<String>) = Line(
        Kind.SENDING_OUTBOX, "Đang gửi thay đổi · còn $left",
        "Đã đọc xong lịch sử nhưng còn $left thay đổi chưa gửi hết. Bấm đồng bộ để gửi tiếp.", Severity.INFO, notes,
    )

    private fun unreadable(reason: PlaylistUnreadableReason) = Line(
        Kind.UNREADABLE, "Không đọc được Playlist trên máy",
        when (reason) {
            PlaylistUnreadableReason.SCHEMA -> "Playlist do bản ứng dụng mới hơn tạo. Bản này chỉ đọc, không ghi đè."
            PlaylistUnreadableReason.ACCOUNT -> "Tệp Playlist thuộc tài khoản Telegram khác; không ghi đè."
            else -> "Để không mất dữ liệu, ứng dụng không ghi đè lên tệp. Hãy giữ nguyên ứng dụng và báo người hỗ trợ."
        },
        Severity.ERROR,
    )

    private fun denied(reason: SyncDenied, notes: List<String>): Line = when (reason) {
        SyncDenied.BUILD_OFF -> Line(Kind.LOCAL_SYNC_OFF, "Đã lưu trên máy · Chưa bật đồng bộ", BUILD_OFF_NOTICE, Severity.INFO, notes)
        SyncDenied.NOT_ENABLED, SyncDenied.WARNING_NOT_ACCEPTED ->
            Line(Kind.LOCAL_SYNC_OFF, "Đã lưu trên máy · Chưa bật đồng bộ", "Cần bật đồng bộ và đồng ý cảnh báo trong Cài đặt.", Severity.INFO, notes)
        SyncDenied.CREATE_NOT_ALLOWED -> Line(Kind.NO_CHANNEL, "Đã lưu trên máy · Chưa có kênh đồng bộ", "Cần bạn cho phép tạo kênh riêng.", Severity.WARN, notes)
        SyncDenied.CREATE_LOCKED ->
            Line(Kind.CREATE_LOCKED, "Đã lưu trên máy · Không tạo kênh mới", "Bản này không tạo kênh Telegram mới; chỉ tìm và gắn kênh đã có.", Severity.INFO, notes)
        SyncDenied.TRANSMIT_NOT_ALLOWED ->
            Line(Kind.NEEDS_TRANSMIT_CONSENT, "Đã lưu trên máy · Chưa cho phép gửi", "Cần bạn cho phép gửi dữ liệu lần đầu trong Cài đặt.", Severity.WARN, notes)
        SyncDenied.NOT_AUTHORIZED -> Line(Kind.NEEDS_LOGIN, "Cần đăng nhập Telegram để đồng bộ", "Playlist trên máy vẫn dùng bình thường.", Severity.WARN, notes)
        SyncDenied.NOT_OPEN, SyncDenied.SINK_BUSY, SyncDenied.BUSY ->
            Line(Kind.BUSY, "Đang bận · Thử lại sau", "Một tác vụ khác đang dùng Telegram. Thử lại sau giây lát.", Severity.INFO, notes)
    }

    private fun recovery(reason: RecoveryReason?, notes: List<String>): Line = when (reason) {
        RecoveryReason.CHANNEL_MISSING ->
            Line(Kind.RECOVERY_CHANNEL_MISSING, "Không thấy kênh đồng bộ", "Kênh Telegram đã bị xóa hoặc không còn truy cập được. Ứng dụng không tự tạo kênh mới; Playlist trên máy vẫn nguyên.", Severity.ERROR, notes)
        RecoveryReason.UNAUTHORIZED, RecoveryReason.SESSION_REVOKED ->
            Line(Kind.RECOVERY_ACCESS, "Mất quyền truy cập Telegram", "Phiên đăng nhập không còn hiệu lực. Đăng nhập lại; Playlist trên máy vẫn nguyên.", Severity.ERROR, notes)
        RecoveryReason.WRONG_ACCOUNT ->
            Line(Kind.RECOVERY_WRONG_ACCOUNT, "Kênh đồng bộ thuộc tài khoản khác", "Ứng dụng không dùng kênh này.", Severity.ERROR, notes)
        RecoveryReason.FUTURE_PROTOCOL ->
            Line(Kind.RECOVERY_FUTURE_PROTOCOL, "Cần cập nhật ứng dụng để đồng bộ", "Kênh dùng định dạng mới hơn bản này hiểu. Không xóa gì.", Severity.ERROR, notes)
        RecoveryReason.REJECTED_BY_TELEGRAM ->
            Line(Kind.RECOVERY_REJECTED, "Telegram từ chối yêu cầu đồng bộ", "Telegram trả lời từ chối nên thử lại cũng không đổi kết quả. Ứng dụng dừng lại, không xóa gì; Playlist trên máy vẫn nguyên.", Severity.ERROR, notes)
        RecoveryReason.CORRUPT_MESSAGE, RecoveryReason.OP_TOO_LARGE, RecoveryReason.TRANSPORT_CONTRACT, RecoveryReason.DEPENDENCY_BLOCKED, null ->
            Line(Kind.RECOVERY_HISTORY, "Lịch sử đồng bộ cần kiểm tra", "Có dữ liệu trong kênh không đọc được hoặc không khớp. Ứng dụng dừng lại, không xóa gì.", Severity.ERROR, notes)
    }

    private fun notesOf(facts: PlaylistStatusFacts): List<String> = buildList {
        if (facts.legacyCount > 0) add("${facts.legacyCount} bài chưa xác định nguồn Telegram: chỉ có trên máy này, không đồng bộ.")
        if (facts.projectionTruncated) add("Bản sao cho ứng dụng cũ chỉ giữ ${TelegramPlaylistPolicy.MAX_IDS} bài mới nhất; danh sách đầy đủ vẫn nằm trong ${facts.savedCount} bài đã lưu.")
        if (facts.projectionStale) add("Bản sao cho ứng dụng cũ chưa cập nhật kịp; danh sách chính vẫn đúng.")
        if (facts.retryRequiredCount > 0) {
            add("${facts.retryRequiredCount} thay đổi đã gửi nhưng chưa rõ đã tới kênh chưa. Ứng dụng giữ nguyên, không tự gửi lại.")
        }
    }

    /** "Mã chẩn đoán: SEND_ACK_TIMEOUT": a closed list of names, never an id, a text or a song. */
    private fun diagnosticNote(run: LastSyncRun?): List<String> =
        if (run == null || run.stage == SyncStage.NONE) emptyList() else listOf("Mã chẩn đoán: ${run.stage.name}")

    private fun waitText(ms: Long): String {
        val seconds = (ms + 999L) / 1000L
        return if (seconds < 90L) "$seconds giây" else "${(seconds + 59L) / 60L} phút"
    }
}
