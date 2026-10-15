package com.bsdung.musicassistantcar.data.telegram

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
        val cachedPath: String?
    )

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

        internal fun onPreviewDetached() {
            serviceRef?.get()?.requestNextIfMissing()
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

    override fun onCreate() {
        super.onCreate()
        serviceRef = WeakReference(this)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                requestNextIfMissing()
            }
        })

        val intent = Intent(this, TelegramPreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else 0
        val launch = PendingIntent.getActivity(this, 0, intent, flags)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(launch)
            .build()
        ShareableTdlibOwner.bindPlayback { event ->
            // Native receive thread may not touch the Media3 player directly.
            val tag = event.optString("@extra")
            val kind = event.optString("@type")
            if (kind == "updateAuthorizationState") {
                val state = event.optJSONObject("authorization_state")
                    ?.optString("@type").orEmpty()
                if (state in setOf("authorizationStateLoggingOut",
                        "authorizationStateClosing", "authorizationStateClosed")) {
                    main.post {
                        ShareableTdlibOwner.revokeAuthorization()
                        clearQueue()
                        exoPlayer?.stop()
                        exoPlayer?.clearMediaItems()
                    }
                }
            } else if ((kind == "file" && tag.startsWith("background:")) ||
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
        if (ordered.size > 300 || ordered.any { it.messageId <= 0L || it.fileId <= 0 }) {
            clearQueue()
            return
        }
        clearQueue()
        expectedAccount = accountId
        allowedRoot = root.canonicalFile
        queueTracks = ordered.toList()
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
        if (ShareableTdlibOwner.uiIsAttached() ||
            nextTrack()?.messageId != next.messageId ||
            expectedAccount != ShareableTdlibOwner.authorizedAccount()) return
        val generation = queueGeneration
        val tag = "background:${next.fileId}:$generation:$attempt"
        pendingDownloadId = next.fileId
        pendingMessageId = next.messageId
        pendingAttempt = attempt
        pendingRequestTag = tag
        val sent = runCatching {
            ShareableTdlibOwner.send("downloadFile", tag) {
                it.put("file_id", next.fileId)
                it.put("priority", 4)
                it.put("offset", 0)
                it.put("limit", 0)
                it.put("synchronous", false)
            }
        }.isSuccess
        if (!sent) {
            retryOrStop(next, attempt, generation)
            return
        }
        main.postDelayed({
            if (queueGeneration == generation &&
                pendingDownloadId == next.fileId &&
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
                !ShareableTdlibOwner.uiIsAttached() &&
                nextTrack()?.messageId == next.messageId) {
                startDownload(next, attempt + 1)
            }
        }, TelegramBackgroundPrefetchPolicy.RETRY_DELAY_MS)
    }

    private fun acceptBackgroundEvent(event: JSONObject) {
        val id = pendingDownloadId ?: return
        val kind = event.optString("@type")
        val tag = event.optString("@extra")
        if (kind == "error") {
            if (tag == pendingRequestTag) {
                nextTrack()?.takeIf { it.fileId == id }?.let { next ->
                    retryOrStop(next, pendingAttempt, queueGeneration)
                } ?: clearPending()
            }
            return
        }
        // Stale tagged response from a previous attempt or queue is discarded.
        if (kind == "file" && tag != pendingRequestTag) return
        val file = if (kind == "updateFile") {
            event.optJSONObject("file") ?: return
        } else event
        val next = nextTrack() ?: return
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
        insertNext(next, target)
    }

    private fun insertNext(next: QueueTrack, local: File) {
        val player = exoPlayer ?: return
        val mediaId = "telegram:${next.messageId}"
        if ((0 until player.mediaItemCount).any {
                player.getMediaItemAt(it).mediaId == mediaId
            }) return
        val item = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(android.net.Uri.fromFile(local))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(next.title).build())
            .build()
        player.addMediaItem(
            (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount), item
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
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
