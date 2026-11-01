package com.bsdung.musicassistantcar.data.telegram

/**
 * Nhiều Playlist có tên: the code-level rollback switch of plan section 8. `false` hides every screen of the named playlists, never opens
 * their session (so the sync finds nothing to run) and leaves their file untouched; nothing is deleted and no version goes back.
 *
 * It lives here and not next to `TelegramPlaylistFlags.V2_ENABLED`, because that object is in `TelegramPlaylistV2Session.kt`, a file this
 * task is not allowed to change. Moving it there later is a one-line change that needs the project owner's word.
 */
internal object NamedPlaylistFlags {
    const val NAMED_ENABLED = true
}
