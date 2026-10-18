package com.bsdung.musicassistantcar.data.telegram

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.ColorRole
import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.Status
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Palette
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Sizes
import kotlin.math.abs
import kotlin.math.sin

/** Tag on the ⋮ button of a song row, so a ListView host can keep just that button clickable. */
internal const val MELO_SONG_ROW_MORE_TAG = "melo.songrow.more"

/**
 * Shared MeloVault building blocks (docs/ui-spec.md "Thành phần dùng chung").
 * Every size comes from [Sizes] in device pixels, so the same code renders the
 * Gọn / Điện thoại / Tablet / Car Mode profiles and the emulated device frame.
 */
internal class MeloComponents(
    private val context: Context,
    val sizes: Sizes,
    val palette: Palette = MeloDesignTokens.palette(sizes.profile)
) {

    data class SongRowModel(
        val title: String,
        val sourceTitle: String?,
        val durationSeconds: Int,
        val status: Status,
        val qualityLabel: String? = null,
        val progressPercent: Int? = null,
        val art: Bitmap? = null,
        val index: Int? = null
    )

    data class MiniPlayerModel(
        val title: String,
        val sourceTitle: String?,
        val positionSeconds: Int,
        val durationSeconds: Int,
        val playing: Boolean,
        val art: Bitmap? = null
    )

    private val isCar get() = sizes.profile == Profile.CAR
    private val isCompact get() = sizes.profile == Profile.COMPACT

    fun color(role: ColorRole): Int = when (role) {
        ColorRole.ACCENT -> palette.accent
        ColorRole.SECONDARY -> palette.textSecondary
        ColorRole.WARNING -> palette.warning
    }

    fun roundedBackground(color: Int, radius: Int = sizes.corner, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (stroke != null) setStroke(maxOf(1, sizes.gap / 8), stroke)
        }

    fun pill(color: Int, stroke: Int? = null): GradientDrawable = roundedBackground(color, 10_000, stroke)

    fun text(value: String, sizePx: Float, color: Int, bold: Boolean = false, maxLines: Int = 1): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
            setTextColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.maxLines = maxLines
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }

    /** Round or pill button with a glyph label; never smaller than the profile's touch target. */
    fun iconButton(
        glyph: String,
        description: String,
        primary: Boolean = false,
        width: Int = sizes.minTouch,
        height: Int = sizes.minTouch,
        onClick: (() -> Unit)? = null
    ): TextView = TextView(context).apply {
        text = glyph
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_PX, minOf(width, height) * 0.42f)
        setTextColor(if (primary) palette.onAccent else palette.text)
        background = if (primary) pill(palette.accent) else pill(palette.surfaceRaised)
        contentDescription = description
        minWidth = maxOf(width, sizes.minTouch)
        minHeight = maxOf(height, sizes.minTouch)
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(maxOf(width, sizes.minTouch), maxOf(height, sizes.minTouch))
        if (onClick != null) setOnClickListener { onClick() }
    }

    /** Square rounded cover; without art, a tinted tile with the channel's first letter. */
    fun artView(art: Bitmap?, sourceTitle: String?, title: String, sizePx: Int): View {
        if (art != null) {
            return ImageView(context).apply {
                setImageBitmap(art)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedBackground(palette.surfaceRaised)
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
        return TextView(context).apply {
            text = MeloComponentPolicy.initialOf(sourceTitle, title)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * 0.42f)
            setTextColor(palette.text)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBackground(
                MeloComponentPolicy.placeholderColor(sourceTitle ?: title, palette),
                radius = minOf(sizes.corner, sizePx / 4)
            )
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    /**
     * Song row: cover, title (2 lines, or 1 on Gọn), status icon + words, total
     * duration, ⋮ (not in Car Mode). Tap = play, long press = menu.
     */
    @SuppressLint("SetTextI18n")
    fun songRow(
        model: SongRowModel,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        onMore: (() -> Unit)? = null
    ): View {
        val status = MeloComponentPolicy.presentStatus(model.status, model.qualityLabel, model.progressPercent)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = sizes.rowHeight
            setPadding(sizes.gap, sizes.gap / 2, sizes.gap / 2, sizes.gap / 2)
            background = roundedBackground(
                if (status.highlightRow) palette.accentSoft else palette.surface,
                stroke = if (status.highlightRow) palette.accent else null
            )
            isClickable = true
            isFocusable = true
            contentDescription = listOfNotNull(model.title, status.label, model.sourceTitle).joinToString(", ")
            if (onClick != null) setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }
        if (isCar && model.index != null) {
            row.addView(text("${model.index}", sizes.secondaryText, palette.textSecondary, bold = true).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(sizes.minTouch * 6 / 10, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }
        row.addView(artView(model.art, model.sourceTitle, model.title, sizes.rowArt))

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sizes.gap * 3 / 2, 0, sizes.gap, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val iconSize = (sizes.secondaryText * 1.15f).toInt()
        if (isCompact) {
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            line.addView(MeloStatusIconView(context, model.status, color(status.color), model.progressPercent, palette.onAccent).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = sizes.gap }
                contentDescription = status.label
            })
            line.addView(text(model.title, sizes.titleText, palette.text, maxLines = 1).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            body.addView(line)
        } else {
            body.addView(text(model.title, sizes.titleText, palette.text, bold = status.highlightRow, maxLines = 2))
            val statusLine = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, sizes.gap / 2, 0, 0)
            }
            statusLine.addView(MeloStatusIconView(context, model.status, color(status.color), model.progressPercent, palette.onAccent).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = sizes.gap / 2 }
            })
            val detail = listOfNotNull(status.label, model.sourceTitle?.takeIf { it.isNotBlank() }).joinToString(" · ")
            statusLine.addView(text(detail, sizes.secondaryText, color(status.color)).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            body.addView(statusLine)
        }
        row.addView(body)

        TelegramSongDisplayPolicy.formatDuration(model.durationSeconds)?.let { duration ->
            row.addView(text(duration, sizes.secondaryText, palette.textSecondary).apply {
                setPadding(0, 0, sizes.gap / 2, 0)
            })
        }
        if (!isCar) {
            row.addView(TextView(context).apply {
                text = "⋮"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, sizes.titleText * 1.25f)
                setTextColor(palette.textSecondary)
                contentDescription = "Tùy chọn bài hát"
                tag = MELO_SONG_ROW_MORE_TAG
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(sizes.minTouch, sizes.minTouch)
                setOnClickListener { (onMore ?: onLongClick)?.invoke() }
            })
        }
        return row
    }

    /**
     * Same look as [songRow] (numbered, no ⋮) but built once and rebound in place, for
     * paged lists such as Car Mode where a page turn must not create new views.
     */
    inner class SongRowHolder internal constructor() {
        private val indexView = text("", sizes.secondaryText, palette.textSecondary, bold = true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(sizes.minTouch * 6 / 10, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        private val artImage = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBackground(palette.surfaceRaised)
            clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val artInitial = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sizes.rowArt * 0.42f)
            setTextColor(palette.text)
            typeface = Typeface.DEFAULT_BOLD
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val title = text("", sizes.titleText, palette.text, maxLines = 2)
        private val iconSize = (sizes.secondaryText * 1.15f).toInt()
        private val icon = MeloStatusIconView(context, Status.TELEGRAM, palette.textSecondary, null, palette.onAccent)
        private val detail = text("", sizes.secondaryText, palette.textSecondary)
        private val duration = text("", sizes.secondaryText, palette.textSecondary).apply { setPadding(0, 0, sizes.gap / 2, 0) }
        private var bound: SongRowModel? = null

        val view: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = sizes.rowHeight
            setPadding(sizes.gap, sizes.gap / 2, sizes.gap / 2, sizes.gap / 2)
            isClickable = true
            isFocusable = true
            addView(indexView)
            addView(FrameLayout(context).apply {
                addView(artImage, FrameLayout.LayoutParams(sizes.rowArt, sizes.rowArt))
                addView(artInitial, FrameLayout.LayoutParams(sizes.rowArt, sizes.rowArt))
            }, LinearLayout.LayoutParams(sizes.rowArt, sizes.rowArt))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(sizes.gap * 3 / 2, 0, sizes.gap, 0)
                addView(title)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, sizes.gap / 2, 0, 0)
                    addView(icon, LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = sizes.gap / 2 })
                    addView(detail, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(duration)
        }

        fun bind(model: SongRowModel, onClick: () -> Unit, onLongClick: () -> Unit) {
            view.setOnClickListener { onClick() }
            view.setOnLongClickListener { onLongClick(); true }
            if (model == bound) return
            bound = model
            val status = MeloComponentPolicy.presentStatus(model.status, model.qualityLabel, model.progressPercent)
            view.background = roundedBackground(
                if (status.highlightRow) palette.accentSoft else palette.surface,
                stroke = if (status.highlightRow) palette.accent else null
            )
            view.contentDescription = listOfNotNull(model.title, status.label, model.sourceTitle).joinToString(", ")
            indexView.text = model.index?.toString().orEmpty()
            if (model.art != null) {
                artImage.setImageBitmap(model.art)
                artImage.visibility = View.VISIBLE
                artInitial.visibility = View.GONE
            } else {
                artImage.setImageDrawable(null)
                artImage.visibility = View.GONE
                artInitial.text = MeloComponentPolicy.initialOf(model.sourceTitle, model.title)
                artInitial.background = roundedBackground(
                    MeloComponentPolicy.placeholderColor(model.sourceTitle ?: model.title, palette),
                    radius = minOf(sizes.corner, sizes.rowArt / 4)
                )
                artInitial.visibility = View.VISIBLE
            }
            title.text = model.title
            title.setTypeface(null, if (status.highlightRow) Typeface.BOLD else Typeface.NORMAL)
            icon.set(model.status, color(status.color), model.progressPercent)
            detail.text = listOfNotNull(status.label, model.sourceTitle?.takeIf { it.isNotBlank() }).joinToString(" · ")
            detail.setTextColor(color(status.color))
            duration.text = TelegramSongDisplayPolicy.formatDuration(model.durationSeconds).orEmpty()
        }
    }

    fun songRowHolder(): SongRowHolder = SongRowHolder()

    fun chip(label: String, selected: Boolean, onClick: (() -> Unit)? = null): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sizes.secondaryText)
            setTextColor(if (selected) palette.onAccent else palette.text)
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            background = if (selected) pill(palette.accent) else pill(palette.surfaceRaised, palette.border)
            // A chip is one line; when space is short its row moves, the text never wraps out of the pill.
            maxLines = 1
            setPadding(sizes.gap * 2, 0, sizes.gap * 2, 0)
            minHeight = sizes.minTouch
            isClickable = true
            isFocusable = true
            isSelected = selected
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, sizes.minTouch).apply {
                marginEnd = sizes.gap
            }
            if (onClick != null) setOnClickListener { onClick() }
        }

    /** Horizontally scrolling single-choice chip row. */
    fun chipRow(labels: List<String>, selectedIndex: Int, onSelect: ((Int) -> Unit)? = null): HorizontalScrollView {
        val strip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        }
        fun render(selected: Int) {
            strip.removeAllViews()
            labels.forEachIndexed { index, label ->
                strip.addView(chip(label, index == selected) {
                    render(index)
                    onSelect?.invoke(index)
                })
            }
        }
        render(selectedIndex)
        return scroller
    }

    /** Search field: hint with the real song count, instant results, filter button at the end. */
    fun searchBar(totalSongs: Int?, onQuery: ((String) -> Unit)? = null, onFilter: (() -> Unit)? = null): LinearLayout {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pill(palette.surface, palette.border)
            setPadding(sizes.gap * 2, 0, sizes.gap / 2, 0)
            minimumHeight = sizes.minTouch
        }
        bar.addView(text("⌕", sizes.titleText * 1.2f, palette.textSecondary).apply {
            setPadding(0, 0, sizes.gap, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        bar.addView(EditText(context).apply {
            hint = MeloComponentPolicy.searchHint(totalSongs)
            setHintTextColor(palette.textSecondary)
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sizes.titleText)
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(0, 0, 0, 0)
            minHeight = sizes.minTouch
            layoutParams = LinearLayout.LayoutParams(0, sizes.minTouch, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { onQuery?.invoke(s?.toString().orEmpty()) }
            })
        })
        bar.addView(iconButton("☰", "Bộ lọc", width = sizes.minTouch, height = sizes.minTouch) { onFilter?.invoke() }.apply {
            background = null
            setTextColor(palette.textSecondary)
        })
        return bar
    }

    class MiniPlayerHandle(
        val view: View,
        private val progress: ProgressBar,
        private val time: TextView,
        private val playButton: TextView
    ) {
        fun update(positionSeconds: Int, durationSeconds: Int, playing: Boolean) {
            progress.max = maxOf(durationSeconds, 1)
            progress.progress = positionSeconds.coerceIn(0, progress.max)
            time.text = MeloComponentPolicy.progressText(positionSeconds, durationSeconds)
            playButton.text = if (playing) "❚❚" else "▶"
            playButton.contentDescription = if (playing) "Dừng" else "Phát"
        }
    }

    /**
     * Bottom mini player: cover, title, thin progress + time, Play/Pause and Next
     * (Previous too on tablet portrait). Tap or swipe up opens Now Playing.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun miniPlayer(
        model: MiniPlayerModel,
        showPrevious: Boolean = false,
        onPlayPause: (() -> Unit)? = null,
        onNext: (() -> Unit)? = null,
        onPrevious: (() -> Unit)? = null,
        onOpen: (() -> Unit)? = null
    ): MiniPlayerHandle {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(palette.surfaceRaised, stroke = palette.border)
            minimumHeight = sizes.miniPlayerHeight
            isClickable = true
            isFocusable = true
            contentDescription = "Đang phát: ${model.title}. Bấm để mở màn Đang phát"
        }
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            progressDrawable = thinProgressDrawable()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sizes.progressHeight)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(sizes.gap, sizes.gap / 2, sizes.gap, sizes.gap / 2)
        }
        val artSize = maxOf(sizes.rowArt * 3 / 4, sizes.miniPlayerHeight - sizes.gap * 2)
        content.addView(artView(model.art, model.sourceTitle, model.title, artSize))
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sizes.gap * 3 / 2, 0, sizes.gap, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(text(model.title, sizes.titleText, palette.text, bold = true))
        val time = text("", sizes.secondaryText, palette.textSecondary)
        texts.addView(time)
        content.addView(texts)
        if (showPrevious) {
            content.addView(iconButton("⏮", "Bài trước") { onPrevious?.invoke() }.apply {
                background = null
            })
        }
        val play = iconButton("▶", "Phát", primary = true,
            width = sizes.miniPlayerHeight - sizes.gap, height = sizes.miniPlayerHeight - sizes.gap) { onPlayPause?.invoke() }
        content.addView(play)
        content.addView(iconButton("⏭", "Bài kế") { onNext?.invoke() }.apply { background = null })
        root.addView(progress)
        root.addView(content)

        val open = onOpen
        if (open != null) {
            root.setOnClickListener { open() }
            val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (velocityY < 0 && abs(velocityY) > abs(velocityX)) { open(); return true }
                    return false
                }
            })
            // A swipe up consumes its ACTION_UP so the tap listener does not open a second time.
            root.setOnTouchListener { _, event -> detector.onTouchEvent(event) && event.action == MotionEvent.ACTION_UP }
        }
        return MiniPlayerHandle(root, progress, time, play).also {
            it.update(model.positionSeconds, model.durationSeconds, model.playing)
        }
    }

    internal fun thinProgressDrawable(): LayerDrawable {
        val track = GradientDrawable().apply { setColor(palette.border) }
        val fill = ClipDrawable(GradientDrawable().apply { setColor(palette.accent) }, Gravity.START, ClipDrawable.HORIZONTAL)
        return LayerDrawable(arrayOf(track, fill)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
    }

    /** Section title used by preview screens. */
    fun sectionTitle(value: String): TextView = text(value, sizes.titleText, palette.text, bold = true).apply {
        setPadding(0, sizes.gap * 2, 0, sizes.gap)
    }

    fun spacer(heightPx: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, heightPx)
    }

    fun frame(): FrameLayout = FrameLayout(context)
}

