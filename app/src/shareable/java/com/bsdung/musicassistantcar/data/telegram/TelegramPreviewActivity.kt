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
import android.graphics.Rect
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
import androidx.core.view.WindowInsetsControllerCompat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.view.ViewGroup
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
        val subtitleView: TextView,
        val trailingContainer: LinearLayout,
        val statusView: TextView,
        val durationView: TextView,
        val moreButton: Button
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
    private lateinit var loginPanel: TelegramLoginPanel
    // Sign-in display state only: a masked phone and Telegram's own password hint, never an OTP or password.
    private var loginEditingPhone = false
    private var loginMaskedPhone: String? = null
    private var loginPasswordHint: String? = null
    private var loginResendUntilMs = 0L
    private var loginResendAllowed = true
    private lateinit var nowPlayingTitle: TextView
    private lateinit var playPause: Button
    private var fullPlayer: NowPlayingFullScreenDialog? = null
    private var studioDeck: MeloStudioDeck? = null
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
    private lateinit var cockpitSecondaryRow: LinearLayout
    private lateinit var cockpitOverflowButton: Button
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
    /** Same rows as [visibleSourceIds] with their counts, for the Gọn layout's Telegram page. */
    private var visibleSourceCounts: List<Pair<Long, Int>> = emptyList()
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
    private val offlineExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val pendingOffline = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<JSONObject>>()
    private var offlineGroups = emptyList<TelegramOfflineLibrary.Group>()
    private var offlineSummary: String? = null
    private var offlineVisibleLimit = 200
    @Volatile private var totalCountForCurrentView: Int = 0
    private var isLoadingMoreSongs = false
    // A–Z bar: sort choice (new layers only) and the active lower bound of the search key.
    private var librarySortCache: SongSort? = null
    @Volatile private var letterFloor: String? = null
    @Volatile private var floorCountForCurrentView: Int = 0

    private fun librarySort(): SongSort = librarySortCache ?: MeloAlphabetPolicy.parseSort(
        getSharedPreferences(MeloScreenPolicy.PREFS_NAME, Context.MODE_PRIVATE).getString(MeloAlphabetPolicy.PREF_SORT, null)
    ).also { librarySortCache = it }

    /** Only the Điện thoại / Tablet / Hai khung layers sort by name; every other screen keeps Mới nhất. */
    private fun activeSort(): SongSort =
        if (phoneScreen != null || tabletScreen != null) librarySort() else SongSort.NEWEST

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
    // Gọn layout (docs/ui-spec.md step 4): a view layer over the unchanged legacy tree.
    private lateinit var outerRoot: FrameLayout
    private var compactScreen: MeloCompactScreen? = null
    private var carScreen: MeloCarScreen? = null
    private var phoneScreen: MeloPhoneScreen? = null
    private var tabletScreen: MeloTabletScreen? = null
    private var meloLayer: View? = null
    private var meloLayerKey: String? = null
    private var carPromptShown = false
    private var compactRefreshPosted = false
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

    // Android 13+: the playback notification (lock screen, watch and headset controls) needs this
    // permission. Asked once after sign-in, with an explanation; a refusal is final.
    private val notificationPermissionResult = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, nothing else depends on it */ }

    private fun maybeAskNotificationPermission() {
        val prefs = getSharedPreferences(MeloScreenPolicy.PREFS_NAME, Context.MODE_PRIVATE)
        val granted = Build.VERSION.SDK_INT < MeloMediaControlPolicy.NOTIFICATION_PERMISSION_SDK ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!MeloMediaControlPolicy.shouldAskNotificationPermission(
                Build.VERSION.SDK_INT, granted, MeloMediaControlPolicy.notificationAsked(prefs), auth.isReady())) return
        MeloMediaControlPolicy.markNotificationAsked(prefs)
        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Cho phép thông báo phát nhạc?")
            .setMessage("Thông báo giúp điều khiển nhạc từ màn hình khoá, đồng hồ và tai nghe Bluetooth. " +
                "Bạn có thể đổi lại trong Cài đặt Android.")
            .setNegativeButton("Không", null)
            .setPositiveButton("Cho phép") { _, _ ->
                notificationPermissionResult.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            .show()
    }

    // Car Mode voice search: the spoken text only fills the search field; nothing is stored.
    private var voiceSearchCallback: ((String) -> Unit)? = null
    private val voiceSearchResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = voiceSearchCallback
        voiceSearchCallback = null
        val text = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) callback?.invoke(text)
    }

    private fun voiceSearchIntent(): Intent =
        Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Nói tên bài hát")

    private fun voiceSearchAvailable(): Boolean = voiceSearchIntent().resolveActivity(packageManager) != null

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
        val fullWasShowing = fullPlayer?.isShowing() == true || studioDeck?.isShowing() == true
        val queueWasShowing = queueDialog?.isShowing == true
        fullPlayer?.dismiss()
        fullPlayer = null
        studioDeck?.dismiss()
        studioDeck = null
        queueDialog?.dismiss()
        queueDialog = null
        applyResponsiveLibraryUi()
        if (::tracks.isInitialized) {
            tracks.post { if (!isDestroyed) tracks.setSelectionFromTop(firstVisible, firstOffset) }
        }
        if (fullWasShowing) showFullPlayer()
        if (queueWasShowing) showUpcomingQueue()
        applyMeloLayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("key_all_chats_mode", allChatsMode)
        outState.putBoolean("key_browse_sources", browseSources)
        outState.putBoolean("key_playlist_mode", playlistMode)
        outState.putBoolean("key_downloaded_only", filterOnlyDownloaded)
        outState.putString("key_source_display_mode", currentSourceDisplayMode.key)
        outState.putBoolean("key_zero_tracks_expanded", isZeroTracksSectionExpanded)
        outState.putBoolean("key_hide_zero_sources", hideZeroSources)
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
        currentSourceDisplayMode = TelegramVaultBentoPolicy.getSavedSourceDisplayMode(prefs)
        hideZeroSources = TelegramVaultBentoPolicy.getSavedHideZeroSources(prefs)
        buildUi()
        if (savedInstanceState != null) {
            allChatsMode = savedInstanceState.getBoolean("key_all_chats_mode", allChatsMode)
            browseSources = savedInstanceState.getBoolean("key_browse_sources", browseSources)
            playlistMode = savedInstanceState.getBoolean("key_playlist_mode", playlistMode)
            filterOnlyDownloaded = savedInstanceState.getBoolean("key_downloaded_only", false)
            savedInstanceState.getString("key_source_display_mode")?.let {
                currentSourceDisplayMode = TelegramVaultBentoPolicy.SourceDisplayMode.fromKey(it)
            }
            isZeroTracksSectionExpanded = savedInstanceState.getBoolean("key_zero_tracks_expanded", false)
            hideZeroSources = savedInstanceState.getBoolean("key_hide_zero_sources", hideZeroSources)
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
        // Picks up a changed "Bố cục Gọn" setting when returning from Cài đặt giao diện.
        applyMeloLayer()
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
            notifyMeloPlayback()
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
            notifyMeloPlayback()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                playAdjacent(1)
            }
            updateMiniPlayerProgress()
            updateCockpitPlayer()
            notifyMeloPlayback()
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

    private fun mediaItem(uri: Uri, title: String, mediaId: String, artist: String? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title)
                .setArtist(MeloMediaControlPolicy.fallbackArtist(artist)).build())
            .build()

    private fun enqueuePlayback(uri: Uri, title: String) {
        enqueueItems(listOf(mediaItem(uri, title, "picked:audio")), 0)
    }

    private fun enqueueTelegramPlayback(song: Song, target: File) {
        cachedSongPaths[song.messageId] = target.canonicalPath
        // Playback queue follows only the selected source (and visible title
        // filter), never silently jumps into a different Telegram channel.
        queueSongOrder = TelegramQueueRetentionPolicy.forSelected(
            if (filterOnlyDownloaded) offlineGroups.flatMap { it.entries }.map { offlineSong(it.song) } else shownSongs,
            queueSongOrder, song.messageId
        ) { it.messageId }.ifEmpty { listOf(song) }
        library.authorizedUser()?.let { owner ->
            ShareablePlaybackService.configureTelegramQueue(
                owner.value, filesRoot, queueSongOrder.map { entry ->
                    ShareablePlaybackService.QueueTrack(
                        entry.messageId, entry.fileId, entry.title,
                        cachedSongPaths[entry.messageId], entry.filename,
                        entry.chatId, entry.expectedSize, entry.genuineMessageId,
                        MeloMediaControlPolicy.fallbackArtist(entry.sourceTitle ?: sourceNames[entry.chatId])
                    )
                }
            )
        }
        // Only contiguous cached neighbors may enter Media3's transport queue:
        // skipping a missing file to jump to a distant cached song is incorrect.
        val selected = queueSongOrder.indexOfFirst { it.messageId == song.messageId }
        if (selected < 0) {
            enqueueItems(listOf(mediaItem(Uri.fromFile(target), song.title,
                "telegram:${song.messageId}", song.sourceTitle ?: sourceNames[song.chatId])), 0)
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
            return mediaItem(Uri.fromFile(file), item.title, "telegram:${item.messageId}", item.sourceTitle ?: sourceNames[item.chatId])
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
                "telegram:${song.messageId}", song.sourceTitle ?: sourceNames[song.chatId])
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
                        entry.chatId, entry.expectedSize, entry.genuineMessageId,
                        MeloMediaControlPolicy.fallbackArtist(entry.sourceTitle ?: sourceNames[entry.chatId])
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
            song.title, "telegram:${song.messageId}", song.sourceTitle ?: sourceNames[song.chatId]
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
            notifyMeloPlayback()
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

    private var isLandscapeImeCollapsed = false
    private var currentSourceDisplayMode: TelegramVaultBentoPolicy.SourceDisplayMode = TelegramVaultBentoPolicy.SourceDisplayMode.LIST
    private var isZeroTracksSectionExpanded = false
    private var hideZeroSources = false
    private var currentAuthoritativeSourceCount: Int = 0
    private lateinit var sourcesHeaderRow: LinearLayout
    private lateinit var sourceViewModeToggle: TextView
    private lateinit var hideZeroSourcesToggle: TextView
    private lateinit var bentoScrollView: ScrollView
    private lateinit var bentoContainer: LinearLayout

    private fun updateLandscapeImeLayout(imeVisible: Boolean, force: Boolean = false) {
        val shouldCollapse = CockpitLayoutPolicy.shouldCollapseHeaderForLandscapeIme(
            isWideOrTwoPane = isCockpitTwoPaneActive || isWideLayout,
            isImeVisible = imeVisible
        )
        if (!force && shouldCollapse == isLandscapeImeCollapsed) return
        isLandscapeImeCollapsed = shouldCollapse

        if (shouldCollapse) {
            if (::topBar.isInitialized) topBar.visibility = View.GONE
            if (::navigationRow.isInitialized) navigationRow.visibility = View.GONE
            if (::filterChipsRow.isInitialized) filterChipsRow.visibility = View.GONE
            if (::status.isInitialized) status.visibility = View.GONE
            if (::cockpitArtFrame.isInitialized) cockpitArtFrame.visibility = View.GONE
            if (::sourcesHeaderRow.isInitialized) sourcesHeaderRow.visibility = View.GONE
        } else {
            if (::topBar.isInitialized) topBar.visibility = View.VISIBLE
            if (::sourcesHeaderRow.isInitialized) sourcesHeaderRow.visibility = View.VISIBLE
            if (::navigationRow.isInitialized) {
                val shouldShowNav = if (isCockpitTwoPaneActive) {
                    auth.isReady()
                } else {
                    val isEdgeProPortrait = ShortWindowLayoutPolicy.shouldUseBottomNav(currentThemePreset, currentPaneMode)
                    !isEdgeProPortrait && auth.isReady() && !navigationCollapsed
                }
                navigationRow.visibility = if (shouldShowNav) View.VISIBLE else View.GONE
            }
            if (::filterChipsRow.isInitialized) {
                filterChipsRow.visibility = if (auth.isReady() && (!browseSources || playlistMode)) View.VISIBLE else View.GONE
            }
            if (::status.isInitialized) {
                status.visibility = if (isShortLayout && !globalScanLoading) View.GONE else View.VISIBLE
            }
            if (::cockpitArtFrame.isInitialized) {
                cockpitArtFrame.visibility = if (isCockpitTwoPaneActive) View.VISIBLE else View.GONE
            }
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

    private fun syncSubtitleText(): String? =
        if (::syncSubtitle.isInitialized) syncSubtitle.text?.toString()?.takeIf { it.isNotBlank() } else null

    private fun updateSyncHeaderSubtitle() {
        if (!::syncSubtitle.isInitialized) return
        if (filterOnlyDownloaded && offlineSummary != null && !(allChatsMode && browseSources && !playlistMode)) {
            syncSubtitle.text = offlineSummary
            return
        }
        val isSyncing = globalScanLoading || syncCoordinator?.isBusy() == true
        val isAuth = auth.isReady()
        val isSources = (allChatsMode && browseSources && !playlistMode)
        val sourceCount = if (currentAuthoritativeSourceCount > 0) {
            currentAuthoritativeSourceCount
        } else {
            val fallback = ShortWindowLayoutPolicy.truthfulSourceCount(visibleSourceIds)
            if (fallback > 0) fallback else songs.map { it.chatId }.distinct().size
        }
        val text = TelegramLibrarySyncDisplayPolicy.formatSyncSubtitle(
            displayedCount = shownSongs.size,
            totalIndexedCount = totalCountForCurrentView,
            isSyncing = isSyncing,
            isAuthenticated = isAuth,
            isSourcesMode = isSources,
            sourceCount = sourceCount,
            selectedSourceTitle = selectedChatId?.let { sourceNames[it] }
        )
        syncSubtitle.text = text
        scheduleMeloLibraryRefresh()
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

    private fun setSourceDisplayMode(mode: TelegramVaultBentoPolicy.SourceDisplayMode) {
        if (currentSourceDisplayMode == mode) return
        currentSourceDisplayMode = mode
        val prefs = getSharedPreferences(UiThemePolicy.PREFS_NAME, Context.MODE_PRIVATE)
        TelegramVaultBentoPolicy.saveSourceDisplayMode(prefs, mode)
        updateSourceViewModeToggleUi()
        refreshSongRows()
    }

    private fun updateSourceViewModeToggleUi() {
        if (!::sourceViewModeToggle.isInitialized) return
        val isBento = currentSourceDisplayMode == TelegramVaultBentoPolicy.SourceDisplayMode.BENTO
        val label = if (isBento) "Bento ⊞" else "Danh sách ☰"
        sourceViewModeToggle.text = label
        sourceViewModeToggle.background = rounded(if (isBento) cardPlaying else panel, 8, strokeColor = if (isBento) accent else border)
        sourceViewModeToggle.setTextColor(if (isBento) accent else subtle)
    }

    private fun updateHideZeroSourcesToggleUi() {
        if (!::hideZeroSourcesToggle.isInitialized) return
        val isHidden = hideZeroSources
        val label = if (isHidden) "⊘ Ẩn 0 bài" else "👁 Hiện 0 bài"
        hideZeroSourcesToggle.text = label
        hideZeroSourcesToggle.background = rounded(if (isHidden) cardPlaying else panel, 8, strokeColor = if (isHidden) accent else border)
        hideZeroSourcesToggle.setTextColor(if (isHidden) accent else subtle)
        hideZeroSourcesToggle.contentDescription = if (isHidden) "Đang ẩn các kênh không có bài. Bấm để hiện lại." else "Đang hiện tất cả các kênh. Bấm để ẩn kênh 0 bài."
    }

    private fun showSourceDisplayModeDialog() {
        val modes = TelegramVaultBentoPolicy.SourceDisplayMode.entries.toTypedArray()
        val names = modes.map { mode ->
            when (mode) {
                TelegramVaultBentoPolicy.SourceDisplayMode.LIST -> "Danh sách truyền thống (List)"
                TelegramVaultBentoPolicy.SourceDisplayMode.BENTO -> "Bento Grid (Lưới thẻ hiện đại)"
            }
        }.toTypedArray()
        val currentIndex = modes.indexOf(currentSourceDisplayMode).coerceAtLeast(0)

        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Chế độ hiển thị nguồn Telegram")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val chosen = modes[which]
                dialog.dismiss()
                if (chosen != currentSourceDisplayMode) {
                    setSourceDisplayMode(chosen)
                    showMessage("Đã đổi chế độ hiển thị: ${chosen.displayName}")
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun renderBentoGrid(
        sources: List<IndexedTelegramSource>,
        totalIndexedCount: Int,
        query: String
    ) {
        if (!::bentoContainer.isInitialized) return
        bentoContainer.removeAllViews()

        // 1. Hero Card (Pinned aggregate "KHO TỔNG HỢP / TẤT CẢ NGUỒN TELEGRAM")
        if (TelegramVaultBentoPolicy.shouldShowHeroCard(query)) {
            val heroCard = buildHeroCard(totalIndexedCount)
            bentoContainer.addView(heroCard)
        }

        // 2. Partition active (>0 tracks) vs empty/unindexed (<=0 tracks)
        val (activeSources, emptySources) = TelegramVaultBentoPolicy.partitionSources(sources)
        val sortedActive = TelegramVaultBentoPolicy.sortSources(activeSources)

        // Calculate columns based on measured or usable width
        val density = resources.displayMetrics.density
        val measuredWidthDp = if (::bentoScrollView.isInitialized && bentoScrollView.width > 0) {
            (bentoScrollView.width / density).toInt()
        } else 0
        val bounds = if (android.os.Build.VERSION.SDK_INT >= 30) {
            windowManager.currentWindowMetrics.bounds
        } else null
        val screenWidthDp = ((bounds?.width() ?: resources.displayMetrics.widthPixels) / density).toInt()
        val usableWidthDp = if (isCockpitTwoPaneActive) {
            (screenWidthDp * CockpitLayoutPolicy.RIGHT_PANE_WEIGHT).toInt()
        } else {
            screenWidthDp
        }
        val effectiveWidthDp = if (measuredWidthDp > 0) measuredWidthDp else usableWidthDp
        val columns = TelegramVaultBentoPolicy.calculateGridColumns(effectiveWidthDp, isCockpitTwoPaneActive)

        // 3. Render active source cards grid
        if (sortedActive.isNotEmpty()) {
            addCardsGridToContainer(sortedActive, columns)
        } else if (emptySources.isEmpty() && query.isNotBlank()) {
            val emptyMsg = TextView(this).apply {
                text = "Không tìm thấy nguồn nhạc nào phù hợp với \"$query\""
                textSize = 13.5f
                setTextColor(subtle)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(36), dp(16), dp(36))
            }
            bentoContainer.addView(emptyMsg)
        }

        // 4. Render collapsible zero-tracks section (only when genuine zero-track sources exist)
        if (emptySources.isNotEmpty()) {
            if (hideZeroSources) {
                val hiddenNotice = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = rounded(panel, 10, strokeColor = border)
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        hideZeroSources = false
                        val prefs = getSharedPreferences(UiThemePolicy.PREFS_NAME, Context.MODE_PRIVATE)
                        TelegramVaultBentoPolicy.saveHideZeroSources(prefs, false)
                        updateHideZeroSourcesToggleUi()
                        refreshSongRows()
                    }
                }
                val noticeTitle = TextView(this).apply {
                    text = "Đang ẩn ${emptySources.size} kênh chưa có bài"
                    textSize = 12f
                    setTextColor(subtle)
                }
                hiddenNotice.addView(noticeTitle, LinearLayout.LayoutParams(0, -2, 1f))
                val revealBtn = TextView(this).apply {
                    text = "Hiện lại ›"
                    textSize = 11.5f
                    setTextColor(accent)
                    setTypeface(null, Typeface.BOLD)
                }
                hiddenNotice.addView(revealBtn, LinearLayout.LayoutParams(-2, -2))
                bentoContainer.addView(hiddenNotice, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(16)
                    bottomMargin = dp(4)
                })
            } else {
                val accordionHeader = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = rounded(panel, 10, strokeColor = border)
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        isZeroTracksSectionExpanded = !isZeroTracksSectionExpanded
                        renderBentoGrid(sources, totalIndexedCount, query)
                    }
                }

                val accordionTitle = TextView(this).apply {
                    text = "Chưa có bài được lập chỉ mục (${emptySources.size} nguồn)"
                    textSize = 12.5f
                    setTextColor(subtle)
                    setTypeface(null, Typeface.BOLD)
                }
                accordionHeader.addView(accordionTitle, LinearLayout.LayoutParams(0, -2, 1f))

                val accordionIndicator = TextView(this).apply {
                    text = if (isZeroTracksSectionExpanded) "▼ Thu gọn" else "▶ Mở rộng"
                    textSize = 11.5f
                    setTextColor(accent)
                    setTypeface(null, Typeface.BOLD)
                }
                accordionHeader.addView(accordionIndicator, LinearLayout.LayoutParams(-2, -2))

                bentoContainer.addView(accordionHeader, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(16)
                    bottomMargin = dp(4)
                })

                if (isZeroTracksSectionExpanded) {
                    val sortedEmpty = TelegramVaultBentoPolicy.sortSources(emptySources)
                    addCardsGridToContainer(sortedEmpty, columns)
                }
            }
        }
    }

    private fun addCardsGridToContainer(cards: List<IndexedTelegramSource>, columns: Int) {
        val chunks = cards.chunked(columns)
        for (chunk in chunks) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(6)
                }
            }
            for (source in chunk) {
                val cardView = buildBentoSourceCard(source, isSelected = (source.chatId == selectedChatId))
                row.addView(cardView, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    leftMargin = dp(3)
                    rightMargin = dp(3)
                })
            }
            // Fill remaining slots in last row with dummy views to preserve equal column sizing
            if (chunk.size < columns) {
                val dummyCount = columns - chunk.size
                for (i in 0 until dummyCount) {
                    val dummy = View(this)
                    row.addView(dummy, LinearLayout.LayoutParams(0, 0, 1f).apply {
                        leftMargin = dp(3)
                        rightMargin = dp(3)
                    })
                }
            }
            bentoContainer.addView(row)
        }
    }

    private fun buildHeroCard(totalIndexedCount: Int): View {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(card, 16, strokeColor = accent)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minimumHeight = dp(88)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedChatId = null
                browseSources = false
                playlistMode = false
                songFilter.text?.clear()
                refreshSongRows()
                updatePlaylistButtons()
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconFrame = FrameLayout(this).apply {
            background = rounded(accent, 20)
            val iconSize = dp(40)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                rightMargin = dp(12)
            }
            val icon = TextView(this@TelegramPreviewActivity).apply {
                text = "♫"
                textSize = 20f
                setTextColor(accentContrast)
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
            }
            addView(icon, FrameLayout.LayoutParams(-1, -1))
        }
        topRow.addView(iconFrame)

        val headerTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tagLabel = TextView(this).apply {
            text = "KHO TỔNG HỢP"
            textSize = 10.5f
            setTextColor(accent)
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val heroTitle = TextView(this).apply {
            text = "Tất cả nguồn Telegram"
            textSize = 16f
            setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
        }
        headerTextCol.addView(tagLabel)
        headerTextCol.addView(heroTitle)
        topRow.addView(headerTextCol, LinearLayout.LayoutParams(0, -2, 1f))

        val countBadge = TextView(this).apply {
            text = TelegramVaultBentoPolicy.formatTrackCount(totalIndexedCount)
            textSize = 12f
            setTextColor(accent)
            setTypeface(null, Typeface.BOLD)
            background = rounded(cardPlaying, 8, strokeColor = accent)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        topRow.addView(countBadge, LinearLayout.LayoutParams(-2, -2))
        hero.addView(topRow)

        val hint = TextView(this).apply {
            text = "Bấm để mở danh sách toàn bộ bài hát trong thư viện MeloVault ›"
            textSize = 11.5f
            setTextColor(subtle)
        }
        hero.addView(hint, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })

        return hero
    }

    private fun buildBentoSourceCard(source: IndexedTelegramSource, isSelected: Boolean): View {
        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(88)
            background = rounded(
                if (isSelected) cardPlaying else card,
                16,
                strokeColor = if (isSelected) accent else border
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedChatId = source.chatId
                browseSources = false
                playlistMode = false
                songFilter.text?.clear()
                refreshSongRows()
                updatePlaylistButtons()
            }
        }

        // Circular 48dp avatar containing first displayable initial
        val avatarFrame = FrameLayout(this).apply {
            val size = dp(48)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = dp(12)
            }
            background = rounded(panel, 24, strokeColor = if (isSelected) accent else border)
            val initialView = TextView(this@TelegramPreviewActivity).apply {
                text = TelegramVaultBentoPolicy.extractInitialGrapheme(source.title)
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(if (source.trackCount > 0) accent else subtle)
                setTypeface(null, Typeface.BOLD)
            }
            addView(initialView, FrameLayout.LayoutParams(-1, -1))
        }
        cardView.addView(avatarFrame)

        // Middle title & track count info
        val middleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(this).apply {
            text = source.title.ifBlank { "Nguồn Telegram" }
            textSize = if (isShortLayout) 15f else 16f
            setTextColor(if (isSelected) accent else ink)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        middleCol.addView(titleView)

        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badgeView = TextView(this).apply {
            text = TelegramVaultBentoPolicy.formatTrackCount(source.trackCount)
            textSize = 11.5f
            if (source.trackCount > 0) {
                setTextColor(accent)
                background = rounded(cardPlaying, 6)
            } else {
                setTextColor(subtle)
                background = rounded(panel, 6)
            }
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(7), dp(2), dp(7), dp(2))
        }
        metaRow.addView(badgeView, LinearLayout.LayoutParams(-2, -2))

        val subtitleView = TextView(this).apply {
            text = TelegramVaultBentoPolicy.formatSourceSubtitle(source.type)
            textSize = 11f
            setTextColor(subtle)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        metaRow.addView(subtitleView, LinearLayout.LayoutParams(-2, -2).apply {
            leftMargin = dp(8)
        })

        middleCol.addView(metaRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4)
        })
        cardView.addView(middleCol, LinearLayout.LayoutParams(0, -2, 1f))

        // Right arrow
        val arrowView = TextView(this).apply {
            text = "›"
            textSize = 18f
            setTextColor(if (isSelected) accent else subtle)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), 0, dp(4), 0)
        }
        cardView.addView(arrowView, LinearLayout.LayoutParams(-2, -2))

        return cardView
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
        updateSourceViewModeToggleUi()
        updateHideZeroSourcesToggleUi()
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
        val currentMsgId = currentTelegramMessageId()
        val currentSong = currentMsgId?.let { id ->
            songs.firstOrNull { it.messageId == id } ?: shownSongs.firstOrNull { it.messageId == id }
        }
        val sourceFileName = currentSong?.filename
            ?: controller.currentMediaItem?.mediaMetadata?.title?.toString()
        for (group in groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSelected(i)) continue
                val format = group.getTrackFormat(i)
                return CockpitLayoutPolicy.formatAudioBadge(
                    sampleMimeType = format.sampleMimeType,
                    sampleRate = format.sampleRate,
                    channelCount = format.channelCount,
                    bitrate = format.bitrate,
                    sourceFileName = sourceFileName
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
        cockpitScrollView.viewTreeObserver.addOnGlobalLayoutListener { fitMeasuredCockpit() }

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
            seekRow.addView(this, LinearLayout.LayoutParams(0, dp(48), 1f))
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
            minHeight = dp(48)
            minimumHeight = dp(48)
            contentDescription = "Bài trước"
            setOnClickListener { playAdjacent(-1) }
            transportRow.addView(this, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                rightMargin = dp(6)
            })
        }

        cockpitPlayPauseButton = Button(this).apply {
            text = "▶"
            textSize = 22f
            background = rounded(accent, 14)
            setTextColor(accentContrast)
            minHeight = dp(52)
            minimumHeight = dp(52)
            contentDescription = "Phát hoặc tạm dừng"
            setOnClickListener {
                player?.let { controller ->
                    if (controller.currentMediaItem != null) {
                        if (controller.isPlaying) controller.pause() else controller.play()
                    }
                }
            }
            transportRow.addView(this, LinearLayout.LayoutParams(dp(52), dp(52)))
        }

        cockpitNextButton = Button(this).apply {
            text = "⏭"
            textSize = 18f
            setTextColor(ink)
            background = rounded(panel, 12)
            minHeight = dp(48)
            minimumHeight = dp(48)
            contentDescription = "Bài kế tiếp"
            setOnClickListener { playAdjacent(1) }
            transportRow.addView(this, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                leftMargin = dp(6)
            })
        }

        val secondaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            cockpitPane.addView(this, LinearLayout.LayoutParams(-1, dp(48)).apply {
                topMargin = dp(2)
            })
        }
        cockpitSecondaryRow = secondaryRow
        cockpitOverflowButton = Button(this).apply {
            text = "⋮"
            contentDescription = "Đảo, lặp, lưu bài và hàng đợi"
            setPadding(0, 0, 0, 0)
            minWidth = dp(48)
            minimumWidth = dp(48)
            visibility = View.GONE
            setOnClickListener {
                val popup = android.widget.PopupMenu(this@TelegramPreviewActivity, this)
                listOf(cockpitShuffleButton, cockpitRepeatButton, cockpitFavoriteButton, cockpitQueueButton)
                    .forEachIndexed { index, button ->
                        popup.menu.add(0, index, index, button.text).isEnabled = button.isEnabled
                    }
                popup.setOnMenuItemClickListener { item ->
                    listOf(cockpitShuffleButton, cockpitRepeatButton, cockpitFavoriteButton, cockpitQueueButton)
                        .get(item.itemId).performClick()
                    true
                }
                popup.show()
            }
            transportRow.addView(this, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(4) })
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
                minHeight = dp(48)
                minimumHeight = dp(48)
                minWidth = dp(48)
                minimumWidth = dp(48)
                setOnClickListener { action() }
                secondaryRow.addView(this, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
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

    private fun fitMeasuredCockpit() {
        if (!isCockpitTwoPaneActive || !::cockpitArtFrame.isInitialized || cockpitScrollView.height <= 0) return
        // splitRoot is already laid out inside root WindowInsets padding. Do not subtract bars again.
        val available = cockpitScrollView.height - cockpitScrollView.paddingTop - cockpitScrollView.paddingBottom
        val compact = available < dp(360)
        val lines = if (compact) 1 else 2
        if (cockpitTitle.maxLines != lines) cockpitTitle.maxLines = lines
        val subtitleVisibility = if (compact) View.GONE else View.VISIBLE
        if (cockpitSubtitle.visibility != subtitleVisibility) cockpitSubtitle.visibility = subtitleVisibility
        var reserved = cockpitPane.paddingTop + cockpitPane.paddingBottom
        for (index in 0 until cockpitPane.childCount) {
            val child = cockpitPane.getChildAt(index)
            if (child === cockpitArtFrame || child === cockpitSecondaryRow || child.visibility == View.GONE) continue
            val lp = child.layoutParams as LinearLayout.LayoutParams
            reserved += child.measuredHeight + lp.topMargin + lp.bottomMargin
        }
        val secondaryParams = cockpitSecondaryRow.layoutParams as LinearLayout.LayoutParams
        val secondaryHeight = dp(48) + secondaryParams.topMargin + secondaryParams.bottomMargin
        val overflow = reserved + secondaryHeight > available
        val secondaryVisibility = if (overflow) View.GONE else View.VISIBLE
        if (cockpitSecondaryRow.visibility != secondaryVisibility) cockpitSecondaryRow.visibility = secondaryVisibility
        val overflowVisibility = if (overflow) View.VISIBLE else View.GONE
        if (cockpitOverflowButton.visibility != overflowVisibility) cockpitOverflowButton.visibility = overflowVisibility
        if (!overflow) reserved += secondaryHeight
        val artParams = cockpitArtFrame.layoutParams as LinearLayout.LayoutParams
        reserved += artParams.topMargin + artParams.bottomMargin
        val size = if (isLandscapeImeCollapsed) 0 else CockpitLayoutPolicy.measuredCoverSize(
            available, cockpitPane.width - cockpitPane.paddingLeft - cockpitPane.paddingRight,
            reserved, dp(360)
        )
        val visibility = if (size > 0) View.VISIBLE else View.GONE
        if (cockpitArtFrame.visibility != visibility) cockpitArtFrame.visibility = visibility
        if (artParams.width != size || artParams.height != size) {
            artParams.width = size
            artParams.height = size
            cockpitArtFrame.layoutParams = artParams
        }
        // Remove obsolete scroll offset after shrinking artwork or resizing the window.
        if (reserved + size <= available && cockpitScrollView.scrollY != 0) cockpitScrollView.scrollTo(0, 0)
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
                filterOnlyDownloaded = true
                updateFilterChipsUi()
                refreshSongRows()
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
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()) || ime.bottom > 0
            lastSystemBarsInsets = WindowInsetsPolicy.Insets(
                systemBars.left, systemBars.top, systemBars.right, systemBars.bottom
            )
            lastImeInsets = WindowInsetsPolicy.Insets(
                ime.left, ime.top, ime.right, ime.bottom
            )
            applyRootWindowInsets()
            updateLandscapeImeLayout(imeVisible)
            insets
        }
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            root.getWindowVisibleDisplayFrame(r)
            val screenHeight = root.rootView.height
            if (screenHeight <= 0) return@addOnGlobalLayoutListener
            val heightDiff = screenHeight - r.bottom
            val isImeOpen = heightDiff > screenHeight * 0.15
            updateLandscapeImeLayout(isImeOpen)
            if (!isImeOpen && ::songFilter.isInitialized && songFilter.hasFocus()) {
                songFilter.clearFocus()
            }
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
        loginPanel = TelegramLoginPanel(
            this,
            TelegramLoginPanel.Colors(ink, subtle, accent, accentContrast, panel, border),
            onSubmit = { submitAuthentication() },
            onResend = { requestResendCode() },
            onEditPhone = { loginEditingPhone = true; renderLoginStep() },
            onBack = { onLoginBack() },
            onQr = { requestQrLogin() }
        )
        authControls = loginPanel.root
        input = loginPanel.input
        submit = loginPanel.submit
        qrLogin = loginPanel.qr.apply { isEnabled = false }
        // The form scrolls when the on-screen keyboard leaves little room (small screens).
        collapsibleNavigation.addView(
            TelegramLoginScrollView(this).apply { addView(authControls) },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        )
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
        sourcesHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            libraryContainer.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(6)
            })
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
            sourcesHeaderRow.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
        }
        sourceViewModeToggle = TextView(this).apply {
            visibility = View.GONE
            textSize = 11.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            contentDescription = "Chuyển chế độ hiển thị nguồn Danh sách hoặc Bento Grid"
            setOnClickListener {
                val nextMode = if (currentSourceDisplayMode == TelegramVaultBentoPolicy.SourceDisplayMode.BENTO) {
                    TelegramVaultBentoPolicy.SourceDisplayMode.LIST
                } else {
                    TelegramVaultBentoPolicy.SourceDisplayMode.BENTO
                }
                setSourceDisplayMode(nextMode)
            }
            sourcesHeaderRow.addView(this, LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(6)
            })
        }
        hideZeroSourcesToggle = TextView(this).apply {
            visibility = View.GONE
            textSize = 11.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            contentDescription = "Bật hoặc tắt ẩn kênh 0 bài"
            setOnClickListener {
                hideZeroSources = !hideZeroSources
                val prefs = getSharedPreferences(UiThemePolicy.PREFS_NAME, Context.MODE_PRIVATE)
                TelegramVaultBentoPolicy.saveHideZeroSources(prefs, hideZeroSources)
                updateHideZeroSourcesToggleUi()
                refreshSongRows()
            }
            sourcesHeaderRow.addView(this, LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(6)
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
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            contentDescription = "Ô tìm kiếm nhóm, kênh hoặc bài hát"
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && (isCockpitTwoPaneActive || isWideLayout)) {
                    updateLandscapeImeLayout(true)
                }
            }
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
                    if (firstVisibleItem + visibleItemCount >= totalItemCount - 20 && totalItemCount > 0) {
                        loadMoreSongsIfNeeded()
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
                            rightMargin = dp(10)
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

                    val trailingContainer = LinearLayout(this@TelegramPreviewActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        rootLayout.addView(this, LinearLayout.LayoutParams(-2, -2).apply {
                            leftMargin = dp(6)
                        })
                    }
                    val statusView = TextView(this@TelegramPreviewActivity).apply {
                        textSize = 12f
                        gravity = Gravity.CENTER
                        trailingContainer.addView(this, LinearLayout.LayoutParams(-2, -2).apply {
                            rightMargin = dp(6)
                        })
                    }
                    val durationView = TextView(this@TelegramPreviewActivity).apply {
                        textSize = 11.5f
                        setTextColor(subtle)
                        gravity = Gravity.CENTER_VERTICAL
                        trailingContainer.addView(this, LinearLayout.LayoutParams(-2, -2).apply {
                            rightMargin = dp(4)
                        })
                    }
                    val moreButton = Button(this@TelegramPreviewActivity).apply {
                        text = "⋮"
                        textSize = 19f
                        setTextColor(subtle)
                        background = rounded(Color.TRANSPARENT, 8)
                        contentDescription = "Tác vụ bài hát"
                        minWidth = dp(48)
                        minimumWidth = dp(48)
                        minHeight = dp(48)
                        minimumHeight = dp(48)
                        setPadding(0, 0, 0, 0)
                        isFocusable = false
                        isClickable = true
                        trailingContainer.addView(this, LinearLayout.LayoutParams(dp(48), dp(48)))
                    }
                    holder = SongRowViewHolder(artFrame, artImage, artPlaceholder, titleView, subtitleView, trailingContainer, statusView, durationView, moreButton)
                    rootLayout.tag = holder
                }

                val song = shownSongs.getOrNull(position)
                val group = offlineGroups.getOrNull(position).takeIf { filterOnlyDownloaded }
                val isCurrent = (song != null && song.messageId == currentTelegramMessageId()) ||
                    group?.entries?.any { it.song.songKey == currentTelegramMessageId() } == true
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

                val isWide = isCockpitTwoPaneActive || isWideLayout
                val performer = song?.performer?.takeIf { it.isNotBlank() }
                val sourceTitle = song?.sourceTitle ?: (song?.chatId?.let { sourceNames[it] } ?: "Telegram")

                holder.subtitleView.text = if (song != null) {
                    if (isWide) {
                        listOfNotNull(performer, sourceTitle).joinToString(" · ")
                    } else {
                        TelegramSongDisplayPolicy.subtitle(
                            source = sourceTitle,
                            isDownloaded = isDownloaded,
                            fileSizeBytes = song.expectedSize,
                            durationSeconds = song.durationSeconds,
                            performer = performer
                        )
                    }
                } else {
                    "Telegram · Chưa tải"
                }
                if (group != null && group.entries.size > 1) holder.subtitleView.text = group.label + " · Chạm để mở"
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

                if (song != null) {
                    val status = CockpitLayoutPolicy.resolveSongStatus(isCurrent, isDownloaded)
                    holder.statusView.text = CockpitLayoutPolicy.statusGlyph(status)
                    holder.statusView.setTextColor(if (status == CockpitLayoutPolicy.SongPlaybackStatus.CLOUD) 0xFF64748B.toInt() else accent)
                    holder.statusView.visibility = View.VISIBLE

                    val durFormatted = if (song.durationSeconds > 0) {
                        CockpitLayoutPolicy.formatDuration(song.durationSeconds.toLong())
                    } else if (isCurrent) {
                        val playerDur = player?.duration ?: 0L
                        if (playerDur > 0L && playerDur != C.TIME_UNSET) {
                            CockpitLayoutPolicy.formatDurationMs(playerDur)
                        } else null
                    } else null

                    if (durFormatted != null && (!isNarrowLayout || isWide)) {
                        holder.durationView.text = durFormatted
                        holder.durationView.visibility = View.VISIBLE
                    } else {
                        holder.durationView.text = ""
                        holder.durationView.visibility = View.GONE
                    }

                    holder.moreButton.visibility = View.VISIBLE
                    holder.moreButton.setOnClickListener { v ->
                        if (group != null && group.entries.size > 1) showOfflineVersions(group, v)
                        else showSongOptionsMenu(song, isDownloaded, v)
                    }
                } else {
                    holder.statusView.visibility = View.GONE
                    holder.durationView.visibility = View.GONE
                    holder.moreButton.visibility = View.GONE
                    holder.moreButton.setOnClickListener(null)
                }

                val cardBg = if (isCurrent) cardPlaying else card
                val cardStroke = if (isCurrent) accent else null
                rootLayout.background = rounded(cardBg, 12, strokeColor = cardStroke, strokeWidthDp = if (isCurrent) 1 else 0)
                val padV = dp(if (isShortLayout) 6 else 10)
                rootLayout.setPadding(dp(12), padV, dp(if (song != null) 4 else 14), padV)
                rootLayout.minimumHeight = dp(if (isShortLayout) 52 else 64)
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
        // The Gọn layout mirrors whatever the legacy list shows; coalesce bursts of clear/addAll.
        val compactObserver = object : android.database.DataSetObserver() {
            override fun onChanged() = scheduleMeloLibraryRefresh()
            override fun onInvalidated() = scheduleMeloLibraryRefresh()
        }
        adapter.registerDataSetObserver(compactObserver)
        sourcesAdapter.registerDataSetObserver(compactObserver)
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
            } else {
                val group = offlineGroups.getOrNull(position).takeIf { filterOnlyDownloaded }
                if (group != null && group.entries.size > 1) showOfflineVersions(group)
                else shownSongs.getOrNull(position)?.let { downloadAndPlay(it) }
            }
        }
        libraryContainer.addView(tracks, LinearLayout.LayoutParams(-1, 0, 1f))
        bentoScrollView = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = true
            clipToPadding = false
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    if (::songFilter.isInitialized && songFilter.hasFocus()) {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(songFilter.windowToken, 0)
                        songFilter.clearFocus()
                    }
                }
                false
            }
            bentoContainer = LinearLayout(this@TelegramPreviewActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(2), dp(2), dp(2), dp(16))
            }
            addView(bentoContainer, FrameLayout.LayoutParams(-1, -2))
            libraryContainer.addView(this, LinearLayout.LayoutParams(-1, 0, 1f))
        }
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
        // Outer frame only so the Gọn layout can sit on top; the legacy tree is unchanged inside it.
        outerRoot = FrameLayout(this).apply {
            addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(outerRoot)
        applyResponsiveLibraryUi()
        applyMeloLayer()
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
        val bentoLabel = "Hiển thị nguồn: ${currentSourceDisplayMode.displayName} ›"
        menu.menu.add(0, 9, 9, bentoLabel)
        menu.menu.add(0, 10, 10, "⚙ Cài đặt giao diện (thử nghiệm)")
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
                9 -> showSourceDisplayModeDialog()
                10 -> startActivity(Intent(this, MeloUiLabActivity::class.java)
                    .putExtra(MeloUiLabActivity.EXTRA_SONG_COUNT, totalCountForCurrentView))
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
                val isShortH = height < 460
                (cockpitArtFrame.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(if (isShortH) 4 else 8)
                cockpitPane.setPadding(dp(12), dp(if (isShortH) 4 else 8), dp(12), dp(if (isShortH) 4 else 8))
                updateCockpitPlayer()
                cockpitScrollView.post { fitMeasuredCockpit() }
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
        if (::logout.isInitialized) {
            logout.visibility =
                if (TelegramLoginStepPolicy.showLogout(auth.isReady())) View.VISIBLE else View.GONE
        }

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
            if (::miniPlayerBar.isInitialized) {
                miniPlayerBar.visibility =
                    if (TelegramLoginStepPolicy.showMiniPlayer(auth.isReady())) View.VISIBLE else View.GONE
            }
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
            songFilter.imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
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
        if (isLandscapeImeCollapsed) {
            updateLandscapeImeLayout(true, force = true)
        }
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
        if (::sourcesAdapter.isInitialized) sourcesAdapter.notifyDataSetChanged()
        if (allChatsMode && browseSources && !playlistMode && currentSourceDisplayMode == TelegramVaultBentoPolicy.SourceDisplayMode.BENTO) {
            refreshSongRows()
        }
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

    /** Progressive-download line for the current track; null when it is not a streamed Telegram file. */
    private fun currentDownloadNote(): String? {
        val uri = player?.currentMediaItem?.localConfiguration?.uri
        val fileId = uri?.let(TelegramProgressiveCache::fileId) ?: return null
        val loaded = TelegramProgressiveCache.readyBytes(fileId)
        val total = TelegramProgressiveCache.totalBytes(fileId)
        val complete = TelegramProgressiveCache.isComplete(fileId)
        return when {
            complete -> "Đã tải đầy đủ · Có thể nghe offline"
            total > 0L -> String.format(java.util.Locale.US,
                "Telegram · Đã nhận %.1f / %.1f MB · %d%%",
                loaded / 1_000_000.0, total / 1_000_000.0,
                (loaded * 100 / total).coerceIn(0, 100))
            else -> String.format(java.util.Locale.US,
                "Telegram · Đang đệm %.1f MB", loaded / 1_000_000.0)
        }
    }

    /** Download percent of the streamed current track, or null when complete / not streaming. */
    private fun currentDownloadPercent(): Int? {
        val uri = player?.currentMediaItem?.localConfiguration?.uri
        val fileId = uri?.let(TelegramProgressiveCache::fileId) ?: return null
        if (TelegramProgressiveCache.isComplete(fileId)) return null
        return MeloCompactPolicy.progressPercent(
            TelegramProgressiveCache.readyBytes(fileId), TelegramProgressiveCache.totalBytes(fileId)
        )
    }

    private fun showFullPlayer() {
        // Studio Deck is the default Now Playing; "Màn cũ" in Cài đặt giao diện keeps the old dialog.
        val style = MeloNowPlayingPolicy.loadStyle(getSharedPreferences(MeloScreenPolicy.PREFS_NAME, Context.MODE_PRIVATE))
        if (style == MeloNowPlayingPolicy.NowPlayingStyle.STUDIO_DECK) {
            val deck = studioDeck?.takeIf { it.isShowing() } ?: MeloStudioDeck(this, meloHost)
            studioDeck = deck
            deck.show()
            return
        }
        val dialog = fullPlayer ?: NowPlayingFullScreenDialog(
            this,
            themeTokens = { themeTokens },
            media = { player },
            downloadProgress = { currentDownloadNote() },
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

    private fun offlineSong(it: IndexedTelegramSong) = Song(
        it.fileId, it.title, it.filename, it.songKey, it.chatId, it.fileSize,
        rawMessageId = it.messageId, performer = it.performer,
        durationSeconds = it.durationSeconds, sourceTitle = it.sourceTitle
    )

    private fun offlineRepresentative(group: TelegramOfflineLibrary.Group): Song = offlineSong(
        group.entries.firstOrNull { it.song.songKey == currentTelegramMessageId() }?.song ?: group.entries.first().song
    )

    private fun showOfflineVersions(group: TelegramOfflineLibrary.Group, anchor: View? = null) {
        val account = library.authorizedUser()?.value ?: return
        val labels = group.entries.map { entry ->
            val s = entry.song
            TelegramSongDisplayPolicy.versionLabel(
                s.sourceTitle ?: sourceNames[s.chatId], s.filename, s.date, s.fileSize,
                playing = s.songKey == currentTelegramMessageId()
            )
        }.toTypedArray()
        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog).setTitle(group.label).setItems(labels) { _, index ->
            val selected = group.entries[index].song
            val entry = group.selected(account, selected.chatId, selected.messageId) ?: return@setItems
            if (library.authorizedUser()?.value != account || !auth.isReady()) return@setItems
            val song = offlineSong(entry.song)
            if (anchor != null) showSongOptionsMenu(song, true, tracks) else downloadAndPlay(song)
        }.setNegativeButton("Đóng", null).show()
    }

    private fun discoverOffline(
        db: TelegramLibraryDao, account: Long, chat: Long?, query: String,
        playlist: Boolean, favorites: Set<Long>, generation: Long
    ) {
        val sortForScan = activeSort()
        val root = filesRoot
        val cancelled = { uiQueryGeneration.get() != generation || isDestroyed ||
            Thread.currentThread().isInterrupted || !auth.isReady() || library.authorizedUser()?.value != account }
        offlineSummary = "Đang kiểm tra tệp offline trên toàn bộ thư viện…"
        shownSongs = emptyList()
        tracks.adapter = adapter
        adapter.clear()
        tracks.setSelectionFromTop(0, tracks.paddingTop)
        updateSyncHeaderSubtitle()
        offlineExecutor.execute {
            try {
                fun publish(snapshot: TelegramOfflineLibrary.Snapshot) {
                    val groups = sortGroups(TelegramOfflineLibrary.groups(snapshot.entries), sortForScan)
                    runOnUiThread {
                        if (cancelled()) return@runOnUiThread
                        offlineGroups = groups
                        snapshot.entries.forEach { cachedSongPaths[it.song.songKey] = it.path }
                        shownSongs = groups.take(offlineVisibleLimit).map { offlineRepresentative(it) }
                        totalCountForCurrentView = groups.size
                        offlineSummary = "${groups.size} nhóm / ${snapshot.entries.size} bản offline đã xác minh · " +
                            "${snapshot.checked} mục đã kiểm tra" +
                            (if (!snapshot.finished) " · Đang kiểm tra…" else "") +
                            (if (snapshot.unknown > 0) " · ${snapshot.unknown} mục chưa xác minh được" else "")
                        bentoScrollView.visibility = View.GONE
                        sourceViewModeToggle.visibility = View.GONE
                        hideZeroSourcesToggle.visibility = View.GONE
                        tracks.visibility = View.VISIBLE
                        if (tracks.adapter !== adapter) tracks.adapter = adapter
                        val first = tracks.firstVisiblePosition
                        val oldTop = tracks.getChildAt(0)?.top ?: tracks.paddingTop
                        val top = if (first == 0) oldTop.coerceAtLeast(tracks.paddingTop) else oldTop
                        adapter.clear()
                        adapter.addAll(shownSongs.map { TelegramSongDisplayPolicy.label(it.title, it.filename) })
                        if (first >= 0 && first < shownSongs.size) tracks.setSelectionFromTop(first, top)
                        libraryTitle.text = if (playlist) "PLAYLIST · OFFLINE" else "THƯ VIỆN OFFLINE · " +
                            (chat?.let { sourceNames[it] } ?: "TẤT CẢ TELEGRAM")
                        filterChipsRow.visibility = if (isLandscapeImeCollapsed) View.GONE else View.VISIBLE
                        updateFilterChipsUi()
                        updateBottomNavState()
                        updateSyncHeaderSubtitle()
                    }
                }
                val snapshot = TelegramOfflineLibrary.scan(account, root,
                    page = { offset, limit ->
                        // Apply playlist selection AFTER pagination, never truncate the account index.
                        db.getSongs(account, chatId = chat, searchQuery = query.takeIf { it.isNotBlank() }, offset = offset, limit = limit)
                    },
                    resolve = { rows ->
                        val eligible = rows.filter { !playlist || it.songKey in favorites }
                        val results = resolveOfflineBatch(eligible, generation, cancelled)
                        val byKey = eligible.zip(results).associate { it.first.songKey to it.second }
                        rows.map { byKey[it.songKey] ?: TelegramOfflineLibrary.Local.NotDownloaded }
                    }, cancelled = cancelled, progress = { publish(it) })
                if (snapshot != null) publish(snapshot)
            } catch (_: Exception) {
                runOnUiThread {
                    if (!cancelled()) {
                        offlineSummary = "Kiểm tra offline chưa hoàn tất. Hãy thử lại."
                        updateSyncHeaderSubtitle()
                    }
                }
            }
        }
    }

    /** At most 32 outstanding local-only requests. Never use a persisted session file_id. */
    private fun resolveOfflineBatch(
        rows: List<IndexedTelegramSong>, generation: Long, cancelled: () -> Boolean
    ): List<TelegramOfflineLibrary.Local> {
        val requests = rows.map { song ->
            val tag = "offlineLocal:$generation:${song.chatId}:${song.messageId}"
            val future = java.util.concurrent.CompletableFuture<JSONObject>()
            pendingOffline[tag] = future
            try {
                send("getMessageLocally", tag) {
                    it.put("chat_id", song.chatId).put("message_id", song.messageId)
                }
            } catch (_: Exception) { future.complete(JSONObject()) }
            Triple(song, tag, future)
        }
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8)
        try {
            return requests.map { (song, _, future) ->
                while (!future.isDone && !cancelled() && System.nanoTime() < deadline) {
                    try { future.get(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
                    catch (_: java.util.concurrent.TimeoutException) { }
                }
                if (!cancelled() && !future.isDone) throw java.util.concurrent.TimeoutException("Local discovery timed out")
                val json = if (!cancelled() && future.isDone) future.getNow(JSONObject()) else JSONObject()
                if (json.optLong("chat_id") != song.chatId || json.optLong("id") != song.messageId) {
                    TelegramOfflineLibrary.Local.Unknown
                } else when (val result = TelegramAudioResolverPolicy.parseMessage(json)) {
                    is TelegramAudioResolverPolicy.Result.Resolved -> if (result.isDownloadingCompleted) {
                        TelegramOfflineLibrary.Local.Complete(result.localPath, result.size)
                    } else TelegramOfflineLibrary.Local.NotDownloaded
                    else -> TelegramOfflineLibrary.Local.Unknown
                }
            }
        } finally {
            requests.forEach { (_, tag, future) -> pendingOffline.remove(tag, future) }
        }
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
        val currentSort = activeSort()
        val currentFloor = letterFloor.takeIf { currentSort == SongSort.NAME }

        offlineSummary = null
        offlineGroups = emptyList()
        offlineVisibleLimit = 200
        if (filterOnlyDownloaded && !isBrowseSources && db != null && userId > 0L) {
            discoverOffline(db, userId, currentChatId, q, isPlaylist, currentFavIds, generation)
            return
        }

        executeOnDb {
            if (uiQueryGeneration.get() != generation) return@executeOnDb

            if (isBrowseSources) {
                val sources = if (db != null && userId > 0L) db.getSources(userId) else emptyList()
                val totalCount = if (db != null && userId > 0L) db.getSongCount(userId) else songs.size
                val rawSources: List<IndexedTelegramSource> = if (sources.isNotEmpty()) {
                    sources
                } else {
                    TelegramSourcePolicy.sortedCounts(songs.map { it.chatId }).map { (chatId, count) ->
                        IndexedTelegramSource(
                            accountId = if (userId > 0L) userId else 1L,
                            chatId = chatId,
                            title = sourceNames[chatId] ?: "Nguồn Telegram $chatId",
                            trackCount = count
                        )
                    }
                }
                rawSources.forEach { s ->
                    if (s.title.isNotBlank()) sourceNames[s.chatId] = s.title
                }
                val distinctSources = TelegramVaultBentoPolicy.distinctSourcesByChatId(rawSources)
                currentAuthoritativeSourceCount = distinctSources.size
                val visibleSources = if (hideZeroSources) distinctSources.filter { it.trackCount > 0 } else distinctSources
                val filteredSources = TelegramVaultBentoPolicy.filterSources(visibleSources, q)
                val activeCount = distinctSources.count { it.trackCount > 0 }
                val sourceCount = distinctSources.size

                runOnUiThread {
                    if (uiQueryGeneration.get() != generation || isDestroyed) return@runOnUiThread
                    shownSongs = emptyList()
                    libraryTitle.text = TelegramVaultBentoPolicy.formatHeaderTitle(activeCount)
                    sourcesButton.text = "☷  Nhóm / kênh / bot ($sourceCount)"
                    songFilter.hint = "Tìm nhóm, kênh hoặc bot…"
                    if (::filterChipsRow.isInitialized) {
                        filterChipsRow.visibility = View.GONE
                    }
                    if (::sourceViewModeToggle.isInitialized) {
                        sourceViewModeToggle.visibility = View.VISIBLE
                        updateSourceViewModeToggleUi()
                    }
                    if (::hideZeroSourcesToggle.isInitialized) {
                        hideZeroSourcesToggle.visibility = View.VISIBLE
                        updateHideZeroSourcesToggleUi()
                    }

                    val display = if (q.isBlank()) {
                        listOf(0L to totalCount) + filteredSources.map { it.chatId to it.trackCount }
                    } else {
                        filteredSources.map { it.chatId to it.trackCount }
                    }
                    visibleSourceIds = display.map { it.first }
                    visibleSourceCounts = display
                    scheduleMeloLibraryRefresh()

                    if (currentSourceDisplayMode == TelegramVaultBentoPolicy.SourceDisplayMode.BENTO) {
                        tracks.visibility = View.GONE
                        bentoScrollView.visibility = View.VISIBLE
                        renderBentoGrid(filteredSources, totalCount, q)
                    } else {
                        bentoScrollView.visibility = View.GONE
                        tracks.visibility = View.VISIBLE
                        if (tracks.adapter !== sourcesAdapter) tracks.adapter = sourcesAdapter
                        sourcesAdapter.clear()
                        sourcesAdapter.addAll(display.map { (chatId, count) ->
                            val label = if (chatId == 0L) "♫ Tất cả nguồn"
                                else sourceNames[chatId] ?: "Nguồn Telegram $chatId"
                            "$label  ·  $count bài"
                        })
                    }
                    updateBottomNavState()
                    // E9.3-03C: Reset totalCountForCurrentView to the authoritative full-DB
                    // total whenever we are in sources-overview mode (Bento OR List). Without
                    // this assignment, a stale per-channel count left by a prior channel visit
                    // would persist and make updateSyncHeaderSubtitle() report e.g. "4,332 bài"
                    // instead of the correct library-wide total.
                    totalCountForCurrentView = totalCount
                    updateSyncHeaderSubtitle()
                }
            } else {
                val (loadedSongs, totalDisplayCount) = if (isPlaylist) {
                    val favSongs = currentFavIds.mapNotNull { id -> songs.firstOrNull { it.messageId == id } }
                    val filtered = if (q.isBlank()) favSongs else favSongs.filter {
                        MeloComponentPolicy.matchesSearch(q, it.title, it.performer, it.filename)
                    }
                    Pair(sortSongs(filtered, currentSort), filtered.size)
                } else if (isAllChats && db != null && userId > 0L) {
                    val filterQuery = q.takeIf { it.isNotBlank() }
                    val count = db.getSongCount(userId, chatId = currentChatId, searchQuery = filterQuery)
                    floorCountForCurrentView = if (currentFloor != null) {
                        db.getSongCount(userId, chatId = currentChatId, searchQuery = filterQuery, fromKey = currentFloor)
                    } else count
                    val page = db.getSongs(
                        userId, chatId = currentChatId, searchQuery = filterQuery, offset = 0, limit = 200,
                        sort = currentSort, fromKey = currentFloor
                    )
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
                        MeloComponentPolicy.matchesSearch(q, it.title, it.performer, it.filename)
                    }
                    Pair(sortSongs(filtered, currentSort), filtered.size)
                } else {
                    val filtered = if (q.isBlank()) songs.toList() else songs.filter {
                        MeloComponentPolicy.matchesSearch(q, it.title, it.performer, it.filename)
                    }
                    Pair(sortSongs(filtered, currentSort), filtered.size)
                }
                val finalSongs = if (filterOnlyDownloaded) {
                    loadedSongs.filter { s ->
                        val p = cachedSongPaths[s.messageId]
                        p != null && File(p).isFile
                    }
                } else loadedSongs
                val finalDisplayCount = if (filterOnlyDownloaded) finalSongs.size else totalDisplayCount

                val sourceCount = if (db != null && userId > 0L) db.getSources(userId).size else songs.map { it.chatId }.distinct().size
                currentAuthoritativeSourceCount = sourceCount

                val withDurationCount = finalSongs.count { it.durationSeconds > 0 }
                android.util.Log.i("MeloVault", "SongItemCard duration audit: $withDurationCount of ${finalSongs.size} displayed songs have positive duration metadata")

                runOnUiThread {
                    if (uiQueryGeneration.get() != generation || isDestroyed) return@runOnUiThread
                    if (::sourceViewModeToggle.isInitialized) {
                        sourceViewModeToggle.visibility = View.GONE
                    }
                    if (::hideZeroSourcesToggle.isInitialized) {
                        hideZeroSourcesToggle.visibility = View.GONE
                    }
                    if (::bentoScrollView.isInitialized) {
                        bentoScrollView.visibility = View.GONE
                    }
                    tracks.visibility = View.VISIBLE
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
                        val shouldShowChips = !isLandscapeImeCollapsed && auth.isReady() && !isBrowseSources
                        filterChipsRow.visibility = if (shouldShowChips) View.VISIBLE else View.GONE
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

    // ---- New-screen layers (Gọn, Car Mode) ---------------------------------------------------

    /**
     * Shows Car Mode or the Gọn layer over the legacy tree when their settings/screen ask for
     * it and the account is verified. Car Mode wins over every other rule (spec).
     */
    private fun applyMeloLayer() {
        if (!::outerRoot.isInitialized || !::mainRoot.isInitialized || isDestroyed) return
        val prefs = getSharedPreferences(MeloScreenPolicy.PREFS_NAME, Context.MODE_PRIVATE)
        val measured = MeloUiLabActivity.realMeasurements(this)
        val display = measured.display
        val carSetting = MeloCarPolicy.loadSetting(prefs)
        if (MeloCarPolicy.shouldUseCar(carSetting, auth.isReady())) {
            showCarLayer(carSetting, display)
            return
        }
        maybeAskCarMode(prefs, carSetting, display)
        val setting = MeloCompactPolicy.loadSetting(prefs)
        val wanted = MeloCompactPolicy.shouldUseCompact(setting, display.widthDp, display.heightDp, auth.isReady())
        if (!wanted) {
            showNewLayer(prefs, measured)
            return
        }
        val key = "compact:${setting.key}:${display.widthPx}x${display.heightPx}:${display.densityDpi}:${display.fontScale}"
        if (compactScreen != null && meloLayerKey == key) {
            scheduleMeloLibraryRefresh()
            return
        }
        detachMeloLayer()
        if (MeloCompactPolicy.needsDefaultDownloadedFilter(MeloCompactPolicy.defaultApplied(prefs))) {
            MeloCompactPolicy.markDefaultApplied(prefs)
            playlistMode = false
            browseSources = false
            filterOnlyDownloaded = true
            updateFilterChipsUi()
            updatePlaylistButtons()
            refreshSongRows()
        }
        val emulate = setting == MeloCompactPolicy.CompactSetting.EMULATE_SHANLING
        val spec = if (emulate) MeloScreenPolicy.Emulation.SHANLING_M3_ULTRA.spec() ?: display else display
        val frameContext = if (emulate) MeloEmulation.emulatedContext(this, spec) else this
        val sizes = MeloDesignTokens.sizes(MeloDesignTokens.Profile.COMPACT, spec.density, spec.fontScale, spec.physicalPpi)
        val screen = MeloCompactScreen(frameContext, sizes, meloHost, onBackPressedDispatcher)
        val layer: View = if (emulate) {
            MeloEmulation.FrameHost(this, frameContext, spec, MeloDesignTokens.PALETTE.background).also {
                it.frame.addView(screen.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }.root
        } else {
            screen.view.also { v ->
                ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                    view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
                    insets
                }
            }
        }
        compactScreen = screen
        addMeloLayer(layer, key)
        screen.attach()
        ViewCompat.requestApplyInsets(layer)
    }

    /**
     * Điện thoại dọc / Tablet dọc / Hai khung (ui-spec step 7) over the legacy tree, chosen from
     * the current window size; null result (legacy style, Gọn, not signed in) removes the layer.
     */
    private fun showNewLayer(prefs: android.content.SharedPreferences, measured: MeloUiLabActivity.Measurements) {
        val display = measured.display
        val layout = MeloScreenPolicy.chooseLayout(measured.windowWidthDp, measured.windowHeightDp, carMode = false)
        val layer = MeloLibraryPolicy.newLayer(MeloLibraryPolicy.loadStyle(prefs), layout, auth.isReady())
        if (layer == null) {
            detachMeloLayer()
            return
        }
        val key = "new:$layer:${measured.windowWidthPx}x${measured.windowHeightPx}:${display.densityDpi}:${display.fontScale}"
        if ((phoneScreen != null || tabletScreen != null) && meloLayerKey == key) {
            scheduleMeloLibraryRefresh()
            return
        }
        detachMeloLayer()
        val sizes = MeloDesignTokens.sizes(
            MeloLibraryPolicy.profileFor(measured.smallestWidthDp), display.density, display.fontScale, display.physicalPpi)
        val view: View
        if (layer == MeloLibraryPolicy.NewLayer.TWO_PANE) {
            val screen = MeloTabletScreen(this, sizes, meloHost, onBackPressedDispatcher)
            tabletScreen = screen
            view = screen.view
            installLayerInsets(view)
            addMeloLayer(view, key)
            screen.attach()
        } else {
            val screen = MeloPhoneScreen(this, sizes, layer, meloHost, onBackPressedDispatcher)
            phoneScreen = screen
            view = screen.view
            installLayerInsets(view)
            addMeloLayer(view, key)
            screen.attach()
        }
        ViewCompat.requestApplyInsets(view)
    }

    /** System bars and keyboard are added to the screen's own padding instead of replacing it. */
    private fun installLayerInsets(view: View) {
        val base = intArrayOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(base[0] + bars.left, base[1] + bars.top, base[2] + bars.right, base[3] + maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    /** Car Mode at the panel's real pixels, full screen; emulated FYT frames for testing on a tablet. */
    private fun showCarLayer(setting: MeloCarPolicy.CarSetting, display: MeloScreenPolicy.DisplaySpec) {
        val key = "car:${setting.key}:${display.widthPx}x${display.heightPx}:${display.densityDpi}"
        if (carScreen != null && meloLayerKey == key) {
            scheduleMeloLibraryRefresh()
            return
        }
        detachMeloLayer()
        val emulation = setting.emulation
        val spec = emulation?.spec() ?: display
        val frameContext = if (emulation != null) MeloEmulation.emulatedContext(this, spec) else this
        val ppi = MeloCarPolicy.effectivePpi(spec.physicalPpi, spec.widthPx, spec.heightPx)
        val sizes = MeloDesignTokens.sizes(MeloDesignTokens.Profile.CAR, spec.density, spec.fontScale, ppi)
        val screen = MeloCarScreen(frameContext, sizes, meloHost, onBackPressedDispatcher, voiceSearchAvailable())
        val layer: View = if (emulation != null) {
            MeloEmulation.FrameHost(this, frameContext, spec, MeloDesignTokens.CAR_PALETTE.background).also {
                it.frame.addView(screen.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }.root
        } else {
            screen.view
        }
        setCarFullscreen(true)
        carScreen = screen
        addMeloLayer(layer, key)
        screen.attach()
    }

    private fun addMeloLayer(layer: View, key: String) {
        // The layer is opaque and takes every touch; the legacy tree keeps running underneath.
        layer.isClickable = true
        outerRoot.addView(layer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mainRoot.visibility = View.INVISIBLE
        meloLayer = layer
        meloLayerKey = key
    }

    private fun detachMeloLayer() {
        val wasCar = carScreen != null
        compactScreen?.detach()
        carScreen?.detach()
        phoneScreen?.detach()
        tabletScreen?.detach()
        meloLayer?.let { if (::outerRoot.isInitialized) outerRoot.removeView(it) }
        compactScreen = null
        carScreen = null
        phoneScreen = null
        tabletScreen = null
        meloLayer = null
        meloLayerKey = null
        if (wasCar) setCarFullscreen(false)
        if (::mainRoot.isInitialized) mainRoot.visibility = View.VISIBLE
    }

    /** Spec: Car Mode hides the system bars; a swipe shows them for a moment. */
    private fun setCarFullscreen(on: Boolean) {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            if (on) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /** First time on a wide landscape display: ask "Bật Car Mode?" once instead of switching on. */
    private fun maybeAskCarMode(
        prefs: android.content.SharedPreferences,
        setting: MeloCarPolicy.CarSetting,
        display: MeloScreenPolicy.DisplaySpec
    ) {
        if (carPromptShown || !MeloCarPolicy.shouldAskCarMode(
                setting, MeloCarPolicy.asked(prefs), display.widthDp, display.heightDp, auth.isReady())) return
        carPromptShown = true
        MeloCarPolicy.markAsked(prefs)
        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Bật Car Mode?")
            .setMessage("Màn hình này rộng như màn hình ô tô. Car Mode dùng nút to, chữ lớn, 5 bài mỗi trang và ẩn thanh hệ thống. " +
                "Có thể đổi lại trong ⋮ → Cài đặt giao diện.")
            .setNegativeButton("Không", null)
            .setPositiveButton("Bật Car Mode") { _, _ ->
                MeloCarPolicy.saveSetting(prefs, MeloCarPolicy.CarSetting.ON)
                applyMeloLayer()
            }
            .show()
    }

    private fun scheduleMeloLibraryRefresh() {
        if ((compactScreen == null && carScreen == null && phoneScreen == null && tabletScreen == null) || compactRefreshPosted || !::mainRoot.isInitialized) return
        compactRefreshPosted = true
        mainRoot.post {
            compactRefreshPosted = false
            if (!isDestroyed) {
                compactScreen?.onLibraryChanged()
                carScreen?.onLibraryChanged()
                phoneScreen?.onLibraryChanged()
                tabletScreen?.onLibraryChanged()
            }
        }
    }

    private fun notifyMeloPlayback() {
        compactScreen?.onPlaybackChanged()
        carScreen?.onPlaybackChanged()
        phoneScreen?.onPlaybackChanged()
        tabletScreen?.onPlaybackChanged()
    }

    private fun songIsDownloaded(song: Song): Boolean {
        if (filterOnlyDownloaded) return true
        val local = cachedSongPaths[song.messageId]?.let { File(it) } ?: return false
        return local.isFile && TelegramAccountStoragePolicy.isOwnedFile(filesRoot, local)
    }

    private fun sourceTitleOf(chatId: Long): String = sourceNames[chatId] ?: "Nguồn Telegram $chatId"

    /** Same row action as the legacy list: grouped offline copies open the chooser. */
    private fun playShownSong(index: Int) {
        val group = offlineGroups.getOrNull(index).takeIf { filterOnlyDownloaded }
        if (group != null && group.entries.size > 1) showOfflineVersions(group)
        else shownSongs.getOrNull(index)?.let { downloadAndPlay(it) }
    }

    private val meloHost = object : MeloScreenHost {
        override fun snapshot(): MeloScreenHost.Snapshot {
            val showingSources = allChatsMode && browseSources && !playlistMode
            val mode = when {
                playlistMode -> MeloCompactPolicy.Mode.PLAYLIST
                filterOnlyDownloaded -> MeloCompactPolicy.Mode.DOWNLOADED
                else -> MeloCompactPolicy.Mode.ALL
            }
            val playingKey = currentTelegramMessageId()
            val downloadingPercent = playingKey?.let { currentDownloadPercent() }
            val sort = activeSort()
            val rows = if (showingSources) emptyList() else shownSongs.map { song ->
                MeloScreenHost.Row(
                    title = TelegramSongDisplayPolicy.label(song.title, song.filename),
                    sourceTitle = song.sourceTitle ?: sourceNames[song.chatId],
                    filename = song.filename,
                    durationSeconds = song.durationSeconds,
                    songKey = song.messageId,
                    downloaded = songIsDownloaded(song),
                    downloadingPercent = if (song.messageId == playingKey) downloadingPercent else null,
                    art = thumbnailCache.get(song.messageId),
                    sortKey = if (sort == SongSort.NAME) MeloAlphabetPolicy.keyOf(song.title, song.performer, song.filename) else ""
                )
            }
            val sources = visibleSourceCounts.map { (chatId, count) ->
                MeloScreenHost.SourceRow(chatId, if (chatId == 0L) "Tất cả nguồn" else sourceTitleOf(chatId), count)
            }
            val total = when {
                showingSources -> null
                filterOnlyDownloaded -> offlineGroups.size
                playlistMode -> shownSongs.size
                else -> totalCountForCurrentView
            }
            val sourceTitle = when {
                playlistMode -> null
                !allChatsMode -> "Saved Messages"
                else -> selectedChatId?.let { sourceTitleOf(it) }
            }
            return MeloScreenHost.Snapshot(
                showingSources = showingSources,
                mode = mode,
                rows = rows,
                sources = sources,
                total = total,
                sourceTitle = sourceTitle,
                playingKey = playingKey,
                query = songFilter.text?.toString().orEmpty(),
                note = offlineSummary.takeIf { filterOnlyDownloaded && !showingSources },
                sort = sort,
                letterFloor = letterFloor.takeIf { sort == SongSort.NAME }
            )
        }

        override fun sort(): SongSort = activeSort()

        override fun setSort(sort: SongSort) {
            if (sort == librarySort()) return
            librarySortCache = sort
            getSharedPreferences(MeloScreenPolicy.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(MeloAlphabetPolicy.PREF_SORT, sort.name).apply()
            letterFloor = null
            offlineVisibleLimit = 200
            refreshSongRows()
        }

        override fun jumpToLetter(lowerBound: String?): Int? {
            if (activeSort() != SongSort.NAME) return 0
            val dbList = allChatsMode && !browseSources && !playlistMode && !filterOnlyDownloaded &&
                libraryDb != null && (library.authorizedUser()?.value ?: 0L) > 0L
            if (dbList) {
                if (letterFloor == lowerBound) return null
                letterFloor = lowerBound
                refreshSongRows()
                return null
            }
            if (lowerBound == null) return 0
            if (filterOnlyDownloaded) {
                val index = MeloAlphabetPolicy.firstIndexAtOrAfter(
                    offlineGroups.map { MeloAlphabetPolicy.keyOf(it.entries.first().song) }, lowerBound
                ).coerceAtMost((offlineGroups.size - 1).coerceAtLeast(0))
                if (index >= shownSongs.size) {
                    val target = (index + 200).coerceAtMost(offlineGroups.size)
                    offlineVisibleLimit = target
                    shownSongs = offlineGroups.take(target).map { offlineRepresentative(it) }
                    adapter.clear()
                    adapter.addAll(shownSongs.map { TelegramSongDisplayPolicy.label(it.title, it.filename) })
                }
                return index
            }
            return MeloAlphabetPolicy.firstIndexAtOrAfter(
                shownSongs.map { MeloAlphabetPolicy.keyOf(it.title, it.performer, it.filename) }, lowerBound
            ).coerceAtMost((shownSongs.size - 1).coerceAtLeast(0))
        }

        override fun setMode(mode: MeloCompactPolicy.Mode) {
            if (!auth.isReady()) return
            letterFloor = null
            when (mode) {
                MeloCompactPolicy.Mode.ALL -> { playlistMode = false; filterOnlyDownloaded = false; browseSources = false }
                MeloCompactPolicy.Mode.DOWNLOADED -> { playlistMode = false; filterOnlyDownloaded = true; browseSources = false }
                MeloCompactPolicy.Mode.PLAYLIST -> { playlistMode = true; browseSources = false; selectedChatId = null }
            }
            updateFilterChipsUi()
            updatePlaylistButtons()
            refreshSongRows()
        }

        override fun showSources() {
            if (!auth.isReady()) return
            letterFloor = null
            browseSources = true
            selectedChatId = null
            playlistMode = false
            songFilter.text?.clear()
            updatePlaylistButtons()
            refreshSongRows()
        }

        override fun openSource(chatId: Long?) {
            if (!auth.isReady()) return
            letterFloor = null
            selectedChatId = chatId
            browseSources = false
            playlistMode = false
            songFilter.text?.clear()
            updatePlaylistButtons()
            refreshSongRows()
        }

        override fun setQuery(query: String) {
            if (songFilter.text?.toString() != query) letterFloor = null
            if (songFilter.text?.toString() != query) songFilter.setText(query)
        }

        override fun loadMore() = loadMoreSongsIfNeeded()

        override fun playAt(index: Int) = playShownSong(index)

        override fun openSongMenu(index: Int, anchor: View) {
            val group = offlineGroups.getOrNull(index).takeIf { filterOnlyDownloaded }
            if (group != null && group.entries.size > 1) showOfflineVersions(group, anchor)
            else shownSongs.getOrNull(index)?.let { showSongOptionsMenu(it, songIsDownloaded(it), anchor) }
        }

        override fun nowPlaying(): MeloScreenHost.NowPlaying? {
            val controller = player ?: return null
            val item = controller.currentMediaItem ?: return null
            val key = currentTelegramMessageId()
            val song = key?.let { k ->
                shownSongs.firstOrNull { it.messageId == k } ?: queueSongOrder.firstOrNull { it.messageId == k }
                    ?: songs.firstOrNull { it.messageId == k }
            }
            val duration = controller.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            return MeloScreenHost.NowPlaying(
                songKey = key,
                title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
                    ?: song?.let { TelegramSongDisplayPolicy.label(it.title, it.filename) } ?: "Đang phát",
                sourceTitle = song?.let { it.sourceTitle ?: sourceNames[it.chatId] },
                filename = song?.filename,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                playing = controller.isPlaying,
                art = key?.let { thumbnailCache.get(it) },
                favorite = key != null && key in favoriteIds,
                shuffle = controller.shuffleModeEnabled,
                repeatLabel = when (controller.repeatMode) {
                    Player.REPEAT_MODE_ALL -> "tất cả"
                    Player.REPEAT_MODE_ONE -> "một bài"
                    else -> "tắt"
                },
                downloadNote = currentDownloadNote()
            )
        }

        override fun playPause() {
            player?.let { controller ->
                if (controller.currentMediaItem != null) {
                    if (controller.isPlaying) controller.pause() else controller.play()
                }
            }
        }

        override fun next() = playAdjacent(1)

        override fun previous() = playAdjacent(-1)

        override fun seekTo(positionMs: Long) {
            player?.seekTo(positionMs.coerceAtLeast(0L))
            notifyMeloPlayback()
        }

        override fun toggleFavorite() = toggleCurrentFavorite()

        override fun toggleShuffle() {
            player?.let {
                it.shuffleModeEnabled = !it.shuffleModeEnabled
                updateCockpitPlayer()
            }
        }

        override fun cycleRepeat() {
            player?.let {
                it.repeatMode = when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                updateCockpitPlayer()
            }
        }

        override fun openQueue() = showUpcomingQueue()

        override fun openLegacyOverflow(anchor: View) = showLibraryOverflow(anchor)

        override fun openUiLab() {
            startActivity(Intent(this@TelegramPreviewActivity, MeloUiLabActivity::class.java)
                .putExtra(MeloUiLabActivity.EXTRA_SONG_COUNT, totalCountForCurrentView))
        }

        override fun startVoiceSearch(onText: (String) -> Unit): Boolean {
            if (!voiceSearchAvailable()) return false
            voiceSearchCallback = onText
            return runCatching { voiceSearchResult.launch(voiceSearchIntent()) }
                .onFailure { voiceSearchCallback = null }
                .isSuccess
        }

        override fun playAll(shuffle: Boolean) {
            if (shownSongs.isEmpty()) return
            // Same path as tapping the first row: the queue is the list on screen.
            if (shuffle) player?.shuffleModeEnabled = true
            playShownSong(0)
        }

        override fun openNowPlaying() = showFullPlayer()

        override fun syncSubtitle(): String? =
            syncSubtitleText()

        override fun downloadedCount(): Int? = offlineGroups.size.takeIf { offlineSummary != null }

        override fun hideZeroSources(): Boolean = this@TelegramPreviewActivity.hideZeroSources

        override fun toggleHideZeroSources() {
            hideZeroSources = !hideZeroSources
            val prefs = getSharedPreferences(UiThemePolicy.PREFS_NAME, Context.MODE_PRIVATE)
            TelegramVaultBentoPolicy.saveHideZeroSources(prefs, hideZeroSources)
            updateHideZeroSourcesToggleUi()
            refreshSongRows()
        }

        override fun rescan() {
            if (!auth.isReady()) return
            autoRecoveryPolicy.onManualScanRequested()
            scanAllTelegramAudio(browseAfter = false, incremental = true)
        }

        override fun queue(): List<MeloScreenHost.QueueItem> {
            val account = library.authorizedUser()?.value ?: return emptyList()
            restorePlayingQueueIfAny(account)
            val current = currentTelegramMessageId()
            val percent = current?.let { currentDownloadPercent() }
            return queueSongOrder.map { song ->
                MeloScreenHost.QueueItem(
                    title = TelegramSongDisplayPolicy.label(song.title, song.filename),
                    sourceTitle = song.sourceTitle ?: sourceNames[song.chatId],
                    durationSeconds = song.durationSeconds,
                    songKey = song.messageId,
                    downloaded = ownedCachedFile(song.messageId) != null,
                    downloadingPercent = if (song.messageId == current) percent else null
                )
            }
        }

        override fun playQueueAt(index: Int) {
            queueSongOrder.getOrNull(index)?.let { downloadAndPlay(it) }
        }

        override fun qualityLabel(bitDepth: Int?): String? {
            val groups = player?.currentTracks?.groups ?: return null
            val key = currentTelegramMessageId()
            val filename = key?.let { k ->
                (queueSongOrder.firstOrNull { it.messageId == k } ?: songs.firstOrNull { it.messageId == k })?.filename
            }
            for (group in groups) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                for (i in 0 until group.length) {
                    if (!group.isTrackSelected(i)) continue
                    val f = group.getTrackFormat(i)
                    return MeloNowPlayingPolicy.qualityLabel(f.sampleMimeType, filename, f.sampleRate, f.bitrate, bitDepth)
                }
            }
            return null
        }

        override fun currentLocalFile(): File? = currentTelegramMessageId()?.let { ownedCachedFile(it) }

        override fun accountId(): Long = library.authorizedUser()?.value ?: 0L

        override fun prefetchEnabled(): Boolean = prefetchNextEnabled()

        override fun setPrefetchEnabled(enabled: Boolean) {
            MeloNowPlayingPolicy.setPrefetchEnabled(
                getSharedPreferences(MeloNowPlayingPolicy.PLAYBACK_PREFS, Context.MODE_PRIVATE), enabled)
            if (enabled) prefetchNextCachedNeighbor()
        }

        override fun sleepTimer(): Pair<Long?, Boolean> = ShareablePlaybackService.sleepTimerState()

        override fun setSleepTimer(option: MeloNowPlayingPolicy.SleepOption) = ShareablePlaybackService.setSleepTimer(option)
    }

    private fun prefetchNextEnabled(): Boolean =
        MeloNowPlayingPolicy.prefetchEnabled(getSharedPreferences(MeloNowPlayingPolicy.PLAYBACK_PREFS, Context.MODE_PRIVATE))

    /** A downloaded, account-owned file for [songKey], same check as the legacy list. */
    private fun ownedCachedFile(songKey: Long): File? {
        val local = cachedSongPaths[songKey]?.let { File(it) } ?: return null
        return local.takeIf { it.isFile && TelegramAccountStoragePolicy.isOwnedFile(filesRoot, it) }
    }

    /** Next page of the visible list; callers decide the list is near its end. */
    private fun sortSongs(list: List<Song>, sort: SongSort): List<Song> =
        if (sort != SongSort.NAME) list else list.sortedWith(
            compareBy<Song> { MeloAlphabetPolicy.keyOf(it.title, it.performer, it.filename) }
                .thenBy { it.chatId }.thenBy { it.messageId }
        )

    private fun sortGroups(groups: List<TelegramOfflineLibrary.Group>, sort: SongSort): List<TelegramOfflineLibrary.Group> =
        if (sort != SongSort.NAME) groups else groups.sortedWith(
            compareBy<TelegramOfflineLibrary.Group> { MeloAlphabetPolicy.keyOf(it.entries.first().song) }
                .thenBy { it.entries.first().song.chatId }.thenBy { it.entries.first().song.messageId }
        )

    private fun loadMoreSongsIfNeeded() {
        if (filterOnlyDownloaded && !(allChatsMode && browseSources && !playlistMode) &&
            shownSongs.size < offlineGroups.size) {
            val more = offlineGroups.drop(shownSongs.size).take(200).map { offlineRepresentative(it) }
            offlineVisibleLimit += more.size
            shownSongs = shownSongs + more
            adapter.addAll(more.map { TelegramSongDisplayPolicy.label(it.title, it.filename) })
        }
        if (!filterOnlyDownloaded && allChatsMode && !browseSources && !playlistMode && !isLoadingMoreSongs) {
            val userId = library.authorizedUser()?.value ?: 0L
            val db = libraryDb
            if (db != null && userId > 0L) {
                val sort = activeSort()
                val floor = letterFloor.takeIf { sort == SongSort.NAME }
                val reachable = if (floor != null) floorCountForCurrentView else totalCountForCurrentView
                if (shownSongs.size < reachable) {
                    isLoadingMoreSongs = true
                    val nextOffset = shownSongs.size
                    val currentGen = uiQueryGeneration.get()
                    val q = songFilter.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
                    val currentChat = selectedChatId
                    executeOnDb {
                        val count = db.getSongCount(userId, chatId = currentChat, searchQuery = q)
                        val floorCount = if (floor != null) {
                            db.getSongCount(userId, chatId = currentChat, searchQuery = q, fromKey = floor)
                        } else count
                        val more = if (nextOffset < floorCount) {
                            db.getSongs(
                                userId, chatId = currentChat, searchQuery = q, offset = nextOffset, limit = 200,
                                sort = sort, fromKey = floor
                            )
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
                                floorCountForCurrentView = floorCount
                                shownSongs = shownSongs + mapped
                                adapter.addAll(
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

    private fun showSongOptionsMenu(song: Song, isDownloaded: Boolean, anchor: View) {
        val themed = ContextThemeWrapper(this, R.style.Theme_MusicAssistantCar_Dialog)
        val popup = PopupMenu(themed, anchor)
        val isFav = song.messageId in favoriteIds
        popup.menu.add(0, 1, 1, "▶ Phát bài này")
        popup.menu.add(0, 2, 2, "⏭ Phát tiếp theo")
        popup.menu.add(0, 3, 3, if (isFav) "★ Xóa khỏi Playlist" else "☆ Thêm vào Playlist")
        if (!isDownloaded) {
            popup.menu.add(0, 4, 4, "⬇ Tải về và phát")
        }
        popup.menu.add(0, 5, 5, "ℹ Thông tin chi tiết")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> downloadAndPlay(song)
                2 -> addToUpcomingQueue(song)
                3 -> toggleFavoriteSong(song)
                4 -> downloadAndPlay(song)
                5 -> showSongDetailsDialog(song, isDownloaded)
            }
            true
        }
        popup.show()
    }

    private fun addToUpcomingQueue(song: Song) {
        if (!auth.isReady()) return
        val currentId = currentTelegramMessageId()
        val mutable = queueSongOrder.toMutableList()
        val currentIndex = if (currentId != null) mutable.indexOfFirst { it.messageId == currentId } else -1
        mutable.removeAll { it.messageId == song.messageId }
        val insertIndex = if (currentIndex >= 0 && currentIndex < mutable.size) {
            currentIndex + 1
        } else {
            mutable.size
        }
        mutable.add(insertIndex, song)
        queueSongOrder = mutable
        library.authorizedUser()?.let { owner ->
            ShareablePlaybackService.configureTelegramQueue(
                owner.value, filesRoot, queueSongOrder.map { entry ->
                    ShareablePlaybackService.QueueTrack(
                        entry.messageId, entry.fileId, entry.title,
                        cachedSongPaths[entry.messageId], entry.filename,
                        entry.chatId, entry.expectedSize, entry.genuineMessageId,
                        MeloMediaControlPolicy.fallbackArtist(entry.sourceTitle ?: sourceNames[entry.chatId])
                    )
                }
            )
        }
        showMessage("Đã thêm '${song.title}' vào hàng đợi phát tiếp theo.")
    }

    private fun toggleFavoriteSong(song: Song) {
        val account = library.authorizedUser() ?: return
        if (!auth.isReady()) return
        val target = song.messageId
        val next = TelegramPlaylistPolicy.toggle(favoriteIds, target)
        val result = runCatching { playlistStore.save(account, next) }
        if (result.isFailure) {
            showMessage("Không lưu được playlist an toàn trên thiết bị.")
            return
        }
        favoriteIds = next
        updatePlaylistButtons()
        refreshSongRows()
        val isAdded = target in next
        showMessage(if (isAdded) "Đã thêm '${song.title}' vào Playlist." else "Đã xóa '${song.title}' khỏi Playlist.")
    }

    private fun showSongDetailsDialog(song: Song, isDownloaded: Boolean) {
        val themed = ContextThemeWrapper(this, R.style.Theme_MusicAssistantCar_Dialog)
        val source = song.sourceTitle ?: (sourceNames[song.chatId] ?: "Telegram")
        val performer = song.performer?.takeIf { it.isNotBlank() } ?: "Không xác định"
        val dur = TelegramSongDisplayPolicy.formatDuration(song.durationSeconds) ?: "Không xác định"
        val size = TelegramSongDisplayPolicy.formatFileSize(song.expectedSize) ?: "Không xác định"
        val statusText = if (isDownloaded) "Đã tải về máy (Sẵn sàng phát offline)" else "Lưu trữ trên đám mây Telegram"
        val localPath = cachedSongPaths[song.messageId]?.let { "• Tệp cục bộ: $it\n" } ?: ""

        val info = buildString {
            append("• Tiêu đề: ${song.title}\n")
            append("• Nghệ sĩ: $performer\n")
            append("• Tệp gốc: ${song.filename}\n")
            append("• Nguồn: $source\n")
            append("• Thời lượng: $dur\n")
            append("• Dung lượng: $size\n")
            append("• Trạng thái: $statusText\n")
            append(localPath)
            append("• ID tin nhắn: ${song.genuineMessageId} (Chat ID: ${song.chatId})")
        }

        AlertDialog.Builder(themed)
            .setTitle("Thông tin chi tiết")
            .setMessage(info)
            .setPositiveButton("Đóng", null)
            .show()
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
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    status.text = message
                    status.visibility = View.VISIBLE
                }
            }
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
        if (tag.startsWith("offlineLocal:")) {
            pendingOffline.remove(tag)?.complete(result)
            return
        }
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
            if (tag == "auth") {
                // Wrong code/password, expired code, bad phone: fixed sentence under the field,
                // the typed value stays. The raw TDLib text is matched, never shown.
                val field = TelegramLoginStepPolicy.contentFor(
                    auth.step, null, null, loginEditingPhone
                )?.field ?: TelegramLoginStepPolicy.Field.CODE
                val text = TelegramLoginStepPolicy.errorFor(result.optString("message"), field)
                runOnUiThread {
                    loginPanel.setBusy(false)
                    loginPanel.showError(text)
                    updateResendButton()
                }
                return
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
                        applyMeloLayer()
                        maybeAskNotificationPermission()
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
                showLoginStep(TelegramAuthStep.WAIT_PHONE_NUMBER)
                runOnUiThread { qrLogin.isEnabled = true }
            }
            "authorizationStateWaitCode" -> {
                val info = state.optJSONObject("code_info")
                // Keep only the masked number for display; the raw one is dropped here.
                loginMaskedPhone = TelegramLoginStepPolicy.maskPhone(info?.optString("phone_number"))
                startResendTimer(info?.optInt("timeout", 0) ?: 0, info?.optJSONObject("next_type") != null)
                showLoginStep(TelegramAuthStep.WAIT_CODE)
            }
            "authorizationStateWaitPassword" -> {
                loginPasswordHint = state.optString("password_hint").takeIf { it.isNotBlank() }
                showLoginStep(TelegramAuthStep.WAIT_PASSWORD)
            }
            "authorizationStateWaitEmailAddress" ->
                showLoginStep(TelegramAuthStep.WAIT_EMAIL_ADDRESS)
            "authorizationStateWaitEmailCode" ->
                showLoginStep(TelegramAuthStep.WAIT_EMAIL_CODE)
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
                runOnUiThread {
                    qrLogin.isEnabled = false
                    loginPanel.clearInput()
                    loginMaskedPhone = null
                    loginPasswordHint = null
                    loginEditingPhone = false
                    mainRoot.removeCallbacks(loginResendTick)
                    status.visibility = View.VISIBLE
                }
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
                    loginPanel.clearInput()
                    loginMaskedPhone = null
                    loginPasswordHint = null
                    loginEditingPhone = false
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

    /** TDLib moved to a credential step: show that step's form (display only). */
    private fun showLoginStep(step: TelegramAuthStep) {
        if (!auth.onNativeState(authEpoch, step)) return
        runOnUiThread {
            loginEditingPhone = false
            renderLoginStep()
        }
    }

    private fun renderLoginStep() {
        val content = TelegramLoginStepPolicy.contentFor(
            auth.step, loginMaskedPhone, loginPasswordHint, loginEditingPhone
        ) ?: return
        mainRoot.removeCallbacks(loginResendTick)
        loginPanel.clearInput()
        loginPanel.render(content)
        authControls.visibility = View.VISIBLE
        collapsibleNavigation.visibility = View.VISIBLE
        input.visibility = View.VISIBLE
        // Errors and progress live inside the form while signing in.
        status.visibility = View.GONE
        if (content.showResend) updateResendButton()
    }

    private fun onLoginBack() {
        if (loginEditingPhone) {
            loginEditingPhone = false
        } else if (auth.step == TelegramAuthStep.WAIT_PASSWORD) {
            loginEditingPhone = true
        } else return
        renderLoginStep()
    }

    /** Telegram's resend timer (code_info.timeout), counted down once a second. */
    private fun startResendTimer(timeoutSeconds: Int, allowed: Boolean) {
        loginResendAllowed = allowed
        loginResendUntilMs = System.currentTimeMillis() + timeoutSeconds.coerceIn(0, 600) * 1000L
    }

    private fun updateResendButton() {
        if (!::loginPanel.isInitialized || !::mainRoot.isInitialized) return
        mainRoot.removeCallbacks(loginResendTick)
        val left = ((loginResendUntilMs - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0)
        loginPanel.setResendState(left, loginResendAllowed)
        if (left > 0 && auth.step == TelegramAuthStep.WAIT_CODE && !loginEditingPhone) {
            mainRoot.postDelayed(loginResendTick, 1000L)
        }
    }

    private val loginResendTick = Runnable { updateResendButton() }

    private fun requestResendCode() {
        if (auth.step != TelegramAuthStep.WAIT_CODE || loginEditingPhone) return
        loginPanel.showError(null)
        try {
            send("resendAuthenticationCode", "auth")
            // Disabled until Telegram answers with a fresh code_info (which resets the timer).
            startResendTimer(10, true)
            updateResendButton()
        } catch (_: Exception) {
            loginPanel.showError(TelegramLoginStepPolicy.errorFor("resend", TelegramLoginStepPolicy.Field.CODE))
        }
    }

    private fun submitAuthentication() {
        val value = input.text?.toString()?.trim().orEmpty()
        if (value.isBlank()) return
        val kind = if (loginEditingPhone && auth.step != TelegramAuthStep.WAIT_PHONE_NUMBER) {
            "setAuthenticationPhoneNumber"
        } else when (auth.step) {
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
            // Never persist or print passwords, codes, phone or email. The field keeps the
            // value only so a wrong code or password can be corrected; it is cleared on the
            // next step, on success and on sign-out.
            loginPanel.showError(null)
            loginPanel.setBusy(true)
        } catch (_: Exception) {
            loginPanel.setBusy(false)
            loginPanel.showError("Không gửi được yêu cầu. Kiểm tra mạng và thử lại.")
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
            currentAuthoritativeSourceCount = sources.size
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
                    val actualSources = if (currentAuthoritativeSourceCount > 0) {
                        currentAuthoritativeSourceCount
                    } else {
                        ShortWindowLayoutPolicy.truthfulSourceCount(visibleSourceIds).coerceAtLeast(1)
                    }
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
        // "Tải trước bài kế" off: already-downloaded neighbors above still play next; no new download.
        if (!prefetchNextEnabled()) return
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
                mediaItem(Uri.fromFile(target), song.title, mediaId, song.sourceTitle ?: sourceNames[song.chatId])
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
        detachMeloLayer()
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
        detachMeloLayer()
        if (::mainRoot.isInitialized) {
            mainRoot.removeCallbacks(miniPlayerProgressTick)
            mainRoot.removeCallbacks(loginResendTick)
        }
        fullPlayer?.dismiss()
        fullPlayer = null
        studioDeck?.dismiss()
        studioDeck = null
        queueDialog?.dismiss()
        queueDialog = null
        clearPendingQrLink()
        syncCoordinator?.cancelSync()
        dbExecutor.shutdownNow()
        offlineExecutor.shutdownNow()
        pendingOffline.values.forEach { it.cancel(false) }
        pendingOffline.clear()
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
