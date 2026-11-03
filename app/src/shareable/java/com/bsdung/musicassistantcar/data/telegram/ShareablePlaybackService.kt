package com.bsdung.musicassistantcar.data.telegram

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.bsdung.musicassistantcar.BuildConfig
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference

/**
 * Owns Media3 playback and bounded TDLib prefetch after the UI detaches.
 * Only the user authorized by an actual TDLib getMe() may supply tracks.
 * No Private backend or Telegram bot is used.
 */
class ShareablePlaybackService : MediaSessionService() {
    internal data class QueueTrack(
        val messageId: Long,
        val fileId: Int,
        val title: String,
        val cachedPath: String?,
        val filename: String = "",
        val chatId: Long = 0L,
        val expectedSize: Long = -1L,
        val rawMessageId: Long = 0L,
        val artist: String? = null,
        val performer: String? = null,
    ) {
        val genuineMessageId: Long get() = if (rawMessageId > 0L) rawMessageId else messageId
    }

    companion object {
        private var serviceRef: WeakReference<ShareablePlaybackService>? = null

        /** Widget never starts/binds this service. All entry points run on the existing player looper. */
        internal fun refreshWidget(): Boolean {
            if (Looper.myLooper() != Looper.getMainLooper()) return false
            val widget = serviceRef?.get()?.widgetSession ?: return false
            widget.refresh(force = true)
            return true
        }

        internal fun dispatchWidget(action: MeloWidgetPolicy.Action, token: String): Boolean {
            if (Looper.myLooper() != Looper.getMainLooper()) return false
            val widget = serviceRef?.get()?.widgetSession ?: return false
            widget.dispatch(action, token)
            return true
        }

        internal fun widgetCanOpenPlayer(): Boolean = Looper.myLooper() == Looper.getMainLooper() &&
            serviceRef?.get()?.widgetSession?.canOpenPlayer() == true

        // Internal same-process API; never expose these details via an Intent.
        internal fun configureTelegramQueue(
            accountId: Long, ownerRoot: File, ordered: List<QueueTrack>, anchorMessageId: Long
        ) {
            serviceRef?.get()?.setQueue(accountId, ownerRoot, ordered, anchorMessageId)
        }

        internal fun clearTelegramQueue() {
            serviceRef?.get()?.clearQueue()
        }

        /** "Phát tiếp theo": right after the playing song in both the source order and the play order. */
        internal fun queueTelegramNext(accountId: Long, track: QueueTrack) {
            serviceRef?.get()?.queueNext(accountId, track)
        }

        /** Local music: source media ids in, play order out (anchor first and the rest permuted while Shuffle is on). */
        internal fun orderLocalQueue(baseIds: List<String>, anchorId: String): List<String> =
            serviceRef?.get()?.orderLocal(baseIds, anchorId) ?: baseIds

        /** Main-thread, authorized snapshot of the source order (Shuffle off); [currentTelegramQueue] is the play order. */
        internal fun currentTelegramBase(accountId: Long): List<QueueTrack> {
            if (accountId <= 0L ||
                ShareableTdlibOwner.authorizedAccount() != accountId ||
                Looper.myLooper() != Looper.getMainLooper()) return emptyList()
            val service = serviceRef?.get() ?: return emptyList()
            if (service.expectedAccount != accountId) return emptyList()
            return service.telegramBase.toList()
        }

        /** Main-thread, authorized snapshot of the play order; never exposes another account's queue. */
        internal fun currentTelegramQueue(accountId: Long): List<QueueTrack> {
            if (accountId <= 0L ||
                ShareableTdlibOwner.authorizedAccount() != accountId ||
                Looper.myLooper() != Looper.getMainLooper()) return emptyList()
            val service = serviceRef?.get() ?: return emptyList()
            if (service.expectedAccount != accountId) return emptyList()
            return service.queueTracks.toList()
        }

        internal fun onPreviewDetached() {
            serviceRef?.get()?.requestNextIfMissing()
        }

        /** Studio Deck "Hẹn giờ tắt"; runs here so it still fires with the screen off. Main thread. */
        internal fun setSleepTimer(option: MeloNowPlayingPolicy.SleepOption) {
            serviceRef?.get()?.applySleepTimer(option)
        }

        /** (deadline in elapsedRealtime millis or null, stop at end of the current track). */
        internal fun sleepTimerState(): Pair<Long?, Boolean> {
            val service = serviceRef?.get() ?: return null to false
            return service.sleepDeadline to service.sleepAtEndOfTrack
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var expectedAccount: Long? = null
    private var allowedRoot: File? = null
    /**
     * Play order. Media3 never shuffles: its flag is only on/off ([ShuffleOrder.UnshuffledShuffleOrder]),
     * this service decides which song is next. Identical to [telegramBase] while Shuffle is off.
     */
    private var queueTracks = emptyList<QueueTrack>()
    private var telegramBase = emptyList<QueueTrack>()
    private var shuffleSeed = 0L
    private var localBaseIds = emptyList<String>()
    private var reorderingLocal = false
    private var pendingDownloadId: Int? = null
    private var pendingMessageId: Long? = null
    private var pendingAttempt = 0
    private var pendingRequestTag: String? = null
    private var queueGeneration = 0L
    private var sessionPlayer: NextAwarePlayer? = null
    private var widgetSession: MeloWidgetSession? = null
    private var widgetAwaitingOwnerItem = false

    private fun widgetOwner(): MeloWidgetSession.Owner? {
        val account = expectedAccount ?: return null
        if (account != ShareableTdlibOwner.authorizedAccount()) return null
        return allowedRoot?.let { MeloWidgetSession.Owner(account, ShareableTdlibOwner.authEpoch(), it) }
    }

    private fun widgetItemAuthorized(item: MediaItem): Boolean = when {
        item.mediaId.startsWith("telegram:") -> !widgetAwaitingOwnerItem && widgetOwner() != null &&
            queueTracks.any { "telegram:${it.messageId}" == item.mediaId }
        else -> MeloLocalSourcePolicy.idOfMediaId(item.mediaId) != null || item.mediaId == "picked:audio"
    }
    /** Message id of the song a user press on Next asked for; lifts the prefetch gates for it only. */
    private var userNextMessageId: Long? = null
    private var sleepDeadline: Long? = null
    private var sleepAtEndOfTrack = false

    /**
     * The player the MediaSession (lock screen, notification, headset, Bluetooth, steering
     * wheel, physical keys) talks to. ExoPlayer only skips to songs already in its list, so a
     * Next press with the following song still in Telegram did nothing and the controller's
     * Next button was disabled. This wrapper keeps Next available and starts that song.
     */
    private inner class NextAwarePlayer(player: ExoPlayer) : ForwardingPlayer(player) {
        private val observers = mutableListOf<Player.Listener>()

        override fun addListener(listener: Player.Listener) {
            observers.add(listener)
            super.addListener(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            observers.remove(listener)
            super.removeListener(listener)
        }

        private fun queuedNext(): Boolean = nextTrackNotLoaded() != null

        override fun getAvailableCommands(): Player.Commands {
            val base = super.getAvailableCommands()
            if (base.contains(Player.COMMAND_SEEK_TO_NEXT) || !queuedNext()) return base
            return base.buildUpon()
                .addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean =
            availableCommands.contains(command)

        override fun hasNextMediaItem(): Boolean =
            MeloMediaControlPolicy.nextAvailable(super.hasNextMediaItem(), queuedNext())

        override fun seekToNext() = seekToNextMediaItem()

        override fun seekToNextMediaItem() {
            if (super.hasNextMediaItem()) super.seekToNextMediaItem() else playNextNow()
        }

        /** The queue or the current song changed: tell controllers whether Next is available. */
        fun refreshCommands() {
            val commands = availableCommands
            observers.toList().forEach { it.onAvailableCommandsChanged(commands) }
        }
    }

    private fun mayDownload(next: QueueTrack): Boolean = MeloMediaControlPolicy.mayDownloadNext(
        userNextMessageId == next.messageId, prefetchAllowed(), ShareableTdlibOwner.uiIsAttached())

    /** The song after the current one in the queue when it is not in the player's list yet. */
    private fun nextTrackNotLoaded(): QueueTrack? {
        val next = nextTrack() ?: return null
        val player = exoPlayer ?: return null
        val loaded = (0 until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId == "telegram:${next.messageId}" }
        return if (loaded) null else next
    }

    /**
     * Play the next queued song right now (Next key, or the track ended with nothing loaded):
     * cached file -> insert and skip; otherwise resolve it, stream it progressively like the
     * library screen does, and skip as soon as it is queued.
     */
    private fun playNextNow() {
        val player = exoPlayer ?: return
        val next = nextTrack() ?: return
        val mediaId = "telegram:${next.messageId}"
        val loadedIndex = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == mediaId }
        if (loadedIndex != null) {
            player.seekToDefaultPosition(loadedIndex)
            player.play()
            return
        }
        val root = allowedRoot ?: return
        val cached = next.cachedPath?.let { File(it) }
        if (cached != null && cached.isFile && TelegramAccountStoragePolicy.isOwnedFile(root, cached)) {
            insertNext(next, cached)
            player.seekToNextMediaItem()
            player.play()
            return
        }
        userNextMessageId = next.messageId
        clearPending()
        startDownload(next, 1)
    }
    private val sleepRunnable = Runnable {
        sleepDeadline = null
        exoPlayer?.pause()
    }

    private fun applySleepTimer(option: MeloNowPlayingPolicy.SleepOption) {
        main.removeCallbacks(sleepRunnable)
        sleepDeadline = null
        if (sleepAtEndOfTrack) {
            exoPlayer?.pauseAtEndOfMediaItems = false
            sleepAtEndOfTrack = false
        }
        val now = SystemClock.elapsedRealtime()
        val deadline = MeloNowPlayingPolicy.sleepDeadline(option, now)
        if (deadline != null) {
            sleepDeadline = deadline
            main.postDelayed(sleepRunnable, deadline - now)
        } else if (option == MeloNowPlayingPolicy.SleepOption.END_OF_TRACK) {
            exoPlayer?.pauseAtEndOfMediaItems = true
            sleepAtEndOfTrack = true
        }
    }

    /** "Tải trước bài kế" switch; when off, no new background download starts. */
    private fun prefetchAllowed(): Boolean =
        MeloNowPlayingPolicy.prefetchEnabled(getSharedPreferences(MeloNowPlayingPolicy.PLAYBACK_PREFS, MODE_PRIVATE))

    override fun onCreate() {
        super.onCreate()
        serviceRef = WeakReference(this)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(TelegramOwnerAudioDataSource.Factory(this)))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player
        // Shuffle only flips the flag: Media3 keeps the list order, this service decides the play order.
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0))
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                widgetAwaitingOwnerItem = false
                requestNextIfMissing()
                sessionPlayer?.refreshCommands()
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                // Any controller (app, Deck, lock screen, Bluetooth) lands here: one authority for the order.
                applyShuffleMode(shuffleModeEnabled)
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) keepIdentityShuffleOrder()
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // "Hết bài này" is a one-shot: after pausing at the end, normal play resumes.
                if (sleepAtEndOfTrack && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    player.pauseAtEndOfMediaItems = false
                    sleepAtEndOfTrack = false
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                // Screen off / headset: the track ended and the next one was never loaded.
                if (playbackState == Player.STATE_ENDED && MeloMediaControlPolicy.shouldContinueAfterEnd(
                        ShareableTdlibOwner.uiIsAttached(), sleepAtEndOfTrack, nextTrackNotLoaded() != null)) {
                    playNextNow()
                }
            }
        })
        sessionPlayer = NextAwarePlayer(player)
        widgetSession = MeloWidgetSession(this, sessionPlayer!!, ::widgetOwner, ::widgetItemAuthorized)

        val intent = Intent(this, TelegramPreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else 0
        val launch = PendingIntent.getActivity(this, 0, intent, flags)
        mediaSession = MediaSession.Builder(this, sessionPlayer ?: player)
            .setSessionActivity(launch)
            .build()
        ShareableTdlibOwner.bindPlayback { event ->
            // Native receive thread may not touch the Media3 player directly.
            val tag = event.optString("@extra")
            val kind = event.optString("@type")
            if (kind == "updateFile") {
                event.optJSONObject("file")?.let(TelegramProgressiveCache::observe)
            } else if (kind == "file") {
                TelegramProgressiveCache.observe(event)
            }
            if (kind == "updateAuthorizationState") {
                val state = event.optJSONObject("authorization_state")
                    ?.optString("@type").orEmpty()
                if (state in setOf("authorizationStateLoggingOut",
                        "authorizationStateClosing", "authorizationStateClosed")) {
                    main.post {
                        ShareableTdlibOwner.revokeAuthorization()
                        clearQueue()
                        TelegramProgressiveCache.clear()
                        exoPlayer?.stop()
                        exoPlayer?.clearMediaItems()
                    }
                }
            } else if ((kind == "file" && tag.startsWith("background:")) ||
                // getMessage answers with a "message"; without this the resolve step never arrived
                // and neither background prefetch nor a Next press could start the next song.
                (kind == "message" && tag.startsWith("background:resolve:")) ||
                kind == "updateFile" ||
                (kind == "error" && tag.startsWith("background:"))) {
                main.post { acceptBackgroundEvent(event) }
            }
        }
    }

    /** [ordered] is the source order; [anchorMessageId] is the song the user chose (first in the play order of a new shuffle). */
    private fun setQueue(accountId: Long, root: File, ordered: List<QueueTrack>, anchorMessageId: Long) {
        val sameOwner = expectedAccount == accountId
        val oldBaseIds = if (sameOwner) telegramBase.map { it.messageId } else emptyList()
        val oldPlayIds = if (sameOwner) queueTracks.map { it.messageId } else emptyList()
        if (Looper.myLooper() != Looper.getMainLooper()) return
        if (accountId <= 0L || ShareableTdlibOwner.authorizedAccount() != accountId) {
            clearQueue()
            return
        }
        if (ordered.size > 2000 || ordered.any { it.messageId <= 0L || it.fileId <= 0 }) {
            clearQueue()
            return
        }
        if (!sameOwner) widgetAwaitingOwnerItem = true
        clearQueue()
        expectedAccount = accountId
        allowedRoot = root.canonicalFile
        telegramBase = ordered.toList()
        shuffleSeed = nextSeed()
        val byId = ordered.associateBy { it.messageId }
        val shuffleOn = exoPlayer?.shuffleModeEnabled == true
        val playIds = MeloShuffleOrderPolicy.rebase(
            oldBaseIds, oldPlayIds, ordered.map { it.messageId },
            anchorMessageId.takeIf { it > 0L }, shuffleOn, shuffleSeed)
        queueTracks = playIds.mapNotNull { byId[it] }
        shuffleLog("configure", shuffleOn, queueTracks.size)
        sessionPlayer?.refreshCommands()
        widgetSession?.refreshNow()
    }

    private fun nextSeed(): Long = SystemClock.elapsedRealtimeNanos() xor System.nanoTime()

    /** Debug builds only, tag MeloShuffle; sizes and indexes only, never titles, ids or paths. */
    private fun shuffleLog(stage: String, on: Boolean, size: Int, extra: String = "") {
        if (!BuildConfig.DEBUG) return
        val player = exoPlayer
        Log.d("MeloShuffle", "stage=$stage on=$on size=$size cur=${player?.currentMediaItemIndex ?: -1} " +
            "next=${player?.nextMediaItemIndex ?: -1}$extra")
    }

    private fun currentTelegramId(): Long? {
        val id = exoPlayer?.currentMediaItem?.mediaId.orEmpty()
        return if (id.startsWith("telegram:")) id.removePrefix("telegram:").toLongOrNull() else null
    }

    private fun insertNextTrack(order: List<QueueTrack>, current: Long?, track: QueueTrack): List<QueueTrack> {
        val byId = order.associateBy { it.messageId } + (track.messageId to track)
        return MeloShuffleOrderPolicy.insertNext(order.map { it.messageId }, current, track.messageId)
            .mapNotNull { byId[it] }
    }

    /** "Phát tiếp theo": the user's intent wins over Shuffle, so the song goes right after the playing one. */
    private fun queueNext(accountId: Long, track: QueueTrack) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        if (accountId <= 0L || expectedAccount != accountId ||
            ShareableTdlibOwner.authorizedAccount() != accountId) return
        if (track.messageId <= 0L || track.fileId <= 0) return
        if (telegramBase.size >= 2000 && telegramBase.none { it.messageId == track.messageId }) return
        val current = currentTelegramId()
        telegramBase = insertNextTrack(telegramBase, current, track)
        queueTracks = insertNextTrack(queueTracks, current, track)
        alignTelegramWindow()
        sessionPlayer?.refreshCommands()
        requestNextIfMissing()
        shuffleLog("next_inserted", exoPlayer?.shuffleModeEnabled == true, queueTracks.size)
    }

    /**
     * Keeps only the Media3 items around the playing song that still follow the play order, so a
     * track ending (or a controller's Next) never moves to a song the order did not choose.
     * Never touches the playing item, never stops or prepares the player.
     */
    private fun alignTelegramWindow() {
        val player = exoPlayer ?: return
        val currentId = currentTelegramId() ?: return
        val cur = player.currentMediaItemIndex
        val count = player.mediaItemCount
        if (cur < 0 || cur >= count) return
        val ids = queueTracks.map { it.messageId }
        val at = ids.indexOf(currentId)
        if (at < 0) return
        fun idAt(index: Int): Long? =
            player.getMediaItemAt(index).mediaId.removePrefix("telegram:").toLongOrNull()
        var after = 0
        while (cur + 1 + after < count && at + 1 + after < ids.size &&
            idAt(cur + 1 + after) == ids[at + 1 + after]) after++
        if (cur + 1 + after < count) player.removeMediaItems(cur + 1 + after, count)
        var before = 0
        while (cur - 1 - before >= 0 && at - 1 - before >= 0 &&
            idAt(cur - 1 - before) == ids[at - 1 - before]) before++
        if (cur - before > 0) player.removeMediaItems(0, cur - before)
    }

    /** Shuffle was switched by any controller: recompute the play order around the playing song. */
    private fun applyShuffleMode(enabled: Boolean) {
        val player = exoPlayer ?: return
        val mediaId = player.currentMediaItem?.mediaId.orEmpty()
        if (mediaId.startsWith("telegram:")) {
            if (expectedAccount == null || telegramBase.isEmpty()) return
            shuffleSeed = nextSeed()
            val byId = telegramBase.associateBy { it.messageId }
            queueTracks = MeloShuffleOrderPolicy.toggled(
                telegramBase.map { it.messageId }, currentTelegramId(), enabled, shuffleSeed
            ).mapNotNull { byId[it] }
            alignTelegramWindow()
            sessionPlayer?.refreshCommands()
            requestNextIfMissing()
            shuffleLog("toggle", enabled, queueTracks.size)
        } else if (MeloLocalSourcePolicy.idOfMediaId(mediaId) != null) {
            reorderLocal(enabled)
        } else {
            shuffleLog("toggle_idle", enabled, 0)
        }
    }

    /** Local music: base media ids in, play order out; the Activity builds the Media3 list in that order. */
    private fun orderLocal(baseIds: List<String>, anchorId: String): List<String> {
        if (Looper.myLooper() != Looper.getMainLooper()) return baseIds
        if (baseIds.any { MeloLocalSourcePolicy.idOfMediaId(it) == null }) return baseIds
        localBaseIds = baseIds.toList()
        if (exoPlayer?.shuffleModeEnabled != true) return baseIds
        shuffleSeed = nextSeed()
        return MeloShuffleOrderPolicy.shuffled(baseIds, anchorId, shuffleSeed)
    }

    /** Re-lays the playing local list around the playing song (shuffled on, source order off) without a stop. */
    private fun reorderLocal(enabled: Boolean) {
        val player = exoPlayer ?: return
        val count = player.mediaItemCount
        val cur = player.currentMediaItemIndex
        if (count < 2 || cur !in 0 until count) return
        val items = (0 until count).map { player.getMediaItemAt(it) }
        val present = items.map { it.mediaId }
        if (present.any { MeloLocalSourcePolicy.idOfMediaId(it) == null }) return
        val started = SystemClock.elapsedRealtime()
        if (localBaseIds.isEmpty()) localBaseIds = present
        shuffleSeed = nextSeed()
        val currentId = present[cur]
        val target = MeloShuffleOrderPolicy.toggled(
            MeloShuffleOrderPolicy.restore(localBaseIds, present), currentId, enabled, shuffleSeed)
        val byId = items.associateBy { it.mediaId }
        val ordered = target.mapNotNull { byId[it] }
        val at = target.indexOf(currentId)
        if (at < 0 || ordered.size != count) return
        reorderingLocal = true
        try {
            if (cur + 1 < count) player.removeMediaItems(cur + 1, count)
            if (cur > 0) player.removeMediaItems(0, cur)
            if (at > 0) player.addMediaItems(0, ordered.subList(0, at))
            if (at + 1 < ordered.size) {
                player.addMediaItems(player.currentMediaItemIndex + 1, ordered.subList(at + 1, ordered.size))
            }
        } finally {
            reorderingLocal = false
        }
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(player.mediaItemCount))
        sessionPlayer?.refreshCommands()
        shuffleLog("local_reorder", enabled, count, " ms=${SystemClock.elapsedRealtime() - started}")
    }

    /**
     * Safety net: the list order must stay the play order. If Media3 ever swaps the identity
     * shuffle order for a random one, its Next would no longer be the next list item: restore it.
     */
    private fun keepIdentityShuffleOrder() {
        val player = exoPlayer ?: return
        if (reorderingLocal || !player.shuffleModeEnabled) return
        val cur = player.currentMediaItemIndex
        val count = player.mediaItemCount
        if (cur < 0 || cur + 1 >= count || player.nextMediaItemIndex == cur + 1) return
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(count))
        shuffleLog("order_reset", true, count)
    }

    private fun clearPending() {
        pendingDownloadId = null
        pendingMessageId = null
        pendingAttempt = 0
        pendingRequestTag = null
    }

    private fun clearQueue() {
        queueGeneration++
        clearPending()
        expectedAccount = null
        allowedRoot = null
        queueTracks = emptyList()
        telegramBase = emptyList()
        localBaseIds = emptyList()
        shuffleSeed = 0L
        sessionPlayer?.refreshCommands()
        widgetSession?.refreshNow()
    }

    private fun nextTrack(): QueueTrack? {
        val account = expectedAccount ?: return null
        if (account != ShareableTdlibOwner.authorizedAccount()) return null
        val id = exoPlayer?.currentMediaItem?.mediaId.orEmpty()
        if (!id.startsWith("telegram:")) return null
        val current = id.removePrefix("telegram:").toLongOrNull() ?: return null
        val nextId = TelegramPrefetchOrderPolicy.immediateSuccessor(
            queueTracks.map { it.messageId }, current
        ) ?: return null
        return queueTracks.firstOrNull { it.messageId == nextId }
    }

    private fun requestNextIfMissing() {
        // Foreground Activity already handles prefetch; never start two
        // independent TDLib downloads for the same neighbor.
        if (ShareableTdlibOwner.uiIsAttached()) return
        val next = nextTrack()
        if (next == null) {
            clearPending()
            return
        }
        if (pendingDownloadId == next.fileId &&
            pendingMessageId == next.messageId) return
        clearPending()
        val player = exoPlayer ?: return
        if ((0 until player.mediaItemCount).any {
                player.getMediaItemAt(it).mediaId == "telegram:${next.messageId}"
            }) return
        val root = allowedRoot ?: return
        val cached = next.cachedPath?.let { File(it) }
        if (cached != null && cached.isFile &&
            TelegramAccountStoragePolicy.isOwnedFile(root, cached)) {
            insertNext(next, cached)
            return
        }
        startDownload(next, 1)
    }

    private fun startDownload(next: QueueTrack, attempt: Int) {
        if (!mayDownload(next) ||
            nextTrack()?.messageId != next.messageId ||
            expectedAccount != ShareableTdlibOwner.authorizedAccount()) return
        val generation = queueGeneration
        val effectiveChatId = if (next.chatId != 0L) next.chatId else (expectedAccount ?: 0L)
        val rawMsgId = next.genuineMessageId
        if (effectiveChatId == 0L || rawMsgId <= 0L) {
            retryOrStop(next, attempt, generation)
            return
        }
        val tag = "background:resolve:${next.messageId}:$generation:$attempt"
        pendingDownloadId = null
        pendingMessageId = next.messageId
        pendingAttempt = attempt
        pendingRequestTag = tag
        val sent = runCatching {
            ShareableTdlibOwner.send("getMessage", tag) {
                it.put("chat_id", effectiveChatId)
                it.put("message_id", rawMsgId)
            }
        }.isSuccess
        if (!sent) {
            retryOrStop(next, attempt, generation)
            return
        }
        main.postDelayed({
            if (queueGeneration == generation &&
                pendingMessageId == next.messageId &&
                pendingAttempt == attempt &&
                pendingRequestTag == tag) {
                retryOrStop(next, attempt, generation)
            }
        }, TelegramBackgroundPrefetchPolicy.TIMEOUT_MS)
    }

    private fun retryOrStop(next: QueueTrack, attempt: Int, generation: Long) {
        clearPending()
        if (!TelegramBackgroundPrefetchPolicy.canRetry(
                attempt, expectedAccount, ShareableTdlibOwner.authorizedAccount(),
                next.messageId, nextTrack()?.messageId
            )) return
        main.postDelayed({
            if (queueGeneration == generation &&
                pendingDownloadId == null &&
                mayDownload(next) &&
                nextTrack()?.messageId == next.messageId) {
                startDownload(next, attempt + 1)
            }
        }, TelegramBackgroundPrefetchPolicy.RETRY_DELAY_MS)
    }

    private fun acceptBackgroundEvent(event: JSONObject) {
        val kind = event.optString("@type")
        val tag = event.optString("@extra")
        val next = nextTrack() ?: run {
            clearPending()
            return
        }

        if (tag.startsWith("background:resolve:")) {
            if (tag != pendingRequestTag || next.messageId != pendingMessageId) return
            if (kind == "error") {
                retryOrStop(next, pendingAttempt, queueGeneration)
                return
            }
            when (val parsed = TelegramAudioResolverPolicy.parseMessage(event)) {
                is TelegramAudioResolverPolicy.Result.Error -> {
                    retryOrStop(next, pendingAttempt, queueGeneration)
                }
                is TelegramAudioResolverPolicy.Result.Resolved -> {
                    val root = allowedRoot ?: return
                    val local = if (parsed.localPath.isNotBlank()) File(parsed.localPath) else null
                    if (parsed.isDownloadingCompleted && local != null && local.isFile &&
                        TelegramAccountStoragePolicy.isOwnedFile(root, local)) {
                        clearPending()
                        insertNext(next.copy(fileId = parsed.freshFileId), local)
                        if (userNextMessageId == next.messageId) {
                            userNextMessageId = null
                            exoPlayer?.seekToNextMediaItem()
                            exoPlayer?.play()
                        }
                    } else {
                        val freshId = parsed.freshFileId
                        val generation = queueGeneration
                        val userAsked = userNextMessageId == next.messageId
                        val downloadTag = "background:download:$freshId:$generation:$pendingAttempt"
                        pendingDownloadId = freshId
                        pendingRequestTag = downloadTag
                        val sent = runCatching {
                            ShareableTdlibOwner.send("downloadFile", downloadTag) {
                                it.put("file_id", freshId)
                                it.put("priority", if (userAsked) 32 else 4)
                                it.put("offset", 0)
                                it.put("limit", 0)
                                it.put("synchronous", false)
                            }
                        }.isSuccess
                        if (!sent) {
                            retryOrStop(next, pendingAttempt, generation)
                        } else if (userAsked) {
                            startProgressiveNext(next.copy(fileId = freshId), parsed.size)
                        }
                    }
                }
            }
            return
        }

        val id = pendingDownloadId ?: return
        if (kind == "error") {
            if (tag == pendingRequestTag) {
                retryOrStop(next, pendingAttempt, queueGeneration)
            }
            return
        }
        // Stale tagged response from a previous attempt or queue is discarded.
        if (kind == "file" && tag != pendingRequestTag) return
        val file = if (kind == "updateFile") {
            event.optJSONObject("file") ?: return
        } else event
        if (!TelegramBackgroundPrefetchPolicy.isCurrentResult(
                id, file.optInt("id"), expectedAccount,
                ShareableTdlibOwner.authorizedAccount(), pendingMessageId, next.messageId
            )) return
        val local = file.optJSONObject("local") ?: return
        if (!local.optBoolean("is_downloading_completed")) return
        clearPending()
        val filePath = local.optString("path")
        if (filePath.isBlank()) return
        val target = File(filePath)
        val root = allowedRoot ?: return
        if (!target.isFile || !TelegramAccountStoragePolicy.isOwnedFile(root, target)) return
        insertNext(next.copy(fileId = id), target)
    }

    private fun metadataFor(next: QueueTrack): MediaMetadata = MediaMetadata.Builder()
        .setTitle(next.title)
        .setArtist(MeloMediaControlPolicy.fallbackArtist(next.artist))
        .setExtras(android.os.Bundle().apply {
            MeloWidgetPolicy.trustedPerformer(next.performer)?.let {
                putString(MeloWidgetPolicy.EXTRA_PERFORMER, it)
            }
        })
        .build()

    private fun insertNext(next: QueueTrack, local: File) {
        val player = exoPlayer ?: return
        val mediaId = "telegram:${next.messageId}"
        if ((0 until player.mediaItemCount).any {
                player.getMediaItemAt(it).mediaId == mediaId
            }) return
        val item = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(android.net.Uri.fromFile(local))
            .setMediaMetadata(metadataFor(next))
            .build()
        player.addMediaItem(
            (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount), item
        )
        sessionPlayer?.refreshCommands()
    }

    /**
     * Next was pressed and the song is still downloading: play it from the growing private file
     * (same source the library screen uses) without waiting for the whole mix.
     */
    private fun startProgressiveNext(next: QueueTrack, size: Long) {
        val player = exoPlayer ?: return
        val account = expectedAccount ?: return
        val root = allowedRoot ?: return
        if (!TelegramProgressiveCache.begin(account, root, next.fileId, if (size > 0L) size else next.expectedSize)) return
        val mediaId = "telegram:${next.messageId}"
        if ((0 until player.mediaItemCount).none { player.getMediaItemAt(it).mediaId == mediaId }) {
            val ext = next.filename.substringAfterLast('.', "mp3").lowercase()
                .takeIf { it in setOf("mp3", "flac", "wav", "m4a", "ogg", "opus", "aac") } ?: "mp3"
            val item = MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri(TelegramProgressiveCache.makeUri(next.fileId, ext))
                .setMediaMetadata(metadataFor(next))
                .build()
            player.addMediaItem((player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount), item)
        }
        userNextMessageId = null
        player.seekToNextMediaItem()
        player.prepare()
        player.play()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        widgetSession?.close()
        widgetSession = null
        TelegramProgressiveCache.clear()
        clearQueue()
        main.removeCallbacksAndMessages(null)
        ShareableTdlibOwner.unbindPlayback()
        serviceRef?.clear()
        serviceRef = null
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