/** Status glyph drawn on canvas so it never depends on emoji fonts: bars, check, ring, cloud, triangle. */
internal class MeloStatusIconView(
    context: Context,
    private var status: Status,
    private var tint: Int,
    private var progressPercent: Int? = null,
    private val onAccent: Int = MeloDesignTokens.PALETTE.onAccent
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
    private val path = Path()
    private val rect = RectF()
    private var animating = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Rebind in place (reused rows); restarts the bars animation only for the playing song. */
    fun set(status: Status, tint: Int, progressPercent: Int?) {
        if (this.status == status && this.tint == tint && this.progressPercent == progressPercent) return
        this.status = status
        this.tint = tint
        this.progressPercent = progressPercent
        paint.color = tint
        val wasAnimating = animating
        animating = status == Status.PLAYING && isAttachedToWindow
        if (animating && !wasAnimating) postInvalidateOnAnimation() else invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animating = status == Status.PLAYING
        if (animating) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        animating = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val stroke = maxOf(1.5f, w / 9f)
        when (status) {
            Status.PLAYING -> {
                paint.style = Paint.Style.FILL
                val t = System.currentTimeMillis() / 140.0
                val barW = w / 5f
                for (i in 0 until 3) {
                    val level = 0.35f + 0.65f * ((sin(t + i * 1.7) + 1.0) / 2.0).toFloat()
                    val left = barW * 0.5f + i * barW * 1.6f
                    rect.set(left, h * (1f - level), left + barW, h)
                    canvas.drawRoundRect(rect, barW / 3f, barW / 3f, paint)
                }
                // ~6 fps is enough to read as "moving" and keeps the screen mostly idle (Sony test F1).
                if (animating && isShown) postInvalidateDelayed(BARS_FRAME_MS)
            }
            Status.PAUSED -> {
                paint.style = Paint.Style.FILL
                val barW = w / 5f
                rect.set(w * 0.22f, h * 0.12f, w * 0.22f + barW, h * 0.88f)
                canvas.drawRoundRect(rect, barW / 3f, barW / 3f, paint)
                rect.set(w * 0.78f - barW, h * 0.12f, w * 0.78f, h * 0.88f)
                canvas.drawRoundRect(rect, barW / 3f, barW / 3f, paint)
            }
            Status.DOWNLOADED -> {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(w / 2f, h / 2f, minOf(w, h) / 2f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = stroke
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = onAccent
                path.reset()
                path.moveTo(w * 0.28f, h * 0.52f)
                path.lineTo(w * 0.44f, h * 0.68f)
                path.lineTo(w * 0.73f, h * 0.36f)
                canvas.drawPath(path, paint)
                paint.color = tint
            }
            Status.DOWNLOADING, Status.OPENING -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = stroke
                paint.strokeCap = Paint.Cap.ROUND
                rect.set(stroke, stroke, w - stroke, h - stroke)
                paint.alpha = 70
                canvas.drawArc(rect, 0f, 360f, false, paint)
                paint.alpha = 255
                val sweep = 360f * ((progressPercent ?: 25).coerceIn(0, 100) / 100f)
                canvas.drawArc(rect, -90f, maxOf(sweep, 12f), false, paint)
            }
            Status.TELEGRAM -> {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(w * 0.36f, h * 0.58f, w * 0.20f, paint)
                canvas.drawCircle(w * 0.58f, h * 0.46f, w * 0.26f, paint)
                rect.set(w * 0.16f, h * 0.58f, w * 0.88f, h * 0.80f)
                canvas.drawRoundRect(rect, h * 0.11f, h * 0.11f, paint)
            }
            Status.ERROR -> {
                paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(w / 2f, h * 0.08f)
                path.lineTo(w * 0.96f, h * 0.90f)
                path.lineTo(w * 0.04f, h * 0.90f)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = onAccent
                rect.set(w * 0.455f, h * 0.36f, w * 0.545f, h * 0.64f)
                canvas.drawRect(rect, paint)
                canvas.drawCircle(w / 2f, h * 0.76f, w * 0.055f, paint)
                paint.color = tint
            }
        }
    }

    private companion object {
        const val BARS_FRAME_MS = 160L
    }
}
