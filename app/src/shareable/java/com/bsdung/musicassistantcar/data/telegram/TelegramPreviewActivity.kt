package com.bsdung.musicassistantcar.data.telegram

import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.widget.FrameLayout
import android.widget.ImageView
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.bsdung.musicassistantcar.BuildConfig
import com.bsdung.musicassistantcar.R
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject

/**
 * Internal preview for the user's Saved Messages and Telegram audio documents
 * searched in chats available to their authenticated user account, plus
 * user-selected SAF documents. No direct Drive OAuth or private backend.
 * User must authenticate on-device; app-level developer API config is not
 * a Telegram user session.
 */
class TelegramPreviewActivity : AppCompatActivity() {
    private data class Song(
        val fileId: Int,
        val title: String,
        val filename: String,
        val messageId: Long,
        val chatId: Long,
        val expectedSize: Long = -1L,
        val rawMessageId: Long = 0L,
        val performer: String? = null,
        val durationSeconds: Int = 0,
        val sourceTitle: String? = null,
    ) {
        val genuineMessageId: Long get() = if (rawMessageId > 0L) rawMessageId else messageId
    }

    private class SongRowViewHolder(
        val artFrame: FrameLayout,
        val artImage: ImageView,
        val artPlaceholder: TextView,
        val titleView: TextView,
        val subtitleView: TextView
    )

