package com.bsdung.musicassistantcar.ui.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.databinding.ItemQueueEmptyBinding
import com.bsdung.musicassistantcar.databinding.ItemQueueHeaderBinding
import com.bsdung.musicassistantcar.databinding.ItemQueueTrackBinding
import com.bsdung.musicassistantcar.playback.QueueListItem
import com.bsdung.musicassistantcar.ui.util.ArtworkLoader

class QueueAdapter(
    private val onTrackClick: (QueueListItem.Track) -> Unit,
    private val onRemoveClick: (QueueListItem.Track) -> Unit,
    private val onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 1
        private const val TYPE_TRACK = 2
        private const val TYPE_EMPTY = 3
    }

    private val items = mutableListOf<QueueListItem>()

    fun submitItems(newItems: List<QueueListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): QueueListItem? = items.getOrNull(position)

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition in items.indices && toPosition in items.indices) {
            val item = items.removeAt(fromPosition)
            items.add(toPosition, item)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    fun getFirstUpcomingPosition(): Int =
        items.indexOfFirst { it is QueueListItem.Track && it.isUpcoming }

    fun getUpcomingCount(): Int =
        items.count { it is QueueListItem.Track && it.isUpcoming }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is QueueListItem.Header -> TYPE_HEADER
            is QueueListItem.Track -> TYPE_TRACK
            is QueueListItem.Empty -> TYPE_EMPTY
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemQueueHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            TYPE_EMPTY -> {
                val binding = ItemQueueEmptyBinding.inflate(inflater, parent, false)
                EmptyViewHolder(binding)
            }
            else -> {
                val binding = ItemQueueTrackBinding.inflate(inflater, parent, false)
                TrackViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is QueueListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is QueueListItem.Empty -> (holder as EmptyViewHolder).bind(item)
            is QueueListItem.Track -> (holder as TrackViewHolder).bind(item)
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemQueueHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: QueueListItem.Header) {
            binding.queueHeaderTitle.text = item.title
        }
    }

    inner class EmptyViewHolder(
        private val binding: ItemQueueEmptyBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: QueueListItem.Empty) {
            binding.queueEmptyMessage.text = item.message
        }
    }

    inner class TrackViewHolder(
        private val binding: ItemQueueTrackBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: QueueListItem.Track) {
            val context = binding.root.context
            binding.trackTitle.text = item.title

            val subtitleText = when {
                item.artist.isNotBlank() && item.album.isNotBlank() -> "${item.artist} · ${item.album}"
                item.artist.isNotBlank() -> item.artist
                item.album.isNotBlank() -> item.album
                else -> ""
            }
            if (subtitleText.isNotBlank()) {
                binding.trackSubtitle.text = subtitleText
                binding.trackSubtitle.visibility = View.VISIBLE
            } else {
                binding.trackSubtitle.text = ""
                binding.trackSubtitle.visibility = View.GONE
            }

            if (!item.audioInfo.isNullOrBlank()) {
                binding.trackAudioInfo.text = item.audioInfo
                binding.trackAudioInfo.visibility = View.VISIBLE
            } else {
                binding.trackAudioInfo.text = ""
                binding.trackAudioInfo.visibility = View.GONE
            }

            ArtworkLoader.loadArtwork(
                imageView = binding.trackThumbnail,
                uri = item.artworkUri,
                cornerRadiusDp = 10f,
                placeholderResId = R.drawable.ic_music_note,
            )

            if (item.isCurrent) {
                binding.activeIndicator.visibility = View.VISIBLE
                binding.trackRoot.setBackgroundResource(R.drawable.bg_track_active)
                binding.trackTitle.setTextColor(ContextCompat.getColor(context, R.color.accent_primary))
                binding.nowPlayingIndicator.visibility = View.VISIBLE
                binding.nowPlayingIndicator.setImageResource(
                    if (item.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                )
                binding.dragHandle.visibility = View.GONE
                binding.removeButton.visibility = View.GONE
            } else {
                binding.activeIndicator.visibility = View.GONE
                binding.trackRoot.setBackgroundResource(R.drawable.bg_track_normal)
                binding.trackTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                binding.nowPlayingIndicator.visibility = View.GONE
                binding.dragHandle.visibility = View.VISIBLE
                binding.dragHandle.setOnTouchListener { _, event ->
                    android.util.Log.i("QueueDrag", "dragHandle onTouch: action=${event.actionMasked}, pos=$bindingAdapterPosition")
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        android.util.Log.i("QueueDrag", "dragHandle ACTION_DOWN -> calling onStartDrag")
                        onStartDrag?.invoke(this)
                    }
                    false
                }
                binding.removeButton.visibility = View.VISIBLE
                binding.removeButton.setOnClickListener {
                    onRemoveClick(item)
                }
            }

            binding.root.setOnClickListener {
                onTrackClick(item)
            }
        }
    }
}
