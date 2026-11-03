package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Event-driven adapter for the existing sessionPlayer. All state lives on its application looper. */
internal class MeloWidgetSession(
    context: Context,
    private val player: Player,
    private val telegramOwner: () -> Owner?,
    private val itemAuthorized: (MediaItem) -> Boolean,
) : Player.Listener {
    internal data class Owner(val account: Long, val epoch: Long, val root: File)
    private data class Key(val item: MediaItem, val metadata: MediaMetadata, val owner: Owner?)
    private data class Render(val view: MeloWidgetPolicy.View, val token: String, val cover: Bitmap?)
    private val context = context.applicationContext
    private val main = Handler(player.applicationLooper)
    private val generation = MeloWidgetPolicy.Generation()
    private var renderGate = MeloWidgetPolicy.RenderGate()
    private var active = false
    private var closed = false
    private var scheduled = false
    private var forceNext = false
    private var cover: Bitmap? = null
    private var executor: ThreadPoolExecutor? = null
    private var artJob: Future<*>? = null
    private val update = Runnable {
        scheduled = false
        val force = forceNext
        forceNext = false
        refreshNow(force)
    }

    init { player.addListener(this); refresh(force = true) }

    override fun onEvents(player: Player, events: Player.Events) = refresh()
    // NextAwarePlayer.refreshCommands() may notify this without an ExoPlayer onEvents callback.
    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) = refresh()
    override fun onPlayerError(error: PlaybackException) { generation.clearNext(); refresh() }

    fun refresh(force: Boolean = false) {
        if (closed || (!force && !active)) return
        forceNext = forceNext || force
        if (!scheduled) { scheduled = true; main.post(update) }
    }

    private fun input(): MeloWidgetPolicy.Input {
        val item = player.currentMediaItem
        val metadata = player.mediaMetadata
        return MeloWidgetPolicy.Input(
            hasItem = item != null,
            ownerValid = item != null && itemAuthorized(item),
            title = metadata.title?.toString(),
            performer = metadata.extras?.getString(MeloWidgetPolicy.EXTRA_PERFORMER)
                ?: item?.mediaMetadata?.extras?.getString(MeloWidgetPolicy.EXTRA_PERFORMER),
            playback = when (player.playbackState) {
                Player.STATE_READY -> MeloWidgetPolicy.Playback.READY
                Player.STATE_BUFFERING -> MeloWidgetPolicy.Playback.BUFFERING
                Player.STATE_ENDED -> MeloWidgetPolicy.Playback.ENDED
                else -> MeloWidgetPolicy.Playback.IDLE
            },
            playWhenReady = player.playWhenReady,
            hasError = player.playerError != null,
            canPlayPause = player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
            canSeekStart = player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) && player.isCurrentMediaItemSeekable,
            hasPrevious = player.hasPreviousMediaItem(), hasNext = player.hasNextMediaItem(),
            canPrevious = player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
            canNext = player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
        )
    }

    fun canOpenPlayer(): Boolean = !closed && MeloWidgetPolicy.view(input()).open == MeloWidgetPolicy.Open.PLAYER

    /** Also used synchronously on owner revocation, before releasing/clearing playback state. */
    fun refreshNow(force: Boolean = false) {
        if (closed) return
        if (MeloNowPlayingWidgetProvider.widgetIds(context).isEmpty()) {
            if (active) {
                generation.update(null)
                generation.invalidate()
                releaseArtwork()
                renderGate = MeloWidgetPolicy.RenderGate()
            }
            active = false
            return
        }
        active = true
        val state = MeloWidgetPolicy.view(input())
        val item = player.currentMediaItem
        val key = if (state.open == MeloWidgetPolicy.Open.PLAYER && item != null)
            Key(item, player.mediaMetadata, if (item.mediaId.startsWith("telegram:")) telegramOwner() else null) else null
        val changed = generation.update(key)
        if (changed) cancelArtwork()
        val value = Render(state, generation.token, cover)
        if (renderGate.needsRender(value, force || changed)) {
            MeloNowPlayingWidgetProvider.render(context, state, generation.token, cover)
        }
        // Placeholder has already been delivered before work on the new cover starts.
        if (changed && key != null) loadArtwork(key, generation.token)
    }

    fun dispatch(action: MeloWidgetPolicy.Action, token: String) {
        if (closed) return
        refreshNow()
        if (!active || !generation.accepts(token)) return
        val current = input()
        if (!MeloWidgetPolicy.allows(action, current)) return
        when (action) {
            MeloWidgetPolicy.Action.PLAY -> {
                if (current.playback == MeloWidgetPolicy.Playback.ENDED) player.seekTo(0)
                player.play()
            }
            MeloWidgetPolicy.Action.PAUSE -> player.pause()
            MeloWidgetPolicy.Action.PREVIOUS_ITEM -> player.seekToPreviousMediaItem()
            MeloWidgetPolicy.Action.NEXT_ITEM -> {
                if (generation.takeNext(token, SystemClock.elapsedRealtime())) player.seekToNextMediaItem()
            }
        }
        refresh()
    }

    private fun loadArtwork(key: Key, token: String) {
        val pool = executor ?: ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(), { task -> Thread(task, "MeloWidgetArt").apply {
                priority = Thread.MIN_PRIORITY; isDaemon = true
            } }).also { executor = it }
        artJob = pool.submit {
            val bitmap = MeloWidgetArtwork.load(context, key.item, key.metadata, key.owner)
            main.post {
                if (closed || !active || !generation.accepts(token) || !itemAuthorized(key.item) ||
                    (key.owner != null && telegramOwner() != key.owner)) {
                    bitmap?.recycle()
                    return@post
                }
                artJob = null
                cover = bitmap
                refresh(force = true)
            }
        }
    }

    private fun cancelArtwork() {
        artJob?.cancel(true)
        artJob = null
        executor?.queue?.clear()
        cover = null
    }

    private fun releaseArtwork() {
        cancelArtwork()
        executor?.shutdownNow()
        executor = null
    }

    fun close() {
        if (closed) return
        closed = true
        generation.invalidate()
        main.removeCallbacks(update)
        player.removeListener(this)
        releaseArtwork()
        renderGate = MeloWidgetPolicy.RenderGate()
        MeloNowPlayingWidgetProvider.renderEmpty(context)
    }
}
