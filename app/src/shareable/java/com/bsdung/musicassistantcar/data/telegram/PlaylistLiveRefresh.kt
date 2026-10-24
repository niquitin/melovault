package com.bsdung.musicassistantcar.data.telegram

import java.util.concurrent.atomic.AtomicLong

/**
 * E96 P5B3 REWORK-03: keeps the Playlist shown on screen equal to the committed saved list when the screen comes back
 * (after Settings > Đồng bộ Playlist, or from the background) - without restarting the app.
 *
 * The saved list can change behind the screen's back: a sync run in the Settings screen applies other devices' changes to the
 * V2 state. The screen only read that list once, at start. This class re-reads it, and nothing else:
 *  - [submit] is the playlist thread, so the read waits behind any local Save that is already queued (it never sees half a Save);
 *  - the read is [PlaylistSessionApi.snapshotFor]: memory only, no file, no TDLib, no network, no queue is touched;
 *  - [toUi] is the main thread; the result is applied only if the screen is still alive, the SAME account is still signed in,
 *    and no newer request or [invalidate] came after it (an older answer never overwrites a newer one);
 *  - an identical list is not applied, so scroll position and search text survive a plain resume.
 * Downloaded/playing state is not read here and not changed: only the list of saved songs is handed to [apply].
 */
internal class PlaylistLiveRefresh(
    private val session: PlaylistSessionApi,
    private val submit: (() -> Unit) -> Unit,
    private val toUi: (() -> Unit) -> Unit,
    private val alive: () -> Boolean,
    private val currentAccount: () -> TelegramUserId?,
    private val shown: () -> List<Long>,
    private val apply: (List<Long>) -> Unit,
    /** Sanitised evidence for the log: how many songs the committed list has (null = no readable list) and whether the screen had to change. */
    private val onResult: (count: Int?, changed: Boolean) -> Unit = { _, _ -> },
    /** Runs on the playlist thread on what was read; identity in production. QA builds use it for the display-only simulation. */
    private val transform: (List<Long>?) -> List<Long>? = { it },
) {
    private val generation = AtomicLong()

    /** Account change, sign-out or close: whatever is in flight must not be applied. */
    fun invalidate() {
        generation.incrementAndGet()
    }

    /** Call from the main thread. */
    fun requestRefresh(account: TelegramUserId) {
        val mine = generation.incrementAndGet()
        submit {
            val ids = transform(session.snapshotFor(account))
            toUi {
                if (!alive() || generation.get() != mine || currentAccount() != account) return@toUi
                if (ids == null || ids == shown()) {
                    onResult(ids?.size, false)
                    return@toUi
                }
                apply(ids)
                onResult(ids.size, true)
            }
        }
    }
}
