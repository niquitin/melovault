package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** The same page in phone and two-pane browse, with a virtualised list and topic-ID scroll anchors. */
internal class MeloForumCatalogueView(private val context: Context, private val c: MeloComponents, private val host: MeloScreenHost) {
    val view = ListView(context).apply {
        divider = null
        dividerHeight = c.sizes.gap
        setBackgroundColor(c.palette.background)
        cacheColorHint = android.graphics.Color.TRANSPARENT
        visibility = View.GONE
    }
    private data class Holder(val root: LinearLayout, val title: TextView, val detail: TextView)
    private var snapshot: MeloScreenHost.Snapshot? = null
    private var key: Triple<Long, MeloBrowseUiPolicy.Scope, String>? = null
    private var pendingAnchor: Pair<Int, Int>? = null
    private var restoring = false
    private fun id(position: Int): Int? = if (position == 0) 0 else snapshot?.forumTopics?.getOrNull(position - 1)?.id
    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = (snapshot?.forumTopics?.size ?: 0) + 2 + if (snapshot?.forumCanLoadMore == true) 1 else 0
        override fun getItem(position: Int): Any? = snapshot?.forumTopics?.getOrNull(position - 1)
        override fun getItemId(position: Int): Long = id(position)?.toLong() ?: -position.toLong() - 1L
        override fun hasStableIds() = true
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int): Boolean = position <= (snapshot?.forumTopics?.size ?: 0) || snapshot?.forumLoading != true
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder = convertView?.tag as? Holder ?: run {
                val title = c.text("", c.sizes.titleText, c.palette.text, maxLines = 2)
                val detail = c.text("", c.sizes.secondaryText, c.palette.textSecondary, maxLines = 3)
                val root = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = c.sizes.minTouch
                    setPadding(c.sizes.gap * 2, c.sizes.gap, c.sizes.gap * 2, c.sizes.gap)
                    addView(title); addView(detail)
                    background = c.roundedBackground(c.palette.surface)
                }
                Holder(root, title, detail).also { root.tag = it }
            }
            val snap = snapshot
            val rows = snap?.forumTopics.orEmpty()
            val row = rows.getOrNull(position - 1)
            val label = when {
                position == 0 -> "Tất cả nhạc của nhóm  ›"
                row != null -> row.title + "  ›"
                position == rows.size + 1 && snap?.forumCanLoadMore == true -> "Tìm thêm chủ đề"
                else -> "Làm mới chủ đề"
            }
            val detail = when {
                position == 0 -> snap?.total?.let { "$it bài đã lập chỉ mục" }.orEmpty()
                row != null -> row.detail
                snap?.forumLoading == true -> "Đang đọc Telegram…"
                else -> ""
            }
            holder.title.text = label
            holder.detail.text = detail
            holder.detail.visibility = if (detail.isBlank()) View.GONE else View.VISIBLE
            holder.root.contentDescription = "$label, $detail"
            holder.root.alpha = if (isEnabled(position)) 1f else 0.6f
            return holder.root
        }
    }

    init {
        view.adapter = adapter
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) { pendingAnchor = null; restoring = false }
            false
        }
        view.setOnItemClickListener { _, _, position, _ ->
            savePosition()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(view.windowToken, 0)
            val snap = snapshot ?: return@setOnItemClickListener
            val rows = snap.forumTopics.orEmpty()
            when {
                position == 0 -> host.openAllForumMusic()
                position <= rows.size -> rows.getOrNull(position - 1)?.let { host.openForumTopic(it.id) }
                position == rows.size + 1 && snap.forumCanLoadMore -> host.loadMoreForum()
                else -> host.refreshForum()
            }
        }
    }

    fun savePosition() {
        if (!restoring && view.visibility == View.VISIBLE) host.saveTopicPosition(id(view.firstVisiblePosition), view.getChildAt(0)?.top ?: 0)
    }

    fun render(snap: MeloScreenHost.Snapshot) {
        val nextKey = Triple(snap.browseOwnerId, snap.browseScope, snap.query)
        val same = nextKey == key
        if (!same) {
            key = nextKey
            pendingAnchor = host.browseContext()?.let { it.topicAnchorId?.let { id -> id to it.topicOffsetPx } }
        } else if (!restoring) pendingAnchor = id(view.firstVisiblePosition)?.let { it to (view.getChildAt(0)?.top ?: 0) }
        snapshot = snap
        adapter.notifyDataSetChanged()
        view.visibility = View.VISIBLE
        val target = pendingAnchor ?: (0 to 0)
        val at = if (target.first == 0) 0 else snap.forumTopics.orEmpty().indexOfFirst { it.id == target.first }.takeIf { it >= 0 }?.plus(1)
        restoring = true
        if (at != null) view.post {
            if (key == nextKey) {
                view.setSelectionFromTop(at, target.second)
                restoring = false
                pendingAnchor = null
            }
        }
    }
}
