package com.bsdung.musicassistantcar.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bsdung.musicassistantcar.data.YouTubePlaylistItemDto
import com.bsdung.musicassistantcar.databinding.ItemYoutubePlaylistItemBinding

class YouTubePlaylistItemsAdapter(
    private val items: List<YouTubePlaylistItemDto>,
    private val onOpenVideo: (YouTubePlaylistItemDto) -> Unit,
) : RecyclerView.Adapter<YouTubePlaylistItemsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemYoutubePlaylistItemBinding.inflate(
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
        private val binding: ItemYoutubePlaylistItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: YouTubePlaylistItemDto) {
            binding.txtYtItemPosition.text = "${item.position + 1}."

            binding.txtYtItemTitle.text = if (item.isAvailable) {
                item.title
            } else {
                "${item.title} (Không khả dụng)"
            }

            binding.txtYtItemChannel.text = item.channelTitle ?: ""

            val canOpen = item.isAvailable && !item.videoId.isNullOrBlank()
            binding.btnYtOpenVideo.isEnabled = canOpen
            binding.btnYtOpenVideo.alpha = if (canOpen) 1.0f else 0.5f

            binding.btnYtOpenVideo.setOnClickListener {
                if (canOpen) {
                    onOpenVideo(item)
                }
            }
        }
    }
}
