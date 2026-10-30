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
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** One opt-in for foreground sync. The pending review is local and never sends anything. */
class MeloSyncSettingsActivity : AppCompatActivity() {
    private lateinit var components: MeloComponents
    private lateinit var content: LinearLayout
    private val automatic by lazy { SyncRuntime.automatic(noBackupFilesDir) }
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "MeloVault-SyncSettings").apply { isDaemon = true }
    }
    private var visible = false
    private var viewGeneration = 0L
    private var refreshGeneration = 0L
    private var reviewing = false

    private fun account() = ShareableTdlibOwner.authorizedAccount()?.let { TelegramUserId(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_MeloVault_Shareable)
        title = "Đồng bộ Playlist"
        PlaylistSyncForeground.install(application, noBackupFilesDir)
        buildFrame()
    }

    override fun onResume() {
        super.onResume()
        visible = true
        SyncRuntime.listen(this) { runOnUiThread { if (visible && !isDestroyed) refresh() } }
        refresh()
    }

    override fun onPause() {
        visible = false
        viewGeneration++
        reviewing = false
        SyncRuntime.unlisten(this)
        super.onPause()
    }

    override fun onDestroy() {
        SyncRuntime.unlisten(this)
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
        val scroll = ScrollView(this).apply { setBackgroundColor(components.palette.background); isFillViewport = true }
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

    private fun background(block: () -> Unit) {
        try { worker.execute(block) } catch (_: RejectedExecutionException) { }
    }

    private fun refresh() {
        val who = account()
        val epoch = ShareableTdlibOwner.authEpoch()
        val request = ++refreshGeneration
        background {
            val facts = who?.let { TelegramPlaylistRuntime.session(noBackupFilesDir).statusFacts(it) }
            val context = automatic.context(who)
            context.lastRun?.takeIf { it.stage != SyncStage.NONE }?.let {
                Log.w("MeloSync", "stage=${it.stage.name} status=${it.status?.name}")
            }
            val line = PlaylistSyncStatusPolicy.evaluate(who?.value ?: 0, facts, context)
            val notice = automatic.notice
            runOnUiThread {
                if (!visible || isDestroyed || request != refreshGeneration || account() != who || ShareableTdlibOwner.authEpoch() != epoch) return@runOnUiThread
                render(who, facts, context, line, notice)
            }
        }
    }

    private fun render(who: TelegramUserId?, facts: PlaylistStatusFacts?, context: SyncContext,
                       line: PlaylistSyncStatusPolicy.Line, notice: String?) {
        content.removeAllViews()
        button("‹ Quay lại", true) { finish() }
        content.addView(components.sectionTitle("Tự đồng bộ Playlist"))
        content.addView(note(line.text, bold = true))
        content.addView(note(line.fullDetail()))
        notice?.let { content.addView(note(it)) }
        if (automatic.needsDisableRetry) button("Lưu trạng thái tắt lại", who != null) { confirmTurnOff() }
        val enabled = context.consent.automatic
        val ready = TelegramPlaylistFlags.SYNC_ENABLED && who != null && facts != null && facts.unreadable == null
        button(if (enabled) "☑ Tự đồng bộ đang bật · Bấm để tắt" else "Bật tự đồng bộ Playlist",
            ready && (enabled || !context.running) && !reviewing) {
            if (enabled) confirmTurnOff() else reviewPending(enable = true)
        }
        content.addView(note("Bật một lần trên mỗi máy. Khi app đang mở, thêm/bỏ bài sẽ tự truyền sang máy khác. Máy đang đóng nhận khi mở lại."))
        content.addView(note("Chỉ dùng cùng tài khoản Telegram và kênh đồng bộ đã có. Không tạo kênh mới; không tự tải hoặc phát nhạc."))
        button("Xem thay đổi chờ gửi (${facts?.pendingCount ?: 0})", ready && !reviewing) { reviewPending(enable = false) }
        button("Đồng bộ ngay", ready && enabled && !context.running && !reviewing) { automatic.requestNow() }
        content.addView(note(SyncPrivacyText.OFF_NOTE))
    }

    private fun reviewPending(enable: Boolean) {
        if (reviewing) return
        reviewing = true
        val token = viewGeneration
        refresh()
        background {
            val review = automatic.pendingReview()
            val rows = review?.let { pendingText(it) }
            runOnUiThread {
                reviewing = false
                if (!visible || isDestroyed || token != viewGeneration) return@runOnUiThread
                if (review == null || account() != review.account || ShareableTdlibOwner.authEpoch() != review.authEpoch) {
                    toast("Playlist chưa sẵn sàng. Hãy quay lại thư viện rồi thử lại."); refresh(); return@runOnUiThread
                }
                val text = if (enable) SyncPrivacyText.WARNING_LINES.joinToString("\n\n") + "\n\nThay đổi sẽ được gửi:\n" + rows else rows.orEmpty()
                val scroll = ScrollView(this).apply {
                    addView(note(text).apply { setPadding(32, 16, 32, 16) })
                }
                val dialog = AlertDialog.Builder(this)
                    .setTitle(if (enable) "Bật tự đồng bộ Playlist?" else "Thay đổi chờ gửi")
                    .setView(scroll)
                    .setNegativeButton(if (enable) "Hủy" else "Đóng", null)
                if (enable) dialog.setPositiveButton("Đồng ý và bật") { _, _ ->
                    if (!visible || token != viewGeneration) return@setPositiveButton
                    automatic.enable(review, confirmed = true) { ok, message ->
                        runOnUiThread {
                            if (visible && !isDestroyed && token == viewGeneration && account() == review.account && ShareableTdlibOwner.authEpoch() == review.authEpoch) {
                                toast(if (ok) "Đã bật tự đồng bộ Playlist." else message ?: "Chưa bật được đồng bộ.")
                                refresh()
                            }
                        }
                    }
                }
                dialog.show()
                refresh()
            }
        }
    }

    /** Metadata from the existing local library only; exact source IDs disambiguate duplicate titles. Never logged. */
    private fun pendingText(review: PendingPlaylistReview): String {
        if (review.entries.isEmpty()) return "Không có thay đổi chờ gửi."
        val keys = review.entries.map { it.op.item.songKey(review.account.value) }
        val dbFile = getDatabasePath("melovault_tg_index_${review.account.value}.db")
        val songs = if (dbFile.exists()) runCatching {
            AndroidTelegramLibraryDatabase(applicationContext, review.account.value).use { db ->
                db.getSongsByKeys(review.account.value, keys).associateBy { it.songKey }
            }
        }.getOrDefault(emptyMap()) else emptyMap()
        return review.entries.mapIndexed { index, entry ->
            val item = entry.op.item
            val song = songs[item.songKey(review.account.value)]
            val action = if (entry.op.type == OpType.ADD) "Thêm" else "Bỏ"
            val name = song?.title?.takeIf { it.isNotBlank() } ?: "Bài chưa có tên trong thư viện máy này"
            val uncertain = if (entry.status == OutboxStatus.RETRY_REQUIRED) "\nChưa xác nhận đã tới kênh; chỉ đối chiếu, không gửi lại." else ""
            "${index + 1}. $action: $name\n${song?.sourceTitle.orEmpty()} · Nguồn ${item.chatId} · Tin ${item.messageId}$uncertain"
        }.joinToString("\n\n")
    }

    private fun confirmTurnOff() {
        val who = account()
        val epoch = ShareableTdlibOwner.authEpoch()
        AlertDialog.Builder(this).setTitle("Tắt tự đồng bộ?").setMessage(SyncPrivacyText.OFF_NOTE)
            .setNegativeButton("Giữ nguyên", null)
            .setPositiveButton("Tắt đồng bộ") { _, _ ->
                background {
                    if (account() != who || ShareableTdlibOwner.authEpoch() != epoch) return@background
                    val ok = automatic.turnOff()
                    runOnUiThread {
                        if (visible && !isDestroyed && account() == who && ShareableTdlibOwner.authEpoch() == epoch) {
                            if (!ok) automatic.notice?.let { toast(it) }
                            refresh()
                        }
                    }
                }
            }.show()
    }

    private fun note(text: String, bold: Boolean = false): TextView = components.text(
        text, components.sizes.secondaryText, components.palette.textSecondary, bold = bold, maxLines = Int.MAX_VALUE,
    ).apply { setPadding(0, components.sizes.gap, 0, components.sizes.gap) }

    private fun button(label: String, usable: Boolean, action: () -> Unit) {
        val s = components.sizes
        val p = components.palette
        content.addView(TextView(this).apply {
            text = label; gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, s.titleText)
            setTextColor(p.text); typeface = Typeface.DEFAULT_BOLD
            background = components.pill(p.surfaceRaised, p.border)
            minHeight = s.minTouch; setPadding(s.gap * 2, s.gap, s.gap * 2, s.gap)
            alpha = if (usable) 1f else 0.45f
            isEnabled = usable; isClickable = usable; isFocusable = usable
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = s.gap }
            if (usable) setOnClickListener { action() }
        })
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
