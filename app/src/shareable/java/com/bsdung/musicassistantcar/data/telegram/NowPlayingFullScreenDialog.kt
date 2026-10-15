package com.bsdung.musicassistantcar.data.telegram

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
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
    private val next: () -> Unit
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
    private var playButton: Button? = null
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

    private val update = object : Runnable {
        override fun run() {
            if (dialog?.isShowing != true) return
            val player = media()
            title?.text = player?.currentMediaItem?.mediaMetadata?.title
                ?.toString()?.ifBlank { "Chọn bài hát" } ?: "Chọn bài hát"
            category?.text = source()
            playButton?.text = if (player?.isPlaying == true) "Ⅱ" else "▶"
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
        val art = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            background = bubble(surface, 26)
            addView(label("♫", 100f, mint))
        }
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
        root.addView(category, LinearLayout.LayoutParams(-1, dp(42)))

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
            bottomMargin = dp(20)
        })
        root.addView(label("Nhạc Telegram  •  Phát nền qua Media3", 12f, secondary),
            LinearLayout.LayoutParams(-1, dp(30)))

        popup.setContentView(root)
        popup.setOnDismissListener {
            main.removeCallbacks(update)
            dialog = null
            seek = null
            title = null
            category = null
            playButton = null
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
