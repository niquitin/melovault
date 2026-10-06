package com.bsdung.musicassistantcar.ui.adapter

import android.content.res.Configuration
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.databinding.ItemLyricsLineBinding
import com.bsdung.musicassistantcar.lyrics.LyricsLine
import java.util.Locale
import kotlin.math.abs

class SyncedLyricsAdapter(
    private val fullscreenMode: Boolean = false,
    private val onLineClick: (LyricsLine) -> Unit,
) : RecyclerView.Adapter<SyncedLyricsAdapter.LineViewHolder>() {

    private val items = ArrayList<LyricsLine>()
    var activeLineIndex: Int = -1
        private set

    fun submitLines(newLines: List<LyricsLine>, initialActiveIndex: Int = -1) {
        items.clear()
        items.addAll(newLines)
        activeLineIndex = initialActiveIndex
        notifyDataSetChanged()
    }

    fun updateActiveLineIndex(newIndex: Int): Boolean {
        if (newIndex == activeLineIndex) return false
        val previous = activeLineIndex
        activeLineIndex = newIndex
        notifyWindowAround(previous)
        notifyWindowAround(newIndex)
        return true
    }

    private fun notifyWindowAround(centerIndex: Int) {
        if (centerIndex !in items.indices) return
        val radius = if (fullscreenMode) 2 else 0
        val start = (centerIndex - radius).coerceAtLeast(0)
        val end = (centerIndex + radius).coerceAtMost(items.lastIndex)
        for (i in start..end) {
            notifyItemChanged(i)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineViewHolder {
        val binding = ItemLyricsLineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return LineViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: LineViewHolder, position: Int) {
        holder.bind(items[position], position, activeLineIndex)
    }

    inner class LineViewHolder(
        private val binding: ItemLyricsLineBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(line: LyricsLine, index: Int, currentActiveIndex: Int) {
            val ctx = binding.root.context
            val density = ctx.resources.displayMetrics.density
            val cfg = ctx.resources.configuration
            val isWideOrLandscape = cfg.screenWidthDp >= 600 ||
                cfg.orientation == Configuration.ORIENTATION_LANDSCAPE

            val isActive = index == currentActiveIndex
            val isNearby = currentActiveIndex >= 0 && abs(index - currentActiveIndex) in 1..2
            val isPast = currentActiveIndex >= 0 && index < currentActiveIndex

            binding.lyricsLineTimestampView.text = formatTimestamp(line.startMs)
            binding.lyricsLineTextView.text = line.text
            binding.lyricsLineRoot.isSelected = isActive

            if (fullscreenMode) {
                val hPad = (18 * density).toInt()
                val vPad = ((if (isActive) 16 else 13) * density).toInt()
                binding.lyricsLineRoot.minimumHeight = (62 * density).toInt()
                binding.lyricsLineRoot.setPadding(hPad, vPad, hPad, vPad)
                binding.lyricsLineTimestampView.textSize = if (isWideOrLandscape) 13.5f else 12f
                binding.lyricsLineTextView.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL

                when {
                    isActive -> {
                        binding.lyricsLineRoot.setBackgroundResource(R.drawable.bg_quality_chip_active)
                        binding.lyricsLineTextView.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                        binding.lyricsLineTextView.textSize = if (isWideOrLandscape) 31f else 25f
                        binding.lyricsLineTextView.setTypeface(null, Typeface.BOLD)
                        binding.lyricsLineTextView.alpha = 1.0f
                        binding.lyricsLineTimestampView.setTextColor(ContextCompat.getColor(ctx, R.color.accent_primary))
                        binding.lyricsLineTimestampView.setTypeface(null, Typeface.BOLD)
                        binding.lyricsLineTimestampView.alpha = 1.0f
                    }
                    isNearby -> {
                        binding.lyricsLineRoot.setBackgroundResource(R.drawable.bg_track_normal)
                        binding.lyricsLineTextView.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                        binding.lyricsLineTextView.textSize = if (isWideOrLandscape) 22.5f else 19.5f
                        binding.lyricsLineTextView.setTypeface(null, Typeface.NORMAL)
                        binding.lyricsLineTextView.alpha = if (isPast) 0.78f else 0.94f
                        binding.lyricsLineTimestampView.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary))
                        binding.lyricsLineTimestampView.setTypeface(null, Typeface.NORMAL)
                        binding.lyricsLineTimestampView.alpha = 0.75f
                    }
                    else -> {
                        binding.lyricsLineRoot.setBackgroundResource(R.drawable.bg_track_normal)
                        val textColor = if (isPast) R.color.text_tertiary else R.color.text_secondary
                        binding.lyricsLineTextView.setTextColor(ContextCompat.getColor(ctx, textColor))
                        binding.lyricsLineTextView.textSize = if (isWideOrLandscape) 20f else 18f
                        binding.lyricsLineTextView.setTypeface(null, Typeface.NORMAL)
                        binding.lyricsLineTextView.alpha = if (isPast) 0.56f else 0.80f
                        binding.lyricsLineTimestampView.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary))
                        binding.lyricsLineTimestampView.setTypeface(null, Typeface.NORMAL)
                        binding.lyricsLineTimestampView.alpha = if (isPast) 0.50f else 0.68f
                    }
                }
            } else {
                val hPad = (12 * density).toInt()
                val vPad = (10 * density).toInt()
                binding.lyricsLineRoot.minimumHeight = (48 * density).toInt()
                binding.lyricsLineRoot.setPadding(hPad, vPad, hPad, vPad)
                binding.lyricsLineTimestampView.textSize = 11f
                binding.lyricsLineTextView.gravity = Gravity.START or Gravity.CENTER_VERTICAL

                if (isActive) {
                    binding.lyricsLineRoot.setBackgroundResource(R.drawable.bg_quality_chip_active)
                    binding.lyricsLineTextView.setTextColor(ContextCompat.getColor(ctx, R.color.accent_primary))
                    binding.lyricsLineTextView.textSize = 18f
                    binding.lyricsLineTextView.setTypeface(null, Typeface.BOLD)
                    binding.lyricsLineTextView.alpha = 1.0f
                    binding.lyricsLineTimestampView.setTextColor(ContextCompat.getColor(ctx, R.color.accent_primary))
                    binding.lyricsLineTimestampView.setTypeface(null, Typeface.BOLD)
                    binding.lyricsLineTimestampView.alpha = 1.0f
                } else {
                    binding.lyricsLineRoot.setBackgroundResource(R.drawable.bg_track_normal)
                    val textColor = if (isPast) R.color.text_tertiary else R.color.text_secondary
                    binding.lyricsLineTextView.setTextColor(ContextCompat.getColor(ctx, textColor))
                    binding.lyricsLineTextView.textSize = 15.5f
                    binding.lyricsLineTextView.setTypeface(null, Typeface.NORMAL)
                    binding.lyricsLineTextView.alpha = if (isPast) 0.65f else 0.92f
                    binding.lyricsLineTimestampView.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary))
                    binding.lyricsLineTimestampView.setTypeface(null, Typeface.NORMAL)
                    binding.lyricsLineTimestampView.alpha = if (isPast) 0.55f else 0.80f
                }
            }

            binding.lyricsLineRoot.setOnClickListener {
                onLineClick(line)
            }
        }
    }

    companion object {
        fun formatTimestamp(ms: Long): String {
            val totalSec = (ms.coerceAtLeast(0L) / 1000L)
            val min = totalSec / 60L
            val sec = totalSec % 60L
            return String.format(Locale.US, "%02d:%02d", min, sec)
        }
    }
}

