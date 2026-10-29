package com.bsdung.musicassistantcar.data.telegram

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
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
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
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
import android.widget.SeekBar
import android.widget.TextView
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.ColorRole
import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.Status
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Palette
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Sizes
import kotlin.math.abs
import kotlin.math.sin

/** Tag on the ⋮ button of a song row, so a ListView host can keep just that button clickable. */
internal const val MELO_SONG_ROW_MORE_TAG = "melo.songrow.more"
internal const val MELO_SONG_ROW_SAVE_TAG = "melo.songrow.save"

/** Tag on the cover frame of a teal-theme song row: which cover ([MeloRowArtPolicy] key) belongs there. */
internal class MeloArtTag(val key: String)

/** The cover frame ([MeloArtTag]) inside a list row, or null. Shared by the teal library pane and the Gọn list (E99). */
internal fun findRowArtFrame(v: View?, depth: Int = 0): FrameLayout? {
    if (v == null || depth > 4) return null
    if (v is FrameLayout && v.tag is MeloArtTag) return v
    if (v is ViewGroup) for (i in 0 until v.childCount) findRowArtFrame(v.getChildAt(i), depth + 1)?.let { return it }
    return null
}

/**
 * Shared MeloVault building blocks (docs/ui-spec.md "Thành phần dùng chung").
 * Every size comes from [Sizes] in device pixels, so the same code renders the
 * Gọn / Điện thoại / Tablet / Car Mode profiles and the emulated device frame.
 */
