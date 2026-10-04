package com.bsdung.musicassistantcar.ui.controller

import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.Player

class ProgressController(
    private val seekBar: SeekBar,
    private val currentTimeView: TextView,
    private val totalTimeView: TextView,
    private val onSeekTo: (Long) -> Unit,
) {
    companion object {
        fun formatTimeMs(millis: Long): String {
            if (millis <= 0L) return "00:00"
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false
    private var attachedPlayer: Player? = null

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            if (attachedPlayer?.isPlaying == true) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    init {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = attachedPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                    if (duration > 0L) {
                        val seekTime = (duration * progress) / sb!!.max
                        currentTimeView.text = formatTimeMs(seekTime)
                    }
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                isUserSeeking = false
                val player = attachedPlayer ?: return
                val duration = player.duration.coerceAtLeast(0L)
                if (duration > 0L && sb != null) {
                    val targetMs = (duration * sb.progress) / sb.max
                    onSeekTo(targetMs)
                }
            }
        })
    }

    fun attachPlayer(player: Player?) {
        attachedPlayer = player
        syncWithPlayer()
    }

    fun detach() {
        stopUpdates()
        attachedPlayer = null
    }

    fun syncWithPlayer() {
        updateProgress()
        if (attachedPlayer?.isPlaying == true) {
            startUpdates()
        } else {
            stopUpdates()
        }
    }

    fun startUpdates() {
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
    }

    fun stopUpdates() {
        handler.removeCallbacks(updateRunnable)
    }

    private fun updateProgress() {
        if (isUserSeeking) return
        val player = attachedPlayer
        if (player == null || player.duration <= 0L) {
            seekBar.progress = 0
            currentTimeView.text = "00:00"
            totalTimeView.text = "00:00"
            return
        }

        val current = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.coerceAtLeast(1L)

        val progressPercent = ((current.toDouble() / duration.toDouble()) * seekBar.max).toInt()
        seekBar.progress = progressPercent.coerceIn(0, seekBar.max)
        currentTimeView.text = formatTimeMs(current)
        totalTimeView.text = formatTimeMs(duration)
    }
}
