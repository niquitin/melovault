package com.bsdung.musicassistantcar.data.telegram

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
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
    )
    private val library = TelegramAccountLibrary()
    private val auth = TelegramAuthorizationFlow(library)
    private lateinit var status: TextView
    private lateinit var note: TextView
    private lateinit var authControls: LinearLayout
    private lateinit var nowPlayingTitle: TextView
    private lateinit var playPause: Button
    private var currentSongIndex = -1
    private lateinit var input: EditText
    private lateinit var submit: Button
    private lateinit var qrLogin: Button
    private lateinit var refresh: Button
    private lateinit var globalSearch: Button
    private lateinit var libraryTitle: TextView
    private lateinit var songFilter: EditText
    private lateinit var openDocument: Button
    private lateinit var favoriteButton: Button
    private lateinit var playlistButton: Button
    private lateinit var logout: Button
    private lateinit var tracks: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var player: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controllerReleased = false
    private data class PendingPlayback(val items: List<MediaItem>, val startIndex: Int)
    private var pendingPlayback: PendingPlayback? = null
    private val cachedSongPaths = java.util.concurrent.ConcurrentHashMap<Long, String>()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_MusicAssistantCar)
        buildUi()
        connectPlaybackService()
        if (BuildConfig.TELEGRAM_APP_API_ID <= 0 ||
            BuildConfig.TELEGRAM_APP_API_HASH.length != 32) {
            showMessage("Chưa cấu hình Telegram API cho bản thử nội bộ.")
            return
        }
        initNative()
    }

    private val playbackListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            showMessage("Không phát được tệp âm thanh (Media3 mã ${error.errorCode}).")
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (::playPause.isInitialized) playPause.text = if (isPlaying) "Ⅱ" else "▶"
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            nowPlayingTitle.text = mediaItem?.mediaMetadata?.title ?: "Chọn một bài hát để bắt đầu"
            val id = mediaItem?.mediaId?.removePrefix("telegram:")?.toLongOrNull()
            currentSongIndex = if (mediaItem?.mediaId?.startsWith("telegram:") == true) {
                songs.indexOfFirst { it.messageId == id }
            } else -1
            updatePlaylistButtons()
            if (currentSongIndex >= 0 && auth.isReady()) {
                prefetchNextCachedNeighbor()
            }
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
                controller.addListener(playbackListener)
                playbackListener.onIsPlayingChanged(controller.isPlaying)
                updatePlaylistButtons()
                nowPlayingTitle.text = controller.currentMediaItem?.mediaMetadata?.title
                    ?: "Chọn một bài hát để bắt đầu"
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
        queueSongOrder = if (playlistMode) shownSongs.toList() else songs.toList()
        library.authorizedUser()?.let { owner ->
            ShareablePlaybackService.configureTelegramQueue(
                owner.value, filesRoot, queueSongOrder.map { entry ->
                    ShareablePlaybackService.QueueTrack(
                        entry.messageId, entry.fileId, entry.title,
                        cachedSongPaths[entry.messageId]
                    )
                }
            )
        }
        val playable = queueSongOrder.mapNotNull { item ->
            val path = cachedSongPaths[item.messageId] ?: return@mapNotNull null
            val file = File(path)
            if (!file.isFile || !TelegramAccountStoragePolicy.isOwnedFile(filesRoot, file)) {
                cachedSongPaths.remove(item.messageId)
                return@mapNotNull null
            }
            item to mediaItem(Uri.fromFile(file), item.title, "telegram:${item.messageId}")
        }
        val selectedIndex = playable.indexOfFirst { it.first.messageId == song.messageId }
        if (selectedIndex >= 0) {
            enqueueItems(playable.map { it.second }, selectedIndex)
        } else {
            enqueueItems(listOf(mediaItem(Uri.fromFile(target), song.title,
                "telegram:${song.messageId}")), 0)
        }
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

    private val ink get() = Color.rgb(233, 241, 250)
    private val subtle get() = Color.rgb(158, 176, 195)
    private val accent get() = Color.rgb(67, 212, 174)
    private val midnight get() = Color.rgb(12, 19, 31)
    private val panel get() = Color.rgb(25, 37, 53)

    private fun rounded(color: Int, radius: Int = 14): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color)
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
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(midnight)
            setPadding(dp(16), dp(12), dp(16), dp(10))
        }
        TextView(this).apply {
            text = "MeloVault"
            textSize = 27f
            setTextColor(ink)
            setTypeface(null, android.graphics.Typeface.BOLD)
            root.addView(this)
        }
        note = TextView(this).apply {
            text = "TELEGRAM  ·  NHẠC CÁ NHÂN  ·  PREVIEW"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(accent)
            setPadding(0, 0, 0, dp(10))
            root.addView(this)
        }
        status = TextView(this).apply {
            text = "Đang khởi tạo Telegram…"
            textSize = 13f
            setTextColor(ink)
            background = rounded(panel)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            root.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }
        authControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            root.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
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
            orientation = LinearLayout.HORIZONTAL
            root.addView(this, LinearLayout.LayoutParams(-1, dp(47)).apply {
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
            orientation = LinearLayout.HORIZONTAL
            root.addView(this, LinearLayout.LayoutParams(-1, dp(43)).apply {
                bottomMargin = dp(10)
            })
        }
        globalSearch = uiButton("♫  Tìm nhạc mọi chat Telegram").apply {
            isEnabled = false
            setOnClickListener { scanAllTelegramAudio() }
            sourceAllRow.addView(this, LinearLayout.LayoutParams(-1, -1))
        }
        libraryTitle = TextView(this).apply {
            text = "THƯ VIỆN NHẠC  ·  SAVED MESSAGES"
            textSize = 12f
            setTextColor(subtle)
            setTypeface(null, android.graphics.Typeface.BOLD)
            root.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(6)
            })
        }
        songFilter = EditText(this).apply {
            hint = "Tìm bài hát theo tên…"
            setSingleLine(true)
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(subtle)
            background = rounded(panel)
            setPadding(dp(13), dp(9), dp(13), dp(9))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(
                    s: CharSequence?, start: Int, before: Int, count: Int) {
                    refreshSongRows()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            root.addView(this, LinearLayout.LayoutParams(-1, dp(45)).apply {
                bottomMargin = dp(8)
            })
        }
        val playlistRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            root.addView(this, LinearLayout.LayoutParams(-1, dp(43)).apply {
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
        }
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
            mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val item = super.getView(position, convertView, parent) as TextView
                item.textSize = 14f
                item.setTextColor(ink)
                item.maxLines = 2
                item.ellipsize = android.text.TextUtils.TruncateAt.END
                item.background = rounded(panel, 11)
                item.setPadding(dp(14), dp(12), dp(14), dp(12))
                item.minHeight = dp(62)
                return item
            }
        }
        tracks.adapter = adapter
        tracks.onItemClickListener = android.widget.AdapterView.OnItemClickListener {
                _, _, position, _ ->
            shownSongs.getOrNull(position)?.let { downloadAndPlay(it) }
        }
        root.addView(tracks, LinearLayout.LayoutParams(-1, 0, 1f))
        val playerBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            root.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
            })
        }
        TextView(this).apply {
            text = "NOW PLAYING"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(accent)
            playerBar.addView(this)
        }
        nowPlayingTitle = TextView(this).apply {
            text = "Chọn một bài hát để bắt đầu"
            textSize = 15f
            setTextColor(ink)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, dp(8))
            playerBar.addView(this)
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            playerBar.addView(this, LinearLayout.LayoutParams(-1, dp(43)))
        }
        fun control(symbol: String, action: () -> Unit): Button =
            uiButton(symbol).apply {
                textSize = 20f
                setOnClickListener { action() }
                controls.addView(this, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    leftMargin = dp(4)
                    rightMargin = dp(4)
                })
            }
        control("⏮") { playAdjacent(-1) }
        playPause = control("▶") {
            player?.let { controller ->
                if (controller.currentMediaItem != null) {
                    if (controller.isPlaying) controller.pause() else controller.play()
                }
            }
        }
        playPause.background = rounded(accent)
        playPause.setTextColor(midnight)
        control("⏭") { playAdjacent(1) }
        logout = uiButton("Đăng xuất Telegram").apply {
            textSize = 11f
            isEnabled = false
            setTextColor(subtle)
            setOnClickListener { logoutUser() }
            root.addView(this, LinearLayout.LayoutParams(-1, dp(35)).apply {
                topMargin = dp(6)
            })
        }
        authControls.visibility = View.GONE
        setContentView(root)
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
        val all = if (playlistMode) {
            favoriteIds.mapNotNull { id -> songs.firstOrNull { it.messageId == id } }
        } else songs.toList()
        val q = if (::songFilter.isInitialized) songFilter.text?.toString()?.trim().orEmpty()
            else ""
        shownSongs = if (q.isBlank()) all else all.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.filename.contains(q, ignoreCase = true)
        }
        adapter.clear()
        adapter.addAll(shownSongs.map { "${it.title} • ${it.filename}" })
        adapter.notifyDataSetChanged()
    }

    private fun toggleCurrentFavorite() {
        val account = library.authorizedUser() ?: return
        if (!auth.isReady()) return
        val target = currentTelegramMessageId() ?: return
        if (songs.none { it.messageId == target }) return
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
        val controller = player
        if (controller != null) {
            if (delta > 0 && controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
                return
            }
            if (delta < 0 && controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
                return
            }
        }
        val currentId = currentTelegramMessageId() ?: return
        val index = queueSongOrder.indexOfFirst { it.messageId == currentId }
        if (index < 0) return
        val target = queueSongOrder.getOrNull(index + delta) ?: return
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

    private fun send(type: String, tag: String? = null, configure: ((JSONObject) -> Unit)? = null) {
        ShareableTdlibOwner.send(type, tag, configure)
    }

    private fun onNativeEvent(result: JSONObject, sessionDb: File, secret: String) {
        val kind = result.optString("@type")
        val tag = result.optString("@extra")
        if (kind == "error") {
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
                    }
                    scanAllTelegramAudio()
                }
            } catch (_: IllegalArgumentException) {
                showMessage("Không xác minh được tài khoản Telegram.")
            }
            return
        }
        if (tag == "savedChat" && kind == "chat") {
            savedChatId = result.optLong("id")
            if (!allChatsMode) requestHistory()
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
            playIfDownloaded(result)
        }
        if (kind == "file" && tag.startsWith("prefetch:")) {
            observePrefetch(result)
        }
        if (kind == "updateFile") {
            result.optJSONObject("file")?.let {
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
                }
            }
            "authorizationStateLoggingOut", "authorizationStateClosing",
            "authorizationStateClosed" -> {
                globalScanGeneration++
                globalScanLoading = false
                ShareableTdlibOwner.revokeAuthorization()
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
                    openDocument.isEnabled = false
                    logout.isEnabled = false
                    submit.isEnabled = false
                    currentSongIndex = -1
                    nowPlayingTitle.text = "Chọn một bài hát để bắt đầu"
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
        return Song(fileId, title, filename, key)
    }

    private fun resetSource(modeAllChats: Boolean) {
        globalScanGeneration++
        globalScanLoading = false
        historyLoading = false
        allChatsMode = modeAllChats
        downloadWaiting = null
        prefetchWaiting = null
        seenSongMessageIds.clear()
        songs.clear()
        shownSongs = emptyList()
        queueSongOrder = emptyList()
        cachedSongPaths.clear()
        playlistMode = false
        ShareablePlaybackService.clearTelegramQueue()
        runOnUiThread {
            adapter.clear()
            songFilter.text?.clear()
            libraryTitle.text = if (modeAllChats)
                "THƯ VIỆN NHẠC  ·  CÁC CHAT TELEGRAM"
                else "THƯ VIỆN NHẠC  ·  SAVED MESSAGES"
            refresh.isEnabled = auth.isReady()
            globalSearch.isEnabled = auth.isReady()
            updatePlaylistButtons()
        }
    }

    private fun loadSavedMessages() {
        if (!auth.isReady()) return
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

    private fun scanAllTelegramAudio() {
        if (!auth.isReady()) return
        resetSource(true)
        globalScanPhase = "audio"
        globalScanOffset = ""
        globalScanPages = 0
        globalScanLoading = true
        runOnUiThread { globalSearch.isEnabled = false }
        showMessage("Đang tìm tệp nhạc trong các cuộc trò chuyện Telegram…")
        requestGlobalPage()
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
            runOnUiThread {
                globalSearch.isEnabled = true
                status.text = "Tìm thấy ${songs.size} bài từ các chat Telegram " +
                    "(quét giới hạn mỗi loại 15 trang)."
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
        val hasMore = newOffset.isNotEmpty() && newOffset != globalScanOffset &&
            globalScanPages < maxGlobalPagesPerKind
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
        prefetchWaiting = next.fileId
        runCatching {
            send("downloadFile", "prefetch:${next.fileId}") {
                it.put("file_id", next.fileId)
                it.put("priority", 4)
                it.put("offset", 0)
                it.put("limit", 0)
                it.put("synchronous", false)
            }
        }.onFailure { prefetchWaiting = null }
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
        currentSongIndex = songs.indexOfFirst { it.messageId == song.messageId }
        nowPlayingTitle.text = song.title
        downloadWaiting = song.fileId
        showMessage("Đang tải bài: ${song.title}")
        try {
            send("downloadFile", "download:${song.fileId}") {
                it.put("file_id", song.fileId)
                it.put("priority", 16)
                it.put("offset", 0)
                it.put("limit", 0)
                it.put("synchronous", false)
            }
        } catch (_: Exception) {
            showMessage("Không thể tải bài này.")
        }
    }

    private fun playIfDownloaded(file: JSONObject) {
        if (!auth.isReady() || file.optInt("id") != downloadWaiting) return
        val local = file.optJSONObject("local") ?: return
        if (!local.optBoolean("is_downloading_completed")) return
        val path = local.optString("path")
        val song = songs.firstOrNull { it.fileId == downloadWaiting } ?: return
        val target = File(path)
        // Treat TDLib file path as untrusted until canonical containment passes.
        if (!TelegramAccountStoragePolicy.isOwnedFile(filesRoot, target) ||
            !target.isFile) {
            showMessage("Từ chối file ngoài vùng riêng của phiên Telegram.")
            return
        }
        downloadWaiting = null
        runOnUiThread {
            enqueueTelegramPlayback(song, target)
            status.text = "Đang phát: ${song.title}"
        }
    }

    private fun logoutUser() {
        if (!auth.isReady()) return
        clearPendingQrLink()
        auth.logout()
        globalScanGeneration++
        globalScanLoading = false
        ShareableTdlibOwner.revokeAuthorization()
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
        openDocument.isEnabled = false
        logout.isEnabled = false
        submit.isEnabled = false
        showMessage("Đang đăng xuất; đợi Telegram đóng phiên.")
        runCatching { send("logOut", "logout") }
    }

    override fun onDestroy() {
        clearPendingQrLink()
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