internal class MeloComponents(
    private val context: Context,
    val sizes: Sizes,
    /** E98: the teal look only where [MeloThemePolicy.appliesTo]; every other caller keeps the default and draws as before. */
    val theme: MeloThemePolicy.ColorTheme = MeloThemePolicy.ColorTheme.CURRENT,
    val palette: Palette = MeloDesignTokens.palette(sizes.profile, theme)
) {
    val teal: Boolean get() = theme == MeloThemePolicy.ColorTheme.TEAL_BRIGHT


    data class SongRowModel(
        val title: String,
        val sourceTitle: String?,
        val durationSeconds: Int,
        val status: Status,
        val qualityLabel: String? = null,
        val progressPercent: Int? = null,
        val art: Bitmap? = null,
        val index: Int? = null,
        /** A "Nhạc trên máy" song: the status line says "Trên máy" instead of "Đã tải". */
        val local: Boolean = false,
        /** E98 teal theme: identity of the row's cover, so a cover that arrives later finds its row. */
        val artKey: String? = null,
        /** Library opt-in; null keeps the existing Compact/Car/queue metadata. Empty means no tags. */
        val browseMetadata: String? = null,
        /** Explicit list opt-in only; Compact, Car and queue callers keep null. */
        val rowSave: MeloSavePolicy.ButtonState? = null,
        val statusTextOverride: String? = null
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
    fun artView(art: Bitmap?, sourceTitle: String?, title: String, sizePx: Int, radius: Int = sizes.corner): View {
        if (art != null) {
            return ImageView(context).apply {
                setImageBitmap(art)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedBackground(palette.surfaceRaised, radius)
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
                radius = minOf(radius, sizePx / 4)
            )
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    /**
     * E98 teal theme: a cover tile whose picture can arrive after the row was built. A frame tagged
     * with [MeloArtTag] holds the picture (hidden until there is one) over the letter tile.
     */
    fun rowArtView(art: Bitmap?, sourceTitle: String?, title: String, sizePx: Int, key: String): FrameLayout {
        val radius = minOf(sizes.corner * 3 / 4, sizePx / 4)
        val frame = FrameLayout(context).apply {
            tag = MeloArtTag(key)
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBackground(palette.surfaceRaised, radius)
            clipToOutline = true
            visibility = View.GONE
        }, FrameLayout.LayoutParams(sizePx, sizePx))
        frame.addView(TextView(context).apply {
            text = MeloComponentPolicy.initialOf(sourceTitle, title)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * 0.42f)
            setTextColor(palette.text)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBackground(MeloComponentPolicy.placeholderColor(sourceTitle ?: title, palette), radius)
        }, FrameLayout.LayoutParams(sizePx, sizePx))
        applyRowArt(frame, art)
        return frame
    }

    /** Whether a [rowArtView] frame currently shows a picture over its letter tile. */
    fun rowArtShowing(frame: FrameLayout): Boolean = (frame.getChildAt(0) as? ImageView)?.visibility == View.VISIBLE

    /**
     * Puts [bmp] into a [rowArtView] frame in place (no relayout, same bitmap not set twice).
     * A null [bmp] never takes a picture away. With [animate] the letter tile fades out when the
     * first picture appears. Returns what the call did to the row.
     */
    fun applyRowArt(frame: FrameLayout, bmp: Bitmap?, animate: Boolean = false): MeloRowArtPolicy.ArtChange {
        val image = frame.getChildAt(0) as? ImageView ?: return MeloRowArtPolicy.ArtChange.NONE
        val tile = frame.getChildAt(1)
        val change = MeloRowArtPolicy.artChange(image.visibility == View.VISIBLE, image.tag === bmp, bmp != null)
        if (change == MeloRowArtPolicy.ArtChange.NONE) return change
        image.setImageBitmap(bmp)
        image.tag = bmp
        if (change == MeloRowArtPolicy.ArtChange.APPEAR) {
            image.visibility = View.VISIBLE
            if (animate) {
                tile.animate().alpha(0f).setDuration(MeloRowArtPolicy.FADE_MS)
                    .withEndAction { tile.visibility = View.GONE; tile.alpha = 1f }
            } else {
                tile.visibility = View.GONE
            }
        }
        return change
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
        onMore: (() -> Unit)? = null,
        onSave: (() -> Unit)? = null
    ): View {
        if (teal && !isCompact && !isCar) return tealSongRow(model, onClick, onLongClick, onMore, onSave)
        val status = MeloComponentPolicy.presentStatus(model.status, model.qualityLabel, model.progressPercent, model.local).let { it.copy(label = model.statusTextOverride ?: it.label) }
        // E96 P5B3: lookalike/combining characters in channel names are drawn by a fallback font; show their plain form.
        val shownTitle = DisplayTextPolicy.clean(model.title)
        val shownSource = DisplayTextPolicy.cleanOrNull(model.sourceTitle)
        val metadata = model.browseMetadata?.let(DisplayTextPolicy::clean) ?: shownSource
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
            contentDescription = listOfNotNull(shownTitle, status.label, metadata).filter { it.isNotBlank() }.joinToString(", ")
            if (onClick != null) setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }
        if (isCar && model.index != null) {
            row.addView(text("${model.index}", sizes.secondaryText, palette.textSecondary, bold = true).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(sizes.minTouch * 6 / 10, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }
        if ((teal || isCompact) && model.artKey != null) {
            row.addView(rowArtView(model.art, shownSource, shownTitle, sizes.rowArt, model.artKey))
        } else {
            row.addView(artView(model.art, shownSource, shownTitle, sizes.rowArt))
        }

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
            line.addView(text(shownTitle, sizes.titleText, palette.text, maxLines = 1).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            body.addView(line)
        } else {
            body.addView(text(shownTitle, sizes.titleText, palette.text, bold = status.highlightRow, maxLines = 2))
            val statusLine = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, sizes.gap / 2, 0, 0)
            }
            statusLine.addView(MeloStatusIconView(context, model.status, color(status.color), model.progressPercent, palette.onAccent).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = sizes.gap / 2 }
            })
            val detail = listOfNotNull(status.label, metadata?.takeIf { it.isNotBlank() }).joinToString(" · ")
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
            rowSaveButton(model, onSave)?.let(row::addView)
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
            val status = MeloComponentPolicy.presentStatus(model.status, model.qualityLabel, model.progressPercent, model.local).let { it.copy(label = model.statusTextOverride ?: it.label) }
            view.background = roundedBackground(
                if (status.highlightRow) palette.accentSoft else palette.surface,
                stroke = if (status.highlightRow) palette.accent else null
            )
            val shownTitle = DisplayTextPolicy.clean(model.title)
            val shownSource = DisplayTextPolicy.cleanOrNull(model.sourceTitle)
            view.contentDescription = listOfNotNull(shownTitle, status.label, shownSource).joinToString(", ")
            indexView.text = model.index?.toString().orEmpty()
            if (model.art != null) {
                artImage.setImageBitmap(model.art)
                artImage.visibility = View.VISIBLE
                artInitial.visibility = View.GONE
            } else {
                artImage.setImageDrawable(null)
                artImage.visibility = View.GONE
                artInitial.text = MeloComponentPolicy.initialOf(shownSource, shownTitle)
                artInitial.background = roundedBackground(
                    MeloComponentPolicy.placeholderColor(shownSource ?: shownTitle, palette),
                    radius = minOf(sizes.corner, sizes.rowArt / 4)
                )
                artInitial.visibility = View.VISIBLE
            }
            title.text = shownTitle
            title.setTypeface(null, if (status.highlightRow) Typeface.BOLD else Typeface.NORMAL)
            icon.set(model.status, color(status.color), model.progressPercent)
            detail.text = listOfNotNull(status.label, shownSource?.takeIf { it.isNotBlank() }).joinToString(" · ")
            detail.setTextColor(color(status.color))
            duration.text = TelegramSongDisplayPolicy.formatDuration(model.durationSeconds).orEmpty()
        }
    }

    fun songRowHolder(): SongRowHolder = SongRowHolder()

    // ---- E98 teal theme ----------------------------------------------------------------------------
    // Everything below is used only when [teal] is true, so the current theme draws exactly as before.

    fun gradient(
        start: Int,
        end: Int,
        radius: Int = sizes.corner,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TOP_BOTTOM,
        stroke: Int? = null
    ): GradientDrawable = GradientDrawable(orientation, intArrayOf(start, end)).apply {
        cornerRadius = radius.toFloat()
        if (stroke != null) setStroke(maxOf(1, sizes.gap / 8), stroke)
    }

    /** A 24dp vector from res/drawable drawn in one colour at [sizePx]. */
    fun vectorIcon(resId: Int, sizePx: Int, tint: Int): ImageView = ImageView(context).apply {
        setImageResource(resId)
        imageTintList = ColorStateList.valueOf(tint)
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** A vector as a start drawable of a TextView: sized to [sizePx] and tinted. Null when the resource is missing. */
    fun iconDrawable(resId: Int, sizePx: Int, tint: Int): android.graphics.drawable.Drawable? =
        androidx.core.content.ContextCompat.getDrawable(context, resId)?.mutate()?.apply {
            setBounds(0, 0, sizePx, sizePx)
            setTint(tint)
        }

    /** A round touch target around a vector, e.g. ⚙ and the filter button. */
    fun vectorButton(resId: Int, description: String, tint: Int, onClick: () -> Unit): FrameLayout {
        val icon = (sizes.minTouch * 0.5f).toInt()
        return FrameLayout(context).apply {
            contentDescription = description
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(sizes.minTouch, sizes.minTouch)
            addView(vectorIcon(resId, icon, tint), FrameLayout.LayoutParams(icon, icon, Gravity.CENTER))
            setOnClickListener { onClick() }
        }
    }

    /** "Melo" in the text colour, "Vault" in the accent. */
    fun logo(sizePx: Float): TextView = TextView(context).apply {
        val full = SpannableString("MeloVault")
        full.setSpan(ForegroundColorSpan(palette.text), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        full.setSpan(ForegroundColorSpan(palette.accent), 4, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text = full
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        maxLines = 1
    }

    /** Outlined pill for the current track's quality, e.g. "MP3 · 44.1 kHz · 320 kbps". */
    fun qualityPill(label: String): TextView = text(label, sizes.secondaryText, palette.accent, bold = true).apply {
        gravity = Gravity.CENTER
        background = pill(0x00000000, palette.accent)
        setPadding(sizes.gap * 2, sizes.gap / 2, sizes.gap * 2, sizes.gap / 2)
    }

    /** One of the four menu tabs: icon + label; the selected one is the accent gradient. [stacked] puts the icon above a short label. */
    fun tabCard(iconRes: Int, label: String, selected: Boolean, stacked: Boolean, onClick: () -> Unit): View {
        val tint = if (selected) palette.onAccent else palette.text
        val icon = (sizes.secondaryText * 1.5f).toInt()
        return LinearLayout(context).apply {
            orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = sizes.minTouch
            setPadding(sizes.gap, sizes.gap / 2, sizes.gap, sizes.gap / 2)
            background = if (selected) {
                gradient(palette.accent, palette.accentEnd, sizes.corner, GradientDrawable.Orientation.LEFT_RIGHT)
            } else {
                roundedBackground(palette.surfaceRaised, sizes.corner, stroke = palette.border)
            }
            isClickable = true
            isFocusable = true
            isSelected = selected
            contentDescription = label + if (selected) ", đang chọn" else ""
            addView(vectorIcon(iconRes, icon, tint))
            addView(text(label, sizes.secondaryText, tint, bold = selected).apply {
                gravity = Gravity.CENTER
                if (stacked) setPadding(0, sizes.gap / 4, 0, 0) else setPadding(sizes.gap / 2, 0, 0, 0)
            })
            setOnClickListener { onClick() }
        }
    }

    /** Row shell of the teal theme: a card, the playing song on an accent gradient. */
    private fun tealSongRow(
        model: SongRowModel,
        onClick: (() -> Unit)?,
        onLongClick: (() -> Unit)?,
        onMore: (() -> Unit)?,
        onSave: (() -> Unit)?
    ): View {
        val status = MeloComponentPolicy.presentStatus(model.status, model.qualityLabel, model.progressPercent, model.local).let { it.copy(label = model.statusTextOverride ?: it.label) }
        val shownTitle = DisplayTextPolicy.clean(model.title)
        val shownSource = DisplayTextPolicy.cleanOrNull(model.sourceTitle)
        val metadata = model.browseMetadata?.let(DisplayTextPolicy::clean) ?: shownSource
        val playing = status.highlightRow
        val radius = sizes.corner * 7 / 8
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = sizes.rowHeight
            setPadding(sizes.gap, sizes.gap / 2, sizes.gap / 2, sizes.gap / 2)
            background = if (playing) {
                gradient(
                    palette.accentSoft, MeloComponentPolicy.blend(palette.accentSoft, palette.accent, 0.35f), radius,
                    GradientDrawable.Orientation.LEFT_RIGHT, stroke = palette.accent
                )
            } else {
                roundedBackground(palette.surface, radius, stroke = palette.border)
            }
            isClickable = true
            isFocusable = true
            contentDescription = listOfNotNull(shownTitle, status.label, metadata).filter { it.isNotBlank() }.joinToString(", ")
            if (onClick != null) setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }
        row.addView(
            if (model.artKey != null) rowArtView(model.art, shownSource, shownTitle, sizes.rowArt, model.artKey)
            else artView(model.art, shownSource, shownTitle, sizes.rowArt)
        )
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sizes.gap * 3 / 2, 0, sizes.gap, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        body.addView(text(shownTitle, sizes.titleText, if (playing) palette.accent else palette.text, bold = true, maxLines = 2))
        body.addView(text((model.statusTextOverride?.let { listOfNotNull(it, metadata).filter { part -> part.isNotBlank() }.joinToString(" · ") } ?: MeloComponentPolicy.themedSecondLine(model.status, status.label, metadata)), sizes.secondaryText,
            if (model.status == Status.ERROR) palette.warning else palette.textSecondary).apply {
            setPadding(0, sizes.gap / 2, 0, 0)
        })
        row.addView(body)

        val iconSize = (sizes.secondaryText * 1.5f).toInt()
        val tint = when (model.status) {
            Status.DOWNLOADED -> palette.statusOk
            Status.ERROR -> palette.warning
            Status.TELEGRAM -> palette.textSecondary
            else -> palette.accent
        }
        row.addView(MeloStatusIconView(context, model.status, tint, model.progressPercent, palette.onAccent).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = sizes.gap }
            contentDescription = status.label
        })
        TelegramSongDisplayPolicy.formatDuration(model.durationSeconds)?.let { duration ->
            row.addView(text(duration, sizes.secondaryText, palette.textSecondary).apply {
                setPadding(0, 0, sizes.gap / 2, 0)
            })
        }
        rowSaveButton(model, onSave)?.let(row::addView)
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
        return row
    }

    private fun rowSaveButton(model: SongRowModel, onSave: (() -> Unit)?): View? {
        val state = model.rowSave ?: return null
        if (isCompact || isCar) return null
        return text(state.label, sizes.titleText, if (state.saved) palette.accent else palette.textSecondary).apply {
            tag = MELO_SONG_ROW_SAVE_TAG
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(sizes.minTouch, sizes.minTouch)
            isFocusable = true
            contentDescription = state.contentDescription
            androidx.core.view.ViewCompat.setStateDescription(this, state.stateDescription)
            setOnClickListener { onSave?.invoke() }
        }
    }

    /** The "☁ Đồng bộ: 27.971 bài ●" line with the ⚙ button at its end. */
    inner class SyncRow internal constructor(onSettings: () -> Unit) {
        private val label = text("", sizes.secondaryText, palette.textSecondary, maxLines = 2)
        private val dot = View(context)
        private var lastDot: MeloThemePolicy.SyncDot? = null
        val view: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val cloud = (sizes.secondaryText * 1.4f).toInt()
            addView(vectorIcon(R.drawable.ic_cloud, cloud, palette.textSecondary).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = sizes.gap
            })
            addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val d = sizes.gap
            addView(dot, LinearLayout.LayoutParams(d, d).apply { marginStart = sizes.gap; marginEnd = sizes.gap / 2 })
            addView(vectorButton(R.drawable.ic_settings, "Cài đặt", palette.text) { onSettings() })
        }

        fun update(headline: CharSequence, state: MeloThemePolicy.SyncDot) {
            if (label.text.toString() != headline.toString()) label.text = headline
            if (state == lastDot) return
            lastDot = state
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(when (state) {
                    MeloThemePolicy.SyncDot.OK -> palette.statusOk
                    MeloThemePolicy.SyncDot.SYNCING -> 0xFFFFC857.toInt()
                    MeloThemePolicy.SyncDot.OFFLINE -> palette.textSecondary
                })
            }
            dot.contentDescription = when (state) {
                MeloThemePolicy.SyncDot.OK -> "Đã đồng bộ"
                MeloThemePolicy.SyncDot.SYNCING -> "Đang đồng bộ"
                MeloThemePolicy.SyncDot.OFFLINE -> "Chưa kết nối"
            }
        }
    }

    fun syncRow(onSettings: () -> Unit): SyncRow = SyncRow(onSettings)

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

    /**
     * Library source card: round letter badge + name + "25.104 bài · đã tải 57". Selected = soft
     * accent fill and accent border. The badge is a letter, not an emoji (emoji break on Android 12).
     */
    fun sourceCard(card: MeloSourceCardPolicy.Card, heightPx: Int, onClick: () -> Unit): View {
        val badgeSize = (heightPx * 0.46f).toInt()
        val badge = TextView(context).apply {
            text = MeloSourceCardPolicy.badgeOf(card.kind)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, badgeSize * 0.5f)
            setTextColor(palette.onAccent)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            background = pill(palette.accent)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(card.title, sizes.titleText, palette.text, bold = true))
            // Two lines so "25.105 bài · đã tải 67" is never cut with "…" on a ~213dp card.
            addView(text(card.detail, sizes.secondaryText, palette.textSecondary, maxLines = 2).apply {
                setPadding(0, sizes.gap / 2, 0, 0)
            })
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = heightPx
            setPadding(sizes.gap * 2, sizes.gap, sizes.gap * 2, sizes.gap)
            background = if (card.selected) roundedBackground(palette.accentSoft, stroke = palette.accent)
            else roundedBackground(palette.surface, stroke = palette.border)
            isClickable = true
            isFocusable = true
            contentDescription = card.contentDescription
            addView(badge, LinearLayout.LayoutParams(badgeSize, badgeSize).apply { marginEnd = sizes.gap * 2 })
            addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { onClick() }
        }
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

    /** The search bar and its text field, so a caller never has to dig the field out of the bar's children. */
    class SearchBar(val view: LinearLayout, val field: EditText)

    /** Search field: hint with the real song count, instant results, filter button at the end. */
    fun searchBar(totalSongs: Int?, onQuery: ((String) -> Unit)? = null, onFilter: (() -> Unit)? = null): LinearLayout =
        searchBarWithField(totalSongs, onQuery, onFilter).view

    fun searchBarWithField(totalSongs: Int?, onQuery: ((String) -> Unit)? = null, onFilter: (() -> Unit)? = null): SearchBar {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = if (teal) roundedBackground(palette.surface, sizes.corner, stroke = palette.border)
            else pill(palette.surface, palette.border)
            setPadding(sizes.gap * 2, 0, sizes.gap / 2, 0)
            minimumHeight = sizes.minTouch
        }
        if (teal) {
            bar.addView(vectorIcon(R.drawable.ic_search, (sizes.titleText * 1.2f).toInt(), palette.textSecondary).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = sizes.gap
            })
        } else {
            bar.addView(text("⌕", sizes.titleText * 1.2f, palette.textSecondary).apply {
                setPadding(0, 0, sizes.gap, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        }
        val field = EditText(context).apply {
            hint = if (teal) "Tìm bài hát theo tên..." else MeloComponentPolicy.searchHint(totalSongs)
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
        }
        bar.addView(field)
        if (teal) {
            bar.addView(vectorButton(R.drawable.ic_tune, "Bộ lọc", palette.textSecondary) { onFilter?.invoke() })
        } else {
            bar.addView(iconButton("☰", "Bộ lọc", width = sizes.minTouch, height = sizes.minTouch) { onFilter?.invoke() }.apply {
                background = null
                setTextColor(palette.textSecondary)
            })
        }
        return SearchBar(bar, field)
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

/**
 * E103: a thin seek line with a round dot, shared by the Studio Deck dock and the two-pane player
 * (it replaces the display-only E98 `MeloThumbProgress`). A real [SeekBar] so touch, DPAD / rotary
 * keys and TalkBack work. A drag only previews the target ([onPreview]); the seek happens when the
 * finger lifts. A key or TalkBack change ([fromUser] without a drag) seeks at once. While the finger
 * is down the 1 s refresh in [set] leaves the dot alone.
 */
internal class MeloSeekLine(
    context: Context,
    trackColor: Int,
    fillColor: Int,
    thumbColor: Int,
    trackPx: Int,
    thumbPx: Int,
    touchPx: Int,
    /** The target position while dragging, null once the finger lifts or the drag is cancelled. */
    private val onPreview: (Long?) -> Unit,
    private val onSeek: (Long) -> Unit
) : SeekBar(context) {
    private var durationMs = 0L
    private var dragging = false

    init {
        max = MeloNowPlayingPolicy.SEEK_BAR_MAX
        val radius = trackPx / 2f
        val track = GradientDrawable().apply { setColor(trackColor); cornerRadius = radius }
        val fill = ClipDrawable(GradientDrawable().apply { setColor(fillColor); cornerRadius = radius }, Gravity.START, ClipDrawable.HORIZONTAL)
        // SeekBar stretches the track to its style height (~16 dp); fixed layer height + gravity keeps it trackPx thick and centred.
        progressDrawable = LayerDrawable(arrayOf(track, fill)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
            for (i in 0..1) {
                setLayerHeight(i, trackPx)
                setLayerGravity(i, Gravity.CENTER_VERTICAL or Gravity.FILL_HORIZONTAL)
            }
        }
        // The dot has the same colour as the played part, so a thin ring in the track colour keeps it visible on top of it.
        val ringPx = maxOf(1, thumbPx / 8)
        thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(thumbColor)
            setStroke(ringPx, trackColor)
            setSize(thumbPx, thumbPx)
        }
        splitTrack = false
        minimumHeight = touchPx
        setPadding(thumbPx / 2, 0, thumbPx / 2, 0)
        contentDescription = "Thanh tua bài hát"
        isEnabled = false
        setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val target = MeloNowPlayingPolicy.seekBarTarget(progress, durationMs)
                if (dragging) onPreview(target) else target?.let(onSeek)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                dragging = true
                onPreview(MeloNowPlayingPolicy.seekBarTarget(progress, durationMs))
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                // Read the target first: onPreview(null) re-renders and set() puts the dot back at the play position.
                val target = MeloNowPlayingPolicy.seekBarTarget(progress, durationMs)
                dragging = false
                onPreview(null)
                target?.let(onSeek)
            }
        })
    }

    /** [positionMs] over [durationMs]; an unknown duration locks the line at 0. Ignored for the dot while dragging. */
    fun set(positionMs: Long, durationMs: Long) {
        this.durationMs = durationMs
        val enabled = MeloNowPlayingPolicy.seekBarEnabled(durationMs)
        if (isEnabled != enabled) isEnabled = enabled
        if (dragging) return
        val next = MeloNowPlayingPolicy.seekBarProgress(positionMs, durationMs)
        if (progress != next) progress = next
    }

    override fun onDetachedFromWindow() {
        if (dragging) {
            dragging = false
            onPreview(null)
        }
        super.onDetachedFromWindow()
    }
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
