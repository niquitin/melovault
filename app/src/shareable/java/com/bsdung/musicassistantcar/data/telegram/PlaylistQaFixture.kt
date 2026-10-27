package com.bsdung.musicassistantcar.data.telegram

/**
 * E96 P5B3 FINAL LIVE QA: the ONE synthetic Playlist item used to prove a real two-device sync, so that no song of the person's
 * library is named in the cloud log.
 *
 * It exists only in a Sync-QA debug build ([TelegramPlaylistFlags.SYNC_QA_ENABLED]); a standard build and Release ignore it. A step
 * is requested by an empty marker file in the app's CACHE directory (put there with `run-as` for a debuggable build). The marker
 * is consumed on the next resume, so one marker is at most one Playlist change, and each step ([Step.ADD], [Step.REMOVE]) can run
 * once per install ([used]); a request for a step that has already run is refused, which is the per-device send budget.
 *
 * The item names a chat and a message that exist nowhere (a chat id no account has, a message id nobody sent), so a library
 * draws it as the "saved song not in this library yet" placeholder and nothing about any real song is revealed. The change goes
 * through the same Playlist session as a tap on Save: it only queues ONE outbox operation. Sending stays behind consent (c) and
 * the per-run send grant on the Settings screen; this class never sends and never touches the consent.
 */
internal object PlaylistQaFixture {
    const val ADD_MARKER = "qa_fixture_add_track.flag"
    const val REMOVE_MARKER = "qa_fixture_remove_track.flag"

    /** Cache file listing the steps already run on this install (written BEFORE the change, so a crash cannot allow a second one). */
    const val USED_FILE = "qa_fixture_used.txt"

    /** A supergroup-style chat id no real account has. */
    const val CHAT_ID = -1_009_999_990_001L

    /** A server-style message id (a multiple of 2^20) nobody sent. */
    const val MESSAGE_ID = 1_048_576L * 9001L

    enum class Step { ADD, REMOVE }

    /** The playlist target of the synthetic item for the signed-in [account]. */
    fun target(account: TelegramUserId): PlaylistTarget {
        val key = TelegramSongKey.forMessage(CHAT_ID, MESSAGE_ID, account.value)
        return PlaylistTarget(key, CHAT_ID, MESSAGE_ID)
    }

    /**
     * What to do on this resume. null = nothing. Both markers at once, a build that is not Sync-QA, a step already used, an ADD of
     * an item already saved and a REMOVE of an item that is not saved all answer null (the caller still consumes the markers).
     */
    fun decide(enabled: Boolean, addMarker: Boolean, removeMarker: Boolean, saved: List<Long>, key: Long, used: Set<Step>): Step? {
        if (!enabled || addMarker == removeMarker) return null
        return when {
            addMarker && Step.ADD !in used && key !in saved -> Step.ADD
            removeMarker && Step.REMOVE !in used && key in saved -> Step.REMOVE
            else -> null
        }
    }
}
