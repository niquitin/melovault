package com.bsdung.musicassistantcar.data.telegram

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
    ) {
        val genuineMessageId: Long get() = if (rawMessageId > 0L) rawMessageId else messageId
    }

    companion object {
        private var serviceRef: WeakReference<ShareablePlaybackService>? = null

        // Internal same-process API; never expose these details via an Intent.
        internal fun configureTelegramQueue(
            accountId: Long, ownerRoot: File, ordered: List<QueueTrack>
        ) {
            serviceRef?.get()?.setQueue(accountId, ownerRoot, ordered)
        }

        internal fun clearTelegramQueue() {
            serviceRef?.get()?.clearQueue()
        }

        /** Main-thread, authorized snapshot; never exposes another account's queue. */
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
    private var queueTracks = emptyList<QueueTrack>()
    private var pendingDownloadId: Int? = null
    private var pendingMessageId: Long? = null
    private var pendingAttempt = 0
    private var pendingRequestTag: String? = null
    private var queueGeneration = 0L
    private var sessionPlayer: NextAwarePlayer? = null
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
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                requestNextIfMissing()
                sessionPlayer?.refreshCommands()
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

    private fun setQueue(accountId: Long, root: File, ordered: List<QueueTrack>) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        if (accountId <= 0L || ShareableTdlibOwner.authorizedAccount() != accountId) {
            clearQueue()
            return
        }
        if (ordered.size > 2000 || ordered.any { it.messageId <= 0L || it.fileId <= 0 }) {
            clearQueue()
            return
        }
        clearQueue()
        expectedAccount = accountId
        allowedRoot = root.canonicalFile
        queueTracks = ordered.toList()
        sessionPlayer?.refreshCommands()
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
        sessionPlayer?.refreshCommands()
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
