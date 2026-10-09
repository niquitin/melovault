package com.bsdung.musicassistantcar.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bsdung.musicassistantcar.data.UnifiedTrackDto
import com.bsdung.musicassistantcar.databinding.ItemUnifiedTrackBinding
import com.bsdung.musicassistantcar.ui.util.MetadataDisplayHelper
import com.bsdung.musicassistantcar.ui.util.UnifiedActionHelper
import com.bsdung.musicassistantcar.ui.util.UnifiedItemAction

class UnifiedTracksAdapter(
    private val onItemClick: (UnifiedTrackDto, UnifiedItemAction) -> Unit,
) : RecyclerView.Adapter<UnifiedTracksAdapter.ViewHolder>() {

    private val items = mutableListOf<UnifiedTrackDto>()

    fun submitList(newItems: List<UnifiedTrackDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUnifiedTrackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemUnifiedTrackBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UnifiedTrackDto) {
            val title = MetadataDisplayHelper.cleanTitle(item.title)
            val artist = MetadataDisplayHelper.cleanArtist(item.artist)
            val album = MetadataDisplayHelper.cleanAlbum(item.album)

            binding.txtUnifiedTrackTitle.text = title

            val subtitleParts = mutableListOf<String>()
            if (artist.isNotBlank()) subtitleParts.add(artist)
            if (album.isNotBlank()) subtitleParts.add(album)
            binding.txtUnifiedTrackSubtitle.text = if (subtitleParts.isNotEmpty()) {
                subtitleParts.joinToString(" • ")
            } else {
                "Chưa có nghệ sĩ"
            }

            val badge = UnifiedActionHelper.mapSourceLabel(item.primarySource, item.sourceBadge)
            binding.txtUnifiedSourceBadge.text = badge

            val action = UnifiedActionHelper.resolveAction(item)
            val actionText = UnifiedActionHelper.getActionButtonText(action)
            binding.btnUnifiedAction.text = actionText

            when (action) {
                UnifiedItemAction.READ_ONLY -> {
                    binding.btnUnifiedAction.isEnabled = false
                    binding.btnUnifiedAction.alpha = 0.5f
                }
                UnifiedItemAction.PLAY_IN_LIBRARY,
                UnifiedItemAction.OPEN_EXTERNAL -> {
                    binding.btnUnifiedAction.isEnabled = true
                    binding.btnUnifiedAction.alpha = 1.0f
                }
            }

            binding.btnUnifiedAction.setOnClickListener {
                if (action != UnifiedItemAction.READ_ONLY) {
                    onItemClick(item, action)
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item, action)
            }
        }
    }
}
