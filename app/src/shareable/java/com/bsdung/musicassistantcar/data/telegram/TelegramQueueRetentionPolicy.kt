package com.bsdung.musicassistantcar.data.telegram

/** Keep the source's current logical queue through car pane resizing and scans. */
internal object TelegramQueueRetentionPolicy {
    fun <T> forSelected(
        visible: List<T>, previous: List<T>, selectedId: Long,
        id: (T) -> Long
    ): List<T> = when {
        visible.any { id(it) == selectedId } -> visible.toList()
        previous.any { id(it) == selectedId } -> previous.toList()
        else -> emptyList()
    }
}
