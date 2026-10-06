package com.bsdung.musicassistantcar.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.databinding.ItemLyricsCandidateBinding
import com.bsdung.musicassistantcar.lyrics.LyricsCandidate
import java.util.Locale

class LyricsCandidateAdapter(
    private val onCandidateClick: (LyricsCandidate) -> Unit,
) : RecyclerView.Adapter<LyricsCandidateAdapter.CandidateViewHolder>() {

    private val items = ArrayList<LyricsCandidate>()

    fun submitCandidates(candidates: List<LyricsCandidate>) {
        items.clear()
        items.addAll(candidates.take(5))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
        val binding = ItemLyricsCandidateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return CandidateViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class CandidateViewHolder(
        private val binding: ItemLyricsCandidateBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(candidate: LyricsCandidate) {
            val ctx = binding.root.context
            binding.candidateTitleView.text = candidate.title.ifBlank { "Không rõ tên bài" }

            val parts = ArrayList<String>(3)
            if (candidate.artist.isNotBlank()) parts.add(candidate.artist)
            if (candidate.album.isNotBlank()) parts.add(candidate.album)
            if (candidate.durationMs != null && candidate.durationMs > 0L) {
                val totalSec = candidate.durationMs / 1000L
                val min = totalSec / 60L
                val sec = totalSec % 60L
                parts.add(String.format(Locale.US, "%d:%02d", min, sec))
            }
            binding.candidateSubtitleView.text = parts.joinToString(" • ")

            if (candidate.hasSynced) {
                binding.candidateBadgeView.text = "Đồng bộ"
                binding.candidateBadgeView.setBackgroundResource(R.drawable.bg_quality_chip_original)
                binding.candidateBadgeView.setTextColor(ContextCompat.getColor(ctx, R.color.quality_original_text))
            } else {
                binding.candidateBadgeView.text = "Lời thường"
                binding.candidateBadgeView.setBackgroundResource(R.drawable.bg_quality_chip_mobile)
                binding.candidateBadgeView.setTextColor(ContextCompat.getColor(ctx, R.color.quality_mobile_text))
            }

            binding.lyricsCandidateRoot.setOnClickListener {
                onCandidateClick(candidate)
            }
        }
    }
}
