package com.bsdung.musicassistantcar.data.telegram

/**
 * E96 P5B3 REWORK-04: a DISPLAY-ONLY way to see, on a real device, what the screen does when the committed saved list changes
 * behind it - without a Telegram write and without touching the person's playlist.
 *
 * It exists only in a Sync-QA debug build ([TelegramPlaylistFlags.SYNC_QA_ENABLED]); a standard build and Release ignore it. It is
 * switched on by an empty marker file in the app's CACHE directory (put there with `run-as` for a debuggable build and removed
 * again afterwards). While the marker exists, [apply] adds ONE extra, unknown song key to the list that the live refresh reads,
 * exactly as if another device had saved a song this device's library does not know yet. Nothing is written anywhere: not the V2
 * state, not the outbox, not the V1 file, not Telegram. Remove the marker and the next resume shows the real list again.
 */
internal object PlaylistQaSimulation {
    const val MARKER_FILE = "qa_sim_remote_saved.flag"

    /** A key no library contains, so the row is drawn as the "saved song not in this library yet" placeholder. */
    const val SIMULATED_KEY = 0x5151_0001L

    /** [enabled] = a Sync-QA build; [markerPresent] = the marker file exists. A null list (no readable playlist) stays null. */
    fun apply(ids: List<Long>?, enabled: Boolean, markerPresent: Boolean): List<Long>? {
        if (ids == null || !enabled || !markerPresent) return ids
        return if (SIMULATED_KEY in ids) ids else ids + SIMULATED_KEY
    }
}
