package com.bsdung.musicassistantcar.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bsdung.musicassistantcar.data.YouTubePlaylistDto
import com.bsdung.musicassistantcar.databinding.ItemYoutubePlaylistBinding

class YouTubePlaylistsAdapter(
    private val playlists: List<YouTubePlaylistDto>,
    private val onPlaylistClick: (YouTubePlaylistDto) -> Unit,
) : RecyclerView.Adapter<YouTubePlaylistsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemYoutubePlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(playlists[position])
    }

    override fun getItemCount(): Int = playlists.size

    inner class ViewHolder(
        private val binding: ItemYoutubePlaylistBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: YouTubePlaylistDto) {
            binding.txtYtPlaylistTitle.text = playlist.title

            val privacyLabel = when (playlist.privacyStatus?.lowercase()) {
                "public" -> "Công khai"
                "private" -> "Riêng tư"
                "unlisted" -> "Không công khai"
                else -> playlist.privacyStatus ?: ""
            }

            val countLabel = "${playlist.itemCount} bài"
            val subtitleParts = listOf(countLabel, privacyLabel).filter { it.isNotBlank() }
            binding.txtYtPlaylistSubtitle.text = subtitleParts.joinToString(" · ")

            binding.root.setOnClickListener {
                onPlaylistClick(playlist)
            }
        }
    }
}
