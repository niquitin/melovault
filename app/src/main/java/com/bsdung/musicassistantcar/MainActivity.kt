package com.bsdung.musicassistantcar

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.os.Parcelable
import android.os.SystemClock
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import java.util.Locale
import kotlin.math.roundToInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import android.net.Uri
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.bsdung.musicassistantcar.databinding.DialogFolderActionsBinding
import com.bsdung.musicassistantcar.databinding.DialogTrackActionsBinding
import com.bsdung.musicassistantcar.databinding.DialogPlaylistChooserBinding
import com.bsdung.musicassistantcar.databinding.DialogSignalPathBinding
import com.bsdung.musicassistantcar.databinding.DialogMusicAccountsBinding
import com.bsdung.musicassistantcar.databinding.DialogYoutubePlaylistsBinding
import com.bsdung.musicassistantcar.ui.dialog.UnifiedLibraryDialog
import com.bsdung.musicassistantcar.data.UnifiedTrackDto
import com.bsdung.musicassistantcar.ui.util.UnifiedPlaybackMapper
import com.bsdung.musicassistantcar.ui.util.ArtworkGestureAction
import com.bsdung.musicassistantcar.ui.util.ArtworkGesturePolicy
import com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy
import com.bsdung.musicassistantcar.ui.util.MobilePlayerLayoutPolicy
import com.bsdung.musicassistantcar.ui.util.QueuePlaylistExport
import com.bsdung.musicassistantcar.ui.util.QueuePlaylistExportPolicy
import com.bsdung.musicassistantcar.ui.util.PlaylistBatchWriter
import com.bsdung.musicassistantcar.ui.util.PlaylistReorderGuard
import com.bsdung.musicassistantcar.data.AccountConnectionState
import com.bsdung.musicassistantcar.data.AccountProvider
import com.bsdung.musicassistantcar.data.AccountStatusDto
import com.bsdung.musicassistantcar.data.YouTubePlaylistDto
import com.bsdung.musicassistantcar.data.YouTubePlaylistItemDto
import com.bsdung.musicassistantcar.ui.adapter.PlaylistChoiceAdapter
import com.bsdung.musicassistantcar.ui.adapter.YouTubePlaylistsAdapter
import com.bsdung.musicassistantcar.ui.adapter.YouTubePlaylistItemsAdapter
import com.bsdung.musicassistantcar.ui.adapter.LyricsCandidateAdapter
import com.bsdung.musicassistantcar.ui.adapter.SyncedLyricsAdapter
import com.bsdung.musicassistantcar.lyrics.LrcParser
import com.bsdung.musicassistantcar.lyrics.LyricsCache
import com.bsdung.musicassistantcar.lyrics.LyricsCandidate
import com.bsdung.musicassistantcar.lyrics.LyricsCandidatePickerState
import com.bsdung.musicassistantcar.lyrics.LyricsOverrideStore
import com.bsdung.musicassistantcar.lyrics.LyricsProvenance
import com.bsdung.musicassistantcar.lyrics.LyricsRepository
import com.bsdung.musicassistantcar.lyrics.LyricsSessionController
import com.bsdung.musicassistantcar.lyrics.LyricsTrackMetadata
import com.bsdung.musicassistantcar.lyrics.LyricsUiState
import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.PlaybackQuality
import com.bsdung.musicassistantcar.data.RemoteCatalog
import com.bsdung.musicassistantcar.data.StreamQuality
import com.bsdung.musicassistantcar.data.MediaTechnicalInfo
import com.bsdung.musicassistantcar.data.SignalPathInfo
import com.bsdung.musicassistantcar.ui.util.SignalPathFormatter
import com.bsdung.musicassistantcar.databinding.ActivityMainBinding
import com.bsdung.musicassistantcar.playback.MusicService
import com.bsdung.musicassistantcar.playback.QueueBrowseSeed
import com.bsdung.musicassistantcar.ui.adapter.LibraryAdapter
import com.bsdung.musicassistantcar.ui.adapter.QueueAdapter
import com.bsdung.musicassistantcar.playback.QueueHelper
import com.bsdung.musicassistantcar.playback.PlayNextAction
import com.bsdung.musicassistantcar.playback.AddToQueueAction
import com.bsdung.musicassistantcar.playback.RemoveQueueResult
import com.bsdung.musicassistantcar.playback.QueueListItem
import com.bsdung.musicassistantcar.ui.controller.ProgressController
import com.bsdung.musicassistantcar.ui.controller.ProgressiveEnrichmentController
import com.bsdung.musicassistantcar.ui.model.LibraryItem
import com.bsdung.musicassistantcar.ui.model.LibraryLocation
import com.bsdung.musicassistantcar.ui.model.NavigationManager
import com.bsdung.musicassistantcar.ui.util.ArtworkLoader
import com.bsdung.musicassistantcar.ui.util.AudioInfoFormatter
import com.bsdung.musicassistantcar.ui.util.MetadataDisplayHelper
import com.google.common.util.concurrent.ListenableFuture
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Width-class breakpoints (based on available window dp, not physical orientation).
 * Thresholds chosen for FYT 1152x720 multi-window pane behaviour:
 *   COMPACT  : < 420 dp   — narrow FYT pane (~1/3 screen), portrait phone
 *   MEDIUM   : 420–699 dp — half-screen FYT pane, small landscape phone
 *   EXPANDED : >= 700 dp  — full/large FYT pane, wide landscape (triggers dual-panel layout)
 */
enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MusicAssistantCar"
        private const val TAG_LAYOUT = "MeloVaultLayout"

        /** Classify available window width in dp into a responsive width class. */
        fun widthClassFor(widthDp: Int): WindowWidthClass = when {
            widthDp >= 700 -> WindowWidthClass.EXPANDED
            widthDp >= 420 -> WindowWidthClass.MEDIUM
            else           -> WindowWidthClass.COMPACT
        }

        fun formatCollectionSubtitle(
            parentId: String,
            total: Int?,
            trackCount: Int?,
            folderCount: Int?,
        ): String? {
            val isDrive = parentId.startsWith(RemoteCatalog.DRIVE_FOLDER_PREFIX) ||
                parentId.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX)
            if (isDrive) {
                val fCount = folderCount ?: 0
                val tCount = trackCount ?: 0
                return when {
                    fCount > 0 && tCount > 0 -> "$fCount thư mục • $tCount bài"
                    fCount > 0 && tCount == 0 -> "$fCount thư mục"
                    fCount == 0 && trackCount != null -> "$trackCount bài"
                    total != null && total >= 0 -> "$total bài"
                    else -> null
                }
            }
            return if (total != null && total >= 0) "$total bài" else null
        }
    }

    private lateinit var binding: ActivityMainBinding
    private var browserFuture: ListenableFuture<MediaBrowser>? = null
    private var browser: MediaBrowser? = null

    private lateinit var progressController: ProgressController
    private lateinit var libraryAdapter: LibraryAdapter
    private lateinit var queueAdapter: QueueAdapter
    private lateinit var syncedLyricsAdapter: SyncedLyricsAdapter
    private lateinit var fullscreenSyncedLyricsAdapter: SyncedLyricsAdapter
    private lateinit var lyricsCandidateAdapter: LyricsCandidateAdapter
    private lateinit var fullscreenLyricsCandidateAdapter: LyricsCandidateAdapter
    private lateinit var enrichmentController: ProgressiveEnrichmentController
    private var isQueueVisible: Boolean = false
    private var isLyricsVisible: Boolean = false
    private var isFullscreenLyricsVisible: Boolean = false
    private var isCandidatePickerOpen: Boolean = false
    private var currentCandidatePickerState: LyricsCandidatePickerState = LyricsCandidatePickerState.Closed
    private var isActivityInForeground: Boolean = false
    private var isLyricsAutoFollow: Boolean = true
    private var currentLyricsUiState: LyricsUiState = LyricsUiState.Idle
    private var currentLyricsSignature: String? = null
    private var currentLyricsSourceSummary: String = "Nguồn: LRCLIB · Tự động"
    private var isEditMode: Boolean = false
    private var userOpenedFullPlayer: Boolean = false
    private var currentLoadedArtworkUri: String? = null
    private var libraryItemTouchHelper: ItemTouchHelper? = null
    private val navManager = NavigationManager()
    private val lyricsRepository by lazy {
        LyricsRepository(
            cache = LyricsCache(this),
            overrideStore = LyricsOverrideStore(this),
            apiClient = CarApiClient(),
        )
    }
    private val lyricsExecutor = Executors.newSingleThreadExecutor()
    private val uiCatalog by lazy {
        RemoteCatalog(
            api = CarApiClient(),
            localRepoSupplier = { com.bsdung.musicassistantcar.data.LocalMediaRepository.getDefault(this) },
            downloadManagerSupplier = { com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this) },
            playlistSyncCoordinatorSupplier = { com.bsdung.musicassistantcar.playback.OfflinePlaylistSyncCoordinator.getDefault(this) },
        )
    }
    private val uiExecutor = Executors.newSingleThreadExecutor()
    private val prewarmExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prewarmedMediaKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var prewarmGeneration: Long = 0L

    private var activeBackendUrl: String? = null
    private var searchDebounceRunnable: Runnable? = null
    private var isSuppressingSearchWatcher: Boolean = false
    private var currentLoadRequestId: Long = 0L
    private var savedRecyclerState: Parcelable? = null
    private var pendingRestorePosition: Int? = null
    private var pendingRestoreOffset: Int? = null
    private val pagingPolicy = ContinuousPagingPolicy(pageSize = 30)
    private val pageSize: Int get() = pagingPolicy.pageSize
    private val isPagingLoading: Boolean get() = pagingPolicy.isLoading
    private val isPagingEndReached: Boolean get() = pagingPolicy.isEndReached
    private val currentLoadedPages: Int get() = pagingPolicy.currentLoadedPages
    private var pagingGeneration: Long = 0L
    private var dragInitialItems: List<com.bsdung.musicassistantcar.ui.model.LibraryItem>? = null
    private var dragInitialTrackIds: List<String>? = null
    private var currentCollectionRequestId: Long = 0L
    private val collectionFetchExecutor = Executors.newSingleThreadExecutor()
    @Volatile
    private var hasRunStartupPlaylistSync: Boolean = false
    private val cachedPlaylistTracks = java.util.concurrent.ConcurrentHashMap<Int, List<com.bsdung.musicassistantcar.data.CarTrack>>()
    private val cachedPlaylistSkippedCount = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    private fun captureCurrentScroll() {
        val lm = binding.recyclerView.layoutManager as? LinearLayoutManager
        val pos = lm?.findFirstVisibleItemPosition() ?: 0
        val offset = lm?.findViewByPosition(pos)?.top ?: 0
        navManager.saveCurrentScroll(pos, offset)
    }

    private fun restoreCurrentScroll() {
        val lm = binding.recyclerView.layoutManager as? LinearLayoutManager ?: return
        val pos = navManager.currentFirstVisiblePosition
        val offset = navManager.currentTopOffsetPx
        if (pos in 0 until libraryAdapter.itemCount) {
            lm.scrollToPositionWithOffset(pos, offset)
        }
    }

    private fun clearSearchInputSilently() {
        if (binding.searchInput.text?.isNotEmpty() == true) {
            isSuppressingSearchWatcher = true
            try {
                binding.searchInput.text?.clear()
                binding.searchClearButton.visibility = View.GONE
            } finally {
                isSuppressingSearchWatcher = false
            }
        } else {
            binding.searchClearButton.visibility = View.GONE
        }
    }


    private var currentFavoriteMediaId: String? = null
    private var currentFavoriteState: Boolean? = null
    private var currentTechnicalInfoMediaId: String? = null
    private var currentTechnicalInfo: MediaTechnicalInfo? = null

    private var currentSignalPathMediaId: String? = null
    private var currentSignalPathQuality: String? = null
    private var currentSignalPathInfo: SignalPathInfo? = null
    private var currentSignalPathFailed: Boolean = false
    private var currentSignalPathDialog: BottomSheetDialog? = null
    private var currentSignalPathDialogBinding: DialogSignalPathBinding? = null

    private fun requestSignalPathForCurrentItem() {
        val player = browser ?: return
        val currentItem = player.currentMediaItem ?: return
        val mediaId = currentItem.mediaId.takeIf { it.isNotBlank() } ?: return
        val quality = if (PlaybackQuality.current == StreamQuality.MOBILE_320) "mobile" else "original"

        if (currentSignalPathMediaId == mediaId && currentSignalPathQuality == quality && currentSignalPathInfo != null) {
            return
        }

        currentSignalPathMediaId = mediaId
        currentSignalPathQuality = quality
        currentSignalPathFailed = false
        uiExecutor.submit {
            val result = runCatching { uiCatalog.getSignalPath(mediaId, quality) }
            val info = result.getOrNull()
            runOnUiThread {
                val activeMediaId = browser?.currentMediaItem?.mediaId
                val activeQuality = if (PlaybackQuality.current == StreamQuality.MOBILE_320) "mobile" else "original"
                // Discard stale response if track or quality changed while request was in flight
                if (activeMediaId == mediaId && activeQuality == quality) {
                    currentSignalPathInfo = info
                    currentSignalPathFailed = (info == null)
                    renderSignalPathInDialog()
                }
            }
        }
    }

    private fun requestTechnicalInfoForCurrentItem() {
        val player = browser ?: return
        val currentItem = player.currentMediaItem ?: return
        val mediaId = currentItem.mediaId.takeIf { it.isNotBlank() } ?: return

        if (currentTechnicalInfoMediaId == mediaId && currentTechnicalInfo != null) {
            return
        }

        currentTechnicalInfoMediaId = mediaId
        uiExecutor.submit {
            val info = runCatching { uiCatalog.getMediaInfo(mediaId) }.getOrNull()
            runOnUiThread {
                if (browser?.currentMediaItem?.mediaId == mediaId) {
                    currentTechnicalInfo = info
                    updateNowPlaying()
                }
            }
        }
    }

    private fun isAnyLyricsVisible(): Boolean = isLyricsVisible || isFullscreenLyricsVisible

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateNowPlaying()
            if (isQueueVisible) {
                renderQueue()
            }
            if (isAnyLyricsVisible() && currentLyricsUiState is LyricsUiState.Synced) {
                syncActiveLyricsLineWithPlayer(forceScroll = false)
                if (player.isPlaying) {
                    startLyricsPositionLoop()
                } else {
                    stopLyricsPositionLoop()
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (isAnyLyricsVisible() && currentLyricsUiState is LyricsUiState.Synced) {
                syncActiveLyricsLineWithPlayer(forceScroll = isLyricsAutoFollow)
            }
            if (isFullscreenLyricsVisible) {
                updateFullscreenLyricsPlayerChrome()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                requestTechnicalInfoForCurrentItem()
                requestSignalPathForCurrentItem()
                val currentItem = browser?.currentMediaItem
                if (isAnyLyricsVisible() && currentItem != null) {
                    val readyMeta = extractLyricsTrackMetadata(currentItem)
                    val readySig = lyricsRepository.signatureFor(readyMeta)
                    if (readySig != currentLyricsSignature || currentLyricsUiState is LyricsUiState.Idle) {
                        loadLyricsForCurrentTrack(forceRefresh = false, metadataOverride = readyMeta)
                    } else if (currentLyricsUiState is LyricsUiState.Synced) {
                        syncActiveLyricsLineWithPlayer(forceScroll = isLyricsAutoFollow)
                    }
                }
            }
            if (isFullscreenLyricsVisible) {
                updateFullscreenLyricsPlayerChrome()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = browser?.currentMediaItem
            val isTelegram = item?.mediaMetadata?.extras?.getString("source_type")?.equals("telegram", ignoreCase = true) == true
                || navManager.currentParentId == RemoteCatalog.TELEGRAM_ID
                || currentSignalPathInfo?.sourceType?.equals("telegram", ignoreCase = true) == true
            if (isTelegram) {
                Toast.makeText(this@MainActivity, "Không thể phát bài này từ Telegram", Toast.LENGTH_SHORT).show()
            }
            updateNowPlaying()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentTechnicalInfo = null
            currentTechnicalInfoMediaId = null
            currentSignalPathInfo = null
            currentSignalPathMediaId = null
            currentSignalPathQuality = null
            currentSignalPathFailed = false
            handleLyricsOnTrackTransition(mediaItem)
            updateNowPlaying()
            requestTechnicalInfoForCurrentItem()
            requestSignalPathForCurrentItem()
            renderSignalPathInDialog()

            val player = browser ?: return
            if (player.mediaItemCount > 0) {
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex in 0 until player.mediaItemCount) {
                    val queueItems = buildList {
                        for (i in 0 until player.mediaItemCount) {
                            add(player.getMediaItemAt(i))
                        }
                    }
                    schedulePrewarmQueue(queueItems, currentIndex)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MusicAssistantCar)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        val mobile = getSharedPreferences("music_assistant_car", MODE_PRIVATE)
            .getBoolean("mobile_320", false)
        PlaybackQuality.current = if (mobile) StreamQuality.MOBILE_320 else StreamQuality.ORIGINAL

        logWindowMetrics("onCreate")

        setupViews()
        requestNotificationPermission()

        // Start service explicitly for sticky automotive lifecycle
        val serviceIntent = Intent(this, MusicService::class.java)
        startService(serviceIntent)
        runCatching {
            com.bsdung.musicassistantcar.playback.OfflinePlaylistSyncCoordinator.getDefault(this)
                .ensureBackgroundSyncScheduled()
        }

        if (savedInstanceState != null) {
            savedRecyclerState = savedInstanceState.getParcelable("recycler_state")
            isQueueVisible = savedInstanceState.getBoolean("is_queue_visible", false)
            isLyricsVisible = savedInstanceState.getBoolean("is_lyrics_visible", false)
            isFullscreenLyricsVisible = savedInstanceState.getBoolean("is_fullscreen_lyrics_visible", false)
            userOpenedFullPlayer = savedInstanceState.getBoolean("user_opened_full_player", false)
            val parentId = savedInstanceState.getString("nav_parent_id", "root") ?: "root"
            val label = savedInstanceState.getString("nav_label", "Thư viện") ?: "Thư viện"
            val page = savedInstanceState.getInt("nav_page", 0)
            val search = savedInstanceState.getString("nav_search", "") ?: ""
            val sort = savedInstanceState.getString("nav_sort", "") ?: ""
            @Suppress("DEPRECATION")
            val restoredStack = (
                savedInstanceState.getSerializable("nav_back_stack") as? ArrayList<*>
            )?.filterIsInstance<LibraryLocation>().orEmpty()
            navManager.restoreState(
                parentId,
                label,
                page,
                search,
                restoredStack,
                sort,
            )
            if (search.isNotBlank()) {
                binding.searchInput.setText(search)
            }
            currentFavoriteMediaId = savedInstanceState.getString("fav_media_id")
            if (savedInstanceState.containsKey("fav_state")) {
                currentFavoriteState = savedInstanceState.getBoolean("fav_state")
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isCandidatePickerOpen) {
                        closeLyricsCandidatePicker()
                        return
                    }
                    if (isFullscreenLyricsVisible) {
                        hideFullscreenLyrics()
                        return
                    }
                    if (isEditMode) {
                        exitEditMode()
                        return
                    }
                    if (isLyricsVisible) {
                        hideLyrics()
                        return
                    }
                    if (isQueueVisible) {
                        hideQueue()
                        return
                    }
                    val cfg = resources.configuration
                    if (userOpenedFullPlayer && MobilePlayerLayoutPolicy.isPortraitPhone(cfg.screenWidthDp, cfg.screenHeightDp)) {
                        userOpenedFullPlayer = false
                        applyMobilePlayerVisibility(isBrowsing = true)
                        return
                    }
                    if (!navManager.isAtRoot) {
                        goBack()
                        return
                    }
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            },
        )

        connectBrowser()
    }

    /**
     * Resolve the current WindowWidthClass from [Configuration.screenWidthDp].
     * This reflects the AVAILABLE window width (correct in FYT multi-window),
     * not the physical screen width.
     */
    private fun currentWidthClass(): WindowWidthClass =
        widthClassFor(resources.configuration.screenWidthDp)

    /**
     * Emit a structured log line for FYT field validation.
     * Example:
     *   MeloVaultLayout: window=384dp x 600dp class=COMPACT lifecycle=onCreate mediaId=track:123 state=3
     */
    private fun logWindowMetrics(lifecycle: String) {
        val cfg = resources.configuration
        val widthDp = cfg.screenWidthDp
        val heightDp = cfg.screenHeightDp
        val wClass = widthClassFor(widthDp)
        val orient = if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) "LAND" else "PORT"
        val mediaId = browser?.currentMediaItem?.mediaId ?: "none"
        val pbState = browser?.playbackState ?: -1
        Log.i(
            TAG_LAYOUT,
            "window=${widthDp}dp x ${heightDp}dp orient=$orient class=$wClass " +
                "lifecycle=$lifecycle mediaId=$mediaId playbackState=$pbState",
        )
    }

    private fun setupViews() {
        // Setup RecyclerView
        libraryAdapter = LibraryAdapter(
            onCategoryClick = { category ->
                if (isEditMode) exitEditMode()
                captureCurrentScroll()
                if (category.id == RemoteCatalog.DEVICE_LOCAL_ID ||
                    category.id == RemoteCatalog.DEVICE_LOCAL_ALL_ID ||
                    category.id == RemoteCatalog.DEVICE_LOCAL_FOLDERS_ID
                ) {
                    checkAndRequestLocalAudioPermission {
                        if (category.id == RemoteCatalog.DEVICE_LOCAL_FOLDERS_ID) {
                            handleDeviceLocalFoldersClick(category)
                        } else {
                            loadChildren(category.id, 0, category.title)
                        }
                    }
                } else {
                    loadChildren(category.id, 0, category.title)
                }
            },
            onFolderClick = { folder ->
                if (isEditMode) exitEditMode()
                if (folder.id == RemoteCatalog.NEW_PLAYLIST_ACTION) {
                    showCreatePlaylistDialog()
                } else {
                    captureCurrentScroll()
                    loadChildren(folder.id, 0, folder.title)
                }
            },
            onTrackClick = { item, tracks, index ->
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID || item.mediaItem.mediaMetadata.extras?.getBoolean("is_download_job") == true) {
                    handleTrackDownloadClick(item)
                } else {
                    playBrowseQueue(tracks, index)
                }
            },
            onTrackLongClick = { track ->
                showTrackActionSheet(track)
            },
            onFolderLongClick = { folder ->
                showFolderActionSheet(folder)
            },
            onStartDrag = { viewHolder ->
                libraryItemTouchHelper?.startDrag(viewHolder)
            },
            onSelectionChanged = { count ->
                updateEditSelectionUi(count)
            },
            onDownloadClick = { track ->
                handleTrackDownloadClick(track)
            },
        )
        val libraryTouchCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled(): Boolean = false
            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (libraryAdapter.isEditMode && libraryAdapter.canReorder && viewHolder is LibraryAdapter.TrackViewHolder) {
                    return ItemTouchHelper.UP or ItemTouchHelper.DOWN
                }
                return 0
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    dragInitialItems = libraryAdapter.getItems()
                    dragInitialTrackIds = libraryAdapter.getItems()
                        .filterIsInstance<LibraryItem.Track>()
                        .map { it.id }
                }
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val canMove = PlaylistReorderGuard.canMove(
                    isEditMode = libraryAdapter.isEditMode,
                    canReorder = libraryAdapter.canReorder,
                    fromIsTrack = viewHolder is LibraryAdapter.TrackViewHolder,
                    toIsTrack = target is LibraryAdapter.TrackViewHolder,
                )
                if (!canMove) return false
                return libraryAdapter.moveItem(from, to)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val currentParent = navManager.currentParentId
                val playlistId = if (currentParent.startsWith(RemoteCatalog.PLAYLIST_PREFIX)) {
                    currentParent.removePrefix(RemoteCatalog.PLAYLIST_PREFIX).toIntOrNull()
                } else null

                val currentTrackIds = libraryAdapter.getItems()
                    .filterIsInstance<LibraryItem.Track>()
                    .map { it.id }
                val initialTrackIds = dragInitialTrackIds ?: currentTrackIds
                val snapshot = dragInitialItems ?: libraryAdapter.getItems()

                when (val decision = PlaylistReorderGuard.evaluateReorder(
                    playlistId = playlistId,
                    initialTrackIds = initialTrackIds,
                    currentTrackIds = currentTrackIds,
                    isPagingLoading = pagingPolicy.isLoading,
                )) {
                    is PlaylistReorderGuard.Decision.NoOp -> {
                        // Drag was a no-op or not in a playlist; no action needed
                    }
                    is PlaylistReorderGuard.Decision.RejectedInFlightPaging -> {
                        Toast.makeText(this@MainActivity, decision.reason, Toast.LENGTH_SHORT).show()
                        libraryAdapter.submitItems(snapshot)
                    }
                    is PlaylistReorderGuard.Decision.RejectedDuplicateTracks -> {
                        Toast.makeText(this@MainActivity, decision.reason, Toast.LENGTH_SHORT).show()
                        libraryAdapter.submitItems(snapshot)
                    }
                    is PlaylistReorderGuard.Decision.Proceed -> {
                        uiExecutor.submit {
                            val ok = runCatching {
                                uiCatalog.reorderPlaylist(decision.playlistId, decision.trackIds, decision.offset)
                            }.getOrDefault(false)
                            runOnUiThread {
                                if (!ok) {
                                    Toast.makeText(this@MainActivity, "Không thể lưu thứ tự danh sách phát", Toast.LENGTH_SHORT).show()
                                    libraryAdapter.submitItems(snapshot)
                                }
                            }
                        }
                    }
                }
                dragInitialItems = null
                dragInitialTrackIds = null
            }
        }
        libraryItemTouchHelper = ItemTouchHelper(libraryTouchCallback).also {
            it.attachToRecyclerView(binding.recyclerView)
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = libraryAdapter
            setHasFixedSize(true)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        triggerVisibleEnrichment()
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val total = lm.itemCount
                    val lastVisible = lm.findLastVisibleItemPosition()
                    if (pagingPolicy.shouldFetchOnScroll(dy, lastVisible, total)) {
                        loadNextPage()
                    }
                }
            })
        }

        enrichmentController = ProgressiveEnrichmentController(
            catalog = uiCatalog,
            executor = uiExecutor,
            mainHandler = mainHandler,
            onUpdateTrack = { mediaId, summary ->
                libraryAdapter.updateTrackTechnicalInfo(mediaId, summary)
            },
            getCurrentItems = { libraryAdapter.getItems() },
        )

        // Setup QueueAdapter & ItemTouchHelper for drag reorder
        var itemTouchHelper: androidx.recyclerview.widget.ItemTouchHelper? = null
        queueAdapter = QueueAdapter(
            onTrackClick = { queueTrack ->
                val player = browser ?: return@QueueAdapter
                Log.i(
                    TAG,
                    "QUEUE_TAP_PLAY: index=${queueTrack.queueIndex} mediaId=${queueTrack.mediaItem.mediaId} isCurrent=${queueTrack.isCurrent}",
                )
                if (queueTrack.isCurrent) {
                    if (player.isPlaying) player.pause() else player.play()
                } else {
                    player.seekToDefaultPosition(queueTrack.queueIndex)
                    player.play()
                }
            },
            onRemoveClick = { queueTrack ->
                removeQueueItem(queueTrack.queueIndex)
            },
            onStartDrag = { viewHolder ->
                android.util.Log.i("QueueDrag", "MainActivity onStartDrag called for pos=${viewHolder.bindingAdapterPosition}")
                itemTouchHelper?.startDrag(viewHolder)
            },
            onReturnToLibraryClick = {
                hideQueue()
            },
        )
        val touchCallback = com.bsdung.musicassistantcar.ui.adapter.QueueItemTouchHelperCallback(
            adapter = queueAdapter,
            getCurrentIndex = { browser?.currentMediaItemIndex ?: -1 },
            onMoveCommitted = { fromQueueIndex, toQueueIndex ->
                android.util.Log.i("QueueDrag", "MainActivity onMoveCommitted: from=$fromQueueIndex, to=$toQueueIndex")
                val player = browser ?: return@QueueItemTouchHelperCallback
                val currentIndex = player.currentMediaItemIndex
                val queueSize = player.mediaItemCount
                if (QueueHelper.canMoveUpcoming(currentIndex, fromQueueIndex, toQueueIndex, queueSize)) {
                    android.util.Log.i("QueueDrag", "Executing player.moveMediaItem($fromQueueIndex, $toQueueIndex)")
                    player.moveMediaItem(fromQueueIndex, toQueueIndex)
                    renderQueue()
                    schedulePrewarmQueue(buildCurrentQueueList(), player.currentMediaItemIndex)
                } else {
                    android.util.Log.w("QueueDrag", "Rejected move: currentIndex=$currentIndex, from=$fromQueueIndex, to=$toQueueIndex, queueSize=$queueSize")
                }
            },
        )
        itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(touchCallback).also {
            it.attachToRecyclerView(binding.queueRecyclerView)
        }
        binding.queueRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = queueAdapter
            setHasFixedSize(true)
        }

        // Setup SyncedLyricsAdapter & LyricsCandidateAdapter (inline + fullscreen car mode)
        val handleLyricLineTapped: (com.bsdung.musicassistantcar.lyrics.LyricsLine) -> Unit = { line ->
            val player = browser
            if (player != null && currentLyricsUiState is LyricsUiState.Synced) {
                Log.i(TAG, "LYRICS_TAP_SEEK: startMs=${line.startMs} fullscreen=$isFullscreenLyricsVisible")
                player.seekTo(line.startMs)
                isLyricsAutoFollow = true
                updateLyricsAutoFollowButtonUi()
                syncActiveLyricsLineWithPlayer(forceScroll = true, targetPositionMs = line.startMs)
            }
        }
        syncedLyricsAdapter = SyncedLyricsAdapter(
            fullscreenMode = false,
            onLineClick = handleLyricLineTapped,
        )
        fullscreenSyncedLyricsAdapter = SyncedLyricsAdapter(
            fullscreenMode = true,
            onLineClick = handleLyricLineTapped,
        )
        binding.lyricsSyncedRecyclerView?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = syncedLyricsAdapter
            itemAnimator = null
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING && isLyricsAutoFollow) {
                        Log.i(TAG, "LYRICS_MANUAL_SCROLL: autoFollow=false fullscreen=false")
                        isLyricsAutoFollow = false
                        updateLyricsAutoFollowButtonUi()
                    }
                }
            })
        }
        binding.fullscreenLyricsSyncedRecyclerView?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = fullscreenSyncedLyricsAdapter
            itemAnimator = null
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING && isLyricsAutoFollow) {
                        Log.i(TAG, "LYRICS_MANUAL_SCROLL: autoFollow=false fullscreen=true")
                        isLyricsAutoFollow = false
                        updateLyricsAutoFollowButtonUi()
                    }
                }
            })
        }

        val handleCandidateSelected: (LyricsCandidate) -> Unit = { candidate ->
            selectManualLyricsCandidate(candidate)
        }
        lyricsCandidateAdapter = LyricsCandidateAdapter(onCandidateClick = handleCandidateSelected)
        fullscreenLyricsCandidateAdapter = LyricsCandidateAdapter(onCandidateClick = handleCandidateSelected)
        binding.lyricsCandidatesRecyclerView?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = lyricsCandidateAdapter
        }
        binding.fullscreenLyricsCandidatesRecyclerView?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = fullscreenLyricsCandidateAdapter
        }

        // Setup ProgressController
        progressController = ProgressController(
            seekBar = binding.seekBar,
            currentTimeView = binding.currentTimeView,
            totalTimeView = binding.totalTimeView,
            onSeekTo = { targetMs ->
                browser?.seekTo(targetMs)
                if (isAnyLyricsVisible() && currentLyricsUiState is LyricsUiState.Synced) {
                    syncActiveLyricsLineWithPlayer(forceScroll = isLyricsAutoFollow, targetPositionMs = targetMs)
                }
            },
        )

        // Setup Controls
        val handlePlayPauseToggle: () -> Unit = {
            browser?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                    } else if (player.playbackState == Player.STATE_ENDED) {
                        player.seekToDefaultPosition(0)
                    }
                    player.play()
                }
            }
        }
        binding.playPauseButton.setOnClickListener {
            handlePlayPauseToggle()
        }

        // Album-art gestures: left/right skips a track, tap opens the queue.
        // Vertical drags yield to the parent scroller, avoiding accidental skips.
        binding.coverView.setOnClickListener { toggleQueue() }
        val artworkSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        val artworkSwipeThreshold = 64f * resources.displayMetrics.density
        var artworkDownX = 0f
        var artworkDownY = 0f
        binding.coverView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    artworkDownX = event.x
                    artworkDownY = event.y
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - artworkDownX
                    val dy = event.y - artworkDownY
                    if (kotlin.math.abs(dy) > artworkSlop &&
                        kotlin.math.abs(dy) > kotlin.math.abs(dx)
                    ) {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    when (ArtworkGesturePolicy.classify(
                        event.x - artworkDownX,
                        event.y - artworkDownY,
                        artworkSwipeThreshold,
                        artworkSlop,
                    )) {
                        ArtworkGestureAction.OPEN_QUEUE -> view.performClick()
                        ArtworkGestureAction.NEXT_TRACK -> browser?.seekToNextMediaItem()
                        ArtworkGestureAction.PREVIOUS_TRACK -> browser?.seekToPreviousMediaItem()
                        ArtworkGestureAction.IGNORE -> Unit
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }

        binding.previousButton.setOnClickListener {
            browser?.seekToPreviousMediaItem()
        }

        binding.nextButton.setOnClickListener {
            browser?.seekToNextMediaItem()
        }

        binding.shuffleButton.setOnClickListener {
            val player = browser ?: return@setOnClickListener
            player.shuffleModeEnabled = !player.shuffleModeEnabled
            updateNowPlaying()
        }

        binding.repeatButton.setOnClickListener {
            val player = browser ?: return@setOnClickListener
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            updateNowPlaying()
        }

        // Favorite Button in Now Playing
        binding.favoriteButton.setOnClickListener {
            val mediaId = browser?.currentMediaItem?.mediaId ?: return@setOnClickListener
            val oldState = currentFavoriteState ?: false
            val optimisticState = !oldState
            currentFavoriteState = optimisticState
            updateFavoriteUi(optimisticState)
            uiExecutor.submit {
                val result = runCatching {
                    uiCatalog.toggleFavorite(mediaId)
                }
                runOnUiThread {
                    result.onSuccess { serverState ->
                        currentFavoriteState = serverState
                        updateFavoriteUi(serverState)
                    }.onFailure {
                        currentFavoriteState = oldState
                        updateFavoriteUi(oldState)
                        Toast.makeText(
                            this@MainActivity,
                            "Không thể cập nhật Yêu thích",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }

        // Quality Chip
        updateQualityChip()
        binding.qualityChip.setOnClickListener {
            toggleQuality()
        }

        // Signal Path
        binding.signalPathButton?.setOnClickListener {
            showSignalPathSheet()
        }
        binding.signalPathButton?.setOnLongClickListener {
            showLoudnessSettingsDialog()
            true
        }
        binding.technicalInfoView.setOnClickListener {
            showSignalPathSheet()
        }

        // Up Next / Queue Button
        binding.queueButton.setOnClickListener {
            toggleQueue()
        }

        // Poweramp-inspired direct destinations: no hidden swipe/menu required.
        // These call existing navigation paths, preserving Queue and scroll state.
        binding.quickQueueButton.setOnClickListener { toggleQueue() }
        binding.quickLibraryButton.setOnClickListener {
            if (isQueueVisible) hideQueue()
            if (isLyricsVisible) hideLyrics()
            userOpenedFullPlayer = false
            applyMobilePlayerVisibility(isBrowsing = true)
            binding.recyclerView.scrollToPosition(0)
        }
        binding.quickSourcesButton.setOnClickListener { showMusicAccountsDialog() }
        binding.quickPlaylistsButton.setOnClickListener {
            if (isQueueVisible) hideQueue()
            if (isLyricsVisible) hideLyrics()
            userOpenedFullPlayer = false
            applyMobilePlayerVisibility(isBrowsing = true)
            handleQuickFilterClick(RemoteCatalog.PLAYLISTS_ID, "Danh sách phát")
            binding.recyclerView.scrollToPosition(0)
        }

        // Mini player bar tap/expand and play/pause
        binding.miniPlayerBar?.setOnClickListener {
            userOpenedFullPlayer = true
            applyMobilePlayerVisibility(isBrowsing = false)
        }
        binding.miniExpandButton?.setOnClickListener {
            userOpenedFullPlayer = true
            applyMobilePlayerVisibility(isBrowsing = false)
        }
        binding.miniPlayPauseButton?.setOnClickListener {
            handlePlayPauseToggle()
        }

        binding.queueBackButton.setOnClickListener {
            hideQueue()
        }

        binding.queueClearButton.setOnClickListener {
            clearUpcoming()
        }
        binding.queueSavePlaylistButton.setOnClickListener {
            saveQueueAsPlaylist()
        }

        // Lyrics Button & Panel Controls (Inline + Fullscreen Car UX)
        binding.lyricsButton?.setOnClickListener {
            toggleLyrics()
        }
        binding.lyricsButton?.setOnLongClickListener {
            showFullscreenLyrics()
            true
        }

        binding.accountsButton?.setOnClickListener {
            showMusicAccountsDialog()
        }
        binding.lyricsBackButton?.setOnClickListener {
            if (isCandidatePickerOpen) {
                closeLyricsCandidatePicker()
            } else {
                hideLyrics()
            }
        }
        binding.lyricsExpandFullscreenButton?.setOnClickListener {
            showFullscreenLyrics()
        }
        binding.lyricsHeaderTitleContainer?.setOnClickListener {
            showFullscreenLyrics()
        }
        binding.lyricsAutoFollowButton?.setOnClickListener {
            Log.i(TAG, "LYRICS_FOLLOW_RESUMED: fullscreen=false")
            isLyricsAutoFollow = true
            updateLyricsAutoFollowButtonUi()
            val activeIdx = syncedLyricsAdapter.activeLineIndex
            if (activeIdx >= 0) {
                scrollToActiveLyricLine(activeIdx, smooth = true)
            } else {
                syncActiveLyricsLineWithPlayer(forceScroll = true)
            }
        }
        binding.lyricsRefreshButton?.setOnClickListener {
            loadLyricsForCurrentTrack(forceRefresh = true)
        }
        binding.lyricsChooseOtherButton?.setOnClickListener {
            openLyricsCandidatePicker()
        }
        binding.lyricsAutoResetButton?.setOnClickListener {
            resetLyricsToAutomatic()
        }
        binding.lyricsCandidatesCloseButton?.setOnClickListener {
            closeLyricsCandidatePicker()
        }
        binding.lyricsStatusActionButton?.setOnClickListener {
            if (isCandidatePickerOpen) {
                if (currentCandidatePickerState is LyricsCandidatePickerState.Empty) {
                    closeLyricsCandidatePicker()
                } else {
                    openLyricsCandidatePicker()
                }
            } else {
                val isTempErr = currentLyricsUiState is LyricsUiState.TemporaryError
                loadLyricsForCurrentTrack(forceRefresh = !isTempErr)
            }
        }
        binding.lyricsStatusSecondaryButton?.setOnClickListener {
            if (isCandidatePickerOpen) {
                closeLyricsCandidatePicker()
            } else {
                openLyricsCandidatePicker()
            }
        }

        // Fullscreen Car Lyrics Overlay Controls
        binding.fullscreenLyricsBackButton?.setOnClickListener {
            if (isCandidatePickerOpen) {
                closeLyricsCandidatePicker()
            } else {
                hideFullscreenLyrics()
            }
        }
        binding.fullscreenLyricsRefreshButton?.setOnClickListener {
            loadLyricsForCurrentTrack(forceRefresh = true)
        }
        binding.fullscreenLyricsChooseOtherButton?.setOnClickListener {
            openLyricsCandidatePicker()
        }
        binding.fullscreenLyricsAutoResetButton?.setOnClickListener {
            resetLyricsToAutomatic()
        }
        binding.fullscreenLyricsCandidatesCloseButton?.setOnClickListener {
            closeLyricsCandidatePicker()
        }
        binding.fullscreenLyricsStatusActionButton?.setOnClickListener {
            if (isCandidatePickerOpen) {
                if (currentCandidatePickerState is LyricsCandidatePickerState.Empty) {
                    closeLyricsCandidatePicker()
                } else {
                    openLyricsCandidatePicker()
                }
            } else {
                val isTempErr = currentLyricsUiState is LyricsUiState.TemporaryError
                loadLyricsForCurrentTrack(forceRefresh = !isTempErr)
            }
        }
        binding.fullscreenLyricsStatusSecondaryButton?.setOnClickListener {
            if (isCandidatePickerOpen) {
                closeLyricsCandidatePicker()
            } else {
                openLyricsCandidatePicker()
            }
        }
        binding.fullscreenLyricsFollowButton?.setOnClickListener {
            Log.i(TAG, "LYRICS_FOLLOW_RESUMED: fullscreen=true")
            isLyricsAutoFollow = true
            updateLyricsAutoFollowButtonUi()
            val activeIdx = fullscreenSyncedLyricsAdapter.activeLineIndex
            if (activeIdx >= 0) {
                scrollToActiveLyricLine(activeIdx, smooth = true)
            } else {
                syncActiveLyricsLineWithPlayer(forceScroll = true)
            }
        }
        binding.fullscreenLyricsPreviousButton?.setOnClickListener {
            browser?.seekToPreviousMediaItem()
        }
        binding.fullscreenLyricsPlayPauseButton?.setOnClickListener {
            handlePlayPauseToggle()
        }
        binding.fullscreenLyricsNextButton?.setOnClickListener {
            browser?.seekToNextMediaItem()
        }

        // Connection Dot
        binding.connectionContainer.setOnClickListener {
            val message = if (activeBackendUrl != null) {
                "Đã kết nối máy chủ:\n$activeBackendUrl"
            } else {
                "Chưa kết nối máy chủ.\nKiểm tra Tailscale hoặc mạng Wi‑Fi nội bộ."
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // Search Bar
        binding.searchClearButton.setOnClickListener {
            searchDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
            clearSearchInputSilently()
            if (navManager.currentParentId == "search") {
                goBack()
            }
        }

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
                runSearch(binding.searchInput.text?.toString().orEmpty())
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
                true
            } else {
                false
            }
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isSuppressingSearchWatcher) return
                val query = NavigationManager.normalizeSearchQuery(s?.toString().orEmpty())
                binding.searchClearButton.visibility = if (query.isNotBlank()) View.VISIBLE else View.GONE
                searchDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
                if (query.isNotBlank()) {
                    val r = Runnable { runSearch(query) }
                    searchDebounceRunnable = r
                    mainHandler.postDelayed(r, 300)
                } else if (navManager.currentParentId == "search") {
                    val r = Runnable {
                        if (navManager.currentParentId == "search") {
                            goBack()
                        }
                    }
                    searchDebounceRunnable = r
                    mainHandler.postDelayed(r, 300)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Quick Filter Chips (Car Library & Queue UX v0.6.32)
        binding.filterRootChip?.setOnClickListener {
            handleQuickFilterClick(RemoteCatalog.ROOT_ID, "Thư viện")
        }
        binding.filterAllChip?.setOnClickListener {
            handleQuickFilterClick(RemoteCatalog.ALL_ID, "Tất cả bài hát")
        }
        binding.filterDownloadedChip?.setOnClickListener {
            handleQuickFilterClick(RemoteCatalog.DOWNLOADED_ID, "Đã tải")
        }
        binding.filterDeviceChip?.setOnClickListener {
            handleQuickFilterClick(RemoteCatalog.DEVICE_LOCAL_ID, "Thiết bị")
        }
        binding.filterDriveChip?.setOnClickListener {
            handleQuickFilterClick(RemoteCatalog.DRIVE_ID, "Google Drive")
        }
        binding.filterPlaylistsChip?.setOnClickListener {
            handleQuickFilterClick(RemoteCatalog.PLAYLISTS_ID, "Danh sách phát")
        }

        // Breadcrumbs & Navigation
        binding.backButton.setOnClickListener {
            goBack()
        }

        binding.retryButton.setOnClickListener {
            if (navManager.searchQuery.isNotBlank()) {
                runSearch(navManager.searchQuery, navManager.currentPage)
            } else {
                loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
            }
        }

        binding.previousPageButton.setOnClickListener {
            if (navManager.currentPage > 0) {
                if (navManager.currentParentId == "search") {
                    runSearch(navManager.searchQuery, navManager.currentPage - 1)
                } else {
                    loadChildren(navManager.currentParentId, navManager.currentPage - 1, navManager.currentLabel)
                }
            }
        }

        binding.nextPageButton.setOnClickListener {
            if (navManager.currentParentId == "search") {
                runSearch(navManager.searchQuery, navManager.currentPage + 1)
            } else {
                loadChildren(navManager.currentParentId, navManager.currentPage + 1, navManager.currentLabel)
            }
        }

        binding.collectionSortButton.setOnClickListener {
            showSortDialog()
        }

        // Playlist Editor & Multi-Select Bar
        binding.playlistEditButton.setOnClickListener {
            enterEditMode(allowReorder = true)
        }

        binding.editSelectAllButton.setOnClickListener {
            libraryAdapter.selectAllTracks()
        }

        binding.editDoneButton.setOnClickListener {
            exitEditMode()
        }

        binding.editAddToPlaylistButton.setOnClickListener {
            val selected = libraryAdapter.selectedMediaIds.toList()
            if (selected.isNotEmpty()) {
                showPlaylistChooserForTracks(selected)
            }
        }

        binding.editRemoveButton.setOnClickListener {
            val selected = libraryAdapter.selectedMediaIds.toList()
            if (selected.isEmpty()) {
                Toast.makeText(this, "Chưa chọn bài hát nào", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (navManager.currentParentId == RemoteCatalog.DOWNLOADED_ID) {
                showMultiDeleteOfflineConfirmation(selected)
                return@setOnClickListener
            }
            val playlistId = navManager.currentParentId.removePrefix(RemoteCatalog.PLAYLIST_PREFIX).toIntOrNull() ?: return@setOnClickListener
            if (selected.size == 1) {
                performBatchRemove(playlistId, selected)
            } else {
                AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                    .setTitle("Xác nhận xóa")
                    .setMessage("Xóa ${selected.size} bài hát khỏi danh sách phát?")
                    .setPositiveButton("Xóa") { _, _ ->
                        performBatchRemove(playlistId, selected)
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
        }

        binding.btnOfflineStorageTarget.setOnClickListener {
            showOfflineStorageTargetDialog()
        }
        binding.txtOfflineStorageTarget.setOnClickListener {
            showOfflineStorageTargetDialog()
        }
        binding.offlineStorageTargetRow.setOnClickListener {
            showOfflineStorageTargetDialog()
        }

        binding.btnOfflineStorageCap.setOnClickListener {
            showOfflineStorageCapDialog()
        }
        binding.txtOfflineStorageCap.setOnClickListener {
            showOfflineStorageCapDialog()
        }

        binding.btnOfflineNetworkPolicy.setOnClickListener {
            showDownloadNetworkPolicyDialog()
        }
        binding.txtOfflineNetworkPolicy.setOnClickListener {
            showDownloadNetworkPolicyDialog()
        }

        binding.btnOfflineMultiSelect.setOnClickListener {
            enterEditMode(allowReorder = false)
        }

        binding.btnDeleteAllOffline.setOnClickListener {
            showDeleteAllOfflineDialog()
        }

        binding.collectionPlayAllButton.setOnClickListener {
            triggerCollectionPlayback(shuffle = false)
        }

        binding.collectionShuffleButton.setOnClickListener {
            triggerCollectionPlayback(shuffle = true)
        }

        binding.collectionAddAllButton.setOnClickListener {
            triggerCollectionAddToQueue()
        }

        binding.collectionDownloadAllButton.setOnClickListener {
            triggerCollectionDownloadAll()
        }

        binding.collectionDeleteOfflineButton.setOnClickListener {
            triggerPlaylistDeleteOffline()
        }

        binding.btnPlaylistSyncToggle.setOnClickListener {
            handlePlaylistSyncToggleClicked()
        }

        binding.btnPlaylistSyncNow.setOnClickListener {
            handlePlaylistSyncNowClicked()
        }

        binding.btnCancelPendingQueue.setOnClickListener {
            val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
            val cancelled = dm.cancelPendingQueue()
            Toast.makeText(this, "Đã hủy các bài trong hàng đợi ($cancelled bài)", Toast.LENGTH_SHORT).show()
            updateDownloadQueueHeader()
            updatePlaylistSyncBarPassive()
            if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
            }
        }

        binding.btnCancelAllDownloads.setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                .setTitle("Hủy tất cả lượt tải?")
                .setMessage("Dừng lượt tải đang thực hiện và hủy toàn bộ hàng đợi? Các bài hát đã tải hoàn tất trước đó sẽ không bị xóa.")
                .setPositiveButton("Hủy tất cả") { _, _ ->
                    val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
                    val cancelled = dm.cancelAllJobs()
                    Toast.makeText(this, "Đã hủy toàn bộ lượt tải ($cancelled bài)", Toast.LENGTH_SHORT).show()
                    updateDownloadQueueHeader()
                    updatePlaylistSyncBarPassive()
                    if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                        loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                    }
                }
                .setNegativeButton("Đóng", null)
                .show()
        }

        updateHeaderAndBreadcrumbs()
        com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this).addListener(downloadListener)
    }


    private fun connectBrowser() {
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaBrowser.Builder(this, token).buildAsync()
        browserFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaBrowser ->
                        browser = mediaBrowser
                        mediaBrowser.addListener(playerListener)
                        progressController.attachPlayer(mediaBrowser)
                        refreshBackendStatus()
                        updateNowPlaying()
                        if (isLyricsVisible) {
                            showLyrics()
                        } else if (isQueueVisible) {
                            showQueue()
                        }
                        if (isFullscreenLyricsVisible) {
                            showFullscreenLyrics()
                        }
                        if (navManager.searchQuery.isNotBlank()) {
                            runSearch(navManager.searchQuery, navManager.currentPage)
                        } else {
                            loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                        }
                        triggerStartupPlaylistSyncOnce()
                    }
                    .onFailure { exc ->
                        binding.statusDot.setImageResource(R.drawable.bg_status_dot_offline)
                        binding.statusText.text = "Lỗi Session"
                        Toast.makeText(this, "Lỗi MediaSession: ${exc.message}", Toast.LENGTH_LONG).show()
                    }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun loadRoot() {
        if (isEditMode) exitEditMode()
        navManager.resetToRoot()
        clearSearchInputSilently()
        loadChildren(RemoteCatalog.ROOT_ID, 0, "Thư viện")
    }

    private fun goBack() {
        if (isEditMode) {
            exitEditMode()
            return
        }
        searchDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
        val wasInSearch = navManager.currentParentId == "search"
        val previous = navManager.goBack()
        if (wasInSearch) {
            clearSearchInputSilently()
        }
        if (previous == null) {
            loadRoot()
            return
        }
        Log.i(
            TAG,
            "NAV_BACK: fromSearch=$wasInSearch toParent=${previous.parentId} page=${previous.page} " +
                "restorePos=${previous.firstVisiblePosition} restoreOffset=${previous.topOffsetPx}",
        )
        pendingRestorePosition = previous.firstVisiblePosition
        pendingRestoreOffset = previous.topOffsetPx
        loadChildren(previous.parentId, previous.page, previous.label, previous.sort)
    }

    private fun loadChildren(
        parentId: String,
        page: Int,
        label: String,
        sort: String = navManager.currentSort,
        reconcilePlaylistSync: Boolean = true,
    ) {
        if (isEditMode) exitEditMode()
        val mediaBrowser = browser
        if (mediaBrowser == null) {
            Log.w(TAG, "loadChildren: MediaBrowser not ready, skipping parentId=$parentId")
            return
        }
        if (navManager.currentParentId == "search" && parentId != "search") {
            clearSearchInputSilently()
        }
        navManager.navigateTo(parentId, label, page, sort)
        if (parentId.startsWith(RemoteCatalog.DEVICE_FOLDER_PREFIX)) {
            val payload = parentId.removePrefix(RemoteCatalog.DEVICE_FOLDER_PREFIX)
            val colonIdx = payload.indexOf(':')
            val vol = if (colonIdx >= 0) payload.substring(0, colonIdx) else payload
            val path = if (colonIdx >= 0) payload.substring(colonIdx + 1) else ""
            com.bsdung.musicassistantcar.data.LocalMediaRepository.getDefault(this).saveLastBrowsedFolder(vol, path)
        } else if (parentId == RemoteCatalog.DEVICE_LOCAL_FOLDERS_ID) {
            com.bsdung.musicassistantcar.data.LocalMediaRepository.getDefault(this).clearLastBrowsedFolder()
        }
        updateHeaderAndBreadcrumbs()

        if (page == 0) {
            pagingGeneration = pagingPolicy.reset()
            libraryAdapter.hideFooter()
        }

        binding.loadingView.visibility = if (isQueueVisible || isLyricsVisible) View.GONE else View.VISIBLE
        binding.emptyView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE

        val requestId = ++currentLoadRequestId
        enrichmentController.nextGeneration()
        Log.i(TAG, "ROOT REQUEST START: parentId=$parentId, page=$page, label=$label, sort=$sort, reqId=$requestId")
        Log.i(TAG, "CURRENT PARENT ID: ${navManager.currentParentId}")

        val libraryParams = androidx.media3.session.MediaLibraryService.LibraryParams.Builder().apply {
            val bundle = Bundle().apply {
                putString("sort", sort)
            }
            setExtras(bundle)
        }.build()

        val future = mediaBrowser.getChildren(parentId, page, pageSize, libraryParams)
        future.addListener(
            {
                Log.i(TAG, "REQUEST GENERATION: requestId=$requestId, currentGeneration=$currentLoadRequestId, parentId=$parentId")
                if (requestId != currentLoadRequestId) {
                    Log.w(TAG, "DISCARD STALE REQUEST: reqId=$requestId != current=$currentLoadRequestId")
                    return@addListener
                }
                runCatching { future.get() }
                    .onSuccess { result ->
                        val code = result.resultCode
                        Log.i(TAG, "ROOT RESULT: parentId=$parentId, resultCode=$code, valueIsNull=${result.value == null}")
                        if (code != androidx.media3.session.LibraryResult.RESULT_SUCCESS) {
                            Log.e(TAG, "ROOT RESULT ERROR CODE: $code")
                            binding.loadingView.visibility = View.GONE
                            binding.recyclerView.visibility = View.GONE
                            binding.emptyView.visibility = View.GONE
                            binding.errorView.visibility = View.VISIBLE
                            binding.errorTitle.text = "Không tải được thư viện"
                            binding.errorSubtitle.text = "Kiểm tra kết nối rồi thử lại."
                            binding.errorView.setOnLongClickListener {
                                Toast.makeText(this, "Mã lỗi: $code", Toast.LENGTH_SHORT).show()
                                true
                            }
                            return@onSuccess
                        }

                        val rawItems = result.value.orEmpty()
                        Log.i(TAG, "ROOT RESULT COUNT: ${rawItems.size}")
                        Log.i(TAG, "ROOT MEDIA IDS: ${rawItems.map { it.mediaId }}")

                        val firstExtras = rawItems.firstOrNull()?.mediaMetadata?.extras
                        val total = firstExtras?.getInt("collection_total", -1) ?: -1
                        val trackCount = if (firstExtras != null && firstExtras.containsKey("collection_track_count")) {
                            firstExtras.getInt("collection_track_count")
                        } else null
                        val folderCount = if (firstExtras != null && firstExtras.containsKey("collection_folder_count")) {
                            firstExtras.getInt("collection_folder_count")
                        } else null
                        if (total >= 0 || trackCount != null || folderCount != null) {
                            navManager.setCollectionCounts(
                                total = if (total >= 0) total else null,
                                trackCount = trackCount,
                                folderCount = folderCount,
                            )
                        }
                        updateHeaderAndBreadcrumbs()

                        val uiItems = mapToUiItems(rawItems)
                        Log.i(TAG, "MAPPED LIBRARY COUNT: ${uiItems.size}")

                        if (page == 0) {
                            pagingPolicy.onPageSuccess(
                                callbackGeneration = pagingGeneration,
                                newItemsCount = uiItems.size,
                                totalInCollection = total,
                                loadedItemsCount = uiItems.size,
                            )
                        }

                        renderItems(uiItems)
                        Log.i(TAG, "ADAPTER ITEM COUNT: ${libraryAdapter.itemCount}")
                        if (parentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX) && page == 0) {
                            reconcileOpenedPlaylistIfNeeded(parentId, label, triggerSync = reconcilePlaylistSync)
                        }
                    }
                    .onFailure { exc ->
                        Log.e(TAG, "ROOT REQUEST FAILED: ${exc.message}", exc)
                        binding.loadingView.visibility = View.GONE
                        binding.recyclerView.visibility = View.GONE
                        binding.emptyView.visibility = View.GONE
                        binding.errorView.visibility = View.VISIBLE
                        binding.errorTitle.text = "Không tải được thư viện"
                        binding.errorSubtitle.text = "Kiểm tra kết nối rồi thử lại."
                        binding.errorView.setOnLongClickListener {
                            Toast.makeText(this, "Lỗi: ${exc.message}", Toast.LENGTH_SHORT).show()
                            true
                        }
                    }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun runSearch(query: String, page: Int = 0) {
        val cleanQuery = NavigationManager.normalizeSearchQuery(query)
        if (cleanQuery.isBlank()) {
            if (navManager.currentParentId == "search") {
                goBack()
            }
            return
        }

        if (page == 0 &&
            !navManager.shouldExecuteSearch(cleanQuery, page) &&
            binding.errorView.visibility != View.VISIBLE &&
            binding.loadingView.visibility != View.VISIBLE
        ) {
            Log.i(TAG, "SEARCH_DEDUP_SKIP: query='$cleanQuery' page=$page")
            return
        }

        if (page == 0) {
            if (navManager.currentParentId != "search") {
                captureCurrentScroll()
            }
            navManager.startSearch(cleanQuery)
        } else {
            navManager.setPage(page)
        }
        updateHeaderAndBreadcrumbs()

        if (page == 0) {
            pagingGeneration = pagingPolicy.reset()
            libraryAdapter.hideFooter()
        }

        binding.loadingView.visibility = if (isQueueVisible || isLyricsVisible) View.GONE else View.VISIBLE
        binding.emptyView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE

        val requestId = ++currentLoadRequestId
        enrichmentController.nextGeneration()
        Log.i(TAG, "SEARCH_EXECUTE: query='$cleanQuery' page=$page reqId=$requestId")

        uiExecutor.submit {
            val result = runCatching {
                uiCatalog.search(cleanQuery, page, pageSize)
            }
            runOnUiThread {
                if (requestId != currentLoadRequestId) return@runOnUiThread
                result
                    .onSuccess { rawItems ->
                        val uiItems = mapToUiItems(rawItems)
                        if (page == 0) {
                            pagingPolicy.onPageSuccess(
                                callbackGeneration = pagingGeneration,
                                newItemsCount = uiItems.size,
                            )
                        }
                        renderItems(uiItems)
                    }
                    .onFailure { exc ->
                        binding.loadingView.visibility = View.GONE
                        binding.recyclerView.visibility = View.GONE
                        binding.emptyView.visibility = View.GONE
                        binding.errorView.visibility = View.VISIBLE
                        binding.errorTitle.text = "Không tải được kết quả tìm kiếm"
                        binding.errorSubtitle.text = "Kiểm tra kết nối rồi thử lại."
                        binding.errorView.setOnLongClickListener {
                            Toast.makeText(this, "Lỗi: ${exc.message}", Toast.LENGTH_SHORT).show()
                            true
                        }
                    }
            }
        }
    }

    private fun mapToUiItems(rawItems: List<MediaItem>): List<LibraryItem> {
        val currentMediaId = browser?.currentMediaItem?.mediaId
        val isPlaying = browser?.isPlaying == true

        return rawItems.map { item ->
            val id = item.mediaId
            val metadata = item.mediaMetadata
            val title = metadata.title?.toString() ?: id
            val subtitle = metadata.subtitle?.toString()
                ?: metadata.artist?.toString()
                ?: ""

            when {
                id.startsWith("source:") || id.startsWith("category:") -> {
                    val iconRes = when (id) {
                        "category:device_local" -> R.drawable.ic_phone_android
                        "category:device_local_all" -> R.drawable.ic_music_note
                        "category:device_local_folders" -> R.drawable.ic_folder
                        "category:downloaded" -> R.drawable.ic_download_done
                        "category:recent" -> R.drawable.ic_history
                        "source:all" -> R.drawable.ic_library_music
                        "source:drive" -> R.drawable.ic_cloud
                        "source:telegram" -> R.drawable.ic_send
                        "category:playlists" -> R.drawable.ic_playlist
                        else -> R.drawable.ic_music_note
                    }
                    LibraryItem.Category(
                        id = id,
                        title = title,
                        subtitle = subtitle,
                        iconResId = iconRes,
                        mediaItem = item,
                    )
                }

                metadata.isBrowsable == true -> {
                    LibraryItem.Folder(
                        id = id,
                        title = MetadataDisplayHelper.cleanPlaylistName(title),
                        subtitle = subtitle,
                        mediaItem = item,
                    )
                }

                else -> {
                    val isCurrent = id == currentMediaId
                    val extras = metadata.extras
                    val durationMs = extras?.getLong("duration_ms")?.takeIf { it > 0L }
                    val cleanArtist = MetadataDisplayHelper.cleanArtist(metadata.artist?.toString())
                    val cleanAlbum = MetadataDisplayHelper.cleanAlbum(metadata.albumTitle?.toString()).takeIf { it.isNotBlank() }
                    val format = extras?.getString("format")
                    val sizeBytes = if (extras != null && extras.containsKey("size_bytes")) extras.getLong("size_bytes") else null
                    val codec = extras?.getString("codec")
                    val bitrate = if (extras != null && extras.containsKey("bitrate")) extras.getInt("bitrate") else null
                    val sampleRate = if (extras != null && extras.containsKey("sample_rate")) extras.getInt("sample_rate") else null
                    val channels = if (extras != null && extras.containsKey("channels")) extras.getInt("channels") else null
                    LibraryItem.Track(
                        id = id,
                        title = MetadataDisplayHelper.cleanTitle(title),
                        artist = cleanArtist,
                        album = cleanAlbum,
                        artworkUri = metadata.artworkUri?.toString(),
                        durationMs = durationMs,
                        isCurrent = isCurrent,
                        isPlaying = if (isCurrent) isPlaying else false,
                        mediaItem = item,
                        format = format,
                        sizeBytes = sizeBytes,
                        codec = codec,
                        bitrate = bitrate,
                        sampleRate = sampleRate,
                        channels = channels,
                    )
                }
            }
        }
    }

    private fun renderItems(uiItems: List<LibraryItem>) {
        binding.loadingView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        val overlayOpen = isQueueVisible || isLyricsVisible

        if (uiItems.isEmpty()) {
            libraryAdapter.submitItems(emptyList())
            binding.recyclerView.visibility = View.GONE
            binding.emptyView.visibility = if (overlayOpen) View.GONE else View.VISIBLE
            binding.emptyText.text = when {
                navManager.searchQuery.isNotBlank() -> "Không tìm thấy bài phù hợp"
                navManager.currentParentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX) -> "Danh sách phát chưa có bài hát"
                else -> "Chưa có bài hát trong thư mục này"
            }
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = if (overlayOpen) View.GONE else View.VISIBLE
            libraryAdapter.submitItems(uiItems)
            if (pendingRestorePosition != null) {
                val pos = pendingRestorePosition ?: 0
                val offset = pendingRestoreOffset ?: 0
                pendingRestorePosition = null
                pendingRestoreOffset = null
                (binding.recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pos, offset)
            } else if (savedRecyclerState != null) {
                binding.recyclerView.layoutManager?.onRestoreInstanceState(savedRecyclerState)
                savedRecyclerState = null
            } else {
                binding.recyclerView.scrollToPosition(0)
            }
            binding.recyclerView.post {
                triggerVisibleEnrichment()
            }
        }

        updateCollectionActionBar()
        updateDownloadQueueHeader()
        updateOfflineStorageDashboard()

        binding.pageBar.visibility = View.GONE
        binding.previousPageButton.visibility = View.GONE
        binding.nextPageButton.visibility = View.GONE
    }

    private fun loadNextPage(isRetry: Boolean = false) {
        val generation = pagingGeneration
        if (!pagingPolicy.startLoading(generation, isRetry = isRetry)) return
        if (navManager.isAtRoot) {
            pagingPolicy.onPageFailure(generation, "At root")
            return
        }
        val currentParent = navManager.currentParentId
        if (currentParent.isBlank()) {
            pagingPolicy.onPageFailure(generation, "Blank parent")
            return
        }

        val nextPage = pagingPolicy.currentLoadedPages
        libraryAdapter.showFooter(isError = false)

        if (currentParent == "search") {
            val query = navManager.searchQuery
            if (query.isBlank()) {
                pagingPolicy.onPageFailure(generation, "Blank query")
                libraryAdapter.hideFooter()
                return
            }
            uiExecutor.submit {
                val result = runCatching {
                    uiCatalog.search(query, nextPage, pageSize)
                }
                runOnUiThread {
                    if (generation != pagingGeneration) {
                        return@runOnUiThread
                    }
                    result.onSuccess { rawItems ->
                        val uiItems = mapToUiItems(rawItems)
                        if (uiItems.isEmpty()) {
                            pagingPolicy.onPageSuccess(generation, 0)
                            libraryAdapter.hideFooter()
                        } else {
                            libraryAdapter.appendItems(uiItems)
                            val currentLoadedCount = libraryAdapter.getContentItemCount()
                            pagingPolicy.onPageSuccess(
                                callbackGeneration = generation,
                                newItemsCount = uiItems.size,
                                totalInCollection = -1,
                                loadedItemsCount = currentLoadedCount,
                            )
                            libraryAdapter.hideFooter()
                        }
                    }.onFailure { exc ->
                        Log.w(TAG, "Search paging failed: ${exc.message}")
                        pagingPolicy.onPageFailure(generation, exc.message)
                        libraryAdapter.showFooter(
                            isError = true,
                            errorMessage = pagingPolicy.errorMessage ?: "Không tải được thêm",
                            onRetry = { loadNextPage(isRetry = true) }
                        )
                    }
                }
            }
            return
        }

        val mediaBrowser = browser
        if (mediaBrowser == null) {
            pagingPolicy.onPageFailure(generation, "Browser disconnected")
            libraryAdapter.hideFooter()
            return
        }

        val libraryParams = androidx.media3.session.MediaLibraryService.LibraryParams.Builder().apply {
            val bundle = Bundle().apply {
                putString("sort", navManager.currentSort)
            }
            setExtras(bundle)
        }.build()

        val future = mediaBrowser.getChildren(currentParent, nextPage, pageSize, libraryParams)
        future.addListener(
            {
                if (generation != pagingGeneration) {
                    return@addListener
                }
                runCatching { future.get() }
                    .onSuccess { result ->
                        val code = result.resultCode
                        if (code != androidx.media3.session.LibraryResult.RESULT_SUCCESS) {
                            Log.w(TAG, "getChildren paging error code: $code")
                            pagingPolicy.onPageFailure(generation, "Lỗi mã $code")
                            libraryAdapter.showFooter(
                                isError = true,
                                errorMessage = pagingPolicy.errorMessage ?: "Không tải được thêm",
                                onRetry = { loadNextPage(isRetry = true) }
                            )
                            return@onSuccess
                        }
                        val rawItems = result.value.orEmpty()
                        val uiItems = mapToUiItems(rawItems)
                        if (uiItems.isEmpty()) {
                            pagingPolicy.onPageSuccess(generation, 0)
                            libraryAdapter.hideFooter()
                        } else {
                            val firstExtras = rawItems.firstOrNull()?.mediaMetadata?.extras
                            val total = firstExtras?.getInt("collection_total", -1) ?: -1
                            libraryAdapter.appendItems(uiItems)
                            val currentLoadedCount = libraryAdapter.getContentItemCount()
                            pagingPolicy.onPageSuccess(
                                callbackGeneration = generation,
                                newItemsCount = uiItems.size,
                                totalInCollection = total,
                                loadedItemsCount = currentLoadedCount,
                            )
                            libraryAdapter.hideFooter()
                        }
                    }
                    .onFailure { exc ->
                        Log.w(TAG, "getChildren paging failed: ${exc.message}")
                        pagingPolicy.onPageFailure(generation, exc.message)
                        libraryAdapter.showFooter(
                            isError = true,
                            errorMessage = pagingPolicy.errorMessage ?: "Không tải được thêm",
                            onRetry = { loadNextPage(isRetry = true) }
                        )
                    }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun triggerVisibleEnrichment() {
        val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first >= 0 && last >= 0) {
            enrichmentController.scheduleEnrichment(first, last, libraryAdapter.getItems())
        }
    }

    private fun updateHeaderAndBreadcrumbs() {
        val baseTitle = if (!navManager.isAtRoot || currentWidthClass() == WindowWidthClass.EXPANDED) {
            navManager.breadcrumbTrail()
        } else {
            navManager.displayTitle()
        }
        binding.libraryTitleView.text = baseTitle

        if (isCollectionContext(navManager.currentParentId)) {
            val total = navManager.currentCollectionTotal
            val trackCount = navManager.currentCollectionTrackCount
            val folderCount = navManager.currentCollectionFolderCount
            val subtitle = formatCollectionSubtitle(
                parentId = navManager.currentParentId,
                total = total,
                trackCount = trackCount,
                folderCount = folderCount,
            )
            if (!subtitle.isNullOrBlank()) {
                binding.librarySubtitleView.visibility = View.VISIBLE
                binding.librarySubtitleView.text = subtitle
            } else {
                binding.librarySubtitleView.visibility = View.GONE
            }
        } else {
            binding.librarySubtitleView.visibility = View.GONE
        }

        binding.backButton.visibility = if (navManager.isAtRoot) View.GONE else View.VISIBLE
        binding.playlistEditButton.visibility =
            if (navManager.currentParentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX) && !isEditMode) View.VISIBLE else View.GONE

        updateQuickFilterChipsUi()
        updateCollectionActionBar()
        updateCollectionSortButton()
        updateDownloadQueueHeader()
        updateOfflineStorageDashboard()
    }

    private fun updateQuickFilterChipsUi() {
        val showBar = !isEditMode && !isQueueVisible && !isLyricsVisible
        binding.quickFilterScroll?.visibility = if (showBar) View.VISIBLE else View.GONE
        if (!showBar) return

        val activeId = navManager.activeQuickFilterId()
        val chips = listOf(
            binding.filterRootChip to RemoteCatalog.ROOT_ID,
            binding.filterAllChip to RemoteCatalog.ALL_ID,
            binding.filterDownloadedChip to RemoteCatalog.DOWNLOADED_ID,
            binding.filterDeviceChip to RemoteCatalog.DEVICE_LOCAL_ID,
            binding.filterDriveChip to RemoteCatalog.DRIVE_ID,
            binding.filterPlaylistsChip to RemoteCatalog.PLAYLISTS_ID,
        )
        val activeTextColor = ContextCompat.getColor(this, R.color.accent_primary)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.text_secondary)

        for ((chipView, chipId) in chips) {
            val view = chipView ?: continue
            val isSelected = activeId == chipId
            view.setBackgroundResource(
                if (isSelected) R.drawable.bg_quality_chip_active else R.drawable.bg_quality_chip_inactive,
            )
            view.setTextColor(if (isSelected) activeTextColor else inactiveTextColor)
            view.isSelected = isSelected
        }
    }

    private fun handleQuickFilterClick(parentId: String, label: String) {
        if (isEditMode) exitEditMode()
        searchDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
        clearSearchInputSilently()
        Log.i(
            TAG,
            "QUICK_FILTER_SELECT: parentId=$parentId label=$label currentParent=${navManager.currentParentId}",
        )
        if (parentId == RemoteCatalog.ROOT_ID) {
            if (navManager.isAtRoot && binding.errorView.visibility != View.VISIBLE) return
            loadRoot()
            return
        }
        if (parentId == RemoteCatalog.DEVICE_LOCAL_ID) {
            checkAndRequestLocalAudioPermission {
                navManager.selectQuickFilter(parentId, label)
                loadChildren(parentId, 0, label)
            }
            return
        }
        if (navManager.currentParentId == parentId &&
            navManager.currentPage == 0 &&
            binding.errorView.visibility != View.VISIBLE
        ) {
            return
        }
        navManager.selectQuickFilter(parentId, label)
        loadChildren(parentId, 0, label)
    }

    private fun updateOfflineStorageDashboard() {
        val showDashboard = !isEditMode && !isQueueVisible && !isLyricsVisible && navManager.currentParentId == RemoteCatalog.DOWNLOADED_ID
        if (!showDashboard) {
            binding.offlineStorageHeader.visibility = View.GONE
            return
        }

        binding.offlineStorageHeader.visibility = View.VISIBLE
        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
        val summary = dm.getStorageSummary(repairStaleMetadata = true)
        binding.txtOfflineStorageUsed.text = "${dm.formatStorageBytes(summary.usedBytes)} · ${summary.trackCount} bài"
        binding.txtOfflineStorageFree.text = dm.formatStorageBytes(summary.availableBytes)
        binding.txtOfflineStorageCap.text = dm.formatCapLabel(summary.capBytes)
        binding.txtOfflineNetworkPolicy.text = dm.formatNetworkPolicyLabel()

        val activeTarget = summary.selectedTarget
        val isSdSelected = activeTarget == com.bsdung.musicassistantcar.playback.OfflineStorageTarget.REMOVABLE_SD
        if (isSdSelected && !summary.sdAvailable) {
            binding.txtOfflineStorageTarget.text = "Thẻ nhớ SD — Không khả dụng"
            binding.txtOfflineStorageTarget.setTextColor(ContextCompat.getColor(this, R.color.status_offline))
        } else {
            binding.txtOfflineStorageTarget.text = dm.formatStorageTargetLabel(activeTarget)
            binding.txtOfflineStorageTarget.setTextColor(ContextCompat.getColor(this, R.color.accent_primary))
        }

        val internalDot = if (!isSdSelected) "●" else "○"
        val sdDot = if (isSdSelected) "●" else "○"
        val internalLine = "$internalDot Bộ nhớ trong: Đã dùng ${dm.formatStorageBytes(summary.internalUsedBytes)} · Còn trống: ${dm.formatStorageBytes(summary.internalAvailableBytes)}"
        val sdLine = if (summary.sdAvailable) {
            "$sdDot Thẻ nhớ SD: Đã dùng ${dm.formatStorageBytes(summary.sdUsedBytes)} · Còn trống: ${dm.formatStorageBytes(summary.sdAvailableBytes)}"
        } else {
            "$sdDot Thẻ nhớ SD — Không khả dụng"
        }
        binding.txtOfflineVolumeDetails.text = "$internalLine\n$sdLine"

        val hasTracks = summary.trackCount > 0
        binding.btnOfflineMultiSelect.isEnabled = hasTracks
        binding.btnOfflineMultiSelect.alpha = if (hasTracks) 1.0f else 0.5f
        binding.btnDeleteAllOffline.isEnabled = hasTracks
        binding.btnDeleteAllOffline.alpha = if (hasTracks) 1.0f else 0.5f
    }

    private fun showOfflineStorageTargetDialog() {
        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
        val summary = dm.getStorageSummary(repairStaleMetadata = false)
        val currentTarget = summary.selectedTarget

        val density = resources.displayMetrics.density
        val padH = (20 * density).toInt()
        val padV = (12 * density).toInt()
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padH, padV, padH, (4 * density).toInt())
        }

        val descView = android.widget.TextView(this).apply {
            text = "Chọn nơi lưu cho các bản nhạc offline tải mới. Các bài đã tải trước đó vẫn hoạt động bình thường ở vị trí hiện tại."
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        container.addView(descView)

        var dialogRef: AlertDialog? = null

        fun addTargetRow(
            target: com.bsdung.musicassistantcar.playback.OfflineStorageTarget,
            titleText: String,
            subtitleText: String,
            enabled: Boolean,
        ) {
            val isSelected = (target == currentTarget)
            val rowLayout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
                isEnabled = enabled
                alpha = if (enabled) 1.0f else 0.55f
                if (enabled) {
                    setBackgroundResource(R.drawable.bg_button_control)
                    setOnClickListener {
                        dialogRef?.dismiss()
                        if (target != currentTarget) {
                            showConfirmChangeStorageTargetDialog(target)
                        }
                    }
                }
            }
            val dot = if (isSelected) "●" else "○"
            val titleView = android.widget.TextView(this).apply {
                text = "$dot $titleText"
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (isSelected) R.color.accent_primary else if (enabled) R.color.text_primary else R.color.text_secondary,
                    ),
                )
            }
            val subView = android.widget.TextView(this).apply {
                text = subtitleText
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setPadding((18 * density).toInt(), (2 * density).toInt(), 0, 0)
            }
            rowLayout.addView(titleView)
            rowLayout.addView(subView)
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
            container.addView(rowLayout, lp)
        }

        addTargetRow(
            target = com.bsdung.musicassistantcar.playback.OfflineStorageTarget.INTERNAL,
            titleText = "Bộ nhớ trong",
            subtitleText = "Đã dùng: ${dm.formatStorageBytes(summary.internalUsedBytes)} (${summary.internalTrackCount} bài) · Còn trống: ${dm.formatStorageBytes(summary.internalAvailableBytes)}",
            enabled = true,
        )

        if (summary.sdAvailable) {
            addTargetRow(
                target = com.bsdung.musicassistantcar.playback.OfflineStorageTarget.REMOVABLE_SD,
                titleText = "Thẻ nhớ SD",
                subtitleText = "Đã dùng: ${dm.formatStorageBytes(summary.sdUsedBytes)} (${summary.sdTrackCount} bài) · Còn trống: ${dm.formatStorageBytes(summary.sdAvailableBytes)}",
                enabled = true,
            )
        } else {
            addTargetRow(
                target = com.bsdung.musicassistantcar.playback.OfflineStorageTarget.REMOVABLE_SD,
                titleText = "Thẻ nhớ SD — Không khả dụng",
                subtitleText = if (summary.sdTrackCount > 0) {
                    "Thẻ nhớ đang tháo (${summary.sdTrackCount} bài đã lưu trên thẻ)"
                } else {
                    "Không phát hiện thẻ nhớ SD gắn ngoài khả dụng"
                },
                enabled = false,
            )
        }

        dialogRef = AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Nơi lưu nhạc offline")
            .setView(container)
            .setNegativeButton("Đóng", null)
            .create()
        dialogRef?.show()
    }

    private fun showConfirmChangeStorageTargetDialog(
        newTarget: com.bsdung.musicassistantcar.playback.OfflineStorageTarget,
        onConfirmed: (() -> Unit)? = null,
    ) {
        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
        val message = buildString {
            append("Các bài đã tải sẽ được giữ nguyên ở vị trí hiện tại.\n")
            append("Chỉ các bài tải mới sử dụng nơi lưu mới.\n")
            append("Không tự động di chuyển nhạc đã tải.")
        }
        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Đổi nơi lưu các bản tải mới?")
            .setMessage(message)
            .setNegativeButton("HỦY", null)
            .setPositiveButton("ĐỔI") { _, _ ->
                dm.setOfflineStorageTarget(newTarget)
                updateOfflineStorageDashboard()
                libraryAdapter.notifyDataSetChanged()
                Toast.makeText(
                    this,
                    "Nơi lưu nhạc offline: ${dm.formatStorageTargetLabel(newTarget)}",
                    Toast.LENGTH_SHORT,
                ).show()
                onConfirmed?.invoke()
            }
            .show()
    }

    private fun showOfflineStorageCapDialog() {
        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
        val currentCap = dm.getOfflineStorageCapBytes()
        val options = listOf(
            Pair("Không giới hạn", com.bsdung.musicassistantcar.playback.PinnedDownloadManager.CAP_UNLIMITED),
            Pair("5 GB", com.bsdung.musicassistantcar.playback.PinnedDownloadManager.CAP_5_GB),
            Pair("10 GB", com.bsdung.musicassistantcar.playback.PinnedDownloadManager.CAP_10_GB),
            Pair("20 GB", com.bsdung.musicassistantcar.playback.PinnedDownloadManager.CAP_20_GB),
            Pair("50 GB", com.bsdung.musicassistantcar.playback.PinnedDownloadManager.CAP_50_GB),
        )
        val labels = options.map { (label, value) ->
            if (value == currentCap) "✓ $label" else "    $label"
        }.toTypedArray()

        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Giới hạn dung lượng offline")
            .setItems(labels) { _, which ->
                val chosenCap = options[which].second
                dm.setOfflineStorageCapBytes(chosenCap)
                updateOfflineStorageDashboard()
                Toast.makeText(
                    this,
                    "Giới hạn dung lượng offline: ${dm.formatCapLabel(chosenCap)}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showDownloadNetworkPolicyDialog() {
        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
        val currentPolicy = dm.getDownloadNetworkPolicy()
        val options = listOf(
            Pair("Mọi mạng", com.bsdung.musicassistantcar.playback.DownloadNetworkPolicy.ANY_NETWORK),
            Pair("Chỉ Wi-Fi", com.bsdung.musicassistantcar.playback.DownloadNetworkPolicy.WIFI_ONLY),
        )

        val density = resources.displayMetrics.density
        val padH = (20 * density).toInt()
        val padV = (12 * density).toInt()
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padH, padV, padH, (4 * density).toInt())
        }

        val descView = android.widget.TextView(this).apply {
            text = "Chỉ tải bản offline khi thiết bị đang có kết nối Wi-Fi Internet."
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        container.addView(descView)

        val radioGroup = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }

        var dialogRef: AlertDialog? = null
        options.forEachIndexed { idx, (label, policy) ->
            val rb = android.widget.RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                buttonTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, R.color.accent_primary)
                )
                setPadding((8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt())
                isChecked = (policy == currentPolicy)
                setOnClickListener {
                    dm.setDownloadNetworkPolicy(policy)
                    updateOfflineStorageDashboard()
                    updateDownloadQueueHeader()
                    libraryAdapter.notifyDataSetChanged()
                    if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                        loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        "Mạng tải xuống: ${dm.formatNetworkPolicyLabel(policy)}",
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialogRef?.dismiss()
                }
            }
            radioGroup.addView(rb, idx)
        }
        container.addView(radioGroup)

        dialogRef = AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Mạng tải xuống")
            .setView(container)
            .setNegativeButton("Đóng", null)
            .create()
        dialogRef?.show()
    }

    private fun stopPlaybackIfDeletedOfflineTrack(deletedTrackIds: Set<Long>) {
        if (deletedTrackIds.isEmpty()) return
        val currentItem = browser?.currentMediaItem ?: return
        val extras = currentItem.mediaMetadata.extras
        val currentTrackId = extras?.getLong("track_id_long")?.takeIf { it > 0L }
            ?: extras?.getInt("track_id")?.takeIf { it > 0 }?.toLong()
            ?: currentItem.mediaId.removePrefix(RemoteCatalog.TRACK_PREFIX).toLongOrNull()
        val isPinnedSource = extras?.getString("playback_source") == "PINNED_DOWNLOAD" ||
            extras?.getBoolean("is_offline_download", false) == true ||
            currentItem.localConfiguration?.uri?.scheme == "file"
        if (currentTrackId != null && deletedTrackIds.contains(currentTrackId) && isPinnedSource) {
            Log.i("MainActivity", "Track $currentTrackId is playing from PINNED_DOWNLOAD, stopping before local delete")
            browser?.stop()
            browser?.clearMediaItems()
        }
    }

    private fun showMultiDeleteOfflineConfirmation(selectedMediaIds: List<String>) {
        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
        val trackIds = selectedMediaIds.mapNotNull { mediaId ->
            mediaId.removePrefix(RemoteCatalog.TRACK_PREFIX).toLongOrNull()
        }.filter { dm.isOfflineReady(it) }.distinct()

        if (trackIds.isEmpty()) {
            Toast.makeText(this, "Chưa chọn bài hát offline hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Xóa ${trackIds.size} bản tải offline?")
            .setMessage("Chỉ xóa bản lưu trên thiết bị.\nCác bài hát vẫn còn trong kho MeloVault và có thể phát trực tuyến.")
            .setPositiveButton("Xóa bản offline") { _, _ ->
                stopPlaybackIfDeletedOfflineTrack(trackIds.toSet())
                val deleted = dm.deleteMultipleDownloads(trackIds)
                exitEditMode()
                updateOfflineStorageDashboard()
                Toast.makeText(this, "Đã xóa $deleted bản tải offline", Toast.LENGTH_SHORT).show()
                loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
            }
            .setNegativeButton("Hủy chọn", null)
            .show()
    }

    private fun showDeleteAllOfflineDialog() {
        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
        if (dm.hasActiveJobs()) {
            AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                .setTitle("Đang tải nhạc offline")
                .setMessage("Đang có tiến trình tải xuống đang chạy. Vui lòng hủy hàng đợi tải hoặc chờ tải xong trước khi xóa tất cả nhạc offline.")
                .setPositiveButton("Đã hiểu", null)
                .show()
            return
        }

        val summary = dm.getStorageSummary(repairStaleMetadata = true)
        if (summary.trackCount <= 0) {
            Toast.makeText(this, "Không có nhạc offline nào để xóa", Toast.LENGTH_SHORT).show()
            return
        }

        val allOfflineIds = dm.getAllDownloadedMediaItems().mapNotNull {
            it.mediaId.removePrefix(RemoteCatalog.TRACK_PREFIX).toLongOrNull()
        }.toSet()

        val message = buildString {
            append("${summary.trackCount} bài\n")
            append("${dm.formatStorageBytes(summary.usedBytes)}\n\n")
            append("Kho nhạc MeloVault và playlist không bị thay đổi.")
        }

        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Xóa tất cả nhạc offline?")
            .setMessage(message)
            .setNegativeButton("HỦY", null)
            .setPositiveButton("XÓA BẢN OFFLINE") { _, _ ->
                stopPlaybackIfDeletedOfflineTrack(allOfflineIds)
                val res = dm.deleteAllOfflineDownloads()
                if (!res.success) {
                    Toast.makeText(this, res.errorMessage ?: "Không thể xóa nhạc offline", Toast.LENGTH_LONG).show()
                } else {
                    updateOfflineStorageDashboard()
                    Toast.makeText(
                        this,
                        "Đã xóa tất cả bản offline (${res.deletedTrackCount} bài)",
                        Toast.LENGTH_SHORT,
                    ).show()
                    loadChildren(navManager.currentParentId, 0, navManager.currentLabel)
                }
            }
            .show()
    }

    private fun updateCollectionSortButton() {
        val parentId = navManager.currentParentId
        val isSortable = !isEditMode && !isQueueVisible && !isLyricsVisible && isSortableCollection(parentId)
        if (isSortable) {
            binding.collectionSortButton.visibility = View.VISIBLE
            if (parentId == RemoteCatalog.DOWNLOADED_ID) {
                val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
                val effectiveSort = navManager.currentSort.takeIf { it.isNotBlank() } ?: dm.getDownloadedSortKey()
                binding.collectionSortButton.text = when (effectiveSort) {
                    com.bsdung.musicassistantcar.playback.PinnedDownloadManager.SORT_NAME_ASC -> "⇅ Tên bài"
                    com.bsdung.musicassistantcar.playback.PinnedDownloadManager.SORT_DATE_DESC -> "⇅ Mới tải"
                    com.bsdung.musicassistantcar.playback.PinnedDownloadManager.SORT_DATE_ASC -> "⇅ Cũ nhất"
                    com.bsdung.musicassistantcar.playback.PinnedDownloadManager.SORT_SIZE_DESC -> "⇅ Dung lượng lớn"
                    com.bsdung.musicassistantcar.playback.PinnedDownloadManager.SORT_SIZE_ASC -> "⇅ Dung lượng nhỏ"
                    else -> "⇅ Mới tải"
                }
            } else {
                binding.collectionSortButton.text = when (navManager.currentSort) {
                    "name_asc" -> "⇅ A → Z"
                    "name_desc" -> "⇅ Z → A"
                    else -> "⇅ Mặc định"
                }
            }
        } else {
            binding.collectionSortButton.visibility = View.GONE
        }
    }

    private fun isSortableCollection(parentId: String): Boolean =
        parentId == RemoteCatalog.DOWNLOADED_ID ||
        parentId.startsWith(RemoteCatalog.DRIVE_FOLDER_PREFIX) ||
        parentId.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX) ||
        parentId.startsWith(RemoteCatalog.ALBUM_PREFIX) ||
        parentId.startsWith(RemoteCatalog.ARTIST_PREFIX)

    private fun showSortDialog() {
        val parentId = navManager.currentParentId
        if (parentId == RemoteCatalog.DOWNLOADED_ID) {
            val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
            val currentSort = navManager.currentSort.takeIf { it.isNotBlank() } ?: dm.getDownloadedSortKey()
            val options = listOf(
                Pair("Tên bài", com.bsdung.musicassistantcar.playback.PinnedDownloadManager.SORT_NAME_ASC),
                Pair("Mới tải", com.bsdung.musicassistantcar.playback.PinnedDownloadManager.SORT_DATE_DESC),
                Pair("Cũ nhất", com.bsdung.musicassistantcar.playback.PinnedDownloadManager.SORT_DATE_ASC),
                Pair("Dung lượng lớn", com.bsdung.musicassistantcar.playback.PinnedDownloadManager.SORT_SIZE_DESC),
                Pair("Dung lượng nhỏ", com.bsdung.musicassistantcar.playback.PinnedDownloadManager.SORT_SIZE_ASC),
            )
            val labels = options.map { (label, value) ->
                if (value == currentSort) "✓ $label" else "    $label"
            }.toTypedArray()

            AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                .setTitle("Sắp xếp nhạc đã tải về")
                .setItems(labels) { _, which ->
                    val chosenSort = options[which].second
                    dm.setDownloadedSortKey(chosenSort)
                    navManager.setSort(chosenSort)
                    updateCollectionSortButton()
                    loadChildren(navManager.currentParentId, 0, navManager.currentLabel, chosenSort)
                }
                .show()
            return
        }

        val isDriveFolder = parentId.startsWith(RemoteCatalog.DRIVE_FOLDER_PREFIX) ||
            parentId.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX)

        val options = if (isDriveFolder) {
            listOf(
                Pair("Mặc định", ""),
                Pair("Tên A → Z", "name_asc"),
                Pair("Tên Z → A", "name_desc"),
            )
        } else {
            listOf(
                Pair("Mặc định", ""),
                Pair("Tên A → Z", "name_asc"),
            )
        }

        val currentSort = navManager.currentSort
        val labels = options.map { (label, value) ->
            if (value == currentSort) "✓ $label" else "    $label"
        }.toTypedArray()

        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Sắp xếp danh sách")
            .setItems(labels) { _, which ->
                val chosenSort = options[which].second
                if (chosenSort != currentSort) {
                    navManager.setSort(chosenSort)
                    updateCollectionSortButton()
                    loadChildren(navManager.currentParentId, 0, navManager.currentLabel, chosenSort)
                }
            }
            .show()
    }

    /**
     * Shows the [collectionActionBar] when browsing a collection that supports Play All /
     * Shuffle / Add All. Hides it at root, search, All Music, and Telegram views.
     */
    private fun updateCollectionActionBar() {
        val parentId = navManager.currentParentId
        val showBar = !isEditMode && !isQueueVisible && !isLyricsVisible && isCollectionContext(parentId)
        binding.collectionActionBar.visibility = if (showBar) View.VISIBLE else View.GONE
        if (showBar) {
            val isPlaylist = parentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX)
            binding.collectionDownloadAllButton.text = if (isPlaylist) "↓ Tải playlist" else "↓ Tải tất cả"

            if (isPlaylist) {
                val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
                val currentItems = libraryAdapter.getItems().filterIsInstance<LibraryItem.Track>()
                val hasOffline = currentItems.any { track ->
                    val tid = RemoteCatalog.canonicalTrackMediaId(track.mediaItem)?.removePrefix(RemoteCatalog.TRACK_PREFIX)?.toLongOrNull()
                        ?: track.id.removePrefix(RemoteCatalog.TRACK_PREFIX).toLongOrNull()
                    tid != null && dm.isOfflineReady(tid)
                }
                binding.collectionDeleteOfflineButton.visibility = if (hasOffline) View.VISIBLE else View.GONE
            } else {
                binding.collectionDeleteOfflineButton.visibility = View.GONE
            }
        }
        updatePlaylistSyncBarPassive()
    }

    /** Returns true if [parentId] is a browseable collection that supports Play All / Shuffle. */
    private fun isCollectionContext(parentId: String): Boolean =
        parentId.startsWith(RemoteCatalog.ARTIST_PREFIX) ||
        parentId.startsWith(RemoteCatalog.ALBUM_PREFIX) ||
        parentId.startsWith(RemoteCatalog.DRIVE_FOLDER_PREFIX) ||
        parentId.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX) ||
        parentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX)

    private fun enterEditMode(allowReorder: Boolean) {
        if (isQueueVisible) hideQueue()
        if (isLyricsVisible) hideLyrics()
        isEditMode = true
        libraryAdapter.setEditMode(true, allowReorder)
        binding.breadcrumbBar.visibility = View.GONE
        binding.quickFilterScroll?.visibility = View.GONE
        binding.collectionActionBar.visibility = View.GONE
        binding.playlistSyncBar.visibility = View.GONE
        binding.collectionSortButton.visibility = View.GONE
        binding.offlineStorageHeader.visibility = View.GONE
        binding.editModeBar.visibility = View.VISIBLE
        val isPlaylist = navManager.currentParentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX)
        val isDownloaded = navManager.currentParentId == RemoteCatalog.DOWNLOADED_ID
        if (isDownloaded) {
            binding.editRemoveButton.visibility = View.VISIBLE
            binding.editRemoveButton.text = "Xóa bản offline"
            binding.editDoneButton.text = "Hủy chọn"
            binding.editAddToPlaylistButton.visibility = View.GONE
        } else {
            binding.editRemoveButton.visibility = if (isPlaylist) View.VISIBLE else View.GONE
            binding.editRemoveButton.text = "Xóa"
            binding.editDoneButton.text = "Xong"
            binding.editAddToPlaylistButton.visibility = View.VISIBLE
        }
        updateEditSelectionUi(0)
    }


    private fun exitEditMode() {
        isEditMode = false
        libraryAdapter.setEditMode(false)
        binding.editModeBar.visibility = View.GONE
        binding.breadcrumbBar.visibility = View.VISIBLE
        updateHeaderAndBreadcrumbs()
    }

    private fun updateEditSelectionUi(count: Int) {
        val isDownloaded = navManager.currentParentId == RemoteCatalog.DOWNLOADED_ID
        binding.editSelectedCountView.text = if (isDownloaded) "Đã chọn $count bài" else "$count đã chọn"
        binding.editAddToPlaylistButton.isEnabled = count > 0
        binding.editRemoveButton.isEnabled = count > 0
        binding.editAddToPlaylistButton.alpha = if (count > 0) 1.0f else 0.5f
        binding.editRemoveButton.alpha = if (count > 0) 1.0f else 0.5f
    }

    private fun performBatchRemove(playlistId: Int, mediaIds: List<String>) {
        uiExecutor.submit {
            val result = runCatching { uiCatalog.batchRemoveFromPlaylist(playlistId, mediaIds) }
            runOnUiThread {
                result.onSuccess { res ->
                    cachedPlaylistTracks.remove(playlistId)
                    cachedPlaylistSkippedCount.remove(playlistId)
                    Toast.makeText(this, "Đã xóa ${res.removed} bài khỏi danh sách phát", Toast.LENGTH_SHORT).show()
                    exitEditMode()
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }.onFailure {
                    Toast.makeText(this, "Không thể xóa bài hát", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Poweramp-style one-touch snapshot: saves the queue currently loaded in
     * MediaSession, without touching playback order, shuffle or seek position.
     * Pages not yet fetched by MediaService are intentionally not represented.
     */
    private fun saveQueueAsPlaylist() {
        val ids = buildCurrentQueueList().map { it.mediaId }
        when (val plan = QueuePlaylistExportPolicy.plan(ids)) {
            is QueuePlaylistExport.Empty -> {
                Toast.makeText(this, "Không có bài từ kho nhạc để lưu", Toast.LENGTH_SHORT).show()
            }
            is QueuePlaylistExport.TooLarge -> {
                AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                    .setTitle("Hàng đợi quá dài")
                    .setMessage("Đang có ${plan.queueSize} mục, giới hạn lưu mỗi lần là ${plan.limit} mục. Hãy chọn Playlist hoặc thư mục để lưu toàn bộ danh sách.")
                    .setPositiveButton("Đóng", null)
                    .show()
            }
            is QueuePlaylistExport.Ready -> {
                val notes = buildList {
                    if (plan.ignoredUnsupported > 0) {
                        add("${plan.ignoredUnsupported} bài từ nguồn chưa hỗ trợ lưu Playlist sẽ được bỏ qua.")
                    }
                    if (plan.ignoredDuplicates > 0) {
                        add("${plan.ignoredDuplicates} bài trùng trong hàng đợi được lược bỏ.")
                    }
                    add("Nếu thư mục còn trang chưa tải, chỉ các bài hiện đang có trong hàng đợi được lưu.")
                }
                AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                    .setTitle("Lưu ${plan.mediaIds.size} bài vào Playlist")
                    .setMessage(notes.joinToString("\n\n"))
                    .setPositiveButton("Playlist mới") { _, _ ->
                        // Snapshot is fixed before the dialog opens, so rapid Next
                        // or queue edits cannot change what the user chose to save.
                        showCreatePlaylistDialog(initialTrackIds = plan.mediaIds)
                    }
                    .setNeutralButton("Playlist có sẵn") { _, _ ->
                        showPlaylistChooserForTracks(plan.mediaIds)
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
        }
    }

    private fun toggleQueue() {
        if (isQueueVisible) {
            hideQueue()
        } else {
            showQueue()
        }
    }

    private fun showQueue() {
        if (isEditMode) exitEditMode()
        if (binding.recyclerView.visibility == View.VISIBLE) {
            captureCurrentScroll()
        }
        if (isLyricsVisible) hideLyrics()
        isQueueVisible = true
        Log.i(
            TAG,
            "QUEUE_OPEN: currentParent=${navManager.currentParentId} savedPos=${navManager.currentFirstVisiblePosition} savedOffset=${navManager.currentTopOffsetPx}",
        )
        binding.queueButton.setColorFilter(ContextCompat.getColor(this, R.color.accent_primary))
        binding.queueButton.setBackgroundResource(R.drawable.bg_button_tertiary_active)
        binding.quickQueueButton.setBackgroundResource(R.drawable.bg_button_tertiary_active)
        binding.quickLibraryButton.setBackgroundResource(R.drawable.bg_button_control)

        // Hide library / search content views
        binding.breadcrumbBar.visibility = View.GONE
        binding.quickFilterScroll?.visibility = View.GONE
        binding.collectionActionBar.visibility = View.GONE
        binding.playlistSyncBar.visibility = View.GONE
        binding.offlineStorageHeader.visibility = View.GONE
        binding.downloadQueueHeader.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.pageBar.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.loadingView.visibility = View.GONE
        binding.searchContainer.visibility = View.GONE

        // Show queue container
        binding.queueContainer.visibility = View.VISIBLE
        userOpenedFullPlayer = true
        applyMobilePlayerVisibility(isBrowsing = false)
        renderQueue()
    }


    private fun hideQueue() {
        isQueueVisible = false
        Log.i(
            TAG,
            "QUEUE_CLOSE: currentParent=${navManager.currentParentId} restorePos=${navManager.currentFirstVisiblePosition} restoreOffset=${navManager.currentTopOffsetPx}",
        )
        binding.queueButton.setColorFilter(ContextCompat.getColor(this, R.color.text_primary))
        binding.queueButton.setBackgroundResource(R.drawable.bg_button_control)
        binding.quickQueueButton.setBackgroundResource(R.drawable.bg_button_control)
        binding.quickLibraryButton.setBackgroundResource(R.drawable.bg_button_tertiary_active)

        binding.queueContainer.visibility = View.GONE

        // Restore normal views
        binding.searchContainer.visibility = View.VISIBLE
        binding.breadcrumbBar.visibility = View.VISIBLE
        updateHeaderAndBreadcrumbs()

        if (libraryAdapter.itemCount > 0) {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            restoreCurrentScroll()
        } else {
            binding.recyclerView.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
        }
        binding.pageBar.visibility = if (!navManager.isAtRoot) View.VISIBLE else View.GONE
        applyMobilePlayerVisibility()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Online Lyrics / Synced & Fullscreen Car Lyrics Controller (v0.6.28 / v0.6.30)
    // ─────────────────────────────────────────────────────────────────────────

    private val lyricsSyncRunnable = object : Runnable {
        override fun run() {
            if (!isLyricsVisible && !isFullscreenLyricsVisible) {
                return
            }
            val player = browser
            if (player == null || !player.isPlaying) {
                return
            }
            if (isFullscreenLyricsVisible) {
                updateFullscreenLyricsTimeUi(player)
            }
            if (currentLyricsUiState is LyricsUiState.Synced) {
                syncActiveLyricsLineWithPlayer(forceScroll = false)
            }
            mainHandler.postDelayed(this, 250L)
        }
    }

    private fun startLyricsPositionLoop() {
        mainHandler.removeCallbacks(lyricsSyncRunnable)
        val shouldRun = (isLyricsVisible && currentLyricsUiState is LyricsUiState.Synced) ||
            isFullscreenLyricsVisible
        if (shouldRun && browser?.isPlaying == true) {
            mainHandler.post(lyricsSyncRunnable)
        }
    }

    private fun stopLyricsPositionLoop() {
        mainHandler.removeCallbacks(lyricsSyncRunnable)
    }

    private fun toggleLyrics() {
        if (isLyricsVisible) {
            hideLyrics()
        } else {
            showLyrics()
        }
    }

    private fun isCurrentTrackLyricsAlreadyLoaded(): Boolean {
        val currentItem = browser?.currentMediaItem ?: return false
        val expectedSig = extractLyricsTrackMetadata(currentItem).cacheSignature()
        return currentLyricsSignature == expectedSig && currentLyricsUiState !is LyricsUiState.Idle
    }

    private fun showLyrics() {
        if (isEditMode) exitEditMode()
        if (binding.recyclerView.visibility == View.VISIBLE) {
            captureCurrentScroll()
        }
        if (isQueueVisible) hideQueue()
        isLyricsVisible = true
        binding.lyricsButton?.setTextColor(ContextCompat.getColor(this, R.color.accent_primary))
        binding.lyricsButton?.setBackgroundResource(R.drawable.bg_quality_chip_active)

        // Hide library / search content views
        binding.breadcrumbBar.visibility = View.GONE
        binding.quickFilterScroll?.visibility = View.GONE
        binding.collectionActionBar.visibility = View.GONE
        binding.playlistSyncBar.visibility = View.GONE
        binding.offlineStorageHeader.visibility = View.GONE
        binding.downloadQueueHeader.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.pageBar.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.loadingView.visibility = View.GONE
        binding.searchContainer.visibility = View.GONE

        binding.lyricsContainer?.visibility = View.VISIBLE
        if (isCurrentTrackLyricsAlreadyLoaded()) {
            renderLyricsUiState(currentLyricsUiState)
        } else {
            loadLyricsForCurrentTrack(forceRefresh = false)
        }
    }

    private fun hideLyrics() {
        isLyricsVisible = false
        if (!isFullscreenLyricsVisible) {
            stopLyricsPositionLoop()
        }
        binding.lyricsButton?.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        binding.lyricsButton?.setBackgroundResource(R.drawable.bg_quality_chip_inactive)
        binding.lyricsContainer?.visibility = View.GONE

        // Restore normal views
        binding.searchContainer.visibility = View.VISIBLE
        binding.breadcrumbBar.visibility = View.VISIBLE
        updateHeaderAndBreadcrumbs()

        if (libraryAdapter.itemCount > 0) {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            restoreCurrentScroll()
        } else {
            binding.recyclerView.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
        }
        binding.pageBar.visibility = if (!navManager.isAtRoot) View.VISIBLE else View.GONE
        applyMobilePlayerVisibility()
    }

    private fun showFullscreenLyrics() {
        if (!isQueueVisible && !isLyricsVisible && binding.recyclerView.visibility == View.VISIBLE) {
            captureCurrentScroll()
        }
        isFullscreenLyricsVisible = true
        binding.mainContentContainer?.visibility = View.GONE
        binding.fullscreenLyricsOverlay?.visibility = View.VISIBLE
        updateFullscreenKeepScreenOn()
        updateFullscreenLyricsPlayerChrome()

        if (isCurrentTrackLyricsAlreadyLoaded()) {
            Log.i(TAG, "LYRICS_FULLSCREEN_OPEN: reusedLoadedState=true sig=$currentLyricsSignature")
            renderLyricsUiState(currentLyricsUiState)
        } else {
            Log.i(TAG, "LYRICS_FULLSCREEN_OPEN: reusedLoadedState=false")
            loadLyricsForCurrentTrack(forceRefresh = false)
        }
    }

    private fun hideFullscreenLyrics() {
        if (!isFullscreenLyricsVisible) return
        isFullscreenLyricsVisible = false
        binding.fullscreenLyricsOverlay?.visibility = View.GONE
        binding.mainContentContainer?.visibility = View.VISIBLE
        updateFullscreenKeepScreenOn()
        Log.i(TAG, "LYRICS_FULLSCREEN_CLOSE: inlineVisible=$isLyricsVisible autoFollow=$isLyricsAutoFollow")

        if (!isLyricsVisible) {
            stopLyricsPositionLoop()
            if (!isQueueVisible && libraryAdapter.itemCount > 0) {
                restoreCurrentScroll()
            }
        } else {
            renderLyricsUiState(currentLyricsUiState)
        }
        applyMobilePlayerVisibility()
    }

    private fun updateFullscreenKeepScreenOn() {
        val shouldKeepOn = isFullscreenLyricsVisible && isActivityInForeground
        if (shouldKeepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateFullscreenLyricsTimeUi(player: Player?) {
        val pos = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val rawDur = player?.duration ?: 0L
        val dur = if (rawDur > 0L && rawDur != androidx.media3.common.C.TIME_UNSET) rawDur else 0L
        binding.fullscreenLyricsTimeView?.text =
            "${ProgressController.formatTimeMs(pos)} / ${ProgressController.formatTimeMs(dur)}"
    }

    private fun updateFullscreenLyricsPlayerChrome() {
        val player = browser
        val currentItem = player?.currentMediaItem
        if (currentItem == null) {
            binding.fullscreenLyricsTrackTitleView?.text = "Chưa phát nhạc"
            binding.fullscreenLyricsTrackSubtitleView?.text = currentLyricsSourceSummary
            binding.fullscreenLyricsPlayPauseButton?.setImageResource(R.drawable.ic_play_arrow)
            binding.fullscreenLyricsTimeView?.text = "00:00 / 00:00"
            return
        }
        val metadata = currentItem.mediaMetadata
        val rawTitle = metadata.title?.toString().orEmpty().trim()
        val artist = MetadataDisplayHelper.cleanArtist(metadata.artist?.toString())
        binding.fullscreenLyricsTrackTitleView?.text = rawTitle.ifBlank { "Đang phát" }
        binding.fullscreenLyricsTrackSubtitleView?.text = if (artist.isNotBlank()) {
            "$artist · $currentLyricsSourceSummary"
        } else {
            currentLyricsSourceSummary
        }
        if (player.isPlaying) {
            binding.fullscreenLyricsPlayPauseButton?.setImageResource(R.drawable.ic_pause)
        } else {
            binding.fullscreenLyricsPlayPauseButton?.setImageResource(R.drawable.ic_play_arrow)
        }
        updateFullscreenLyricsTimeUi(player)
    }

    private fun extractLyricsTrackMetadata(mediaItem: MediaItem): LyricsTrackMetadata {
        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        val rawTitle = metadata.title?.toString().orEmpty().trim()
        val cleanTitle = if (rawTitle.equals("Chưa xác định", ignoreCase = true)) "" else rawTitle
        val cleanArtist = MetadataDisplayHelper.cleanArtist(metadata.artist?.toString())
        val cleanAlbum = MetadataDisplayHelper.cleanAlbum(metadata.albumTitle?.toString())

        val extrasDurationMs = extras?.getLong("duration_ms")?.takeIf { it > 0L }
        val playerDurationMs = if (browser?.currentMediaItem?.mediaId == mediaItem.mediaId) {
            browser?.duration?.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET }
        } else {
            null
        }
        val durationMs = extrasDurationMs ?: playerDurationMs

        val canonicalId = RemoteCatalog.canonicalTrackMediaId(mediaItem) ?: mediaItem.mediaId
        val isDeviceLocal = mediaItem.mediaId.startsWith(RemoteCatalog.DEVICE_PREFIX) ||
            extras?.getBoolean("is_device_local", false) == true

        return LyricsTrackMetadata(
            trackKey = canonicalId,
            title = cleanTitle,
            artist = cleanArtist,
            album = cleanAlbum,
            durationMs = durationMs,
            isDeviceLocal = isDeviceLocal,
        )
    }

    private fun handleLyricsOnTrackTransition(mediaItem: MediaItem?) {
        stopLyricsPositionLoop()
        isCandidatePickerOpen = false
        currentCandidatePickerState = LyricsCandidatePickerState.Closed
        isLyricsAutoFollow = true
        updateLyricsAutoFollowButtonUi()
        syncedLyricsAdapter.submitLines(emptyList(), -1)
        fullscreenSyncedLyricsAdapter.submitLines(emptyList(), -1)
        binding.lyricsSyncedRecyclerView?.scrollToPosition(0)
        binding.fullscreenLyricsSyncedRecyclerView?.scrollToPosition(0)
        binding.lyricsPlainContainer?.scrollTo(0, 0)
        binding.fullscreenLyricsPlainContainer?.scrollTo(0, 0)

        if (mediaItem == null) {
            lyricsRepository.cancelActiveRequests()
            currentLyricsSignature = null
            currentLyricsUiState = LyricsUiState.Idle
            updateFullscreenLyricsPlayerChrome()
            if (isLyricsVisible || isFullscreenLyricsVisible) {
                renderLyricsUiState(LyricsUiState.NotFound())
            }
            return
        }

        val trackMeta = extractLyricsTrackMetadata(mediaItem)
        val token = lyricsRepository.beginTrackRequest(trackMeta)
        currentLyricsSignature = token.signature
        updateFullscreenLyricsPlayerChrome()

        if (isLyricsVisible || isFullscreenLyricsVisible) {
            loadLyricsForCurrentTrack(
                forceRefresh = false,
                preAllocatedToken = token,
                metadataOverride = trackMeta,
            )
        } else {
            currentLyricsUiState = LyricsUiState.Idle
        }
    }

    private fun loadLyricsForCurrentTrack(
        forceRefresh: Boolean,
        preferredProvider: String? = null,
        preferredRecordId: String? = null,
        preAllocatedToken: com.bsdung.musicassistantcar.lyrics.LyricsRequestToken? = null,
        metadataOverride: LyricsTrackMetadata? = null,
    ) {
        val currentItem = browser?.currentMediaItem
        if (currentItem == null) {
            lyricsRepository.cancelActiveRequests()
            isCandidatePickerOpen = false
            currentCandidatePickerState = LyricsCandidatePickerState.Closed
            currentLyricsSignature = null
            currentLyricsUiState = LyricsUiState.NotFound()
            if (isLyricsVisible || isFullscreenLyricsVisible) {
                renderLyricsUiState(currentLyricsUiState)
            }
            return
        }

        val trackMeta = metadataOverride ?: extractLyricsTrackMetadata(currentItem)
        val token = preAllocatedToken ?: lyricsRepository.beginTrackRequest(trackMeta)
        currentLyricsSignature = token.signature
        isCandidatePickerOpen = false
        currentCandidatePickerState = LyricsCandidatePickerState.Closed

        isLyricsAutoFollow = true
        updateLyricsAutoFollowButtonUi()
        syncedLyricsAdapter.submitLines(emptyList(), -1)
        fullscreenSyncedLyricsAdapter.submitLines(emptyList(), -1)
        binding.lyricsSyncedRecyclerView?.scrollToPosition(0)
        binding.fullscreenLyricsSyncedRecyclerView?.scrollToPosition(0)
        binding.lyricsPlainContainer?.scrollTo(0, 0)
        binding.fullscreenLyricsPlainContainer?.scrollTo(0, 0)

        currentLyricsUiState = LyricsUiState.Loading(
            title = trackMeta.title,
            artist = trackMeta.artist,
        )
        if (isLyricsVisible || isFullscreenLyricsVisible) {
            renderLyricsUiState(currentLyricsUiState)
        }

        lyricsExecutor.submit {
            val result = lyricsRepository.resolveLyrics(
                metadata = trackMeta,
                forceRefresh = forceRefresh,
                preferredProvider = preferredProvider,
                preferredRecordId = preferredRecordId,
            )
            Log.i(
                TAG,
                "LYRICS_RESOLVED: trackKey=${trackMeta.trackKey} status=${result.status} " +
                    "fromCache=${result.fromCache} provider=${result.provider} " +
                    "recordId=${result.providerRecordId ?: "none"} provenance=${result.provenance} " +
                    "lines=${result.syncedLines.size}",
            )
            runOnUiThread {
                if (!lyricsRepository.isTokenCurrent(token)) {
                    Log.i(TAG, "LYRICS_STALE_DISCARDED: trackKey=${trackMeta.trackKey} gen=${token.generation}")
                    return@runOnUiThread
                }
                val posMs = browser?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val uiState = LyricsRepository.toUiState(
                    result = result,
                    currentPositionMs = posMs,
                    autoFollow = isLyricsAutoFollow,
                )
                currentLyricsUiState = uiState
                if (isLyricsVisible || isFullscreenLyricsVisible) {
                    renderLyricsUiState(uiState)
                }
            }
        }
    }

    private fun openLyricsCandidatePicker() {
        val currentItem = browser?.currentMediaItem ?: return
        val trackMeta = extractLyricsTrackMetadata(currentItem)
        isCandidatePickerOpen = true

        val token = lyricsRepository.beginCandidateListRequest(trackMeta)
        currentCandidatePickerState = LyricsCandidatePickerState.Loading(
            title = trackMeta.title,
            artist = trackMeta.artist,
        )
        if (isLyricsVisible || isFullscreenLyricsVisible) {
            renderLyricsUiState(currentLyricsUiState)
        }

        lyricsExecutor.submit {
            val candResult = lyricsRepository.fetchCandidates(trackMeta)
            Log.i(
                TAG,
                "LYRICS_CANDIDATES_LOADED: trackKey=${trackMeta.trackKey} status=${candResult.status} " +
                    "count=${candResult.candidates.size}",
            )
            runOnUiThread {
                if (!lyricsRepository.isCandidateListTokenCurrent(token)) {
                    Log.i(
                        TAG,
                        "LYRICS_CANDIDATES_STALE_DISCARDED: trackKey=${trackMeta.trackKey} gen=${token.generation}",
                    )
                    return@runOnUiThread
                }
                val activeRecordId = activeRecordIdForCurrentTrack(trackMeta)
                currentCandidatePickerState = when {
                    candResult.status.equals("ERROR", ignoreCase = true) ||
                        candResult.status.equals("TEMPORARY_ERROR", ignoreCase = true) -> {
                        LyricsCandidatePickerState.Error(
                            message = candResult.errorMessage ?: "Không thể tải danh sách lời bài hát",
                        )
                    }
                    candResult.candidates.isEmpty() -> {
                        val fallbackCandidates = (currentLyricsUiState as? LyricsUiState.Ambiguous)?.candidates.orEmpty()
                        if (fallbackCandidates.isNotEmpty()) {
                            LyricsCandidatePickerState.Loaded(
                                candidates = fallbackCandidates.take(LyricsRepository.MAX_DISPLAY_CANDIDATES),
                                selectedRecordId = activeRecordId,
                            )
                        } else {
                            LyricsCandidatePickerState.Empty()
                        }
                    }
                    else -> {
                        LyricsCandidatePickerState.Loaded(
                            candidates = candResult.candidates.take(LyricsRepository.MAX_DISPLAY_CANDIDATES),
                            selectedRecordId = activeRecordId,
                        )
                    }
                }
                if (isLyricsVisible || isFullscreenLyricsVisible) {
                    renderLyricsUiState(currentLyricsUiState)
                }
            }
        }
    }

    private fun closeLyricsCandidatePicker() {
        if (!isCandidatePickerOpen) return
        isCandidatePickerOpen = false
        currentCandidatePickerState = LyricsCandidatePickerState.Closed
        if (isLyricsVisible || isFullscreenLyricsVisible) {
            renderLyricsUiState(currentLyricsUiState)
        }
    }

    private fun selectManualLyricsCandidate(candidate: LyricsCandidate) {
        val currentItem = browser?.currentMediaItem ?: return
        val trackMeta = extractLyricsTrackMetadata(currentItem)
        val token = lyricsRepository.beginTrackRequest(trackMeta)
        currentLyricsSignature = token.signature

        isCandidatePickerOpen = false
        currentCandidatePickerState = LyricsCandidatePickerState.Closed
        isLyricsAutoFollow = true
        updateLyricsAutoFollowButtonUi()
        syncedLyricsAdapter.submitLines(emptyList(), -1)
        fullscreenSyncedLyricsAdapter.submitLines(emptyList(), -1)
        binding.lyricsSyncedRecyclerView?.scrollToPosition(0)
        binding.fullscreenLyricsSyncedRecyclerView?.scrollToPosition(0)
        binding.lyricsPlainContainer?.scrollTo(0, 0)
        binding.fullscreenLyricsPlainContainer?.scrollTo(0, 0)

        currentLyricsUiState = LyricsUiState.Loading(
            title = trackMeta.title,
            artist = trackMeta.artist,
        )
        if (isLyricsVisible || isFullscreenLyricsVisible) {
            renderLyricsUiState(currentLyricsUiState)
        }

        lyricsExecutor.submit {
            val result = lyricsRepository.selectManualCandidate(
                metadata = trackMeta,
                candidate = candidate,
            )
            Log.i(
                TAG,
                "LYRICS_MANUAL_SELECTED: trackKey=${trackMeta.trackKey} recordId=${candidate.recordId} " +
                    "status=${result.status} provenance=${result.provenance} lines=${result.syncedLines.size}",
            )
            runOnUiThread {
                if (!lyricsRepository.isTokenCurrent(token)) {
                    Log.i(TAG, "LYRICS_STALE_DISCARDED: trackKey=${trackMeta.trackKey} gen=${token.generation}")
                    return@runOnUiThread
                }
                val posMs = browser?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val uiState = LyricsRepository.toUiState(
                    result = result,
                    currentPositionMs = posMs,
                    autoFollow = isLyricsAutoFollow,
                )
                currentLyricsUiState = uiState
                if (isLyricsVisible || isFullscreenLyricsVisible) {
                    renderLyricsUiState(uiState)
                }
            }
        }
    }

    private fun resetLyricsToAutomatic() {
        val currentItem = browser?.currentMediaItem ?: return
        val trackMeta = extractLyricsTrackMetadata(currentItem)
        val token = lyricsRepository.beginTrackRequest(trackMeta)
        currentLyricsSignature = token.signature

        isCandidatePickerOpen = false
        currentCandidatePickerState = LyricsCandidatePickerState.Closed
        isLyricsAutoFollow = true
        updateLyricsAutoFollowButtonUi()
        syncedLyricsAdapter.submitLines(emptyList(), -1)
        fullscreenSyncedLyricsAdapter.submitLines(emptyList(), -1)
        binding.lyricsSyncedRecyclerView?.scrollToPosition(0)
        binding.fullscreenLyricsSyncedRecyclerView?.scrollToPosition(0)
        binding.lyricsPlainContainer?.scrollTo(0, 0)
        binding.fullscreenLyricsPlainContainer?.scrollTo(0, 0)

        currentLyricsUiState = LyricsUiState.Loading(
            title = trackMeta.title,
            artist = trackMeta.artist,
        )
        if (isLyricsVisible || isFullscreenLyricsVisible) {
            renderLyricsUiState(currentLyricsUiState)
        }

        lyricsExecutor.submit {
            val result = lyricsRepository.clearManualOverride(trackMeta)
            Log.i(
                TAG,
                "LYRICS_MANUAL_CLEARED: trackKey=${trackMeta.trackKey} status=${result.status} " +
                    "provenance=${result.provenance} recordId=${result.providerRecordId ?: "none"}",
            )
            runOnUiThread {
                if (!lyricsRepository.isTokenCurrent(token)) {
                    Log.i(TAG, "LYRICS_STALE_DISCARDED: trackKey=${trackMeta.trackKey} gen=${token.generation}")
                    return@runOnUiThread
                }
                val posMs = browser?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val uiState = LyricsRepository.toUiState(
                    result = result,
                    currentPositionMs = posMs,
                    autoFollow = isLyricsAutoFollow,
                )
                currentLyricsUiState = uiState
                if (isLyricsVisible || isFullscreenLyricsVisible) {
                    renderLyricsUiState(uiState)
                }
            }
        }
    }

    private fun activeRecordIdForCurrentTrack(metadataOverride: LyricsTrackMetadata? = null): String? {
        val currentItem = browser?.currentMediaItem
        val meta = metadataOverride ?: currentItem?.let { extractLyricsTrackMetadata(it) }
        val overrideRecId = meta?.let { lyricsRepository.getManualOverride(it)?.providerRecordId }
        if (!overrideRecId.isNullOrBlank()) return overrideRecId
        return when (val s = currentLyricsUiState) {
            is LyricsUiState.Synced -> s.recordId
            is LyricsUiState.Plain -> s.recordId
            else -> null
        }
    }

    private fun isManualOverrideActiveForState(state: LyricsUiState): Boolean {
        val stateManual = when (state) {
            is LyricsUiState.Synced -> state.provenance == LyricsProvenance.MANUAL
            is LyricsUiState.Plain -> state.provenance == LyricsProvenance.MANUAL
            is LyricsUiState.Instrumental -> state.provenance == LyricsProvenance.MANUAL
            else -> false
        }
        if (stateManual) return true
        val currentItem = browser?.currentMediaItem ?: return false
        val meta = extractLyricsTrackMetadata(currentItem)
        return lyricsRepository.hasManualOverride(meta)
    }

    private fun renderLyricsUiState(state: LyricsUiState) {
        stopLyricsPositionLoop()
        // Inline containers
        binding.lyricsLoadingView?.visibility = View.GONE
        binding.lyricsPlainBadgeView?.visibility = View.GONE
        binding.lyricsSyncedRecyclerView?.visibility = View.GONE
        binding.lyricsPlainContainer?.visibility = View.GONE
        binding.lyricsAmbiguousContainer?.visibility = View.GONE
        binding.lyricsStatusContainer?.visibility = View.GONE
        binding.lyricsAutoFollowButton?.visibility = View.GONE
        binding.lyricsStatusSecondaryButton?.visibility = View.GONE

        // Fullscreen containers
        binding.fullscreenLyricsLoadingView?.visibility = View.GONE
        binding.fullscreenLyricsSyncedRecyclerView?.visibility = View.GONE
        binding.fullscreenLyricsPlainContainer?.visibility = View.GONE
        binding.fullscreenLyricsAmbiguousContainer?.visibility = View.GONE
        binding.fullscreenLyricsStatusContainer?.visibility = View.GONE
        binding.fullscreenLyricsStatusSecondaryButton?.visibility = View.GONE

        val hasTrack = browser?.currentMediaItem != null
        val manualOverrideActive = isManualOverrideActiveForState(state)
        val provenanceBadge = if (manualOverrideActive) {
            LyricsProvenance.MANUAL.badgeText
        } else {
            LyricsProvenance.AUTO.badgeText
        }

        // Action buttons visibility & labels
        val showAutoReset = hasTrack && manualOverrideActive && state !is LyricsUiState.Loading
        binding.lyricsAutoResetButton?.visibility = if (showAutoReset) View.VISIBLE else View.GONE
        binding.fullscreenLyricsAutoResetButton?.visibility = if (showAutoReset) View.VISIBLE else View.GONE

        val chooseButtonLabel = if (state is LyricsUiState.Ambiguous && !isCandidatePickerOpen) {
            "Chọn kết quả"
        } else {
            "Chọn lời khác"
        }
        binding.lyricsChooseOtherButton?.text = chooseButtonLabel
        binding.fullscreenLyricsChooseOtherButton?.text = chooseButtonLabel
        binding.lyricsChooseOtherButton?.visibility = if (hasTrack) View.VISIBLE else View.GONE
        binding.fullscreenLyricsChooseOtherButton?.visibility = if (hasTrack) View.VISIBLE else View.GONE

        if (isCandidatePickerOpen) {
            when (val pickerState = currentCandidatePickerState) {
                is LyricsCandidatePickerState.Loading -> {
                    currentLyricsSourceSummary = "Đang tải danh sách lời từ LRCLIB..."
                    binding.lyricsSourceBadgeView?.text = currentLyricsSourceSummary
                    binding.lyricsLoadingTextView?.text = "Đang tải danh sách lời bài hát…"
                    binding.fullscreenLyricsLoadingTextView?.text = "Đang tải danh sách lời bài hát…"
                    binding.lyricsLoadingView?.visibility = View.VISIBLE
                    binding.fullscreenLyricsLoadingView?.visibility = View.VISIBLE
                }
                is LyricsCandidatePickerState.Loaded -> {
                    currentLyricsSourceSummary = "Nguồn: $provenanceBadge · Chọn bản lời (${pickerState.candidates.size} kết quả)"
                    binding.lyricsSourceBadgeView?.text = currentLyricsSourceSummary

                    binding.lyricsAmbiguousTitleView?.text = "Chọn bản lời bài hát phù hợp"
                    binding.lyricsAmbiguousSubtitleView?.text = "Chọn bản khớp với bài đang phát (${pickerState.candidates.size} kết quả):"
                    binding.lyricsCandidatesCloseButton?.visibility = View.VISIBLE
                    lyricsCandidateAdapter.submitCandidates(
                        candidates = pickerState.candidates,
                        activeRecordId = pickerState.activeRecordId,
                    )
                    binding.lyricsAmbiguousContainer?.visibility = View.VISIBLE

                    binding.fullscreenLyricsAmbiguousTitleView?.text = "Chọn bản lời bài hát phù hợp"
                    binding.fullscreenLyricsAmbiguousSubtitleView?.text = "Chọn bản khớp với bài đang phát (${pickerState.candidates.size} kết quả):"
                    binding.fullscreenLyricsCandidatesCloseButton?.visibility = View.VISIBLE
                    fullscreenLyricsCandidateAdapter.submitCandidates(
                        candidates = pickerState.candidates,
                        activeRecordId = pickerState.activeRecordId,
                    )
                    binding.fullscreenLyricsAmbiguousContainer?.visibility = View.VISIBLE
                }
                is LyricsCandidatePickerState.Empty -> {
                    currentLyricsSourceSummary = "Nguồn: $provenanceBadge · Không có ứng viên phù hợp"
                    binding.lyricsSourceBadgeView?.text = currentLyricsSourceSummary

                    binding.lyricsStatusTitleView?.text = "Không tìm thấy lời phù hợp"
                    binding.lyricsStatusSubtitleView?.text = "LRCLIB chưa có bản lời nào khớp với bài hát này."
                    binding.lyricsStatusSubtitleView?.visibility = View.VISIBLE
                    binding.lyricsStatusActionButton?.text = "Đóng danh sách"
                    binding.lyricsStatusSecondaryButton?.visibility = View.GONE
                    binding.lyricsStatusContainer?.visibility = View.VISIBLE

                    binding.fullscreenLyricsStatusTitleView?.text = "Không tìm thấy lời phù hợp"
                    binding.fullscreenLyricsStatusSubtitleView?.text = "LRCLIB chưa có bản lời nào khớp với bài hát này."
                    binding.fullscreenLyricsStatusSubtitleView?.visibility = View.VISIBLE
                    binding.fullscreenLyricsStatusActionButton?.text = "Đóng danh sách"
                    binding.fullscreenLyricsStatusSecondaryButton?.visibility = View.GONE
                    binding.fullscreenLyricsStatusContainer?.visibility = View.VISIBLE
                }
                is LyricsCandidatePickerState.Error -> {
                    currentLyricsSourceSummary = "Lỗi tải danh sách lời bài hát"
                    binding.lyricsSourceBadgeView?.text = currentLyricsSourceSummary

                    binding.lyricsStatusTitleView?.text = "Không thể tải danh sách lời bài hát"
                    binding.lyricsStatusSubtitleView?.text = pickerState.message
                    binding.lyricsStatusSubtitleView?.visibility = View.VISIBLE
                    binding.lyricsStatusActionButton?.text = "Thử lại"
                    binding.lyricsStatusSecondaryButton?.text = "Đóng"
                    binding.lyricsStatusSecondaryButton?.visibility = View.VISIBLE
                    binding.lyricsStatusContainer?.visibility = View.VISIBLE

                    binding.fullscreenLyricsStatusTitleView?.text = "Không thể tải danh sách lời bài hát"
                    binding.fullscreenLyricsStatusSubtitleView?.text = pickerState.message
                    binding.fullscreenLyricsStatusSubtitleView?.visibility = View.VISIBLE
                    binding.fullscreenLyricsStatusActionButton?.text = "Thử lại"
                    binding.fullscreenLyricsStatusSecondaryButton?.text = "Đóng"
                    binding.fullscreenLyricsStatusSecondaryButton?.visibility = View.VISIBLE
                    binding.fullscreenLyricsStatusContainer?.visibility = View.VISIBLE
                }
                is LyricsCandidatePickerState.Hidden -> {
                    isCandidatePickerOpen = false
                }
            }
            updateLyricsAutoFollowButtonUi()
            startLyricsPositionLoop()
            updateFullscreenLyricsPlayerChrome()
            return
        }

        binding.lyricsLoadingTextView?.text = "Đang tìm lời bài hát…"
        binding.fullscreenLyricsLoadingTextView?.text = "Đang tìm lời bài hát…"

        when (state) {
            is LyricsUiState.Idle,
            is LyricsUiState.Loading -> {
                currentLyricsSourceSummary = "Đang tải lời bài hát..."
                binding.lyricsSourceBadgeView?.text = currentLyricsSourceSummary
                binding.lyricsLoadingView?.visibility = View.VISIBLE
                binding.fullscreenLyricsLoadingView?.visibility = View.VISIBLE
                updateLyricsAutoFollowButtonUi()
            }
            is LyricsUiState.Synced -> {
                val srcLabel = if (state.fromCache) "Đã lưu đệm" else "Trực tuyến"
                currentLyricsSourceSummary = "Nguồn: $provenanceBadge · Lời đồng bộ · $srcLabel"
                binding.lyricsSourceBadgeView?.text = currentLyricsSourceSummary
                binding.lyricsSyncedRecyclerView?.visibility = View.VISIBLE
                binding.fullscreenLyricsSyncedRecyclerView?.visibility = View.VISIBLE

                val posMs = browser?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val activeIdx = LrcParser.findActiveLineIndex(state.lines, posMs)
                syncedLyricsAdapter.submitLines(state.lines, activeIdx)
                fullscreenSyncedLyricsAdapter.submitLines(state.lines, activeIdx)
                updateLyricsAutoFollowButtonUi()
                if (activeIdx >= 0 && isLyricsAutoFollow) {
                    binding.lyricsSyncedRecyclerView?.post {
                        scrollToActiveLyricLine(activeIdx, smooth = false)
                    }
                    binding.fullscreenLyricsSyncedRecyclerView?.post {
                        scrollToActiveLyricLine(activeIdx, smooth = false)
                    }
                }
                startLyricsPositionLoop()
            }
            is LyricsUiState.Plain -> {
                val srcLabel = if (state.fromCache) "Đã lưu đệm" else "Trực tuyến"
                currentLyricsSourceSummary = "Nguồn: $provenanceBadge · Lời văn bản · $srcLabel"
                binding.lyricsSourceBadgeView?.text = currentLyricsSourceSummary
                binding.lyricsPlainBadgeView?.text = "Lời chưa đồng bộ thời gian"
                binding.lyricsPlainBadgeView?.visibility = View.VISIBLE
                binding.lyricsPlainTextView?.text = state.text
                binding.lyricsPlainContainer?.visibility = View.VISIBLE

                binding.fullscreenLyricsPlainBadgeView?.text = "Lời chưa đồng bộ thời gian"
                binding.fullscreenLyricsPlainTextView?.text = state.text
                binding.fullscreenLyricsPlainContainer?.visibility = View.VISIBLE
                updateLyricsAutoFollowButtonUi()
                startLyricsPositionLoop()
            }
            is LyricsUiState.Instrumental -> {
                currentLyricsSourceSummary = "Nguồn: $provenanceBadge · Bản nhạc không lời"
                binding.lyricsSourceBadgeView?.text = currentLyricsSourceSummary
                binding.lyricsStatusTitleView?.text = "Bản nhạc không lời"
                binding.lyricsStatusSubtitleView?.text = "Bài hát này được đánh dấu là nhạc không lời (Instrumental)."
                binding.lyricsStatusSubtitleView?.visibility = View.VISIBLE
                binding.lyricsStatusActionButton?.text = "Tìm lại lời"
                binding.lyricsStatusSecondaryButton?.text = "Chọn lời khác"
                binding.lyricsStatusSecondaryButton?.visibility = if (hasTrack) View.VISIBLE else View.GONE
                binding.lyricsStatusContainer?.visibility = View.VISIBLE

                binding.fullscreenLyricsStatusTitleView?.text = "Bản nhạc không lời"
                binding.fullscreenLyricsStatusSubtitleView?.text = "Bài hát này được đánh dấu là nhạc không lời (Instrumental)."
                binding.fullscreenLyricsStatusSubtitleView?.visibility = View.VISIBLE
                binding.fullscreenLyricsStatusActionButton?.text = "Tìm lại lời"
                binding.fullscreenLyricsStatusSecondaryButton?.text = "Chọn lời khác"
                binding.fullscreenLyricsStatusSecondaryButton?.visibility = if (hasTrack) View.VISIBLE else View.GONE
                binding.fullscreenLyricsStatusContainer?.visibility = View.VISIBLE
                updateLyricsAutoFollowButtonUi()
                startLyricsPositionLoop()
            }
            is LyricsUiState.Ambiguous -> {
                currentLyricsSourceSummary = "Nguồn: ${LyricsProvenance.AUTO.badgeText} · Có nhiều kết quả gần giống"
                binding.lyricsSourceBadgeView?.text = currentLyricsSourceSummary
                val activeId = activeRecordIdForCurrentTrack()
                binding.lyricsAmbiguousTitleView?.text = "Tìm thấy nhiều lời bài hát phù hợp"
                binding.lyricsAmbiguousSubtitleView?.text = "Chọn bản khớp với bài đang phát:"
                binding.lyricsCandidatesCloseButton?.visibility = View.GONE
                lyricsCandidateAdapter.submitCandidates(state.candidates, activeRecordId = activeId)
                binding.lyricsAmbiguousContainer?.visibility = View.VISIBLE

                binding.fullscreenLyricsAmbiguousTitleView?.text = "Tìm thấy nhiều lời bài hát phù hợp"
                binding.fullscreenLyricsAmbiguousSubtitleView?.text = "Chọn bản khớp với bài đang phát:"
                binding.fullscreenLyricsCandidatesCloseButton?.visibility = View.GONE
                fullscreenLyricsCandidateAdapter.submitCandidates(state.candidates, activeRecordId = activeId)
                binding.fullscreenLyricsAmbiguousContainer?.visibility = View.VISIBLE
                updateLyricsAutoFollowButtonUi()
                startLyricsPositionLoop()
            }
            is LyricsUiState.NotFound -> {
                currentLyricsSourceSummary = "Nguồn: $provenanceBadge · Không có dữ liệu lời phù hợp"
                binding.lyricsSourceBadgeView?.text = currentLyricsSourceSummary
                binding.lyricsStatusTitleView?.text = "Không tìm thấy lời bài hát"
                binding.lyricsStatusSubtitleView?.text = "Đã kiểm tra tên bài hát và nghệ sĩ nhưng chưa có bản lời khớp chính xác."
                binding.lyricsStatusSubtitleView?.visibility = View.VISIBLE
                binding.lyricsStatusActionButton?.text = "Tìm lại lời"
                binding.lyricsStatusSecondaryButton?.text = "Chọn lời khác"
                binding.lyricsStatusSecondaryButton?.visibility = if (hasTrack) View.VISIBLE else View.GONE
                binding.lyricsStatusContainer?.visibility = View.VISIBLE

                binding.fullscreenLyricsStatusTitleView?.text = "Không tìm thấy lời bài hát"
                binding.fullscreenLyricsStatusSubtitleView?.text = "Đã kiểm tra tên bài hát và nghệ sĩ nhưng chưa có bản lời khớp chính xác."
                binding.fullscreenLyricsStatusSubtitleView?.visibility = View.VISIBLE
                binding.fullscreenLyricsStatusActionButton?.text = "Tìm lại lời"
                binding.fullscreenLyricsStatusSecondaryButton?.text = "Chọn lời khác"
                binding.fullscreenLyricsStatusSecondaryButton?.visibility = if (hasTrack) View.VISIBLE else View.GONE
                binding.fullscreenLyricsStatusContainer?.visibility = View.VISIBLE
                updateLyricsAutoFollowButtonUi()
                startLyricsPositionLoop()
            }
            is LyricsUiState.TemporaryError -> {
                currentLyricsSourceSummary = "Lỗi kết nối dịch vụ lời bài hát"
                val titleText = state.message.ifBlank { "Không thể tải lời bài hát" }
                binding.lyricsSourceBadgeView?.text = currentLyricsSourceSummary
                binding.lyricsStatusTitleView?.text = titleText
                binding.lyricsStatusSubtitleView?.text = "Kiểm tra kết nối mạng hoặc thử lại sau."
                binding.lyricsStatusSubtitleView?.visibility = View.VISIBLE
                binding.lyricsStatusActionButton?.text = "Thử lại"
                binding.lyricsStatusSecondaryButton?.text = "Chọn lời khác"
                binding.lyricsStatusSecondaryButton?.visibility = if (hasTrack) View.VISIBLE else View.GONE
                binding.lyricsStatusContainer?.visibility = View.VISIBLE

                binding.fullscreenLyricsStatusTitleView?.text = titleText
                binding.fullscreenLyricsStatusSubtitleView?.text = "Kiểm tra kết nối mạng hoặc thử lại sau."
                binding.fullscreenLyricsStatusSubtitleView?.visibility = View.VISIBLE
                binding.fullscreenLyricsStatusActionButton?.text = "Thử lại"
                binding.fullscreenLyricsStatusSecondaryButton?.text = "Chọn lời khác"
                binding.fullscreenLyricsStatusSecondaryButton?.visibility = if (hasTrack) View.VISIBLE else View.GONE
                binding.fullscreenLyricsStatusContainer?.visibility = View.VISIBLE
                updateLyricsAutoFollowButtonUi()
                startLyricsPositionLoop()
            }
        }
        updateFullscreenLyricsPlayerChrome()
    }

    private fun updateLyricsAutoFollowButtonUi() {
        val isSynced = currentLyricsUiState is LyricsUiState.Synced && !isCandidatePickerOpen
        val showInlineBtn = isLyricsVisible && isSynced && !isLyricsAutoFollow
        binding.lyricsAutoFollowButton?.visibility = if (showInlineBtn) View.VISIBLE else View.GONE

        val fullscreenBtn = binding.fullscreenLyricsFollowButton
        if (fullscreenBtn != null) {
            if (!isSynced) {
                fullscreenBtn.visibility = View.INVISIBLE
            } else {
                fullscreenBtn.visibility = View.VISIBLE
                fullscreenBtn.text = "Theo nhạc"
                if (!isLyricsAutoFollow) {
                    fullscreenBtn.setBackgroundResource(R.drawable.bg_quality_chip_active)
                    fullscreenBtn.setTextColor(ContextCompat.getColor(this, R.color.accent_primary))
                    fullscreenBtn.alpha = 1.0f
                } else {
                    fullscreenBtn.setBackgroundResource(R.drawable.bg_quality_chip_inactive)
                    fullscreenBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    fullscreenBtn.alpha = 0.85f
                }
            }
        }
    }

    private fun syncActiveLyricsLineWithPlayer(
        forceScroll: Boolean,
        targetPositionMs: Long? = null,
    ) {
        val state = currentLyricsUiState as? LyricsUiState.Synced ?: return
        val posMs = targetPositionMs ?: browser?.currentPosition?.coerceAtLeast(0L) ?: return
        val newIndex = LrcParser.findActiveLineIndex(state.lines, posMs)
        val inlineChanged = syncedLyricsAdapter.updateActiveLineIndex(newIndex)
        val fullscreenChanged = fullscreenSyncedLyricsAdapter.updateActiveLineIndex(newIndex)
        val changed = inlineChanged || fullscreenChanged
        if (changed) {
            val lineStartMs = if (newIndex in state.lines.indices) state.lines[newIndex].startMs else -1L
            Log.i(TAG, "LYRICS_ACTIVE_LINE: index=$newIndex startMs=$lineStartMs posMs=$posMs autoFollow=$isLyricsAutoFollow")
        }
        if ((changed || forceScroll) && isLyricsAutoFollow && newIndex >= 0) {
            scrollToActiveLyricLine(newIndex, smooth = !forceScroll)
        }
    }

    private fun scrollToActiveLyricLine(index: Int, smooth: Boolean = true) {
        if (isLyricsVisible) {
            val rv = binding.lyricsSyncedRecyclerView
            val lm = rv?.layoutManager as? LinearLayoutManager
            if (rv != null && lm != null) {
                val rvHeight = rv.height
                if (rvHeight > 0) {
                    lm.scrollToPositionWithOffset(index, (rvHeight / 3).coerceAtLeast(48))
                } else {
                    rv.scrollToPosition(index)
                }
            }
        }
        if (isFullscreenLyricsVisible) {
            val rv = binding.fullscreenLyricsSyncedRecyclerView
            val lm = rv?.layoutManager as? LinearLayoutManager
            if (rv != null && lm != null) {
                val rvHeight = rv.height
                val density = resources.displayMetrics.density
                val estimatedItemHeightPx = (64f * density).toInt()
                if (rvHeight > 0 && smooth) {
                    val scroller = object : LinearSmoothScroller(this) {
                        override fun calculateDtToFit(
                            viewStart: Int,
                            viewEnd: Int,
                            boxStart: Int,
                            boxEnd: Int,
                            snapPreference: Int,
                        ): Int {
                            val viewportHeight = boxEnd - boxStart
                            val itemHeight = viewEnd - viewStart
                            val targetTop = boxStart + LyricsSessionController.computeCenteredScrollOffset(
                                viewportHeightPx = viewportHeight,
                                itemHeightPx = itemHeight,
                            )
                            return targetTop - viewStart
                        }
                    }
                    scroller.targetPosition = index
                    lm.startSmoothScroll(scroller)
                } else if (rvHeight > 0) {
                    val offset = LyricsSessionController.computeCenteredScrollOffset(
                        viewportHeightPx = rvHeight,
                        itemHeightPx = estimatedItemHeightPx,
                    )
                    lm.scrollToPositionWithOffset(index, offset)
                } else {
                    rv.scrollToPosition(index)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Collection Playback Actions: Play All / Shuffle / Add All / Play From Here
    // These NEVER use the RecyclerView page — they fetch ALL tracks from backend.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Data class encoding the collection type and its parameters for a backend call.
     * Derived from the current [navManager.currentParentId].
     */
    private data class CollectionParams(
        val kind: String,
        val artist: String? = null,
        val album: String? = null,
        val root: Int? = null,
        val path: String? = null,
        val playlistId: Int? = null,
    )

    /**
     * Derives [CollectionParams] from the current parentId.
     * Returns null if the parentId is not a supported collection context.
     */
    private fun buildCollectionParams(parentId: String): CollectionParams? {
        return when {
            parentId.startsWith(RemoteCatalog.ARTIST_PREFIX) -> {
                val artist = RemoteCatalog.safeDecode(parentId.removePrefix(RemoteCatalog.ARTIST_PREFIX))
                CollectionParams(kind = "artist", artist = artist)
            }
            parentId.startsWith(RemoteCatalog.ALBUM_PREFIX) -> {
                val payload = parentId.removePrefix(RemoteCatalog.ALBUM_PREFIX)
                val parts = payload.split("::", limit = 2)
                val artist = if (parts.size == 2) RemoteCatalog.safeDecode(parts[0]) else ""
                val album = if (parts.size == 2) RemoteCatalog.safeDecode(parts[1]) else RemoteCatalog.safeDecode(parts[0])
                CollectionParams(kind = "album", artist = artist, album = album)
            }
            parentId.startsWith(RemoteCatalog.DRIVE_FOLDER_PREFIX) -> {
                val payload = parentId.removePrefix(RemoteCatalog.DRIVE_FOLDER_PREFIX)
                val colonIdx = payload.indexOf(':')
                if (colonIdx < 0) return null
                val rootIdx = payload.substring(0, colonIdx).toIntOrNull() ?: return null
                val folderPath = RemoteCatalog.safeDecode(payload.substring(colonIdx + 1))
                CollectionParams(kind = "drive_folder", root = rootIdx, path = folderPath)
            }
            parentId.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX) -> {
                val rootIdx = parentId.removePrefix(RemoteCatalog.DRIVE_ROOT_PREFIX).toIntOrNull() ?: return null
                CollectionParams(kind = "drive_folder", root = rootIdx, path = "")
            }
            parentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX) -> {
                val playlistId = parentId.removePrefix(RemoteCatalog.PLAYLIST_PREFIX).toIntOrNull() ?: return null
                CollectionParams(kind = "playlist", playlistId = playlistId)
            }
            else -> null
        }
    }

    /**
     * Fetches ALL playable tracks for the current collection in background,
     * then calls [playQueue] (with optional shuffle). Stale-guard: if the user
     * navigated away before the fetch completes, the response is discarded.
     *
     * @param explicitParams When provided, uses these params directly instead of reading navManager.
     *                       Used by folder long-press dialog to play a folder that isn't navigated into yet.
     */
    private fun triggerCollectionPlayback(shuffle: Boolean, explicitParams: CollectionParams? = null) {
        val params = explicitParams ?: buildCollectionParams(navManager.currentParentId) ?: return
        val requestId = ++currentCollectionRequestId
        val effectiveSort = if (params.kind == "playlist") "" else navManager.currentSort
        binding.collectionPlayAllButton.isEnabled = false
        binding.collectionShuffleButton.isEnabled = false
        collectionFetchExecutor.submit {
            val result = runCatching {
                uiCatalog.fetchCollectionPlayback(
                    kind = params.kind,
                    artist = params.artist,
                    album = params.album,
                    root = params.root,
                    path = params.path,
                    playlistId = params.playlistId,
                    sort = effectiveSort,
                )
            }
            runOnUiThread {
                binding.collectionPlayAllButton.isEnabled = true
                binding.collectionShuffleButton.isEnabled = true
                // Stale-guard: discard if user has navigated away
                if (requestId != currentCollectionRequestId) return@runOnUiThread
                result.onSuccess { res ->
                    if (res.items.isEmpty()) {
                        Toast.makeText(this, "Không có bài hát có thể phát", Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }
                    if (res.skipped > 0) {
                        Toast.makeText(this, "Đã bỏ qua ${res.skipped} bài không thể phát", Toast.LENGTH_SHORT).show()
                    }
                    val mediaItems = res.items.map { uiCatalog.trackItem(it) }
                        .map { uiCatalog.withCurrentQuality(it) }
                    val ordered = if (shuffle) mediaItems.shuffled() else mediaItems
                    playQueue(ordered, 0)
                }.onFailure {
                    Toast.makeText(this, "Không thể tải danh sách phát", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    /**
     * Fetches ALL playable tracks for the current collection and appends them to the queue.
     */
    private fun triggerCollectionAddToQueue() {
        val params = buildCollectionParams(navManager.currentParentId) ?: return
        val requestId = ++currentCollectionRequestId
        val effectiveSort = if (params.kind == "playlist") "" else navManager.currentSort
        binding.collectionAddAllButton.isEnabled = false
        collectionFetchExecutor.submit {
            val result = runCatching {
                uiCatalog.fetchCollectionPlayback(
                    kind = params.kind,
                    artist = params.artist,
                    album = params.album,
                    root = params.root,
                    path = params.path,
                    playlistId = params.playlistId,
                    sort = effectiveSort,
                )
            }
            runOnUiThread {
                binding.collectionAddAllButton.isEnabled = true
                if (requestId != currentCollectionRequestId) return@runOnUiThread
                result.onSuccess { res ->
                    if (res.items.isEmpty()) {
                        Toast.makeText(this, "Không có bài hát có thể phát", Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }
                    val player = browser
                    if (player == null) {
                        Toast.makeText(this, "Chưa kết nối trình phát", Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }
                    val mediaItems = res.items.map { uiCatalog.trackItem(it) }
                        .map { uiCatalog.withCurrentQuality(it) }
                    mediaItems.forEach { player.addMediaItem(it) }
                    if (res.skipped > 0) {
                        Toast.makeText(this, "Đã thêm ${res.playable} bài (bỏ qua ${res.skipped})", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Đã thêm ${res.playable} bài vào hàng đợi", Toast.LENGTH_SHORT).show()
                    }
                    schedulePrewarmQueue(buildCurrentQueueList(), player.currentMediaItemIndex)
                }.onFailure {
                    Toast.makeText(this, "Không thể tải danh sách phát", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun triggerCollectionDownloadAll() {
        val parentId = navManager.currentParentId
        val params = buildCollectionParams(parentId)
        val isPlaylist = parentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX)
        val collectionTitle = navManager.currentLabel.ifBlank { if (isPlaylist) "Playlist" else "Bộ sưu tập" }
        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)

        fun onTracksReady(tracks: List<com.bsdung.musicassistantcar.data.CarTrack>) {
            if (tracks.isEmpty()) {
                Toast.makeText(this, "Không có bài hát để tải", Toast.LENGTH_SHORT).show()
                return
            }

            val classification = dm.classifyTracksForDownload(tracks)
            val total = classification.totalTracks
            val alreadyOffline = classification.alreadyOfflineTracks.size
            val inProgress = classification.inProgressTracks.size
            val eligibleNew = classification.eligibleNewTracks.size
            val knownBytes = classification.knownTotalBytes
            val unknownCount = classification.unknownSizeCount

            if (eligibleNew == 0) {
                if (alreadyOffline == total) {
                    Toast.makeText(this, "Tất cả bài hát đã được tải offline", Toast.LENGTH_SHORT).show()
                } else if (inProgress > 0) {
                    Toast.makeText(this, "Tất cả bài hát còn lại đang trong hàng đợi tải", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Không có bài hát mới cần tải", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val precheck = dm.checkBulkDownloadStorage(classification.eligibleNewTracks)
            val summary = dm.getStorageSummary()

            if (!precheck.isAllowed) {
                val builder = AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                    .setTitle(precheck.title ?: "Không đủ dung lượng")
                    .setMessage(precheck.message ?: "Không đủ dung lượng để tải.")
                    .setNegativeButton("Đóng", null)
                if (precheck.status == com.bsdung.musicassistantcar.playback.StoragePrecheckStatus.STORAGE_TARGET_UNAVAILABLE) {
                    builder.setPositiveButton("Đổi sang Bộ nhớ trong") { _, _ ->
                        showConfirmChangeStorageTargetDialog(com.bsdung.musicassistantcar.playback.OfflineStorageTarget.INTERNAL)
                    }
                } else {
                    builder.setPositiveButton("Đã hiểu", null)
                }
                builder.show()
                return
            }

            val msgBuilder = StringBuilder()
            msgBuilder.append("Danh sách gồm $total bài hát:\n")
            if (alreadyOffline > 0) {
                msgBuilder.append("• $alreadyOffline bài đã tải xong (sẽ bỏ qua)\n")
            }
            if (inProgress > 0) {
                msgBuilder.append("• $inProgress bài đang trong hàng đợi (sẽ bỏ qua)\n")
            }
            msgBuilder.append("• Sẽ tải: $eligibleNew bài")
            if (knownBytes > 0L) {
                msgBuilder.append(" (${dm.formatStorageBytes(knownBytes)})")
            }
            if (unknownCount > 0) {
                msgBuilder.append(" [có $unknownCount bài chưa rõ dung lượng]")
            }
            msgBuilder.append("\n\n")
            msgBuilder.append("Nơi lưu: ${dm.formatStorageTargetLabel(summary.selectedTarget)}\n")
            msgBuilder.append("Offline hiện tại: ${dm.formatStorageBytes(summary.usedBytes)}\n")
            msgBuilder.append("Giới hạn: ${dm.formatCapLabel(summary.capBytes)}\n")
            msgBuilder.append("Còn trống nơi lưu: ${dm.formatStorageBytes(summary.availableBytes)}\n")
            msgBuilder.append("Mạng tải: ${dm.formatNetworkPolicyLabel()}\n\n")
            msgBuilder.append("Tiếp tục tải?")

            val dialogTitle = if (isPlaylist) "Tải offline playlist" else "Tải offline danh sách"
            AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                .setTitle(dialogTitle)
                .setMessage(msgBuilder.toString())
                .setPositiveButton("Tải về") { _, _ ->
                    val sourceType = if (isPlaylist) "PLAYLIST" else (params?.kind?.uppercase() ?: "CURRENT_REMOTE_LIST")
                    val sourceId = if (isPlaylist) (params?.playlistId?.toString() ?: parentId) else parentId
                    val batch = com.bsdung.musicassistantcar.playback.DownloadBatch(
                        sourceType = sourceType,
                        sourceId = sourceId,
                        sourceDisplayName = collectionTitle,
                        trackIds = tracks.map { it.id.toLong() },
                        initialEligibleCount = eligibleNew,
                        knownTotalBytes = knownBytes,
                        hasUnknownSizes = unknownCount > 0,
                    )
                    val networkAllowed = dm.isNetworkAllowedForDownload()
                    val enqueued = dm.enqueueBatch(batch, classification.eligibleNewTracks)
                    libraryAdapter.notifyDataSetChanged()
                    val toastMsg = if (!networkAllowed) {
                        "Đã thêm $enqueued bài vào hàng đợi — Đang chờ Wi-Fi"
                    } else {
                        "Đã thêm $enqueued bài vào hàng đợi tải offline"
                    }
                    Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
                    if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                        loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        if (params != null) {
            binding.collectionDownloadAllButton.isEnabled = false
            collectionFetchExecutor.submit {
                val result = runCatching {
                    val effectiveSort = if (params.kind == "playlist") "" else navManager.currentSort
                    uiCatalog.fetchCollectionPlayback(
                        kind = params.kind,
                        artist = params.artist,
                        album = params.album,
                        root = params.root,
                        path = params.path,
                        playlistId = params.playlistId,
                        sort = effectiveSort,
                    )
                }
                runOnUiThread {
                    binding.collectionDownloadAllButton.isEnabled = true
                    result.onSuccess { res ->
                        onTracksReady(res.items)
                    }.onFailure { exc ->
                        Toast.makeText(this, "Không thể lấy danh sách bài hát: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            val tracks = libraryAdapter.getItems().filterIsInstance<LibraryItem.Track>().map { track ->
                val tid = RemoteCatalog.canonicalTrackMediaId(track.mediaItem)?.removePrefix(RemoteCatalog.TRACK_PREFIX)?.toIntOrNull()
                    ?: track.id.removePrefix(RemoteCatalog.TRACK_PREFIX).toIntOrNull() ?: 0
                com.bsdung.musicassistantcar.data.CarTrack(
                    id = tid,
                    title = track.title,
                    artist = track.artist,
                    album = track.album.orEmpty(),
                    status = "ready",
                    duration = (track.durationMs ?: 0L) / 1000.0,
                    sourceFormat = track.format,
                    sourceSizeBytes = track.sizeBytes,
                    sourceCodec = track.codec,
                    sourceBitrate = track.bitrate,
                    sourceSampleRate = track.sampleRate,
                    sourceChannels = track.channels,
                    loudnessLufs = track.mediaItem.mediaMetadata.extras?.takeIf { it.containsKey("loudness_lufs") }?.getDouble("loudness_lufs"),
                    truePeakDbtp = track.mediaItem.mediaMetadata.extras?.takeIf { it.containsKey("true_peak_dbtp") }?.getDouble("true_peak_dbtp"),
                    loudnessRangeLu = track.mediaItem.mediaMetadata.extras?.takeIf { it.containsKey("loudness_range_lu") }?.getDouble("loudness_range_lu"),
                    loudnessAnalysisVersion = track.mediaItem.mediaMetadata.extras?.takeIf { it.containsKey("loudness_analysis_version") }?.getInt("loudness_analysis_version"),
                )
            }
            onTracksReady(tracks)
        }
    }

    private fun triggerPlaylistDeleteOffline() {
        val parentId = navManager.currentParentId
        if (!parentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX)) return
        val playlistTitle = navManager.currentLabel.ifBlank { "Playlist" }
        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)

        val currentItems = libraryAdapter.getItems().filterIsInstance<LibraryItem.Track>()
        val offlineTrackIds = currentItems.mapNotNull { track ->
            val tid = RemoteCatalog.canonicalTrackMediaId(track.mediaItem)?.removePrefix(RemoteCatalog.TRACK_PREFIX)?.toLongOrNull()
                ?: track.id.removePrefix(RemoteCatalog.TRACK_PREFIX).toLongOrNull()
            tid?.takeIf { dm.isOfflineReady(it) }
        }

        if (offlineTrackIds.isEmpty()) {
            Toast.makeText(this, "Không có bài hát offline nào trong danh sách này", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Xóa bản offline của playlist")
            .setMessage("Xóa ${offlineTrackIds.size} bài hát đã tải offline trong \"$playlistTitle\" khỏi bộ nhớ thiết bị?\n\nCác bài hát trong kho MeloVault vẫn được giữ nguyên và có thể phát trực tuyến.")
            .setPositiveButton("Xóa bản offline") { _, _ ->
                stopPlaybackIfDeletedOfflineTrack(offlineTrackIds.toSet())
                val deleted = dm.deleteOfflinePlaylist(offlineTrackIds)
                libraryAdapter.notifyDataSetChanged()
                updateCollectionActionBar()
                updateOfflineStorageDashboard()
                Toast.makeText(this, "Đã xóa bản offline của $deleted bài hát", Toast.LENGTH_SHORT).show()
                loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel, reconcilePlaylistSync = false)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun extractCurrentAdapterTracks(): List<com.bsdung.musicassistantcar.data.CarTrack> {
        return libraryAdapter.getItems().filterIsInstance<LibraryItem.Track>().map { track ->
            val tid = RemoteCatalog.canonicalTrackMediaId(track.mediaItem)?.removePrefix(RemoteCatalog.TRACK_PREFIX)?.toIntOrNull()
                ?: track.id.removePrefix(RemoteCatalog.TRACK_PREFIX).toIntOrNull() ?: 0
            com.bsdung.musicassistantcar.data.CarTrack(
                id = tid,
                title = track.title,
                artist = track.artist,
                album = track.album.orEmpty(),
                status = "ready",
                duration = (track.durationMs ?: 0L) / 1000.0,
                sourceFormat = track.format,
                sourceSizeBytes = track.sizeBytes,
                sourceCodec = track.codec,
                sourceBitrate = track.bitrate,
                sourceSampleRate = track.sampleRate,
                sourceChannels = track.channels,
                sourceType = track.mediaItem.mediaMetadata.extras?.getString("source_type"),
                loudnessLufs = track.mediaItem.mediaMetadata.extras?.takeIf { it.containsKey("loudness_lufs") }?.getDouble("loudness_lufs"),
                truePeakDbtp = track.mediaItem.mediaMetadata.extras?.takeIf { it.containsKey("true_peak_dbtp") }?.getDouble("true_peak_dbtp"),
                loudnessRangeLu = track.mediaItem.mediaMetadata.extras?.takeIf { it.containsKey("loudness_range_lu") }?.getDouble("loudness_range_lu"),
                loudnessAnalysisVersion = track.mediaItem.mediaMetadata.extras?.takeIf { it.containsKey("loudness_analysis_version") }?.getInt("loudness_analysis_version"),
            )
        }
    }

    private fun updatePlaylistSyncBarPassive() {
        val parentId = navManager.currentParentId
        val showSyncBar = !isEditMode && !isQueueVisible && !isLyricsVisible && parentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX)
        if (!showSyncBar) {
            binding.playlistSyncBar.visibility = View.GONE
            return
        }

        val playlistId = parentId.removePrefix(RemoteCatalog.PLAYLIST_PREFIX).toIntOrNull()
        if (playlistId == null || playlistId <= 0) {
            binding.playlistSyncBar.visibility = View.GONE
            return
        }

        binding.playlistSyncBar.visibility = View.VISIBLE
        val coordinator = com.bsdung.musicassistantcar.playback.OfflinePlaylistSyncCoordinator.getDefault(this)
        val playlistName = navManager.currentLabel.ifBlank { "Playlist #$playlistId" }
        val tracks = cachedPlaylistTracks[playlistId] ?: extractCurrentAdapterTracks()
        val skippedCount = cachedPlaylistSkippedCount[playlistId] ?: 0

        val status = coordinator.getPlaylistSyncStatus(
            playlistId = playlistId,
            playlistName = playlistName,
            tracks = tracks,
            additionalUnsupportedCount = skippedCount,
        )

        binding.btnPlaylistSyncToggle.text = status.toggleLabel
        if (status.isWatched) {
            binding.btnPlaylistSyncNow.visibility = View.VISIBLE
            binding.txtPlaylistSyncStatus.visibility = View.VISIBLE
            val baseLine = if (!status.secondaryStatusText.isNullOrBlank() && !status.statusText.contains(status.secondaryStatusText)) {
                "${status.statusText} · ${status.secondaryStatusText}"
            } else {
                status.statusText
            }
            val bgInfo = coordinator.formatBackgroundSyncInfoLine()
            val line = if (!baseLine.contains(bgInfo)) {
                "$baseLine · $bgInfo"
            } else {
                baseLine
            }
            binding.txtPlaylistSyncStatus.text = line
        } else {
            binding.btnPlaylistSyncNow.visibility = View.GONE
            binding.txtPlaylistSyncStatus.visibility = View.GONE
        }
    }

    private fun triggerStartupPlaylistSyncOnce() {
        if (hasRunStartupPlaylistSync) return
        hasRunStartupPlaylistSync = true
        val coordinator = com.bsdung.musicassistantcar.playback.OfflinePlaylistSyncCoordinator.getDefault(this)
        if (coordinator.getWatchedPlaylistIds().isEmpty()) return

        collectionFetchExecutor.submit {
            val summary = runCatching {
                coordinator.syncAllWatchedPlaylistsFromRemote(CarApiClient())
            }.getOrNull() ?: return@submit
            for (res in summary.reconciledResults) {
                cachedPlaylistTracks[res.playlistId] = res.classification.eligibleTracks
                cachedPlaylistSkippedCount[res.playlistId] = res.classification.unsupportedCount
            }
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
                updateCollectionActionBar()
                updateDownloadQueueHeader()
                updateOfflineStorageDashboard()
            }
        }
    }

    private fun reconcileOpenedPlaylistIfNeeded(
        parentId: String,
        playlistLabel: String,
        triggerSync: Boolean = true,
    ) {
        val playlistId = parentId.removePrefix(RemoteCatalog.PLAYLIST_PREFIX).toIntOrNull() ?: return
        val coordinator = com.bsdung.musicassistantcar.playback.OfflinePlaylistSyncCoordinator.getDefault(this)
        val isWatched = coordinator.isPlaylistWatched(playlistId)
        val total = navManager.currentCollectionTotal ?: -1
        val adapterTracks = extractCurrentAdapterTracks()

        if (!isWatched && total in 0..adapterTracks.size) {
            cachedPlaylistTracks[playlistId] = adapterTracks
            cachedPlaylistSkippedCount[playlistId] = (total - adapterTracks.size).coerceAtLeast(0)
            updatePlaylistSyncBarPassive()
            return
        }

        collectionFetchExecutor.submit {
            val res = runCatching {
                uiCatalog.fetchCollectionPlayback(
                    kind = "playlist",
                    playlistId = playlistId,
                )
            }.getOrNull()

            val tracks = res?.items ?: adapterTracks
            val skipped = res?.skipped ?: 0
            cachedPlaylistTracks[playlistId] = tracks
            cachedPlaylistSkippedCount[playlistId] = skipped

            if (triggerSync && coordinator.isPlaylistWatched(playlistId)) {
                coordinator.reconcilePlaylist(
                    playlistId = playlistId,
                    playlistName = playlistLabel.ifBlank { "Playlist #$playlistId" },
                    tracks = tracks,
                    additionalUnsupportedCount = skipped,
                )
            }

            runOnUiThread {
                if (navManager.currentParentId == parentId) {
                    libraryAdapter.notifyDataSetChanged()
                    updateCollectionActionBar()
                    updateDownloadQueueHeader()
                }
            }
        }
    }

    private fun handlePlaylistSyncToggleClicked() {
        val parentId = navManager.currentParentId
        if (!parentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX)) return
        val playlistId = parentId.removePrefix(RemoteCatalog.PLAYLIST_PREFIX).toIntOrNull() ?: return
        val playlistTitle = navManager.currentLabel.ifBlank { "Playlist #$playlistId" }
        val coordinator = com.bsdung.musicassistantcar.playback.OfflinePlaylistSyncCoordinator.getDefault(this)
        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)

        if (coordinator.isPlaylistWatched(playlistId)) {
            AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                .setTitle("Tắt tự động đồng bộ offline?")
                .setMessage("Các bài đã tải sẽ được giữ lại trên thiết bị.")
                .setPositiveButton("TẮT") { _, _ ->
                    coordinator.disableWatch(playlistId)
                    updatePlaylistSyncBarPassive()
                    Toast.makeText(this, "Đã tắt Luôn giữ offline cho \"$playlistTitle\"", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("HỦY", null)
                .show()
            return
        }

        binding.btnPlaylistSyncToggle.isEnabled = false
        collectionFetchExecutor.submit {
            val playbackRes = runCatching {
                uiCatalog.fetchCollectionPlayback(
                    kind = "playlist",
                    playlistId = playlistId,
                )
            }.getOrNull()

            runOnUiThread {
                binding.btnPlaylistSyncToggle.isEnabled = true
                val tracks = playbackRes?.items ?: cachedPlaylistTracks[playlistId] ?: extractCurrentAdapterTracks()
                val skipped = playbackRes?.skipped ?: cachedPlaylistSkippedCount[playlistId] ?: 0
                cachedPlaylistTracks[playlistId] = tracks
                cachedPlaylistSkippedCount[playlistId] = skipped

                val classification = coordinator.classifyPlaylist(
                    playlistId = playlistId,
                    playlistName = playlistTitle,
                    tracks = tracks,
                    additionalUnsupportedCount = skipped,
                )

                // Section 8: Already 100% offline playlist enables immediately without modal or service start
                if (classification.isAllEligibleOffline) {
                    coordinator.enableWatch(playlistId, playlistTitle)
                    updatePlaylistSyncBarPassive()
                    Toast.makeText(this, "Luôn giữ offline: Bật · ✓ Đã đồng bộ offline", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                val prompt = coordinator.buildEnableConfirmationPrompt(playlistTitle, classification)
                AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                    .setTitle(prompt.title)
                    .setMessage(prompt.message)
                    .setPositiveButton(prompt.positiveButtonText) { _, _ ->
                        val reconcileRes = coordinator.enableWatchAndSync(
                            playlistId = playlistId,
                            displayName = playlistTitle,
                            tracks = tracks,
                            additionalUnsupportedCount = skipped,
                        )
                        libraryAdapter.notifyDataSetChanged()
                        updateCollectionActionBar()
                        updateDownloadQueueHeader()
                        updateOfflineStorageDashboard()

                        val precheck = reconcileRes.storagePrecheck
                        if (precheck != null && !precheck.isAllowed) {
                            AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                                .setTitle(precheck.title ?: "Không đủ dung lượng")
                                .setMessage(precheck.message ?: "Không đủ dung lượng để đồng bộ playlist.")
                                .setPositiveButton("Đã hiểu", null)
                                .show()
                        } else {
                            val toastMsg = if (!dm.isNetworkAllowedForDownload() && reconcileRes.enqueuedCount > 0) {
                                "Đã bật đồng bộ offline — Đang chờ Wi-Fi (${reconcileRes.enqueuedCount} bài)"
                            } else if (reconcileRes.enqueuedCount > 0) {
                                "Đang đồng bộ offline ${reconcileRes.enqueuedCount} bài"
                            } else {
                                "Đã bật Luôn giữ offline"
                            }
                            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(prompt.negativeButtonText, null)
                    .show()
            }
        }
    }

    private fun handlePlaylistSyncNowClicked() {
        val parentId = navManager.currentParentId
        if (!parentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX)) return
        val playlistId = parentId.removePrefix(RemoteCatalog.PLAYLIST_PREFIX).toIntOrNull() ?: return
        val playlistTitle = navManager.currentLabel.ifBlank { "Playlist #$playlistId" }
        val coordinator = com.bsdung.musicassistantcar.playback.OfflinePlaylistSyncCoordinator.getDefault(this)
        if (!coordinator.isPlaylistWatched(playlistId)) return

        binding.btnPlaylistSyncNow.isEnabled = false
        collectionFetchExecutor.submit {
            val playbackRes = runCatching {
                uiCatalog.fetchCollectionPlayback(
                    kind = "playlist",
                    playlistId = playlistId,
                )
            }.getOrNull()

            runOnUiThread {
                binding.btnPlaylistSyncNow.isEnabled = true
                val tracks = playbackRes?.items ?: cachedPlaylistTracks[playlistId] ?: extractCurrentAdapterTracks()
                val skipped = playbackRes?.skipped ?: cachedPlaylistSkippedCount[playlistId] ?: 0
                cachedPlaylistTracks[playlistId] = tracks
                cachedPlaylistSkippedCount[playlistId] = skipped

                val reconcileRes = coordinator.reconcilePlaylist(
                    playlistId = playlistId,
                    playlistName = playlistTitle,
                    tracks = tracks,
                    additionalUnsupportedCount = skipped,
                )
                libraryAdapter.notifyDataSetChanged()
                updateCollectionActionBar()
                updateDownloadQueueHeader()
                updateOfflineStorageDashboard()

                val precheck = reconcileRes.storagePrecheck
                if (precheck != null && !precheck.isAllowed) {
                    AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                        .setTitle(precheck.title ?: "Không đủ dung lượng")
                        .setMessage(precheck.message ?: "Không đủ dung lượng để đồng bộ playlist.")
                        .setPositiveButton("Đã hiểu", null)
                        .show()
                } else {
                    val toastMsg = when {
                        reconcileRes.enqueuedCount > 0 && reconcileRes.status.state == com.bsdung.musicassistantcar.playback.PlaylistSyncState.WAITING_FOR_WIFI ->
                            "Đã thêm ${reconcileRes.enqueuedCount} bài — Đang chờ Wi-Fi"
                        reconcileRes.enqueuedCount > 0 ->
                            "Đang đồng bộ thêm ${reconcileRes.enqueuedCount} bài offline"
                        reconcileRes.status.state == com.bsdung.musicassistantcar.playback.PlaylistSyncState.SYNCED ->
                            "✓ Đã đồng bộ offline"
                        reconcileRes.status.state == com.bsdung.musicassistantcar.playback.PlaylistSyncState.WAITING_FOR_WIFI ->
                            "Đang chờ kết nối Wi-Fi để tải tiếp"
                        else ->
                            reconcileRes.status.statusText
                    }
                    Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateDownloadQueueHeader() {
        if (isQueueVisible || isLyricsVisible || navManager.currentParentId != RemoteCatalog.DOWNLOADING_ID) {
            binding.downloadQueueHeader.visibility = View.GONE
            return
        }

        binding.downloadQueueHeader.visibility = View.VISIBLE
        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
        val progress = dm.getActiveBatchProgress()
        val waitingForWifi = dm.isQueueWaitingForWifi()

        if (progress != null) {
            val title = progress.sourceDisplayName.ifBlank { "Hàng đợi tải offline" }
            binding.queueBatchTitle.text = title
            binding.queueTrackCount.text = "${progress.completedTracks}/${progress.totalTracks} bài"

            val currTitle = progress.currentTrackTitle
            val currProgress = progress.currentTrackProgress
            if (waitingForWifi) {
                binding.queueCurrentTrackText.text = "Đang chờ Wi-Fi để tiếp tục tải"
            } else if (currTitle != null) {
                val dlMb = String.format(Locale.US, "%.1f", (currProgress?.bytesDownloaded ?: 0L) / (1024.0 * 1024.0))
                val totBytes = currProgress?.totalBytes ?: 0L
                val infoText = if (totBytes > 0L) {
                    val totMb = String.format(Locale.US, "%.1f", totBytes / (1024.0 * 1024.0))
                    val pct = (((currProgress?.bytesDownloaded ?: 0L) * 100L) / totBytes).toInt().coerceIn(0, 100)
                    "Đang tải: $currTitle · $pct% ($dlMb / $totMb MB)"
                } else {
                    "Đang tải: $currTitle · $dlMb MB"
                }
                binding.queueCurrentTrackText.text = infoText
            } else if (progress.inProgressTracks > 0) {
                binding.queueCurrentTrackText.text = "Đang chờ tải..."
            } else {
                binding.queueCurrentTrackText.text = "Hàng đợi hoàn tất"
            }

            if (progress.totalBytesKnown > 0L) {
                val pct = ((progress.downloadedBytes * 100L) / progress.totalBytesKnown).toInt().coerceIn(0, 100)
                binding.queueProgressBar.isIndeterminate = false
                binding.queueProgressBar.progress = pct
            } else {
                val pct = if (progress.totalTracks > 0) {
                    ((progress.completedTracks * 100) / progress.totalTracks).coerceIn(0, 100)
                } else {
                    0
                }
                binding.queueProgressBar.isIndeterminate = false
                binding.queueProgressBar.progress = pct
            }
        } else {
            val allJobs = dm.getActiveJobMediaItems()
            binding.queueBatchTitle.text = "Hàng đợi tải offline"
            binding.queueTrackCount.text = "${allJobs.size} bài"
            binding.queueCurrentTrackText.text = when {
                waitingForWifi -> "Đang chờ Wi-Fi để tiếp tục tải"
                allJobs.isNotEmpty() -> "Đang xử lý hàng đợi"
                else -> "Không có tác vụ tải"
            }
            binding.queueProgressBar.isIndeterminate = false
            binding.queueProgressBar.progress = 0
        }
    }

    /**
     * Play From Here: fetches the complete collection, resolves the canonical identity of
     * [selectedTrack] (bridging "drive-file:" and "track:" namespaces), and starts playback
     * at the matching position. Tracks *before* the selected item are NOT included in the
     * Media3 queue — queue becomes [selected, selected+1, ..., end].
     *
     * If the selected item cannot be identified in the full collection a toast is shown and
     * current playback is left untouched. There is NO silent fallback to index 0.
     */
    private fun playFromHere(selectedTrack: LibraryItem.Track) {
        val params = buildCollectionParams(navManager.currentParentId) ?: return
        val requestId = ++currentCollectionRequestId
        val effectiveSort = if (params.kind == "playlist") "" else navManager.currentSort
        // Resolve the canonical "track:<id>" identity of the browse row that was long-pressed.
        val selectedCanonicalId = RemoteCatalog.canonicalTrackMediaId(selectedTrack.mediaItem)
        collectionFetchExecutor.submit {
            val result = runCatching {
                uiCatalog.fetchCollectionPlayback(
                    kind = params.kind,
                    artist = params.artist,
                    album = params.album,
                    root = params.root,
                    path = params.path,
                    playlistId = params.playlistId,
                    sort = effectiveSort,
                    recursive = false, // Direct-context only for Play From Here
                )
            }
            runOnUiThread {
                if (requestId != currentCollectionRequestId) return@runOnUiThread
                result.onSuccess { res ->
                    if (res.items.isEmpty()) {
                        Toast.makeText(this, "Không có bài hát có thể phát", Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }
                    val mediaItems = res.items.map { uiCatalog.trackItem(it) }
                        .map { uiCatalog.withCurrentQuality(it) }

                    // Match via canonical "track:<id>" — each collection item already has
                    // mediaId = "track:<id>" so canonicalTrackMediaId() returns it directly.
                    // For the selected browse row it resolves drive-file extras or track: directly.
                    val startIndex = if (selectedCanonicalId != null) {
                        mediaItems.indexOfFirst { item ->
                            RemoteCatalog.canonicalTrackMediaId(item) == selectedCanonicalId
                        }
                    } else -1

                    if (startIndex < 0) {
                        // ID mismatch is an explicit error — do NOT fall back to index 0.
                        Toast.makeText(
                            this,
                            "Không tìm thấy bài đã chọn trong danh sách",
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@onSuccess
                    }

                    if (res.skipped > 0) {
                        Toast.makeText(this, "Đã bỏ qua ${res.skipped} bài không thể phát", Toast.LENGTH_SHORT).show()
                    }

                    // Slice: only tracks from selected position onward become the queue.
                    // Queue becomes [D, E, F], selected plays as item 0.
                    val suffix = mediaItems.drop(startIndex)
                    playQueue(suffix, 0)
                }.onFailure {
                    Toast.makeText(this, "Không thể tải danh sách phát", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    private fun isCompactPlayerLayout(): Boolean =
        findViewById<View>(R.id.mainScrollContainer) != null

    private fun renderQueue() {
        val player = browser
        if (player == null) {
            binding.queueTitleView.text = QueueHelper.formatQueueTitle(-1, 0)
            binding.queueSummaryView.text = QueueHelper.formatQueueSummary(-1, 0)
            queueAdapter.submitItems(listOf(QueueListItem.Empty("Hàng đợi đang trống", isFullQueueEmpty = true)))
            binding.queueClearButton.visibility = View.GONE
            binding.queueSavePlaylistButton.visibility = View.GONE
            return
        }

        val queueItems = buildCurrentQueueList()
        val currentIndex = player.currentMediaItemIndex
        val isPlaying = player.isPlaying
        binding.queueTitleView.text = QueueHelper.formatQueueTitle(currentIndex, queueItems.size)
        binding.queueSummaryView.text = QueueHelper.formatQueueSummary(currentIndex, queueItems.size)

        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
        val listItems = QueueHelper.buildQueueList(
            queue = queueItems,
            currentIndex = currentIndex,
            isPlaying = isPlaying,
            sourceBadgeResolver = { item ->
                val tid = RemoteCatalog.canonicalTrackMediaId(item)
                    ?.removePrefix(RemoteCatalog.TRACK_PREFIX)
                    ?.toLongOrNull()
                    ?: item.mediaId.removePrefix(RemoteCatalog.TRACK_PREFIX).toLongOrNull()
                val isOffline = tid != null && dm.isOfflineReady(tid)
                QueueHelper.deriveSourceBadge(item, isOffline)
            },
        )
        queueAdapter.submitItems(listItems)

        val hasUpcoming = currentIndex in 0 until (queueItems.size - 1)
        binding.queueClearButton.visibility = if (hasUpcoming) View.VISIBLE else View.GONE
        binding.queueSavePlaylistButton.visibility = if (queueItems.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun playNext(track: LibraryItem.Track) {
        val player = browser ?: return
        val currentQueue = buildCurrentQueueList()
        val currentIndex = player.currentMediaItemIndex
        val preparedItem = uiCatalog.withCurrentQuality(track.mediaItem)
        val action = QueueHelper.planPlayNext(currentQueue, currentIndex, preparedItem)
        Log.i(
            TAG,
            "QUEUE_PLAY_NEXT: mediaId=${track.id} currentIndex=$currentIndex queueSize=${currentQueue.size} action=${action.javaClass.simpleName}",
        )

        when (action) {
            is PlayNextAction.StartNewQueue -> {
                playQueue(listOf(preparedItem), 0)
                Toast.makeText(this, "Đang phát: ${MetadataDisplayHelper.cleanTitle(track.title)}", Toast.LENGTH_SHORT).show()
            }
            is PlayNextAction.AlreadyCurrent -> {
                Toast.makeText(this, "Bài hát đang phát", Toast.LENGTH_SHORT).show()
            }
            is PlayNextAction.Move -> {
                player.moveMediaItem(action.fromIndex, action.toIndex)
                Toast.makeText(this, "Đã chuyển lên phát tiếp theo", Toast.LENGTH_SHORT).show()
                schedulePrewarmQueue(buildCurrentQueueList(), player.currentMediaItemIndex)
                if (isQueueVisible) {
                    renderQueue()
                    val pos = queueAdapter.getFirstUpcomingPosition()
                    if (pos != -1) binding.queueRecyclerView.smoothScrollToPosition(pos)
                }
            }
            is PlayNextAction.Insert -> {
                player.addMediaItem(action.targetIndex, action.item)
                Toast.makeText(this, "Sẽ phát tiếp theo: ${MetadataDisplayHelper.cleanTitle(track.title)}", Toast.LENGTH_SHORT).show()
                schedulePrewarmQueue(buildCurrentQueueList(), player.currentMediaItemIndex)
                if (isQueueVisible) {
                    renderQueue()
                    val pos = queueAdapter.getFirstUpcomingPosition()
                    if (pos != -1) binding.queueRecyclerView.smoothScrollToPosition(pos)
                }
            }
        }
    }

    private fun addToQueue(track: LibraryItem.Track) {
        val player = browser ?: return
        val currentQueue = buildCurrentQueueList()
        val preparedItem = uiCatalog.withCurrentQuality(track.mediaItem)
        val action = QueueHelper.planAddToQueue(currentQueue, preparedItem)
        Log.i(
            TAG,
            "QUEUE_ADD_END: mediaId=${track.id} queueSize=${currentQueue.size} action=${action.javaClass.simpleName}",
        )

        when (action) {
            is AddToQueueAction.StartNewQueue -> {
                player.setMediaItems(listOf(preparedItem), 0, 0L)
                player.prepare()
                Toast.makeText(this, "Đã thêm vào hàng đợi: ${MetadataDisplayHelper.cleanTitle(track.title)}", Toast.LENGTH_SHORT).show()
                if (isQueueVisible) renderQueue()
            }
            is AddToQueueAction.AlreadyExists -> {
                Toast.makeText(this, "Bài hát đã có trong hàng đợi", Toast.LENGTH_SHORT).show()
            }
            is AddToQueueAction.Append -> {
                player.addMediaItem(action.item)
                Toast.makeText(this, "Đã thêm vào hàng đợi: ${MetadataDisplayHelper.cleanTitle(track.title)}", Toast.LENGTH_SHORT).show()
                schedulePrewarmQueue(buildCurrentQueueList(), player.currentMediaItemIndex)
                if (isQueueVisible) {
                    renderQueue()
                    if (queueAdapter.itemCount in 1..60) {
                        binding.queueRecyclerView.smoothScrollToPosition(queueAdapter.itemCount - 1)
                    }
                }
            }
        }
    }

    private fun removeQueueItem(targetIndex: Int) {
        val player = browser ?: return
        val queueSize = player.mediaItemCount
        val currentIndex = player.currentMediaItemIndex
        val plan = QueueHelper.planRemoveQueueItem(queueSize, currentIndex, targetIndex)
        Log.i(
            TAG,
            "QUEUE_REMOVE: targetIndex=$targetIndex currentIndex=$currentIndex queueSize=$queueSize result=${plan.javaClass.simpleName}",
        )
        when (plan) {
            is RemoveQueueResult.InvalidIndex -> return
            is RemoveQueueResult.RemovedBeforeCurrent,
            is RemoveQueueResult.RemovedAfterCurrent,
            -> {
                player.removeMediaItem(targetIndex)
                updateNowPlaying()
                schedulePrewarmQueue(buildCurrentQueueList(), player.currentMediaItemIndex)
                if (isQueueVisible) renderQueue()
            }
            is RemoveQueueResult.RemovedCurrentAdvanceNext -> {
                val wasPlaying = player.isPlaying
                player.removeMediaItem(targetIndex)
                if (wasPlaying && !player.isPlaying) {
                    player.play()
                }
                updateNowPlaying()
                schedulePrewarmQueue(buildCurrentQueueList(), player.currentMediaItemIndex)
                if (isQueueVisible) renderQueue()
            }
            is RemoveQueueResult.RemovedCurrentMovePrevious -> {
                val wasPlaying = player.isPlaying
                player.seekToDefaultPosition(plan.newCurrentIndex)
                player.removeMediaItem(targetIndex)
                if (wasPlaying) {
                    player.play()
                }
                updateNowPlaying()
                schedulePrewarmQueue(buildCurrentQueueList(), player.currentMediaItemIndex)
                if (isQueueVisible) renderQueue()
            }
            is RemoveQueueResult.RemovedLastStopEmpty -> {
                player.stop()
                player.clearMediaItems()
                updateNowPlaying()
                if (isQueueVisible) renderQueue()
                Toast.makeText(this, "Hàng đợi đang trống", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearUpcoming() {
        val player = browser ?: return
        val currentIndex = player.currentMediaItemIndex
        val range = QueueHelper.planClearUpcoming(player.mediaItemCount, currentIndex) ?: return
        Log.i(TAG, "QUEUE_CLEAR_UPCOMING: currentIndex=$currentIndex range=${range.first}..${range.last}")
        player.removeMediaItems(range.first, range.last + 1)
        Toast.makeText(this, "Đã xóa danh sách tiếp theo", Toast.LENGTH_SHORT).show()
        schedulePrewarmQueue(buildCurrentQueueList(), player.currentMediaItemIndex)
        if (isQueueVisible) renderQueue()
    }

    private fun buildCurrentQueueList(): List<MediaItem> {
        val player = browser ?: return emptyList()
        return buildList {
            for (i in 0 until player.mediaItemCount) {
                add(player.getMediaItemAt(i))
            }
        }
    }

    /**
     * A browse tap carries the NEXT source page into MusicService. The service,
     * not this Activity, appends further pages even when the UI is backgrounded.
     * Manual Play From Here / Queue edits intentionally do not carry this context.
     */
    private fun playBrowseQueue(items: List<MediaItem>, selectedIndex: Int) {
        val parent = navManager.currentParentId
        val query = if (parent == "search") navManager.searchQuery else ""
        if (!QueueBrowseSeed.supports(parent, query) || items.isEmpty()) {
            playQueue(items, selectedIndex)
            return
        }
        val first = items.first()
        val extras = Bundle(first.mediaMetadata.extras ?: Bundle()).apply {
            putString(QueueBrowseSeed.KEY_PARENT, parent)
            putString(QueueBrowseSeed.KEY_SORT, navManager.currentSort)
            putString(QueueBrowseSeed.KEY_QUERY, query)
            putInt(QueueBrowseSeed.KEY_NEXT_PAGE, currentLoadedPages.coerceAtLeast(1))
            putInt(QueueBrowseSeed.KEY_PAGE_SIZE, pageSize)
        }
        val seededFirst = first.buildUpon()
            .setMediaMetadata(first.mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
        playQueue(listOf(seededFirst) + items.drop(1), selectedIndex)
    }

    private fun playQueue(items: List<MediaItem>, selectedIndex: Int) {
        val mediaBrowser = browser ?: return
        if (items.isEmpty()) return

        val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)
        val startedAt = SystemClock.elapsedRealtime()
        val sameQueue = mediaBrowser.mediaItemCount == items.size &&
            items.indices.all { index ->
                mediaBrowser.getMediaItemAt(index).mediaId ==
                    items[index].mediaId
            }

        if (sameQueue) {
            if (mediaBrowser.currentMediaItemIndex != safeIndex) {
                mediaBrowser.seekToDefaultPosition(safeIndex)
            }
            mediaBrowser.play()
            Log.i(
                TAG,
                "playQueue: reused existing queue size=${items.size}, " +
                    "targetIndex=$safeIndex, elapsed=" +
                    "${SystemClock.elapsedRealtime() - startedAt}ms",
            )
        } else {
            mediaBrowser.setMediaItems(items, safeIndex, 0L)
            mediaBrowser.prepare()
            mediaBrowser.play()
            Log.i(
                TAG,
                "playQueue: rebuilt queue size=${items.size}, " +
                    "targetIndex=$safeIndex, elapsed=" +
                    "${SystemClock.elapsedRealtime() - startedAt}ms",
            )
        }

        schedulePrewarmQueue(items, safeIndex)

        // Keep the activity UI in sync even if the controller does not emit an
        // immediate callback for a queue change initiated by this controller.
        mainHandler.post { updateNowPlaying() }
    }

    private fun updateNowPlaying() {
        val player = browser ?: return
        val currentItem = player.currentMediaItem
        val widthClass = currentWidthClass()

        if (currentItem == null) {
            // Idle / Welcome state
            currentLoadedArtworkUri = null
            binding.favoriteButton.visibility = View.GONE
            currentFavoriteMediaId = null
            currentFavoriteState = null
            binding.titleView.text = "Chọn một bài để bắt đầu"
            binding.artistView.visibility = View.GONE
            binding.albumView.visibility = View.GONE
            binding.technicalInfoView.text = ""
            binding.technicalInfoView.visibility = View.GONE
            binding.coverView.setImageResource(R.drawable.ic_album_placeholder)
            binding.miniCoverView?.setImageResource(R.drawable.ic_album_placeholder)
            binding.miniTitleView?.text = ""
            binding.miniArtistView?.text = ""
            binding.miniPlayerBar?.visibility = View.GONE
            binding.fullPlayerContainer?.visibility = View.VISIBLE

            // Artwork size at idle: driven by width class, not orientation
            val idleSizeRes = when (widthClass) {
                WindowWidthClass.COMPACT  -> R.dimen.artwork_size_compact_idle
                WindowWidthClass.MEDIUM   -> R.dimen.artwork_size_medium_idle
                WindowWidthClass.EXPANDED -> R.dimen.artwork_size_expanded_idle
            }
            val idleSize = resources.getDimensionPixelSize(idleSizeRes)
            val lp = binding.coverView.layoutParams
            if (lp.width != idleSize || lp.height != idleSize) {
                lp.width = idleSize
                lp.height = idleSize
                binding.coverView.layoutParams = lp
            }

            binding.playPauseButton.setImageResource(R.drawable.ic_play_arrow)

            // In expanded mode always show controls (dual-panel has fixed height left panel)
            if (widthClass == WindowWidthClass.EXPANDED) {
                binding.seekBar.visibility = View.VISIBLE
                binding.seekBar.isEnabled = false
                binding.timeRow.visibility = View.VISIBLE
                binding.currentTimeView.text = "--:--"
                binding.totalTimeView.text = "--:--"
                binding.qualityChip.visibility = View.VISIBLE
                binding.signalPathButton?.visibility = View.VISIBLE
                binding.lyricsButton?.visibility = View.VISIBLE
                binding.queueButton.visibility = if (isCompactPlayerLayout()) View.GONE else View.VISIBLE
                binding.shuffleButton.visibility = View.VISIBLE
                binding.repeatButton.visibility = View.VISIBLE
            } else {
                binding.seekBar.visibility = View.GONE
                binding.timeRow.visibility = View.GONE
                binding.qualityChip.visibility = View.GONE
                binding.signalPathButton?.visibility = View.GONE
                binding.lyricsButton?.visibility = View.GONE
                binding.queueButton.visibility = if (isCompactPlayerLayout()) View.GONE else View.VISIBLE
                binding.shuffleButton.visibility = View.INVISIBLE
                binding.repeatButton.visibility = View.INVISIBLE
            }
            if (isQueueVisible) {
                binding.queueButton.setColorFilter(ContextCompat.getColor(this, R.color.accent_primary))
                binding.queueButton.setBackgroundResource(R.drawable.bg_button_tertiary_active)
            } else {
                binding.queueButton.setColorFilter(ContextCompat.getColor(this, R.color.text_primary))
                binding.queueButton.setBackgroundResource(R.drawable.bg_button_control)
            }

            libraryAdapter.updateNowPlaying(null, false)
            updateFullscreenLyricsPlayerChrome()
            return
        }

        // Active track state — use authoritative metadata directly.
        // MetadataDisplayHelper.cleanTitle() is NOT applied here because authoritative
        // metadata (from driveFileItem detail endpoint) has proper Title tags.
        // cleanTitle() is only used in the list display path (LibraryAdapter/mapToUiItems)
        // for raw filenames from fast browse.
        val metadata = currentItem.mediaMetadata
        val rawTitle = metadata.title?.toString().orEmpty().trim()
        binding.titleView.text = rawTitle.ifBlank { "Đang phát" }

        // cleanArtist: suppress junk fallback values ("Nghệ sĩ không rõ", "Unknown", etc.)
        val artist = MetadataDisplayHelper.cleanArtist(metadata.artist?.toString())
        if (artist.isNotBlank()) {
            binding.artistView.text = artist
            binding.artistView.visibility = View.VISIBLE
        } else {
            binding.artistView.text = ""
            binding.artistView.visibility = View.GONE
        }

        // cleanAlbum: suppress "Unknown Album" / "Unknown" / blank junk values
        val album = MetadataDisplayHelper.cleanAlbum(metadata.albumTitle?.toString())
        if (album.isNotBlank()) {
            binding.albumView.text = album
            binding.albumView.visibility = View.VISIBLE
        } else {
            binding.albumView.text = ""
            binding.albumView.visibility = View.GONE
        }

        val extras = metadata.extras

        val techInfo = if (currentTechnicalInfoMediaId == currentItem.mediaId) {
            currentTechnicalInfo
        } else {
            null
        }

        val format = techInfo?.format ?: extras?.getString("format")
        val sizeBytes = techInfo?.sizeBytes ?: (if (extras != null && extras.containsKey("size_bytes")) extras.getLong("size_bytes") else null)
        val codec = techInfo?.codec ?: extras?.getString("codec")
        val bitrate = techInfo?.bitrate ?: (if (extras != null && extras.containsKey("bitrate")) extras.getInt("bitrate") else null)
        val sampleRate = techInfo?.sampleRate ?: (if (extras != null && extras.containsKey("sample_rate")) extras.getInt("sample_rate") else null)
        val channels = techInfo?.channels ?: (if (extras != null && extras.containsKey("channels")) extras.getInt("channels") else null)

        val technicalInfo = AudioInfoFormatter.formatAudioInfo(
            format = format,
            sizeBytes = sizeBytes,
            codec = codec,
            bitrate = bitrate,
            sampleRate = sampleRate,
            channels = channels,
        )
        if (!technicalInfo.isNullOrBlank()) {
            binding.technicalInfoView.text = "Nguồn: $technicalInfo"
            binding.technicalInfoView.visibility = View.VISIBLE
        } else {
            binding.technicalInfoView.text = ""
            binding.technicalInfoView.visibility = View.GONE
        }

        // Artwork size when track is active: driven by width class and MobilePlayerLayoutPolicy
        val activeSizeRes = when (widthClass) {
            WindowWidthClass.COMPACT  -> R.dimen.artwork_size_compact
            WindowWidthClass.MEDIUM   -> R.dimen.artwork_size_medium
            WindowWidthClass.EXPANDED -> R.dimen.artwork_size_expanded
        }
        val cfg = resources.configuration
        val activeSize = if (MobilePlayerLayoutPolicy.isPortraitPhone(cfg.screenWidthDp, cfg.screenHeightDp)) {
            val sizeDp = MobilePlayerLayoutPolicy.artworkSizeDp(cfg.screenWidthDp, cfg.screenHeightDp)
            (sizeDp * resources.displayMetrics.density).roundToInt()
        } else {
            resources.getDimensionPixelSize(activeSizeRes)
        }
        val lp = binding.coverView.layoutParams
        if (lp.width != activeSize || lp.height != activeSize) {
            lp.width = activeSize
            lp.height = activeSize
            binding.coverView.layoutParams = lp
        }

        val artworkUriStr = metadata.artworkUri?.toString()
        if (currentLoadedArtworkUri != artworkUriStr) {
            currentLoadedArtworkUri = artworkUriStr
            ArtworkLoader.loadArtwork(
                imageView = binding.coverView,
                uri = artworkUriStr,
                cornerRadiusDp = 16f,
                placeholderResId = R.drawable.ic_album_placeholder,
            )
            binding.miniCoverView?.let { miniCover ->
                ArtworkLoader.loadArtwork(
                    imageView = miniCover,
                    uri = artworkUriStr,
                    cornerRadiusDp = 8f,
                    placeholderResId = R.drawable.ic_album_placeholder,
                )
            }
        }

        // Mini player text & controls
        binding.miniTitleView?.text = rawTitle.ifBlank { "Đang phát" }
        binding.miniArtistView?.text = if (artist.isNotBlank()) artist else (album.ifBlank { "" })
        binding.miniArtistView?.visibility = if (binding.miniArtistView?.text.isNullOrBlank()) View.GONE else View.VISIBLE
        if (player.isPlaying) {
            binding.miniPlayPauseButton?.setImageResource(R.drawable.ic_pause)
        } else {
            binding.miniPlayPauseButton?.setImageResource(R.drawable.ic_play_arrow)
        }

        // Make all controls visible in active state
        binding.seekBar.visibility = View.VISIBLE
        binding.seekBar.isEnabled = true
        binding.timeRow.visibility = View.VISIBLE
        binding.qualityChip.visibility = View.VISIBLE
        binding.signalPathButton?.visibility = View.VISIBLE
        binding.lyricsButton?.visibility = View.VISIBLE
        binding.queueButton.visibility = if (isCompactPlayerLayout()) View.GONE else View.VISIBLE
        binding.shuffleButton.visibility = View.VISIBLE
        binding.repeatButton.visibility = View.VISIBLE

        if (isLyricsVisible) {
            binding.lyricsButton?.setTextColor(ContextCompat.getColor(this, R.color.accent_primary))
            binding.lyricsButton?.setBackgroundResource(R.drawable.bg_quality_chip_active)
        } else {
            binding.lyricsButton?.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            binding.lyricsButton?.setBackgroundResource(R.drawable.bg_quality_chip_inactive)
        }

        if (isQueueVisible) {
            binding.queueButton.setColorFilter(ContextCompat.getColor(this, R.color.accent_primary))
            binding.queueButton.setBackgroundResource(R.drawable.bg_button_tertiary_active)
        } else {
            binding.queueButton.setColorFilter(ContextCompat.getColor(this, R.color.text_primary))
            binding.queueButton.setBackgroundResource(R.drawable.bg_button_control)
        }

        binding.favoriteButton.visibility = View.VISIBLE
        val mediaId = currentItem.mediaId
        if (currentFavoriteMediaId != mediaId) {
            currentFavoriteMediaId = mediaId
            currentFavoriteState = null
            updateFavoriteUi(false)
            uiExecutor.submit {
                val fav = runCatching { uiCatalog.getFavoriteStatus(mediaId) }.getOrDefault(false)
                runOnUiThread {
                    if (currentFavoriteMediaId == mediaId) {
                        currentFavoriteState = fav
                        updateFavoriteUi(fav)
                    }
                }
            }
        } else {
            updateFavoriteUi(currentFavoriteState ?: false)
        }
        binding.seekBar.isEnabled = true
        binding.timeRow.visibility = View.VISIBLE
        binding.qualityChip.visibility = View.VISIBLE
        binding.shuffleButton.visibility = View.VISIBLE
        binding.repeatButton.visibility = View.VISIBLE

        // Hero Play/Pause Icon
        if (player.isPlaying) {
            binding.playPauseButton.setImageResource(R.drawable.ic_pause)
        } else {
            binding.playPauseButton.setImageResource(R.drawable.ic_play_arrow)
        }

        // Shuffle state
        if (player.shuffleModeEnabled) {
            binding.shuffleButton.setColorFilter(ContextCompat.getColor(this, R.color.accent_primary))
            binding.shuffleButton.setBackgroundResource(R.drawable.bg_button_tertiary_active)
        } else {
            binding.shuffleButton.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
            binding.shuffleButton.setBackgroundResource(R.drawable.bg_button_tertiary)
        }

        // Repeat state
        when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> {
                binding.repeatButton.setImageResource(R.drawable.ic_repeat)
                binding.repeatButton.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
                binding.repeatButton.setBackgroundResource(R.drawable.bg_button_tertiary)
            }
            Player.REPEAT_MODE_ALL -> {
                binding.repeatButton.setImageResource(R.drawable.ic_repeat)
                binding.repeatButton.setColorFilter(ContextCompat.getColor(this, R.color.accent_primary))
                binding.repeatButton.setBackgroundResource(R.drawable.bg_button_tertiary_active)
            }
            Player.REPEAT_MODE_ONE -> {
                binding.repeatButton.setImageResource(R.drawable.ic_repeat_one)
                binding.repeatButton.setColorFilter(ContextCompat.getColor(this, R.color.accent_primary))
                binding.repeatButton.setBackgroundResource(R.drawable.bg_button_tertiary_active)
            }
        }

        progressController.syncWithPlayer()
        libraryAdapter.updateNowPlaying(currentItem.mediaId, player.isPlaying)
        updateFullscreenLyricsPlayerChrome()
        applyMobilePlayerVisibility()
    }

    private fun applyMobilePlayerVisibility(isBrowsing: Boolean? = null) {
        val cfg = resources.configuration
        val isPortraitPhone = MobilePlayerLayoutPolicy.isPortraitPhone(cfg.screenWidthDp, cfg.screenHeightDp)
        if (!isPortraitPhone) {
            binding.fullPlayerContainer?.visibility = View.VISIBLE
            binding.miniPlayerBar?.visibility = View.GONE
            binding.libraryCard.visibility = if (isQueueVisible || isLyricsVisible) View.GONE else View.VISIBLE
            binding.searchContainer.visibility = if (isQueueVisible || isLyricsVisible) View.GONE else View.VISIBLE
            return
        }

        val hasCurrentItem = browser?.currentMediaItem != null
        if (!hasCurrentItem) {
            binding.fullPlayerContainer?.visibility = View.VISIBLE
            binding.miniPlayerBar?.visibility = View.GONE
            binding.libraryCard.visibility = if (isQueueVisible || isLyricsVisible) View.GONE else View.VISIBLE
            binding.searchContainer.visibility = if (isQueueVisible || isLyricsVisible) View.GONE else View.VISIBLE
            return
        }

        val browsing = isBrowsing ?: (!isQueueVisible && !isFullscreenLyricsVisible)
        val showMini = MobilePlayerLayoutPolicy.shouldShowMiniPlayer(
            isPortraitPhone = true,
            isBrowsing = browsing,
            userOpenedFullPlayer = userOpenedFullPlayer,
        )

        binding.fullPlayerContainer?.visibility = if (showMini) View.GONE else View.VISIBLE
        binding.miniPlayerBar?.visibility = if (showMini) View.VISIBLE else View.GONE

        if (showMini) {
            binding.libraryCard.visibility = if (isQueueVisible || isLyricsVisible) View.GONE else View.VISIBLE
            binding.searchContainer.visibility = if (isQueueVisible || isLyricsVisible) View.GONE else View.VISIBLE
        } else {
            binding.libraryCard.visibility = View.GONE
            binding.searchContainer.visibility = View.GONE
        }
    }

    private fun updateQualityChip() {
        when (PlaybackQuality.current) {
            StreamQuality.ORIGINAL -> {
                binding.qualityChip.text = "Original"
                binding.qualityChip.setBackgroundResource(R.drawable.bg_quality_chip_original)
                binding.qualityChip.setTextColor(ContextCompat.getColor(this, R.color.quality_original_text))
            }
            StreamQuality.MOBILE_320 -> {
                binding.qualityChip.text = "Mobile 320"
                binding.qualityChip.setBackgroundResource(R.drawable.bg_quality_chip_mobile)
                binding.qualityChip.setTextColor(ContextCompat.getColor(this, R.color.quality_mobile_text))
            }
        }
    }

    private fun toggleQuality() {
        val newQuality = PlaybackQuality.toggle()
        getSharedPreferences("music_assistant_car", MODE_PRIVATE).edit()
            .putBoolean("mobile_320", newQuality == StreamQuality.MOBILE_320)
            .apply()
        updateQualityChip()
        currentSignalPathInfo = null
        currentSignalPathMediaId = null
        currentSignalPathQuality = null
        currentSignalPathFailed = false
        requestSignalPathForCurrentItem()
        renderSignalPathInDialog()
        rebuildQueueForQuality()
        if (!navManager.isAtRoot && navManager.currentParentId != "search") {
            loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
        }
    }

    private fun rebuildQueueForQuality() {
        val player = browser ?: return
        if (player.mediaItemCount <= 0) return

        PlaybackQuality.isQualitySwitching = true
        try {
            val items = buildList {
                for (index in 0 until player.mediaItemCount) {
                    add(uiCatalog.withCurrentQuality(player.getMediaItemAt(index)))
                }
            }
            val index = player.currentMediaItemIndex.coerceAtLeast(0)
            val position = player.currentPosition
            val shouldPlay = player.playWhenReady

            player.setMediaItems(items, index.coerceAtMost(items.lastIndex), position)
            player.prepare()
            if (shouldPlay) {
                player.play()
            }
            schedulePrewarmQueue(items, index.coerceAtMost(items.lastIndex))
        } finally {
            mainHandler.postDelayed({ PlaybackQuality.isQualitySwitching = false }, 500L)
        }
    }

    private fun schedulePrewarmQueue(items: List<MediaItem>, selectedIndex: Int) {
        if (items.isEmpty() || selectedIndex !in items.indices) return
        val generation = ++prewarmGeneration
        val snapshot = items.toList()
        mainHandler.postDelayed(
            {
                if (generation == prewarmGeneration) {
                    prewarmQueue(snapshot, selectedIndex)
                }
            },
            1_200L,
        )
    }

    private fun prewarmQueue(items: List<MediaItem>, selectedIndex: Int) {
        val quality = PlaybackQuality.current
        val count = if (quality == StreamQuality.MOBILE_320) 2 else 1
        val candidates = items.drop(selectedIndex + 1).take(count)
        candidates.forEach { item ->
            val key = "${quality.queryValue}:${item.mediaId}"
            if (!prewarmedMediaKeys.add(key)) return@forEach

            prewarmExecutor.submit {
                val ok = runCatching {
                    uiCatalog.prewarm(item.mediaId, quality)
                }.getOrDefault(false)
                if (!ok) {
                    prewarmedMediaKeys.remove(key)
                }
                Log.i(
                    TAG,
                    "prewarm: mediaId=${item.mediaId}, quality=${quality.queryValue}, ok=$ok",
                )
            }
        }
    }

    private fun refreshBackendStatus() {
        binding.statusDot.setImageResource(R.drawable.bg_status_dot_connecting)
        binding.statusText.text = "Connecting"
        uiExecutor.submit {
            val result = runCatching { uiCatalog.backendLabel() }
            runOnUiThread {
                result
                    .onSuccess { url ->
                        if (activeBackendUrl != null && activeBackendUrl != url) {
                            enrichmentController.clearCaches()
                        }
                        activeBackendUrl = url
                        binding.statusDot.setImageResource(R.drawable.bg_status_dot_online)
                        binding.statusText.text = "Online"
                    }
                    .onFailure {
                        activeBackendUrl = null
                        binding.statusDot.setImageResource(R.drawable.bg_status_dot_offline)
                        binding.statusText.text = "Offline"
                    }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private var pendingAudioPermissionAction: (() -> Unit)? = null
    private val REQUEST_CODE_AUDIO_PERMISSION = 2002

    private fun checkAndRequestLocalAudioPermission(onGranted: () -> Unit) {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            pendingAudioPermissionAction = onGranted
            requestPermissions(arrayOf(permission), REQUEST_CODE_AUDIO_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingAudioPermissionAction?.invoke()
            } else {
                Toast.makeText(this, "Cần cấp quyền truy cập để đọc nhạc trên thiết bị", Toast.LENGTH_LONG).show()
            }
            pendingAudioPermissionAction = null
        }
    }

    private fun handleDeviceLocalFoldersClick(category: LibraryItem.Category) {
        val repo = com.bsdung.musicassistantcar.data.LocalMediaRepository.getDefault(this)
        val last = repo.getLastBrowsedFolder()
        if (last != null && repo.folderExists(last.first, last.second)) {
            resumeToDeviceFolder(last.first, last.second)
        } else {
            repo.clearLastBrowsedFolder()
            loadChildren(category.id, 0, category.title)
        }
    }

    private fun resumeToDeviceFolder(volume: String, path: String) {
        val repo = com.bsdung.musicassistantcar.data.LocalMediaRepository.getDefault(this)
        val normalizedPath = com.bsdung.musicassistantcar.data.LocalMediaRepository.normalizeRelativePath(path)
        val volLabel = repo.getVolumeDisplayName(volume)

        navManager.navigateTo(RemoteCatalog.DEVICE_LOCAL_FOLDERS_ID, "Theo thư mục", 0)
        navManager.navigateTo("${RemoteCatalog.DEVICE_FOLDER_PREFIX}$volume:", volLabel, 0)

        if (normalizedPath.isNotBlank()) {
            val segments = normalizedPath.split('/')
            var currentSubPath = ""
            for (i in 0 until segments.lastIndex) {
                val segment = segments[i]
                currentSubPath = if (currentSubPath.isEmpty()) segment else "$currentSubPath/$segment"
                navManager.navigateTo("${RemoteCatalog.DEVICE_FOLDER_PREFIX}$volume:$currentSubPath", segment, 0)
            }
            val targetSegment = segments.last()
            loadChildren("${RemoteCatalog.DEVICE_FOLDER_PREFIX}$volume:$normalizedPath", 0, targetSegment)
        } else {
            loadChildren("${RemoteCatalog.DEVICE_FOLDER_PREFIX}$volume:", 0, volLabel)
        }
    }

    private val downloadListener = object : com.bsdung.musicassistantcar.playback.PinnedDownloadManager.DownloadListener {
        override fun onDownloadStarted(trackId: Long) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
                updateDownloadQueueHeader()
                updatePlaylistSyncBarPassive()
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }
            }
        }
        override fun onDownloadProgress(trackId: Long, bytesDownloaded: Long, totalBytes: Long) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    updateDownloadQueueHeader()
                }
            }
        }
        override fun onDownloadFinalizing(trackId: Long) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    updateDownloadQueueHeader()
                }
            }
        }
        override fun onDownloadWaitingForWifi(trackId: Long) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
                updateDownloadQueueHeader()
                updatePlaylistSyncBarPassive()
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }
            }
        }
        override fun onDownloadCompleted(trackId: Long, record: com.bsdung.musicassistantcar.playback.OfflineTrackRecord) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
                updateCollectionActionBar()
                updateDownloadQueueHeader()
                updateOfflineStorageDashboard()
                Toast.makeText(this@MainActivity, "Đã tải xong: ${record.title}", Toast.LENGTH_SHORT).show()
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADED_ID || navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }
            }
        }
        override fun onDownloadFailed(trackId: Long, error: String) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
                updateDownloadQueueHeader()
                updateOfflineStorageDashboard()
                updatePlaylistSyncBarPassive()
                if (error != "Deleted") {
                    Toast.makeText(this@MainActivity, "Tải thất bại: $error", Toast.LENGTH_SHORT).show()
                }
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }
            }
        }
        override fun onDownloadCancelled(trackId: Long) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
                updateDownloadQueueHeader()
                updateOfflineStorageDashboard()
                updatePlaylistSyncBarPassive()
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }
            }
        }
        override fun onDownloadDeleted(trackId: Long) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
                updateCollectionActionBar()
                updateDownloadQueueHeader()
                updateOfflineStorageDashboard()
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADED_ID || navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }
            }
        }
    }

    private fun handleTrackDownloadClick(track: LibraryItem.Track) {
        val canonicalId = RemoteCatalog.canonicalTrackMediaId(track.mediaItem) ?: track.id
        val trackId = canonicalId.removePrefix("download_job:").removePrefix(RemoteCatalog.TRACK_PREFIX).toLongOrNull() ?: return
        val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)

        if (dm.isOfflineReady(trackId)) {
            AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                .setTitle("Xóa bản tải offline")
                .setMessage("Chỉ xóa bản lưu trên thiết bị. Bài hát vẫn còn trong kho MeloVault và có thể phát trực tuyến.")
                .setPositiveButton("Xóa") { _, _ ->
                    stopPlaybackIfDeletedOfflineTrack(setOf(trackId))
                    dm.deleteDownload(trackId)
                    libraryAdapter.notifyDataSetChanged()
                    updateOfflineStorageDashboard()
                    Toast.makeText(this, "Đã xóa bản tải offline", Toast.LENGTH_SHORT).show()
                    if (navManager.currentParentId == RemoteCatalog.DOWNLOADED_ID || navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                        loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
            return
        }

        val carTrack = com.bsdung.musicassistantcar.data.CarTrack(
            id = trackId.toInt(),
            title = track.title,
            artist = track.artist,
            album = track.album.orEmpty(),
            status = "ready",
            duration = (track.durationMs ?: 0L) / 1000.0,
            sourceFormat = track.format,
            sourceSizeBytes = track.sizeBytes,
            sourceCodec = track.codec,
            sourceBitrate = track.bitrate,
            sourceSampleRate = track.sampleRate,
            sourceChannels = track.channels,
            loudnessLufs = track.mediaItem.mediaMetadata.extras?.takeIf { it.containsKey("loudness_lufs") }?.getDouble("loudness_lufs"),
            truePeakDbtp = track.mediaItem.mediaMetadata.extras?.takeIf { it.containsKey("true_peak_dbtp") }?.getDouble("true_peak_dbtp"),
            loudnessRangeLu = track.mediaItem.mediaMetadata.extras?.takeIf { it.containsKey("loudness_range_lu") }?.getDouble("loudness_range_lu"),
            loudnessAnalysisVersion = track.mediaItem.mediaMetadata.extras?.takeIf { it.containsKey("loudness_analysis_version") }?.getInt("loudness_analysis_version"),
        )

        val status = dm.getDownloadStatus(trackId)
        when (status.state) {
            com.bsdung.musicassistantcar.playback.DownloadState.OFFLINE_MEDIA_UNAVAILABLE -> {
                AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                    .setTitle("Thẻ nhớ SD không khả dụng")
                    .setMessage("Bài hát này đã được tải về Thẻ nhớ SD nhưng thẻ nhớ hiện không khả dụng.\nMeloVault sẽ giữ nguyên bản tải đã lưu và tạm phát trực tuyến cho đến khi thẻ nhớ được lắp lại.")
                    .setPositiveButton("Đã hiểu", null)
                    .show()
                return
            }
            com.bsdung.musicassistantcar.playback.DownloadState.DOWNLOADING,
            com.bsdung.musicassistantcar.playback.DownloadState.QUEUED,
            com.bsdung.musicassistantcar.playback.DownloadState.WAITING_FOR_WIFI -> {
                AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                    .setTitle("Hủy tải bài hát?")
                    .setMessage("Dừng và hủy tiến trình tải bài hát \"${MetadataDisplayHelper.cleanTitle(track.title)}\".")
                    .setPositiveButton("Hủy tải") { _, _ ->
                        dm.cancelDownload(trackId)
                        libraryAdapter.notifyDataSetChanged()
                        Toast.makeText(this, "Đã hủy tải bài hát", Toast.LENGTH_SHORT).show()
                        if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                            loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                        }
                    }
                    .setNegativeButton("Đóng", null)
                    .show()
                return
            }
            com.bsdung.musicassistantcar.playback.DownloadState.STORAGE_TARGET_UNAVAILABLE,
            com.bsdung.musicassistantcar.playback.DownloadState.FAILED,
            com.bsdung.musicassistantcar.playback.DownloadState.CANCELLED -> {
                val precheck = dm.checkSingleDownloadStorage(carTrack)
                if (!precheck.isAllowed) {
                    if (precheck.status == com.bsdung.musicassistantcar.playback.StoragePrecheckStatus.STORAGE_TARGET_UNAVAILABLE) {
                        dm.retryDownload(trackId)
                        libraryAdapter.notifyDataSetChanged()
                        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                            .setTitle("Thẻ nhớ SD không khả dụng")
                            .setMessage(precheck.message ?: "Thẻ nhớ SD không khả dụng.")
                            .setPositiveButton("Đổi sang Bộ nhớ trong") { _, _ ->
                                showConfirmChangeStorageTargetDialog(com.bsdung.musicassistantcar.playback.OfflineStorageTarget.INTERNAL)
                            }
                            .setNegativeButton("Đóng", null)
                            .show()
                    } else {
                        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                            .setTitle(precheck.title ?: "Không đủ dung lượng")
                            .setMessage(precheck.message ?: "Không đủ dung lượng để tải.")
                            .setPositiveButton("Đã hiểu", null)
                            .show()
                    }
                    return
                }
                val networkAllowed = dm.isNetworkAllowedForDownload()
                dm.retryDownload(trackId)
                libraryAdapter.notifyDataSetChanged()
                val toastMsg = if (!networkAllowed) "Đang chờ Wi-Fi để tải xuống" else "Đang thử lại..."
                Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }
                return
            }
            else -> {}
        }

        val precheck = dm.checkSingleDownloadStorage(carTrack)
        if (!precheck.isAllowed) {
            if (precheck.status == com.bsdung.musicassistantcar.playback.StoragePrecheckStatus.STORAGE_TARGET_UNAVAILABLE) {
                dm.startDownload(carTrack)
                libraryAdapter.notifyDataSetChanged()
                AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                    .setTitle("Thẻ nhớ SD không khả dụng")
                    .setMessage(precheck.message ?: "Thẻ nhớ SD không khả dụng.")
                    .setPositiveButton("Đổi sang Bộ nhớ trong") { _, _ ->
                        showConfirmChangeStorageTargetDialog(com.bsdung.musicassistantcar.playback.OfflineStorageTarget.INTERNAL)
                    }
                    .setNegativeButton("Đóng", null)
                    .show()
            } else {
                AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
                    .setTitle(precheck.title ?: "Không đủ dung lượng")
                    .setMessage(precheck.message ?: "Không đủ dung lượng để tải.")
                    .setPositiveButton("Đã hiểu", null)
                    .show()
            }
            return
        }

        val networkAllowed = dm.isNetworkAllowedForDownload()
        val toastMsg = if (!networkAllowed) "Đang chờ Wi-Fi để tải xuống" else "Bắt đầu tải về..."
        Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
        dm.startDownload(carTrack)
        libraryAdapter.notifyDataSetChanged()
        if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
            loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
        }
    }


    override fun onStart() {
        super.onStart()
        logWindowMetrics("onStart")
    }

    override fun onResume() {
        super.onResume()
        isActivityInForeground = true
        updateFullscreenKeepScreenOn()
        logWindowMetrics("onResume")
        updateOfflineStorageDashboard()
        if (browser != null) {
            refreshBackendStatus()
            // Reconcile Now Playing after returning from background or launcher.
            updateNowPlaying()
            if ((isLyricsVisible || isFullscreenLyricsVisible) && currentLyricsUiState is LyricsUiState.Synced) {
                syncActiveLyricsLineWithPlayer(forceScroll = isLyricsAutoFollow)
            }
            if (isLyricsVisible || isFullscreenLyricsVisible) {
                startLyricsPositionLoop()
            }
        }
    }

    override fun onStop() {
        isActivityInForeground = false
        updateFullscreenKeepScreenOn()
        stopLyricsPositionLoop()
        super.onStop()
        logWindowMetrics("onStop")
    }

    /**
     * Called when FYT multi-window pane resizes the window without full recreation.
     * The w700dp qualifier causes Android to recreate the Activity when the threshold
     * is crossed (compact<->expanded), so this override primarily handles minor
     * configuration changes that DON'T cross a qualifier boundary.
     * Log it so field validation can distinguish FYT compositor frames from real app events.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        logWindowMetrics("onConfigurationChanged")
        // Re-apply artwork sizing to match new window dimensions.
        // Full recreation is handled by Android when qualifier boundary is crossed.
        if (browser != null) {
            updateNowPlaying()
        }
        if (isFullscreenLyricsVisible) {
            fullscreenSyncedLyricsAdapter.notifyDataSetChanged()
            updateFullscreenLyricsPlayerChrome()
            if (currentLyricsUiState is LyricsUiState.Synced && isLyricsAutoFollow) {
                binding.fullscreenLyricsSyncedRecyclerView?.post {
                    syncActiveLyricsLineWithPlayer(forceScroll = true)
                }
            }
        }
        updateHeaderAndBreadcrumbs()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("recycler_state", binding.recyclerView.layoutManager?.onSaveInstanceState())
        outState.putBoolean("is_queue_visible", isQueueVisible)
        outState.putBoolean("is_lyrics_visible", isLyricsVisible)
        outState.putBoolean("is_fullscreen_lyrics_visible", isFullscreenLyricsVisible)
        outState.putBoolean("user_opened_full_player", userOpenedFullPlayer)
        outState.putString("nav_parent_id", navManager.currentParentId)
        outState.putString("nav_label", navManager.currentLabel)
        outState.putInt("nav_page", navManager.currentPage)
        outState.putString("nav_sort", navManager.currentSort)
        outState.putString("nav_search", navManager.searchQuery)
        outState.putSerializable(
            "nav_back_stack",
            navManager.backStackSnapshot(),
        )
        outState.putString("fav_media_id", currentFavoriteMediaId)
        currentFavoriteState?.let { outState.putBoolean("fav_state", it) }
    }

    private fun updateFavoriteUi(isFav: Boolean) {
        if (isFav) {
            binding.favoriteButton.setImageResource(R.drawable.ic_heart_filled)
            binding.favoriteButton.setColorFilter(ContextCompat.getColor(this, R.color.accent_primary))
        } else {
            binding.favoriteButton.setImageResource(R.drawable.ic_heart_outline)
            binding.favoriteButton.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
        }
    }

    private fun refreshFavoriteForCurrentTrack() {
        val mediaId = browser?.currentMediaItem?.mediaId ?: return
        uiExecutor.submit {
            val result = runCatching { uiCatalog.getFavoriteStatus(mediaId) }
            runOnUiThread {
                result.onSuccess { state ->
                    if (browser?.currentMediaItem?.mediaId == mediaId) {
                        currentFavoriteMediaId = mediaId
                        currentFavoriteState = state
                        updateFavoriteUi(state)
                    }
                }
            }
        }
    }

    private fun showMusicAccountsDialog() {
        val dialog = BottomSheetDialog(this)
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        val bindingDialog = DialogMusicAccountsBinding.inflate(layoutInflater)
        dialog.setContentView(bindingDialog.root)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        val client = CarApiClient()

        fun loadAccounts() {
            bindingDialog.accountsLoadingView.visibility = View.VISIBLE
            bindingDialog.accountsErrorView.visibility = View.GONE
            bindingDialog.accountsScrollView.visibility = View.GONE

            kotlin.concurrent.thread {
                val result: Result<List<AccountStatusDto>> = runCatching {
                    client.getMusicAccounts()
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    bindingDialog.accountsLoadingView.visibility = View.GONE
                    result.fold(
                        onSuccess = { accounts: List<AccountStatusDto> ->
                            bindingDialog.accountsScrollView.visibility = View.VISIBLE
                            bindingDialog.accountsErrorView.visibility = View.GONE

                            val apple = accounts.firstOrNull { it.provider == AccountProvider.APPLE_MUSIC.id }
                            val yt = accounts.firstOrNull { it.provider == AccountProvider.YOUTUBE.id }

                            // Render Apple Music
                            if (apple != null) {
                                bindingDialog.appleStatusBadge.text = apple.state.vietnameseLabel
                                val badgeColor = when (apple.state) {
                                    AccountConnectionState.CONNECTED -> getColor(R.color.status_online)
                                    AccountConnectionState.CONNECTING -> getColor(R.color.status_connecting)
                                    AccountConnectionState.NOT_CONFIGURED,
                                    AccountConnectionState.NOT_CONNECTED,
                                    AccountConnectionState.VERIFICATION_UNAVAILABLE -> getColor(R.color.text_secondary)
                                    else -> getColor(R.color.status_offline)
                                }
                                bindingDialog.appleStatusBadge.setTextColor(badgeColor)

                                val caps = mutableListOf<String>()
                                if (apple.capabilities.catalogSearchAvailable) caps.add("Catalog Search")
                                if (apple.capabilities.personalLibraryAvailable) caps.add("Thư viện cá nhân")
                                if (apple.capabilities.playlistReadAvailable) caps.add("Danh sách phát")
                                caps.add("Phát nhạc: Chưa hỗ trợ")
                                bindingDialog.appleCapabilitiesText.text = "Tính năng: " + caps.joinToString(" · ")

                                if (apple.state == AccountConnectionState.NOT_CONFIGURED) {
                                    bindingDialog.appleNoticeText.text = "Cần cấu hình Apple MusicKit Developer Token trên máy chủ."
                                    bindingDialog.appleNoticeText.visibility = View.VISIBLE
                                } else if (!apple.errorMessage.isNullOrBlank()) {
                                    bindingDialog.appleNoticeText.text = apple.errorMessage
                                    bindingDialog.appleNoticeText.visibility = View.VISIBLE
                                } else {
                                    bindingDialog.appleNoticeText.visibility = View.GONE
                                }

                                bindingDialog.appleDisconnectButton.visibility =
                                    if (apple.state == AccountConnectionState.CONNECTED) View.VISIBLE else View.GONE
                                bindingDialog.appleDisconnectButton.setOnClickListener {
                                    bindingDialog.appleDisconnectButton.isEnabled = false
                                    kotlin.concurrent.thread {
                                        runCatching { client.disconnectAccount(AccountProvider.APPLE_MUSIC.id) }
                                        runOnUiThread { loadAccounts() }
                                    }
                                }
                            }

                            // Render YouTube
                            if (yt != null) {
                                bindingDialog.youtubeStatusBadge.text = yt.state.vietnameseLabel
                                val badgeColor = when (yt.state) {
                                    AccountConnectionState.CONNECTED -> getColor(R.color.status_online)
                                    AccountConnectionState.CONNECTING -> getColor(R.color.status_connecting)
                                    AccountConnectionState.NOT_CONFIGURED,
                                    AccountConnectionState.NOT_CONNECTED,
                                    AccountConnectionState.VERIFICATION_UNAVAILABLE -> getColor(R.color.text_secondary)
                                    else -> getColor(R.color.status_offline)
                                }
                                bindingDialog.youtubeStatusBadge.setTextColor(badgeColor)

                                val caps = mutableListOf<String>()
                                if (yt.capabilities.catalogSearchAvailable) caps.add("Catalog Search")
                                if (yt.capabilities.playlistReadAvailable) caps.add("Danh sách phát")
                                caps.add("Phát nhạc: Chưa hỗ trợ")
                                bindingDialog.youtubeCapabilitiesText.text = "Tính năng: " + caps.joinToString(" · ")

                                if (yt.state == AccountConnectionState.NOT_CONFIGURED) {
                                    bindingDialog.youtubeNoticeText.text = "Cần cấu hình Google OAuth Client ID trên máy chủ."
                                    bindingDialog.youtubeNoticeText.visibility = View.VISIBLE
                                } else if (!yt.errorMessage.isNullOrBlank()) {
                                    bindingDialog.youtubeNoticeText.text = yt.errorMessage
                                    bindingDialog.youtubeNoticeText.visibility = View.VISIBLE
                                } else {
                                    bindingDialog.youtubeNoticeText.visibility = View.GONE
                                }

                                    // YouTube Playlists button (visible only when CONNECTED and playlistReadAvailable)
                                    if (yt.state == AccountConnectionState.CONNECTED && yt.capabilities.playlistReadAvailable) {
                                        bindingDialog.youtubePlaylistsButton.visibility = View.VISIBLE
                                        bindingDialog.youtubePlaylistsButton.setOnClickListener {
                                            showYouTubePlaylistsDialog()
                                        }
                                    } else {
                                        bindingDialog.youtubePlaylistsButton.visibility = View.GONE
                                    }

                                    bindingDialog.youtubeDisconnectButton.visibility =
                                        if (yt.state == AccountConnectionState.CONNECTED) View.VISIBLE else View.GONE
                                    bindingDialog.youtubeDisconnectButton.setOnClickListener {
                                        bindingDialog.youtubeDisconnectButton.isEnabled = false
                                        kotlin.concurrent.thread {
                                            runCatching { client.disconnectAccount(AccountProvider.YOUTUBE.id) }
                                            runOnUiThread { loadAccounts() }
                                        }
                                    }
                                }
                        },
                        onFailure = { err ->
                            bindingDialog.accountsScrollView.visibility = View.GONE
                            bindingDialog.accountsErrorView.visibility = View.VISIBLE
                            bindingDialog.accountsErrorText.text = "Lỗi kết nối máy chủ: ${err.message ?: "Không xác định"}"
                        }
                    )
                }
            }
        }

        bindingDialog.accountsRefreshButton.setOnClickListener {
            loadAccounts()
        }
        bindingDialog.accountsRetryButton.setOnClickListener {
            loadAccounts()
        }
        bindingDialog.btnUnifiedLibrary.setOnClickListener {
            UnifiedLibraryDialog(
                activity = this,
                onPlayTrackInLibrary = { track ->
                    val started = playFromUnifiedLibrary(track)
                    if (started) dialog.dismiss()
                    started
                },
                onAddToQueue = ::addFromUnifiedLibrary,
            ).show()
        }
        bindingDialog.accountsCloseButton.setOnClickListener {
            dialog.dismiss()
        }

        loadAccounts()
        dialog.show()
    }

    /**
     * Unified Library may display external-only catalog entries. Only a canonical,
     * explicitly STREAM-capable MeloVault track may reach the authenticated Media3
     * pipeline. No provider URL or untrusted server stream path is opened directly.
     */
    private fun unifiedPlayableItem(track: UnifiedTrackDto): MediaItem? {
        val canonical = UnifiedPlaybackMapper.playableTrack(track) ?: return null
        return uiCatalog.withCurrentQuality(uiCatalog.trackItem(canonical))
    }

    private fun playFromUnifiedLibrary(track: UnifiedTrackDto): Boolean {
        if (browser == null) {
            Toast.makeText(this, "Chưa kết nối trình phát", Toast.LENGTH_SHORT).show()
            return false
        }
        val item = unifiedPlayableItem(track)
        if (item == null) {
            Toast.makeText(this, "Nguồn nhạc này không hỗ trợ phát trực tiếp", Toast.LENGTH_SHORT).show()
            return false
        }
        playQueue(listOf(item), 0)
        return true
    }

    private fun addFromUnifiedLibrary(track: UnifiedTrackDto): Boolean {
        if (browser == null) {
            Toast.makeText(this, "Chưa kết nối trình phát", Toast.LENGTH_SHORT).show()
            return false
        }
        val item = unifiedPlayableItem(track)
        if (item == null) {
            Toast.makeText(this, "Nguồn nhạc này không thể thêm vào hàng đợi", Toast.LENGTH_SHORT).show()
            return false
        }
        // Reuse the existing QueueHelper dedup/append logic and offline fallback.
        addToQueue(
            LibraryItem.Track(
                id = item.mediaId,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUri = item.mediaMetadata.artworkUri?.toString(),
                durationMs = track.duration?.takeIf { it.isFinite() && it > 0.0 }
                    ?.let { (it * 1000).toLong() },
                isCurrent = false,
                isPlaying = false,
                mediaItem = item,
                sourceBadge = track.sourceBadge,
                playbackAction = track.playbackAction,
                isPlayable = true,
            )
        )
        return true
    }

    private fun showYouTubePlaylistsDialog() {
        val dialog = BottomSheetDialog(this)
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        val bindingDialog = DialogYoutubePlaylistsBinding.inflate(layoutInflater)
        dialog.setContentView(bindingDialog.root)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        val client = CarApiClient()

        bindingDialog.btnYtPlaylistsClose.setOnClickListener {
            dialog.dismiss()
        }

        bindingDialog.btnYtItemsBack.setOnClickListener {
            bindingDialog.ytPlaylistItemsContainer.visibility = View.GONE
            bindingDialog.ytPlaylistsListContainer.visibility = View.VISIBLE
        }

        fun loadPlaylistItems(playlist: YouTubePlaylistDto) {
            bindingDialog.ytPlaylistsListContainer.visibility = View.GONE
            bindingDialog.ytPlaylistItemsContainer.visibility = View.VISIBLE
            bindingDialog.txtYtItemsPlaylistTitle.text = playlist.title
            bindingDialog.ytItemsLoadingView.visibility = View.VISIBLE
            bindingDialog.ytItemsStatusText.visibility = View.GONE
            bindingDialog.ytPlaylistItemsRecyclerView.visibility = View.GONE

            kotlin.concurrent.thread {
                val result = runCatching { client.getYouTubePlaylistItems(playlist.id) }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    bindingDialog.ytItemsLoadingView.visibility = View.GONE
                    result.fold(
                        onSuccess = { items ->
                            if (items.isEmpty()) {
                                bindingDialog.ytItemsStatusText.text = "Danh sách phát không có bài hát"
                                bindingDialog.ytItemsStatusText.visibility = View.VISIBLE
                                bindingDialog.ytPlaylistItemsRecyclerView.visibility = View.GONE
                            } else {
                                bindingDialog.ytItemsStatusText.visibility = View.GONE
                                bindingDialog.ytPlaylistItemsRecyclerView.visibility = View.VISIBLE
                                bindingDialog.ytPlaylistItemsRecyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                                bindingDialog.ytPlaylistItemsRecyclerView.adapter = YouTubePlaylistItemsAdapter(items) { item ->
                                    val videoId = item.videoId ?: return@YouTubePlaylistItemsAdapter
                                    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
                                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                                    try {
                                        startActivity(appIntent)
                                    } catch (_: Exception) {
                                        try {
                                            startActivity(webIntent)
                                        } catch (_: Exception) {
                                            Toast.makeText(this@MainActivity, "Không thể mở liên kết YouTube", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        },
                        onFailure = { err ->
                            bindingDialog.ytItemsStatusText.text = "Không thể tải bài hát: ${err.message ?: "Lỗi kết nối"}"
                            bindingDialog.ytItemsStatusText.visibility = View.VISIBLE
                            bindingDialog.ytPlaylistItemsRecyclerView.visibility = View.GONE
                        }
                    )
                }
            }
        }

        fun loadPlaylists() {
            bindingDialog.ytPlaylistsLoadingView.visibility = View.VISIBLE
            bindingDialog.ytPlaylistsStatusText.visibility = View.GONE
            bindingDialog.ytPlaylistsRecyclerView.visibility = View.GONE

            kotlin.concurrent.thread {
                val result = runCatching { client.getYouTubePlaylists() }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    bindingDialog.ytPlaylistsLoadingView.visibility = View.GONE
                    result.fold(
                        onSuccess = { playlists ->
                            if (playlists.isEmpty()) {
                                bindingDialog.ytPlaylistsStatusText.text = "Không tìm thấy danh sách phát nào trong tài khoản"
                                bindingDialog.ytPlaylistsStatusText.visibility = View.VISIBLE
                                bindingDialog.ytPlaylistsRecyclerView.visibility = View.GONE
                            } else {
                                bindingDialog.ytPlaylistsStatusText.visibility = View.GONE
                                bindingDialog.ytPlaylistsRecyclerView.visibility = View.VISIBLE
                                bindingDialog.ytPlaylistsRecyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                                bindingDialog.ytPlaylistsRecyclerView.adapter = YouTubePlaylistsAdapter(playlists) { selected ->
                                    loadPlaylistItems(selected)
                                }
                            }
                        },
                        onFailure = { err ->
                            bindingDialog.ytPlaylistsStatusText.text = "Không thể tải danh sách phát: ${err.message ?: "Lỗi kết nối"}"
                            bindingDialog.ytPlaylistsStatusText.visibility = View.VISIBLE
                            bindingDialog.ytPlaylistsRecyclerView.visibility = View.GONE
                        }
                    )
                }
            }
        }

        loadPlaylists()
        dialog.show()
    }

    private fun showSignalPathSheet() {
        val dialog = BottomSheetDialog(this)
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        val bindingDialog = DialogSignalPathBinding.inflate(layoutInflater)
        dialog.setContentView(bindingDialog.root)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        currentSignalPathDialog = dialog
        currentSignalPathDialogBinding = bindingDialog

        dialog.setOnDismissListener {
            if (currentSignalPathDialog == dialog) {
                currentSignalPathDialog = null
                currentSignalPathDialogBinding = null
            }
        }

        renderSignalPathInDialog()
        requestSignalPathForCurrentItem()
        dialog.show()
    }

    private fun renderSignalPathInDialog() {
        val bindingDialog = currentSignalPathDialogBinding ?: return
        val currentItem = browser?.currentMediaItem
        if (currentItem == null) {
            bindingDialog.signalPathErrorView.text = "Không có bài hát nào đang phát"
            bindingDialog.signalPathErrorView.visibility = View.VISIBLE
            bindingDialog.signalPathScrollView.visibility = View.GONE
            return
        }

        val info = currentSignalPathInfo
        val activeUrl = uiCatalog.activeEndpoint()
        val networkStage = SignalPathFormatter.formatNetworkStage(activeUrl)

        if (info == null) {
            if (currentSignalPathFailed) {
                bindingDialog.signalPathErrorView.text = "Không lấy được thông tin Signal Path"
                bindingDialog.signalPathErrorView.visibility = View.VISIBLE
                bindingDialog.signalPathScrollView.visibility = View.GONE
            } else {
                bindingDialog.signalPathErrorView.visibility = View.GONE
                bindingDialog.signalPathScrollView.visibility = View.VISIBLE
                bindingDialog.stageSourceLabel.text = "Đang tải..."
                bindingDialog.stageSourceDetails.text = ""
                bindingDialog.stageSourceDetails.visibility = View.GONE
                bindingDialog.stageServerAction.text = "Đang tải..."
                bindingDialog.stageServerSubtitle.visibility = View.GONE
                bindingDialog.stageDeliveryTitle.text = if (PlaybackQuality.current == StreamQuality.MOBILE_320) "Mobile 320" else "Original"
                bindingDialog.stageDeliveryDetails.text = ""
                bindingDialog.stageDeliveryDetails.visibility = View.GONE
                bindingDialog.stageNetworkTitle.text = networkStage
                bindingDialog.stagePlayerTitle.text = "Media3 / ExoPlayer"
                bindingDialog.stageOutputPipeline.text = "Android system audio pipeline"
                bindingDialog.stageOutputBitPerfect.text = "Bit-perfect: Not verified"
            }
            return
        }

        bindingDialog.signalPathErrorView.visibility = View.GONE
        bindingDialog.signalPathScrollView.visibility = View.VISIBLE

        // 1. SOURCE
        bindingDialog.stageSourceLabel.text = info.sourceLabel ?: "Unknown source"
        val sourceDetails = SignalPathFormatter.formatSourceDetails(info)
        if (sourceDetails.isNotBlank()) {
            bindingDialog.stageSourceDetails.text = sourceDetails
            bindingDialog.stageSourceDetails.visibility = View.VISIBLE
        } else {
            bindingDialog.stageSourceDetails.visibility = View.GONE
        }

        // 2. SERVER
        val (serverTitle, serverSubtitle) = SignalPathFormatter.formatServerStage(info)
        bindingDialog.stageServerAction.text = serverTitle
        if (!serverSubtitle.isNullOrBlank()) {
            bindingDialog.stageServerSubtitle.text = serverSubtitle
            bindingDialog.stageServerSubtitle.visibility = View.VISIBLE
        } else {
            bindingDialog.stageServerSubtitle.visibility = View.GONE
        }

        // 3. DELIVERY
        val (deliveryTitle, deliveryDetails) = SignalPathFormatter.formatDeliveryStage(info)
        bindingDialog.stageDeliveryTitle.text = deliveryTitle
        if (deliveryDetails.isNotBlank()) {
            bindingDialog.stageDeliveryDetails.text = deliveryDetails
            bindingDialog.stageDeliveryDetails.visibility = View.VISIBLE
        } else {
            bindingDialog.stageDeliveryDetails.visibility = View.GONE
        }

        // 4. NETWORK
        bindingDialog.stageNetworkTitle.text = networkStage

        // 5. PLAYER
        val isNormEnabled = getSharedPreferences("music_assistant_car", MODE_PRIVATE)
            .getBoolean("loudness_normalization", true)
        val normState = if (isNormEnabled) {
            getString(R.string.loudness_normalization_on)
        } else {
            getString(R.string.loudness_normalization_off)
        }
        bindingDialog.stagePlayerTitle.text = "Media3 / ExoPlayer (Chuẩn hóa: $normState)"
        bindingDialog.stagePlayerTitle.setOnClickListener {
            showLoudnessSettingsDialog()
        }

        // 6. OUTPUT
        bindingDialog.stageOutputPipeline.text = "Android system audio pipeline"
        bindingDialog.stageOutputBitPerfect.text = "Bit-perfect: Not verified"

        bindingDialog.btnClearStreamCache.setOnClickListener {
            com.bsdung.musicassistantcar.playback.StreamCacheManager.clearCache(this)
            Toast.makeText(this, "Đã xóa toàn bộ bộ nhớ đệm phát nhạc", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoudnessSettingsDialog() {
        val prefs = getSharedPreferences("music_assistant_car", MODE_PRIVATE)
        val current = prefs.getBoolean("loudness_normalization", true)
        val options = arrayOf(
            getString(R.string.loudness_normalization_on),
            getString(R.string.loudness_normalization_off),
        )
        val checkedItem = if (current) 0 else 1
        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle(R.string.loudness_normalization_title)
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val enabled = (which == 0)
                prefs.edit().putBoolean("loudness_normalization", enabled).apply()
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "${getString(R.string.loudness_normalization_title)}: ${options[which]}",
                    Toast.LENGTH_SHORT,
                ).show()
                renderSignalPathInDialog()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showTrackActionSheet(track: LibraryItem.Track) {
        val dialog = BottomSheetDialog(this)
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        val bindingDialog = DialogTrackActionsBinding.inflate(layoutInflater)
        dialog.setContentView(bindingDialog.root)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        bindingDialog.dialogTrackTitle.text = track.title
        bindingDialog.dialogTrackArtist.text = track.artist.ifBlank { "Nghệ sĩ không rõ" }

        bindingDialog.actionPlayTrack.setOnClickListener {
            dialog.dismiss()
            val playableTracks = libraryAdapter.getItems().filterIsInstance<LibraryItem.Track>().map { it.mediaItem }
            val trackIndex = playableTracks.indexOfFirst { it.mediaId == track.mediaItem.mediaId }.coerceAtLeast(0)
            if (playableTracks.isNotEmpty()) {
                playQueue(playableTracks, trackIndex)
            } else {
                val prepared = uiCatalog.withCurrentQuality(track.mediaItem)
                playQueue(listOf(prepared), 0)
            }
        }

        bindingDialog.actionPlayNext.setOnClickListener {
            dialog.dismiss()
            playNext(track)
        }

        bindingDialog.actionAddToQueue.setOnClickListener {
            dialog.dismiss()
            addToQueue(track)
        }

        // "Play From Here" — only in collection contexts (Artist / Album / Drive Folder / Playlist)
        val currentParent = navManager.currentParentId
        if (isCollectionContext(currentParent)) {
            bindingDialog.actionPlayFromHere.visibility = View.VISIBLE
            bindingDialog.actionPlayFromHere.setOnClickListener {
                dialog.dismiss()
                playFromHere(track)
            }
        } else {
            bindingDialog.actionPlayFromHere.visibility = View.GONE
        }

        // Download Action
        val canonicalId = RemoteCatalog.canonicalTrackMediaId(track.mediaItem) ?: track.id
        val trackId = canonicalId.removePrefix(RemoteCatalog.TRACK_PREFIX).toLongOrNull()
        val isDeviceTrack = track.id.startsWith(RemoteCatalog.DEVICE_PREFIX)

        if (trackId != null && !isDeviceTrack) {
            val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this)
            val status = dm.getDownloadStatus(trackId)
            bindingDialog.actionDownload.visibility = View.VISIBLE
            when (status.state) {
                com.bsdung.musicassistantcar.playback.DownloadState.OFFLINE_READY -> {
                    bindingDialog.layoutOfflineInfo.visibility = View.VISIBLE
                    bindingDialog.txtOfflineState.text = "Có thể nghe offline"
                    val record = dm.getRecord(trackId)
                    val sizeMb = String.format(java.util.Locale.US, "%.1f MB", (record?.fileSizeBytes ?: 0L) / (1024.0 * 1024.0))
                    val targetLabel = record?.storageTarget?.let { dm.formatStorageTargetLabel(it) } ?: "Bộ nhớ trong"
                    val dateStr = record?.downloadedAtMs?.let {
                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
                    } ?: ""
                    bindingDialog.txtOfflineDetails.text = "Dung lượng: $sizeMb • Nơi lưu: $targetLabel • Đã tải: $dateStr"

                    bindingDialog.actionDownloadIcon.setImageResource(R.drawable.ic_delete)
                    bindingDialog.actionDownloadText.text = "Xóa bản tải offline"
                }
                com.bsdung.musicassistantcar.playback.DownloadState.OFFLINE_MEDIA_UNAVAILABLE -> {
                    bindingDialog.layoutOfflineInfo.visibility = View.VISIBLE
                    bindingDialog.txtOfflineState.text = "Thẻ nhớ SD không khả dụng"
                    bindingDialog.txtOfflineDetails.text = "Bản tải lưu trên Thẻ nhớ SD hiện đang tháo. Sẽ tạm phát trực tuyến."
                    bindingDialog.actionDownloadIcon.setImageResource(R.drawable.ic_download_done)
                    bindingDialog.actionDownloadText.text = "Thẻ nhớ SD không khả dụng"
                }
                com.bsdung.musicassistantcar.playback.DownloadState.DOWNLOADING,
                com.bsdung.musicassistantcar.playback.DownloadState.QUEUED,
                com.bsdung.musicassistantcar.playback.DownloadState.WAITING_FOR_WIFI -> {
                    bindingDialog.layoutOfflineInfo.visibility = View.GONE
                    bindingDialog.actionDownloadIcon.setImageResource(R.drawable.ic_close)
                    bindingDialog.actionDownloadText.text = "Hủy tải bài hát"
                }
                com.bsdung.musicassistantcar.playback.DownloadState.STORAGE_TARGET_UNAVAILABLE,
                com.bsdung.musicassistantcar.playback.DownloadState.FAILED,
                com.bsdung.musicassistantcar.playback.DownloadState.CANCELLED -> {
                    bindingDialog.layoutOfflineInfo.visibility = View.GONE
                    bindingDialog.actionDownloadIcon.setImageResource(R.drawable.ic_download)
                    bindingDialog.actionDownloadText.text = "Thử lại tải về"
                }
                else -> {
                    bindingDialog.layoutOfflineInfo.visibility = View.GONE
                    bindingDialog.actionDownloadIcon.setImageResource(R.drawable.ic_download)
                    bindingDialog.actionDownloadText.text = "↓ Tải về"
                }
            }
            bindingDialog.actionDownload.setOnClickListener {
                dialog.dismiss()
                handleTrackDownloadClick(track)
            }
        } else {
            bindingDialog.layoutOfflineInfo.visibility = View.GONE
            bindingDialog.actionDownload.visibility = View.GONE
        }

        val isInPlaylist = currentParent.startsWith("playlist:")

        if (isInPlaylist) {
            val isFavoritesScreen = navManager.currentLabel.contains("Yêu thích", ignoreCase = true) ||
                navManager.currentLabel.contains("Favorites", ignoreCase = true)
            bindingDialog.actionRemoveFromPlaylist.visibility = View.VISIBLE
            bindingDialog.actionRemoveText.text = if (isFavoritesScreen) {
                "Xóa khỏi Yêu thích"
            } else {
                "Xóa khỏi danh sách phát này"
            }
            bindingDialog.actionRemoveFromPlaylist.setOnClickListener {
                dialog.dismiss()
                val playlistId = currentParent.removePrefix("playlist:").toIntOrNull()
                if (playlistId != null) {
                    uiExecutor.submit {
                        val result = runCatching { uiCatalog.removeFromPlaylist(playlistId, track.id) }
                        runOnUiThread {
                            result.onSuccess {
                                Toast.makeText(this@MainActivity, "Đã xóa khỏi danh sách phát", Toast.LENGTH_SHORT).show()
                                if (isFavoritesScreen) {
                                    refreshFavoriteForCurrentTrack()
                                }
                                loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                            }.onFailure {
                                Toast.makeText(this@MainActivity, "Không thể xóa bài hát", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        } else {
            bindingDialog.actionRemoveFromPlaylist.visibility = View.GONE
        }

        var isFav = false
        uiExecutor.submit {
            val fav = runCatching { uiCatalog.getFavoriteStatus(track.id) }.getOrDefault(false)
            runOnUiThread {
                if (dialog.isShowing) {
                    isFav = fav
                    if (fav) {
                        bindingDialog.actionFavoriteIcon.setImageResource(R.drawable.ic_heart_filled)
                        bindingDialog.actionFavoriteIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.accent_primary))
                        bindingDialog.actionFavoriteText.text = "Xóa khỏi Yêu thích"
                    } else {
                        bindingDialog.actionFavoriteIcon.setImageResource(R.drawable.ic_heart_outline)
                        bindingDialog.actionFavoriteIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                        bindingDialog.actionFavoriteText.text = "Thêm vào Yêu thích"
                    }
                }
            }
        }

        bindingDialog.actionFavorite.setOnClickListener {
            dialog.dismiss()
            val newFav = !isFav
            uiExecutor.submit {
                val result = runCatching { uiCatalog.setFavorite(track.id, newFav) }
                runOnUiThread {
                    result.onSuccess {
                        refreshFavoriteForCurrentTrack()
                        if (navManager.currentParentId.startsWith("playlist:") &&
                            (navManager.currentLabel.contains("Yêu thích", ignoreCase = true) ||
                             navManager.currentLabel.contains("Favorites", ignoreCase = true))) {
                            loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                        }
                    }.onFailure {
                        Toast.makeText(this@MainActivity, "Không thể cập nhật Yêu thích", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        bindingDialog.actionAddToPlaylist.setOnClickListener {
            dialog.dismiss()
            showPlaylistChooserForTracks(listOf(track.id))
        }

        val cleanArtist = track.artist.trim()
        val hasArtist = cleanArtist.isNotBlank() &&
            !cleanArtist.equals("Unknown", ignoreCase = true) &&
            !cleanArtist.equals("Nghệ sĩ không rõ", ignoreCase = true)
        if (hasArtist) {
            bindingDialog.actionOpenArtist.visibility = View.VISIBLE
            bindingDialog.actionOpenArtistText.text = "Xem nghệ sĩ: $cleanArtist"
            bindingDialog.actionOpenArtist.setOnClickListener {
                dialog.dismiss()
                captureCurrentScroll()
                loadChildren(RemoteCatalog.artistId(cleanArtist), 0, cleanArtist)
            }
            bindingDialog.actionAddArtistToPlaylist.visibility = View.VISIBLE
            bindingDialog.actionAddArtistToPlaylistText.text = "Thêm tất cả bài của $cleanArtist"
            bindingDialog.actionAddArtistToPlaylist.setOnClickListener {
                dialog.dismiss()
                showPlaylistChooserForCollection(kind = "artist", artist = cleanArtist)
            }
        } else {
            bindingDialog.actionOpenArtist.visibility = View.GONE
            bindingDialog.actionAddArtistToPlaylist.visibility = View.GONE
        }

        val cleanAlbum = track.album?.trim().orEmpty()
        val hasAlbum = cleanAlbum.isNotBlank() &&
            !cleanAlbum.equals("Unknown", ignoreCase = true) &&
            !cleanAlbum.equals("Album không rõ", ignoreCase = true)
        if (hasAlbum) {
            bindingDialog.actionOpenAlbum.visibility = View.VISIBLE
            bindingDialog.actionOpenAlbumText.text = "Xem album: $cleanAlbum"
            bindingDialog.actionOpenAlbum.setOnClickListener {
                dialog.dismiss()
                captureCurrentScroll()
                loadChildren(RemoteCatalog.albumId(cleanArtist, cleanAlbum), 0, cleanAlbum)
            }
            bindingDialog.actionAddAlbumToPlaylist.visibility = View.VISIBLE
            bindingDialog.actionAddAlbumToPlaylistText.text = "Thêm toàn bộ album vào DS phát"
            bindingDialog.actionAddAlbumToPlaylist.setOnClickListener {
                dialog.dismiss()
                showPlaylistChooserForCollection(kind = "album", artist = cleanArtist, album = cleanAlbum)
            }
        } else {
            bindingDialog.actionOpenAlbum.visibility = View.GONE
            bindingDialog.actionAddAlbumToPlaylist.visibility = View.GONE
        }

        bindingDialog.actionMultiSelect.setOnClickListener {
            dialog.dismiss()
            val isPlaylist = navManager.currentParentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX)
            enterEditMode(allowReorder = isPlaylist)
            libraryAdapter.toggleSelection(track.id)
        }

        dialog.show()
    }

    private fun showFolderActionSheet(folder: LibraryItem.Folder) {
        val isDriveRoot = folder.id.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX)
        val isDriveFolder = folder.id.startsWith(RemoteCatalog.DRIVE_FOLDER_PREFIX)
        if (!isDriveRoot && !isDriveFolder) return

        val dialog = BottomSheetDialog(this)
        val bindingDialog = DialogFolderActionsBinding.inflate(layoutInflater)
        dialog.setContentView(bindingDialog.root)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        bindingDialog.dialogFolderTitle.text = folder.title
        bindingDialog.actionOpenFolder.setOnClickListener {
            dialog.dismiss()
            captureCurrentScroll()
            loadChildren(folder.id, 0, folder.title)
        }

        // Extract root/path for folder collection actions
        val (folderRoot, folderPath) = if (isDriveRoot) {
            val r = folder.id.removePrefix(RemoteCatalog.DRIVE_ROOT_PREFIX).toIntOrNull() ?: 0
            Pair(r, "")
        } else {
            val payload = folder.id.removePrefix(RemoteCatalog.DRIVE_FOLDER_PREFIX)
            val colonIdx = payload.indexOf(':')
            val r = if (colonIdx >= 0) payload.substring(0, colonIdx).toIntOrNull() ?: 0 else 0
            val p = if (colonIdx >= 0) RemoteCatalog.safeDecode(payload.substring(colonIdx + 1)) else ""
            Pair(r, p)
        }
        val folderParams = CollectionParams(kind = "drive_folder", root = folderRoot, path = folderPath)

        bindingDialog.actionPlayFolderAll.setOnClickListener {
            dialog.dismiss()
            triggerCollectionPlayback(shuffle = false, explicitParams = folderParams)
        }

        bindingDialog.actionShuffleFolderAll.setOnClickListener {
            dialog.dismiss()
            triggerCollectionPlayback(shuffle = true, explicitParams = folderParams)
        }

        bindingDialog.actionAddFolderToPlaylist.setOnClickListener {
            dialog.dismiss()
            showPlaylistChooserForCollection(
                kind = "drive_folder",
                root = folderRoot,
                path = folderPath,
            )
        }

        dialog.show()
    }


    private fun showPlaylistChooserForTracks(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        val dialog = BottomSheetDialog(this)
        val bindingDialog = DialogPlaylistChooserBinding.inflate(layoutInflater)
        dialog.setContentView(bindingDialog.root)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        bindingDialog.actionCreateNewPlaylist.setOnClickListener {
            dialog.dismiss()
            showCreatePlaylistDialog(initialTrackIds = trackIds)
        }

        bindingDialog.playlistLoadingView.visibility = View.VISIBLE
        bindingDialog.playlistRecyclerView.layoutManager = LinearLayoutManager(this)

        uiExecutor.submit {
            val playlists = runCatching { uiCatalog.playlists() }.getOrDefault(emptyList())
            runOnUiThread {
                if (dialog.isShowing) {
                    bindingDialog.playlistLoadingView.visibility = View.GONE
                    bindingDialog.playlistRecyclerView.adapter = PlaylistChoiceAdapter(playlists) { playlist ->
                        dialog.dismiss()
                        uiExecutor.submit {
                            val outcome = PlaylistBatchWriter.write(trackIds) { ids ->
                                val res = uiCatalog.batchAddToPlaylist(playlist.id, ids)
                                PlaylistBatchWriter.Counts(res.added, res.skipped)
                            }
                            runOnUiThread {
                                val cleanName = MetadataDisplayHelper.cleanPlaylistName(playlist.name)
                                val msg = when {
                                    outcome.failed -> "Đã thêm ${outcome.added}/${outcome.requested} bài vào \"$cleanName\". Kiểm tra Playlist trước khi thử lại."
                                    outcome.skipped > 0 -> "Đã thêm ${outcome.added} bài vào \"$cleanName\", bỏ qua ${outcome.skipped} bài trùng/không khả dụng."
                                    else -> "Đã thêm ${outcome.added} bài vào \"$cleanName\""
                                }
                                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                if (isEditMode) exitEditMode()
                                if (outcome.added > 0 && (playlist.name.contains("Favorites", ignoreCase = true) || playlist.name.contains("Yêu thích", ignoreCase = true))) {
                                    refreshFavoriteForCurrentTrack()
                                }
                            }
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showPlaylistChooserForCollection(
        kind: String,
        artist: String? = null,
        album: String? = null,
        root: Int? = null,
        path: String? = null,
    ) {
        val dialog = BottomSheetDialog(this)
        val bindingDialog = DialogPlaylistChooserBinding.inflate(layoutInflater)
        dialog.setContentView(bindingDialog.root)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        bindingDialog.actionCreateNewPlaylist.setOnClickListener {
            dialog.dismiss()
            showCreatePlaylistDialog(
                collectionKind = kind,
                collectionArtist = artist,
                collectionAlbum = album,
                collectionRoot = root,
                collectionPath = path,
            )
        }

        bindingDialog.playlistLoadingView.visibility = View.VISIBLE
        bindingDialog.playlistRecyclerView.layoutManager = LinearLayoutManager(this)

        uiExecutor.submit {
            val playlists = runCatching { uiCatalog.playlists() }.getOrDefault(emptyList())
            runOnUiThread {
                if (dialog.isShowing) {
                    bindingDialog.playlistLoadingView.visibility = View.GONE
                    bindingDialog.playlistRecyclerView.adapter = PlaylistChoiceAdapter(playlists) { playlist ->
                        dialog.dismiss()
                        uiExecutor.submit {
                            val result = runCatching {
                                uiCatalog.addCollectionToPlaylist(
                                    playlistId = playlist.id,
                                    kind = kind,
                                    artist = artist,
                                    album = album,
                                    root = root,
                                    path = path,
                                )
                            }
                            runOnUiThread {
                                result.onSuccess { colRes ->
                                    val cleanName = MetadataDisplayHelper.cleanPlaylistName(playlist.name)
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Đã thêm ${colRes.added} bài vào \"$cleanName\"",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    if (playlist.name.contains("Favorites", ignoreCase = true) || playlist.name.contains("Yêu thích", ignoreCase = true)) {
                                        refreshFavoriteForCurrentTrack()
                                    }
                                }.onFailure {
                                    Toast.makeText(this@MainActivity, "Không thể thêm vào danh sách phát", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showCreatePlaylistDialog(
        initialTrackIds: List<String>? = null,
        collectionKind: String? = null,
        collectionArtist: String? = null,
        collectionAlbum: String? = null,
        collectionRoot: Int? = null,
        collectionPath: String? = null,
    ) {
        val input = EditText(this).apply {
            hint = "Tên danh sách phát"
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_tertiary))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setBackgroundResource(R.drawable.bg_search_input)
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(60))
            setPadding(32, 28, 32, 28)
        }

        val container = FrameLayout(this).apply {
            setPadding(48, 24, 48, 8)
            addView(input)
        }

        AlertDialog.Builder(this, R.style.Theme_MusicAssistantCar_Dialog)
            .setTitle("Tạo danh sách phát")
            .setView(container)
            .setPositiveButton("Tạo") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Tên danh sách phát không được để trống", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                uiExecutor.submit {
                    val result = runCatching { uiCatalog.createPlaylist(name) }
                    runOnUiThread {
                        result.onSuccess { playlist ->
                            if (!initialTrackIds.isNullOrEmpty()) {
                                uiExecutor.submit {
                                    // The backend has a hard 100-track batch limit.
                                    // Persist every accepted queue item in its original order.
                                    var added = 0
                                    var skipped = 0
                                    var failed = false
                                    for (batch in initialTrackIds.chunked(QueuePlaylistExportPolicy.BATCH_SIZE)) {
                                        val result = runCatching {
                                            uiCatalog.batchAddToPlaylist(playlist.id, batch)
                                        }.getOrNull()
                                        if (result == null) {
                                            failed = true
                                            break
                                        }
                                        added += result.added
                                        skipped += result.skipped
                                    }
                                    runOnUiThread {
                                        val message = when {
                                            failed -> "Đã tạo \"$name\", lưu được $added/${initialTrackIds.size} bài. Kiểm tra Playlist để thử lại phần còn thiếu."
                                            skipped > 0 -> "Đã tạo \"$name\": thêm $added bài, bỏ qua $skipped bài không khả dụng hoặc trùng."
                                            else -> "Đã tạo \"$name\" và thêm $added bài hát"
                                        }
                                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                                        if (isEditMode) exitEditMode()
                                    }
                                }
                            } else if (collectionKind != null) {
                                uiExecutor.submit {
                                    val addRes = runCatching {
                                        uiCatalog.addCollectionToPlaylist(
                                            playlistId = playlist.id,
                                            kind = collectionKind,
                                            artist = collectionArtist,
                                            album = collectionAlbum,
                                            root = collectionRoot,
                                            path = collectionPath,
                                        )
                                    }.getOrNull()
                                    runOnUiThread {
                                        val count = addRes?.added ?: 0
                                        Toast.makeText(this@MainActivity, "Đã tạo \"$name\" và thêm $count bài hát", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(this@MainActivity, "Đã tạo \"$name\"", Toast.LENGTH_SHORT).show()
                            }
                            if (navManager.currentParentId == "category:playlists") {
                                loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                            }
                        }.onFailure {
                            val msg = it.message ?: "Không thể tạo danh sách phát"
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    override fun onDestroy() {
        logWindowMetrics("onDestroy")
        isActivityInForeground = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopLyricsPositionLoop()
        progressController.detach()
        enrichmentController.cancel()
        browser?.removeListener(playerListener)
        browser?.release()
        browser = null
        browserFuture?.takeIf { !it.isDone }?.cancel(true)
        browserFuture = null
        searchDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
        com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this).removeListener(downloadListener)
        lyricsExecutor.shutdownNow()
        uiExecutor.shutdownNow()
        super.onDestroy()
    }
}
