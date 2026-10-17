package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.Status
import com.bsdung.musicassistantcar.data.telegram.MeloComponents.SongRowModel
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.DisplaySpec
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.Emulation
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.Layout

/**
 * Component preview, optionally inside an emulated device frame with that device's
 * exact pixels and density (centered, rest dimmed). Sample data only; nothing here
 * reads or writes the Telegram library, login, playlists or cache.
 */
class MeloUiPreviewActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var frame: FrameLayout
    private lateinit var scaleNote: TextView
    private var frameWidthPx = 0
    private var frameHeightPx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_MusicAssistantCar)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val prefs = getSharedPreferences(MeloScreenPolicy.PREFS_NAME, Context.MODE_PRIVATE)
        val emulation = MeloScreenPolicy.loadEmulation(prefs)
        val carPreview = MeloScreenPolicy.loadPreviewCarMode(prefs)
        val real = MeloUiLabActivity.realMeasurements(this).display
        val spec = emulation.spec() ?: real
        val frameContext = if (emulation.isActive) emulatedContext(spec) else this

        val layout = MeloScreenPolicy.chooseLayout(spec.widthDp, spec.heightDp, carPreview)
        val profile = MeloScreenPolicy.profileFor(layout, spec.smallestWidthDp)
        val sizes = MeloDesignTokens.sizes(profile, spec.density, spec.fontScale, spec.physicalPpi)
        val components = MeloComponents(frameContext, sizes)
        val songCount = intent.getIntExtra(MeloUiLabActivity.EXTRA_SONG_COUNT, 0).takeIf { it > 0 }

        root = FrameLayout(this).apply { setBackgroundColor(DIMMED) }
        frame = FrameLayout(frameContext).apply { setBackgroundColor(components.palette.background) }
        if (emulation.isActive) {
            frameWidthPx = spec.widthPx
            frameHeightPx = spec.heightPx
            root.addView(frame, FrameLayout.LayoutParams(spec.widthPx, spec.heightPx, Gravity.CENTER))
        } else {
            root.addView(frame, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        frame.addView(
            PreviewContent(frameContext, components, spec, emulation, layout, songCount) { finish() }.build(),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        scaleNote = TextView(this).apply {
            setTextColor(0xFFFFD27A.toInt())
            textSize = 14f
            setBackgroundColor(0xCC000000.toInt())
            setPadding(24, 12, 24, 12)
            visibility = View.GONE
        }
        root.addView(scaleNote, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ))
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> fitFrame(r - l, b - t) }
        setContentView(root)
    }

    /** Resources inside the frame report the emulated density and dp size, like the real device. */
    private fun emulatedContext(spec: DisplaySpec): Context {
        val config = Configuration(resources.configuration).apply {
            densityDpi = spec.densityDpi
            screenWidthDp = spec.widthDp
            screenHeightDp = spec.heightDp
            smallestScreenWidthDp = spec.smallestWidthDp
            fontScale = spec.fontScale
            orientation = if (spec.isLandscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        return ContextThemeWrapper(createConfigurationContext(config), R.style.Theme_MusicAssistantCar)
    }

    private fun fitFrame(availableW: Int, availableH: Int) {
        if (frameWidthPx <= 0) return
        val scale = MeloScreenPolicy.fitScale(frameWidthPx, frameHeightPx, availableW, availableH)
        if (frame.scaleX == scale && frame.scaleY == scale) return
        frame.pivotX = frameWidthPx / 2f
        frame.pivotY = frameHeightPx / 2f
        frame.scaleX = scale
        frame.scaleY = scale
        if (scale < 1f) {
            scaleNote.text = "Khung ${frameWidthPx}×${frameHeightPx} lớn hơn màn này: đang thu nhỏ còn ${(scale * 100).toInt()}%. Xoay máy để xem đúng 1:1."
            scaleNote.visibility = View.VISIBLE
        } else {
            scaleNote.visibility = View.GONE
        }
    }

    private companion object {
        const val DIMMED = 0xFF03070B.toInt()
    }

    /** The preview screen built only from the shared components. */
    private class PreviewContent(
        private val context: Context,
        private val c: MeloComponents,
        private val spec: DisplaySpec,
        private val emulation: Emulation,
        private val layout: Layout,
        private val songCount: Int?,
        private val onClose: () -> Unit
    ) {
        private val s = c.sizes
        private val p = c.palette
        private val isCar = s.profile == Profile.CAR
        private val isCompact = s.profile == Profile.COMPACT

        private val samples = listOf(
            SongRowModel("CHILLOUT #9 ~ TERRY x EMI", "Nhạc Lạnh – Chillout (EMI)", 3725, Status.DOWNLOADED,
                MeloComponentPolicy.qualityLabel("MP3", 44_100, null, 320)),
            SongRowModel("Sử Thanh Hóa", "LakHouse Việt", 296, Status.DOWNLOADED,
                MeloComponentPolicy.qualityLabel("MP3", null, null, null)),
            SongRowModel("LakHouse Session 12", "LakHouse Việt", 2410, Status.DOWNLOADING, progressPercent = 45),
            SongRowModel("Zalo 0948… – Vip Chillout Mixset Nhạc Lạnh Cực Chill Đêm Khuya Không Quảng Cáo", "Mixset >30p", 5400, Status.TELEGRAM),
            SongRowModel("Mixset >30p – Deep House", "Mixset >30p", 2100, Status.ERROR),
            SongRowModel("Biển Gọi Tên Gió", "Hi-Res Lossless", 251, Status.DOWNLOADED,
                MeloComponentPolicy.qualityLabel("FLAC", 96_000, 24, null)),
            SongRowModel("Ballad Đêm Mưa", "Nhạc Lạnh – Chillout (EMI)", 318, Status.TELEGRAM)
        )

        private var playingIndex = 0
        private var playing = true
        private var query = ""
        private var chipIndex = 0
        private var page = 0
        private lateinit var list: LinearLayout
        private lateinit var miniHost: FrameLayout

        private val chipNames = listOf("Tất cả", "Đã tải", "Playlist", "Chillout", "LakHouse", "Mixset >30p")

        fun build(): View = if (isCar) buildCar() else buildColumn()

        private fun searchBar(): LinearLayout =
            c.searchBar(songCount, onQuery = { query = it; page = 0; renderList() }) {
                Toast.makeText(context, "Bộ lọc: sẽ làm ở bước sau", Toast.LENGTH_SHORT).show()
            }

        private fun sampleNote(): TextView =
            c.text("Dữ liệu mẫu – không phải thư viện thật", s.secondaryText, p.textSecondary).apply {
                setPadding(0, s.gap, 0, s.gap / 2)
            }

        private fun matchWrap() =
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        /** Gọn / Điện thoại / Tablet: one column, scrolling list, mini player at the bottom. */
        private fun buildColumn(): View {
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(s.screenMargin, s.gap, s.screenMargin, s.gap)
            }
            column.addView(topBar())
            column.addView(c.text(infoLine(), s.secondaryText, p.textSecondary, maxLines = 3).apply {
                setPadding(0, 0, 0, s.gap)
            })
            val search = searchBar()
            if (isCompact) {
                // Gọn: search is hidden behind the icon in the top bar; no chip row.
                search.visibility = View.GONE
                searchToggle = { search.visibility = if (search.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            }
            column.addView(search)
            if (!isCompact) {
                column.addView(c.spacer(s.gap))
                column.addView(c.chipRow(chipLabels(), chipIndex) { chipIndex = it; page = 0; renderList() })
            }
            column.addView(sampleNote())
            list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            column.addView(ScrollView(context).apply { addView(list) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            miniHost = FrameLayout(context)
            column.addView(miniHost, matchWrap().apply { topMargin = s.gap })
            renderList()
            return column
        }

        /**
         * Car Mode: player pane (760px at FYT density) and list pane (1000px) side by side,
         * 5 numbered rows per page with page buttons instead of scrolling.
         * The navigation column arrives with the real Car Mode screen (spec step 5).
         */
        private fun buildCar(): View {
            val r = MeloDesignTokens.CarReferencePx
            val ppi = spec.physicalPpi
            val panes = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(s.screenMargin, s.screenMargin, s.screenMargin, s.screenMargin)
            }
            val player = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            player.addView(topBar())
            player.addView(c.text(infoLine(), s.secondaryText, p.textSecondary, maxLines = 4).apply {
                setPadding(0, 0, 0, s.gap)
            })
            player.addView(searchBar())
            player.addView(c.spacer(s.gap))
            miniHost = FrameLayout(context)
            player.addView(miniHost, matchWrap())
            panes.addView(player, LinearLayout.LayoutParams(MeloDesignTokens.carPx(r.PLAYER_PANE, ppi),
                ViewGroup.LayoutParams.MATCH_PARENT).apply { marginEnd = s.screenMargin })

            val listPane = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            listPane.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            listPane.addView(pageButtons())
            panes.addView(listPane, LinearLayout.LayoutParams(MeloDesignTokens.carPx(r.LIST_PANE, ppi),
                ViewGroup.LayoutParams.MATCH_PARENT))
            renderList()
            return panes
        }

        private var searchToggle: (() -> Unit)? = null

        private fun infoLine(): String {
            val device = if (emulation.isActive) emulation.displayName else "Màn thật của máy này"
            return "$device · ${spec.widthPx}×${spec.heightPx} px · ${spec.densityDpi} dpi · " +
                "${spec.widthDp}×${spec.heightDp} dp · Bố cục: ${layout.displayName} · Hồ sơ: ${s.profile.displayName}"
        }

        private fun topBar(): View = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            if (isCompact) addView(c.iconButton("☰", "Menu") {}.apply { background = null })
            addView(c.text(if (isCompact) "Đã tải" else "MeloVault · Xem trước", s.titleText * 1.15f, p.text, bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (isCompact) addView(c.iconButton("⌕", "Tìm kiếm") { searchToggle?.invoke() }.apply { background = null })
            addView(c.iconButton("✕", "Đóng xem trước") { onClose() })
        }

        private fun chipLabels(): List<String> = chipNames.mapIndexed { i, name ->
            if (i == 1) MeloComponentPolicy.chipLabel(name, samples.count { it.status == Status.DOWNLOADED }) else name
        }

        private fun visibleSamples(): List<Pair<Int, SongRowModel>> = samples.withIndex()
            .filter { (_, m) -> MeloComponentPolicy.matchesSearch(query, m.title, m.sourceTitle) }
            .filter { (_, m) ->
                when (chipIndex) {
                    1 -> m.status == Status.DOWNLOADED
                    3, 4, 5 -> MeloComponentPolicy.matchesSearch(chipNames[chipIndex], m.title, m.sourceTitle)
                    else -> true
                }
            }
            .map { it.index to it.value }

        private fun renderList() {
            list.removeAllViews()
            val rows = visibleSamples()
            val perPage = MeloDesignTokens.CarReferencePx.ROWS_PER_PAGE
            val shown = if (isCar) rows.drop(page * perPage).take(perPage) else rows
            if (shown.isEmpty()) {
                list.addView(c.text("Không có bài khớp", s.secondaryText, p.textSecondary))
            }
            shown.forEachIndexed { position, (index, model) ->
                val status = if (index == playingIndex) Status.PLAYING else model.status
                val number = if (isCar) page * perPage + position + 1 else null
                list.addView(c.songRow(model.copy(status = status, index = number),
                    onClick = { playingIndex = index; playing = true; renderList() },
                    onLongClick = { Toast.makeText(context, "Menu bài hát (mẫu)", Toast.LENGTH_SHORT).show() }
                ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = s.gap
                })
            }
            renderMini()
        }

        private fun renderMini() {
            val current = samples[playingIndex]
            miniHost.removeAllViews()
            val handle = c.miniPlayer(
                MeloComponents.MiniPlayerModel(current.title, current.sourceTitle, 83, current.durationSeconds, playing),
                showPrevious = layout == Layout.TABLET_PORTRAIT || isCar,
                onPlayPause = { playing = !playing; renderMini() },
                onNext = { playingIndex = (playingIndex + 1) % samples.size; renderList() },
                onPrevious = { playingIndex = (playingIndex - 1 + samples.size) % samples.size; renderList() },
                onOpen = { Toast.makeText(context, "Màn Đang phát: làm ở bước 6", Toast.LENGTH_SHORT).show() }
            )
            miniHost.addView(handle.view)
        }

        private fun pageButtons(): View = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val perPage = MeloDesignTokens.CarReferencePx.ROWS_PER_PAGE
            fun pageButton(label: String, delta: Int) = c.iconButton(label, label, height = s.pageButtonHeight) {
                val pages = maxOf(1, (visibleSamples().size + perPage - 1) / perPage)
                page = (page + delta).coerceIn(0, pages - 1)
                renderList()
            }.apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, s.titleText)
                layoutParams = LinearLayout.LayoutParams(0, s.pageButtonHeight, 1f).apply { marginEnd = s.gap }
            }
            addView(pageButton("▲ Cuộn trang lên", -1))
            addView(pageButton("▼ Cuộn trang xuống", 1))
        }
    }
}
