package com.bsdung.musicassistantcar.data.telegram

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bsdung.musicassistantcar.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * E96 P4: Settings > Đồng bộ Playlist. Shows the privacy notice, the three SEPARATE consents, the channel state and the
 * manual sync buttons, all driven by [SyncSettingsController]. In the shipped P4 build the build switch
 * ([TelegramPlaylistFlags.SYNC_ENABLED]) is off: the screen says so, every control is locked, nothing is persisted and
 * nothing is sent to TDLib ([DisabledSyncActions]). Opening it, rotating it or coming back to it starts no network work.
 */
class MeloSyncSettingsActivity : AppCompatActivity() {

    private lateinit var components: MeloComponents
    private lateinit var content: LinearLayout
    private lateinit var controller: SyncSettingsController
    private var account: TelegramUserId? = null
    @Volatile private var lastFacts: PlaylistStatusFacts? = null

    /** Blocking controller calls (P5 will put TDLib behind them) never run on the UI thread nor on the playlist thread. */
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "MeloVault-SyncSettings").apply { isDaemon = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_MeloVault_Shareable)
        title = "Đồng bộ Playlist"
        // The id of the signed-in account is only read from the owner's memory; nothing is asked of TDLib.
        account = ShareableTdlibOwner.authorizedAccount()?.let { runCatching { TelegramUserId(it) }.getOrNull() }
        controller = SyncSettingsController(
            store = FileSyncConsentStore(noBackupFilesDir),
            buildEnabled = TelegramPlaylistFlags.SYNC_ENABLED,
            // With the build switch off this is DisabledSyncActions and nothing of TDLib is touched.
            actions = SyncRuntime.provider(noBackupFilesDir).actions(),
            account = { account },
            facts = { lastFacts },
            runLog = SyncRuntime.runLog,
            channelCreationUnlocked = TelegramPlaylistFlags.SYNC_CREATE_ALLOWED,
        )
        buildFrame()
        refresh()
    }

    override fun onDestroy() {
        // Leaving the screen releases the sync's TDLib binding and stops an unfinished run; it sends and deletes nothing.
        controller.close()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun buildFrame() {
        val m = MeloUiLabActivity.realMeasurements(this)
        val layout = MeloScreenPolicy.chooseLayout(m.windowWidthDp, m.windowHeightDp, carMode = false, panelCompact = m.panelCompact)
        val profile = if (layout == MeloScreenPolicy.Layout.COMPACT) MeloDesignTokens.Profile.COMPACT
        else MeloLibraryPolicy.profileFor(m.smallestWidthDp)
        val sizes = MeloDesignTokens.sizes(profile, m.display.density, m.display.fontScale, m.display.physicalPpi)
        components = MeloComponents(this, sizes)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(components.palette.background)
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sizes.screenMargin, sizes.gap, sizes.screenMargin, sizes.screenMargin)
        }
        scroll.addView(content)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
    }

    /** Reads the saved state off the UI thread (playlist thread for the V2 counts, then this screen's worker for consent). */
    private fun refresh() {
        val who = account
        if (who == null) {
            render(lockedScreen(), PlaylistSyncStatusPolicy.evaluate(0L, null, SyncContext(TelegramPlaylistFlags.SYNC_ENABLED, SyncConsent())))
            return
        }
        TelegramPlaylistRuntime.submit {
            lastFacts = TelegramPlaylistRuntime.session(noBackupFilesDir).statusFacts(who)
            try {
                worker.execute {
                    val screen = controller.screen()
                    val line = PlaylistSyncStatusPolicy.evaluate(who.value, lastFacts, controller.syncContext())
                    runOnUiThread { if (!isDestroyed) render(screen, line) }
                }
            } catch (_: RejectedExecutionException) {
            }
        }
    }

    /** Shown while there is no signed-in account in this process: every control locked, nothing to read. */
    private fun lockedScreen(): SyncSettingsScreen {
        val off = ControlState.BUILD_OFF.takeIf { !TelegramPlaylistFlags.SYNC_ENABLED } ?: ControlState.LOCKED
        return SyncSettingsScreen(
            buildOff = !TelegramPlaylistFlags.SYNC_ENABLED,
            buildNotice = if (TelegramPlaylistFlags.SYNC_ENABLED) null else PlaylistSyncStatusPolicy.BUILD_OFF_NOTICE,
            consent = SyncConsent(), enable = off, allowCreate = off, allowTransmit = off, discover = off, createNow = off,
            bindNow = off, syncNow = off, fullReplay = off, channel = ChannelView.Unchecked, busy = false,
        )
    }

    private fun render(screen: SyncSettingsScreen, status: PlaylistSyncStatusPolicy.Line) {
        val c = components
        val s = c.sizes
        val p = c.palette
        content.removeAllViews()
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            addView(c.iconButton("←", "Quay lại") { finish() }.apply { background = null })
            addView(c.text("Đồng bộ Playlist", s.titleText * 1.15f, p.text, bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        })

        screen.buildNotice?.let { notice ->
            content.addView(card {
                addView(c.text(notice, s.titleText, p.warning, bold = true, maxLines = 4))
                addView(note("Playlist vẫn được lưu trên máy này như bình thường. Các nút bên dưới đang bị khóa và chỉ để xem trước."))
            })
        }

        content.addView(c.sectionTitle("Trạng thái Playlist"))
        content.addView(card {
            addView(c.text(status.text, s.titleText, severityColor(status.severity), bold = true, maxLines = 3))
            val detail = status.fullDetail()
            if (detail.isNotBlank()) addView(note(detail))
        })

        content.addView(c.sectionTitle("Dữ liệu được lưu ở đâu"))
        content.addView(card { SyncPrivacyText.WARNING_LINES.forEach { addView(note(it)) } })

        content.addView(c.sectionTitle("Ba quyền riêng biệt"))
        content.addView(consentRow(SyncPrivacyText.CONSENT_ENABLE, screen.enable) {
            if (screen.enable == ControlState.ACTIVE) confirmTurnOff() else confirmEnable()
        })
        content.addView(consentRow(SyncPrivacyText.CONSENT_CREATE, screen.allowCreate) {
            act { controller.setAllowCreate(screen.allowCreate != ControlState.ACTIVE) }
        })
        content.addView(consentRow(SyncPrivacyText.CONSENT_TRANSMIT, screen.allowTransmit) {
            act { controller.setAllowTransmit(screen.allowTransmit != ControlState.ACTIVE) }
        })
        content.addView(note(SyncPrivacyText.OFF_NOTE))

        content.addView(c.sectionTitle("Kênh đồng bộ"))
        content.addView(card { addView(note(screen.channel.text())) })
        content.addView(button("Tìm kênh (chỉ đọc)", screen.discover) { act { controller.discover() } })
        content.addView(button("Tạo kênh riêng", screen.createNow) { confirmCreate() })
        if (screen.createNow == ControlState.LOCKED && !TelegramPlaylistFlags.SYNC_CREATE_ALLOWED) {
            content.addView(note("Bản này không tạo kênh mới: chỉ tìm và gắn kênh Telegram đã có."))
        }
        content.addView(button("Dùng kênh này", screen.bindNow) { confirmBind() })

        content.addView(c.sectionTitle("Lượt gửi (do bạn cho phép từng lần)"))
        content.addView(card {
            addView(c.text(
                if (screen.sendGrant > 0) "Còn ${screen.sendGrant} thay đổi được phép gửi" else "Chưa cho phép gửi thay đổi nào",
                s.titleText, if (screen.sendGrant > 0) p.accent else p.text, bold = true, maxLines = 2,
            ))
            addView(note(SyncPrivacyText.GRANT_NOTE))
        })
        for (n in 1..SyncSendBudget.PRODUCTION_MAX) {
            content.addView(button("Cho phép gửi $n thay đổi", screen.grantSend.takeUnless { it == ControlState.ACTIVE } ?: ControlState.AVAILABLE) {
                confirmGrant(n)
            })
        }
        content.addView(button("Thu hồi lượt gửi", if (screen.sendGrant > 0) ControlState.AVAILABLE else ControlState.LOCKED) {
            act { controller.revokeSendGrant() }
        })

        content.addView(c.sectionTitle("Đồng bộ thủ công"))
        content.addView(button("Đồng bộ ngay", screen.syncNow) { act { controller.syncNow() } })
        content.addView(button("Đọc lại toàn bộ lịch sử", screen.fullReplay) { confirmFullReplay() })
        content.addView(note(
            "Đồng bộ chỉ chạy khi bạn bấm. Không có tác vụ nền, không tự chạy lại. " +
                "Đọc lại toàn bộ lịch sử an toàn, không xóa gì trên máy hay trên Telegram."
        ))
    }

    // ---- actions -----------------------------------------------------------------------------------------

    private fun act(call: () -> ActionResult) {
        try {
            worker.execute {
                val result = call()
                // Only a closed list of names is logged (Sony hides Log.i, hence warn): never an id, a text or a song.
                account?.let { SyncRuntime.runLog.last(it.value) }?.takeIf { it.stage != SyncStage.NONE }?.let {
                    Log.w("MeloSync", "stage=${it.stage.name} status=${it.status?.name}")
                }
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    resultText(result)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                    refresh()
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun resultText(result: ActionResult): String? = when (result) {
        ActionResult.Done -> null
        ActionResult.NotSaved -> "Chưa lưu được thay đổi. Không có gì bị mất."
        is ActionResult.Denied -> when (result.reason) {
            SyncDenied.BUILD_OFF -> PlaylistSyncStatusPolicy.BUILD_OFF_NOTICE
            SyncDenied.CREATE_LOCKED -> "Bản này không tạo kênh mới; chỉ tìm và gắn kênh đã có."
            else -> "Chưa được phép (${result.reason.name.lowercase().replace('_', ' ')})."
        }
        is ActionResult.Refused -> when (result.reason) {
            ActionResult.Reason.BUILD_OFF -> PlaylistSyncStatusPolicy.BUILD_OFF_NOTICE
            ActionResult.Reason.NO_ACCOUNT -> "Cần đăng nhập Telegram."
            ActionResult.Reason.WARNING_NOT_ACCEPTED -> "Cần đọc và đồng ý cảnh báo trước."
            ActionResult.Reason.NOT_ENABLED -> "Hãy bật đồng bộ trước."
            ActionResult.Reason.NOT_CONFIRMED -> "Cần bạn xác nhận."
            ActionResult.Reason.NEEDS_DISCOVERY -> "Hãy bấm \"Tìm kênh\" trước."
            ActionResult.Reason.NO_CHANNEL -> "Chưa có kênh đồng bộ."
            ActionResult.Reason.BUSY -> "Đang bận, thử lại sau giây lát."
            ActionResult.Reason.TRANSMIT_NOT_ALLOWED -> "Hãy bật quyền gửi danh sách trước."
            ActionResult.Reason.BAD_SEND_BUDGET -> "Chỉ cho phép từ 1 đến ${SyncSendBudget.PRODUCTION_MAX} thay đổi."
        }
    }

    private fun confirmGrant(n: Int) {
        val waiting = lastFacts?.pendingCount ?: 0
        AlertDialog.Builder(this)
            .setTitle("Cho phép gửi $n thay đổi?")
            .setMessage(
                "Hiện có $waiting thay đổi chờ gửi. Lần \"Đồng bộ ngay\" kế tiếp được gửi tối đa $n thay đổi đầu hàng đợi, theo đúng thứ tự, " +
                    "rồi lượt này hết. Nếu thay đổi nào đã gửi mà chưa biết có tới nơi, ứng dụng không gửi lại.\n\n" + SyncPrivacyText.GRANT_NOTE
            )
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Cho phép $n") { _, _ -> act { controller.grantSend(n, confirmed = true) } }
            .show()
    }

    private fun confirmEnable() {
        AlertDialog.Builder(this)
            .setTitle(SyncPrivacyText.TITLE)
            .setMessage(SyncPrivacyText.WARNING_LINES.joinToString("\n\n"))
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Đồng ý và bật") { _, _ -> act { controller.enable(warningShownAndAccepted = true) } }
            .show()
    }

    private fun confirmTurnOff() {
        AlertDialog.Builder(this)
            .setTitle("Tắt đồng bộ?")
            .setMessage(SyncPrivacyText.OFF_NOTE)
            .setNegativeButton("Giữ nguyên", null)
            .setPositiveButton("Tắt đồng bộ") { _, _ -> act { controller.turnOff() } }
            .show()
    }

    private fun confirmCreate() {
        AlertDialog.Builder(this)
            .setTitle("Tạo kênh Telegram riêng?")
            .setMessage("Ứng dụng sẽ tạo MỘT kênh riêng tên \"${SyncChannelSpec.TITLE}\" trong tài khoản của bạn. Chỉ làm khi đã xác nhận chưa có kênh nào.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Tạo kênh") { _, _ -> act { controller.createChannel(confirmed = true) } }
            .show()
    }

    private fun confirmBind() {
        AlertDialog.Builder(this)
            .setTitle("Dùng kênh đã tìm thấy?")
            .setMessage("Gắn kênh này làm nơi đồng bộ Playlist của tài khoản. Không xóa hay đổi gì trong kênh.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Dùng kênh này") { _, _ -> act { controller.bindFoundChannel(confirmed = true) } }
            .show()
    }

    private fun confirmFullReplay() {
        AlertDialog.Builder(this)
            .setTitle("Đọc lại toàn bộ lịch sử?")
            .setMessage("Đọc lại từ đầu mọi thay đổi trong kênh để chắc chắn không sót. Không xóa dữ liệu trên máy hay Telegram; có thể lâu hơn.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Đọc lại") { _, _ -> act { controller.fullReplay(confirmed = true) } }
            .show()
    }

    // ---- small views -----------------------------------------------------------------------------------------

    private fun severityColor(severity: PlaylistSyncStatusPolicy.Severity): Int = when (severity) {
        PlaylistSyncStatusPolicy.Severity.OK -> components.palette.accent
        PlaylistSyncStatusPolicy.Severity.INFO -> components.palette.text
        PlaylistSyncStatusPolicy.Severity.WARN, PlaylistSyncStatusPolicy.Severity.ERROR -> components.palette.warning
    }

    private fun card(build: LinearLayout.() -> Unit): View {
        val s = components.sizes
        val p = components.palette
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = components.roundedBackground(p.surface, stroke = p.border)
            setPadding(s.gap * 2, s.gap, s.gap * 2, s.gap)
            build()
        }
    }

    private fun note(value: String): TextView =
        components.text(value, components.sizes.secondaryText, components.palette.textSecondary, maxLines = 12).apply {
            setPadding(0, components.sizes.gap / 2, 0, components.sizes.gap / 2)
        }

    private fun button(label: String, state: ControlState, onClick: () -> Unit): TextView {
        val s = components.sizes
        val p = components.palette
        val usable = state == ControlState.AVAILABLE
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, s.titleText)
            setTextColor(p.text)
            typeface = Typeface.DEFAULT_BOLD
            background = components.pill(p.surfaceRaised, p.border)
            minHeight = s.minTouch
            setPadding(s.gap * 2, 0, s.gap * 2, 0)
            alpha = if (usable) 1f else 0.45f
            isEnabled = usable
            isClickable = usable
            isFocusable = usable
            contentDescription = if (state == ControlState.BUILD_OFF) "$label, chưa khả dụng trong bản thử nghiệm" else label
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.minTouch).apply { topMargin = s.gap / 2 }
            if (usable) setOnClickListener { onClick() }
        }
    }

    private fun consentRow(label: String, state: ControlState, onClick: () -> Unit): View {
        val s = components.sizes
        val p = components.palette
        val usable = state == ControlState.AVAILABLE || state == ControlState.ACTIVE
        val on = state == ControlState.ACTIVE
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            setPadding(s.gap, 0, s.gap, 0)
            background = components.roundedBackground(if (on) p.accentSoft else p.surface)
            alpha = if (usable) 1f else 0.45f
            isClickable = usable
            isFocusable = usable
            contentDescription = label + when (state) {
                ControlState.ACTIVE -> ", đang bật"
                ControlState.AVAILABLE -> ", đang tắt"
                ControlState.LOCKED -> ", đang khóa, cần bật đồng bộ trước"
                ControlState.BUILD_OFF -> ", chưa khả dụng trong bản thử nghiệm"
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = s.gap / 2
            }
            addView(components.text(if (on) "☑" else "☐", s.titleText * 1.2f, if (on) p.accent else p.textSecondary).apply {
                setPadding(0, 0, s.gap * 3 / 2, 0)
            })
            addView(components.text(label, s.titleText, p.text, maxLines = 3).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (usable) setOnClickListener { onClick() }
        }
    }
}