    private val thumbnailCache = android.util.LruCache<Long, Bitmap>(60)
    private val thumbnailExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "MeloVault-ThumbLoader").apply { priority = Thread.MIN_PRIORITY }
    }
    private val library = TelegramAccountLibrary()
    private val auth = TelegramAuthorizationFlow(library)
    private lateinit var status: TextView
    private lateinit var note: TextView
    private lateinit var authControls: LinearLayout
    private lateinit var nowPlayingTitle: TextView
    private lateinit var playPause: Button
    private var fullPlayer: NowPlayingFullScreenDialog? = null
    private var queueDialog: Dialog? = null
    private var currentSongIndex = -1
    private lateinit var input: EditText
    private lateinit var submit: Button
    private lateinit var qrLogin: Button
    private lateinit var refresh: Button
    private lateinit var globalSearch: Button
    private lateinit var libraryTitle: TextView
    private lateinit var mainRoot: LinearLayout
    private lateinit var miniPlayerBar: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var overflowButton: Button
    private lateinit var miniControls: LinearLayout
    private var isShortLayout = false
    private var isWideLayout = false
    private var isNarrowLayout = false
    private var currentPaneMode: CarPaneLayoutPolicy.Mode = CarPaneLayoutPolicy.Mode.MEDIUM
    private var currentThemePreset: UiThemePolicy.ThemePreset = UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO
    private var isCockpitTwoPaneActive = false
    private lateinit var splitRoot: LinearLayout
    private lateinit var libraryContainer: LinearLayout
    private lateinit var cockpitScrollView: ScrollView
    private lateinit var cockpitPane: LinearLayout
    private lateinit var cockpitDivider: View
    private lateinit var cockpitArtFrame: FrameLayout
    private lateinit var cockpitArtImage: ImageView
    private lateinit var cockpitArtPlaceholder: TextView
    private lateinit var cockpitTitle: TextView
    private lateinit var cockpitSubtitle: TextView
    private lateinit var cockpitFormatBadge: TextView
    private lateinit var cockpitTimeElapsed: TextView
    private lateinit var cockpitTimeTotal: TextView
    private lateinit var cockpitSeekBar: SeekBar
    private lateinit var cockpitPrevButton: Button
    private lateinit var cockpitPlayPauseButton: Button
    private lateinit var cockpitNextButton: Button
    private lateinit var cockpitShuffleButton: Button
    private lateinit var cockpitRepeatButton: Button
    private lateinit var cockpitFavoriteButton: Button
    private lateinit var cockpitQueueButton: Button
    private var cockpitTrackingTouch = false
    private lateinit var filterChipsRow: LinearLayout
    private lateinit var chipAll: Button
    private lateinit var chipDownloaded: Button
    private var filterOnlyDownloaded = false
    private var lastSystemBarsInsets = WindowInsetsPolicy.Insets.ZERO
    private var lastImeInsets = WindowInsetsPolicy.Insets.ZERO
    private lateinit var syncSubtitle: TextView
    private lateinit var bottomNavBar: LinearLayout
    private val bottomNavButtons = mutableListOf<Button>()
    private lateinit var miniProgressBar: ProgressBar
    private lateinit var miniArtFrame: FrameLayout
    private lateinit var miniArtImage: ImageView
    private lateinit var miniArtPlaceholder: TextView
    private lateinit var nowPlayingSubtitle: TextView
    private lateinit var titleHeader: TextView
    private lateinit var navigationRow: LinearLayout
    private lateinit var miniOpenPlayer: Button
    private lateinit var collapsibleNavigation: LinearLayout
    private var navigationCollapsed = false
    private var gestureLastY = 0f
    private var gestureAccumulation = 0f
    private var pendingNavigationCollapse: Boolean? = null
    private var listScrollState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE
    private lateinit var sourcesButton: Button
    private lateinit var songFilter: EditText
    private lateinit var openDocument: Button
    private lateinit var favoriteButton: Button
    private lateinit var playlistButton: Button
    private lateinit var logout: Button
    private lateinit var tracks: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var sourcesAdapter: ArrayAdapter<String>
    private var visibleSourceIds: List<Long> = emptyList()
    private var browseSources = false
    private var selectedChatId: Long? = null
    private val sourceNames = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private val requestedSourceNames =
        java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private var player: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controllerReleased = false
    private data class PendingPlayback(val items: List<MediaItem>, val startIndex: Int)
    private var pendingPlayback: PendingPlayback? = null
    private val cachedSongPaths = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var libraryDb: TelegramLibraryDao? = null
    private var syncCoordinator: TelegramSyncCoordinator? = null
    private val dbExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val uiQueryGeneration = java.util.concurrent.atomic.AtomicLong(1L)
    @Volatile private var totalCountForCurrentView: Int = 0
    private var isLoadingMoreSongs = false

    private fun executeOnDb(task: () -> Unit) {
        if (isDestroyed || isFinishing) return
        try {
            if (!dbExecutor.isShutdown && !dbExecutor.isTerminated) {
                dbExecutor.execute {
                    if (isDestroyed || isFinishing) return@execute
                    try {
                        task()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
        }
    }

    private val songs = mutableListOf<Song>()
    private var shownSongs: List<Song> = emptyList()
    private var queueSongOrder: List<Song> = emptyList()
    private val playlistStore by lazy { TelegramSavedPlaylistStore(noBackupFilesDir) }
    private var favoriteIds: List<Long> = emptyList()
    private var playlistMode = false
    private var authEpoch: Long = 0L
    private var tdlibParametersSent = false
    @Volatile private var nativeClientId = 0
    @Volatile private var running = false
    @Volatile private var exiting = false
    private var savedChatId: Long = 0L
    private var historyCursorId: Long = 0L
    private var historyPages: Int = 0
    private var historyLoading = false
    private val autoRecoveryPolicy = TelegramAutoRecoveryPolicy()
    private val seenSongMessageIds = mutableSetOf<Long>()
    private val maxHistoryPages = 24
    private var allChatsMode = false
    private var globalScanGeneration = 0
    private var globalScanPhase = "audio"
    private var globalScanOffset = ""
    private var globalScanPages = 0
    private var globalScanLoading = false
    private val maxGlobalPagesPerKind = 15
    private var downloadWaiting: Int? = null
    private var prefetchWaiting: Int? = null
    private lateinit var filesRoot: File
    private var currentTask = ""
    private var mutedMessages = false
    private var nativeReceiver: ((JSONObject) -> Unit)? = null

    // Android's system picker grants access only to the selected document.
    // Deliberately do not persist or log provider URIs or account tokens.
    private val openDocumentResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || !auth.isReady()) {
            return@registerForActivityResult
        }
        val uri = result.data?.data ?: return@registerForActivityResult
        val fileName = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.take(96) ?: "Tệp âm thanh"
        val mime = runCatching { contentResolver.getType(uri).orEmpty() }
            .getOrDefault("")
        val allowed = listOf(".mp3", ".flac", ".wav", ".m4a", ".ogg", ".opus")
            .any { fileName.endsWith(it, ignoreCase = true) } || mime.startsWith("audio/")
        if (!allowed) {
            showMessage("Chỉ hỗ trợ chọn tệp âm thanh.")
            return@registerForActivityResult
        }
        // File remains under the selected account/provider's control. No copy
        // into TDLib's isolated cache, and no persistent URI permission.
        downloadWaiting = null
        currentSongIndex = -1
        enqueuePlayback(uri, fileName)
        showMessage("Đang mở tệp: $fileName")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val firstVisible = if (::tracks.isInitialized) tracks.firstVisiblePosition else 0
        val firstOffset = if (::tracks.isInitialized) tracks.getChildAt(0)?.top ?: 0 else 0
        super.onConfigurationChanged(newConfig)
        // Multi-window car head units change the playback pane size often.
        // Keep TDLib and the MediaSession alive; rebuild only the dialog chrome.
        val fullWasShowing = fullPlayer?.isShowing() == true
        val queueWasShowing = queueDialog?.isShowing == true
        fullPlayer?.dismiss()
        fullPlayer = null
        queueDialog?.dismiss()
        queueDialog = null
        applyResponsiveLibraryUi()
        if (::tracks.isInitialized) {
            tracks.post { if (!isDestroyed) tracks.setSelectionFromTop(firstVisible, firstOffset) }
        }
        if (fullWasShowing) showFullPlayer()
        if (queueWasShowing) showUpcomingQueue()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("key_all_chats_mode", allChatsMode)
        outState.putBoolean("key_browse_sources", browseSources)
        outState.putBoolean("key_playlist_mode", playlistMode)
        selectedChatId?.let { outState.putLong("key_selected_chat_id", it) }
        if (::songFilter.isInitialized) {
            songFilter.text?.toString()?.takeIf { it.isNotEmpty() }?.let {
                outState.putString("key_filter_query", it)
            }
        }
        if (::tracks.isInitialized) {
            outState.putInt("key_list_pos", tracks.firstVisiblePosition)
            outState.putInt("key_list_offset", tracks.getChildAt(0)?.top ?: 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_MusicAssistantCar)
        val prefs = getSharedPreferences(UiThemePolicy.PREFS_NAME, Context.MODE_PRIVATE)
        currentThemePreset = UiThemePolicy.getSavedPreset(prefs)
        buildUi()
        if (savedInstanceState != null) {
            allChatsMode = savedInstanceState.getBoolean("key_all_chats_mode", allChatsMode)
            browseSources = savedInstanceState.getBoolean("key_browse_sources", browseSources)
            playlistMode = savedInstanceState.getBoolean("key_playlist_mode", playlistMode)
            if (savedInstanceState.containsKey("key_selected_chat_id")) {
                selectedChatId = savedInstanceState.getLong("key_selected_chat_id")
            }
            savedInstanceState.getString("key_filter_query")?.let {
                if (it.isNotEmpty()) songFilter.setText(it)
            }
            val pos = savedInstanceState.getInt("key_list_pos", -1)
            val offset = savedInstanceState.getInt("key_list_offset", 0)
            if (pos >= 0) {
                tracks.post { if (!isDestroyed) tracks.setSelectionFromTop(pos, offset) }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (allChatsMode && (!browseSources || playlistMode)) {
                    playlistMode = false
                    browseSources = true
                    selectedChatId = null
                    songFilter.text?.clear()
                    updatePlaylistButtons()
                    refreshSongRows()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        connectPlaybackService()
        if (BuildConfig.TELEGRAM_APP_API_ID <= 0 ||
            BuildConfig.TELEGRAM_APP_API_HASH.length != 32) {
            showMessage("Chưa cấu hình Telegram API cho bản thử nội bộ.")
            return
        }
        initNative()
    }

    override fun onResume() {
        super.onResume()
        if (auth.isReady()) {
            val user = library.authorizedUser()?.value ?: 0L
            if (user > 0L && songs.isEmpty()) {
                loadDatabaseIndex(user) {
                    checkAutoRecovery()
                }
            } else {
                checkAutoRecovery()
            }
        }
    }

    private val playbackListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            showMessage("Không phát được tệp âm thanh (Media3 mã ${error.errorCode}).")
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (::playPause.isInitialized) playPause.text = if (isPlaying) "Ⅱ" else "▶"
            updateCockpitPlayer()
            if (::mainRoot.isInitialized) {
                if (isPlaying) mainRoot.post(miniPlayerProgressTick)
                else mainRoot.removeCallbacks(miniPlayerProgressTick)
            }
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val titleText = mediaItem?.mediaMetadata?.title ?: "Chọn một bài hát để bắt đầu"
            nowPlayingTitle.text = titleText
            if (::nowPlayingSubtitle.isInitialized) {
                val artist = mediaItem?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
                nowPlayingSubtitle.text = if (mediaItem != null) {
                    listOfNotNull(artist, "Telegram · Đang phát").joinToString(" · ")
                } else "Telegram · MeloVault"
            }
            updateMiniPlayerProgress()
            updateMiniPlayerCover(mediaItem)
            updateCockpitPlayer()
            val id = mediaItem?.mediaId?.removePrefix("telegram:")?.toLongOrNull()
            currentSongIndex = if (mediaItem?.mediaId?.startsWith("telegram:") == true) {
                songs.indexOfFirst { it.messageId == id }
            } else -1
            updatePlaylistButtons()
            if (::adapter.isInitialized) adapter.notifyDataSetChanged()
            if (currentSongIndex >= 0 && auth.isReady()) {
                prefetchNextCachedNeighbor()
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                playAdjacent(1)
            }
            updateMiniPlayerProgress()
            updateCockpitPlayer()
        }
    }

    private fun connectPlaybackService() {
        val token = SessionToken(this, ComponentName(this, ShareablePlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        Futures.addCallback(future, object : FutureCallback<MediaController> {
            override fun onSuccess(controller: MediaController?) {
                if (controller == null || controllerReleased) return
                player = controller
                library.authorizedUser()?.value?.let { restorePlayingQueueIfAny(it) }
                controller.addListener(playbackListener)
                playbackListener.onIsPlayingChanged(controller.isPlaying)
                updatePlaylistButtons()
                nowPlayingTitle.text = controller.currentMediaItem?.mediaMetadata?.title
                    ?: "Chọn một bài hát để bắt đầu"
                updateMiniPlayerCover(controller.currentMediaItem)
                updateCockpitPlayer()
                pendingPlayback?.let { pending ->
                    pendingPlayback = null
                    startPlayback(controller, pending.items, pending.startIndex)
                }
            }

            override fun onFailure(t: Throwable) {
                if (!controllerReleased) showMessage("Không kết nối được trình phát nền.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun mediaItem(uri: Uri, title: String, mediaId: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()

    private fun enqueuePlayback(uri: Uri, title: String) {
        enqueueItems(listOf(mediaItem(uri, title, "picked:audio")), 0)
    }

    private fun enqueueTelegramPlayback(song: Song, target: File) {
        cachedSongPaths[song.messageId] = target.canonicalPath
        // Playback queue follows only the selected source (and visible title
        // filter), never silently jumps into a different Telegram channel.
        queueSongOrder = TelegramQueueRetentionPolicy.forSelected(
            shownSongs, queueSongOrder, song.messageId
        ) { it.messageId }.ifEmpty { listOf(song) }
        library.authorizedUser()?.let { owner ->
            ShareablePlaybackService.configureTelegramQueue(
                owner.value, filesRoot, queueSongOrder.map { entry ->
                    ShareablePlaybackService.QueueTrack(
                        entry.messageId, entry.fileId, entry.title,
                        cachedSongPaths[entry.messageId], entry.filename,
                        entry.chatId, entry.expectedSize, entry.genuineMessageId
                    )
                }
            )
        }
        // Only contiguous cached neighbors may enter Media3's transport queue:
        // skipping a missing file to jump to a distant cached song is incorrect.
        val selected = queueSongOrder.indexOfFirst { it.messageId == song.messageId }
        if (selected < 0) {
            enqueueItems(listOf(mediaItem(Uri.fromFile(target), song.title,
                "telegram:${song.messageId}")), 0)
            return
        }
        fun playableAt(index: Int): MediaItem? {
            val item = queueSongOrder.getOrNull(index) ?: return null
            val path = cachedSongPaths[item.messageId] ?: return null
            val file = File(path)
            if (!file.isFile || !TelegramAccountStoragePolicy.isOwnedFile(filesRoot, file)) {
                cachedSongPaths.remove(item.messageId)
                return null
            }
            return mediaItem(Uri.fromFile(file), item.title, "telegram:${item.messageId}")
        }
        val orderIds = queueSongOrder.map { it.messageId }
        val indices = TelegramContiguousQueuePolicy.indices(orderIds, song.messageId) {
            id -> id == song.messageId || run {
                val path = cachedSongPaths[id] ?: return@run false
                val file = File(path)
                file.isFile && TelegramAccountStoragePolicy.isOwnedFile(filesRoot, file)
            }
        }
        val contiguous = indices.mapNotNull { index ->
            if (index == selected) mediaItem(Uri.fromFile(target), song.title,
                "telegram:${song.messageId}")
            else playableAt(index)
        }
        enqueueItems(contiguous, indices.indexOf(selected).coerceAtLeast(0))
    }

    private fun enqueueProgressivePlayback(song: Song) {
        // This is a growing private TDLib file, not a public Telegram URL.
        // The Media3 source waits only for missing bytes; do not wait for the
        // full mix or silently pretend the file is fully cached.
        queueSongOrder = TelegramQueueRetentionPolicy.forSelected(
            shownSongs, queueSongOrder, song.messageId
        ) { it.messageId }.ifEmpty { listOf(song) }
        library.authorizedUser()?.let { owner ->
            ShareablePlaybackService.configureTelegramQueue(
                owner.value, filesRoot, queueSongOrder.map { entry ->
                    ShareablePlaybackService.QueueTrack(
                        entry.messageId, entry.fileId, entry.title,
                        cachedSongPaths[entry.messageId], entry.filename,
                        entry.chatId, entry.expectedSize, entry.genuineMessageId
                    )
                }
            )
        }
        val ext = song.filename.substringAfterLast('.', "mp3")
            .lowercase().takeIf { it in setOf(
                "mp3", "flac", "wav", "m4a", "ogg", "opus", "aac"
            ) } ?: "mp3"
        val progressive = mediaItem(
            TelegramProgressiveCache.makeUri(song.fileId, ext),
            song.title, "telegram:${song.messageId}"
        )
        enqueueItems(listOf(progressive), 0)
    }

    private fun enqueueItems(items: List<MediaItem>, startIndex: Int) {
        if (items.isEmpty()) return
        nowPlayingTitle.text = items[startIndex].mediaMetadata.title ?: ""
        val controller = player
        if (controller != null) startPlayback(controller, items, startIndex)
        else pendingPlayback = PendingPlayback(items, startIndex)
    }

    private fun startPlayback(controller: MediaController, items: List<MediaItem>,
                              startIndex: Int) {
        controller.setMediaItems(items, startIndex, C.TIME_UNSET)
        controller.prepare()
        controller.play()
    }

    private fun clearPlaybackSession() {
        ShareablePlaybackService.clearTelegramQueue()
        cachedSongPaths.clear()
        queueSongOrder = emptyList()
        pendingPlayback = null
        player?.let { controller ->
            controller.stop()
            controller.clearMediaItems()
        }
        prefetchWaiting = null
        // A background session must never retain a logged-out account's music.
        stopService(Intent(this, ShareablePlaybackService::class.java))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private val themeTokens get() = UiThemePolicy.tokensFor(currentThemePreset)

    private val ink get() = themeTokens.text
    private val subtle get() = themeTokens.subtle
    private val accent get() = themeTokens.accent
    private val midnight get() = themeTokens.background
    private val panel get() = themeTokens.surface
    private val card get() = themeTokens.card
    private val border get() = themeTokens.border
    private val accentContrast get() = themeTokens.accentContrast
    private val cardPlaying get() = themeTokens.cardPlaying

    private val miniPlayerProgressTick = object : Runnable {
        override fun run() {
            updateMiniPlayerProgress()
            updateCockpitPlayer()
            if (player?.isPlaying == true) {
                if (::mainRoot.isInitialized) mainRoot.postDelayed(this, 1000L)
            }
        }
    }

    private fun updateMiniPlayerProgress() {
        if (!::miniProgressBar.isInitialized) return
        val p = player
        val dur = p?.duration ?: C.TIME_UNSET
        val pos = p?.currentPosition ?: 0L
        if (dur > 0L && dur != C.TIME_UNSET) {
            miniProgressBar.progress = ((pos.toDouble() / dur) * 1000).toInt().coerceIn(0, 1000)
        } else {
            miniProgressBar.progress = 0
        }
    }

    private fun applyRootWindowInsets() {
        if (!::mainRoot.isInitialized) return
        val computed = WindowInsetsPolicy.computeMainRootPadding(
            systemBars = lastSystemBarsInsets,
            ime = lastImeInsets,
            displayCutout = WindowInsetsPolicy.Insets.ZERO,
            baseHorizontal = dp(if (isNarrowLayout) 6 else 12),
            baseTop = dp(if (isShortLayout) 2 else 6),
            baseBottom = dp(3)
        )
        mainRoot.setPadding(computed.left, computed.top, computed.right, computed.bottom)
    }

    private fun updateSyncHeaderSubtitle() {
        if (!::syncSubtitle.isInitialized) return
        val isSyncing = globalScanLoading || syncCoordinator?.isBusy() == true
        val isAuth = auth.isReady()
        val isSources = (allChatsMode && browseSources && !playlistMode)
        val text = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = shownSongs.size,
            totalIndexedCount = totalCountForCurrentView,
            isSyncing = isSyncing,
            isAuthenticated = isAuth,
            isSourcesMode = isSources,
            sourceCount = ShortWindowLayoutPolicy.truthfulSourceCount(visibleSourceIds),
            selectedSourceTitle = selectedChatId?.let { sourceNames[it] }
        )
        syncSubtitle.text = text
    }

    private fun bindSongThumbnail(
        messageId: Long,
        localFile: File,
        imageView: ImageView,
        placeholder: TextView
    ) {
        val cached = thumbnailCache.get(messageId)
        if (cached != null) {
            imageView.tag = messageId
            imageView.setImageBitmap(cached)
            placeholder.visibility = View.GONE
            return
        }
        imageView.tag = messageId
        imageView.setImageDrawable(null)
        placeholder.visibility = View.VISIBLE

        thumbnailExecutor.execute {
            if (isDestroyed || isFinishing) return@execute
            val bitmap = runCatching {
                val extractor = MediaMetadataRetriever()
                try {
                    extractor.setDataSource(localFile.path)
                    val bytes = extractor.embeddedPicture
                    if (bytes != null && bytes.isNotEmpty()) {
                        val sample = TelegramSongDisplayPolicy.computeSample(bytes.size, 96, 96)
                        BitmapFactory.decodeByteArray(
                            bytes, 0, bytes.size,
                            BitmapFactory.Options().apply { inSampleSize = sample }
                        )
                    } else null
                } finally {
                    extractor.release()
                }
            }.getOrNull()

            if (bitmap != null) {
                thumbnailCache.put(messageId, bitmap)
                runOnUiThread {
                    if (!isDestroyed && imageView.tag == messageId) {
                        imageView.setImageBitmap(bitmap)
                        placeholder.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun updateMiniPlayerCover(mediaItem: MediaItem?) {
        if (!::miniArtImage.isInitialized || !::miniArtPlaceholder.isInitialized) return
        if (mediaItem == null) {
            miniArtImage.setImageDrawable(null)
            miniArtPlaceholder.visibility = View.VISIBLE
            return
        }
        val id = mediaItem.mediaId.removePrefix("telegram:").toLongOrNull()
        if (id != null) {
            val cached = thumbnailCache.get(id)
            if (cached != null) {
                miniArtImage.setImageBitmap(cached)
                miniArtPlaceholder.visibility = View.GONE
                return
            }
            val localPath = cachedSongPaths[id]
            if (localPath != null) {
                val localFile = File(localPath)
                if (localFile.isFile) {
                    bindSongThumbnail(id, localFile, miniArtImage, miniArtPlaceholder)
                    return
                }
            }
        }
        val artData = mediaItem.mediaMetadata.artworkData
        if (artData != null && artData.isNotEmpty()) {
            val bmp = runCatching {
                BitmapFactory.decodeByteArray(artData, 0, artData.size)
            }.getOrNull()
            if (bmp != null) {
                if (id != null) thumbnailCache.put(id, bmp)
                miniArtImage.setImageBitmap(bmp)
                miniArtPlaceholder.visibility = View.GONE
                return
            }
        }
        miniArtImage.setImageDrawable(null)
        miniArtPlaceholder.visibility = View.VISIBLE
    }

    private fun updateBottomNavState() {
        if (!::bottomNavBar.isInitialized) return
        val activeIndex = when {
            playlistMode -> 2
            browseSources -> 1
            else -> 0
        }
        for (i in 0 until bottomNavButtons.size) {
            val btn = bottomNavButtons[i]
            val isActive = (i == activeIndex)
            btn.setTextColor(if (isActive) accent else subtle)
            btn.background = if (isActive) rounded(card, 10) else null
            btn.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun showThemePickerDialog() {
        val presets = UiThemePolicy.ThemePreset.entries.toTypedArray()
        val names = presets.map { preset ->
            when (preset) {
                UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO -> "Portrait Edge-to-Edge Pro (Mặc định)"
                UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE -> "Cockpit 2-Pane Split (Khoang lái 2 cột)"
                UiThemePolicy.ThemePreset.CLASSIC -> "Cổ điển (Classic)"
            }
        }.toTypedArray()
        val currentIndex = presets.indexOf(currentThemePreset).coerceAtLeast(0)

        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Chọn giao diện MeloVault")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val chosen = presets[which]
                dialog.dismiss()
                if (chosen != currentThemePreset) {
                    setThemePreset(chosen)
                    showMessage("Đã đổi sang: ${chosen.displayName}")
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun toggleThemePreset() {
        showThemePickerDialog()
    }

    private fun setThemePreset(preset: UiThemePolicy.ThemePreset) {
        currentThemePreset = preset
        val prefs = getSharedPreferences(UiThemePolicy.PREFS_NAME, Context.MODE_PRIVATE)
        UiThemePolicy.savePreset(prefs, preset)
        applyThemeColors()
    }

    private fun applyThemeColors() {
        window.statusBarColor = midnight
        window.navigationBarColor = midnight
        if (::mainRoot.isInitialized) mainRoot.setBackgroundColor(midnight)
        if (::titleHeader.isInitialized) titleHeader.setTextColor(ink)
        if (::syncSubtitle.isInitialized) syncSubtitle.setTextColor(subtle)
        if (::note.isInitialized) note.setTextColor(accent)
        if (::status.isInitialized) status.setTextColor(subtle)
        if (::libraryTitle.isInitialized) libraryTitle.setTextColor(subtle)
        if (::songFilter.isInitialized) {
            songFilter.setTextColor(ink)
            songFilter.setHintTextColor(subtle)
            songFilter.background = rounded(panel)
        }
        if (::tracks.isInitialized) tracks.setBackgroundColor(midnight)
        if (::miniPlayerBar.isInitialized) miniPlayerBar.background = rounded(panel, 14, strokeColor = border)
        if (::miniArtFrame.isInitialized) miniArtFrame.background = rounded(card, 8)
        if (::miniArtPlaceholder.isInitialized) miniArtPlaceholder.setTextColor(accent)
        if (::nowPlayingTitle.isInitialized) nowPlayingTitle.setTextColor(ink)
        if (::nowPlayingSubtitle.isInitialized) nowPlayingSubtitle.setTextColor(subtle)
        if (::playPause.isInitialized) {
            playPause.background = rounded(accent, 12)
            playPause.setTextColor(accentContrast)
        }
        if (::miniProgressBar.isInitialized) {
            miniProgressBar.progressTintList = ColorStateList.valueOf(accent)
        }
        if (::bottomNavBar.isInitialized) {
            bottomNavBar.background = rounded(panel, 14)
            updateBottomNavState()
        }
        if (::cockpitPane.isInitialized) {
            cockpitPane.background = rounded(midnight, 0)
            cockpitDivider.setBackgroundColor(border)
            cockpitArtFrame.background = rounded(card, 16)
            cockpitArtPlaceholder.setTextColor(accent)
            cockpitTitle.setTextColor(ink)
            cockpitSubtitle.setTextColor(subtle)
            cockpitFormatBadge.background = rounded(panel, 8)
            cockpitFormatBadge.setTextColor(subtle)
            cockpitTimeElapsed.setTextColor(subtle)
            cockpitTimeTotal.setTextColor(subtle)
            cockpitSeekBar.progressTintList = ColorStateList.valueOf(accent)
            cockpitSeekBar.thumbTintList = ColorStateList.valueOf(accent)
            cockpitPlayPauseButton.background = rounded(accent, 14)
            cockpitPlayPauseButton.setTextColor(accentContrast)
            cockpitPrevButton.background = rounded(panel, 12)
            cockpitPrevButton.setTextColor(ink)
            cockpitNextButton.background = rounded(panel, 12)
            cockpitNextButton.setTextColor(ink)
        }
        updateFilterChipsUi()
        applyResponsiveLibraryUi()
        updateCockpitPlayer()
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
        if (::sourcesAdapter.isInitialized) sourcesAdapter.notifyDataSetChanged()
    }

    private fun updateCockpitPlayer() {
        if (!::cockpitPane.isInitialized || !isCockpitTwoPaneActive) return
        val controller = player
        val mediaItem = controller?.currentMediaItem
        val isPlaying = controller?.isPlaying == true

        if (mediaItem == null) {
            cockpitArtImage.setImageDrawable(null)
            cockpitArtPlaceholder.visibility = View.VISIBLE
            cockpitTitle.text = "Chọn một bài hát để bắt đầu"
            cockpitSubtitle.text = "Telegram · MeloVault"
            cockpitFormatBadge.visibility = View.GONE
            cockpitTimeElapsed.text = "00:00"
            cockpitTimeTotal.text = "--:--"
            cockpitSeekBar.progress = 0
            cockpitSeekBar.isEnabled = false
            cockpitPlayPauseButton.text = "▶"
            cockpitShuffleButton.alpha = 0.5f
            cockpitRepeatButton.alpha = 0.5f
            cockpitFavoriteButton.alpha = 0.5f
            cockpitQueueButton.alpha = 0.5f
            return
        }

        cockpitSeekBar.isEnabled = true
        cockpitShuffleButton.alpha = 1.0f
        cockpitRepeatButton.alpha = 1.0f
        cockpitFavoriteButton.alpha = 1.0f
        cockpitQueueButton.alpha = 1.0f

        val rawTitle = mediaItem.mediaMetadata.title?.toString().orEmpty()
        cockpitTitle.text = CockpitLayoutPolicy.cleanTrackTitle(rawTitle).ifEmpty { "Bài hát không tên" }

        val performer = mediaItem.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
        val currentMsgId = currentTelegramMessageId()
        val sourceName = currentMsgId?.let { id ->
            val chat = songs.firstOrNull { it.messageId == id }?.chatId
            if (chat != null) sourceNames[chat] else null
        } ?: "Telegram"
        cockpitSubtitle.text = listOfNotNull(performer, sourceName).joinToString(" · ")

        val badge = getAudioFormatBadge(controller)
        if (badge != null) {
            cockpitFormatBadge.text = badge
            cockpitFormatBadge.visibility = View.VISIBLE
        } else {
            cockpitFormatBadge.visibility = View.GONE
        }

        cockpitPlayPauseButton.text = if (isPlaying) "Ⅱ" else "▶"

        val dur = controller.duration
        val pos = controller.currentPosition
        if (!cockpitTrackingTouch) {
            if (dur > 0L && dur != C.TIME_UNSET) {
                val progressPercent = ((pos.toDouble() / dur) * 1000).toInt().coerceIn(0, 1000)
                cockpitSeekBar.progress = progressPercent
                cockpitTimeElapsed.text = CockpitLayoutPolicy.formatDurationMs(pos)
                cockpitTimeTotal.text = CockpitLayoutPolicy.formatDurationMs(dur)
            } else {
                cockpitSeekBar.progress = 0
                cockpitTimeElapsed.text = CockpitLayoutPolicy.formatDurationMs(pos)
                cockpitTimeTotal.text = "--:--"
            }
        }

        val isShuffle = controller.shuffleModeEnabled
        cockpitShuffleButton.setTextColor(if (isShuffle) accent else ink)
        cockpitShuffleButton.background = rounded(if (isShuffle) cardPlaying else panel, 10, if (isShuffle) accent else border)

        val isRepeat = controller.repeatMode != Player.REPEAT_MODE_OFF
        cockpitRepeatButton.setTextColor(if (isRepeat) accent else ink)
        cockpitRepeatButton.background = rounded(if (isRepeat) cardPlaying else panel, 10, if (isRepeat) accent else border)

        val isFav = currentMsgId != null && favoriteIds.contains(currentMsgId)
        cockpitFavoriteButton.text = if (isFav) "★ Đã lưu" else "☆ Lưu bài"
        cockpitFavoriteButton.setTextColor(if (isFav) accent else ink)
        cockpitFavoriteButton.background = rounded(if (isFav) cardPlaying else panel, 10, if (isFav) accent else border)

        updateCockpitCover(mediaItem)
    }

    private fun getAudioFormatBadge(controller: MediaController?): String? {
        val groups = controller?.currentTracks?.groups ?: return null
        for (group in groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSelected(i)) continue
                val format = group.getTrackFormat(i)
                return CockpitLayoutPolicy.formatAudioBadge(
                    sampleMimeType = format.sampleMimeType,
                    sampleRate = format.sampleRate,
                    channelCount = format.channelCount,
                    bitrate = format.bitrate
                )
            }
        }
        return null
    }

    private fun updateCockpitCover(mediaItem: MediaItem?) {
        if (!::cockpitArtImage.isInitialized || !::cockpitArtPlaceholder.isInitialized) return
        if (mediaItem == null) {
            cockpitArtImage.setImageDrawable(null)
            cockpitArtPlaceholder.visibility = View.VISIBLE
            return
        }
        val id = mediaItem.mediaId.removePrefix("telegram:").toLongOrNull()
        if (id != null) {
            val cached = thumbnailCache.get(id)
            if (cached != null) {
                cockpitArtImage.setImageBitmap(cached)
                cockpitArtPlaceholder.visibility = View.GONE
                return
            }
            val localPath = cachedSongPaths[id]
            if (localPath != null) {
                val localFile = File(localPath)
                if (localFile.isFile) {
                    bindSongThumbnail(id, localFile, cockpitArtImage, cockpitArtPlaceholder)
                    return
                }
            }
        }
        val artData = mediaItem.mediaMetadata.artworkData
        if (artData != null && artData.isNotEmpty()) {
            val bmp = runCatching {
                BitmapFactory.decodeByteArray(artData, 0, artData.size)
            }.getOrNull()
            if (bmp != null) {
                if (id != null) thumbnailCache.put(id, bmp)
                cockpitArtImage.setImageBitmap(bmp)
                cockpitArtPlaceholder.visibility = View.GONE
                return
            }
        }
        cockpitArtImage.setImageDrawable(null)
        cockpitArtPlaceholder.visibility = View.VISIBLE
    }

    private fun buildCockpitLeftPane() {
        cockpitScrollView = ScrollView(this).apply {
            isFillViewport = true
            visibility = View.GONE
            splitRoot.addView(this, LinearLayout.LayoutParams(0, -1, CockpitLayoutPolicy.LEFT_PANE_WEIGHT))
        }

        cockpitPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(midnight, 0)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            cockpitScrollView.addView(this, FrameLayout.LayoutParams(-1, -2))
        }

        val artContainer = FrameLayout(this).apply {
            background = rounded(card, 16)
            setOnClickListener { showFullPlayer() }
            val initialSize = dp(140)
            cockpitPane.addView(this, LinearLayout.LayoutParams(initialSize, initialSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(6)
            })
        }
        cockpitArtFrame = artContainer

        cockpitArtImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            artContainer.addView(this, FrameLayout.LayoutParams(-1, -1))
        }

        cockpitArtPlaceholder = TextView(this).apply {
            text = "♫"
            textSize = 44f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setTypeface(null, Typeface.BOLD)
            artContainer.addView(this, FrameLayout.LayoutParams(-1, -1))
        }

        cockpitTitle = TextView(this).apply {
            text = "Chọn một bài hát để bắt đầu"
            textSize = 17f
            setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), dp(2), dp(4), dp(1))
            cockpitPane.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
        }

        cockpitSubtitle = TextView(this).apply {
            text = "Telegram · MeloVault"
            textSize = 12.5f
            setTextColor(subtle)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), 0, dp(4), dp(4))
            cockpitPane.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
        }

        cockpitFormatBadge = TextView(this).apply {
            textSize = 11f
            setTextColor(subtle)
            gravity = Gravity.CENTER
            background = rounded(panel, 8)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            visibility = View.GONE
            cockpitPane.addView(this, LinearLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(6)
            })
        }

        val seekRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            cockpitPane.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(2)
                bottomMargin = dp(4)
            })
        }

        cockpitTimeElapsed = TextView(this).apply {
            text = "00:00"
            textSize = 11f
            setTextColor(subtle)
            gravity = Gravity.START
            seekRow.addView(this, LinearLayout.LayoutParams(dp(42), -2))
        }

        cockpitSeekBar = SeekBar(this).apply {
            max = 1000
            progress = 0
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val dur = player?.duration ?: C.TIME_UNSET
                        if (dur > 0L && dur != C.TIME_UNSET) {
                            val targetMs = (dur * (progress / 1000.0)).toLong()
                            cockpitTimeElapsed.text = CockpitLayoutPolicy.formatDurationMs(targetMs)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    cockpitTrackingTouch = true
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val dur = player?.duration ?: C.TIME_UNSET
                    if (dur > 0L && dur != C.TIME_UNSET && seekBar != null) {
                        val targetMs = (dur * (seekBar.progress / 1000.0)).toLong()
                        player?.seekTo(targetMs)
                    }
                    cockpitTrackingTouch = false
                }
            })
            seekRow.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
        }

        cockpitTimeTotal = TextView(this).apply {
            text = "--:--"
            textSize = 11f
            setTextColor(subtle)
            gravity = Gravity.END
            seekRow.addView(this, LinearLayout.LayoutParams(dp(44), -2))
        }

        val transportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            cockpitPane.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(2)
                bottomMargin = dp(4)
            })
        }

        cockpitPrevButton = Button(this).apply {
            text = "⏮"
            textSize = 18f
            setTextColor(ink)
            background = rounded(panel, 12)
            minHeight = dp(44)
            minimumHeight = dp(44)
            contentDescription = "Bài trước"
            setOnClickListener { playAdjacent(-1) }
            transportRow.addView(this, LinearLayout.LayoutParams(dp(52), dp(44)).apply {
                rightMargin = dp(10)
            })
        }

        cockpitPlayPauseButton = Button(this).apply {
            text = "▶"
            textSize = 22f
            background = rounded(accent, 14)
            setTextColor(accentContrast)
            minHeight = dp(48)
            minimumHeight = dp(48)
            contentDescription = "Phát hoặc tạm dừng"
            setOnClickListener {
                player?.let { controller ->
                    if (controller.currentMediaItem != null) {
                        if (controller.isPlaying) controller.pause() else controller.play()
                    }
                }
            }
            transportRow.addView(this, LinearLayout.LayoutParams(dp(58), dp(48)))
        }

        cockpitNextButton = Button(this).apply {
            text = "⏭"
            textSize = 18f
            setTextColor(ink)
            background = rounded(panel, 12)
            minHeight = dp(44)
            minimumHeight = dp(44)
            contentDescription = "Bài kế tiếp"
            setOnClickListener { playAdjacent(1) }
            transportRow.addView(this, LinearLayout.LayoutParams(dp(52), dp(44)).apply {
                leftMargin = dp(10)
            })
        }

        val secondaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            cockpitPane.addView(this, LinearLayout.LayoutParams(-1, dp(38)).apply {
                topMargin = dp(2)
            })
        }

        fun secondaryButton(label: String, desc: String, action: () -> Unit): Button =
            Button(this).apply {
                text = label
                contentDescription = desc
                textSize = 11.5f
                isAllCaps = false
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ink)
                background = rounded(panel, 10, border)
                setPadding(dp(2), 0, dp(2), 0)
                minHeight = dp(34)
                minimumHeight = dp(34)
                setOnClickListener { action() }
                secondaryRow.addView(this, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                })
            }

        cockpitShuffleButton = secondaryButton("⇄ Đảo", "Bật hoặc tắt phát ngẫu nhiên") {
            player?.let {
                it.shuffleModeEnabled = !it.shuffleModeEnabled
                updateCockpitPlayer()
            }
        }

        cockpitRepeatButton = secondaryButton("↻ Lặp", "Đổi chế độ lặp lại") {
            player?.let {
                it.repeatMode = when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                updateCockpitPlayer()
            }
        }

        cockpitFavoriteButton = secondaryButton("☆ Lưu", "Thêm vào danh sách yêu thích") {
            toggleCurrentFavorite()
        }

        cockpitQueueButton = secondaryButton("☷ Hàng đợi", "Xem danh sách hàng đợi phát nhạc") {
            showUpcomingQueue()
        }
    }

    private fun buildFilterChips() {
        filterChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            libraryContainer.addView(this, LinearLayout.LayoutParams(-1, dp(34)).apply {
                bottomMargin = dp(4)
            })
        }

        chipAll = Button(this).apply {
            text = "Tất cả"
            textSize = 11.5f
            isAllCaps = false
            minHeight = dp(30)
            minimumHeight = dp(30)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                if (filterOnlyDownloaded) {
                    filterOnlyDownloaded = false
                    updateFilterChipsUi()
                    refreshSongRows()
                }
            }
            filterChipsRow.addView(this, LinearLayout.LayoutParams(-2, dp(30)).apply {
                rightMargin = dp(6)
            })
        }

        chipDownloaded = Button(this).apply {
            text = "Đã tải về máy"
            textSize = 11.5f
            isAllCaps = false
            minHeight = dp(30)
            minimumHeight = dp(30)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                if (!filterOnlyDownloaded) {
                    filterOnlyDownloaded = true
                    updateFilterChipsUi()
                    refreshSongRows()
                }
            }
            filterChipsRow.addView(this, LinearLayout.LayoutParams(-2, dp(30)))
        }
        updateFilterChipsUi()
    }

    private fun updateFilterChipsUi() {
        if (!::chipAll.isInitialized || !::chipDownloaded.isInitialized) return
        chipAll.setTextColor(if (!filterOnlyDownloaded) accentContrast else ink)
        chipAll.background = rounded(if (!filterOnlyDownloaded) accent else panel, 10)
        chipAll.setTypeface(null, if (!filterOnlyDownloaded) Typeface.BOLD else Typeface.NORMAL)

        chipDownloaded.setTextColor(if (filterOnlyDownloaded) accentContrast else ink)
        chipDownloaded.background = rounded(if (filterOnlyDownloaded) accent else panel, 10)
        chipDownloaded.setTypeface(null, if (filterOnlyDownloaded) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun rounded(color: Int, radius: Int = 14, strokeColor: Int? = null, strokeWidthDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color)
            if (strokeColor != null) {
                setStroke(dp(strokeWidthDp), strokeColor)
            }
        }

    private fun uiButton(label: String, primary: Boolean = false): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(if (primary) midnight else ink)
            background = rounded(if (primary) accent else panel, 12)
            minHeight = dp(45)
            minimumHeight = dp(45)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

    private fun buildUi() {
        window.statusBarColor = midnight
        window.navigationBarColor = midnight
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(midnight)
        }
        mainRoot = root
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            lastSystemBarsInsets = WindowInsetsPolicy.Insets(
                systemBars.left, systemBars.top, systemBars.right, systemBars.bottom
            )
            lastImeInsets = WindowInsetsPolicy.Insets(
                ime.left, ime.top, ime.right, ime.bottom
            )
            applyRootWindowInsets()
            insets
        }
        splitRoot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            root.addView(this, LinearLayout.LayoutParams(-1, 0, 1f))
        }

        buildCockpitLeftPane()

        cockpitDivider = View(this).apply {
            setBackgroundColor(border)
            visibility = View.GONE
            splitRoot.addView(this, LinearLayout.LayoutParams(dp(1), -1))
        }

        libraryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            splitRoot.addView(this, LinearLayout.LayoutParams(0, -1, 1f))
        }

        val topBarView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            libraryContainer.addView(this, LinearLayout.LayoutParams(-1, dp(48)))
        }
        topBar = topBarView
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleHeader = TextView(this).apply {
            text = "MeloVault"
            textSize = 21f
            setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
        }
        titleColumn.addView(titleHeader)
        syncSubtitle = TextView(this).apply {
            text = "Telegram · Nhạc cá nhân · Preview"
            textSize = 11f
            setTextColor(subtle)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titleColumn.addView(syncSubtitle)
        topBar.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        val overflow = uiButton("⋮").apply {
            textSize = 21f
            contentDescription = "Tác vụ và cài đặt MeloVault"
            setOnClickListener { showLibraryOverflow(this) }
        }
        overflowButton = overflow
        topBar.addView(overflow, LinearLayout.LayoutParams(dp(49), dp(47)))
        note = TextView(this).apply {
            text = "TELEGRAM  ·  NHẠC CÁ NHÂN  ·  PREVIEW"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(accent)
            setPadding(0, 0, 0, dp(10))
            libraryContainer.addView(this)
        }
        navigationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            libraryContainer.addView(this, LinearLayout.LayoutParams(-1, dp(45)).apply {
                bottomMargin = dp(4)
            })
        }
        fun tab(label: String, description: String, action: () -> Unit) {
            val btn = uiButton(label).apply {
                contentDescription = description
                textSize = 11f
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(2), 0, dp(2), 0)
                setOnClickListener { action() }
            }
            navigationRow.addView(btn, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
            })
        }
        tab("Thư viện", "Danh sách tất cả bài hát") {
            if (!auth.isReady()) return@tab
            playlistMode = false
            browseSources = false
            selectedChatId = null
            val shouldScan = autoRecoveryPolicy.shouldTabTriggerScan(
                auth.isReady(), allChatsMode, globalScanLoading, songs.size,
                library.authorizedUser()?.value
            )
            if (shouldScan) scanAllTelegramAudio(browseAfter = false)
            else { songFilter.text?.clear(); refreshSongRows() }
        }
        tab("Nguồn Telegram", "Xem nhóm và kênh Telegram") {
            if (!auth.isReady()) return@tab
            playlistMode = false
            browseSources = true
            selectedChatId = null
            val shouldScan = autoRecoveryPolicy.shouldTabTriggerScan(
                auth.isReady(), allChatsMode, globalScanLoading, songs.size,
                library.authorizedUser()?.value
            )
            if (shouldScan) scanAllTelegramAudio()
            else { songFilter.text?.clear(); refreshSongRows() }
        }
        tab("Playlist", "Playlist các bài yêu thích") {
            if (!auth.isReady()) return@tab
            playlistMode = true
            browseSources = false
            selectedChatId = null
            songFilter.text?.clear()
            updatePlaylistButtons()
            refreshSongRows()
        }
        tab("Đang phát", "Mở trình phát nhạc") { showFullPlayer() }
        collapsibleNavigation = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            libraryContainer.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        status = TextView(this).apply {
            text = "Đang khởi tạo Telegram…"
            textSize = 11f
            setTextColor(subtle)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(2), dp(3), dp(2), dp(3))
            libraryContainer.addView(this, LinearLayout.LayoutParams(-1, dp(23)))
        }
        buildFilterChips()
        authControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            collapsibleNavigation.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }
        input = EditText(this).apply {
            hint = "Số điện thoại Telegram (+84...)"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_PHONE
            setTextColor(ink)
            setHintTextColor(subtle)
            textSize = 15f
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            background = rounded(panel)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            authControls.addView(this, LinearLayout.LayoutParams(-1, dp(48)))
        }
        submit = uiButton("Tiếp tục", primary = true).apply {
            setOnClickListener { submitAuthentication() }
            authControls.addView(this, LinearLayout.LayoutParams(-1, dp(46)).apply {
                topMargin = dp(6)
            })
        }
        qrLogin = uiButton("Đăng nhập bằng QR").apply {
            isEnabled = false
            setOnClickListener { requestQrLogin() }
            authControls.addView(this, LinearLayout.LayoutParams(-1, dp(46)).apply {
                topMargin = dp(6)
            })
        }
        val sourceRow = LinearLayout(this).apply {
            visibility = View.GONE
            orientation = LinearLayout.HORIZONTAL
            collapsibleNavigation.addView(this, LinearLayout.LayoutParams(-1, dp(47)).apply {
                bottomMargin = dp(10)
            })
        }
        refresh = uiButton("☆ Tin đã lưu", primary = true).apply {
            isEnabled = false
            setOnClickListener { loadSavedMessages() }
            sourceRow.addView(this, LinearLayout.LayoutParams(0, -1, 1f).apply {
                rightMargin = dp(4)
            })
        }
        openDocument = uiButton("♫  Chọn tệp nhạc").apply {
            isEnabled = false
            setOnClickListener { openAudioDocument() }
            sourceRow.addView(this, LinearLayout.LayoutParams(0, -1, 1f).apply {
                leftMargin = dp(4)
            })
        }
        val sourceAllRow = LinearLayout(this).apply {
            visibility = View.GONE
            orientation = LinearLayout.HORIZONTAL
            collapsibleNavigation.addView(this, LinearLayout.LayoutParams(-1, dp(43)).apply {
                bottomMargin = dp(10)
            })
        }
        globalSearch = uiButton("♫  Tìm nhạc mọi chat Telegram").apply {
            isEnabled = false
            setOnClickListener {
                autoRecoveryPolicy.onManualScanRequested()
                scanAllTelegramAudio()
            }
            sourceAllRow.addView(this, LinearLayout.LayoutParams(-1, -1))
        }
        libraryTitle = TextView(this).apply {
            text = "THƯ VIỆN NHẠC  ·  SAVED MESSAGES"
            textSize = 12f
            setTextColor(subtle)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setOnClickListener {
                if (allChatsMode && (!browseSources || playlistMode)) {
                    playlistMode = false
                    browseSources = true
                    selectedChatId = null
                    songFilter.text?.clear()
                    updatePlaylistButtons()
                    refreshSongRows()
                }
            }
            libraryContainer.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(6)
            })
        }
        sourcesButton = uiButton("☷  Nhóm / kênh / bot", primary = true).apply {
            visibility = View.GONE
            isEnabled = false
            setOnClickListener {
                if (!auth.isReady() || !allChatsMode) return@setOnClickListener
                browseSources = true
                selectedChatId = null
                playlistMode = false
                songFilter.text?.clear()
                refreshSongRows()
                updatePlaylistButtons()
            }
            collapsibleNavigation.addView(this, LinearLayout.LayoutParams(-1, dp(43)).apply {
                bottomMargin = dp(8)
            })
        }
        songFilter = EditText(this).apply {
            hint = "Tìm nhóm, kênh hoặc bot…"
            setSingleLine(true)
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(subtle)
            background = rounded(panel)
            setPadding(dp(13), dp(9), dp(13), dp(9))
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            contentDescription = "Ô tìm kiếm nhóm, kênh hoặc bài hát"
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(v.windowToken, 0)
                    v.clearFocus()
                    true
                } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(
                    s: CharSequence?, start: Int, before: Int, count: Int) {
                    refreshSongRows()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            libraryContainer.addView(this, LinearLayout.LayoutParams(-1, dp(45)).apply {
                bottomMargin = dp(8)
            })
        }
        val playlistRow = LinearLayout(this).apply {
            visibility = View.GONE
            orientation = LinearLayout.HORIZONTAL
            collapsibleNavigation.addView(this, LinearLayout.LayoutParams(-1, dp(43)).apply {
                bottomMargin = dp(9)
            })
        }
        favoriteButton = uiButton("☆ Lưu bài").apply {
            isEnabled = false
            setOnClickListener { toggleCurrentFavorite() }
            playlistRow.addView(this, LinearLayout.LayoutParams(0, -1, 1f).apply {
                rightMargin = dp(4)
            })
        }
        playlistButton = uiButton("☷ Playlist").apply {
            isEnabled = false
            setOnClickListener {
                if (!auth.isReady()) return@setOnClickListener
                playlistMode = !playlistMode
                if (playlistMode) {
                    browseSources = false
                    selectedChatId = null
                } else if (allChatsMode) {
                    browseSources = true
                }
                songFilter.text?.clear()
                updatePlaylistButtons()
                refreshSongRows()
                if (playlistMode && shownSongs.isEmpty()) {
                    showMessage("Playlist chưa có bài trong thư viện hiện tại.")
                }
            }
            playlistRow.addView(this, LinearLayout.LayoutParams(0, -1, 1f).apply {
                leftMargin = dp(4)
            })
        }
        tracks = ListView(this).apply {
            setBackgroundColor(midnight)
            divider = null
            dividerHeight = dp(6)
            cacheColorHint = Color.TRANSPARENT
            setSelector(android.R.color.transparent)
            // Do NOT resize the ListView during ACTION_MOVE or flinging:
            // doing so moves content underneath the finger and clips rows.
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(
                    view: AbsListView?, scrollState: Int
                ) {
                    listScrollState = scrollState
                    if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                        post { commitNavigationGestureIfIdle() }
                    }
                }
                override fun onScroll(
                    view: AbsListView?, firstVisibleItem: Int,
                    visibleItemCount: Int, totalItemCount: Int
                ) {
                    if (allChatsMode && !browseSources && !playlistMode &&
                        firstVisibleItem + visibleItemCount >= totalItemCount - 20 &&
                        totalItemCount > 0 && !isLoadingMoreSongs) {
                        val userId = library.authorizedUser()?.value ?: 0L
                        val db = libraryDb
                        if (db != null && userId > 0L) {
                            if (shownSongs.size < totalCountForCurrentView) {
                                isLoadingMoreSongs = true
                                val nextOffset = shownSongs.size
                                val currentGen = uiQueryGeneration.get()
                                val q = songFilter.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
                                val currentChat = selectedChatId
                                executeOnDb {
                                    val count = db.getSongCount(userId, chatId = currentChat, searchQuery = q)
                                    val more = if (nextOffset < count) {
                                        db.getSongs(userId, chatId = currentChat, searchQuery = q, offset = nextOffset, limit = 200)
                                    } else emptyList()
                                    val mapped = more.map {
                                        Song(
                                            it.fileId, it.title, it.filename, it.songKey, it.chatId, it.fileSize,
                                            rawMessageId = it.messageId,
                                            performer = it.performer,
                                            durationSeconds = it.durationSeconds,
                                            sourceTitle = it.sourceTitle
                                        )
                                    }
                                    runOnUiThread {
                                        if (uiQueryGeneration.get() == currentGen &&
                                            shownSongs.size == nextOffset &&
                                            allChatsMode && !browseSources && !playlistMode &&
                                            mapped.isNotEmpty() && !isDestroyed) {
                                            totalCountForCurrentView = count
                                            shownSongs = shownSongs + mapped
                                            this@TelegramPreviewActivity.adapter.addAll(
                                                mapped.map { TelegramSongDisplayPolicy.label(it.title, it.filename) }
                                            )
                                            updateSyncHeaderSubtitle()
                                        }
                                        isLoadingMoreSongs = false
                                    }
                                }
                            }
                        }
                    }
                }
            })
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (::songFilter.isInitialized && songFilter.hasFocus()) {
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.hideSoftInputFromWindow(songFilter.windowToken, 0)
                            songFilter.clearFocus()
                        }
                        gestureLastY = event.y
                        gestureAccumulation = 0f
                        pendingNavigationCollapse = null
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val delta = event.y - gestureLastY
                        gestureLastY = event.y
                        gestureAccumulation =
                            (gestureAccumulation + delta).coerceIn(
                                -dp(110).toFloat(), dp(110).toFloat()
                            )
                    }
                    MotionEvent.ACTION_UP -> {
                        val allowCollapse = ShortWindowLayoutPolicy.shouldAutoCollapseTopNav(currentThemePreset, currentPaneMode)
                        if (allowCollapse) {
                            val requested = TelegramLibraryChromePolicy.collapsedAfter(
                                navigationCollapsed, gestureAccumulation,
                                dp(36).toFloat(), auth.isReady(), this.count
                            )
                            if (requested != navigationCollapsed) {
                                pendingNavigationCollapse = requested
                                post { commitNavigationGestureIfIdle() }
                            }
                        } else {
                            if (navigationCollapsed) {
                                setNavigationCollapsed(false)
                            }
                        }
                        gestureAccumulation = 0f
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        gestureAccumulation = 0f
                        pendingNavigationCollapse = null
                    }
                }
                false
            }
        }
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
            mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val holder: SongRowViewHolder
                val rootLayout: LinearLayout
                if (convertView is LinearLayout && convertView.tag is SongRowViewHolder) {
                    rootLayout = convertView
                    holder = convertView.tag as SongRowViewHolder
                } else {
                    rootLayout = LinearLayout(this@TelegramPreviewActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val artFrame = FrameLayout(this@TelegramPreviewActivity).apply {
                        rootLayout.addView(this, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                            rightMargin = dp(12)
                        })
                    }
                    val artImage = ImageView(this@TelegramPreviewActivity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        artFrame.addView(this, FrameLayout.LayoutParams(-1, -1))
                    }
                    val artPlaceholder = TextView(this@TelegramPreviewActivity).apply {
                        text = "♫"
                        textSize = 18f
                        gravity = Gravity.CENTER
                        setTypeface(null, Typeface.BOLD)
                        artFrame.addView(this, FrameLayout.LayoutParams(-1, -1))
                    }
                    val textColumn = LinearLayout(this@TelegramPreviewActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_VERTICAL
                        rootLayout.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
                    }
                    val titleView = TextView(this@TelegramPreviewActivity)
                    val subtitleView = TextView(this@TelegramPreviewActivity)
                    textColumn.addView(titleView)
                    textColumn.addView(subtitleView)
                    holder = SongRowViewHolder(artFrame, artImage, artPlaceholder, titleView, subtitleView)
                    rootLayout.tag = holder
                }

                val song = shownSongs.getOrNull(position)
                val isCurrent = (song != null && song.messageId == currentTelegramMessageId())
                val songLabel = song?.let { TelegramSongDisplayPolicy.label(it.title, it.filename) }
                    ?: getItem(position).orEmpty()
                val displayTitle = CockpitLayoutPolicy.cleanTrackTitle(songLabel)

                holder.titleView.text = (if (isCurrent) "▶ " else "") + displayTitle
                holder.titleView.textSize = if (isShortLayout) 13.5f else 15f
                holder.titleView.setTextColor(if (isCurrent) accent else ink)
                holder.titleView.maxLines = 1
                holder.titleView.ellipsize = android.text.TextUtils.TruncateAt.END
                holder.titleView.setTypeface(null, if (isCurrent) Typeface.BOLD else Typeface.NORMAL)

                val local = song?.let { cachedSongPaths[it.messageId]?.let { p -> File(p) } }
                val isDownloaded = local?.isFile == true && TelegramAccountStoragePolicy.isOwnedFile(filesRoot, local)

                holder.subtitleView.text = if (song != null) {
                    TelegramSongDisplayPolicy.subtitle(
                        source = song.sourceTitle ?: (sourceNames[song.chatId] ?: "Telegram"),
                        isDownloaded = isDownloaded,
                        fileSizeBytes = song.expectedSize,
                        durationSeconds = song.durationSeconds,
                        performer = song.performer
                    )
                } else {
                    "Telegram · Chưa tải"
                }
                holder.subtitleView.textSize = if (isShortLayout) 10.5f else 12f
                holder.subtitleView.setTextColor(subtle)
                holder.subtitleView.setPadding(0, dp(2), 0, 0)
                holder.subtitleView.maxLines = 1
                holder.subtitleView.ellipsize = android.text.TextUtils.TruncateAt.END

                holder.artFrame.background = rounded(card, 8)
                holder.artPlaceholder.setTextColor(if (isCurrent) accent else subtle)

                if (song != null && isDownloaded && local != null) {
                    bindSongThumbnail(song.messageId, local, holder.artImage, holder.artPlaceholder)
                } else {
                    holder.artImage.tag = null
                    holder.artImage.setImageDrawable(null)
                    holder.artPlaceholder.visibility = View.VISIBLE
                }

                val cardBg = if (isCurrent) cardPlaying else card
                val cardStroke = if (isCurrent) accent else null
                rootLayout.background = rounded(cardBg, 12, strokeColor = cardStroke, strokeWidthDp = if (isCurrent) 1 else 0)
                val padV = dp(if (isShortLayout) 6 else 10)
                rootLayout.setPadding(dp(12), padV, dp(14), padV)
                rootLayout.minimumHeight = dp(if (isShortLayout) 48 else 64)
                return rootLayout
            }
        }
        sourcesAdapter = object : ArrayAdapter<String>(this,
            android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(
                position: Int, convertView: View?, parent: android.view.ViewGroup
            ): View {
                val layout = if (convertView is LinearLayout && convertView.childCount == 2) {
                    convertView
                } else {
                    LinearLayout(this@TelegramPreviewActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        val iconFrame = FrameLayout(this@TelegramPreviewActivity).apply {
                            addView(TextView(this@TelegramPreviewActivity).apply {
                                text = "☷"
                                textSize = 18f
                                gravity = Gravity.CENTER
                                setTextColor(accent)
                                setTypeface(null, Typeface.BOLD)
                            }, FrameLayout.LayoutParams(-1, -1))
                        }
                        val textCol = LinearLayout(this@TelegramPreviewActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        val tView = TextView(this@TelegramPreviewActivity)
                        val sView = TextView(this@TelegramPreviewActivity)
                        textCol.addView(tView)
                        textCol.addView(sView)
                        addView(iconFrame, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                            rightMargin = dp(12)
                        })
                        addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
                    }
                }
                val iconFrame = layout.getChildAt(0) as FrameLayout
                iconFrame.background = rounded(card, 8)
                val textCol = layout.getChildAt(1) as LinearLayout
                val titleView = textCol.getChildAt(0) as TextView
                val subtitleView = textCol.getChildAt(1) as TextView
                val sourceText = getItem(position).orEmpty()

                titleView.text = sourceText
                titleView.textSize = if (isShortLayout) 13f else 14.5f
                titleView.setTextColor(ink)
                titleView.maxLines = 1
                titleView.ellipsize = android.text.TextUtils.TruncateAt.END
                titleView.setTypeface(null, Typeface.BOLD)

                subtitleView.text = "Kênh / Nhóm Telegram"
                subtitleView.textSize = if (isShortLayout) 10f else 11.5f
                subtitleView.setTextColor(subtle)
                subtitleView.setPadding(0, dp(2), 0, 0)

                layout.background = rounded(panel, 11)
                val padV = dp(if (isShortLayout) 6 else 10)
                layout.setPadding(dp(12), padV, dp(14), padV)
                layout.minimumHeight = dp(if (isShortLayout) 44 else 58)
                return layout
            }
        }
        tracks.adapter = adapter
        tracks.onItemClickListener = android.widget.AdapterView.OnItemClickListener {
                _, _, position, _ ->
            if (::songFilter.isInitialized && songFilter.hasFocus()) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(songFilter.windowToken, 0)
                songFilter.clearFocus()
            }
            if (allChatsMode && browseSources && !playlistMode) {
                visibleSourceIds.getOrNull(position)?.let { source ->
                    selectedChatId = source.takeIf { it != 0L }
                    browseSources = false
                    songFilter.text?.clear()
                    refreshSongRows()
                }
            } else shownSongs.getOrNull(position)?.let { downloadAndPlay(it) }
        }
        libraryContainer.addView(tracks, LinearLayout.LayoutParams(-1, 0, 1f))
        miniPlayerBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 14, strokeColor = border)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            root.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(3)
            })
        }
        miniProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            progressTintList = ColorStateList.valueOf(accent)
            miniPlayerBar.addView(this, LinearLayout.LayoutParams(-1, dp(3)).apply {
                bottomMargin = dp(3)
            })
        }
        val miniContentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            miniPlayerBar.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        val miniArtFrameView = FrameLayout(this).apply {
            background = rounded(card, 8)
            setOnClickListener { showFullPlayer() }
            miniContentRow.addView(this, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                leftMargin = dp(4)
                rightMargin = dp(8)
            })
        }
        miniArtFrame = miniArtFrameView
        miniArtImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            miniArtFrame.addView(this, FrameLayout.LayoutParams(-1, -1))
        }
        miniArtPlaceholder = TextView(this).apply {
            text = "♫"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setTypeface(null, Typeface.BOLD)
            miniArtFrame.addView(this, FrameLayout.LayoutParams(-1, -1))
        }
        val miniInfoColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnClickListener { showFullPlayer() }
            miniContentRow.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = dp(2)
                rightMargin = dp(6)
            })
        }
        nowPlayingTitle = TextView(this).apply {
            text = "Chọn một bài hát để bắt đầu"
            textSize = 14f
            setTextColor(ink)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(null, Typeface.BOLD)
            miniInfoColumn.addView(this)
        }
        nowPlayingSubtitle = TextView(this).apply {
            text = "Telegram · MeloVault"
            textSize = 11f
            setTextColor(subtle)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            miniInfoColumn.addView(this)
        }
        miniOpenPlayer = uiButton("⤢  Mở trình phát toàn màn hình").apply {
            textSize = 12f
            visibility = View.GONE
            setOnClickListener { showFullPlayer() }
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            miniContentRow.addView(this, LinearLayout.LayoutParams(-2, dp(44)))
        }
        miniControls = controls
        fun control(symbol: String, action: () -> Unit): Button =
            Button(this).apply {
                text = symbol
                textSize = 18f
                setTextColor(ink)
                background = rounded(panel, 10)
                setPadding(0, 0, 0, 0)
                minHeight = dp(40)
                minimumHeight = dp(40)
                setOnClickListener { action() }
                controls.addView(this, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                })
            }
        control("⏮") { playAdjacent(-1) }
        playPause = Button(this).apply {
            text = "▶"
            textSize = 18f
            background = rounded(accent, 12)
            setTextColor(accentContrast)
            setPadding(0, 0, 0, 0)
            minHeight = dp(42)
            minimumHeight = dp(42)
            setOnClickListener {
                player?.let { controller ->
                    if (controller.currentMediaItem != null) {
                        if (controller.isPlaying) controller.pause() else controller.play()
                    }
                }
            }
            controls.addView(this, LinearLayout.LayoutParams(dp(44), dp(42)).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
            })
        }
        control("⏭") { playAdjacent(1) }
        bottomNavBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 14)
            setPadding(dp(4), dp(3), dp(4), dp(3))
            root.addView(this, LinearLayout.LayoutParams(-1, dp(52)).apply {
                topMargin = dp(4)
            })
        }
        fun bottomTab(index: Int, label: String, description: String, action: () -> Unit) {
            val btn = Button(this).apply {
                text = label
                contentDescription = description
                textSize = 11.5f
                isAllCaps = false
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(4), 0, dp(4), 0)
                minHeight = dp(44)
                minimumHeight = dp(44)
                setOnClickListener { action() }
            }
            bottomNavButtons.add(btn)
            bottomNavBar.addView(btn, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
            })
        }
        bottomTab(0, "♫ Thư viện", "Danh sách tất cả bài hát") {
            if (!auth.isReady()) return@bottomTab
            playlistMode = false
            browseSources = false
            selectedChatId = null
            val shouldScan = autoRecoveryPolicy.shouldTabTriggerScan(
                auth.isReady(), allChatsMode, globalScanLoading, songs.size,
                library.authorizedUser()?.value
            )
            if (shouldScan) scanAllTelegramAudio(browseAfter = false)
            else { songFilter.text?.clear(); refreshSongRows() }
            updateBottomNavState()
        }
        bottomTab(1, "☷ Nguồn", "Xem nhóm và kênh Telegram") {
            if (!auth.isReady()) return@bottomTab
            playlistMode = false
            browseSources = true
            selectedChatId = null
            val shouldScan = autoRecoveryPolicy.shouldTabTriggerScan(
                auth.isReady(), allChatsMode, globalScanLoading, songs.size,
                library.authorizedUser()?.value
            )
            if (shouldScan) scanAllTelegramAudio()
            else { songFilter.text?.clear(); refreshSongRows() }
            updateBottomNavState()
        }
        bottomTab(2, "☆ Playlist", "Playlist các bài yêu thích") {
            if (!auth.isReady()) return@bottomTab
            playlistMode = true
            browseSources = false
            selectedChatId = null
            songFilter.text?.clear()
            updatePlaylistButtons()
            refreshSongRows()
            updateBottomNavState()
        }
        bottomTab(3, "▶ Đang phát", "Mở trình phát nhạc toàn màn hình") {
            showFullPlayer()
        }
        logout = uiButton("Đăng xuất Telegram").apply {
            textSize = 11f
            isEnabled = false
            setTextColor(subtle)
            setOnClickListener { logoutUser() }
            // Account controls belong to the expandable toolbar, never
            // underneath Android's bottom system navigation.
            collapsibleNavigation.addView(this, LinearLayout.LayoutParams(
                -1, dp(35)).apply { bottomMargin = dp(5) })
        }
        authControls.visibility = View.GONE
        setContentView(root)
        applyResponsiveLibraryUi()
    }

    private fun showLibraryOverflow(anchor: View) {
        val themed = ContextThemeWrapper(this, R.style.Theme_MusicAssistantCar_Dialog)
        val menu = PopupMenu(themed, anchor)
        menu.menu.add(0, 1, 1, "☆ Tin đã lưu")
        menu.menu.add(0, 2, 2, "Đồng bộ nhanh (Cập nhật bài mới)")
        menu.menu.add(0, 3, 3, "Đồng bộ toàn bộ thư viện")
        val pauseResumeLabel = if (syncCoordinator?.isBusy() == true) "Tạm dừng đồng bộ" else "Tiếp tục đồng bộ"
        menu.menu.add(0, 4, 4, pauseResumeLabel)
        menu.menu.add(0, 5, 5, "Nhóm / kênh / bot Telegram")
        menu.menu.add(0, 6, 6, "Chọn tệp nhạc (Google Drive / thiết bị)")
        menu.menu.add(0, 7, 7, "Đăng xuất Telegram")
        val themeLabel = "Giao diện: ${currentThemePreset.displayName} ✓"
        menu.menu.add(0, 8, 8, themeLabel)
        menu.setOnMenuItemClickListener {
            val user = library.authorizedUser()?.value ?: 0L
            when (it.itemId) {
                1 -> if (auth.isReady()) loadSavedMessages()
                2 -> if (auth.isReady()) {
                    autoRecoveryPolicy.onManualScanRequested()
                    scanAllTelegramAudio(browseAfter = false, incremental = true)
                }
                3 -> if (auth.isReady()) {
                    autoRecoveryPolicy.onManualScanRequested()
                    scanAllTelegramAudio(browseAfter = false, incremental = false, forceRestart = true)
                }
                4 -> if (auth.isReady() && user > 0L) {
                    val coord = syncCoordinator
                    if (coord != null && coord.isBusy()) {
                        coord.pauseSync()?.let { action -> handleSyncAction(action, user) }
                    } else {
                        scanAllTelegramAudio(browseAfter = false, incremental = false, forceRestart = false)
                    }
                }
                5 -> if (auth.isReady()) {
                    val shouldScan = autoRecoveryPolicy.shouldTabTriggerScan(
                        auth.isReady(), allChatsMode, globalScanLoading, songs.size,
                        user
                    )
                    if (shouldScan) scanAllTelegramAudio()
                    browseSources = true
                    selectedChatId = null
                    playlistMode = false
                    songFilter.text?.clear()
                    refreshSongRows()
                }
                6 -> if (auth.isReady()) openAudioDocument()
                7 -> if (auth.isReady()) {
                    AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                        .setTitle("Đăng xuất tài khoản Telegram?")
                        .setMessage("Bài đã tải và phiên đăng nhập trên thiết bị sẽ thay đổi.")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Đăng xuất") { _, _ -> logoutUser() }
                        .show()
                }
                8 -> toggleThemePreset()
            }
            true
        }
        menu.show()
    }

    private fun applyResponsiveLibraryUi() {
        if (!::navigationRow.isInitialized || !::mainRoot.isInitialized) return
        val bounds = if (android.os.Build.VERSION.SDK_INT >= 30) {
            windowManager.currentWindowMetrics.bounds
        } else null
        val density = resources.displayMetrics.density
        val width = ((bounds?.width() ?: resources.displayMetrics.widthPixels) / density).toInt()
        val height = ((bounds?.height() ?: resources.displayMetrics.heightPixels) / density).toInt()
        val pane = CarPaneLayoutPolicy.modeFor(width, height)
        val short = ShortWindowLayoutPolicy.isShort(height)
        val narrow = pane == CarPaneLayoutPolicy.Mode.NARROW
        val wide = pane == CarPaneLayoutPolicy.Mode.WIDE
        isShortLayout = short
        isNarrowLayout = narrow
        isWideLayout = wide
        currentPaneMode = pane

        // Theme 02 Cockpit 2-Pane split eligibility
        val isCockpitEligible = CockpitLayoutPolicy.isEligibleForCockpitTwoPane(width, height)
        val shouldShowTwoPane = (currentThemePreset == UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE) && isCockpitEligible
        isCockpitTwoPaneActive = shouldShowTwoPane

        if (::cockpitScrollView.isInitialized && ::cockpitDivider.isInitialized && ::libraryContainer.isInitialized) {
            if (shouldShowTwoPane) {
                cockpitScrollView.visibility = View.VISIBLE
                cockpitDivider.visibility = View.VISIBLE
                cockpitScrollView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, CockpitLayoutPolicy.LEFT_PANE_WEIGHT)
                libraryContainer.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, CockpitLayoutPolicy.RIGHT_PANE_WEIGHT)
                val artSize = dp(CockpitLayoutPolicy.cockpitCoverSizeDp(width, height))
                cockpitArtFrame.layoutParams.width = artSize
                cockpitArtFrame.layoutParams.height = artSize
                val isShortH = height < 460
                (cockpitArtFrame.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(if (isShortH) 4 else 8)
                cockpitPane.setPadding(dp(12), dp(if (isShortH) 4 else 8), dp(12), dp(if (isShortH) 4 else 8))
                updateCockpitPlayer()
            } else {
                cockpitScrollView.visibility = View.GONE
                cockpitDivider.visibility = View.GONE
                libraryContainer.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
            }
        }

        applyRootWindowInsets()
        if (::topBar.isInitialized) {
            topBar.layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
            topBar.minimumHeight = dp(ShortWindowLayoutPolicy.topBarMinHeightDp(short))
            (topBar.layoutParams as? LinearLayout.LayoutParams)?.apply {
                bottomMargin = dp(if (short) 2 else 4)
            }
        }
        if (::overflowButton.isInitialized) {
            overflowButton.layoutParams.height = dp(if (short) 34 else 44)
            overflowButton.layoutParams.width = dp(if (short) 38 else 46)
            overflowButton.minHeight = dp(if (short) 30 else 40)
            overflowButton.minimumHeight = dp(if (short) 30 else 40)
            overflowButton.textSize = if (short) 16f else 20f
            overflowButton.setPadding(0, 0, 0, 0)
        }
        titleHeader.textSize = if (short) 16f else 21f
        if (::syncSubtitle.isInitialized) {
            syncSubtitle.textSize = if (short) 10.5f else 11f
        }
        updateSyncHeaderSubtitle()
        note.visibility = if (auth.isReady() || short) View.GONE else View.VISIBLE
        collapsibleNavigation.visibility = if (auth.isReady()) View.GONE else View.VISIBLE

        if (shouldShowTwoPane) {
            if (::miniPlayerBar.isInitialized) miniPlayerBar.visibility = View.GONE
            if (::bottomNavBar.isInitialized) bottomNavBar.visibility = View.GONE
            if (::navigationRow.isInitialized) {
                navigationRow.visibility = if (auth.isReady()) View.VISIBLE else View.GONE
                val navHeight = dp(ShortWindowLayoutPolicy.topNavigationHeightDp(short))
                navigationRow.layoutParams.height = navHeight
                (navigationRow.layoutParams as? LinearLayout.LayoutParams)?.apply {
                    topMargin = dp(if (short) 2 else 4)
                    bottomMargin = dp(if (short) 2 else 4)
                }
                for (i in 0 until navigationRow.childCount) {
                    val child = navigationRow.getChildAt(i)
                    child.layoutParams.height = dp(ShortWindowLayoutPolicy.topNavButtonHeightDp(short))
                    if (child is TextView) {
                        child.textSize = if (narrow) 10.5f else 11.5f
                        child.minHeight = dp(if (short) 30 else 40)
                        child.minimumHeight = dp(if (short) 30 else 40)
                        child.setPadding(dp(2), 0, dp(2), 0)
                    }
                }
            }
        } else {
            if (::miniPlayerBar.isInitialized) miniPlayerBar.visibility = View.VISIBLE
            val isEdgeProPortrait = ShortWindowLayoutPolicy.shouldUseBottomNav(currentThemePreset, pane)
            if (isEdgeProPortrait) {
                navigationRow.visibility = View.GONE
                if (::bottomNavBar.isInitialized) {
                    bottomNavBar.visibility = if (auth.isReady()) View.VISIBLE else View.GONE
                    bottomNavBar.layoutParams.height = dp(ShortWindowLayoutPolicy.bottomNavHeightDp(short))
                    (bottomNavBar.layoutParams as? LinearLayout.LayoutParams)?.apply {
                        topMargin = dp(if (short) 2 else 4)
                    }
                    for (btn in bottomNavButtons) {
                        btn.layoutParams.height = dp(ShortWindowLayoutPolicy.bottomNavButtonHeightDp(short))
                        btn.minHeight = dp(if (short) 34 else 42)
                        btn.minimumHeight = dp(if (short) 34 else 42)
                        btn.textSize = if (short) 10.5f else 11.5f
                    }
                    updateBottomNavState()
                }
            } else {
                if (::bottomNavBar.isInitialized) bottomNavBar.visibility = View.GONE
                val allowCollapse = ShortWindowLayoutPolicy.shouldAutoCollapseTopNav(currentThemePreset, pane)
                if (!allowCollapse) {
                    navigationCollapsed = false
                    pendingNavigationCollapse = null
                }
                navigationRow.visibility = if ((navigationCollapsed && allowCollapse) || !auth.isReady()) View.GONE else View.VISIBLE
                if (::navigationRow.isInitialized) {
                    val navHeight = dp(ShortWindowLayoutPolicy.topNavigationHeightDp(short))
                    navigationRow.layoutParams.height = navHeight
                    (navigationRow.layoutParams as? LinearLayout.LayoutParams)?.apply {
                        topMargin = dp(if (short) 2 else 4)
                        bottomMargin = dp(if (short) 2 else 4)
                    }
                    for (i in 0 until navigationRow.childCount) {
                        val child = navigationRow.getChildAt(i)
                        child.layoutParams.height = dp(ShortWindowLayoutPolicy.topNavButtonHeightDp(short))
                        if (child is TextView) {
                            child.textSize = if (narrow) 10.5f else 11.5f
                            child.minHeight = dp(if (short) 30 else 40)
                            child.minimumHeight = dp(if (short) 30 else 40)
                            child.setPadding(dp(2), 0, dp(2), 0)
                        }
                    }
                }
            }
        }

        if (::filterChipsRow.isInitialized) {
            filterChipsRow.visibility = if (auth.isReady() && (!browseSources || playlistMode)) View.VISIBLE else View.GONE
        }
        if (::libraryTitle.isInitialized) {
            libraryTitle.textSize = if (short) 11f else 12f
            libraryTitle.maxLines = 1
            libraryTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        }
        if (::songFilter.isInitialized) {
            songFilter.layoutParams.height = dp(if (short) 36 else 44)
            songFilter.textSize = if (short) 12.5f else 14f
            songFilter.setPadding(dp(11), dp(if (short) 4 else 9), dp(11), dp(if (short) 4 else 9))
            songFilter.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        if (::status.isInitialized) {
            status.visibility = if (short && !globalScanLoading) View.GONE else View.VISIBLE
        }
        // Keep the source rows as the main scroll surface. The mini-player
        // cannot steal most of a short split-screen pane.
        miniOpenPlayer.visibility = View.GONE
        nowPlayingTitle.maxLines = 1
        nowPlayingTitle.textSize = if (short) 12.5f else 14f
        nowPlayingTitle.setPadding(0, dp(if (short) 1 else 2), 0, dp(if (short) 1 else 2))
        if (::nowPlayingSubtitle.isInitialized) {
            nowPlayingSubtitle.textSize = if (short) 10f else 11.5f
        }
        if (::miniArtFrame.isInitialized) {
            val artSize = dp(ShortWindowLayoutPolicy.miniArtSizeDp(short))
            miniArtFrame.layoutParams.width = artSize
            miniArtFrame.layoutParams.height = artSize
        }
        if (::miniControls.isInitialized) {
            val ctrlHeight = dp(ShortWindowLayoutPolicy.miniControlHeightDp(short))
            miniControls.layoutParams.height = ctrlHeight
            for (i in 0 until miniControls.childCount) {
                val child = miniControls.getChildAt(i)
                child.layoutParams.height = ctrlHeight
                if (child is Button) {
                    child.minHeight = dp(if (short) 30 else 38)
                    child.minimumHeight = dp(if (short) 30 else 38)
                    child.textSize = if (short) 15f else 18f
                }
            }
        }
        tracks.clipToPadding = false
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
        if (::sourcesAdapter.isInitialized) sourcesAdapter.notifyDataSetChanged()
    }

    private fun commitNavigationGestureIfIdle() {
        if (listScrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) return
        val requested = pendingNavigationCollapse ?: return
        pendingNavigationCollapse = null
        setNavigationCollapsed(requested)
    }

    private fun setNavigationCollapsed(collapsed: Boolean) {
        val allowCollapse = ShortWindowLayoutPolicy.shouldAutoCollapseTopNav(currentThemePreset, currentPaneMode)
        val targetCollapse = if (allowCollapse) collapsed else false
        if (navigationCollapsed == targetCollapse) return
        if (targetCollapse && (!auth.isReady() || tracks.count < 2)) return
        val firstVisible = tracks.firstVisiblePosition
        val firstOffset = tracks.getChildAt(0)?.top ?: 0
        navigationCollapsed = targetCollapse
        // Only the one-row navigation tabs hide on song-list scroll if allowed.
        // In wide / landscape mode, top navigation tabs remain persistently visible.
        navigationRow.visibility = if (targetCollapse || !auth.isReady()) View.GONE else View.VISIBLE
        tracks.post { if (!isDestroyed) tracks.setSelectionFromTop(firstVisible, firstOffset) }
    }

    private fun showFullPlayer() {
        val dialog = fullPlayer ?: NowPlayingFullScreenDialog(
            this,
            themeTokens = { themeTokens },
            media = { player },
            downloadProgress = {
                val uri = player?.currentMediaItem?.localConfiguration?.uri
                val fileId = uri?.let(TelegramProgressiveCache::fileId)
                if (fileId == null) null
                else {
                    val loaded = TelegramProgressiveCache.readyBytes(fileId)
                    val total = TelegramProgressiveCache.totalBytes(fileId)
                    val complete = TelegramProgressiveCache.isComplete(fileId)
                    when {
                        complete -> "Đã tải đầy đủ · Có thể nghe offline"
                        total > 0L -> String.format(java.util.Locale.US,
                            "Telegram · Đã nhận %.1f / %.1f MB · %d%%",
                            loaded / 1_000_000.0, total / 1_000_000.0,
                            (loaded * 100 / total).coerceIn(0, 100))
                        else -> String.format(java.util.Locale.US,
                            "Telegram · Đang đệm %.1f MB", loaded / 1_000_000.0)
                    }
                }
            },
            source = {
                val itemId = currentTelegramMessageId()
                val chat = songs.firstOrNull { it.messageId == itemId }?.chatId
                if (chat != null) sourceNames[chat] ?: "Nhạc từ Telegram"
                else "Tệp âm thanh đã chọn"
            },
            previous = { playAdjacent(-1) },
            next = { playAdjacent(1) },
            isFavorite = {
                val current = currentTelegramMessageId()
                current != null && current in favoriteIds
            },
            toggleFavorite = { toggleCurrentFavorite() },
            openQueue = { showUpcomingQueue() }
        ).also { fullPlayer = it }
        dialog.show()
    }

    private fun restorePlayingQueueIfAny(accountId: Long) {
        if (!auth.isReady() || accountId <= 0L) return
        val restored = ShareablePlaybackService.currentTelegramQueue(accountId)
        if (restored.isEmpty()) return
        queueSongOrder = restored.map { track ->
            Song(track.fileId, track.title, track.filename.ifBlank { track.title },
                track.messageId, track.chatId, track.expectedSize, rawMessageId = track.genuineMessageId)
        }
        restored.forEach { track ->
            val path = track.cachedPath ?: return@forEach
            val local = File(path)
            if (local.isFile && TelegramAccountStoragePolicy.isOwnedFile(filesRoot, local)) {
                cachedSongPaths[track.messageId] = local.canonicalPath
            }
        }
    }

    private fun showUpcomingQueue() {
        if (!auth.isReady()) return
        library.authorizedUser()?.value?.let { restorePlayingQueueIfAny(it) }
        val list = queueSongOrder
        queueDialog?.dismiss()
        if (list.isEmpty()) {
            val popup = android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar)
            queueDialog = popup
            popup.setOnDismissListener { if (queueDialog === popup) queueDialog = null }
            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(18))
                setBackgroundColor(midnight)
                addView(TextView(this@TelegramPreviewActivity).apply {
                    text = "HÀNG ĐỢI MELOVAULT"
                    textSize = 18f
                    setTextColor(ink)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@TelegramPreviewActivity).apply {
                    val playing = player?.currentMediaItem?.mediaMetadata?.title
                    text = if (playing != null) {
                        "Đang phát: $playing\nDanh sách nguồn đang được khôi phục. " +
                            "Hãy đợi Telegram đồng bộ hoặc mở lại nhóm."
                    } else "Chưa chọn bài nào. Mở một nhóm nhạc để tạo hàng đợi."
                    textSize = 14f
                    setTextColor(subtle)
                    setPadding(0, dp(24), 0, dp(22))
                })
                addView(uiButton("Đóng").apply { setOnClickListener { popup.dismiss() } })
            }
            ViewCompat.setOnApplyWindowInsetsListener(wrapper) { _, insets ->
                val systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val computed = WindowInsetsPolicy.computeDialogRootPadding(
                    systemBars = WindowInsetsPolicy.Insets(
                        systemBars.left, systemBars.top, systemBars.right, systemBars.bottom
                    ),
                    displayCutout = WindowInsetsPolicy.Insets.ZERO,
                    baseHorizontal = dp(18),
                    baseTop = dp(18),
                    baseBottom = dp(18)
                )
                wrapper.setPadding(computed.left, computed.top, computed.right, computed.bottom)
                insets
            }
            popup.setContentView(wrapper)
            popup.show()
            popup.window?.apply {
                setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                navigationBarColor = midnight
                statusBarColor = midnight
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    setDecorFitsSystemWindows(false)
                }
            }
            return
        }
        val selected = currentTelegramMessageId()
        val at = list.indexOfFirst { it.messageId == selected }.coerceAtLeast(0)
        val from = 0
        val excerpt = list // ListView virtualizes rows; don't silently omit later tracks.
        val popup = android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar)
        queueDialog = popup
        popup.setOnDismissListener { if (queueDialog === popup) queueDialog = null }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            setBackgroundColor(midnight)
        }
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val computed = WindowInsetsPolicy.computeDialogRootPadding(
                systemBars = WindowInsetsPolicy.Insets(
                    systemBars.left, systemBars.top, systemBars.right, systemBars.bottom
                ),
                displayCutout = WindowInsetsPolicy.Insets.ZERO,
                baseHorizontal = dp(16),
                baseTop = dp(14),
                baseBottom = dp(16)
            )
            container.setPadding(computed.left, computed.top, computed.right, computed.bottom)
            insets
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "DANH SÁCH PHÁT · ${list.size} BÀI"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ink)
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        header.addView(uiButton("Xong").apply {
            setOnClickListener { popup.dismiss() }
        }, LinearLayout.LayoutParams(dp(82), dp(48)))
        container.addView(header, LinearLayout.LayoutParams(-1, dp(60)))
        container.addView(TextView(this).apply {
            text = "Danh sách của nguồn · Bài chưa tải sẽ cần tải qua Telegram"
            textSize = 12f
            setTextColor(subtle)
        }, LinearLayout.LayoutParams(-1, dp(47)))
        val rows = ListView(this).apply {
            divider = null
            dividerHeight = dp(8)
            setBackgroundColor(midnight)
            cacheColorHint = Color.TRANSPARENT
        }
        val queueAdapter = object : ArrayAdapter<Song>(
            this, android.R.layout.simple_list_item_1, excerpt
        ) {
            override fun getView(
                position: Int, convertView: View?, parent: android.view.ViewGroup
            ): View {
                val song = getItem(position) ?: return View(this@TelegramPreviewActivity)
                val current = (from + position == at)
                val local = cachedSongPaths[song.messageId]?.let { File(it) }
                val ready = local?.isFile == true &&
                    TelegramAccountStoragePolicy.isOwnedFile(filesRoot, local)
                return LinearLayout(this@TelegramPreviewActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(panel)
                    background = rounded(panel, 12)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    minimumHeight = dp(74)
                    addView(TextView(this@TelegramPreviewActivity).apply {
                        text = (if (current) "▶ " else "") +
                            TelegramSongDisplayPolicy.label(song.title, song.filename)
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        textSize = 15f
                        setTextColor(if (current) accent else ink)
                    })
                    addView(TextView(this@TelegramPreviewActivity).apply {
                        text = "${from + position + 1} / ${list.size}" +
                            if (ready) "  ·  Đã tải" else "  ·  Chưa tải"
                        textSize = 12f
                        setTextColor(subtle)
                        setPadding(0, dp(4), 0, 0)
                    })
                }
            }
        }
        rows.adapter = queueAdapter
        rows.setOnItemClickListener { _, _, index, _ ->
            excerpt.getOrNull(index)?.let { song ->
                popup.dismiss()
                downloadAndPlay(song)
            }
        }
        container.addView(rows, LinearLayout.LayoutParams(-1, 0, 1f))
        popup.setContentView(container)
        popup.show()
        popup.window?.apply {
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            navigationBarColor = midnight
            statusBarColor = midnight
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            }
        }
        rows.setSelection((at - from - 2).coerceAtLeast(0))
    }

    private fun currentTelegramMessageId(): Long? {
        val mediaId = player?.currentMediaItem?.mediaId.orEmpty()
        if (!mediaId.startsWith("telegram:")) return null
        return mediaId.removePrefix("telegram:").toLongOrNull()?.takeIf { it > 0 }
    }

    private fun updatePlaylistButtons() {
        if (!::favoriteButton.isInitialized || !::playlistButton.isInitialized) return
        val currentId = currentTelegramMessageId()
        favoriteButton.isEnabled = auth.isReady() && currentId != null
        favoriteButton.text = if (currentId != null && currentId in favoriteIds) {
            "★ Đã lưu bài"
        } else "☆ Lưu bài"
        playlistButton.isEnabled = auth.isReady()
        playlistButton.text = if (playlistMode) "☷ Tất cả bài" else "☷ Playlist"
    }

    private fun refreshSongRows() {
        if (!::sourcesAdapter.isInitialized || !::songFilter.isInitialized) return
        val q = songFilter.text?.toString()?.trim().orEmpty()
        sourcesButton.isEnabled = auth.isReady() && allChatsMode
        sourcesButton.visibility = if (allChatsMode) View.VISIBLE else View.GONE

        val userId = library.authorizedUser()?.value ?: 0L
        val db = if (userId > 0L) libraryDb else null

        val generation = uiQueryGeneration.incrementAndGet()
        val isBrowseSources = allChatsMode && browseSources && !playlistMode
        val isPlaylist = playlistMode
        val currentChatId = selectedChatId
        val isAllChats = allChatsMode
        val currentFavIds = favoriteIds.toSet()

        executeOnDb {
            if (uiQueryGeneration.get() != generation) return@executeOnDb

            if (isBrowseSources) {
                val sources = if (db != null && userId > 0L) db.getSources(userId) else emptyList()
                val totalCount = if (db != null && userId > 0L) db.getSongCount(userId) else songs.size
                val entries = if (sources.isNotEmpty()) {
                    sources.filter {
                        q.isBlank() || TelegramSourcePolicy.matchesSource(it.title, q)
                    }.map { it.chatId to it.trackCount }
                } else {
                    TelegramSourcePolicy.sortedCounts(songs.map { it.chatId }).filter { (chatId, _) ->
                        TelegramSourcePolicy.matchesSource(sourceNames[chatId] ?: "Nguồn Telegram $chatId", q)
                    }
                }
                val display = if (q.isBlank()) listOf(0L to totalCount) + entries else entries
                val sourceCount = if (sources.isNotEmpty()) sources.size else display.filter { it.first != 0L }.size

                runOnUiThread {
                    if (uiQueryGeneration.get() != generation || isDestroyed) return@runOnUiThread
                    visibleSourceIds = display.map { it.first }
                    shownSongs = emptyList()
                    if (tracks.adapter !== sourcesAdapter) tracks.adapter = sourcesAdapter
                    sourcesAdapter.clear()
                    sourcesAdapter.addAll(display.map { (chatId, count) ->
                        val label = if (chatId == 0L) "♫ Tất cả nguồn"
                            else sourceNames[chatId] ?: "Nguồn Telegram $chatId"
                        "$label  ·  $count bài"
                    })
                    libraryTitle.text = "NGUỒN NHẠC TELEGRAM  ·  $sourceCount nhóm/kênh/chat"
                    sourcesButton.text = "☷  Nhóm / kênh / bot ($sourceCount)"
                    songFilter.hint = "Tìm nhóm, kênh hoặc bot…"
                    updateBottomNavState()
                    updateSyncHeaderSubtitle()
                }
            } else {
                val (loadedSongs, totalDisplayCount) = if (isPlaylist) {
                    val favSongs = currentFavIds.mapNotNull { id -> songs.firstOrNull { it.messageId == id } }
                    val filtered = if (q.isBlank()) favSongs else favSongs.filter {
                        it.title.contains(q, ignoreCase = true) || it.filename.contains(q, ignoreCase = true)
                    }
                    Pair(filtered, filtered.size)
                } else if (isAllChats && db != null && userId > 0L) {
                    val filterQuery = q.takeIf { it.isNotBlank() }
                    val count = db.getSongCount(userId, chatId = currentChatId, searchQuery = filterQuery)
                    val page = db.getSongs(userId, chatId = currentChatId, searchQuery = filterQuery, offset = 0, limit = 200)
                        .map {
                            Song(
                                it.fileId, it.title, it.filename, it.songKey, it.chatId, it.fileSize,
                                rawMessageId = it.messageId,
                                performer = it.performer,
                                durationSeconds = it.durationSeconds,
                                sourceTitle = it.sourceTitle
                            )
                        }
                    Pair(page, count)
                } else if (currentChatId != null) {
                    val chatSongs = songs.filter { it.chatId == currentChatId }
                    val filtered = if (q.isBlank()) chatSongs else chatSongs.filter {
                        it.title.contains(q, ignoreCase = true) || it.filename.contains(q, ignoreCase = true)
                    }
                    Pair(filtered, filtered.size)
                } else {
                    val filtered = if (q.isBlank()) songs.toList() else songs.filter {
                        it.title.contains(q, ignoreCase = true) || it.filename.contains(q, ignoreCase = true)
                    }
                    Pair(filtered, filtered.size)
                }
                val finalSongs = if (filterOnlyDownloaded) {
                    loadedSongs.filter { s ->
                        val p = cachedSongPaths[s.messageId]
                        p != null && File(p).isFile
                    }
                } else loadedSongs
                val finalDisplayCount = if (filterOnlyDownloaded) finalSongs.size else totalDisplayCount

                val sourceCount = if (db != null && userId > 0L) db.getSources(userId).size else songs.map { it.chatId }.distinct().size

                runOnUiThread {
                    if (uiQueryGeneration.get() != generation || isDestroyed) return@runOnUiThread
                    if (tracks.adapter !== adapter) tracks.adapter = adapter
                    shownSongs = finalSongs
                    totalCountForCurrentView = finalDisplayCount
                    adapter.clear()
                    adapter.addAll(shownSongs.map {
                        TelegramSongDisplayPolicy.label(it.title, it.filename)
                    })
                    val chatId = currentChatId
                    libraryTitle.text = when {
                        isPlaylist -> "PLAYLIST  ·  BÀI ĐÃ LƯU (${shownSongs.size})"
                        !isAllChats -> "THƯ VIỆN NHẠC  ·  SAVED MESSAGES (${shownSongs.size})"
                        chatId != null -> "‹ " + (sourceNames[chatId] ?: "Nguồn Telegram $chatId") + " ($totalDisplayCount bài)"
                        else -> "THƯ VIỆN NHẠC  ·  TẤT CẢ TELEGRAM ($totalDisplayCount bài)"
                    }
                    sourcesButton.text = "‹  Trở về nhóm / kênh ($sourceCount)"
                    songFilter.hint = "Tìm bài hát theo tên…"
                    if (::filterChipsRow.isInitialized) {
                        filterChipsRow.visibility = if (auth.isReady() && !isBrowseSources) View.VISIBLE else View.GONE
                        updateFilterChipsUi()
                    }
                    updateBottomNavState()
                    updateSyncHeaderSubtitle()
                }
            }
        }
    }

    private fun toggleCurrentFavorite() {
        val account = library.authorizedUser() ?: return
        if (!auth.isReady()) return
        val target = currentTelegramMessageId() ?: return
        if (songs.none { it.messageId == target } &&
            queueSongOrder.none { it.messageId == target }) return
        val next = TelegramPlaylistPolicy.toggle(favoriteIds, target)
        val result = runCatching { playlistStore.save(account, next) }
        if (result.isFailure) {
            showMessage("Không lưu được playlist an toàn trên thiết bị.")
            return
        }
        favoriteIds = next
        updatePlaylistButtons()
        refreshSongRows()
        showMessage("Playlist cá nhân: ${favoriteIds.size} bài đã lưu.")
    }

    private fun playAdjacent(delta: Int) {
        if (!auth.isReady()) return
        val currentId = currentTelegramMessageId() ?: return
        val index = queueSongOrder.indexOfFirst { it.messageId == currentId }
        if (index < 0) return
        val target = queueSongOrder.getOrNull(index + delta) ?: return
        val controller = player
        val mediaId = "telegram:${target.messageId}"
        if (controller != null) {
            val targetIndex = (0 until controller.mediaItemCount).firstOrNull {
                controller.getMediaItemAt(it).mediaId == mediaId
            }
            if (targetIndex != null) {
                controller.seekToDefaultPosition(targetIndex)
                controller.play()
                return
            }
        }
        downloadAndPlay(target)
    }

    private fun qrBridgeFile(): File = File(noBackupFilesDir, "e91_pending_qr_login.txt")

    /** Login links are single-purpose short-lived bearer credentials.
     * Never log them, back them up or store them outside app-private space.
     * The owner-only debug host can read the file with ADB run-as.
     */
    private fun clearPendingQrLink() {
        runCatching { qrBridgeFile().delete() }
    }

    private fun requestQrLogin() {
        if (auth.step != TelegramAuthStep.WAIT_PHONE_NUMBER) return
        try {
            qrLogin.isEnabled = false
            clearPendingQrLink()
            send("requestQrCodeAuthentication", "qrLogin") {
                it.put("other_user_ids", org.json.JSONArray())
            }
            showMessage("Đang lấy mã QR. Quét trên máy khác khi QR xuất hiện trên Lenovo.")
        } catch (_: Exception) {
            qrLogin.isEnabled = true
            showMessage("Không thể yêu cầu QR. Có thể đăng nhập bằng số điện thoại.")
        }
    }

    private fun showMessage(message: String) {
        if (!isFinishing && !isDestroyed) {
            runOnUiThread { if (!isFinishing && !isDestroyed) status.text = message }
        }
    }

    private fun slotPath(): File {
        val prefs = getSharedPreferences("e91_tdlib_preview", MODE_PRIVATE)
        val slot = prefs.getString("slot_id", null)
            ?: TelegramAccountStoragePolicy.newSessionSlotId().also {
                check(prefs.edit().putString("slot_id", it).commit())
            }
        // Directory assigned before user identity is known. Never trust UI
        // input as the identity; bind only after authorized getMe().
        return TelegramAccountStoragePolicy.sessionSlotDirectory(noBackupFilesDir, slot)
    }

    private fun tdlibDatabaseKey(): String {
        val root = noBackupFilesDir
        val encrypted = File(root, "e91_tdlib_db_key_v1.enc")
        val alias = "melovault_e91_tdlib_db_key_v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generator.generateKey()
        }
        val key = store.getKey(alias, null) as SecretKey
        val clear = if (encrypted.isFile) {
            val blob = encrypted.readBytes()
            require(blob.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, 12))
            cipher.doFinal(blob, 12, blob.size - 12)
        } else {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val payload = cipher.iv + cipher.doFinal(bytes)
            val tmp = File(root, "e91_tdlib_db_key_v1.enc.part")
            tmp.writeBytes(payload)
            if (!tmp.renameTo(encrypted)) {
                tmp.delete()
                error("Cannot store TDLib encryption key")
            }
            bytes
        }
        require(clear.size == 32)
        return Base64.encodeToString(clear, Base64.NO_WRAP)
    }

    private fun initNative() {
        try {
            val slot = slotPath()
            filesRoot = File(slot, "files").apply { check(mkdirs() || isDirectory) }
            val sessionDb = File(slot, "database").apply { check(mkdirs() || isDirectory) }
            val encryptedKey = tdlibDatabaseKey()
            authEpoch = auth.startClient()
            val listener: (JSONObject) -> Unit = { event ->
                onNativeEvent(event, sessionDb, encryptedKey)
            }
            nativeReceiver = listener
            nativeClientId = ShareableTdlibOwner.attachUi(slot, listener)
            running = true
            send("getAuthorizationState", "state")
        } catch (e: Exception) {
            showMessage("Không thể khởi tạo Telegram: " +
                (e.message ?: e.javaClass.simpleName).take(130))
        }
    }

    private val resolveRequestCounter = java.util.concurrent.atomic.AtomicLong(1L)
    private val pendingMessageResolutions =
        java.util.concurrent.ConcurrentHashMap<String, (TelegramAudioResolverPolicy.Result) -> Unit>()
    private var currentDownloadingSong: Song? = null

    private fun resolveTelegramMessage(
        chatId: Long,
        messageId: Long,
        callback: (TelegramAudioResolverPolicy.Result) -> Unit
    ) {
        if (!auth.isReady()) {
            callback(TelegramAudioResolverPolicy.Result.Error("Phiên Telegram chưa sẵn sàng"))
            return
        }
        val reqId = resolveRequestCounter.incrementAndGet()
        val tag = "resolveMsg:$reqId:$chatId:$messageId"
        pendingMessageResolutions[tag] = callback
        try {
            send("getMessage", tag) {
                it.put("chat_id", chatId)
                it.put("message_id", messageId)
            }
            mainHandler.postDelayed({
                val timedOutCb = pendingMessageResolutions.remove(tag)
                if (timedOutCb != null) {
                    runOnUiThread {
                        timedOutCb(TelegramAudioResolverPolicy.Result.Error("Quá thời gian kết nối tới máy chủ Telegram"))
                    }
                }
            }, 12000L)
        } catch (e: Exception) {
            pendingMessageResolutions.remove(tag)
            callback(TelegramAudioResolverPolicy.Result.Error("Lỗi gửi yêu cầu tới Telegram: ${e.message}"))
        }
    }

    private fun send(type: String, tag: String? = null, configure: ((JSONObject) -> Unit)? = null) {
        ShareableTdlibOwner.send(type, tag, configure)
    }

    private fun onNativeEvent(result: JSONObject, sessionDb: File, secret: String) {
        val kind = result.optString("@type")
        val tag = result.optString("@extra")
        if (kind == "error") {
            if (tag.startsWith("resolveMsg:")) {
                val cb = pendingMessageResolutions.remove(tag)
                if (cb != null) {
                    val errMsg = result.optString("message").ifBlank {
                        "Không tìm thấy tin nhắn trên Telegram (mã ${result.optInt("code")})"
                    }
                    runOnUiThread { cb(TelegramAudioResolverPolicy.Result.Error(errMsg)) }
                }
                return
            }
            if (tag.startsWith("sourceName:")) return
            if (tag.startsWith("sync:")) {
                processSyncError(result, tag)
                return
            }
            if (tag.startsWith("all:")) {
                if (tag == "all:${globalScanGeneration}:${globalScanPhase}" &&
                    allChatsMode) {
                    runOnUiThread {
                        status.text = "Telegram không tìm được một nhóm tệp; đang tiếp tục…"
                    }
                    advanceGlobalPhase()
                }
                return
            }
            if (tag.startsWith("savedHistory:")) {
                historyLoading = false
                runOnUiThread { refresh.isEnabled = true }
                return
            }
            if (tag.startsWith("prefetch:")) {
                prefetchWaiting = null
                return // A prefetched-neighbor miss must not interrupt current audio.
            }
            // Initial parameter errors need a useful diagnosis. Raw auth errors
            // must never reveal an OTP, phone number or 2FA content in the UI.
            val safeStage = tag.takeIf { it in setOf("parameters", "state", "identity") }
                ?: "authentication"
            val message = result.optString("message")
            val known = when {
                message.contains("API_ID_INVALID", true) -> "API_ID_INVALID"
                message.contains("API_HASH_INVALID", true) -> "API_HASH_INVALID"
                message.contains("database", true) -> "database configuration"
                message.contains("encryption", true) -> "encryption configuration"
                message.contains("setTdlibParameters", true) -> "TDLib parameters"
                message.contains("PHONE_NUMBER_INVALID", true) -> "PHONE_NUMBER_INVALID"
                else -> "unknown"
            }
            // Only at parameters stage, before any end-user credentials were
            // entered, surface TDLib's parser message with secrets scrubbed.
            val diagnostic = if (safeStage == "parameters") {
                message.replace(secret, "[db-key]")
                    .replace(BuildConfig.TELEGRAM_APP_API_HASH, "[app-hash]")
                    .replace(Regex("[A-Za-z0-9+/]{40,}={0,2}"), "[redacted]")
                    .take(180)
            } else known
            showMessage("Telegram lỗi " + result.optInt("code") +
                " tại " + safeStage + ": " + diagnostic)
            return
        }
        if (kind == "updateAuthorizationState") {
            onAuthState(result.optJSONObject("authorization_state"), sessionDb, secret)
            return
        }
        if (tag == "state" && kind.startsWith("authorizationState")) {
            onAuthState(result, sessionDb, secret)
            return
        }
        if (tag == "identity" && kind == "user") {
            val userId = result.optLong("id")
            try {
                val verified = TelegramUserId(userId)
                if (auth.onGetMe(authEpoch, verified)) {
                    ShareableTdlibOwner.markGetMeVerified(verified.value)
                    showMessage("Đã đăng nhập Telegram. Tải nhạc trong Saved Messages.")
                    favoriteIds = runCatching { playlistStore.load(verified) }
                        .getOrDefault(emptyList())
                    playlistMode = false
                    runOnUiThread {
                        updatePlaylistButtons()
                        refresh.isEnabled = true
                        globalSearch.isEnabled = true
                        openDocument.isEnabled = true
                        logout.isEnabled = true
                        submit.isEnabled = false
                        input.visibility = View.GONE
                        authControls.visibility = View.GONE
                        restorePlayingQueueIfAny(verified.value)
                        applyResponsiveLibraryUi()
                    }
                    loadDatabaseIndex(verified.value) {
                        scanAllTelegramAudio(browseAfter = false, incremental = true)
                    }
                }
            } catch (_: IllegalArgumentException) {
                showMessage("Không xác minh được tài khoản Telegram.")
            }
            return
        }
        if (tag.startsWith("sourceName:") && kind == "chat") {
            val currentGeneration = "sourceName:${globalScanGeneration}:"
            if (allChatsMode && tag.startsWith(currentGeneration)) {
                val chatId = result.optLong("id")
                val title = result.optString("title").trim()
                if (chatId != 0L && title.isNotBlank() &&
                    tag == "$currentGeneration$chatId") {
                    val cleanTitle = title.take(110)
                    sourceNames[chatId] = cleanTitle
                    val user = library.authorizedUser()?.value
                    if (user != null && user > 0L) {
                        executeOnDb { libraryDb?.updateSourceTitle(user, chatId, cleanTitle) }
                    }
                    runOnUiThread {
                        if (allChatsMode && browseSources) {
                            refreshSongRows()
                        } else if (allChatsMode && selectedChatId == chatId) {
                            libraryTitle.text = cleanTitle
                        }
                    }
                }
            }
            return
        }
        if (tag.startsWith("resolveMsg:")) {
            val cb = pendingMessageResolutions.remove(tag)
            if (cb != null) {
                val res = TelegramAudioResolverPolicy.parseMessage(result)
                runOnUiThread { cb(res) }
            }
            return
        }
        if (tag == "savedChat" && kind == "chat") {
            savedChatId = result.optLong("id")
            if (!allChatsMode) requestHistory()
            return
        }
        if (tag.startsWith("sync:") && kind == "foundMessages") {
            processSyncPage(result, tag)
            return
        }
        if (tag.startsWith("all:") && kind == "foundMessages") {
            acceptGlobalResults(result, tag)
            return
        }
        if (tag.startsWith("savedHistory:") && kind == "messages" &&
            tag == "savedHistory:${globalScanGeneration}" && !allChatsMode) {
            val items = result.optJSONArray("messages") ?: org.json.JSONArray()
            val extracted = mutableListOf<Song>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val song = parseTelegramSong(item, savedChatId) ?: continue
                if (seenSongMessageIds.add(song.messageId)) extracted.add(song)
            }
            val pageCount = ++historyPages
            val oldestId = items.optJSONObject(items.length() - 1)?.optLong("id") ?: 0L
            // getChatHistory includes from_message_id; skip it if TDLib returns only
            // that boundary message. Never loop without moving backward.
            val nextCursor = if (historyCursorId > 0L && oldestId >= historyCursorId) {
                historyCursorId - 1L
            } else oldestId
            val hasMore = items.length() > 0 && nextCursor > 0L &&
                nextCursor != historyCursorId && pageCount < maxHistoryPages
            runOnUiThread {
                if (pageCount == 1) {
                    songs.clear()
                }
                songs.addAll(extracted)
                refreshSongRows()
                updatePlaylistButtons()
                if (hasMore) {
                    status.text = "Đang đọc lịch sử Telegram: ${songs.size} bài / ${pageCount} lượt."
                } else {
                    status.text = "Đã tìm thấy ${songs.size} bài nhạc trong ${pageCount} lượt lịch sử."
                    refresh.isEnabled = true
                    if (currentSongIndex >= 0) prefetchNextCachedNeighbor()
                }
            }
            if (hasMore) {
                historyCursorId = nextCursor
                requestHistoryPage()
            } else {
                historyLoading = false
            }
            return
        }
        if (kind == "file" && tag.startsWith("download:")) {
            TelegramProgressiveCache.observe(result)
            playIfDownloaded(result)
        }
        if (kind == "file" && tag.startsWith("prefetch:")) {
            observePrefetch(result)
        }
        if (kind == "updateFile") {
            result.optJSONObject("file")?.let {
                TelegramProgressiveCache.observe(it)
                playIfDownloaded(it)
                observePrefetch(it)
            }
        }
    }

    private fun onAuthState(state: JSONObject?, db: File, secret: String) {
        val kind = state?.optString("@type") ?: return
        when (kind) {
            "authorizationStateWaitTdlibParameters" -> {
                // TDLib can send both an auth update and getAuthorizationState
                // reply for the same state. Parameters must be submitted ONCE.
                if (tdlibParametersSent) return
                tdlibParametersSent = true
                auth.onNativeState(authEpoch, TelegramAuthStep.WAIT_PARAMETERS)
                send("setTdlibParameters", "parameters") {
                    it.put("use_test_dc", false)
                    it.put("database_directory", db.absolutePath)
                    it.put("files_directory", filesRoot.absolutePath)
                    it.put("database_encryption_key", secret)
                    it.put("use_file_database", true)
                    it.put("use_chat_info_database", true)
                    it.put("use_message_database", true)
                    it.put("use_secret_chats", false)
                    it.put("api_id", BuildConfig.TELEGRAM_APP_API_ID)
                    it.put("api_hash", BuildConfig.TELEGRAM_APP_API_HASH)
                    it.put("system_language_code", "vi")
                    it.put("device_model", android.os.Build.MODEL.take(70))
                    it.put("system_version", android.os.Build.VERSION.RELEASE.take(24))
                    it.put("application_version", BuildConfig.VERSION_NAME)
                }
            }
            "authorizationStateWaitPhoneNumber" -> {
                clearPendingQrLink()
                showInput(TelegramAuthStep.WAIT_PHONE_NUMBER,
                    "Có thể đăng nhập bằng QR trên Lenovo hoặc nhập số Telegram.",
                    InputType.TYPE_CLASS_PHONE)
                runOnUiThread { qrLogin.isEnabled = true }
            }
            "authorizationStateWaitCode" ->
                showInput(TelegramAuthStep.WAIT_CODE,
                    "Nhập mã Telegram gửi cho bạn.",
                    InputType.TYPE_CLASS_NUMBER)
            "authorizationStateWaitPassword" ->
                showInput(TelegramAuthStep.WAIT_PASSWORD,
                    "Nhập mật khẩu xác minh hai bước Telegram.",
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            "authorizationStateWaitEmailAddress" ->
                showInput(TelegramAuthStep.WAIT_EMAIL_ADDRESS,
                    "Nhập email xác minh mà Telegram yêu cầu.",
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            "authorizationStateWaitEmailCode" ->
                showInput(TelegramAuthStep.WAIT_EMAIL_CODE,
                    "Nhập mã Telegram gửi về email.",
                    InputType.TYPE_CLASS_NUMBER)
            "authorizationStateWaitOtherDeviceConfirmation" -> {
                auth.onNativeState(authEpoch, TelegramAuthStep.WAIT_OTHER_DEVICE_CONFIRMATION)
                val link = state.optString("link")
                if (link.startsWith("tg://login?token=") && link.length in 32..1024) {
                    // Write only to private no-backup directory, read locally via ADB.
                    runCatching { qrBridgeFile().writeText(link, Charsets.UTF_8) }
                        .onFailure { showMessage("Không lưu được QR cục bộ.") }
                        .onSuccess {
                            showMessage("Mã QR đã sẵn sàng trên Lenovo. Quét bằng Telegram đang đăng nhập.")
                        }
                } else {
                    showMessage("TDLib không cung cấp QR hợp lệ. Hãy thử lại.")
                }
                runOnUiThread { qrLogin.isEnabled = false }
            }
            "authorizationStateWaitRegistration" -> {
                auth.onNativeState(authEpoch, TelegramAuthStep.WAIT_REGISTRATION)
                showMessage("Tài khoản mới cần đăng ký; bản Preview chưa hỗ trợ.")
            }
            "authorizationStateReady" -> {
                clearPendingQrLink()
                runOnUiThread { qrLogin.isEnabled = false }
                if (auth.onNativeState(authEpoch,
                        TelegramAuthStep.AUTHORIZED_AWAITING_GET_ME)) {
                    send("getMe", "identity")
                } else if (auth.isReady()) {
                    checkAutoRecovery()
                }
            }
            "authorizationStateLoggingOut", "authorizationStateClosing",
            "authorizationStateClosed" -> {
                autoRecoveryPolicy.reset()
                globalScanGeneration++
                globalScanLoading = false
                ShareableTdlibOwner.revokeAuthorization()
                TelegramProgressiveCache.clear()
                auth.logout()
                clearPendingQrLink()
                runOnUiThread {
                    clearPlaybackSession()
                    tracks.adapter = adapter
                    songs.clear()
                    adapter.clear()
                    favoriteIds = emptyList()
                    playlistMode = false
                    shownSongs = emptyList()
                    updatePlaylistButtons()
                    refresh.isEnabled = false
                    globalSearch.isEnabled = false
                    sourcesButton.isEnabled = false
                    sourceNames.clear()
                    requestedSourceNames.clear()
                    sourcesAdapter.clear()
                    tracks.adapter = adapter
                    openDocument.isEnabled = false
                    logout.isEnabled = false
                    submit.isEnabled = false
                    currentSongIndex = -1
                    nowPlayingTitle.text = "Chọn một bài hát để bắt đầu"
                    applyResponsiveLibraryUi()
                }
                showMessage("Đã đóng phiên Telegram. Mở lại ứng dụng để đăng nhập lại.")
            }
        }
    }

    private fun showInput(step: TelegramAuthStep, help: String, type: Int) {
        if (!auth.onNativeState(authEpoch, step)) return
        runOnUiThread {
            input.text?.clear()
            input.inputType = type
            authControls.visibility = View.VISIBLE
            collapsibleNavigation.visibility = View.VISIBLE
            input.visibility = View.VISIBLE
            submit.isEnabled = true
            status.text = help
        }
    }

    private fun submitAuthentication() {
        val value = input.text?.toString()?.trim().orEmpty()
        if (value.isBlank()) return
        val kind = when (auth.step) {
            TelegramAuthStep.WAIT_PHONE_NUMBER -> "setAuthenticationPhoneNumber"
            TelegramAuthStep.WAIT_CODE -> "checkAuthenticationCode"
            TelegramAuthStep.WAIT_PASSWORD -> "checkAuthenticationPassword"
            TelegramAuthStep.WAIT_EMAIL_ADDRESS -> "setAuthenticationEmailAddress"
            TelegramAuthStep.WAIT_EMAIL_CODE -> "checkAuthenticationEmailCode"
            else -> return
        }
        try {
            send(kind, "auth") {
                when (kind) {
                    "setAuthenticationPhoneNumber" -> {
                        it.put("phone_number", value)
                        it.put("settings", JSONObject().put(
                            "@type", "phoneNumberAuthenticationSettings"))
                    }
                    "checkAuthenticationCode" -> it.put("code", value)
                    "checkAuthenticationPassword" -> it.put("password", value)
                    "setAuthenticationEmailAddress" -> it.put("email_address", value)
                    "checkAuthenticationEmailCode" -> it.put(
                        "code", JSONObject().put("@type", "emailAddressAuthenticationCode")
                            .put("code", value))
                }
            }
            // Never persist or print passwords, codes, phone or email.
            input.text?.clear()
            status.text = "Đang chờ Telegram xác thực…"
        } catch (_: Exception) {
            input.text?.clear()
            showMessage("Không gửi được yêu cầu. Kiểm tra mạng và thử lại.")
        }
    }

    private fun openAudioDocument() {
        if (!auth.isReady()) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // Some Drive providers label FLAC as octet-stream.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { openDocumentResult.launch(intent) }
            .onFailure { showMessage("Không mở được trình chọn tệp trên thiết bị.") }
    }

    private fun parseTelegramSong(message: JSONObject, fallbackChatId: Long): Song? {
        val content = message.optJSONObject("content") ?: return null
        val mediaType = content.optString("@type")
        val attachment = when (mediaType) {
            "messageAudio" -> content.optJSONObject("audio")
            "messageDocument" -> content.optJSONObject("document")
            else -> null
        } ?: return null
        val file = attachment.optJSONObject(
            if (mediaType == "messageAudio") "audio" else "document"
        ) ?: return null
        val filename = attachment.optString("file_name", "audio")
        val title = attachment.optString("title").ifBlank { filename }
        val audioExtension = listOf(".flac", ".mp3", ".m4a", ".wav",
            ".ogg", ".opus", ".aac")
        if (mediaType != "messageAudio" &&
            audioExtension.none { filename.endsWith(it, ignoreCase = true) }) return null
        val messageId = message.optLong("id")
        val chatId = message.optLong("chat_id").takeIf { it != 0L } ?: fallbackChatId
        val fileId = file.optInt("id")
        if (messageId <= 0L || chatId == 0L || fileId <= 0) return null
        val selfChatId = savedChatId.takeIf { it != 0L }
            ?: (library.authorizedUser()?.value ?: 0L)
        val key = TelegramSongKey.forMessage(chatId, messageId, selfChatId)
        val local = file.optJSONObject("local")
        if (local?.optBoolean("is_downloading_completed") == true) {
            val path = local.optString("path")
            if (path.isNotBlank()) {
                val cached = File(path)
                if (cached.isFile &&
                    TelegramAccountStoragePolicy.isOwnedFile(filesRoot, cached)) {
                    cachedSongPaths[key] = cached.canonicalPath
                }
            }
        }
        if (allChatsMode && requestedSourceNames.size < 180 &&
            requestedSourceNames.add(chatId)) {
            runCatching {
                send("getChat", "sourceName:${globalScanGeneration}:$chatId") {
                    it.put("chat_id", chatId)
                }
            }
        }
        val performer = attachment.optString("performer").takeIf { it.isNotBlank() }
        val duration = attachment.optInt("duration")
        return Song(fileId, title, filename, key, chatId,
            file.optLong("size").takeIf { it > 0 }
                ?: file.optLong("expected_size").takeIf { it > 0 } ?: -1L,
            rawMessageId = messageId,
            performer = performer,
            durationSeconds = duration,
            sourceTitle = sourceNames[chatId])
    }

    private fun resetSource(modeAllChats: Boolean) {
        globalScanGeneration++
        globalScanLoading = false
        historyLoading = false
        allChatsMode = modeAllChats
        browseSources = modeAllChats
        selectedChatId = null
        sourceNames.clear()
        requestedSourceNames.clear()
        downloadWaiting = null
        prefetchWaiting = null
        seenSongMessageIds.clear()
        songs.clear()
        shownSongs = emptyList()
        // The MediaSession owns the playing queue through window resizing,
        // source browsing and Activity recreation. Never destroy it here.
        // It is cleared only on explicit logout/clearPlaybackSession.
        if (currentTelegramMessageId() == null) queueSongOrder = emptyList()
        playlistMode = false
        runOnUiThread {
            adapter.clear()
            songFilter.text?.clear()
            libraryTitle.text = if (modeAllChats)
                "THƯ VIỆN NHẠC  ·  CÁC CHAT TELEGRAM"
                else "THƯ VIỆN NHẠC  ·  SAVED MESSAGES"
            refresh.isEnabled = auth.isReady()
            globalSearch.isEnabled = auth.isReady()
            updatePlaylistButtons()
            refreshSongRows()
        }
    }

    private fun loadSavedMessages() {
        if (!auth.isReady()) return
        setNavigationCollapsed(false)
        resetSource(false)
        showMessage("Đang đọc nhạc từ Saved Messages…")
        val userId = library.authorizedUser()?.value ?: return
        if (savedChatId != 0L) requestHistory()
        else send("createPrivateChat", "savedChat") {
            it.put("user_id", userId)
            it.put("force", true)
        }
    }

    private fun requestHistory() {
        if (allChatsMode || savedChatId == 0L || !auth.isReady() ||
            historyLoading) return
        historyCursorId = 0L
        historyPages = 0
        seenSongMessageIds.clear()
        historyLoading = true
        runOnUiThread { refresh.isEnabled = false }
        requestHistoryPage()
    }

    private fun requestHistoryPage() {
        if (!auth.isReady() || allChatsMode) return
        send("getChatHistory", "savedHistory:${globalScanGeneration}") {
            it.put("chat_id", savedChatId)
            it.put("from_message_id", historyCursorId)
            it.put("offset", 0)
            it.put("limit", 60)
            it.put("only_local", false)
        }
    }

    private fun getOrInitDatabase(accountId: Long): TelegramLibraryDao {
        val current = libraryDb
        if (current != null && current is AndroidTelegramLibraryDatabase && current.accountId == accountId) {
            return current
        }
        if (current is AndroidTelegramLibraryDatabase) {
            runCatching { current.close() }
        }
        val db = AndroidTelegramLibraryDatabase(applicationContext, accountId)
        libraryDb = db
        syncCoordinator = TelegramSyncCoordinator(db)
        return db
    }

    private fun loadDatabaseIndex(userId: Long, onLoaded: (() -> Unit)? = null) {
        val db = getOrInitDatabase(userId)
        executeOnDb {
            val count = db.getSongCount(userId)
            val sources = db.getSources(userId)
            for (src in sources) {
                sourceNames[src.chatId] = src.title
            }
            val syncState = db.getSyncState(userId)
            val initialSongs = if (count > 0) db.getSongs(userId, offset = 0, limit = 200) else emptyList()
            runOnUiThread {
                if (!auth.isReady() || library.authorizedUser()?.value != userId || isDestroyed) return@runOnUiThread
                if (count > 0) {
                    totalCountForCurrentView = count
                    songs.clear()
                    seenSongMessageIds.clear()
                    for (s in initialSongs) {
                        val song = Song(
                            s.fileId, s.title, s.filename, s.songKey, s.chatId, s.fileSize,
                            rawMessageId = s.messageId,
                            performer = s.performer,
                            durationSeconds = s.durationSeconds,
                            sourceTitle = s.sourceTitle
                        )
                        songs.add(song)
                        seenSongMessageIds.add(song.messageId)
                    }
                    refreshSongRows()
                    val statusText = if (syncState?.status == TelegramSyncState.STATUS_COMPLETE) {
                        "Thư viện: $count bài từ ${sources.size} nguồn · Đã đồng bộ"
                    } else if (syncState?.status == TelegramSyncState.STATUS_PAUSED) {
                        "Thư viện: $count bài · Đồng bộ tạm dừng"
                    } else {
                        "Thư viện: $count bài từ ${sources.size} nguồn"
                    }
                    status.text = statusText
                }
                onLoaded?.invoke()
            }
        }
    }

    private fun reloadIndexedSourcesFromDb(userId: Long) {
        val db = libraryDb ?: return
        executeOnDb {
            val sources = db.getSources(userId)
            for (src in sources) {
                sourceNames[src.chatId] = src.title
            }
            runOnUiThread {
                if (allChatsMode && browseSources && !isDestroyed) {
                    refreshSongRows()
                }
            }
        }
    }

    private fun checkAutoRecovery() {
        val userId = library.authorizedUser()?.value ?: return
        executeOnDb {
            val db = getOrInitDatabase(userId)
            val dbCount = db.getSongCount(userId)
            runOnUiThread {
                if (!isDestroyed && autoRecoveryPolicy.shouldAutoRecover(
                        auth.isReady(), allChatsMode, globalScanLoading, dbCount, userId
                    )) {
                    scanAllTelegramAudio(browseAfter = browseSources, incremental = false)
                }
            }
        }
    }

    private fun scanAllTelegramAudio(
        browseAfter: Boolean = true,
        incremental: Boolean = false,
        forceRestart: Boolean = false
    ) {
        if (!auth.isReady() || globalScanLoading) return
        val user = library.authorizedUser()?.value ?: 0L
        if (user <= 0L) return
        autoRecoveryPolicy.recordScanDispatched(user)
        setNavigationCollapsed(false)
        if (forceRestart) {
            globalScanGeneration++
            historyLoading = false
            allChatsMode = true
            globalScanLoading = true
            seenSongMessageIds.clear()
        } else {
            allChatsMode = true
            globalScanLoading = true
        }
        browseSources = browseAfter
        runOnUiThread { globalSearch.isEnabled = false }

        val db = getOrInitDatabase(user)
        val coordinator = syncCoordinator ?: TelegramSyncCoordinator(db).also { syncCoordinator = it }
        val mode = if (incremental) TelegramSyncMode.INCREMENTAL else TelegramSyncMode.FULL_DISCOVERY
        val action = coordinator.startSync(user, mode, forceRestart)
        handleSyncAction(action, user)
    }

    private fun handleSyncAction(action: TelegramSyncAction, userId: Long) {
        when (action) {
            is TelegramSyncAction.RequestPage -> {
                val filter = if (action.phase == TelegramSyncState.PHASE_AUDIO)
                    "searchMessagesFilterAudio" else "searchMessagesFilterDocument"
                val tag = "sync:${userId}:${action.generation}:${action.phase}"
                val executeRequest = {
                    if (auth.isReady() && allChatsMode && globalScanLoading) {
                        send("searchMessages", tag) {
                            it.put("chat_list", JSONObject.NULL)
                            it.put("query", "")
                            it.put("offset", action.offset)
                            it.put("limit", action.limit)
                            it.put("filter", JSONObject().put("@type", filter))
                            it.put("chat_type_filter", JSONObject.NULL)
                            it.put("min_date", 0)
                            it.put("max_date", 0)
                        }
                    }
                }
                if (action.recommendedDelayMs > 0) {
                    mainHandler.postDelayed(executeRequest, action.recommendedDelayMs)
                } else {
                    executeRequest()
                }
            }
            is TelegramSyncAction.BackoffScheduled -> {
                globalScanLoading = true
                runOnUiThread {
                    status.text = "Đang tạm dừng chờ Telegram (thử lại sau ${action.retryAfterSeconds}s, lần ${action.attempt}/${TelegramSyncCoordinator.MAX_BACKOFF_RETRIES})…"
                }
                mainHandler.postDelayed({
                    if (auth.isReady() && allChatsMode && globalScanLoading) {
                        val coordinator = syncCoordinator
                        if (coordinator != null && coordinator.getActiveGeneration() == action.generation &&
                            coordinator.getActiveAccountId() == userId) {
                            val filter = if (action.phase == TelegramSyncState.PHASE_AUDIO)
                                "searchMessagesFilterAudio" else "searchMessagesFilterDocument"
                            val tag = "sync:${userId}:${action.generation}:${action.phase}"
                            send("searchMessages", tag) {
                                it.put("chat_list", JSONObject.NULL)
                                it.put("query", "")
                                it.put("offset", action.nextOffset)
                                it.put("limit", TelegramSyncCoordinator.PAGE_LIMIT)
                                it.put("filter", JSONObject().put("@type", filter))
                                it.put("chat_type_filter", JSONObject.NULL)
                                it.put("min_date", 0)
                                it.put("max_date", 0)
                            }
                        }
                    }
                }, action.retryAfterSeconds * 1000L)
            }
            is TelegramSyncAction.Completed -> {
                globalScanLoading = false
                autoRecoveryPolicy.recordScanCompleted(action.totalTracksIndexed)
                reloadIndexedSourcesFromDb(userId)
                runOnUiThread {
                    globalSearch.isEnabled = true
                    refreshSongRows()
                    val modeLabel = if (action.isIncremental) "đồng bộ mới" else "đồng bộ toàn bộ"
                    val actualSources = ShortWindowLayoutPolicy.truthfulSourceCount(visibleSourceIds).coerceAtLeast(1)
                    status.text = if (action.totalTracksIndexed == 0) {
                        "Không tìm thấy tệp âm thanh trong các chat Telegram."
                    } else {
                        "Đã $modeLabel: ${action.totalTracksIndexed} bài từ $actualSources nguồn Telegram."
                    }
                }
            }
            is TelegramSyncAction.Paused -> {
                globalScanLoading = false
                runOnUiThread {
                    globalSearch.isEnabled = true
                    status.text = "Đồng bộ tạm dừng · ${action.state.totalItemsSynced} bài đã lưu"
                }
            }
            is TelegramSyncAction.Failed -> {
                globalScanLoading = false
                runOnUiThread {
                    globalSearch.isEnabled = true
                    status.text = "Đồng bộ gặp lỗi (${action.errorMessage}) · Bấm để tiếp tục"
                }
            }
            is TelegramSyncAction.ProgressUpdate -> {}
            TelegramSyncAction.DiscardedStale -> {}
        }
    }

    private fun processSyncPage(response: JSONObject, tag: String) {
        executeOnDb {
            val parts = tag.split(":")
            val tagUserId = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            val gen = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            val phase = parts.getOrNull(3) ?: ""
            val currentUser = library.authorizedUser()?.value ?: 0L

            if (!auth.isReady() || currentUser <= 0L || tagUserId != currentUser) {
                return@executeOnDb
            }
            val coordinator = syncCoordinator ?: return@executeOnDb
            if (coordinator.getActiveAccountId() != currentUser || coordinator.getActiveGeneration() != gen) {
                return@executeOnDb
            }

            val items = response.optJSONArray("messages") ?: org.json.JSONArray()
            val indexedSongs = mutableListOf<IndexedTelegramSong>()
            val now = System.currentTimeMillis()
            val mType = if (phase == TelegramSyncState.PHASE_AUDIO) "audio" else "document"

            for (i in 0 until items.length()) {
                val msg = items.optJSONObject(i) ?: continue
                val song = parseTelegramSong(msg, 0L) ?: continue
                val rawMessageId = msg.optLong("id")
                val rawDate = msg.optLong("date")
                indexedSongs.add(
                    IndexedTelegramSong(
                        accountId = currentUser,
                        chatId = song.chatId,
                        messageId = rawMessageId,
                        songKey = song.messageId,
                        fileId = song.fileId,
                        title = song.title,
                        filename = song.filename,
                        mediaType = mType,
                        date = rawDate,
                        lastSeenAtMs = now,
                        sourceTitle = sourceNames[song.chatId]
                    )
                )
            }
            val nextOffset = response.optString("next_offset")
            val nextAction = coordinator.onPageReceived(currentUser, gen, phase, indexedSongs, nextOffset)
            if (nextAction is TelegramSyncAction.DiscardedStale) {
                return@executeOnDb
            }
            val syncState = libraryDb?.getSyncState(currentUser)
            val totalSynced = syncState?.totalItemsSynced ?: indexedSongs.size

            runOnUiThread {
                if (!allChatsMode || !auth.isReady() || library.authorizedUser()?.value != currentUser || isDestroyed) return@runOnUiThread
                val phaseLabel = if (phase == "audio") "Âm thanh" else "Tệp âm thanh"
                status.text = "Đang đồng bộ ($phaseLabel): đã tìm $totalSynced bài…"
            }
            handleSyncAction(nextAction, currentUser)
        }
    }

    private fun processSyncError(result: JSONObject, tag: String) {
        executeOnDb {
            val parts = tag.split(":")
            val tagUserId = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            val gen = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            val currentUser = library.authorizedUser()?.value ?: 0L
            if (!auth.isReady() || currentUser <= 0L || tagUserId != currentUser) return@executeOnDb
            val coordinator = syncCoordinator ?: return@executeOnDb
            if (coordinator.getActiveAccountId() != currentUser || coordinator.getActiveGeneration() != gen) return@executeOnDb
            val code = result.optInt("code")
            val msg = result.optString("message")
            val nextAction = coordinator.onError(currentUser, gen, code, msg)
            handleSyncAction(nextAction, currentUser)
        }
    }

    private fun requestGlobalPage() {
        if (!auth.isReady() || !allChatsMode || !globalScanLoading) return
        val filter = if (globalScanPhase == "audio")
            "searchMessagesFilterAudio" else "searchMessagesFilterDocument"
        val tag = "all:${globalScanGeneration}:${globalScanPhase}"
        send("searchMessages", tag) {
            it.put("chat_list", JSONObject.NULL)
            it.put("query", "")
            it.put("offset", globalScanOffset)
            it.put("limit", 100)
            it.put("filter", JSONObject().put("@type", filter))
            it.put("chat_type_filter", JSONObject.NULL)
            it.put("min_date", 0)
            it.put("max_date", 0)
        }
    }

    private fun advanceGlobalPhase() {
        if (!allChatsMode || !auth.isReady()) return
        if (globalScanPhase == "audio") {
            globalScanPhase = "document"
            globalScanOffset = ""
            globalScanPages = 0
            requestGlobalPage()
        } else {
            globalScanLoading = false
            autoRecoveryPolicy.recordScanCompleted(songs.size)
            runOnUiThread {
                globalSearch.isEnabled = true
                status.text = if (songs.isEmpty()) {
                    "Không tìm thấy tệp âm thanh trong các chat Telegram."
                } else {
                    "Tìm thấy ${songs.size} bài từ các chat Telegram."
                }
            }
        }
    }

    private fun acceptGlobalResults(response: JSONObject, tag: String) {
        if (!allChatsMode || !auth.isReady() ||
            tag != "all:${globalScanGeneration}:${globalScanPhase}") return
        val items = response.optJSONArray("messages") ?: org.json.JSONArray()
        val incoming = mutableListOf<Song>()
        for (i in 0 until items.length()) {
            val msg = items.optJSONObject(i) ?: continue
            val song = parseTelegramSong(msg, 0L) ?: continue
            if (seenSongMessageIds.add(song.messageId)) incoming.add(song)
        }
        globalScanPages++
        val newOffset = response.optString("next_offset")
        val hasMore = newOffset.isNotEmpty() && newOffset != globalScanOffset
        runOnUiThread {
            if (!allChatsMode || !auth.isReady()) return@runOnUiThread
            songs.addAll(incoming)
            refreshSongRows()
            status.text = "Đang quét các chat: đã tìm ${songs.size} bài…"
        }
        if (hasMore) {
            globalScanOffset = newOffset
            requestGlobalPage()
        } else {
            advanceGlobalPhase()
        }
    }

    /**
     * Bounded look-ahead: at most one TDLib download for the immediate next
     * discovered Saved Messages track, started only while this authenticated
     * Activity is alive. Media3 may then advance in the background.
     * Never prefetch from arbitrary channels, providers or foreign cache roots.
     */
    private fun prefetchNextCachedNeighbor() {
        if (!auth.isReady() || !running || currentSongIndex < 0 ||
            prefetchWaiting != null || downloadWaiting != null) return
        val currentId = currentTelegramMessageId() ?: return
        val nextId = TelegramPrefetchOrderPolicy.immediateSuccessor(
            queueSongOrder.map { it.messageId }, currentId
        ) ?: return
        val next = queueSongOrder.firstOrNull { it.messageId == nextId } ?: return
        val path = cachedSongPaths[next.messageId]
        if (path != null) {
            val target = File(path)
            if (target.isFile && TelegramAccountStoragePolicy.isOwnedFile(filesRoot, target)) {
                insertCachedNeighborIfRelevant(next, target)
                return
            }
            cachedSongPaths.remove(next.messageId)
        }
        val effectiveChatId = if (next.chatId != 0L) next.chatId else (savedChatId.takeIf { it != 0L } ?: (library.authorizedUser()?.value ?: 0L))
        val rawMsgId = next.genuineMessageId
        if (effectiveChatId == 0L || rawMsgId <= 0L) return

        resolveTelegramMessage(effectiveChatId, rawMsgId) { result ->
            if (!auth.isReady() || !running || isDestroyed) return@resolveTelegramMessage
            if (result is TelegramAudioResolverPolicy.Result.Resolved) {
                val freshId = result.freshFileId
                val resolvedNext = next.copy(fileId = freshId)
                val user = library.authorizedUser()?.value
                if (user != null && user > 0L) {
                    executeOnDb {
                        libraryDb?.updateSongFileId(user, effectiveChatId, rawMsgId, freshId)
                    }
                }
                val localFile = if (result.localPath.isNotBlank()) File(result.localPath) else null
                if (result.isDownloadingCompleted && localFile != null && localFile.isFile &&
                    TelegramAccountStoragePolicy.isOwnedFile(filesRoot, localFile)) {
                    cachedSongPaths[next.messageId] = localFile.canonicalPath
                    insertCachedNeighborIfRelevant(resolvedNext, localFile)
                    prefetchNextCachedNeighbor()
                } else if (prefetchWaiting == null && downloadWaiting == null) {
                    prefetchWaiting = freshId
                    runCatching {
                        send("downloadFile", "prefetch:$freshId") {
                            it.put("file_id", freshId)
                            it.put("priority", 4)
                            it.put("offset", 0)
                            it.put("limit", 0)
                            it.put("synchronous", false)
                        }
                    }.onFailure { prefetchWaiting = null }
                }
            }
        }
    }

    private fun observePrefetch(file: JSONObject) {
        if (!auth.isReady() || file.optInt("id") != prefetchWaiting) return
        val local = file.optJSONObject("local") ?: return
        if (!local.optBoolean("is_downloading_completed")) return
        prefetchWaiting = null
        val path = local.optString("path")
        val target = File(path)
        if (!target.isFile || !TelegramAccountStoragePolicy.isOwnedFile(filesRoot, target)) return
        val song = songs.firstOrNull { it.fileId == file.optInt("id") } ?: return
        cachedSongPaths[song.messageId] = target.canonicalPath
        runOnUiThread {
            if (!auth.isReady()) return@runOnUiThread
            insertCachedNeighborIfRelevant(song, target)
            prefetchNextCachedNeighbor()
        }
    }

    private fun insertCachedNeighborIfRelevant(song: Song, target: File) {
        val controller = player ?: return
        val currentId = controller.currentMediaItem?.mediaId.orEmpty()
        if (!currentId.startsWith("telegram:")) return
        val currentIdLong = currentId.removePrefix("telegram:").toLongOrNull() ?: return
        if (!TelegramPrefetchOrderPolicy.isStillNext(
                queueSongOrder.map { it.messageId }, currentIdLong, song.messageId
            )) return
        val mediaId = "telegram:${song.messageId}"
        for (index in 0 until controller.mediaItemCount) {
            if (controller.getMediaItemAt(index).mediaId == mediaId) return
        }
        if (target.isFile && TelegramAccountStoragePolicy.isOwnedFile(filesRoot, target)) {
            controller.addMediaItem(
                (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount),
                mediaItem(Uri.fromFile(target), song.title, mediaId)
            )
        }
    }

    private fun downloadAndPlay(song: Song) {
        if (!auth.isReady()) return
        val effectiveChatId = if (song.chatId != 0L) song.chatId else (savedChatId.takeIf { it != 0L } ?: (library.authorizedUser()?.value ?: 0L))
        val rawMsgId = song.genuineMessageId
        if (effectiveChatId == 0L || rawMsgId <= 0L) {
            showMessage("Không xác định được nguồn Telegram của bài hát.")
            return
        }

        val previous = downloadWaiting
        if (previous != null) {
            TelegramProgressiveCache.stop(previous)
            runCatching {
                send("cancelDownloadFile", "switch:$previous") {
                    it.put("file_id", previous)
                    it.put("only_if_pending", false)
                }
            }
            downloadWaiting = null
            currentDownloadingSong = null
        }

        currentSongIndex = songs.indexOfFirst { it.messageId == song.messageId }
        nowPlayingTitle.text = song.title
        showMessage("Đang kiểm tra tệp Telegram: ${song.title}…")

        resolveTelegramMessage(effectiveChatId, rawMsgId) { result ->
            if (!auth.isReady() || isDestroyed) return@resolveTelegramMessage
            when (result) {
                is TelegramAudioResolverPolicy.Result.Error -> {
                    downloadWaiting = null
                    currentDownloadingSong = null
                    showMessage("Không thể phát bài: ${result.message}")
                }
                is TelegramAudioResolverPolicy.Result.Resolved -> {
                    val freshId = result.freshFileId
                    val resolvedSong = song.copy(
                        fileId = freshId,
                        expectedSize = if (result.size > 0L) result.size else song.expectedSize
                    )

                    val songIdx = songs.indexOfFirst { it.messageId == song.messageId }
                    if (songIdx >= 0) songs[songIdx] = resolvedSong
                    val qIdx = queueSongOrder.indexOfFirst { it.messageId == song.messageId }
                    if (qIdx >= 0) {
                        val newQ = queueSongOrder.toMutableList()
                        newQ[qIdx] = resolvedSong
                        queueSongOrder = newQ
                    }

                    val user = library.authorizedUser()?.value
                    if (user != null && user > 0L) {
                        executeOnDb {
                            libraryDb?.updateSongFileId(user, effectiveChatId, rawMsgId, freshId)
                        }
                    }

                    val localFile = if (result.localPath.isNotBlank()) File(result.localPath) else null
                    if (result.isDownloadingCompleted && localFile != null && localFile.isFile &&
                        TelegramAccountStoragePolicy.isOwnedFile(filesRoot, localFile)) {
                        downloadWaiting = null
                        currentDownloadingSong = null
                        cachedSongPaths[song.messageId] = localFile.canonicalPath
                        nowPlayingTitle.text = song.title
                        enqueueTelegramPlayback(resolvedSong, localFile)
                        showMessage("Đang phát: ${song.title}")
                    } else {
                        val account = library.authorizedUser()?.value
                        if (account == null || account <= 0L) {
                            showMessage("Phiên Telegram chưa sẵn sàng.")
                            return@resolveTelegramMessage
                        }
                        if (!TelegramProgressiveCache.begin(
                                account, filesRoot, freshId, resolvedSong.expectedSize
                            )) {
                            showMessage("Không tạo được bộ đệm nhạc của tài khoản Telegram.")
                            return@resolveTelegramMessage
                        }
                        downloadWaiting = freshId
                        currentDownloadingSong = resolvedSong
                        nowPlayingTitle.text = song.title
                        showMessage("Đang đệm nhạc qua Telegram: ${song.title}")
                        enqueueProgressivePlayback(resolvedSong)
                        try {
                            send("downloadFile", "download:$freshId") {
                                it.put("file_id", freshId)
                                it.put("priority", 32)
                                it.put("offset", 0)
                                it.put("limit", 0)
                                it.put("synchronous", false)
                            }
                        } catch (_: Exception) {
                            TelegramProgressiveCache.stop(freshId)
                            downloadWaiting = null
                            currentDownloadingSong = null
                            showMessage("Không thể tải bài này.")
                        }
                    }
                }
            }
        }
    }

    private fun playIfDownloaded(file: JSONObject) {
        if (!auth.isReady() || file.optInt("id") != downloadWaiting) return
        val local = file.optJSONObject("local") ?: return
        if (!local.optBoolean("is_downloading_completed")) return
        val path = local.optString("path")
        val song = currentDownloadingSong ?: songs.firstOrNull { it.fileId == downloadWaiting } ?: return
        val target = File(path)
        // Treat TDLib file path as untrusted until canonical containment passes.
        if (!TelegramAccountStoragePolicy.isOwnedFile(filesRoot, target) ||
            !target.isFile) {
            showMessage("Từ chối file ngoài vùng riêng của phiên Telegram.")
            return
        }
        downloadWaiting = null
        currentDownloadingSong = null
        cachedSongPaths[song.messageId] = target.canonicalPath
        runOnUiThread {
            // Never restart at 0: the same Media3 item is already reading
            // the growing TDLib file and may be several minutes in.
            val sameStream = player?.currentMediaItem?.mediaId ==
                "telegram:${song.messageId}"
            if (!sameStream) enqueueTelegramPlayback(song, target)
            status.text = "Nhạc đã tải xong: ${song.title}"
        }
    }

    private fun logoutUser() {
        if (!auth.isReady()) return
        autoRecoveryPolicy.reset()
        setNavigationCollapsed(false)
        clearPendingQrLink()
        auth.logout()
        globalScanGeneration++
        globalScanLoading = false
        ShareableTdlibOwner.revokeAuthorization()
        TelegramProgressiveCache.clear()
        clearPlaybackSession()
        songs.clear()
        adapter.clear()
        queueSongOrder = emptyList()
        favoriteIds = emptyList()
        playlistMode = false
        shownSongs = emptyList()
        updatePlaylistButtons()
        downloadWaiting = null
        currentSongIndex = -1
        nowPlayingTitle.text = "Chọn một bài hát để bắt đầu"
        refresh.isEnabled = false
        globalSearch.isEnabled = false
        sourcesButton.isEnabled = false
        sourceNames.clear()
        requestedSourceNames.clear()
        openDocument.isEnabled = false
        logout.isEnabled = false
        submit.isEnabled = false
        showMessage("Đang đăng xuất; đợi Telegram đóng phiên.")
        syncCoordinator?.cancelSync()
        if (libraryDb is AndroidTelegramLibraryDatabase) {
            runCatching { (libraryDb as AndroidTelegramLibraryDatabase).close() }
        }
        libraryDb = null
        syncCoordinator = null
        runCatching { send("logOut", "logout") }
    }

    override fun onDestroy() {
        if (::mainRoot.isInitialized) mainRoot.removeCallbacks(miniPlayerProgressTick)
        fullPlayer?.dismiss()
        fullPlayer = null
        queueDialog?.dismiss()
        queueDialog = null
        clearPendingQrLink()
        syncCoordinator?.cancelSync()
        dbExecutor.shutdownNow()
        thumbnailExecutor.shutdownNow()
        thumbnailCache.evictAll()
        if (libraryDb is AndroidTelegramLibraryDatabase) {
            runCatching { (libraryDb as AndroidTelegramLibraryDatabase).close() }
        }
        libraryDb = null
        syncCoordinator = null
        running = false
        nativeReceiver?.let { ShareableTdlibOwner.detachUi(it) }
        nativeReceiver = null
        // Service may now resolve a missing queued neighbor with TDLib.
        ShareablePlaybackService.onPreviewDetached()
        controllerReleased = true
        player?.removeListener(playbackListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        player = null
        super.onDestroy()
    }
}
