package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

internal class TelegramApiSetupPanel(
    private val context: Context,
    private val colors: TelegramLoginPanel.Colors,
    private val onSave: (TelegramApiCredentialPolicy.Credentials) -> Unit
) {
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
    private fun rounded() = GradientDrawable().apply {
        setColor(this@TelegramApiSetupPanel.colors.surface)
        setStroke(dp(1), this@TelegramApiSetupPanel.colors.border)
        cornerRadius = dp(12).toFloat()
    }
    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; isSaveEnabled = false }
    private fun field(label: String, type: Int) = EditText(context).apply {
        hint = label
        contentDescription = label
        setSingleLine(true)
        inputType = type
        setTextColor(colors.text)
        setHintTextColor(colors.subtle)
        background = rounded()
        setPadding(dp(12), 0, dp(12), 0)
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        minHeight = dp(MeloDesignTokens.MIN_TOUCH_DP)
    }
    private val id = field("API ID", InputType.TYPE_CLASS_NUMBER).apply {
        keyListener = DigitsKeyListener.getInstance("0123456789")
    }
    private val hash = field("API hash", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
    private fun errorView() = TextView(context).apply {
        setTextColor(MeloDesignTokens.PALETTE.warning)
        textSize = 13f
        visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val idError = errorView()
    private val hashError = errorView()
    private val saveError = errorView()
    private var busy = false
    private val save = Button(context).apply {
        text = "Lưu và tiếp tục"
        isAllCaps = false
        setTextColor(colors.onAccent)
        background = GradientDrawable().apply { setColor(this@TelegramApiSetupPanel.colors.accent); cornerRadius = dp(12).toFloat() }
        minHeight = dp(MeloDesignTokens.MIN_TOUCH_DP)
        setOnClickListener {
            if (!busy) {
                val result = TelegramApiCredentialPolicy.validate(this@TelegramApiSetupPanel.id.text.toString(), hash.text.toString())
                show(idError, result.idError?.text)
                show(hashError, result.hashError?.text)
                showError(null)
                result.credentials?.let { setBusy(true); onSave(it) }
            }
        }
    }
    private val browser = Button(context).apply {
        text = "Mở my.telegram.org"
        isAllCaps = false
        setTextColor(colors.text)
        background = rounded()
        minHeight = dp(MeloDesignTokens.MIN_TOUCH_DP)
        setOnClickListener {
            if (runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org"))) }.isFailure) {
                showError("Không mở được trình duyệt. Hãy mở my.telegram.org trong trình duyệt của bạn.")
            }
        }
    }
    init {
        root.addView(TextView(context).apply {
            text = "Kết nối Telegram của bạn"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colors.text)
        })
        root.addView(TextView(context).apply {
            text = "1. Vào my.telegram.org → API development tools.\n2. Chép App api_id và App api_hash.\n3. Mã chỉ được lưu mã hóa trên máy này, không gửi cho ai ngoài Telegram."
            textSize = 13f
            setTextColor(colors.subtle)
        })
        listOf(id, idError, hash, hashError, saveError, save, browser).forEach {
            root.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(MeloDesignTokens.GAP_DP) })
        }
    }
    private fun show(view: TextView, message: String?) {
        view.text = message.orEmpty()
        view.visibility = if (message == null) View.GONE else View.VISIBLE
    }
    fun showError(message: String?) = show(saveError, message)
    fun setBusy(value: Boolean) {
        busy = value
        save.isEnabled = !value
        id.isEnabled = !value
        hash.isEnabled = !value
        browser.isEnabled = !value
    }
    fun clearInput() { id.text.clear(); hash.text.clear() }
}
