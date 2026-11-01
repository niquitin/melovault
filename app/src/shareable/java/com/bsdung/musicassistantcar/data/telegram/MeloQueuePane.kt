package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView

/** Rendering identity only. Position ticks never change this key or the service queue. */
internal object MeloQueueUiPolicy {
    data class Key(val owner: Long, val rows: List<MeloScreenHost.QueueItem>, val current: Long?,
        val playing: Boolean, val paused: Boolean, val buffering: Boolean, val shuffle: Boolean = false)
    /** The rows are the real play order (the service applies Shuffle), so the title says so while Đảo is on. */
    fun caption(shuffle: Boolean, preview: Boolean): String =
        if (shuffle) "Thứ tự phát · Đảo bật" else if (preview) "Tiếp theo" else "Hàng đợi"
    fun scrollToCurrent(previous: Key?, next: Key): Boolean = next.current != null &&
        (previous?.current != next.current || previous.owner != next.owner)
    fun window(size: Int, currentIndex: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val start = (currentIndex - 1).coerceIn(0, size - 1)
        return start until minOf(size, start + 12)
    }
    /** UI-03: left, top, right, bottom padding of the full-screen queue: system bars and keyboard plus the screen's own margin. */
    fun fullScreenPadding(barsLeft: Int, barsTop: Int, barsRight: Int, barsBottom: Int, keyboardBottom: Int,
        marginPx: Int, gapPx: Int): List<Int> =
        listOf(barsLeft + marginPx, barsTop + gapPx, barsRight + marginPx, maxOf(barsBottom, keyboardBottom))
    fun dockStacked(width: Int, minTouch: Int, playWidth: Int, gap: Int, measuredSide: Int): Boolean =
        width < maxOf(minTouch, measuredSide) * 4 + maxOf(minTouch, playWidth) + gap * 2
}

/** Shared queue UI: reads the host and resolves taps by owner/song identity at click time. */
internal class MeloQueuePane(context: Context, private val c: MeloComponents, private val host: MeloScreenHost,
    private val preview: Boolean = false, onLibrary: (() -> Unit)? = null) {
    private val s = c.sizes
    private val p = c.palette
    val view = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val title = c.text("Tiếp theo", s.titleText, p.text, bold = true, maxLines = 2)
    private val previewRows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val list: ListView? = if (preview) null else ListView(context).apply {
        divider = null; dividerHeight = s.gap / 2; itemsCanFocus = true
        setSelector(android.R.color.transparent)
    }
    private var key: MeloQueueUiPolicy.Key? = null
    private val adapter = object : BaseAdapter() {
        override fun getCount() = key?.rows?.size ?: 0
        override fun getItem(position: Int) = key!!.rows[position]
        override fun getItemId(position: Int) = getItem(position).songKey
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = row(position)
    }
    init {
        view.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (!preview && host.named() != null) {
                addView(c.chip("≡ Lưu Playlist", false) { host.named()?.saveQueue() }.apply { contentDescription = MeloNamedPlaylistPolicy.SAVE_QUEUE_LABEL })
            }
            if (onLibrary != null) addView(c.chip("Thư viện", false) { onLibrary() })
        })
        if (list != null) {
            list.adapter = adapter
            view.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else view.addView(previewRows)
    }
    fun update(now: MeloScreenHost.NowPlaying? = host.nowPlaying()) {
        val next = MeloQueueUiPolicy.Key(host.accountId(), host.queue(), now?.songKey,
            now?.playing == true, now?.paused == true, now?.buffering == true, now?.shuffle == true)
        val old = key
        if (next == old) return
        val anchor = anchor()
        key = next
        val caption = MeloQueueUiPolicy.caption(next.shuffle, preview)
        title.text = if (preview) "$caption · tối đa 12 / ${next.rows.size} bài"
            else "$caption · ${next.rows.size} bài"
        if (list != null) {
            adapter.notifyDataSetChanged()
            if (MeloQueueUiPolicy.scrollToCurrent(old, next)) {
                val index = next.rows.indexOfFirst { it.songKey == next.current }
                if (index >= 0) list.setSelection((index - 2).coerceAtLeast(0))
            } else if (anchor.first != null && old?.owner == next.owner) restore(anchor.first, anchor.second)
        } else {
            previewRows.removeAllViews()
            MeloQueueUiPolicy.window(next.rows.size, next.rows.indexOfFirst { it.songKey == next.current }).forEach {
                previewRows.addView(row(it), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = s.gap / 2 })
            }
            if (next.rows.isEmpty()) previewRows.addView(c.text("Hàng đợi trống. Chọn bài trong thư viện.", s.secondaryText, p.textSecondary, maxLines = 2))
        }
    }
    fun anchor(): Pair<Long?, Int> = list?.let {
        key?.rows?.getOrNull(it.firstVisiblePosition)?.songKey to (it.getChildAt(0)?.top ?: 0)
    } ?: (null to 0)
    fun restore(songKey: Long?, offset: Int) {
        val index = key?.rows?.indexOfFirst { it.songKey == songKey } ?: -1
        if (index >= 0) list?.setSelectionFromTop(index, offset)
    }
    private fun row(index: Int): View {
        val state = key!!
        val item = state.rows[index]
        fun currentIndex(): Int? {
            if (host.accountId() != state.owner) return null
            return host.queue().indexOfFirst { it.songKey == item.songKey }.takeIf { it >= 0 }
        }
        val current = item.songKey == state.current
        val status = MeloNowPlayingPolicy.queueStatus(current, item.downloaded, item.downloadingPercent, state.paused, state.buffering)
        val fact = item.download ?: MeloDownloadUiPolicy.Presentation(when {
            item.downloaded -> MeloDownloadUiPolicy.State.READY
            item.downloadingPercent != null -> MeloDownloadUiPolicy.State.DOWNLOADING
            else -> MeloDownloadUiPolicy.State.UNKNOWN
        }, item.downloadingPercent)
        var row: View? = null
        row = c.songRow(MeloComponents.SongRowModel("${index + 1}. ${item.title}", item.sourceTitle,
            item.durationSeconds, status, progressPercent = item.downloadingPercent, local = item.local,
            statusTextOverride = MeloDownloadUiPolicy.rowText(fact, current, state.playing, state.paused, state.buffering, local = item.local)),
            onClick = { currentIndex()?.let(host::playQueueAt) },
            onLongClick = { currentIndex()?.let { host.openQueueSongMenu(it, row!!) } },
            onMore = { currentIndex()?.let { host.openQueueSongMenu(it, row!!) } })
        return row
    }
}
