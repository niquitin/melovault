package com.bsdung.musicassistantcar.data.telegram

import java.util.Locale
import java.util.UUID

/** Launcher state and command rules. No Android, file access or persisted playback state. */
internal object MeloWidgetPolicy {
    const val EXTRA_PERFORMER = "app.melovault.widget.trusted_performer"
    const val EXTRA_OPEN_PLAYER = "app.melovault.widget.open_player"
    const val MAX_ART_SIDE = 144
    const val NEXT_FENCE_MS = 2_000L

    enum class Playback { IDLE, BUFFERING, READY, ENDED }
    enum class Center { NONE, PLAY, PAUSE, REPLAY }
    enum class Open { LIBRARY, PLAYER }
    enum class Action { PLAY, PAUSE, PREVIOUS_ITEM, NEXT_ITEM }

    data class Input(
        val hasItem: Boolean = false,
        val ownerValid: Boolean = true,
        val title: String? = null,
        val performer: String? = null,
        val playback: Playback = Playback.IDLE,
        val playWhenReady: Boolean = false,
        val hasError: Boolean = false,
        val canPlayPause: Boolean = false,
        val canSeekStart: Boolean = false,
        val hasPrevious: Boolean = false,
        val hasNext: Boolean = false,
        val canPrevious: Boolean = false,
        val canNext: Boolean = false,
    )

    data class View(
        val title: String,
        val performer: String,
        val center: Center,
        val previous: Boolean,
        val next: Boolean,
        val open: Open,
    )

    fun trustedPerformer(raw: String?): String? = raw?.trim()?.takeIf {
        it.isNotEmpty() && it.lowercase(Locale.ROOT) !in setOf(
            "<unknown>", "unknown", "nghệ sĩ không rõ", "chưa rõ ca sĩ")
    }

    fun view(input: Input): View {
        if (!input.hasItem || !input.ownerValid || input.hasError || input.playback == Playback.IDLE) {
            return View("MeloVault", "Mở ứng dụng để chọn bài", Center.NONE, false, false, Open.LIBRARY)
        }
        val center = when {
            !input.canPlayPause -> Center.NONE
            input.playback == Playback.ENDED -> if (input.canSeekStart) Center.REPLAY else Center.NONE
            input.playWhenReady -> Center.PAUSE
            else -> Center.PLAY
        }
        return View(input.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Bài hát chưa có tên",
            trustedPerformer(input.performer) ?: "Chưa rõ ca sĩ", center,
            input.hasPrevious && input.canPrevious, input.hasNext && input.canNext, Open.PLAYER)
    }

    /** Re-evaluated at receipt: delayed PAUSE can never turn into PLAY. */
    fun allows(action: Action, input: Input): Boolean {
        val state = view(input)
        return when (action) {
            Action.PLAY -> state.center == Center.PLAY || state.center == Center.REPLAY
            Action.PAUSE -> state.center == Center.PAUSE
            Action.PREVIOUS_ITEM -> state.previous
            Action.NEXT_ITEM -> state.next
        }
    }

    /** Only opaque tokens leave the process; neither keys nor metadata are intent identity. */
    fun intentIdentity(widgetId: Int, action: Action, token: String): String =
        "melovault-widget://control/$widgetId/${action.name}/$token"

    /**
     * A Telegram song chosen before the service existed may start only for the owner who chose it,
     * and only once the service queue holds it. Otherwise it would play without an owner or queue.
     */
    fun mayStartPendingTelegram(chosenOwner: Long, currentOwner: Long?, serviceQueue: List<Long>, songId: Long): Boolean =
        chosenOwner == currentOwner && songId in serviceQueue

    class Generation(private val newToken: () -> String = { UUID.randomUUID().toString() }) {
        private var key: Any? = null
        private var fenceKey: Any? = null
        var token: String = newToken()
            private set
        private var nextAt: Long? = null

        /**
         * [currentKey] is everything on screen: any change rotates the token, which rejects late
         * artwork and clicks from the old render. [currentFenceKey] is only the song and its owner:
         * new metadata for the same song must not reopen a Next press that is still in flight.
         */
        fun update(currentKey: Any?, currentFenceKey: Any? = currentKey): Boolean {
            if (key == currentKey && fenceKey == currentFenceKey) return false
            if (fenceKey != currentFenceKey) nextAt = null
            key = currentKey
            fenceKey = currentFenceKey
            token = newToken()
            return true
        }

        fun invalidate() {
            token = newToken()
            nextAt = null
        }

        fun accepts(candidate: String): Boolean = token == candidate

        fun takeNext(candidate: String, now: Long): Boolean {
            if (!accepts(candidate)) return false
            val previous = nextAt
            if (previous != null && now - previous < NEXT_FENCE_MS) return false
            nextAt = now
            return true
        }

        fun clearNext() { nextAt = null }
    }

    /** A new id/resize must receive a full RemoteViews even when display values did not change. */
    class RenderGate {
        private var last: Any? = null
        fun needsRender(value: Any, force: Boolean): Boolean {
            if (!force && last == value) return false
            last = value
            return true
        }
    }
}
