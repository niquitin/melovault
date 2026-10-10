package com.bsdung.musicassistantcar.playback

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.bsdung.musicassistantcar.playback.alac.AlacAudioRenderer
import com.bsdung.musicassistantcar.playback.alac.AlacCapabilities
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.PlaybackQuality
import com.bsdung.musicassistantcar.data.RemoteCatalog
import com.bsdung.musicassistantcar.data.StreamQuality
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.json.JSONArray
import java.util.Collections
import java.util.concurrent.Executors

private data class StoredPlaybackState(
    val mediaIds: List<String>,
    val currentIndex: Int,
    val positionMs: Long,
)

class MusicService : MediaLibraryService() {
    companion object {
        private const val TAG = "MusicService"
        private const val PREFS_NAME = "music_assistant_car"
        private const val KEY_QUEUE_MEDIA_IDS = "resume_queue_media_ids"
        private const val KEY_QUEUE_INDEX = "resume_queue_index"
        private const val KEY_QUEUE_POSITION_MS = "resume_queue_position_ms"
        private const val KEY_SHUFFLE_ENABLED = "playback_shuffle_enabled"
        private const val KEY_REPEAT_MODE = "playback_repeat_mode"
        private const val PERSIST_INTERVAL_MS = 10_000L
        const val NETWORK_RECOVERY_COOLDOWN_MS = NetworkRecoveryPolicy.NETWORK_RECOVERY_COOLDOWN_MS
        const val KEY_LOUDNESS_NORMALIZATION = "loudness_normalization"
    }

