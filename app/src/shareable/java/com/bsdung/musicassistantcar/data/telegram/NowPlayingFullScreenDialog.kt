package com.bsdung.musicassistantcar.data.telegram

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.widget.FrameLayout
import android.widget.ImageView
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.os.Build
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import java.util.Locale

/** A real full-screen audio player, backed by the existing MediaSession. */
internal class NowPlayingFullScreenDialog(
    private val activity: Activity,
    private val media: () -> MediaController?,
    private val source: () -> String,
    private val previous: () -> Unit,
    private val next: () -> Unit,
    private val isFavorite: () -> Boolean,
    private val toggleFavorite: () -> Unit,
    private val openQueue: () -> Unit
) {
    private val background = Color.rgb(9, 17, 29)
    private val surface = Color.rgb(26, 41, 56)
    private val foreground = Color.rgb(239, 246, 251)
    private val secondary = Color.rgb(156, 177, 196)
    private val mint = Color.rgb(67, 212, 174)
    private val main = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null
    private var title: TextView? = null
    private var category: TextView? = null
    private var formatLabel: TextView? = null
    private var albumImage: ImageView? = null
    private var albumPlaceholder: TextView? = null
    private var coverItemId = ""
    private var coverRequestId = 0L
    private var playButton: Button? = null
    private var shuffleButton: Button? = null
    private var repeatButton: Button? = null
    private var favoriteButton: Button? = null
    private var positionLabel: TextView? = null
    private var durationLabel: TextView? = null
    private var seek: SeekBar? = null
    private var progressUserControlled = false

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density + .5f).toInt()

    private fun bubble(color: Int, r: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(r).toFloat()
            setColor(color)
        }

    private fun button(label: String, backgroundColor: Int, textColor: Int,
                       fontSize: Float = 21f): Button =
        Button(activity).apply {
            text = label
            isAllCaps = false
            textSize = fontSize
            setTextColor(textColor)
            background = bubble(backgroundColor, 20)
            minHeight = dp(64)
        }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(activity).apply {
            this.text = text
            this.textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun formatTime(timeMs: Long): String {
        if (timeMs < 0L) return "00:00"
        val total = timeMs / 1000
        return if (total >= 3600L) {
            String.format(Locale.US, "%d:%02d:%02d",
                total / 3600L, total / 60L % 60L, total % 60L)
        } else String.format(Locale.US, "%02d:%02d", total / 60L, total % 60L)
    }

    private fun audioFormatText(player: MediaController?): String {
        val groups = player?.currentTracks?.groups ?: return "Chưa có thông tin âm thanh"
        for (group in groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (index in 0 until group.length) {
                if (!group.isTrackSelected(index)) continue
                val format = group.getTrackFormat(index)
                val codec = when (format.sampleMimeType) {
                    "audio/mpeg" -> "MP3"
                    "audio/flac" -> "FLAC"
                    "audio/raw" -> "PCM/WAV"
                    "audio/mp4a-latm" -> "AAC"
                    "audio/opus" -> "OPUS"
                    "audio/vorbis" -> "VORBIS"
                    else -> format.sampleMimeType
                        ?.removePrefix("audio/")?.uppercase(Locale.ROOT)
                        ?: "Âm thanh"
                }
                val fields = mutableListOf(codec)
                if (format.sampleRate > 0) {
                    fields.add(String.format(Locale.US,
                        "%.1f kHz", format.sampleRate / 1000.0))
                }
                if (format.channelCount > 0) fields.add("${format.channelCount} kênh")
                if (format.bitrate > 0) fields.add("${format.bitrate / 1000} kbps")
                return "Đầu vào: " + fields.joinToString(" · ")
            }
        }
        return "Đang đọc định dạng âm thanh…"
    }

    private fun decodeArtwork(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty() || bytes.size > 8_000_000) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 900) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun updateAlbumArt(player: MediaController?) {
        val item = player?.currentMediaItem
        val key = item?.mediaId.orEmpty()
        if (key == coverItemId) return
        coverItemId = key
        val requestId = ++coverRequestId
        albumImage?.setImageDrawable(null)
        albumPlaceholder?.visibility = View.VISIBLE
        if (item == null) return
        val embedded = item.mediaMetadata.artworkData
        val uri = item.localConfiguration?.uri
        // Read embedded cover art only from the audio URI already authorized
        // for playback. Never request an unauthenticated remote image URL.
        Thread {
            val picture = embedded ?: runCatching {
                if (uri == null || uri.scheme !in setOf("file", "content")) {
                    null
                } else {
                    MediaMetadataRetriever().let { extractor ->
                        try {
                            extractor.setDataSource(activity, uri)
                            extractor.embeddedPicture
                        } finally {
                            extractor.release()
                        }
                    }
                }
            }.getOrNull()
            val bitmap = runCatching { decodeArtwork(picture) }.getOrNull()
            activity.runOnUiThread {
                if (requestId != coverRequestId || dialog?.isShowing != true) {
                    bitmap?.recycle()
                    return@runOnUiThread
                }
                if (bitmap != null) {
                    albumImage?.setImageBitmap(bitmap)
                    albumPlaceholder?.visibility = View.GONE
                }
            }
        }.apply {
            name = "MeloVault-EmbeddedCover"
            isDaemon = true
            start()
        }
    }

    private val update = object : Runnable {
        override fun run() {
            if (dialog?.isShowing != true) return
            val player = media()
            updateAlbumArt(player)
            title?.text = player?.currentMediaItem?.mediaMetadata?.title
                ?.toString()?.ifBlank { "Chọn bài hát" } ?: "Chọn bài hát"
            category?.text = source()
            formatLabel?.text = audioFormatText(player)
            playButton?.text = if (player?.isPlaying == true) "Ⅱ" else "▶"
            val shuffle = player?.shuffleModeEnabled == true
            shuffleButton?.text = if (shuffle) "⇄ Đảo: Bật" else "⇄ Đảo: Tắt"
            shuffleButton?.setTextColor(if (shuffle) mint else secondary)
            repeatButton?.text = when (player?.repeatMode) {
                Player.REPEAT_MODE_ALL -> "↻ Lặp tất cả"
                Player.REPEAT_MODE_ONE -> "↻ Lặp một"
                else -> "↻ Không lặp"
            }
            repeatButton?.setTextColor(
                if (player?.repeatMode != Player.REPEAT_MODE_OFF) mint else secondary
            )
            favoriteButton?.text = if (isFavorite()) "★ Đã lưu" else "☆ Lưu bài"
            favoriteButton?.setTextColor(if (isFavorite()) mint else secondary)
            val duration = player?.duration ?: C.TIME_UNSET
            val validDuration = duration != C.TIME_UNSET && duration > 0
            val current = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            positionLabel?.text = formatTime(current)
            durationLabel?.text = if (validDuration) formatTime(duration) else "--:--"
            seek?.isEnabled = validDuration
            if (!progressUserControlled) {
                seek?.progress = if (validDuration) {
                    ((current.toDouble() / duration) * 1000)
                        .toInt().coerceIn(0, 1000)
                } else 0
            }
            main.postDelayed(this, 1_000L)
        }
    }

    fun show() {
        if (dialog?.isShowing == true) return
        val popup = Dialog(activity, android.R.style.Theme_Material_NoActionBar)
        dialog = popup
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(this@NowPlayingFullScreenDialog.background)
            setPadding(dp(22), dp(20), dp(22), dp(20))
        }
        val top = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val close = button("⌄", surface, foreground, 27f).apply {
            setOnClickListener { popup.dismiss() }
        }
        top.addView(close, LinearLayout.LayoutParams(dp(62), dp(56)))
        top.addView(label("MELOVAULT  ·  NOW PLAYING", 12f, mint, true),
            LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(top, LinearLayout.LayoutParams(-1, dp(60)))

        // Album-art placeholder. Actual cover art will be used only when
        // provided by the selected account's Media3 item in a future release.
        val art = FrameLayout(activity).apply {
            background = bubble(surface, 26)
            clipToOutline = true
        }
        albumImage = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Ảnh bìa của bài đang phát"
        }
        art.addView(albumImage, FrameLayout.LayoutParams(-1, -1))
        albumPlaceholder = label("♫", 100f, mint)
        art.addView(albumPlaceholder, FrameLayout.LayoutParams(-1, -1))
        root.addView(art, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(20)
            bottomMargin = dp(22)
        })

        title = label("Chọn bài hát", 23f, foreground, true).apply {
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        category = label("", 13f, secondary)
        root.addView(category, LinearLayout.LayoutParams(-1, dp(30)))
        formatLabel = label("Đang đọc định dạng âm thanh…", 12f, secondary)
        root.addView(formatLabel, LinearLayout.LayoutParams(-1, dp(28)))

        seek = SeekBar(activity).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(mint)
            thumbTintList = android.content.res.ColorStateList.valueOf(mint)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) {
                    progressUserControlled = true
                }
                override fun onProgressChanged(bar: SeekBar, progress: Int,
                                               fromUser: Boolean) {
                    if (fromUser) {
                        val length = media()?.duration ?: C.TIME_UNSET
                        if (length > 0 && length != C.TIME_UNSET) {
                            positionLabel?.text = formatTime(length * progress / 1000L)
                        }
                    }
                }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    val player = media()
                    val length = player?.duration ?: C.TIME_UNSET
                    if (length > 0 && length != C.TIME_UNSET) {
                        player?.seekTo(length * bar.progress / 1000L)
                    }
                    progressUserControlled = false
                }
            })
        }
        root.addView(seek, LinearLayout.LayoutParams(-1, dp(45)))
        val times = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        positionLabel = label("00:00", 13f, secondary)
        durationLabel = label("--:--", 13f, secondary)
        times.addView(positionLabel)
        times.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
        times.addView(durationLabel)
        root.addView(times, LinearLayout.LayoutParams(-1, dp(25)))

        val controls = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        val back = button("⏮", surface, foreground, 27f).apply {
            setOnClickListener { previous() }
        }
        playButton = button("▶", mint, background, 32f).apply {
            setOnClickListener {
                media()?.let { player ->
                    if (player.currentMediaItem != null) {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                }
            }
        }
        val forward = button("⏭", surface, foreground, 27f).apply {
            setOnClickListener { next() }
        }
        for (b in listOf(back, playButton!!, forward)) {
            controls.addView(b, LinearLayout.LayoutParams(0, dp(72), 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            })
        }
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(80)).apply {
            topMargin = dp(8)
            bottomMargin = dp(9)
        })
        val modes = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        shuffleButton = button("⇄ Đảo: Tắt", surface, secondary, 12f).apply {
            setOnClickListener {
                media()?.let { player ->
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                }
            }
        }
        repeatButton = button("↻ Không lặp", surface, secondary, 12f).apply {
            setOnClickListener {
                media()?.let { player ->
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            }
        }
        for (b in listOf(shuffleButton!!, repeatButton!!)) {
            modes.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            })
        }
        root.addView(modes, LinearLayout.LayoutParams(-1, dp(52)))
        val extras = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        favoriteButton = button("☆ Lưu bài", surface, secondary, 13f).apply {
            setOnClickListener { toggleFavorite() }
        }
        val queue = button("☷ Hàng đợi", surface, foreground, 13f).apply {
            setOnClickListener { openQueue() }
        }
        for (b in listOf(favoriteButton!!, queue)) {
            extras.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            })
        }
        root.addView(extras, LinearLayout.LayoutParams(-1, dp(55)))
        root.addView(label("Ngẫu nhiên: bài đã tải • Phát nền Media3", 11f, secondary),
            LinearLayout.LayoutParams(-1, dp(30)))

        popup.setContentView(root)
        popup.setOnDismissListener {
            main.removeCallbacks(update)
            dialog = null
            seek = null
            title = null
            category = null
            formatLabel = null
            coverRequestId++
            coverItemId = ""
            albumImage?.setImageDrawable(null)
            albumImage = null
            albumPlaceholder = null
            playButton = null
            shuffleButton = null
            repeatButton = null
            favoriteButton = null
        }
        popup.show()
        popup.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            navigationBarColor = background
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
                insetsController?.apply {
                    systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsets.Type.systemBars())
                }
            }
        }
        main.post(update)
    }

    fun dismiss() { dialog?.dismiss() }
}
