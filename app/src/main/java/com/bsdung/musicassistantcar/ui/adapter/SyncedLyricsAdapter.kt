package com.bsdung.musicassistantcar.ui.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.databinding.ItemLyricsLineBinding
import com.bsdung.musicassistantcar.lyrics.LyricsLine
import java.util.Locale

class SyncedLyricsAdapter(
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
        if (previous in items.indices) {
            notifyItemChanged(previous)
        }
        if (newIndex in items.indices) {
            notifyItemChanged(newIndex)
        }
        return true
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
            val isActive = index == currentActiveIndex
            val isPast = currentActiveIndex >= 0 && index < currentActiveIndex

            binding.lyricsLineTimestampView.text = formatTimestamp(line.startMs)
            binding.lyricsLineTextView.text = line.text
            binding.lyricsLineRoot.isSelected = isActive

            if (isActive) {
                binding.lyricsLineRoot.setBackgroundResource(R.drawable.bg_button_tertiary_active)
                binding.lyricsLineTextView.setTextColor(ContextCompat.getColor(ctx, R.color.accent_primary))
                binding.lyricsLineTextView.textSize = 18f
                binding.lyricsLineTextView.setTypeface(null, Typeface.BOLD)
                binding.lyricsLineTextView.alpha = 1.0f
                binding.lyricsLineTimestampView.setTextColor(ContextCompat.getColor(ctx, R.color.accent_primary))
                binding.lyricsLineTimestampView.alpha = 1.0f
            } else {
                binding.lyricsLineRoot.setBackgroundResource(R.drawable.bg_button_tertiary)
                val textColor = if (isPast) R.color.text_tertiary else R.color.text_secondary
                binding.lyricsLineTextView.setTextColor(ContextCompat.getColor(ctx, textColor))
                binding.lyricsLineTextView.textSize = 15.5f
                binding.lyricsLineTextView.setTypeface(null, Typeface.NORMAL)
                binding.lyricsLineTextView.alpha = if (isPast) 0.65f else 0.92f
                binding.lyricsLineTimestampView.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary))
                binding.lyricsLineTimestampView.alpha = if (isPast) 0.55f else 0.80f
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
