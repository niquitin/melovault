package com.bsdung.musicassistantcar.data.telegram

/** Window-dependent chrome only: playback and Telegram state are never resized/restarted. */
internal object CarPaneLayoutPolicy {
    fun compactFor(widthDp: Int, heightDp: Int): Boolean =
        widthDp in 1..499 && heightDp in 1..739
}
