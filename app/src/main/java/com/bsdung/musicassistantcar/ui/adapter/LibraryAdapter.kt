package com.bsdung.musicassistantcar.ui.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.RecyclerView
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.data.MediaTechnicalSummary
import com.bsdung.musicassistantcar.data.RemoteCatalog
import com.bsdung.musicassistantcar.databinding.ItemLibraryCategoryBinding
import com.bsdung.musicassistantcar.databinding.ItemLibraryFolderBinding
import com.bsdung.musicassistantcar.databinding.ItemLibraryTrackBinding
import com.bsdung.musicassistantcar.ui.controller.ProgressController
import com.bsdung.musicassistantcar.ui.model.LibraryItem
import com.bsdung.musicassistantcar.ui.util.ArtworkLoader
import com.bsdung.musicassistantcar.ui.util.AudioInfoFormatter
import com.bsdung.musicassistantcar.ui.util.MetadataDisplayHelper
import com.bsdung.musicassistantcar.ui.util.MobilePlayerLayoutPolicy

class LibraryAdapter(
    private val onCategoryClick: (LibraryItem.Category) -> Unit,
    private val onFolderClick: (LibraryItem.Folder) -> Unit,
    private val onTrackClick: (LibraryItem.Track, List<MediaItem>, Int) -> Unit,
    private val onTrackLongClick: ((LibraryItem.Track) -> Unit)? = null,
    private val onFolderLongClick: ((LibraryItem.Folder) -> Unit)? = null,
    private val onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null,
    private val onSelectionChanged: ((Int) -> Unit)? = null,
    private val onDownloadClick: ((LibraryItem.Track) -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CATEGORY = 1
        private const val TYPE_FOLDER = 2
        private const val TYPE_TRACK = 3
        const val PAYLOAD_TECHNICAL_INFO = "PAYLOAD_TECHNICAL_INFO"
    }

    private val items = mutableListOf<LibraryItem>()
    private var currentPlayingMediaId: String? = null
    private var isPlayerPlaying: Boolean = false

    var isEditMode: Boolean = false
        private set
    var canReorder: Boolean = false
        private set
    val selectedMediaIds = mutableSetOf<String>()

    fun setEditMode(enabled: Boolean, allowReorder: Boolean = false) {
        isEditMode = enabled
        canReorder = allowReorder
        if (!enabled) {
            selectedMediaIds.clear()
        }
        runCatching { notifyDataSetChanged() }
    }

    private fun isSelectableInEditMode(item: LibraryItem.Track): Boolean {
        if (item.id.startsWith("download_job:")) return false
        val extras = item.mediaItem.mediaMetadata.extras
        if (extras?.getBoolean("is_download_job", false) == true) return false
        return true
    }

    fun toggleSelection(mediaId: String) {
        if (mediaId.startsWith("download_job:")) return
        if (selectedMediaIds.contains(mediaId)) {
            selectedMediaIds.remove(mediaId)
        } else {
            selectedMediaIds.add(mediaId)
        }
        onSelectionChanged?.invoke(selectedMediaIds.size)
    }

    fun selectAllTracks() {
        val trackIds = items.filterIsInstance<LibraryItem.Track>()
            .filter { isSelectableInEditMode(it) }
            .map { it.id }
        if (selectedMediaIds.size == trackIds.size && trackIds.isNotEmpty()) {
            selectedMediaIds.clear()
        } else {
            selectedMediaIds.clear()
            selectedMediaIds.addAll(trackIds)
        }
        runCatching { notifyDataSetChanged() }
        onSelectionChanged?.invoke(selectedMediaIds.size)
    }

    fun clearSelection() {
        selectedMediaIds.clear()
        runCatching { notifyDataSetChanged() }
        onSelectionChanged?.invoke(0)
    }

    fun moveItem(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < 0 || fromPosition >= items.size || toPosition < 0 || toPosition >= items.size) {
            return false
        }
        val item = items.removeAt(fromPosition)
        items.add(toPosition, item)
        runCatching { notifyItemMoved(fromPosition, toPosition) }
        return true
    }

    fun submitItems(newItems: List<LibraryItem>) {
        items.clear()
        items.addAll(newItems)
        runCatching { notifyDataSetChanged() }
    }

    fun getItems(): List<LibraryItem> = items.toList()

    fun updateTrackTechnicalInfo(mediaId: String, summary: MediaTechnicalSummary): Boolean {
        val index = items.indexOfFirst { it is LibraryItem.Track && it.id == mediaId }
        if (index >= 0) {
            val old = items[index] as LibraryItem.Track
            val hasChanges = (summary.format != null && summary.format != old.format) ||
                    (summary.codec != null && summary.codec != old.codec) ||
                    (summary.sampleRate != null && summary.sampleRate != old.sampleRate) ||
                    (summary.bitrate != null && summary.bitrate != old.bitrate) ||
                    (summary.channels != null && summary.channels != old.channels) ||
                    (summary.sizeBytes != null && summary.sizeBytes != old.sizeBytes)

            if (!hasChanges) {
                return false
            }

            items[index] = old.copy(
                format = summary.format ?: old.format,
                codec = summary.codec ?: old.codec,
                sampleRate = summary.sampleRate ?: old.sampleRate,
                bitrate = summary.bitrate ?: old.bitrate,
                channels = summary.channels ?: old.channels,
                sizeBytes = summary.sizeBytes ?: old.sizeBytes,
            )
            runCatching { notifyItemChanged(index, PAYLOAD_TECHNICAL_INFO) }
            return true
        }
        return false
    }

    fun updateNowPlaying(mediaId: String?, isPlaying: Boolean) {
        val previousId = currentPlayingMediaId
        val previousPlaying = isPlayerPlaying
        currentPlayingMediaId = mediaId
        isPlayerPlaying = isPlaying

        items.forEachIndexed { index, item ->
            if (item is LibraryItem.Track) {
                val matchesCurrent = item.id == mediaId
                val matchedPrevious = item.id == previousId
                if (matchesCurrent || matchedPrevious) {
                    items[index] = item.copy(
                        isCurrent = matchesCurrent,
                        isPlaying = if (matchesCurrent) isPlaying else false,
                    )
                    notifyItemChanged(index)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is LibraryItem.Category -> TYPE_CATEGORY
            is LibraryItem.Folder -> TYPE_FOLDER
            is LibraryItem.Track -> TYPE_TRACK
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CATEGORY -> {
                val binding = ItemLibraryCategoryBinding.inflate(inflater, parent, false)
                CategoryViewHolder(binding)
            }
            TYPE_FOLDER -> {
                val binding = ItemLibraryFolderBinding.inflate(inflater, parent, false)
                FolderViewHolder(binding)
            }
            else -> {
                val binding = ItemLibraryTrackBinding.inflate(inflater, parent, false)
                TrackViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LibraryItem.Category -> (holder as CategoryViewHolder).bind(item)
            is LibraryItem.Folder -> (holder as FolderViewHolder).bind(item)
            is LibraryItem.Track -> (holder as TrackViewHolder).bind(item)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.contains(PAYLOAD_TECHNICAL_INFO) && holder is TrackViewHolder) {
            val item = items.getOrNull(position)
            if (item is LibraryItem.Track) {
                holder.bindTechnicalInfo(item)
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    inner class CategoryViewHolder(
        private val binding: ItemLibraryCategoryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LibraryItem.Category) {
            binding.categoryTitle.text = MetadataDisplayHelper.cleanPlaylistName(item.title)
            binding.categorySubtitle.text = item.subtitle
            binding.categoryIcon.setImageResource(item.iconResId)
            binding.root.setOnClickListener {
                onCategoryClick(item)
            }
        }
    }

    inner class FolderViewHolder(
        private val binding: ItemLibraryFolderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LibraryItem.Folder) {
            binding.folderTitle.text = MetadataDisplayHelper.cleanPlaylistName(item.title)
            if (item.subtitle.isNotBlank()) {
                binding.folderSubtitle.text = item.subtitle
                binding.folderSubtitle.visibility = View.VISIBLE
            } else {
                binding.folderSubtitle.visibility = View.GONE
            }
            binding.root.setOnClickListener {
                onFolderClick(item)
            }
            binding.root.setOnLongClickListener {
                onFolderLongClick?.invoke(item)
                true
            }
        }
    }

    inner class TrackViewHolder(
        private val binding: ItemLibraryTrackBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bindTechnicalInfo(item: LibraryItem.Track) {
            val audioInfo = AudioInfoFormatter.formatAudioInfo(
                format = item.format,
                sizeBytes = item.sizeBytes,
                codec = item.codec,
                bitrate = item.bitrate,
                sampleRate = item.sampleRate,
                channels = item.channels,
            )
            val extras = item.mediaItem.mediaMetadata.extras
            val rawSource = (extras?.getString("source_type") ?: extras?.getString("source"))
                ?.lowercase()
                .orEmpty()
            val sourceLabel = when (rawSource) {
                "drive", "google_drive" -> "Drive"
                "telegram" -> "Telegram"
                "device", "local" -> "Thiết bị"
                else -> when {
                    item.id.startsWith(RemoteCatalog.DEVICE_PREFIX) -> "Thiết bị"
                    item.id.startsWith(RemoteCatalog.DRIVE_FILE_PREFIX) -> "Drive"
                    else -> ""
                }
            }
            val combinedInfo = listOfNotNull(
                sourceLabel.takeIf { it.isNotBlank() },
                audioInfo?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (combinedInfo.isNotBlank()) {
                binding.trackAudioInfo.text = combinedInfo
                binding.trackAudioInfo.visibility = View.VISIBLE
            } else {
                binding.trackAudioInfo.text = ""
                binding.trackAudioInfo.visibility = View.GONE
            }
        }

        fun bind(item: LibraryItem.Track) {
            val cleanTitle = MetadataDisplayHelper.cleanTitle(item.title)
            val cleanArtist = MetadataDisplayHelper.cleanArtist(item.artist)
            val cleanAlbum = MetadataDisplayHelper.cleanAlbum(item.album)

            binding.trackTitle.text = cleanTitle
            val subtitleText = when {
                cleanArtist.isNotBlank() && cleanAlbum.isNotBlank() -> "$cleanArtist · $cleanAlbum"
                cleanArtist.isNotBlank() -> cleanArtist
                cleanAlbum.isNotBlank() -> cleanAlbum
                else -> ""
            }
            if (subtitleText.isNotBlank()) {
                binding.trackSubtitle.text = subtitleText
                binding.trackSubtitle.visibility = View.VISIBLE
            } else {
                binding.trackSubtitle.text = ""
                binding.trackSubtitle.visibility = View.GONE
            }

            bindTechnicalInfo(item)

            ArtworkLoader.loadArtwork(
                imageView = binding.trackThumbnail,
                uri = item.artworkUri,
                cornerRadiusDp = 10f,
                placeholderResId = R.drawable.ic_music_note,
            )

            val context = binding.root.context
            if (item.isCurrent) {
                binding.activeIndicator.visibility = View.VISIBLE
                binding.trackRoot.setBackgroundResource(R.drawable.bg_track_active)
                binding.trackTitle.setTextColor(ContextCompat.getColor(context, R.color.accent_primary))
                binding.nowPlayingIndicator.visibility = View.VISIBLE
                binding.nowPlayingIndicator.setImageResource(
                    if (item.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                )
                binding.trackDuration.visibility = View.GONE
            } else {
                binding.activeIndicator.visibility = View.GONE
                binding.trackRoot.setBackgroundResource(R.drawable.bg_track_normal)
                binding.trackTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                binding.nowPlayingIndicator.visibility = View.GONE
                if (item.durationMs != null && item.durationMs > 0L) {
                    binding.trackDuration.text = ProgressController.formatTimeMs(item.durationMs)
                    binding.trackDuration.visibility = View.VISIBLE
                } else {
                    binding.trackDuration.visibility = View.GONE
                }
            }

            if (isEditMode) {
                binding.downloadContainer.visibility = View.GONE
                binding.txtDownloadStatus.setOnClickListener(null)
                binding.btnDownload.setOnClickListener(null)
                binding.btnTrackActions.visibility = View.GONE
                binding.btnTrackActions.setOnClickListener(null)
                val selectable = isSelectableInEditMode(item)
                if (selectable) {
                    binding.trackCheckbox.visibility = View.VISIBLE
                    binding.trackCheckbox.isChecked = selectedMediaIds.contains(item.id)
                    if (canReorder) {
                        binding.dragHandle.visibility = View.VISIBLE
                        binding.dragHandle.setOnTouchListener { _, event ->
                            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                                onStartDrag?.invoke(this@TrackViewHolder)
                            }
                            false
                        }
                    } else {
                        binding.dragHandle.visibility = View.GONE
                        binding.dragHandle.setOnTouchListener(null)
                    }

                    binding.root.setOnClickListener {
                        toggleSelection(item.id)
                        binding.trackCheckbox.isChecked = selectedMediaIds.contains(item.id)
                    }
                } else {
                    binding.trackCheckbox.visibility = View.GONE
                    binding.dragHandle.visibility = View.GONE
                    binding.dragHandle.setOnTouchListener(null)
                    binding.root.setOnClickListener(null)
                }
                binding.root.setOnLongClickListener(null)
            } else {
                binding.trackCheckbox.visibility = View.GONE
                binding.dragHandle.visibility = View.GONE
                binding.dragHandle.setOnTouchListener(null)
                binding.btnTrackActions.visibility = View.VISIBLE
                binding.btnTrackActions.setOnClickListener {
                    onTrackLongClick?.invoke(item)
                }

                val canonicalId = RemoteCatalog.canonicalTrackMediaId(item.mediaItem) ?: item.id
                val trackId = canonicalId.removePrefix("download_job:").removePrefix(RemoteCatalog.TRACK_PREFIX).toLongOrNull()
                val isDeviceTrack = item.id.startsWith(RemoteCatalog.DEVICE_PREFIX)

                if (trackId != null && !isDeviceTrack) {
                    val dm = com.bsdung.musicassistantcar.playback.PinnedDownloadManager.getDefault(context)
                    val status = dm.getDownloadStatus(trackId)
                    val cfg = context.resources.configuration
                    val isPortraitPhone = MobilePlayerLayoutPolicy.isPortraitPhone(cfg.screenWidthDp, cfg.screenHeightDp)

                    binding.downloadContainer.visibility = View.VISIBLE
                    when (status.state) {
                        com.bsdung.musicassistantcar.playback.DownloadState.NOT_DOWNLOADED -> {
                            binding.downloadProgressLayout.visibility = View.GONE
                            if (isPortraitPhone) {
                                binding.txtDownloadStatus.visibility = View.GONE
                                binding.txtDownloadStatus.setOnClickListener(null)
                            } else {
                                binding.txtDownloadStatus.visibility = View.VISIBLE
                                binding.txtDownloadStatus.text = "↓ Tải về"
                                binding.txtDownloadStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                                binding.txtDownloadStatus.setOnClickListener { onDownloadClick?.invoke(item) }
                            }
                            binding.btnDownload.visibility = View.VISIBLE
                            binding.btnDownload.setImageResource(R.drawable.ic_download)
                            binding.btnDownload.setColorFilter(ContextCompat.getColor(context, R.color.text_tertiary))
                            binding.btnDownload.contentDescription = "↓ Tải về"
                            binding.btnDownload.setOnClickListener { onDownloadClick?.invoke(item) }
                        }
                        com.bsdung.musicassistantcar.playback.DownloadState.QUEUED -> {
                            binding.downloadProgressLayout.visibility = View.GONE
                            if (isPortraitPhone) {
                                binding.txtDownloadStatus.visibility = View.GONE
                                binding.txtDownloadStatus.setOnClickListener(null)
                            } else {
                                binding.txtDownloadStatus.visibility = View.VISIBLE
                                binding.txtDownloadStatus.text = "Đang chờ tải"
                                binding.txtDownloadStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                                binding.txtDownloadStatus.setOnClickListener { onDownloadClick?.invoke(item) }
                            }
                            binding.btnDownload.visibility = View.VISIBLE
                            binding.btnDownload.setImageResource(R.drawable.ic_close)
                            binding.btnDownload.setColorFilter(ContextCompat.getColor(context, R.color.text_tertiary))
                            binding.btnDownload.contentDescription = "Hủy"
                            binding.btnDownload.setOnClickListener { onDownloadClick?.invoke(item) }
                        }
                        com.bsdung.musicassistantcar.playback.DownloadState.WAITING_FOR_WIFI -> {
                            binding.downloadProgressLayout.visibility = View.GONE
                            if (isPortraitPhone) {
                                binding.txtDownloadStatus.visibility = View.GONE
                                binding.txtDownloadStatus.setOnClickListener(null)
                            } else {
                                binding.txtDownloadStatus.visibility = View.VISIBLE
                                binding.txtDownloadStatus.text = "Đang chờ Wi-Fi"
                                binding.txtDownloadStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                                binding.txtDownloadStatus.setOnClickListener { onDownloadClick?.invoke(item) }
                            }
                            binding.btnDownload.visibility = View.VISIBLE
                            binding.btnDownload.setImageResource(R.drawable.ic_close)
                            binding.btnDownload.setColorFilter(ContextCompat.getColor(context, R.color.text_tertiary))
                            binding.btnDownload.contentDescription = "Hủy"
                            binding.btnDownload.setOnClickListener { onDownloadClick?.invoke(item) }
                        }
                        com.bsdung.musicassistantcar.playback.DownloadState.DOWNLOADING -> {
                            binding.txtDownloadStatus.visibility = View.GONE
                            binding.txtDownloadStatus.setOnClickListener(null)
                            binding.downloadProgressLayout.visibility = View.VISIBLE
                            val total = status.totalBytes
                            val downloaded = status.bytesDownloaded
                            val dlMb = String.format(java.util.Locale.US, "%.1f", downloaded / (1024.0 * 1024.0))

                            if (total > 0L) {
                                val pct = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                                binding.downloadProgressBar.isIndeterminate = false
                                binding.downloadProgressBar.progress = pct
                                val totMb = String.format(java.util.Locale.US, "%.1f", total / (1024.0 * 1024.0))
                                binding.txtDownloadProgress.text = "$pct% ($dlMb / $totMb MB)"
                            } else {
                                binding.downloadProgressBar.isIndeterminate = true
                                binding.txtDownloadProgress.text = "$dlMb MB"
                            }
                            binding.downloadProgressLayout.setOnClickListener { onDownloadClick?.invoke(item) }
                            binding.btnDownload.visibility = View.VISIBLE
                            binding.btnDownload.setImageResource(R.drawable.ic_close)
                            binding.btnDownload.setColorFilter(ContextCompat.getColor(context, R.color.text_tertiary))
                            binding.btnDownload.contentDescription = "Hủy"
                            binding.btnDownload.setOnClickListener { onDownloadClick?.invoke(item) }
                        }
                        com.bsdung.musicassistantcar.playback.DownloadState.FINALIZING -> {
                            binding.downloadProgressLayout.visibility = View.GONE
                            binding.txtDownloadStatus.visibility = View.VISIBLE
                            binding.txtDownloadStatus.text = "Đang hoàn tất..."
                            binding.txtDownloadStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_primary))
                            binding.txtDownloadStatus.setOnClickListener { onDownloadClick?.invoke(item) }
                            binding.btnDownload.visibility = View.GONE
                            binding.btnDownload.setOnClickListener(null)
                        }
                        com.bsdung.musicassistantcar.playback.DownloadState.OFFLINE_READY -> {
                            binding.downloadProgressLayout.visibility = View.GONE
                            if (isPortraitPhone) {
                                binding.txtDownloadStatus.visibility = View.GONE
                                binding.txtDownloadStatus.setOnClickListener(null)
                            } else {
                                binding.txtDownloadStatus.visibility = View.VISIBLE
                                val isOfflineRow = item.mediaItem.mediaMetadata.extras?.getBoolean("is_offline_download", false) == true
                                if (isOfflineRow) {
                                    val rec = dm.getRecord(trackId)
                                    val actualBytes = dm.getEffectiveTrackSizeBytes(trackId).takeIf { it > 0L }
                                        ?: (rec?.fileSizeBytes ?: 0L)
                                    val sizeStr = if (actualBytes > 0L) " · ${dm.formatStorageBytes(actualBytes)}" else ""
                                    val targetStr = rec?.storageTarget?.let { " · ${dm.formatStorageTargetLabel(it)}" } ?: ""
                                    val dateStr = if ((rec?.downloadedAtMs ?: 0L) > 0L) {
                                        "\n${dm.formatDownloadDate(rec!!.downloadedAtMs)}$targetStr"
                                    } else if (targetStr.isNotEmpty()) {
                                        "\n${targetStr.removePrefix(" · ")}"
                                    } else {
                                        ""
                                    }
                                    binding.txtDownloadStatus.text = "✓ Offline$sizeStr$dateStr"
                                } else {
                                    binding.txtDownloadStatus.text = "✓ Offline"
                                }
                                binding.txtDownloadStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_primary))
                                binding.txtDownloadStatus.setOnClickListener { onDownloadClick?.invoke(item) }
                            }
                            binding.btnDownload.visibility = View.VISIBLE
                            binding.btnDownload.setImageResource(R.drawable.ic_download_done)
                            binding.btnDownload.setColorFilter(ContextCompat.getColor(context, R.color.accent_primary))
                            binding.btnDownload.contentDescription = "✓ Offline"
                            binding.btnDownload.setOnClickListener { onDownloadClick?.invoke(item) }
                        }
                        com.bsdung.musicassistantcar.playback.DownloadState.OFFLINE_MEDIA_UNAVAILABLE -> {
                            binding.downloadProgressLayout.visibility = View.GONE
                            binding.txtDownloadStatus.visibility = View.VISIBLE
                            binding.txtDownloadStatus.text = if (isPortraitPhone) "! Thẻ SD" else "Thẻ nhớ SD không khả dụng"
                            binding.txtDownloadStatus.setTextColor(ContextCompat.getColor(context, R.color.status_offline))
                            binding.txtDownloadStatus.setOnClickListener { onDownloadClick?.invoke(item) }
                            binding.btnDownload.visibility = View.VISIBLE
                            binding.btnDownload.setImageResource(R.drawable.ic_download_done)
                            binding.btnDownload.setColorFilter(ContextCompat.getColor(context, R.color.status_offline))
                            binding.btnDownload.contentDescription = "Thẻ nhớ SD không khả dụng"
                            binding.btnDownload.setOnClickListener { onDownloadClick?.invoke(item) }
                        }
                        com.bsdung.musicassistantcar.playback.DownloadState.STORAGE_TARGET_UNAVAILABLE -> {
                            binding.downloadProgressLayout.visibility = View.GONE
                            binding.txtDownloadStatus.visibility = View.VISIBLE
                            binding.txtDownloadStatus.text = if (isPortraitPhone) "! Thẻ SD" else "! Thẻ nhớ SD không khả dụng"
                            binding.txtDownloadStatus.setTextColor(ContextCompat.getColor(context, R.color.status_offline))
                            binding.txtDownloadStatus.setOnClickListener { onDownloadClick?.invoke(item) }
                            binding.btnDownload.visibility = View.VISIBLE
                            binding.btnDownload.setImageResource(R.drawable.ic_download)
                            binding.btnDownload.setColorFilter(ContextCompat.getColor(context, R.color.status_offline))
                            binding.btnDownload.contentDescription = "Thử lại"
                            binding.btnDownload.setOnClickListener { onDownloadClick?.invoke(item) }
                        }
                        com.bsdung.musicassistantcar.playback.DownloadState.FAILED -> {
                            binding.downloadProgressLayout.visibility = View.GONE
                            binding.txtDownloadStatus.visibility = View.VISIBLE
                            binding.txtDownloadStatus.text = "! Tải lỗi"
                            binding.txtDownloadStatus.setTextColor(ContextCompat.getColor(context, R.color.status_offline))
                            binding.txtDownloadStatus.setOnClickListener { onDownloadClick?.invoke(item) }
                            binding.btnDownload.visibility = View.VISIBLE
                            binding.btnDownload.setImageResource(R.drawable.ic_download)
                            binding.btnDownload.setColorFilter(ContextCompat.getColor(context, R.color.status_offline))
                            binding.btnDownload.contentDescription = "Thử lại"
                            binding.btnDownload.setOnClickListener { onDownloadClick?.invoke(item) }
                        }
                        com.bsdung.musicassistantcar.playback.DownloadState.CANCELLED -> {
                            binding.downloadProgressLayout.visibility = View.GONE
                            binding.txtDownloadStatus.visibility = View.VISIBLE
                            binding.txtDownloadStatus.text = "Đã hủy"
                            binding.txtDownloadStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                            binding.txtDownloadStatus.setOnClickListener { onDownloadClick?.invoke(item) }
                            binding.btnDownload.visibility = View.VISIBLE
                            binding.btnDownload.setImageResource(R.drawable.ic_download)
                            binding.btnDownload.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
                            binding.btnDownload.contentDescription = "Thử lại"
                            binding.btnDownload.setOnClickListener { onDownloadClick?.invoke(item) }
                        }
                    }
                } else {
                    binding.downloadContainer.visibility = View.GONE
                    binding.txtDownloadStatus.setOnClickListener(null)
                    binding.btnDownload.setOnClickListener(null)
                }

                binding.root.setOnClickListener {
                    // Collect only playable track items in current list for full queue playback
                    val playableTracks = items.filterIsInstance<LibraryItem.Track>().map { it.mediaItem }
                    val trackIndex = playableTracks.indexOfFirst { it.mediaId == item.mediaItem.mediaId }
                        .coerceAtLeast(0)
                    onTrackClick(item, playableTracks, trackIndex)
                }

                binding.root.setOnLongClickListener {
                    onTrackLongClick?.invoke(item)
                    true
                }
            }
        }
    }
}
