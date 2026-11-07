package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Views for the Telegram sign-in steps. Display only: it builds the form, shows the texts
 * chosen by [TelegramLoginStepPolicy] and reports taps through callbacks. It never talks to
 * TDLib and never keeps the typed value anywhere except the EditText it owns.
 */
internal class TelegramLoginPanel(
    private val context: Context,
    private val colors: Colors,
    private val onSubmit: () -> Unit,
    private val onResend: () -> Unit,
    private val onEditPhone: () -> Unit,
    private val onBack: () -> Unit,
    private val onQr: () -> Unit
) {
    data class Colors(
        val text: Int, val subtle: Int, val accent: Int, val onAccent: Int, val surface: Int, val border: Int
    )

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density + 0.5f).toInt()
    private val warning = MeloDesignTokens.PALETTE.warning
    private val touch = MeloDesignTokens.MIN_TOUCH_DP
    private val gap = MeloDesignTokens.GAP_DP

    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val titleView = TextView(context).apply {
        textSize = 18f
        setTypeface(null, Typeface.BOLD)
        setTextColor(colors.text)
        // Screen readers announce the new step when the heading changes.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val descView = TextView(context).apply {
        textSize = 13f
        setTextColor(colors.subtle)
    }
    private val hintView = TextView(context).apply {
        textSize = 13f
        setTextColor(colors.accent)
        visibility = View.GONE
    }

    val input = EditText(context).apply {
        setSingleLine(true)
        setTextColor(colors.text)
        setHintTextColor(colors.subtle)
        textSize = 16f
        background = rounded(colors.surface, colors.border)
        setPadding(dp(12), 0, dp(12), 0)
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { onSubmit(); true } else false
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { showError(null) }
        })
    }

    private val toggle = Button(context).apply {
        isAllCaps = false
        textSize = 13f
        setTextColor(colors.text)
        background = rounded(colors.surface, colors.border)
        setPadding(dp(4), 0, dp(4), 0)
        visibility = View.GONE
        setOnClickListener { setPasswordRevealed(!revealed) }
    }

    private val errorView = TextView(context).apply {
        textSize = 13f
        setTextColor(warning)
        visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
    }

    val submit = button("Tiếp tục", primary = true) { onSubmit() }
    val qr = button("Đăng nhập bằng QR") { onQr() }
    private val resend = button("Gửi lại mã") { onResend() }
    private val editPhone = button("Sửa số điện thoại") { onEditPhone() }
    private val back = button("Quay lại") { onBack() }
    private val secondaryRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

    private var field = TelegramLoginStepPolicy.Field.PHONE
    private var revealed = false

    init {
        root.addView(titleView, LinearLayout.LayoutParams(-1, -2))
        root.addView(descView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        root.addView(hintView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(touch), 1f))
        inputRow.addView(toggle, LinearLayout.LayoutParams(dp(64), dp(touch)).apply { leftMargin = dp(gap) })
        root.addView(inputRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(gap) })
        root.addView(errorView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        root.addView(submit, LinearLayout.LayoutParams(-1, dp(touch)).apply { topMargin = dp(gap) })
        root.addView(qr, LinearLayout.LayoutParams(-1, dp(touch)).apply { topMargin = dp(gap) })
        root.addView(secondaryRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(gap) })
        secondaryRow.addView(resend, LinearLayout.LayoutParams(0, dp(touch), 1f).apply { rightMargin = dp(gap / 2) })
        secondaryRow.addView(editPhone, LinearLayout.LayoutParams(0, dp(touch), 1f).apply { leftMargin = dp(gap / 2) })
        secondaryRow.addView(back, LinearLayout.LayoutParams(0, dp(touch), 1f))
        if (MeloPublicBuildPolicy.showUnofficialNotice(com.bsdung.musicassistantcar.BuildConfig.MELO_OPEN_BUILD)) {
            root.addView(unofficialNoticeView(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(gap * 2) })
        }
    }

    /** Open build only: says the app is unofficial and links to the privacy section of the project page. */
    private fun unofficialNoticeView(): TextView = TextView(context).apply {
        textSize = 12f
        setTextColor(colors.subtle)
        val link = MeloPublicBuildPolicy.PRIVACY_LINK_LABEL
        val text = SpannableString("${MeloPublicBuildPolicy.UNOFFICIAL_NOTICE} $link")
        val start = text.length - link.length
        text.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(MeloPublicBuildPolicy.PRIVACY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    // No browser installed: the notice stays readable, nothing else to do.
                }
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = colors.accent
                ds.isUnderlineText = true
            }
        }, start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        this.text = text
        movementMethod = LinkMovementMethod.getInstance()
        setLineSpacing(0f, 1.1f)
    }

    fun render(content: TelegramLoginStepPolicy.StepContent) {
        field = content.field
        titleView.text = content.title
        descView.text = content.description
        hintView.text = content.passwordHint?.let { "Gợi ý từ Telegram: $it" }.orEmpty()
        hintView.visibility = if (content.passwordHint != null) View.VISIBLE else View.GONE
        input.hint = content.inputHint
        input.contentDescription = content.inputHint
        submit.text = content.submitLabel
        configureInput(content.field)
        toggle.visibility = if (content.showPasswordToggle) View.VISIBLE else View.GONE
        setPasswordRevealed(false)
        qr.visibility =
            if (content.showQr && MeloPublicBuildPolicy.showQrLogin(com.bsdung.musicassistantcar.BuildConfig.MELO_OPEN_BUILD)) View.VISIBLE else View.GONE
        resend.visibility = if (content.showResend) View.VISIBLE else View.GONE
        editPhone.visibility = if (content.showEditPhone) View.VISIBLE else View.GONE
        back.visibility = if (content.showBack) View.VISIBLE else View.GONE
        secondaryRow.visibility =
            if (content.showResend || content.showEditPhone || content.showBack) View.VISIBLE else View.GONE
        showError(null)
        setBusy(false)
    }

    fun showError(text: String?) {
        if (text.isNullOrBlank()) {
            if (errorView.visibility != View.GONE) errorView.visibility = View.GONE
            return
        }
        errorView.text = text
        errorView.visibility = View.VISIBLE
    }

    /** Waiting for Telegram's answer: block double taps, keep the typed value. */
    fun setBusy(busy: Boolean) {
        submit.isEnabled = !busy
        submit.alpha = if (busy) 0.6f else 1f
        submit.text = if (busy) "Đang xác thực…" else "Tiếp tục"
    }

    fun setResendState(secondsLeft: Int, allowed: Boolean) {
        val on = allowed && secondsLeft <= 0
        resend.text = TelegramLoginStepPolicy.resendLabel(if (allowed) secondsLeft else 0)
        resend.isEnabled = on
        resend.alpha = if (on) 1f else 0.6f
    }

    fun clearInput() {
        input.text?.clear()
    }

    private fun configureInput(f: TelegramLoginStepPolicy.Field) {
        input.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        input.setAutofillHints()
        when (f) {
            TelegramLoginStepPolicy.Field.PHONE ->
                input.inputType = InputType.TYPE_CLASS_PHONE
            TelegramLoginStepPolicy.Field.CODE, TelegramLoginStepPolicy.Field.EMAIL_CODE -> {
                input.inputType = InputType.TYPE_CLASS_NUMBER
                input.keyListener = DigitsKeyListener.getInstance("0123456789")
                if (f == TelegramLoginStepPolicy.Field.CODE) {
                    // Literal of View.AUTOFILL_HINT_SMS_OTP, which needs a newer API level.
                    input.setAutofillHints("smsOTPCode")
                    input.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                }
            }
            TelegramLoginStepPolicy.Field.PASSWORD -> {
                // Never offer to save or fill the 2FA password.
                input.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                input.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                    EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            TelegramLoginStepPolicy.Field.EMAIL ->
                input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        input.maxLines = 1
        input.ellipsize = TextUtils.TruncateAt.END
    }

    private fun setPasswordRevealed(show: Boolean) {
        revealed = show && field == TelegramLoginStepPolicy.Field.PASSWORD
        if (field == TelegramLoginStepPolicy.Field.PASSWORD) {
            val variation = if (revealed) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else InputType.TYPE_TEXT_VARIATION_PASSWORD
            input.inputType = InputType.TYPE_CLASS_TEXT or variation
            input.setSelection(input.text?.length ?: 0)
        }
        toggle.text = if (revealed) "Ẩn" else "Hiện"
        toggle.contentDescription = if (revealed) "Ẩn mật khẩu" else "Hiện mật khẩu"
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(context).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(if (primary) colors.onAccent else colors.text)
        background = rounded(if (primary) colors.accent else colors.surface, if (primary) null else colors.border)
        minHeight = dp(touch)
        minimumHeight = dp(touch)
        setPadding(dp(8), 0, dp(8), 0)
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
        setOnClickListener { onClick() }
    }

    private fun rounded(fill: Int, stroke: Int?) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(12).toFloat()
        setColor(fill)
        if (stroke != null) setStroke(dp(1), stroke)
    }
}

/**
 * ScrollView that never grows past the visible window, so with the keyboard open the form
 * stays inside the space above it and scrolls instead of being clipped.
 */
internal class TelegramLoginScrollView(context: Context) : android.widget.ScrollView(context) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val frame = android.graphics.Rect()
        getWindowVisibleDisplayFrame(frame)
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val minRoom = (96 * resources.displayMetrics.density).toInt()
        val room = frame.bottom - loc[1]
        val mode = MeasureSpec.getMode(heightSpec)
        val size = MeasureSpec.getSize(heightSpec)
        val limit = when {
            room < minRoom -> size
            mode == MeasureSpec.UNSPECIFIED -> room
            else -> minOf(room, size)
        }
        super.onMeasure(
            widthSpec,
            if (limit > 0) MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST) else heightSpec
        )
    }
}
