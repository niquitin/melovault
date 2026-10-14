package com.bsdung.musicassistantcar.data.telegram

/**
 * Sequential session generation prevents stale, asynchronous TDLib callbacks
 * from a previous Telegram account from authorizing the next account.
 *
 * A session becomes READY only after the native client reports authorized and
 * getMe() returns its numeric Telegram user ID; never use a phone input here.
 */
class TelegramSessionEpoch(
    private val library: TelegramAccountLibrary,
) {
    private var generation: Long = 0L
    private var active: Boolean = false

    fun beginNewClient(): Long {
        library.logout()
        generation = Math.addExact(generation, 1L)
        active = true
        return generation
    }

    /** Returns false for events from already closed/replaced TDLib clients. */
    fun onAuthenticatedGetMe(epoch: Long, authenticatedId: TelegramUserId): Boolean {
        if (!active || epoch != generation) return false
        library.onAuthenticatedUser(authenticatedId)
        return true
    }

    fun closeClient(epoch: Long): Boolean {
        if (!active || epoch != generation) return false
        library.logout()
        active = false
        return true
    }

    fun isReady(epoch: Long): Boolean =
        active && epoch == generation && library.authorizedUser() != null
}
