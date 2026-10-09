package com.bsdung.musicassistantcar.ui.dialog

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.UnifiedTrackDto
import com.bsdung.musicassistantcar.ui.adapter.UnifiedTracksAdapter
import com.bsdung.musicassistantcar.ui.util.UnifiedActionHelper
import com.bsdung.musicassistantcar.ui.util.UnifiedItemAction
import com.bsdung.musicassistantcar.ui.util.UnifiedSourceFilter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class UnifiedLibraryDialog(
    private val activity: Activity,
    private val client: CarApiClient = CarApiClient(),
    private val onPlayTrackInLibrary: ((UnifiedTrackDto) -> Unit)? = null,
) {

    private val dialog = BottomSheetDialog(activity)
    private val adapter: UnifiedTracksAdapter

    private var currentFilter: UnifiedSourceFilter = UnifiedSourceFilter.ALL
    private var currentQuery: String = ""
    private var currentOffset: Int = 0
    private val pageSize: Int = 30
    private var totalItems: Int = 0
    private var isLoading: Boolean = false

    // Views created programmatically
    private val loadingBar: ProgressBar
    private val statusMessageView: TextView
    private val retryButton: Button
    private val recyclerView: RecyclerView
    private val prevButton: Button
    private val nextButton: Button
    private val pageIndicatorView: TextView
    private val filterButtons = mutableMapOf<UnifiedSourceFilter, Button>()
    private val searchEditText: EditText

    init {
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_bottom_sheet)
            setPadding(dp(20), dp(12), dp(20), dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Pill handle
        val handle = View(activity).apply {
            setBackgroundResource(R.drawable.bg_button_tertiary)
            val lp = LinearLayout.LayoutParams(dp(40), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(12)
            }
            layoutParams = lp
        }
        root.addView(handle)

        // 2. Header: Title + Close Button
        val headerLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val titleView = TextView(activity).apply {
            text = "Thư viện hợp nhất"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeButton = Button(activity).apply {
            text = "Đóng"
            textSize = 13f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_control)
            minHeight = dp(44)
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { dialog.dismiss() }
        }
        headerLayout.addView(titleView)
        headerLayout.addView(closeButton)
        root.addView(headerLayout)

        // 3. Search Bar
        val searchLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_surface_card)
            setPadding(dp(12), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { bottomMargin = dp(8) }
        }
        searchEditText = EditText(activity).apply {
            hint = "Tìm bài hát, ca sĩ..."
            setHintTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            textSize = 14f
            background = null
            maxLines = 1
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard()
                    applySearch(text.toString().trim())
                    true
                } else false
            }
        }
        val searchBtn = Button(activity).apply {
            text = "Tìm"
            textSize = 13f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_control)
            minHeight = dp(40)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                hideKeyboard()
                applySearch(searchEditText.text.toString().trim())
            }
        }
        val clearBtn = Button(activity).apply {
            text = "Xóa"
            textSize = 13f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            setBackgroundResource(R.drawable.bg_button_control)
            minHeight = dp(40)
            setPadding(dp(10), 0, dp(10), 0)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(4) }
            layoutParams = lp
            setOnClickListener {
                searchEditText.setText("")
                hideKeyboard()
                applySearch("")
            }
        }
        searchLayout.addView(searchEditText)
        searchLayout.addView(searchBtn)
        searchLayout.addView(clearBtn)
        root.addView(searchLayout)

        // 4. Source Filter ScrollView
        val filterScrollView = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val filterContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        UnifiedSourceFilter.values().forEach { filter ->
            val filterBtn = Button(activity).apply {
                text = filter.displayLabel
                textSize = 12f
                isAllCaps = false
                minHeight = dp(38)
                setPadding(dp(12), 0, dp(12), 0)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
                layoutParams = lp
                setOnClickListener {
                    applyFilter(filter)
                }
            }
            filterButtons[filter] = filterBtn
            filterContainer.addView(filterBtn)
        }
        filterScrollView.addView(filterContainer)
        root.addView(filterScrollView)

        // 5. Loading Bar
        loadingBar = ProgressBar(activity).apply {
            isIndeterminate = true
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(16)
                bottomMargin = dp(16)
            }
            layoutParams = lp
        }
        root.addView(loadingBar)

        // 6. Error & Empty State Layout
        val stateContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(20)
                bottomMargin = dp(20)
            }
        }
        statusMessageView = TextView(activity).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            visibility = View.GONE
        }
        retryButton = Button(activity).apply {
            text = "Thử lại"
            textSize = 13f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_control)
            minHeight = dp(44)
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            layoutParams = lp
            setOnClickListener { loadCurrentPage() }
        }
        stateContainer.addView(statusMessageView)
        stateContainer.addView(retryButton)
        root.addView(stateContainer)

        // 7. RecyclerView
        recyclerView = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        adapter = UnifiedTracksAdapter { track, action ->
            handleItemAction(track, action)
        }
        recyclerView.adapter = adapter
        root.addView(recyclerView)

        // 8. Pagination Controls Layout
        val paginationLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        prevButton = Button(activity).apply {
            text = "◀ Trang trước"
            textSize = 12f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_control)
            minHeight = dp(44)
            isEnabled = false
            alpha = 0.5f
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                if (currentOffset >= pageSize) {
                    currentOffset -= pageSize
                    loadCurrentPage()
                }
            }
        }
        pageIndicatorView = TextView(activity).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            text = "Trang 1/1"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nextButton = Button(activity).apply {
            text = "Trang sau ▶"
            textSize = 12f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_control)
            minHeight = dp(44)
            isEnabled = false
            alpha = 0.5f
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                if (currentOffset + pageSize < totalItems) {
                    currentOffset += pageSize
                    loadCurrentPage()
                }
            }
        }
        paginationLayout.addView(prevButton)
        paginationLayout.addView(pageIndicatorView)
        paginationLayout.addView(nextButton)
        root.addView(paginationLayout)

        dialog.setContentView(root)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)

        updateFilterButtonStyles()
    }

    fun show() {
        dialog.show()
        currentOffset = 0
        loadCurrentPage()
    }

    private fun applyFilter(filter: UnifiedSourceFilter) {
        if (currentFilter == filter && currentQuery.isBlank()) return
        currentFilter = filter
        currentOffset = 0
        updateFilterButtonStyles()
        loadCurrentPage()
    }

    private fun applySearch(query: String) {
        currentQuery = query
        currentOffset = 0
        loadCurrentPage()
    }

    private fun updateFilterButtonStyles() {
        filterButtons.forEach { (filter, btn) ->
            if (filter == currentFilter) {
                btn.setBackgroundResource(R.drawable.bg_quality_chip_active)
                btn.setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            } else {
                btn.setBackgroundResource(R.drawable.bg_quality_chip_inactive)
                btn.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            }
        }
    }

    private fun loadCurrentPage() {
        if (isLoading) return
        isLoading = true

        loadingBar.visibility = View.VISIBLE
        statusMessageView.visibility = View.GONE
        retryButton.visibility = View.GONE

        val isSearch = currentQuery.isNotBlank()
        val filterParam = if (currentFilter == UnifiedSourceFilter.ALL) null else currentFilter.serverKey
        val offset = currentOffset
        val query = currentQuery

        kotlin.concurrent.thread {
            val result: Result<Pair<List<UnifiedTrackDto>, Int>> = runCatching {
                if (isSearch) {
                    val items = client.unifiedSearch(
                        query = query,
                        limit = pageSize,
                        offset = offset,
                        includeExternal = true,
                    )
                    // If filter is active during search, filter client-side if needed
                    val filtered = if (filterParam != null) {
                        items.filter { it.primarySource.equals(filterParam, ignoreCase = true) }
                    } else {
                        items
                    }
                    Pair(filtered, if (filtered.size < pageSize && offset == 0) filtered.size else (offset + filtered.size + 1))
                } else {
                    val browseResult = client.unifiedBrowse(
                        source = filterParam,
                        limit = pageSize,
                        offset = offset,
                    )
                    Pair(browseResult.items, browseResult.total)
                }
            }

            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                isLoading = false
                loadingBar.visibility = View.GONE

                result.fold(
                    onSuccess = { (items, total) ->
                        totalItems = total
                        renderTracks(items)
                    },
                    onFailure = { err ->
                        showError("Không thể tải danh sách: ${err.message ?: "Lỗi mạng"}")
                    }
                )
            }
        }
    }

    private fun renderTracks(items: List<UnifiedTrackDto>) {
        if (items.isEmpty()) {
            adapter.submitList(emptyList())
            statusMessageView.text = if (currentQuery.isNotBlank()) {
                "Không tìm thấy kết quả nào cho '$currentQuery'"
            } else {
                "Thư viện chưa có bài hát nào"
            }
            statusMessageView.visibility = View.VISIBLE
            retryButton.visibility = View.GONE
        } else {
            statusMessageView.visibility = View.GONE
            retryButton.visibility = View.GONE
            adapter.submitList(items)
        }
        updatePaginationUI(items.size)
    }

    private fun showError(message: String) {
        adapter.submitList(emptyList())
        statusMessageView.text = message
        statusMessageView.visibility = View.VISIBLE
        retryButton.visibility = View.VISIBLE
        updatePaginationUI(0)
    }

    private fun updatePaginationUI(currentBatchSize: Int) {
        val totalPages = UnifiedActionHelper.calculateTotalPages(totalItems, pageSize)
        val currentPage = UnifiedActionHelper.calculateCurrentPage(currentOffset, pageSize)

        pageIndicatorView.text = if (totalItems > 0) {
            "Trang $currentPage / $totalPages ($totalItems mục)"
        } else {
            "Trang 1 / 1"
        }

        val hasPrev = currentOffset > 0
        val hasNext = if (currentQuery.isNotBlank()) {
            currentBatchSize >= pageSize
        } else {
            currentOffset + pageSize < totalItems
        }

        prevButton.isEnabled = hasPrev
        prevButton.alpha = if (hasPrev) 1.0f else 0.5f

        nextButton.isEnabled = hasNext
        nextButton.alpha = if (hasNext) 1.0f else 0.5f
    }

    private fun handleItemAction(track: UnifiedTrackDto, action: UnifiedItemAction) {
        when (action) {
            UnifiedItemAction.PLAY_IN_LIBRARY -> {
                if (onPlayTrackInLibrary != null) {
                    onPlayTrackInLibrary.invoke(track)
                    dialog.dismiss()
                } else {
                    Toast.makeText(
                        activity,
                        "Phát trong thư viện: ${track.title} (ID #${track.canonicalTrackId})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            UnifiedItemAction.OPEN_EXTERNAL -> {
                val url = track.externalUrl
                if (!url.isNullOrBlank() && UnifiedActionHelper.isValidExternalUrl(url)) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    try {
                        activity.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(activity, "Không tìm thấy ứng dụng mở liên kết", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(activity, "Liên kết không an toàn hoặc không hợp lệ", Toast.LENGTH_SHORT).show()
                }
            }
            UnifiedItemAction.READ_ONLY -> {
                Toast.makeText(activity, "${track.title} - ${track.artist} (Chỉ xem)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hideKeyboard() {
        val imm = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchEditText.windowToken, 0)
    }

    private fun dp(value: Int): Int {
        val density = activity.resources.displayMetrics.density
        return (value * density).toInt()
    }
}
