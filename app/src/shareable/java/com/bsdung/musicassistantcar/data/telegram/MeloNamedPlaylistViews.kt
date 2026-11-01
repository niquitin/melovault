package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.IndexRow
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.RowKind

/**
 * Nhiều Playlist có tên, S14: the level-1 view of the Playlist tab (the index) and the host contract the library screens use. The view
 * draws only what [MeloNamedPlaylistPolicy.indexRows] says; a tap is reported, nothing is decided here. Android Views, no Compose.
 */
internal interface MeloNamedPlaylistHost {
    /** Which screen of the Playlist tab is shown. */
    fun screen(): MeloNamedPlaylistPolicy.Screen

    /** A short sentence when the playlists are not usable (still opening, or unreadable); null when they are. */
    fun statusNote(): String?

    fun indexRows(): List<IndexRow>

    /** The group title of level 2 ("Nhạc đi xe · 4 bài" / "Yêu thích"); null on the index. */
    fun headerTitle(): String?

    /** A tap on an index row. */
    fun open(row: IndexRow)

    /** "‹ Playlist": level 2 back to the index. */
    fun back()

    /** Gọn: the index as a list in a box (that screen has no room for an inline index). */
    fun openIndexDialog()

    /** The ⋮ of a playlist: rename, sort, delete. */
    fun openMenu(anchor: View)

    /** "▶ Phát" ([shuffle] false) and "⤨ Phát ngẫu nhiên" for the playlist on screen. */
    fun play(shuffle: Boolean)

    /** "Lưu hàng chờ thành Playlist" from the queue screen. */
    fun saveQueue()
}

internal object MeloNamedPlaylistViews {
    /** The index as a scrolling column of rows, each at least one touch target high. */
    fun indexView(
        context: Context, c: MeloComponents, note: String?, rows: List<IndexRow>, onRow: (IndexRow) -> Unit,
    ): ScrollView {
        val s = c.sizes
        val p = c.palette
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, s.gap / 2, 0, s.gap)
        }
        if (!note.isNullOrBlank()) {
            column.addView(c.text(note, s.secondaryText, p.textSecondary, maxLines = 4).apply { setPadding(0, 0, 0, s.gap) })
        }
        for (row in rows) {
            val label = c.text(row.title, s.titleText, if (row.enabled) p.text else p.textSecondary, bold = row.kind != RowKind.NAMED).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = maxOf(s.minTouch, s.rowHeight)
                setPadding(s.gap, s.gap / 2, s.gap, s.gap / 2)
                background = c.roundedBackground(if (row.kind == RowKind.CREATE) p.surfaceRaised else p.surface)
                isClickable = row.enabled
                isFocusable = row.enabled
                contentDescription = row.description
                alpha = if (row.enabled) 1f else 0.6f
                if (row.enabled) setOnClickListener { onRow(row) }
                addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            column.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = s.gap / 2
            })
        }
        return ScrollView(context).apply { addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
    }

    /** A plain label for a dialog list row that needs no styling. */
    fun plain(context: Context, c: MeloComponents, text: String): TextView =
        c.text(text, c.sizes.titleText, c.palette.text, maxLines = 2).apply { setPadding(c.sizes.gap, c.sizes.gap, c.sizes.gap, c.sizes.gap) }

}
