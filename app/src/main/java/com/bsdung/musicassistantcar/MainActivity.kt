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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.net.Uri
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.bsdung.musicassistantcar.databinding.DialogFolderActionsBinding
import com.bsdung.musicassistantcar.databinding.DialogTrackActionsBinding
import com.bsdung.musicassistantcar.databinding.DialogPlaylistChooserBinding
import com.bsdung.musicassistantcar.databinding.DialogSignalPathBinding
import com.bsdung.musicassistantcar.ui.adapter.PlaylistChoiceAdapter
import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.PlaybackQuality
import com.bsdung.musicassistantcar.data.RemoteCatalog
import com.bsdung.musicassistantcar.data.StreamQuality
import com.bsdung.musicassistantcar.data.MediaTechnicalInfo
import com.bsdung.musicassistantcar.data.SignalPathInfo
import com.bsdung.musicassistantcar.ui.util.SignalPathFormatter
import com.bsdung.musicassistantcar.databinding.ActivityMainBinding
import com.bsdung.musicassistantcar.playback.MusicService
import com.bsdung.musicassistantcar.ui.adapter.LibraryAdapter
import com.bsdung.musicassistantcar.ui.adapter.QueueAdapter
import com.bsdung.musicassistantcar.playback.QueueHelper
import com.bsdung.musicassistantcar.playback.PlayNextAction
import com.bsdung.musicassistantcar.playback.AddToQueueAction
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
    private lateinit var enrichmentController: ProgressiveEnrichmentController
    private var isQueueVisible: Boolean = false
    private var isEditMode: Boolean = false
    private var libraryItemTouchHelper: ItemTouchHelper? = null
    private val navManager = NavigationManager()
    private val uiCatalog by lazy {
        RemoteCatalog(
            api = CarApiClient(),
            localRepoSupplier = { com.bsdung.musicassistantcar.data.LocalMediaRepository.getDefault(this) },
            downloadManagerSupplier = { com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this) },
        )
    }
    private val uiExecutor = Executors.newSingleThreadExecutor()
    private val prewarmExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prewarmedMediaKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var prewarmGeneration: Long = 0L

    private var activeBackendUrl: String? = null
    private var searchDebounceRunnable: Runnable? = null
    private var currentLoadRequestId: Long = 0L
    private var savedRecyclerState: Parcelable? = null
    private var pendingRestorePosition: Int? = null
    private var pendingRestoreOffset: Int? = null
    private val pageSize = 30
    private var currentCollectionRequestId: Long = 0L
    private val collectionFetchExecutor = Executors.newSingleThreadExecutor()

    private fun captureCurrentScroll() {
        val lm = binding.recyclerView.layoutManager as? LinearLayoutManager
        val pos = lm?.findFirstVisibleItemPosition() ?: 0
        val offset = lm?.findViewByPosition(pos)?.top ?: 0
        navManager.saveCurrentScroll(pos, offset)
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

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateNowPlaying()
            if (isQueueVisible) {
                renderQueue()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                requestTechnicalInfoForCurrentItem()
                requestSignalPathForCurrentItem()
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

        if (savedInstanceState != null) {
            savedRecyclerState = savedInstanceState.getParcelable("recycler_state")
            isQueueVisible = savedInstanceState.getBoolean("is_queue_visible", false)
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
                    if (isEditMode) {
                        exitEditMode()
                        return
                    }
                    if (isQueueVisible) {
                        hideQueue()
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
                    playQueue(tracks, index)
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

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                return libraryAdapter.moveItem(from, to)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val currentParent = navManager.currentParentId
                if (currentParent.startsWith(RemoteCatalog.PLAYLIST_PREFIX)) {
                    val playlistId = currentParent.removePrefix(RemoteCatalog.PLAYLIST_PREFIX).toIntOrNull()
                    if (playlistId != null) {
                        val currentTrackIds = libraryAdapter.getItems()
                            .filterIsInstance<LibraryItem.Track>()
                            .map { it.id }
                        val offset = navManager.currentPage * pageSize
                        uiExecutor.submit {
                            val ok = runCatching { uiCatalog.reorderPlaylist(playlistId, currentTrackIds, offset) }.getOrDefault(false)
                            runOnUiThread {
                                if (!ok) {
                                    Toast.makeText(this@MainActivity, "Không thể lưu thứ tự danh sách phát", Toast.LENGTH_SHORT).show()
                                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                                }
                            }
                        }
                    }
                }
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
                if (queueTrack.isCurrent) {
                    if (player.isPlaying) player.pause() else player.play()
                } else {
                    player.seekToDefaultPosition(queueTrack.queueIndex)
                    player.play()
                }
            },
            onRemoveClick = { queueTrack ->
                removeUpcomingItem(queueTrack.queueIndex)
            },
            onStartDrag = { viewHolder ->
                android.util.Log.i("QueueDrag", "MainActivity onStartDrag called for pos=${viewHolder.bindingAdapterPosition}")
                itemTouchHelper?.startDrag(viewHolder)
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

        // Setup ProgressController
        progressController = ProgressController(
            seekBar = binding.seekBar,
            currentTimeView = binding.currentTimeView,
            totalTimeView = binding.totalTimeView,
            onSeekTo = { targetMs ->
                browser?.seekTo(targetMs)
            },
        )

        // Setup Controls
        binding.playPauseButton.setOnClickListener {
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

        binding.queueBackButton.setOnClickListener {
            hideQueue()
        }

        binding.queueClearButton.setOnClickListener {
            clearUpcoming()
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
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
            goBack()
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
                val query = s?.toString().orEmpty().trim()
                binding.searchClearButton.visibility = if (query.isNotBlank()) View.VISIBLE else View.GONE
                searchDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
                if (query.isNotBlank()) {
                    val r = Runnable { runSearch(query) }
                    searchDebounceRunnable = r
                    mainHandler.postDelayed(r, 300)
                } else if (navManager.currentParentId == "search") {
                    val r = Runnable { goBack() }
                    searchDebounceRunnable = r
                    mainHandler.postDelayed(r, 300)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

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

        binding.collectionPlayAllButton.setOnClickListener {
            triggerCollectionPlayback(shuffle = false)
        }

        binding.collectionShuffleButton.setOnClickListener {
            triggerCollectionPlayback(shuffle = true)
        }

        binding.collectionAddAllButton.setOnClickListener {
            triggerCollectionAddToQueue()
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
                        if (isQueueVisible) {
                            showQueue()
                        }
                        if (navManager.searchQuery.isNotBlank()) {
                            runSearch(navManager.searchQuery, navManager.currentPage)
                        } else {
                            loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                        }
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
        binding.searchInput.text?.clear()
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
        if (wasInSearch && binding.searchInput.text?.isNotEmpty() == true) {
            binding.searchInput.text?.clear()
        }
        if (previous == null) {
            loadRoot()
            return
        }
        pendingRestorePosition = previous.firstVisiblePosition
        pendingRestoreOffset = previous.topOffsetPx
        loadChildren(previous.parentId, previous.page, previous.label, previous.sort)
    }

    private fun loadChildren(
        parentId: String,
        page: Int,
        label: String,
        sort: String = navManager.currentSort,
    ) {
        if (isEditMode) exitEditMode()
        val mediaBrowser = browser
        if (mediaBrowser == null) {
            Log.w(TAG, "loadChildren: MediaBrowser not ready, skipping parentId=$parentId")
            return
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

        binding.loadingView.visibility = View.VISIBLE
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

                        renderItems(uiItems)
                        Log.i(TAG, "ADAPTER ITEM COUNT: ${libraryAdapter.itemCount}")
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
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            goBack()
            return
        }

        if (page == 0) {
            captureCurrentScroll()
            navManager.startSearch(cleanQuery)
        } else {
            navManager.setPage(page)
        }
        updateHeaderAndBreadcrumbs()

        binding.loadingView.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE

        val requestId = ++currentLoadRequestId
        enrichmentController.nextGeneration()

        uiExecutor.submit {
            val result = runCatching {
                uiCatalog.search(cleanQuery, page, pageSize)
            }
            runOnUiThread {
                if (requestId != currentLoadRequestId) return@runOnUiThread
                result
                    .onSuccess { rawItems ->
                        val uiItems = mapToUiItems(rawItems)
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

        if (uiItems.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyText.text = when {
                navManager.searchQuery.isNotBlank() -> "Không tìm thấy bài phù hợp"
                navManager.currentParentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX) -> "Danh sách phát chưa có bài hát"
                else -> "Chưa có bài hát trong thư mục này"
            }
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
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

        val showPagination = !navManager.isAtRoot
        binding.pageBar.visibility = if (showPagination) View.VISIBLE else View.GONE
        binding.previousPageButton.visibility =
            if (showPagination && navManager.currentPage > 0) View.VISIBLE else View.INVISIBLE
        binding.nextPageButton.visibility =
            if (showPagination && uiItems.size >= pageSize) View.VISIBLE else View.INVISIBLE
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
        // Use breadcrumb trail in expanded dual-panel mode (more screen real estate),
        // display title only in compact/medium single-column mode.
        val baseTitle = if (currentWidthClass() == WindowWidthClass.EXPANDED) {
            navManager.breadcrumbTrail()
        } else {
            navManager.displayTitle()
        }
        binding.libraryTitleView.text = if (navManager.currentPage == 0) {
            baseTitle
        } else {
            "$baseTitle · Trang ${navManager.currentPage + 1}"
        }

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

        updateCollectionActionBar()
        updateCollectionSortButton()
    }

    private fun updateCollectionSortButton() {
        val parentId = navManager.currentParentId
        val isSortable = !isEditMode && !isQueueVisible && isSortableCollection(parentId)
        if (isSortable) {
            binding.collectionSortButton.visibility = View.VISIBLE
            binding.collectionSortButton.text = when (navManager.currentSort) {
                "name_asc" -> "⇅ A → Z"
                "name_desc" -> "⇅ Z → A"
                else -> "⇅ Mặc định"
            }
        } else {
            binding.collectionSortButton.visibility = View.GONE
        }
    }

    private fun isSortableCollection(parentId: String): Boolean =
        parentId.startsWith(RemoteCatalog.DRIVE_FOLDER_PREFIX) ||
        parentId.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX) ||
        parentId.startsWith(RemoteCatalog.ALBUM_PREFIX) ||
        parentId.startsWith(RemoteCatalog.ARTIST_PREFIX)

    private fun showSortDialog() {
        val parentId = navManager.currentParentId
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
        val showBar = !isEditMode && !isQueueVisible && isCollectionContext(parentId)
        binding.collectionActionBar.visibility = if (showBar) View.VISIBLE else View.GONE
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
        isEditMode = true
        libraryAdapter.setEditMode(true, allowReorder)
        binding.breadcrumbBar.visibility = View.GONE
        binding.collectionActionBar.visibility = View.GONE
        binding.collectionSortButton.visibility = View.GONE
        binding.editModeBar.visibility = View.VISIBLE
        val isPlaylist = navManager.currentParentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX)
        binding.editRemoveButton.visibility = if (isPlaylist) View.VISIBLE else View.GONE
        binding.editAddToPlaylistButton.visibility = View.VISIBLE
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
        binding.editSelectedCountView.text = "$count đã chọn"
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
                    Toast.makeText(this, "Đã xóa ${res.removed} bài khỏi danh sách phát", Toast.LENGTH_SHORT).show()
                    exitEditMode()
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }.onFailure {
                    Toast.makeText(this, "Không thể xóa bài hát", Toast.LENGTH_SHORT).show()
                }
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
        isQueueVisible = true
        binding.queueButton.setColorFilter(ContextCompat.getColor(this, R.color.accent_primary))
        binding.queueButton.setBackgroundResource(R.drawable.bg_button_tertiary_active)

        // Hide library / search content views
        binding.breadcrumbBar.visibility = View.GONE
        binding.collectionActionBar.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.pageBar.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.loadingView.visibility = View.GONE
        binding.searchContainer.visibility = View.GONE

        // Show queue container
        binding.queueContainer.visibility = View.VISIBLE
        renderQueue()
    }


    private fun hideQueue() {
        isQueueVisible = false
        binding.queueButton.setColorFilter(ContextCompat.getColor(this, R.color.text_primary))
        binding.queueButton.setBackgroundResource(R.drawable.bg_button_control)

        binding.queueContainer.visibility = View.GONE

        // Restore normal views
        binding.searchContainer.visibility = View.VISIBLE
        binding.breadcrumbBar.visibility = View.VISIBLE
        updateHeaderAndBreadcrumbs()

        if (libraryAdapter.itemCount > 0) {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
        } else {
            binding.recyclerView.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
        }
        binding.pageBar.visibility = if (!navManager.isAtRoot) View.VISIBLE else View.GONE
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



    private fun renderQueue() {
        val player = browser
        if (player == null) {
            queueAdapter.submitItems(listOf(QueueListItem.Empty("Chưa có bài trong hàng đợi")))
            binding.queueClearButton.visibility = View.GONE
            return
        }

        val queueItems = buildCurrentQueueList()
        val currentIndex = player.currentMediaItemIndex
        val isPlaying = player.isPlaying

        val listItems = QueueHelper.buildQueueList(queueItems, currentIndex, isPlaying)
        queueAdapter.submitItems(listItems)

        val hasUpcoming = currentIndex in 0 until (queueItems.size - 1)
        binding.queueClearButton.visibility = if (hasUpcoming) View.VISIBLE else View.GONE
    }

    private fun playNext(track: LibraryItem.Track) {
        val player = browser ?: return
        val currentQueue = buildCurrentQueueList()
        val currentIndex = player.currentMediaItemIndex
        val preparedItem = uiCatalog.withCurrentQuality(track.mediaItem)
        val action = QueueHelper.planPlayNext(currentQueue, currentIndex, preparedItem)

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

    private fun removeUpcomingItem(targetIndex: Int) {
        val player = browser ?: return
        val currentIndex = player.currentMediaItemIndex
        if (!QueueHelper.planRemoveUpcoming(player.mediaItemCount, currentIndex, targetIndex)) {
            return
        }
        player.removeMediaItem(targetIndex)
        schedulePrewarmQueue(buildCurrentQueueList(), player.currentMediaItemIndex)
        if (isQueueVisible) renderQueue()
    }

    private fun clearUpcoming() {
        val player = browser ?: return
        val currentIndex = player.currentMediaItemIndex
        val range = QueueHelper.planClearUpcoming(player.mediaItemCount, currentIndex) ?: return
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
            binding.favoriteButton.visibility = View.GONE
            currentFavoriteMediaId = null
            currentFavoriteState = null
            binding.titleView.text = "Chọn một bài để bắt đầu"
            binding.artistView.visibility = View.GONE
            binding.albumView.visibility = View.GONE
            binding.technicalInfoView.text = ""
            binding.technicalInfoView.visibility = View.GONE
            binding.coverView.setImageResource(R.drawable.ic_album_placeholder)

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
                binding.queueButton.visibility = View.VISIBLE
                binding.shuffleButton.visibility = View.VISIBLE
                binding.repeatButton.visibility = View.VISIBLE
            } else {
                // Compact/Medium: hide secondary controls when idle to save vertical space
                binding.seekBar.visibility = View.GONE
                binding.timeRow.visibility = View.GONE
                binding.qualityChip.visibility = View.GONE
                binding.signalPathButton?.visibility = View.GONE
                binding.queueButton.visibility = View.GONE
                binding.shuffleButton.visibility = View.INVISIBLE
                binding.repeatButton.visibility = View.INVISIBLE
            }

            libraryAdapter.updateNowPlaying(null, false)
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

        // Artwork size when track is active: driven by width class
        val activeSizeRes = when (widthClass) {
            WindowWidthClass.COMPACT  -> R.dimen.artwork_size_compact
            WindowWidthClass.MEDIUM   -> R.dimen.artwork_size_medium
            WindowWidthClass.EXPANDED -> R.dimen.artwork_size_expanded
        }
        val activeSize = resources.getDimensionPixelSize(activeSizeRes)
        val lp = binding.coverView.layoutParams
        if (lp.width != activeSize || lp.height != activeSize) {
            lp.width = activeSize
            lp.height = activeSize
            binding.coverView.layoutParams = lp
        }

        ArtworkLoader.loadArtwork(
            imageView = binding.coverView,
            uri = metadata.artworkUri?.toString(),
            cornerRadiusDp = 16f,
            placeholderResId = R.drawable.ic_album_placeholder,
        )

        // Make all controls visible in active state
        binding.seekBar.visibility = View.VISIBLE
        binding.seekBar.isEnabled = true
        binding.timeRow.visibility = View.VISIBLE
        binding.qualityChip.visibility = View.VISIBLE
        binding.signalPathButton?.visibility = View.VISIBLE
        binding.queueButton.visibility = View.VISIBLE
        binding.shuffleButton.visibility = View.VISIBLE
        binding.repeatButton.visibility = View.VISIBLE

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
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }
            }
        }
        override fun onDownloadProgress(trackId: Long, bytesDownloaded: Long, totalBytes: Long) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
            }
        }
        override fun onDownloadFinalizing(trackId: Long) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
            }
        }
        override fun onDownloadCompleted(trackId: Long, record: com.bsdung.musicassistantcar.playback.OfflineTrackRecord) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
                Toast.makeText(this@MainActivity, "Đã tải xong: ${record.title}", Toast.LENGTH_SHORT).show()
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADED_ID || navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }
            }
        }
        override fun onDownloadFailed(trackId: Long, error: String) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
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
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }
            }
        }
        override fun onDownloadDeleted(trackId: Long) {
            runOnUiThread {
                libraryAdapter.notifyDataSetChanged()
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
                    // Section 23: Delete while playing safety policy
                    val currentItem = browser?.currentMediaItem
                    val currentTrackId = currentItem?.mediaMetadata?.extras?.getLong("track_id_long")
                        ?: currentItem?.mediaMetadata?.extras?.getInt("track_id")?.toLong()
                        ?: currentItem?.mediaId?.removePrefix(RemoteCatalog.TRACK_PREFIX)?.toLongOrNull()
                    if (currentTrackId == trackId) {
                        Log.i("MainActivity", "Track $trackId is currently playing, stopping playback before deleting offline copy")
                        browser?.stop()
                        browser?.clearMediaItems()
                    }
                    dm.deleteDownload(trackId)
                    libraryAdapter.notifyDataSetChanged()
                    Toast.makeText(this, "Đã xóa bản tải offline", Toast.LENGTH_SHORT).show()
                    if (navManager.currentParentId == RemoteCatalog.DOWNLOADED_ID || navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                        loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
            return
        }

        val status = dm.getDownloadStatus(trackId)
        when (status.state) {
            com.bsdung.musicassistantcar.playback.DownloadState.DOWNLOADING,
            com.bsdung.musicassistantcar.playback.DownloadState.QUEUED -> {
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
            com.bsdung.musicassistantcar.playback.DownloadState.FAILED,
            com.bsdung.musicassistantcar.playback.DownloadState.CANCELLED -> {
                dm.retryDownload(trackId)
                libraryAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Đang thử lại...", Toast.LENGTH_SHORT).show()
                if (navManager.currentParentId == RemoteCatalog.DOWNLOADING_ID) {
                    loadChildren(navManager.currentParentId, navManager.currentPage, navManager.currentLabel)
                }
                return
            }
            else -> {}
        }

        Toast.makeText(this, "Bắt đầu tải về...", Toast.LENGTH_SHORT).show()
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
        logWindowMetrics("onResume")
        if (browser != null) {
            refreshBackendStatus()
            // Reconcile Now Playing after returning from background or launcher.
            updateNowPlaying()
        }
    }

    override fun onStop() {
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
        updateHeaderAndBreadcrumbs()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("recycler_state", binding.recyclerView.layoutManager?.onSaveInstanceState())
        outState.putBoolean("is_queue_visible", isQueueVisible)
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
                    val dateStr = record?.downloadedAtMs?.let {
                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
                    } ?: ""
                    bindingDialog.txtOfflineDetails.text = "Dung lượng: $sizeMb • Đã tải: $dateStr"

                    bindingDialog.actionDownloadIcon.setImageResource(R.drawable.ic_delete)
                    bindingDialog.actionDownloadText.text = "Xóa bản tải offline"
                }
                com.bsdung.musicassistantcar.playback.DownloadState.DOWNLOADING,
                com.bsdung.musicassistantcar.playback.DownloadState.QUEUED -> {
                    bindingDialog.layoutOfflineInfo.visibility = View.GONE
                    bindingDialog.actionDownloadIcon.setImageResource(R.drawable.ic_close)
                    bindingDialog.actionDownloadText.text = "Hủy tải bài hát"
                }
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
                            val result = runCatching { uiCatalog.batchAddToPlaylist(playlist.id, trackIds) }
                            runOnUiThread {
                                result.onSuccess { batchRes ->
                                    val cleanName = MetadataDisplayHelper.cleanPlaylistName(playlist.name)
                                    val msg = if (trackIds.size == 1) {
                                        "Đã thêm vào \"$cleanName\""
                                    } else {
                                        "Đã thêm ${batchRes.added} bài vào \"$cleanName\""
                                    }
                                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                    if (isEditMode) {
                                        exitEditMode()
                                    }
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
                                    val addRes = runCatching { uiCatalog.batchAddToPlaylist(playlist.id, initialTrackIds) }.getOrNull()
                                    runOnUiThread {
                                        val count = addRes?.added ?: initialTrackIds.size
                                        Toast.makeText(this@MainActivity, "Đã tạo \"$name\" và thêm $count bài hát", Toast.LENGTH_SHORT).show()
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
        progressController.detach()
        enrichmentController.cancel()
        browser?.removeListener(playerListener)
        browser?.release()
        browser = null
        browserFuture?.takeIf { !it.isDone }?.cancel(true)
        browserFuture = null
        searchDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
        com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(this).removeListener(downloadListener)
        uiExecutor.shutdownNow()
        super.onDestroy()
    }
}