    val gainAudioProcessor = GainAudioProcessor()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == KEY_LOUDNESS_NORMALIZATION) {
            val enabled = prefs.getBoolean(KEY_LOUDNESS_NORMALIZATION, true)
            Log.i(TAG, "Loudness normalization preference changed: enabled=$enabled")
            gainAudioProcessor.isNormalizationEnabled = enabled
            if (::player.isInitialized) {
                applyNormalizationForMediaItem(player.currentMediaItem)
            }
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var executor: ListeningExecutorService
    private lateinit var catalog: RemoteCatalog
    private lateinit var historyTracker: PlayHistoryTracker
    private var librarySession: MediaLibrarySession? = null
    @Volatile
    private var networkRecoveryInProgress = false
    private var lastNetworkRecoveryAttemptMs = 0L
    private val persistHandler = Handler(Looper.getMainLooper())
    // Main-thread handler for replaceMediaItem after background resolution.
    private val mainHandler = Handler(Looper.getMainLooper())
    // Drive-file IDs whose authoritative metadata has already been fetched this session.
    // Prevents duplicate detail requests on repeat/re-navigation.
    private val resolvedDriveFileIds: MutableSet<Int> =
        Collections.synchronizedSet(mutableSetOf())
    private lateinit var trackResumeStore: TrackResumeStore
    private var serviceShuttingDown = false
    @Volatile private var incomingBrowseQueue: Pair<QueueBrowseSeed, List<String>>? = null
    private var browseQueueCursor: QueuePageCursor? = null
    private var lastActiveMediaId: String? = null
    private var lastKnownPositionMs: Long = 0L
    private var lastKnownDurationMs: Long = 0L
    private var currentOccurrenceMediaId: String? = null
    private var pendingDurationMediaId: String? = null
    private var sessionRestoredMediaId: String? = null
    private var isRestoringResume: Boolean = false

    private val persistRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                persistPlaybackState()
                saveCurrentTrackResume()
                if (::historyTracker.isInitialized) {
                    historyTracker.onPeriodicTick(
                        player.isPlaying,
                        SystemClock.elapsedRealtime(),
                    )
                }
                persistHandler.postDelayed(this, PERSIST_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Initializing MusicService on thread=${Thread.currentThread().name}")

        trackResumeStore = TrackResumeStore.create(this)
        runCatching {
            OfflinePlaylistSyncCoordinator.getDefault(this).ensureBackgroundSyncScheduled()
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val mobile = prefs.getBoolean("mobile_320", false)
        PlaybackQuality.current = if (mobile) {
            StreamQuality.MOBILE_320
        } else {
            StreamQuality.ORIGINAL
        }
        val normEnabled = prefs.getBoolean(KEY_LOUDNESS_NORMALIZATION, true)
        gainAudioProcessor.isNormalizationEnabled = normEnabled
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        catalog = RemoteCatalog(
            api = CarApiClient(),
            localRepoSupplier = { com.bsdung.musicassistantcar.data.LocalMediaRepository.getDefault(this) },
            downloadManagerSupplier = { PinnedDownloadManager.getDefault(this) },
            playlistSyncCoordinatorSupplier = { OfflinePlaylistSyncCoordinator.getDefault(this) },
        )
        historyTracker = PlayHistoryTracker { mediaId ->
            Log.i(TAG, "Track reached >=10s playback, reporting history: $mediaId")
            executor.execute {
                try {
                    val trackId = catalog.reportPlay(mediaId)
                    Log.i(TAG, "History reported: mediaId=$mediaId, trackId=$trackId")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to report play history for $mediaId: ${e.message}")
                }
            }
        }
        executor = MoreExecutors.listeningDecorator(
            Executors.newFixedThreadPool(3)
        )

        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(60_000)
            .setDefaultRequestProperties(
                catalog.authorizationHeaders()
            )
        val dataSourceFactory = StreamCacheManager.createDataSourceFactory(this, httpFactory)
        val mediaSourceFactory =
            DefaultMediaSourceFactory(this)
                .setDataSourceFactory(dataSourceFactory)

        val attributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000,
                30_000,
                1_000,
                2_000,
            )
            .build()

        AlacCapabilities.logDeviceCapabilities()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(gainAudioProcessor))
                    .build()
            }

            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>,
            ) {
                // Platform MediaCodecAudioRenderer is added first (Priority 1)
                super.buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out,
                )
                // Pure-Java software ALAC decoder fallback (Priority 2)
                out.add(AlacAudioRenderer(eventHandler, eventListener, audioSink))
            }
        }

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setAudioAttributes(attributes, true)
            }

        // Restore modes before registering the listener, so the restored values
        // cannot be overwritten by an initial ExoPlayer default-state callback.
        val restoredModes = PlaybackModeState.restore(
            shuffleEnabled = prefs.getBoolean(KEY_SHUFFLE_ENABLED, false),
            storedRepeatMode = prefs.getInt(KEY_REPEAT_MODE, Player.REPEAT_MODE_OFF),
        )
        player.shuffleModeEnabled = restoredModes.shuffleEnabled
        player.repeatMode = restoredModes.repeatMode
        Log.i(TAG, "onCreate: restored shuffle=${restoredModes.shuffleEnabled}, repeat=${restoredModes.repeatMode}")

        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                var audioTrackSelected = false
                var hasAudioTrack = false
                var unhandledAudioFormat: androidx.media3.common.Format? = null
                for (group in tracks.groups) {
                    val trackType = group.type
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val isTrackSelected = group.isTrackSelected(i)
                        if (trackType == C.TRACK_TYPE_AUDIO) {
                            hasAudioTrack = true
                            if (isTrackSelected) {
                                audioTrackSelected = true
                                if (format.sampleMimeType == MimeTypes.AUDIO_ALAC) {
                                    val platformSupported = AlacCapabilities.supportsPlatformDecoder(MimeTypes.AUDIO_ALAC)
                                    AlacCapabilities.logSelectedRenderer(platformSupported)
                                }
                            } else {
                                unhandledAudioFormat = format
                            }
                        }
                        Log.i(
                            TAG,
                            "onTracksChanged: type=$trackType, selected=$isTrackSelected, " +
                                "sampleMime=${format.sampleMimeType}, containerMime=${format.containerMimeType}, " +
                                "codecs=${format.codecs}, sampleRate=${format.sampleRate}, " +
                                "channels=${format.channelCount}, pcmEncoding=${format.pcmEncoding}",
                        )
                    }
                }
                if (hasAudioTrack && !audioTrackSelected) {
                    val mime = unhandledAudioFormat?.sampleMimeType
                    Log.w(
                        TAG,
                        "SILENT_RENDER_GUARD_TRIGGERED: unsupported audio track detected without active audio renderer (mime=$mime)",
                    )
                    player.pause()
                    val errorMsg = if (mime == MimeTypes.AUDIO_ALAC) {
                        "Thiết bị chưa hỗ trợ định dạng ALAC"
                    } else {
                        "Thiết bị chưa hỗ trợ định dạng âm thanh này"
                    }
                    showPlaybackErrorToast(errorMsg)
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                    } else {
                        player.stop()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                Log.i(
                    TAG,
                    "onPlaybackStateChanged: state=$stateName, " +
                        "playWhenReady=${player.playWhenReady}, " +
                        "isPlaying=${player.isPlaying}, " +
                        "mediaId=${player.currentMediaItem?.mediaId}, " +
                        "queueSize=${player.mediaItemCount}",
                )
                if (playbackState == Player.STATE_ENDED) {
                    player.currentMediaItem?.let { item ->
                        val mediaId = TrackResumeStore.getMediaIdentity(item)
                        trackResumeStore.clear(mediaId)
                        Log.i(TAG, "onPlaybackStateChanged: STATE_ENDED, cleared resume position for $mediaId")
                    }
                }
                if (
                    playbackState == Player.STATE_READY ||
                    playbackState == Player.STATE_ENDED
                ) {
                    persistPlaybackState()
                }
                if (playbackState == Player.STATE_READY) {
                    val currentIndex = player.currentMediaItemIndex
                    val queueSize = player.mediaItemCount
                    applyNormalizationForMediaItem(player.currentMediaItem)
                    lazyResolveDriveFile(currentIndex)
                    val nextIndex = currentIndex + 1
                    if (nextIndex < queueSize) {
                        lazyResolveDriveFile(nextIndex)
                    }

                    // Check deferred resume position if duration was unknown at transition
                    val currentItem = player.currentMediaItem
                    val currentId = TrackResumeStore.getMediaIdentity(currentItem)
                    if (pendingDurationMediaId != null && currentId == pendingDurationMediaId) {
                        pendingDurationMediaId = null
                        val dur = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
                        if (dur > 0L) {
                            evaluateAndApplyResume(currentItem, currentId, dur)
                        }
                    }
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                Log.d(TAG, "onTimelineChanged: reason=$reason, queueSize=${player.mediaItemCount}")
                syncBrowseQueue()
                persistPlaybackState()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                persistPlaybackModes()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                persistPlaybackModes()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                val reasonName = when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
                    else -> "REASON_$reason"
                }
                Log.i(
                    TAG,
                    "onPlayWhenReadyChanged: playWhenReady=$playWhenReady, " +
                        "reason=$reasonName, isPlaying=${player.isPlaying}, " +
                        "state=${player.playbackState}",
                )
                persistPlaybackState()
                if (!playWhenReady) {
                    saveCurrentTrackResume()
                }
                updatePersistenceLoop()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.i(
                    TAG,
                    "onIsPlayingChanged: isPlaying=$isPlaying, " +
                        "mediaId=${player.currentMediaItem?.mediaId}, " +
                        "pos=${player.currentPosition}ms, " +
                        "queueSize=${player.mediaItemCount}",
                )
                persistPlaybackState()
                if (!isPlaying) {
                    saveCurrentTrackResume()
                }
                updatePersistenceLoop()
                if (::historyTracker.isInitialized) {
                    historyTracker.onIsPlayingChanged(
                        isPlaying,
                        SystemClock.elapsedRealtime(),
                    )
                    if (isPlaying) {
                        persistHandler.postDelayed(
                            {
                                if (::player.isInitialized && ::historyTracker.isInitialized) {
                                    historyTracker.onPeriodicTick(
                                        player.isPlaying,
                                        SystemClock.elapsedRealtime(),
                                    )
                                }
                            },
                            PlayHistoryTracker.REQUIRED_PLAYBACK_MS + 50L,
                        )
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                val reasonName = when (reason) {
                    Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
                    Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
                    Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
                    Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
                    else -> "REASON_$reason"
                }
                Log.i(
                    TAG,
                    "onPositionDiscontinuity: reason=$reasonName, " +
                        "oldPos=${oldPosition.positionMs}ms, newPos=${newPosition.positionMs}ms",
                )
                if (reason == Player.DISCONTINUITY_REASON_SEEK && !isRestoringResume) {
                    if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                        // Intra-track seek by user
                        pendingDurationMediaId = null
                        saveCurrentTrackResume(newPosition.positionMs)
                    } else {
                        // Inter-track seek: save departing track's actual position
                        val oldMediaItem = oldPosition.mediaItem
                        val oldIdentity = TrackResumeStore.getMediaIdentity(oldMediaItem).ifBlank { lastActiveMediaId ?: "" }
                        if (oldIdentity.isNotBlank()) {
                            val oldDur = oldMediaItem?.mediaMetadata?.extras?.getLong("duration_ms")?.takeIf { it > 0L }
                                ?: lastKnownDurationMs
                            lastKnownPositionMs = oldPosition.positionMs
                            lastKnownDurationMs = oldDur
                            trackResumeStore.save(oldIdentity, oldPosition.positionMs, oldDur)
                        }
                    }
                } else if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    // Natural track completion: clear departing track from resume store
                    val oldMediaItem = oldPosition.mediaItem
                    val oldIdentity = TrackResumeStore.getMediaIdentity(oldMediaItem).ifBlank { lastActiveMediaId ?: "" }
                    if (oldIdentity.isNotBlank()) {
                        Log.i(TAG, "onPositionDiscontinuity: AUTO_TRANSITION, clearing resume entry for completed track $oldIdentity")
                        trackResumeStore.clear(oldIdentity)
                    }
                    lastKnownPositionMs = 0L
                    lastKnownDurationMs = 0L
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyNormalizationForMediaItem(mediaItem)
                val reasonName = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                    else -> "REASON_$reason"
                }
                val currentIndex = player.currentMediaItemIndex
                val queueSize = player.mediaItemCount
                maybeExtendBrowseQueue()
                val playbackSource = when {
                    mediaItem?.mediaMetadata?.extras?.getBoolean("is_offline_download") == true ||
                    mediaItem?.mediaMetadata?.extras?.getString("playback_source") == "PINNED_DOWNLOAD" ||
                    mediaItem?.localConfiguration?.uri?.scheme == "file" -> "PINNED_DOWNLOAD"
                    mediaItem?.localConfiguration?.uri?.scheme?.startsWith("http") == true -> "REMOTE_STREAM"
                    else -> "DEVICE_LOCAL"
                }
                Log.i(TAG, "Playback source: $playbackSource")
                Log.i(
                    TAG,
                    "onMediaItemTransition: mediaId=${mediaItem?.mediaId}, " +
                        "title=${mediaItem?.mediaMetadata?.title}, " +
                        "playback_source=$playbackSource, " +
                        "reason=$reasonName, " +
                        "index=$currentIndex/$queueSize",
                )
                persistPlaybackState()

                val currentIdentity = TrackResumeStore.getMediaIdentity(mediaItem)

                // Handle departing track completion vs manual seek
                when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
                        // Departing track completed naturally: clear resume entry and do NOT re-save
                        if (lastActiveMediaId != null) {
                            Log.i(TAG, "onMediaItemTransition: AUTO transition, clearing departing track $lastActiveMediaId")
                            trackResumeStore.clear(lastActiveMediaId!!)
                        }
                        lastKnownPositionMs = 0L
                        lastKnownDurationMs = 0L
                    }
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> {
                        // Natural completion with repeat: clear resume entry so stale checkpoint is not used
                        if (lastActiveMediaId != null) {
                            Log.i(TAG, "onMediaItemTransition: REPEAT transition, clearing departing track $lastActiveMediaId")
                            trackResumeStore.clear(lastActiveMediaId!!)
                        }
                        if (currentIdentity.isNotBlank()) {
                            trackResumeStore.clear(currentIdentity)
                        }
                        lastKnownPositionMs = 0L
                        lastKnownDurationMs = 0L
                    }
                    else -> {
                        // Manual SEEK or PLAYLIST_CHANGED: save departing track position
                        if (lastActiveMediaId != null && lastActiveMediaId != currentIdentity) {
                            trackResumeStore.save(lastActiveMediaId!!, lastKnownPositionMs, lastKnownDurationMs)
                        }
                    }
                }

                if (::historyTracker.isInitialized) {
                    historyTracker.onMediaItemTransition(
                        mediaItem?.mediaId,
                        player.isPlaying,
                        SystemClock.elapsedRealtime(),
                    )
                    if (player.isPlaying) {
                        persistHandler.postDelayed(
                            {
                                if (::player.isInitialized && ::historyTracker.isInitialized) {
                                    historyTracker.onPeriodicTick(
                                        player.isPlaying,
                                        SystemClock.elapsedRealtime(),
                                    )
                                }
                            },
                            PlayHistoryTracker.REQUIRED_PLAYBACK_MS + 50L,
                        )
                    }
                }

                if (mediaItem == null || currentIdentity.isBlank()) {
                    currentOccurrenceMediaId = null
                    pendingDurationMediaId = null
                    return
                }

                lastActiveMediaId = currentIdentity

                // Natural completion with repeat: play from 00:00 without restoring old checkpoint
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    currentOccurrenceMediaId = currentIdentity
                    pendingDurationMediaId = null
                    Log.i(TAG, "onMediaItemTransition: REPEAT transition for $currentIdentity, starting from 00:00")
                    return
                }

                // Session restoration priority guard
                if (sessionRestoredMediaId == currentIdentity) {
                    sessionRestoredMediaId = null
                    currentOccurrenceMediaId = currentIdentity
                    pendingDurationMediaId = null
                    Log.i(TAG, "onMediaItemTransition: session restore has priority for $currentIdentity, keeping position")
                    return
                }

                // Quality switch in progress guard
                if (PlaybackQuality.isQualitySwitching) {
                    currentOccurrenceMediaId = currentIdentity
                    pendingDurationMediaId = null
                    Log.i(TAG, "onMediaItemTransition: Quality switch in progress, skipping resume restore for $currentIdentity")
                    return
                }

                // Network recovery in progress guard
                if (networkRecoveryInProgress) {
                    currentOccurrenceMediaId = currentIdentity
                    pendingDurationMediaId = null
                    Log.i(TAG, "onMediaItemTransition: Network recovery in progress, skipping resume restore for $currentIdentity")
                    return
                }

                // Same occurrence (lazy metadata replacement or already active track)
                if (currentOccurrenceMediaId == currentIdentity) {
                    Log.i(TAG, "onMediaItemTransition: Same playback occurrence for $currentIdentity, skipping duplicate restore")
                    return
                }

                currentOccurrenceMediaId = currentIdentity
                pendingDurationMediaId = null

                val extrasDuration = mediaItem.mediaMetadata.extras?.getLong("duration_ms")?.takeIf { it > 0L }
                val playerDuration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                val duration = playerDuration ?: extrasDuration ?: 0L

                if (duration > 0L) {
                    evaluateAndApplyResume(mediaItem, currentIdentity, duration)
                } else {
                    Log.i(TAG, "onMediaItemTransition: Duration unknown for $currentIdentity, deferring to STATE_READY")
                    pendingDurationMediaId = currentIdentity
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    TAG,
                    "onPlayerError: errorCode=${error.errorCode}, message=${error.message}",
                    error,
                )
                recoverNetworkPlayback(error)
            }
        })

        val sessionIntent = packageManager
            ?.getLaunchIntentForPackage(packageName)
            ?.let { intent ->
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }

        librarySession = MediaLibrarySession.Builder(
            this,
            player,
            LibraryCallback(
                this,
                catalog,
                executor,
                player,
                ::loadPlaybackState,
                onSessionRestore = { targetId ->
                    sessionRestoredMediaId = targetId
                },
                onBrowseQueueRequested = { seed, seedIds ->
                    incomingBrowseQueue = seed?.let { it to seedIds }
                },
            ),
        ).apply {
            if (sessionIntent != null) {
                setSessionActivity(sessionIntent)
            }
        }.build()
        Log.i(TAG, "onCreate: MediaLibrarySession created successfully")
        restorePersistedQueueOnStartup()
    }

    /**
     * Restore the last queue as a PAUSED timeline, so opening the app after the
     * playback service was killed immediately shows the previous song and lets
     * Play/Next resume it. Resolve metadata off the main thread and never
     * overwrite any newer queue the user chose while resolution was pending.
     */
    private fun restorePersistedQueueOnStartup() {
        val stored = loadPlaybackState() ?: return
        executor.execute {
            val restoredWithSourceIndices = stored.mediaIds.mapIndexedNotNull { sourceIndex, id ->
                runCatching { catalog.item(id) }
                    .onFailure { Log.w(TAG, "Queue startup restore skipped $id: ${it.message}") }
                    .getOrNull()
                    ?.let { sourceIndex to it }
            }
            if (restoredWithSourceIndices.isEmpty()) {
                Log.w(TAG, "Queue startup restore: no items resolved; saved state retained")
                return@execute
            }
            val restored = restoredWithSourceIndices.map { it.second }
            val target = QueueStartupRestorePolicy.selectTarget(
                savedIndex = stored.currentIndex,
                savedPositionMs = stored.positionMs,
                restoredSourceIndices = restoredWithSourceIndices.map { it.first },
            )
            if (target.positionMs == 0L && stored.positionMs > 0L) {
                Log.w(TAG, "Queue startup restore: selected track unavailable; discarding stale seek position")
            }
            mainHandler.post {
                if (serviceShuttingDown || !::player.isInitialized) {
                    return@post
                }
                if (!QueueStartupRestorePolicy.shouldApply(
                        serviceShuttingDown = serviceShuttingDown,
                        hasSession = librarySession != null,
                        currentQueueSize = player.mediaItemCount,
                    )
                ) {
                    Log.i(TAG, "Queue startup restore skipped: newer session queue exists")
                    return@post
                }
                // setMediaItems does not start playback or trigger an audio-focus request.
                player.setMediaItems(restored, target.index, target.positionMs)
                Log.i(TAG, "QUEUE_STARTUP_RESTORED: count=${restored.size}, index=${target.index}, pos=${target.positionMs}")
            }
        }
    }

    private fun persistPlaybackModes() {
        if (!::player.isInitialized) return
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHUFFLE_ENABLED, player.shuffleModeEnabled)
            .putInt(KEY_REPEAT_MODE, player.repeatMode)
            .apply()
    }

    private fun persistPlaybackState() {
        if (!::player.isInitialized || player.mediaItemCount <= 0) {
            return
        }
        val mediaIds = JSONArray()
        for (index in 0 until player.mediaItemCount) {
            val mediaId = player.getMediaItemAt(index).mediaId
            if (mediaId.isNotBlank()) {
                mediaIds.put(mediaId)
            }
        }
        if (mediaIds.length() == 0) {
            return
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_QUEUE_MEDIA_IDS, mediaIds.toString())
            .putInt(
                KEY_QUEUE_INDEX,
                player.currentMediaItemIndex.coerceAtLeast(0),
            )
            .putLong(
                KEY_QUEUE_POSITION_MS,
                player.currentPosition.coerceAtLeast(0L),
            )
            .apply()
    }

    private fun loadPlaybackState(): StoredPlaybackState? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val encoded = prefs.getString(KEY_QUEUE_MEDIA_IDS, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching {
            val array = JSONArray(encoded)
            val mediaIds = buildList {
                for (index in 0 until array.length()) {
                    array.optString(index)
                        .takeIf { it.isNotBlank() }
                        ?.let(::add)
                }
            }
            if (mediaIds.isEmpty()) {
                return@runCatching null
            }
            StoredPlaybackState(
                mediaIds = mediaIds,
                currentIndex = prefs.getInt(
                    KEY_QUEUE_INDEX,
                    0,
                ).coerceIn(0, mediaIds.lastIndex),
                positionMs = prefs.getLong(
                    KEY_QUEUE_POSITION_MS,
                    0L,
                ).coerceAtLeast(0L),
            )
        }.getOrNull()
    }

    private fun updatePersistenceLoop() {
        persistHandler.removeCallbacks(persistRunnable)
        if (::player.isInitialized && player.isPlaying) {
            persistHandler.postDelayed(
                persistRunnable,
                PERSIST_INTERVAL_MS,
            )
        }
    }

    private fun saveCurrentTrackResume(explicitPositionMs: Long? = null) {
        if (!::player.isInitialized || !::trackResumeStore.isInitialized) return
        val item = player.currentMediaItem ?: return
        val mediaId = TrackResumeStore.getMediaIdentity(item)
        if (mediaId.isBlank()) return

        val pos = explicitPositionMs ?: player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
            ?: item.mediaMetadata.extras?.getLong("duration_ms")?.takeIf { it > 0L }
            ?: 0L

        lastKnownPositionMs = pos
        lastKnownDurationMs = dur
        lastActiveMediaId = mediaId

        trackResumeStore.save(mediaId, pos, dur)
    }

    private fun evaluateAndApplyResume(mediaItem: MediaItem?, mediaId: String, durationMs: Long) {
        if (!::player.isInitialized || !::trackResumeStore.isInitialized) return
        val resumePos = trackResumeStore.get(mediaId, durationMs)
        if (resumePos != null && resumePos >= TrackResumePolicy.MIN_RESUME_POSITION_MS) {
            Log.i(TAG, "evaluateAndApplyResume: Resuming $mediaId at ${resumePos}ms (duration=${durationMs}ms)")
            isRestoringResume = true
            try {
                player.seekTo(resumePos)
                showResumeToast(resumePos)
            } finally {
                isRestoringResume = false
            }
        } else {
            Log.i(TAG, "evaluateAndApplyResume: No valid resume position for $mediaId (pos=$resumePos, duration=${durationMs}ms)")
        }
    }

    private fun showResumeToast(positionMs: Long) {
        mainHandler.post {
            val formatted = TrackResumePolicy.formatDuration(positionMs)
            Toast.makeText(this, "Tiếp tục từ $formatted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlaybackErrorToast(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Fetches authoritative metadata for the drive-file at [index] in the player queue,
     * then calls player.replaceMediaItem() on the main thread to update it in place.
     *
     * This is the lazy counterpart to the fast browse path:
     *   driveBrowse() returns filename-as-title for speed.
     *   driveFileItem() returns real Title/Artist/Album from the detail endpoint.
     *
     * A [resolvedDriveFileIds] set prevents re-fetching on repeat or re-navigation.
     * replaceMediaItem() on the current item is safe in ExoPlayer — it updates metadata
     * without interrupting the audio stream or causing a seek.
     */
    private fun lazyResolveDriveFile(index: Int) {
        if (!::player.isInitialized || index < 0 || index >= player.mediaItemCount) return
        val item = runCatching { player.getMediaItemAt(index) }.getOrNull() ?: return
        val mediaId = item.mediaId
        if (!mediaId.startsWith(RemoteCatalog.DRIVE_FILE_PREFIX)) return

        val driveFileId = mediaId
            .removePrefix(RemoteCatalog.DRIVE_FILE_PREFIX)
            .toIntOrNull() ?: return

        // Skip if already resolved this session.
        if (!resolvedDriveFileIds.add(driveFileId)) {
            Log.d(TAG, "lazyResolveDriveFile: id=$driveFileId already resolved, skip")
            return
        }

        val queueSize = player.mediaItemCount
        val startMs = SystemClock.elapsedRealtime()
        Log.i(
            TAG,
            "lazyResolveDriveFile: START index=$index/$queueSize, " +
                "driveFileId=$driveFileId, mediaId=$mediaId",
        )

        executor.submit {
            val resolved = runCatching { catalog.driveFileItem(driveFileId) }.getOrElse { exc ->
                Log.w(
                    TAG,
                    "lazyResolveDriveFile: FAILED id=$driveFileId, re-queuing",
                    exc,
                )
                // Allow retry on next transition.
                resolvedDriveFileIds.remove(driveFileId)
                return@submit
            }
            val elapsedMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                TAG,
                "lazyResolveDriveFile: DONE id=$driveFileId, " +
                    "elapsed=${elapsedMs}ms, " +
                    "title='${resolved.mediaMetadata.title}', " +
                    "artist='${resolved.mediaMetadata.artist}', " +
                    "album='${resolved.mediaMetadata.albumTitle}'",
            )
            mainHandler.post {
                if (serviceShuttingDown || !::player.isInitialized) return@post
                if (index >= player.mediaItemCount) return@post
                // Confirm this slot still holds the same media ID before replacing.
                val current = runCatching { player.getMediaItemAt(index) }.getOrNull()
                if (current?.mediaId == mediaId) {
                    player.replaceMediaItem(index, resolved)
                    if (index == player.currentMediaItemIndex) {
                        applyNormalizationForMediaItem(resolved)
                    }
                    Log.i(TAG, "lazyResolveDriveFile: replaceMediaItem index=$index id=$driveFileId")
                } else {
                    Log.i(
                        TAG,
                        "lazyResolveDriveFile: slot $index changed (was $mediaId, " +
                            "now ${current?.mediaId}), skip replace",
                    )
                }
            }
        }
    }

    private fun isRecoverableNetworkError(
        error: PlaybackException,
    ): Boolean = NetworkRecoveryPolicy.isRecoverableNetworkError(error)

    private fun recoverNetworkPlayback(
        error: PlaybackException,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (
            !NetworkRecoveryPolicy.canAttemptRecovery(
                isShuttingDown = serviceShuttingDown,
                isPlayerInitialized = ::player.isInitialized,
                queueSize = if (::player.isInitialized) player.mediaItemCount else 0,
                networkRecoveryInProgress = networkRecoveryInProgress,
                nowMs = now,
                lastNetworkRecoveryAttemptMs = lastNetworkRecoveryAttemptMs,
                errorCode = error.errorCode,
            )
        ) {
            return
        }

        val queue = buildList {
            for (index in 0 until player.mediaItemCount) {
                add(player.getMediaItemAt(index))
            }
        }
        if (queue.isEmpty()) {
            return
        }

        val currentIndex = player.currentMediaItemIndex
            .coerceIn(0, queue.lastIndex)
        val positionMs = player.currentPosition
            .coerceAtLeast(0L)
        val shouldResume = player.playWhenReady || player.isPlaying

        networkRecoveryInProgress = true
        lastNetworkRecoveryAttemptMs = now
        Log.w(
            TAG,
            "recoverNetworkPlayback: probing alternate backend after " +
                "errorCode=${error.errorCode}, mediaId=" +
                "${player.currentMediaItem?.mediaId}",
        )

        val future = executor.submit<List<MediaItem>> {
            val backend = catalog.backendLabel()
            Log.i(
                TAG,
                "recoverNetworkPlayback: selected backend=$backend",
            )
            queue.map(catalog::withCurrentQuality)
        }
        future.addListener(
            {
                val result = runCatching { future.get() }
                persistHandler.post {
                    networkRecoveryInProgress = false
                    if (serviceShuttingDown || !::player.isInitialized) {
                        return@post
                    }
                    result.onSuccess { refreshed ->
                        if (refreshed.isEmpty()) {
                            return@onSuccess
                        }
                        if (serviceShuttingDown || !::player.isInitialized) {
                            return@onSuccess
                        }
                        val targetIndex = currentIndex
                            .coerceIn(0, refreshed.lastIndex)
                        Log.i(
                            TAG,
                            "recoverNetworkPlayback: rebuilding queue " +
                                "size=${refreshed.size}, " +
                                "index=$targetIndex, pos=${positionMs}ms",
                        )
                        player.setMediaItems(
                            refreshed,
                            targetIndex,
                            positionMs,
                        )
                        player.prepare()
                        if (shouldResume) {
                            player.play()
                        }
                    }.onFailure { exc ->
                        Log.e(
                            TAG,
                            "recoverNetworkPlayback: failover failed",
                            exc,
                        )
                    }
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(
            TAG,
            "onStartCommand: action=${intent?.action}, flags=$flags, startId=$startId",
        )
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun currentQueueIds(): List<String> =
        (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }

    /**
     * Called only on the player application looper. A changed queue disables automatic
     * extension rather than rewriting a user-edited queue with older source results.
     */
    private fun syncBrowseQueue() {
        val actual = currentQueueIds()
        val incoming = incomingBrowseQueue
        if (incoming != null && incoming.second == actual) {
            browseQueueCursor?.cancel()
            browseQueueCursor = QueuePageCursor(incoming.first, actual)
            incomingBrowseQueue = null
            Log.i(TAG, "QUEUE_PAGING_START: source=${incoming.first.parentId}, nextPage=${incoming.first.nextPage}, size=${actual.size}")
        } else if (browseQueueCursor != null && browseQueueCursor?.isUnchanged(actual) != true) {
            Log.i(TAG, "QUEUE_PAGING_CANCEL: player queue edited/replaced")
            browseQueueCursor?.cancel()
            browseQueueCursor = null
            incomingBrowseQueue = null
        }
        maybeExtendBrowseQueue()
    }

    private fun maybeExtendBrowseQueue() {
        if (serviceShuttingDown || !::player.isInitialized || !::executor.isInitialized) return
        val cursor = browseQueueCursor ?: return
        val page = cursor.beginFetch(player.currentMediaItemIndex) ?: return
        executor.execute {
            val response = runCatching {
                if (cursor.seed.parentId == "search") {
                    catalog.search(cursor.seed.query, page, cursor.seed.pageSize)
                } else {
                    catalog.children(cursor.seed.parentId, page, cursor.seed.pageSize, cursor.seed.sort)
                }
            }
            mainHandler.post {
                if (serviceShuttingDown || browseQueueCursor !== cursor || !::player.isInitialized) {
                    return@post
                }
                if (!cursor.isUnchanged(currentQueueIds())) {
                    cursor.cancel()
                    browseQueueCursor = null
                    return@post
                }
                response.onSuccess { rows ->
                    val tracks = rows.filter {
                        it.mediaMetadata.isPlayable == true &&
                            (it.mediaId.startsWith(RemoteCatalog.TRACK_PREFIX) ||
                                it.mediaId.startsWith(RemoteCatalog.DRIVE_FILE_PREFIX) ||
                                it.mediaId.startsWith(RemoteCatalog.DEVICE_PREFIX))
                    }.map { item ->
                        val id = RemoteCatalog.canonicalTrackMediaId(item)
                            ?.removePrefix(RemoteCatalog.TRACK_PREFIX)?.toLongOrNull()
                        val offline = id?.let {
                            val manager = PinnedDownloadManager.getDefault(this)
                            if (manager.isOfflineReady(it)) manager.getRecord(it)?.let(manager::toMediaItem) else null
                        }
                        offline ?: catalog.withCurrentQuality(item)
                    }
                    if (!cursor.acceptPage(page, rows.size, tracks.map { it.mediaId })) {
                        return@onSuccess
                    }
                    if (tracks.isNotEmpty()) {
                        player.addMediaItems(tracks)
                        Log.i(TAG, "QUEUE_PAGING_APPEND: page=$page, appended=${tracks.size}, queue=${player.mediaItemCount}")
                    } else {
                        Log.i(TAG, "QUEUE_PAGING_EMPTY_ROWS: page=$page, sourceRows=${rows.size}")
                    }
                    // In a mixed folder-only page we may still have more playable tracks.
                    // Delay the next request to avoid a tight loop or a busy server.
                    if (rows.isNotEmpty() && tracks.isEmpty() && !cursor.endReached) {
                        mainHandler.postDelayed({ maybeExtendBrowseQueue() }, 350L)
                    }
                }.onFailure { error ->
                    cursor.fail(page)
                    Log.w(TAG, "QUEUE_PAGING_PAUSED: page=$page; waiting for explicit user action", error)
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(
            TAG,
            "onTaskRemoved: rootIntent=$rootIntent, isPlaying=${player.isPlaying}, " +
                "queueSize=${player.mediaItemCount}",
        )
        persistPlaybackState()
        saveCurrentTrackResume()
        // On car head units, do NOT stop service when paused and task is removed;
        // keeping the service alive ensures hardware steering wheel media keys can resume playback.
        if (player.isPlaying) {
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaLibrarySession? = librarySession

    fun applyNormalizationForMediaItem(mediaItem: MediaItem?) {
        gainAudioProcessor.resetGain()
        gainAudioProcessor.applyMediaItem(mediaItem, gainAudioProcessor.isNormalizationEnabled)
        val mediaId = mediaItem?.mediaId ?: "none"
        val appliedGain = gainAudioProcessor.appliedGainDb
        Log.i(
            TAG,
            "applyNormalizationForMediaItem: mediaId=$mediaId, " +
                "enabled=${gainAudioProcessor.isNormalizationEnabled}, " +
                "appliedGainDb=${appliedGain}dB, mult=${gainAudioProcessor.gainMultiplier}",
        )
    }

    override fun onDestroy() {
        serviceShuttingDown = true
        browseQueueCursor?.cancel()
        browseQueueCursor = null
        incomingBrowseQueue = null
        Log.i(TAG, "onDestroy: Tearing down MusicService")
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        gainAudioProcessor.reset()
        persistPlaybackState()
        persistPlaybackModes()
        saveCurrentTrackResume()
        persistHandler.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)
        librarySession?.release()
        librarySession = null
        player.release()
        executor.shutdownNow()
        super.onDestroy()
    }
}

private class LibraryCallback(
    private val context: Context,
    private val catalog: RemoteCatalog,
    private val executor: ListeningExecutorService,
    private val player: ExoPlayer,
    private val storedPlaybackState: () -> StoredPlaybackState?,
    private val onSessionRestore: (String?) -> Unit = {},
    private val onBrowseQueueRequested: (QueueBrowseSeed?, List<String>) -> Unit = { _, _ -> },
) : MediaLibrarySession.Callback {

    companion object {
        private const val TAG = "LibraryCallback"
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        Log.i(
            TAG,
            "onConnect: pkg=${controller.packageName}, " +
                "uid=${controller.uid}, isAuto=${session.isAutomotiveController(controller)}",
        )
        val defaultResult = super.onConnect(session, controller)
        val availableSessionCommands = defaultResult.availableSessionCommands
            .buildUpon()
            .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
            .add(SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE)
            .add(SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE)
            .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)
            .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)
            .add(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)
            .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT)
            .build()
        val availablePlayerCommands = defaultResult.availablePlayerCommands.buildUpon()
            .addAll(player.availableCommands)
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .add(Player.COMMAND_SET_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SET_SHUFFLE_MODE)
            .add(Player.COMMAND_SET_REPEAT_MODE)
            .build()

        Log.i(
            TAG,
            "onConnect: libraryReadCommands granted=" +
                listOf(
                    SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT,
                    SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE,
                    SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE,
                    SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN,
                    SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM,
                    SessionCommand.COMMAND_CODE_LIBRARY_SEARCH,
                    SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT,
                ).all(availableSessionCommands::contains),
        )

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(availableSessionCommands)
            .setAvailablePlayerCommands(availablePlayerCommands)
            .apply {
                if (session.sessionActivity != null) {
                    setSessionActivity(session.sessionActivity)
                }
            }
            .build()
    }

    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent,
    ): Boolean {
        val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent::class.java,
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

        Log.i(
            TAG,
            "onMediaButtonEvent: action=${intent.action}, keyEvent=$keyEvent, " +
                "controller=${controllerInfo.packageName}, isPlaying=${player.isPlaying}, " +
                "playbackState=${player.playbackState}, playWhenReady=${player.playWhenReady}, " +
                "queueSize=${player.mediaItemCount}",
        )

        if (keyEvent != null && MediaButtonHandler.handleKeyEvent(keyEvent, player)) {
            return true
        }

        return super.onMediaButtonEvent(session, controllerInfo, intent)
    }

    override fun onPlaybackResumption(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        Log.i(
            TAG,
            "onPlaybackResumption: controller=${controllerInfo.packageName}, " +
                "queueSize=${player.mediaItemCount}, isPlaying=${player.isPlaying}",
        )

        if (player.mediaItemCount > 0) {
            val currentItems = buildList {
                for (i in 0 until player.mediaItemCount) {
                    add(player.getMediaItemAt(i))
                }
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    currentItems,
                    player.currentMediaItemIndex.coerceAtLeast(0),
                    player.currentPosition.coerceAtLeast(0L),
                )
            )
        }

        return executor.submit<MediaSession.MediaItemsWithStartPosition> {
            val stored = storedPlaybackState()
            if (stored != null) {
                val restoredItems = stored.mediaIds.mapNotNull { mediaId ->
                    runCatching {
                        catalog.item(mediaId)
                    }.getOrNull()
                }
                if (restoredItems.isNotEmpty()) {
                    val targetMediaId = stored.mediaIds.getOrNull(
                        stored.currentIndex
                    )
                    onSessionRestore(targetMediaId)
                    val restoredIndex = restoredItems.indexOfFirst {
                        it.mediaId == targetMediaId
                    }.takeIf { it >= 0 } ?: 0
                    Log.i(
                        TAG,
                        "onPlaybackResumption: restored " +
                            "${restoredItems.size} persisted queue items, " +
                            "index=$restoredIndex, pos=${stored.positionMs}ms",
                    )
                    return@submit MediaSession.MediaItemsWithStartPosition(
                        restoredItems,
                        restoredIndex,
                        stored.positionMs,
                    )
                }
            }

            val recentItems = catalog.children(
                RemoteCatalog.RECENT_ID,
                0,
                30,
            ).filter {
                it.mediaMetadata.isPlayable == true
            }
            if (recentItems.isNotEmpty()) {
                MediaSession.MediaItemsWithStartPosition(
                    recentItems,
                    0,
                    0L,
                )
            } else {
                throw UnsupportedOperationException("No resume items available")
            }
        }
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(
            LibraryResult.ofItem(
                catalog.rootItem(),
                params,
            )
        )

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        executor.submit<LibraryResult<ImmutableList<MediaItem>>> {
            val sort = params?.extras?.getString("sort").orEmpty()
            val items = if (
                parentId == RemoteCatalog.ROOT_ID
            ) {
                catalog.rootChildren()
            } else {
                catalog.children(
                    parentId,
                    page,
                    pageSize,
                    sort,
                )
            }
            LibraryResult.ofItemList(
                items,
                params,
            )
        }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        executor.submit<LibraryResult<MediaItem>> {
            val item = catalog.item(mediaId)
            if (item != null) {
                LibraryResult.ofItem(item, null)
            } else {
                LibraryResult.ofError(
                    LibraryResult.RESULT_ERROR_BAD_VALUE
                )
            }
        }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> =
        executor.submit<MutableList<MediaItem>> {
            val first = mediaItems.firstOrNull()
            val extras = first?.mediaMetadata?.extras
            val parent = extras?.getString(QueueBrowseSeed.KEY_PARENT).orEmpty()
            val query = extras?.getString(QueueBrowseSeed.KEY_QUERY).orEmpty()
            val nextPage = extras?.getInt(QueueBrowseSeed.KEY_NEXT_PAGE, 0) ?: 0
            val pageSize = extras?.getInt(QueueBrowseSeed.KEY_PAGE_SIZE, 0) ?: 0
            val seed = if (QueueBrowseSeed.supports(parent, query) &&
                nextPage > 0 && pageSize in 1..100 && mediaItems.isNotEmpty()
            ) {
                QueueBrowseSeed(
                    parentId = parent,
                    sort = extras?.getString(QueueBrowseSeed.KEY_SORT).orEmpty(),
                    query = query,
                    nextPage = nextPage,
                    pageSize = pageSize,
                )
            } else null
            onBrowseQueueRequested(seed, mediaItems.map { it.mediaId })
            val queueSize = mediaItems.size
            val driveFileCount = mediaItems.count { it.mediaId.startsWith(RemoteCatalog.DRIVE_FILE_PREFIX) }
            Log.i(
                TAG,
                "onAddMediaItems: queueSize=$queueSize, " +
                    "driveFileItems=$driveFileCount (metadata to be resolved lazily via onMediaItemTransition), " +
                    "otherItems=${queueSize - driveFileCount}",
            )
            mediaItems.mapNotNull { requested ->
                val mediaId = requested.mediaId
                when {
                    mediaId.startsWith(RemoteCatalog.DEVICE_PREFIX) -> {
                        if (requested.localConfiguration != null) requested else catalog.item(mediaId)
                    }
                    // Media3 IPC may strip localConfiguration/URI from controller MediaItems.
                    // Rebuild the deterministic drive stream URI locally without any network/detail
                    // request, then resolve authoritative metadata lazily after playback starts.
                    // This keeps queue setup at 0 detail requests and preserves fast track switching.
                    mediaId.startsWith(RemoteCatalog.DRIVE_FILE_PREFIX) -> {
                        val trackId = requested.mediaMetadata.extras?.getInt("track_id", 0)?.toLong() ?: 0L
                        val dm = PinnedDownloadManager.getDefault(context)
                        if (trackId > 0L && dm.isOfflineReady(trackId)) {
                            dm.getRecord(trackId)?.let { dm.toMediaItem(it) } ?: catalog.withCurrentQuality(requested)
                        } else {
                            if (requested.localConfiguration == null) {
                                Log.i(
                                    TAG,
                                    "onAddMediaItems: drive-file URI missing across IPC; " +
                                        "rebuilding locally for mediaId=$mediaId",
                                )
                            }
                            catalog.withCurrentQuality(requested)
                        }
                    }
                    // MeloVault remote tracks: resolve to offline file IF offline ready, otherwise remote stream!
                    mediaId.startsWith(RemoteCatalog.TRACK_PREFIX) -> {
                        val trackId = mediaId.removePrefix(RemoteCatalog.TRACK_PREFIX).toLongOrNull() ?: -1L
                        val dm = PinnedDownloadManager.getDefault(context)
                        if (trackId > 0L && dm.isOfflineReady(trackId)) {
                            val rec = dm.getRecord(trackId)
                            if (rec != null) {
                                dm.toMediaItem(rec)
                            } else {
                                catalog.item(mediaId) ?: catalog.withCurrentQuality(requested)
                            }
                        } else {
                            // If track is NOT offline ready (e.g. SD card unmounted or local download deleted):
                            // discard any stale file:// URI and resolve fresh remote stream from catalog.
                            if (requested.localConfiguration?.uri?.scheme == "file" ||
                                requested.mediaMetadata.extras?.getBoolean("is_offline_download") == true ||
                                requested.localConfiguration == null
                            ) {
                                runCatching { catalog.item(mediaId) }.getOrNull() ?: catalog.withCurrentQuality(requested)
                            } else {
                                requested
                            }
                        }
                    }
                    // Local file or content URI: keep as-is.
                    requested.localConfiguration?.uri?.scheme == "file" ||
                    requested.localConfiguration?.uri?.scheme == "content" -> requested
                    // Non-drive items already fully resolved (stream URI set): keep as-is.
                    requested.localConfiguration != null -> requested
                    // track:/category: items without a URI: resolve via catalog.item().
                    else -> runCatching { catalog.item(mediaId) }.getOrNull()
                }
            }.toMutableList()
        }
}
