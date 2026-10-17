package com.bsdung.musicassistantcar.data.telegram

import java.io.File
import java.util.Locale

/** Read-only discovery. A missing local message is unknown, never proof that no file exists. */
internal object TelegramOfflineLibrary {
    sealed class Local {
        data class Complete(val path: String, val size: Long) : Local()
        data object NotDownloaded : Local()
        data object Unknown : Local()
    }
    data class Entry(val song: IndexedTelegramSong, val path: String)
    data class Snapshot(val entries: List<Entry>, val checked: Int, val unknown: Int, val finished: Boolean)
    data class Group(val entries: List<Entry>) {
        val sameFile: Boolean get() = entries.map { it.path }.distinct().size == 1
        val label: String get() = if (sameFile) "Có tại ${entries.size} nguồn/bản" else "Các bản cùng tên · ${entries.size} bản"
        fun selected(accountId: Long, chatId: Long, messageId: Long): Entry? = entries.firstOrNull {
            it.song.accountId == accountId && it.song.chatId == chatId && it.song.messageId == messageId
        }
    }

    fun verifiedPath(root: File, local: Local): String? {
        if (local !is Local.Complete || local.size <= 0L) return null
        return runCatching {
            val file = File(local.path).canonicalFile
            file.takeIf { TelegramAccountStoragePolicy.isOwnedFile(root, it) && it.isFile &&
                it.canRead() && it.length() == local.size }?.path
        }.getOrNull()
    }

    /** Bounded pages and local-only resolver batches; callbacks execute on the caller's worker. */
    fun scan(
        accountId: Long, root: File,
        page: (Int, Int) -> List<IndexedTelegramSong>,
        resolve: (List<IndexedTelegramSong>) -> List<Local>,
        cancelled: () -> Boolean,
        progress: (Snapshot) -> Unit,
        pageSize: Int = 200, batchSize: Int = 32
    ): Snapshot? {
        require(pageSize > 0 && batchSize > 0)
        val found = linkedMapOf<Pair<Long, Long>, Entry>()
        var offset = 0
        var checked = 0
        var unknown = 0
        while (!cancelled()) {
            val rows = page(offset, pageSize)
            if (rows.isEmpty()) return Snapshot(found.values.toList(), checked, unknown, true)
            for (batch in rows.chunked(batchSize)) {
                if (cancelled()) return null
                val owned = batch.filter { it.accountId == accountId && !it.isDeleted }
                val locals = resolve(owned)
                if (cancelled()) return null
                owned.forEachIndexed { index, song ->
                    val local = locals.getOrNull(index) ?: Local.Unknown
                    val path = verifiedPath(root, local)
                    if (path != null) found[song.chatId to song.messageId] = Entry(song, path)
                    if (local == Local.Unknown || (local is Local.Complete && path == null)) unknown++
                    checked++
                }
            }
            offset += rows.size
            progress(Snapshot(found.values.toList(), checked, unknown, false))
        }
        return null
    }

    /** Same path proves identity; a shared title only creates an explicitly ambiguous expandable group. */
    fun groups(entries: List<Entry>): List<Group> {
        val groups = mutableListOf<MutableList<Entry>>()
        val pathGroup = mutableMapOf<Pair<Long, String>, MutableList<Entry>>()
        val titleGroup = mutableMapOf<Pair<Long, String>, MutableList<Entry>>()
        entries.forEach { entry ->
            val title = entry.song.title.trim().lowercase(Locale.ROOT)
            val owner = entry.song.accountId
            val byPath = pathGroup[owner to entry.path]
            val byTitle = title.takeIf { it.isNotEmpty() }?.let { titleGroup[owner to it] }
            val group = byPath ?: byTitle ?: mutableListOf<Entry>().also { groups.add(it) }
            if (byPath != null && byTitle != null && byPath !== byTitle) {
                group.addAll(byTitle)
                groups.removeAll { it === byTitle }
                pathGroup.entries.forEach { if (it.value === byTitle) it.setValue(group) }
                titleGroup.entries.forEach { if (it.value === byTitle) it.setValue(group) }
            }
            group.add(entry)
            pathGroup[owner to entry.path] = group
            if (title.isNotEmpty()) titleGroup[owner to title] = group
        }
        return groups.map { Group(it.toList()) }
    }
}
