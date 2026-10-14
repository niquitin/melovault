package com.bsdung.musicassistantcar.data.telegram

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
 * Internal preview, intentionally limited to audio in the user's own
 * Telegram Saved Messages plus user-selected SAF documents (including Drive
 * when the device provides it). No direct Drive OAuth or private backend.
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
    private lateinit var input: EditText
    private lateinit var submit: Button
    private lateinit var qrLogin: Button
    private lateinit var refresh: Button
    private lateinit var openDocument: Button
    private lateinit var logout: Button
    private lateinit var tracks: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var player: ExoPlayer
    private val songs = mutableListOf<Song>()
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
    private var downloadWaiting: Int? = null
    private lateinit var filesRoot: File
    private var currentTask = ""
    private var mutedMessages = false

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
        player.stop()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
        showMessage("Đang mở tệp: $fileName")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_MusicAssistantCar)
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Do not surface provider URLs, filenames or exception details.
                showMessage("Không phát được tệp âm thanh (Media3 mã ${error.errorCode}).")
            }
        })
        buildUi()
        if (BuildConfig.TELEGRAM_APP_API_ID <= 0 ||
            BuildConfig.TELEGRAM_APP_API_HASH.length != 32) {
            showMessage("Chưa cấu hình Telegram API cho bản thử nội bộ.")
            return
        }
        initNative()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 26, 28, 18)
        }
        TextView(this).apply {
            text = "MeloVault • Telegram Preview"
            textSize = 21f
            setTextColor(0xff132943.toInt())
            root.addView(this)
        }
        note = TextView(this).apply {
            text = "Thử nghiệm: Saved Messages Telegram và tệp âm thanh do người dùng chọn qua Android (có thể từ Google Drive). Chưa có Google Drive OAuth, playlist hoặc phát nền."
            textSize = 13f
            root.addView(this)
        }
        status = TextView(this).apply {
            text = "Đang khởi tạo TDLib…"
            textSize = 15f
            setPadding(0, 18, 0, 12)
            root.addView(this)
        }
        input = EditText(this).apply {
            hint = "Số điện thoại Telegram (+84...)"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_PHONE
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            root.addView(this)
        }
        submit = Button(this).apply {
            text = "Tiếp tục"
            setOnClickListener { submitAuthentication() }
            root.addView(this)
        }
        qrLogin = Button(this).apply {
            text = "Đăng nhập bằng QR (quét trên máy khác)"
            isEnabled = false
            setOnClickListener { requestQrLogin() }
            root.addView(this)
        }
        refresh = Button(this).apply {
            text = "Tải danh sách nhạc Saved Messages"
            isEnabled = false
            setOnClickListener { loadSavedMessages() }
            root.addView(this)
        }
        openDocument = Button(this).apply {
            text = "Chọn nhạc từ Google Drive / thiết bị"
            isEnabled = false
            setOnClickListener { openAudioDocument() }
            root.addView(this)
        }
        logout = Button(this).apply {
            text = "Đăng xuất Telegram"
            isEnabled = false
            setOnClickListener { logoutUser() }
            root.addView(this)
        }
        tracks = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        tracks.adapter = adapter
        tracks.onItemClickListener = android.widget.AdapterView.OnItemClickListener {
                _, _, position, _ ->
            songs.getOrNull(position)?.let { downloadAndPlay(it) }
        }
        root.addView(tracks, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
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
            nativeClientId = TdlibNativeJsonGateway.newClientId()
            running = true
            Thread({
                while (running) {
                    try {
                        val event = TdlibNativeJsonGateway.receiveOne(0.4) ?: continue
                        val obj = JSONObject(event)
                        if (obj.optInt("@client_id") != nativeClientId) continue
                        onNativeEvent(obj, sessionDb, encryptedKey)
                    } catch (e: Exception) {
                        if (running) {
                            running = false
                            showMessage("Lỗi kết nối TDLib: " +
                                (e.message ?: e.javaClass.simpleName).take(150))
                        }
                    }
                }
            }, "MeloVault-TDLib-Preview").start()
            send("getAuthorizationState", "state")
        } catch (e: Exception) {
            showMessage("Không thể khởi tạo Telegram: " +
                (e.message ?: e.javaClass.simpleName).take(130))
        }
    }

    private fun send(type: String, tag: String? = null, configure: ((JSONObject) -> Unit)? = null) {
        val obj = JSONObject().put("@type", type)
        if (tag != null) obj.put("@extra", tag)
        configure?.invoke(obj)
        TdlibNativeJsonGateway.send(nativeClientId, obj.toString())
    }

    private fun onNativeEvent(result: JSONObject, sessionDb: File, secret: String) {
        val kind = result.optString("@type")
        val tag = result.optString("@extra")
        if (kind == "error") {
            if (tag == "savedHistory") {
                historyLoading = false
                runOnUiThread { refresh.isEnabled = true }
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
                    showMessage("Đã đăng nhập Telegram. Tải nhạc trong Saved Messages.")
                    runOnUiThread {
                        refresh.isEnabled = true
                        openDocument.isEnabled = true
                        logout.isEnabled = true
                        submit.isEnabled = false
                        input.visibility = View.GONE
                    }
                    loadSavedMessages()
                }
            } catch (_: IllegalArgumentException) {
                showMessage("Không xác minh được tài khoản Telegram.")
            }
            return
        }
        if (tag == "savedChat" && kind == "chat") {
            savedChatId = result.optLong("id")
            requestHistory()
            return
        }
        if (tag == "savedHistory" && kind == "messages") {
            val items = result.optJSONArray("messages") ?: org.json.JSONArray()
            val extracted = mutableListOf<Song>()
            for (i in 0 until items.length()) {
                val msg = items.optJSONObject(i) ?: continue
                val content = msg.optJSONObject("content") ?: continue
                val mediaType = content.optString("@type")
                val attachment = when (mediaType) {
                    "messageAudio" -> content.optJSONObject("audio")
                    "messageDocument" -> content.optJSONObject("document")
                    else -> null
                } ?: continue
                val file = attachment.optJSONObject(
                    if (mediaType == "messageAudio") "audio" else "document"
                ) ?: continue
                val name = attachment.optString("file_name", "bai-hat")
                val title = attachment.optString("title").ifBlank { name }
                val looksAudio = (mediaType == "messageAudio") ||
                    name.lowercase().endsWith(".flac") ||
                    name.lowercase().endsWith(".mp3") ||
                    name.lowercase().endsWith(".m4a") ||
                    name.lowercase().endsWith(".wav")
                val messageId = msg.optLong("id")
                if (looksAudio && messageId > 0L && file.optInt("id") > 0 &&
                    seenSongMessageIds.add(messageId)) {
                    extracted.add(Song(file.optInt("id"), title, name, messageId))
                }
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
                    adapter.clear()
                }
                songs.addAll(extracted)
                adapter.addAll(extracted.map { "${it.title} • ${it.filename}" })
                adapter.notifyDataSetChanged()
                if (hasMore) {
                    status.text = "Đang đọc lịch sử Telegram: ${songs.size} bài / ${pageCount} lượt."
                } else {
                    status.text = "Đã tìm thấy ${songs.size} bài nhạc trong ${pageCount} lượt lịch sử."
                    refresh.isEnabled = true
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
        if (kind == "updateFile") {
            result.optJSONObject("file")?.let { playIfDownloaded(it) }
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
                auth.logout()
                clearPendingQrLink()
                runOnUiThread {
                    player.stop()
                    tracks.adapter = adapter
                    songs.clear()
                    adapter.clear()
                    refresh.isEnabled = false
                    openDocument.isEnabled = false
                    logout.isEnabled = false
                    submit.isEnabled = false
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

    private fun loadSavedMessages() {
        if (!auth.isReady()) return
        showMessage("Đang đọc nhạc từ Saved Messages…")
        val userId = library.authorizedUser()?.value ?: return
        if (savedChatId != 0L) requestHistory()
        else send("createPrivateChat", "savedChat") {
            it.put("user_id", userId)
            it.put("force", true)
        }
    }

    private fun requestHistory() {
        if (savedChatId == 0L || !auth.isReady() || historyLoading) return
        historyCursorId = 0L
        historyPages = 0
        seenSongMessageIds.clear()
        historyLoading = true
        runOnUiThread { refresh.isEnabled = false }
        requestHistoryPage()
    }

    private fun requestHistoryPage() {
        send("getChatHistory", "savedHistory") {
            it.put("chat_id", savedChatId)
            it.put("from_message_id", historyCursorId)
            it.put("offset", 0)
            it.put("limit", 60)
            it.put("only_local", false)
        }
    }

    private fun downloadAndPlay(song: Song) {
        if (!auth.isReady()) return
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
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(target)))
            player.prepare()
            player.play()
            status.text = "Đang phát: ${song.title}"
        }
    }

    private fun logoutUser() {
        if (!auth.isReady()) return
        clearPendingQrLink()
        auth.logout()
        player.stop()
        songs.clear()
        adapter.clear()
        downloadWaiting = null
        refresh.isEnabled = false
        openDocument.isEnabled = false
        logout.isEnabled = false
        submit.isEnabled = false
        showMessage("Đang đăng xuất; đợi Telegram đóng phiên.")
        runCatching { send("logOut", "logout") }
    }

    override fun onDestroy() {
        clearPendingQrLink()
        running = false
        runCatching {
            if (nativeClientId > 0) TdlibNativeJsonGateway.requestClose(nativeClientId)
        }
        player.release()
        super.onDestroy()
    }
}
