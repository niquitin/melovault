package com.bsdung.musicassistantcar.data.telegram

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.AddTarget
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.IndexRow
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.NameCheck
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.PlayPlan
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.QueueSavePlan
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.RowKind
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.Screen

/**
 * Nhiều Playlist có tên, S14-S18: the dialogs and the actions of the named playlists, as Views. It decides nothing: names, targets,
 * messages and plans come from [MeloNamedPlaylistPolicy]; every write goes through [NamedPlaylistController], which queues it on the
 * playlist thread. The activity supplies what only it knows through [Env].
 */
internal class NamedPlaylistUi(
    private val activity: AppCompatActivity,
    private val controller: NamedPlaylistController,
    private val env: Env,
) : MeloNamedPlaylistHost {

    interface Env {
        fun toast(message: String)
        fun accountId(): Long

        /** The favourites' count for the index row; null while they are not read yet. */
        fun favouritesCount(): Int?

        /** What the person sees for [entry]: the song's title, or the placeholder wording. */
        fun titleOf(entry: NamedEntry): String

        /** The kinds of the rows of the playlist on screen, in the same order as its rows. */
        fun detailPlayEntries(): List<MeloNamedPlaylistPolicy.PlayEntry>

        /** Plays row [index] of the playlist on screen (and turns Shuffle on first when [shuffle]). */
        fun playDetailRow(index: Int, shuffle: Boolean)

        /** The queue now held by the player, in order. */
        fun queueCandidates(): List<MeloNamedPlaylistPolicy.QueueCandidate>
    }

    private val dialogContext get() = ContextThemeWrapper(activity, R.style.Theme_MeloVault_Dialog)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun nameOf(id: ListId): String = controller.lists.firstOrNull { it.id == id }?.name ?: "Playlist"

    // ---- the host contract ------------------------------------------------------------------------------

    override fun screen(): Screen = controller.screen

    override fun statusNote(): String? = when (controller.status) {
        NamedPlaylistController.Status.READY -> null
        NamedPlaylistController.Status.OPENING -> "Đang mở Playlist…"
        NamedPlaylistController.Status.BLOCKED -> MeloNamedPlaylistPolicy.REFUSED_UNREADABLE
        NamedPlaylistController.Status.CLOSED -> MeloNamedPlaylistPolicy.REFUSED_NOT_READY
    }

    override fun indexRows(): List<IndexRow> = MeloNamedPlaylistPolicy.indexRows(env.favouritesCount(), controller.lists)
        .let { rows -> if (controller.ready) rows else rows.map { if (it.kind == RowKind.CREATE) it.copy(enabled = false) else it } }

    override fun headerTitle(): String? = when (val screen = controller.screen) {
        Screen.Index -> null
        Screen.Favourites -> MeloNamedPlaylistPolicy.FAVOURITES_NAME
        is Screen.Detail -> controller.lists.firstOrNull { it.id == screen.id }
            ?.let { MeloNamedPlaylistPolicy.detailTitle(it.name, it.count) }
    }

    override fun open(row: IndexRow) {
        when (row.kind) {
            RowKind.FAVOURITES -> controller.show(Screen.Favourites)
            RowKind.NAMED -> row.id?.let { controller.show(Screen.Detail(it)) }
            RowKind.CREATE -> promptCreate(null, "")
        }
    }

    override fun back() = controller.show(MeloNamedPlaylistPolicy.back(controller.screen))

    override fun openIndexDialog() {
        val rows = indexRows()
        AlertDialog.Builder(activity, R.style.Theme_MeloVault_Dialog)
            .setTitle("Playlist")
            .setItems(rows.map { it.title }.toTypedArray()) { _, which -> rows.getOrNull(which)?.takeIf { it.enabled }?.let(::open) }
            .setNegativeButton("Đóng", null)
            .show()
    }

    override fun openMenu(anchor: View) {
        val id = (controller.screen as? Screen.Detail)?.id ?: return
        val popup = PopupMenu(dialogContext, anchor)
        popup.menu.add(0, 1, 1, "✎ Đổi tên")
        popup.menu.add(0, 2, 2, "↕ ${MeloNamedPlaylistPolicy.SORT_TITLE}")
        popup.menu.add(0, 3, 3, "🗑 Xóa Playlist")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> promptRename(id)
                2 -> showSort(id)
                3 -> confirmDelete(id)
            }
            true
        }
        popup.show()
    }

    override fun play(shuffle: Boolean) {
        val entries = env.detailPlayEntries()
        val pick = if (shuffle) kotlin.random.Random.nextInt(0, Int.MAX_VALUE) else 0
        when (val plan = MeloNamedPlaylistPolicy.playPlan(entries, shuffle, pick)) {
            is PlayPlan.FromTelegram -> {
                env.playDetailRow(plan.index, shuffle)
                plan.note?.let(env::toast)
            }
            is PlayPlan.FromLocal -> env.playDetailRow(plan.index, shuffle)
            is PlayPlan.NothingToPlay -> env.toast(plan.message)
        }
    }

    // ---- names: create / rename ------------------------------------------------------------------------------------------

    private fun promptName(title: String, initial: String, positive: String, except: ListId?, onName: (String) -> Unit) {
        val input = EditText(dialogContext).apply {
            setText(initial)
            setSelection(text.length)
            hint = "Tên Playlist"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(MeloNamedPlaylistPolicy.MAX_NAME_INPUT))
            setSingleLine()
            contentDescription = "Tên Playlist, tối đa ${NamedPlaylistNames.MAX_CHARS} ký tự"
        }
        val box = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(activity, R.style.Theme_MeloVault_Dialog)
            .setTitle(title).setView(box)
            .setPositiveButton(positive, null)
            .setNegativeButton("Hủy", null)
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                when (val check = MeloNamedPlaylistPolicy.checkName(input.text.toString(), controller.lists, except)) {
                    is NameCheck.Ok -> {
                        dialog.dismiss()
                        onName(check.name)
                    }
                    else -> input.error = MeloNamedPlaylistPolicy.nameMessage(check)
                }
            }
        }
        dialog.show()
    }

    /** "+ Tạo Playlist" ([thenAdd] null) or "+ Playlist mới…" in the add box (the song goes into the new playlist at once). */
    fun promptCreate(thenAdd: AddTarget?, songTitle: String) {
        if (!controller.ready) { env.toast(unavailable()); return }
        promptName("Tạo Playlist", "", "Tạo", null) { name ->
            if (thenAdd == null) {
                controller.create(name) { outcome ->
                    env.toast(MeloNamedPlaylistPolicy.failureMessage(outcome, name) ?: MeloNamedPlaylistPolicy.created(name))
                }
            } else {
                controller.createWith(name, listOf(thenAdd)) { created ->
                    val failure = MeloNamedPlaylistPolicy.failureMessage(created.stoppedBy ?: created.outcome, name)
                    env.toast(failure ?: (MeloNamedPlaylistPolicy.created(name) + " " + MeloNamedPlaylistPolicy.added(songTitle, name)))
                }
            }
        }
    }

    private fun promptRename(id: ListId) {
        val current = nameOf(id)
        promptName("Đổi tên Playlist", current, "Đổi tên", id) { name ->
            controller.rename(id, name) { outcome ->
                env.toast(MeloNamedPlaylistPolicy.failureMessage(outcome, current) ?: MeloNamedPlaylistPolicy.renamed(name))
            }
        }
    }

    private fun confirmDelete(id: ListId) {
        val name = nameOf(id)
        AlertDialog.Builder(activity, R.style.Theme_MeloVault_Dialog)
            .setTitle("Xóa Playlist")
            .setMessage(MeloNamedPlaylistPolicy.deleteQuestion(name))
            .setNegativeButton("Giữ lại", null)
            .setPositiveButton("Xóa") { _, _ ->
                controller.delete(id) { outcome ->
                    env.toast(MeloNamedPlaylistPolicy.failureMessage(outcome, name) ?: MeloNamedPlaylistPolicy.deleted(name))
                }
            }
            .show()
    }

    private fun unavailable(): String =
        if (controller.status == NamedPlaylistController.Status.BLOCKED) MeloNamedPlaylistPolicy.REFUSED_UNREADABLE
        else MeloNamedPlaylistPolicy.REFUSED_NOT_READY

    // ---- "Thêm vào Playlist…" ---------------------------------------------------------------------------------------------------

    /** From a song's menu. A refused target (legacy, placeholder, not ready) says why and opens nothing. */
    fun showAddToPlaylist(target: AddTarget, songTitle: String) {
        val item: PlaylistItemId = when (target) {
            is AddTarget.Telegram -> target.item
            is AddTarget.Local -> PlaylistItemId.Local(target.localId)
            is AddTarget.Refused -> { env.toast(target.message); return }
        }
        if (!controller.ready) { env.toast(unavailable()); return }
        controller.containing(item) { containing ->
            val rows = MeloNamedPlaylistPolicy.choiceRows(controller.lists, containing, songTitle)
            AlertDialog.Builder(activity, R.style.Theme_MeloVault_Dialog)
                .setTitle("Thêm vào Playlist")
                .setItems(rows.map { it.label }.toTypedArray()) { _, which -> rows.getOrNull(which)?.let { onChoice(it, target, item, songTitle) } }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    private fun onChoice(row: MeloNamedPlaylistPolicy.ChoiceRow, target: AddTarget, item: PlaylistItemId, songTitle: String) {
        val id = row.id
        if (id == null) {
            promptCreate(target, songTitle)
            return
        }
        val list = nameOf(id)
        if (MeloNamedPlaylistPolicy.tapAdds(row)) {
            controller.add(id, target) { outcome ->
                env.toast(MeloNamedPlaylistPolicy.failureMessage(outcome, list) ?: MeloNamedPlaylistPolicy.added(songTitle, list))
            }
        } else {
            controller.remove(id, item) { outcome ->
                env.toast(MeloNamedPlaylistPolicy.failureMessage(outcome, list) ?: MeloNamedPlaylistPolicy.removed(songTitle, list))
            }
        }
    }

    // ---- a song of the playlist on screen --------------------------------------------------------------------------------------------

    /** "Bỏ khỏi ‘tên’" from a row menu of the playlist on screen, and the offer to drop a device song that is gone. */
    fun removeFromCurrent(item: PlaylistItemId, songTitle: String) {
        val id = (controller.screen as? Screen.Detail)?.id ?: return
        val list = nameOf(id)
        controller.remove(id, item) { outcome ->
            env.toast(MeloNamedPlaylistPolicy.failureMessage(outcome, list) ?: MeloNamedPlaylistPolicy.removed(songTitle, list))
        }
    }

    fun offerRemoveGoneLocal(item: PlaylistItemId) {
        AlertDialog.Builder(activity, R.style.Theme_MeloVault_Dialog)
            .setTitle(MeloNamedPlaylistPolicy.LOCAL_TAG.replaceFirstChar { it.uppercase() })
            .setMessage(MeloNamedPlaylistPolicy.LOCAL_GONE_LABEL)
            .setNegativeButton("Giữ lại", null)
            .setPositiveButton("Bỏ khỏi Playlist") { _, _ -> removeFromCurrent(item, "Bài trên máy") }
            .show()
    }

    // ---- sorting ------------------------------------------------------------------------------------------------------------------------

    private var sortDialog: AlertDialog? = null
    private var sortAdapter: BaseAdapter? = null
    private var sortBusy = false

    /** Redraws the open sort box after the entries changed (also when another device changed them). */
    fun onEntriesChanged() {
        sortAdapter?.notifyDataSetChanged()
    }

    private fun touchButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit): TextView =
        TextView(dialogContext).apply {
            text = label
            textSize = 20f
            gravity = Gravity.CENTER
            minWidth = dp(48)
            minHeight = dp(48)
            contentDescription = description
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.35f
            background = GradientDrawable().apply { setColor(Color.argb(0x33, 0xFF, 0xFF, 0xFF)); cornerRadius = dp(8).toFloat() }
            isClickable = enabled
            isFocusable = enabled
            if (enabled) setOnClickListener { onClick() }
        }

    private fun showSort(id: ListId) {
        sortDialog?.dismiss()
        val list = ListView(dialogContext)
        val adapter = object : BaseAdapter() {
            override fun getCount() = controller.detailEntries.size
            override fun getItem(position: Int): Any = controller.detailEntries[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val entry = controller.detailEntries[position]
                val title = env.titleOf(entry)
                val size = controller.detailEntries.size
                val up = MeloNamedPlaylistPolicy.moveTo(position, -1, size)
                val down = MeloNamedPlaylistPolicy.moveTo(position, 1, size)
                return LinearLayout(dialogContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    addView(TextView(dialogContext).apply {
                        text = "${position + 1}. $title"
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(dp(8), 0, dp(8), 0)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(touchButton(MeloNamedPlaylistPolicy.SORT_UP, MeloNamedPlaylistPolicy.sortUpDescription(title), up != null) { move(id, entry, up) })
                    addView(touchButton(MeloNamedPlaylistPolicy.SORT_DOWN, MeloNamedPlaylistPolicy.sortDownDescription(title), down != null) { move(id, entry, down) })
                }
            }
        }
        list.adapter = adapter
        sortAdapter = adapter
        val dialog = AlertDialog.Builder(activity, R.style.Theme_MeloVault_Dialog)
            .setTitle("${MeloNamedPlaylistPolicy.SORT_TITLE} · ${nameOf(id)}")
            .setView(list)
            .setPositiveButton(MeloNamedPlaylistPolicy.SORT_DONE, null)
            .create()
        dialog.setOnDismissListener { if (sortDialog === dialog) { sortDialog = null; sortAdapter = null } }
        sortDialog = dialog
        dialog.show()
    }

    private fun move(id: ListId, entry: NamedEntry, to: Int?) {
        if (to == null || sortBusy) return
        sortBusy = true
        controller.move(id, entry.item, to) { outcome ->
            sortBusy = false
            MeloNamedPlaylistPolicy.failureMessage(outcome, nameOf(id))?.let(env::toast)
            sortAdapter?.notifyDataSetChanged()
        }
    }

    // ---- "Lưu hàng chờ thành Playlist" --------------------------------------------------------------------------------------------------

    override fun saveQueue() {
        if (!controller.ready) { env.toast(unavailable()); return }
        when (val plan = MeloNamedPlaylistPolicy.planQueueSave(env.queueCandidates(), env.accountId())) {
            QueueSavePlan.Empty -> env.toast(MeloNamedPlaylistPolicy.QUEUE_EMPTY)
            is QueueSavePlan.TooMany -> env.toast(MeloNamedPlaylistPolicy.queueTooMany(plan.count))
            is QueueSavePlan.Ready -> {
                if (plan.items.isEmpty()) {
                    env.toast(MeloNamedPlaylistPolicy.QUEUE_NOTHING_SAVABLE)
                    return
                }
                promptName("Lưu hàng chờ thành Playlist", "", "Lưu", null) { name ->
                    controller.createWith(name, plan.items) { created ->
                        val failure = MeloNamedPlaylistPolicy.failureMessage(created.stoppedBy ?: created.outcome, name)
                        val skipped = plan.skipped + (plan.items.size - created.added)
                        val summary = if (created.listId != null) MeloNamedPlaylistPolicy.queueSaved(created.added, skipped) else null
                        env.toast(listOfNotNull(summary, failure).joinToString(" ").ifBlank { MeloNamedPlaylistPolicy.FAILED })
                    }
                }
            }
        }
    }
}
