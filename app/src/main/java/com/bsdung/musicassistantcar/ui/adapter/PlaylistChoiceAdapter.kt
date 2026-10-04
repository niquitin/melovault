package com.bsdung.musicassistantcar.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bsdung.musicassistantcar.data.CarPlaylist
import com.bsdung.musicassistantcar.databinding.ItemPlaylistChoiceBinding
import com.bsdung.musicassistantcar.ui.util.MetadataDisplayHelper

class PlaylistChoiceAdapter(
    private val playlists: List<CarPlaylist>,
    private val onPlaylistSelected: (CarPlaylist) -> Unit,
) : RecyclerView.Adapter<PlaylistChoiceAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistChoiceBinding.inflate(
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
        private val binding: ItemPlaylistChoiceBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: CarPlaylist) {
            binding.playlistChoiceName.text = MetadataDisplayHelper.cleanPlaylistName(playlist.name)
            binding.playlistChoiceTrackCount.text = "${playlist.trackCount} bài"
            binding.root.setOnClickListener {
                onPlaylistSelected(playlist)
            }
        }
    }
}
