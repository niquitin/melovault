package com.bsdung.musicassistantcar.data.telegram

/**
 * Pure display rules for the Telegram sign-in steps (phone, code, 2FA password, email).
 * No TDLib, no Android: it only decides which texts and controls each step shows and
 * turns raw TDLib error text into a fixed Vietnamese sentence. It never sees, stores or
 * returns an OTP, a password or a full phone number.
 */
internal object TelegramLoginStepPolicy {

    enum class Field { PHONE, CODE, PASSWORD, EMAIL, EMAIL_CODE }

    data class StepContent(
        val field: Field,
        val title: String,
        val description: String,
        /** Telegram's own password hint, shown on its own line; null when there is none. */
        val passwordHint: String?,
        val inputHint: String,
        val submitLabel: String,
        val showQr: Boolean,
        val showResend: Boolean,
        val showEditPhone: Boolean,
        /** Back to the phone step (password step) or to the code step (editing the phone). */
        val showBack: Boolean,
        val showPasswordToggle: Boolean
    )

    /** Longest hint line shown; Telegram hints are user text and can be long. */
    private const val MAX_HINT_CHARS = 80

    fun isCredentialStep(step: TelegramAuthStep): Boolean = when (step) {
        TelegramAuthStep.WAIT_PHONE_NUMBER,
        TelegramAuthStep.WAIT_CODE,
        TelegramAuthStep.WAIT_PASSWORD,
        TelegramAuthStep.WAIT_EMAIL_ADDRESS,
        TelegramAuthStep.WAIT_EMAIL_CODE -> true
        else -> false
    }

    /**
     * @param maskedPhone already masked by [maskPhone], or null when TDLib sent none.
     * @param editingPhone the user pressed "Sửa số điện thoại" / "Quay lại": show the phone
     * form again although TDLib is still waiting for the code or password.
     */
    fun contentFor(
        step: TelegramAuthStep,
        maskedPhone: String? = null,
        passwordHint: String? = null,
        editingPhone: Boolean = false
    ): StepContent? {
        if (editingPhone && (step == TelegramAuthStep.WAIT_CODE ||
                step == TelegramAuthStep.WAIT_PASSWORD ||
                step == TelegramAuthStep.WAIT_EMAIL_ADDRESS ||
                step == TelegramAuthStep.WAIT_EMAIL_CODE)) {
            return phoneContent(showQr = false, showBack = true)
        }
        return when (step) {
            TelegramAuthStep.WAIT_PHONE_NUMBER -> phoneContent(showQr = true, showBack = false)
            TelegramAuthStep.WAIT_CODE -> StepContent(
                field = Field.CODE,
                title = "Nhập mã Telegram",
                description = if (maskedPhone.isNullOrBlank()) {
                    "Nhập mã Telegram đã gửi đến tài khoản của bạn."
                } else {
                    "Nhập mã Telegram đã gửi đến $maskedPhone."
                },
                passwordHint = null,
                inputHint = "Mã gồm các chữ số",
                submitLabel = "Tiếp tục",
                showQr = false,
                showResend = true,
                showEditPhone = true,
                showBack = false,
                showPasswordToggle = false
            )
            TelegramAuthStep.WAIT_PASSWORD -> StepContent(
                field = Field.PASSWORD,
                title = "Nhập mật khẩu cấp 2",
                description = "Nhập mật khẩu cấp 2 (xác minh 2 bước) của tài khoản Telegram.",
                passwordHint = cleanHint(passwordHint),
                inputHint = "Mật khẩu cấp 2",
                submitLabel = "Tiếp tục",
                showQr = false,
                showResend = false,
                showEditPhone = false,
                showBack = true,
                showPasswordToggle = true
            )
            TelegramAuthStep.WAIT_EMAIL_ADDRESS -> StepContent(
                field = Field.EMAIL,
                title = "Nhập email xác minh",
                description = "Telegram yêu cầu một địa chỉ email để xác minh đăng nhập.",
                passwordHint = null,
                inputHint = "Địa chỉ email",
                submitLabel = "Tiếp tục",
                showQr = false,
                showResend = false,
                showEditPhone = true,
                showBack = false,
                showPasswordToggle = false
            )
            TelegramAuthStep.WAIT_EMAIL_CODE -> StepContent(
                field = Field.EMAIL_CODE,
                title = "Nhập mã gửi về email",
                description = "Nhập mã Telegram vừa gửi về email xác minh của bạn.",
                passwordHint = null,
                inputHint = "Mã gồm các chữ số",
                submitLabel = "Tiếp tục",
                showQr = false,
                showResend = false,
                showEditPhone = true,
                showBack = false,
                showPasswordToggle = false
            )
            else -> null
        }
    }

    private fun phoneContent(showQr: Boolean, showBack: Boolean) = StepContent(
        field = Field.PHONE,
        title = "Nhập số điện thoại",
        description = "Nhập số điện thoại đã đăng ký Telegram, kèm mã nước (ví dụ +84…).",
        passwordHint = null,
        inputHint = "Số điện thoại (+84...)",
        submitLabel = "Tiếp tục",
        showQr = showQr,
        showResend = false,
        showEditPhone = false,
        showBack = showBack,
        showPasswordToggle = false
    )

    /**
     * Hides the middle of the number: "84901234567" -> "+84 ••••• 567". Returns null when
     * there are too few digits to mask safely, so a short or odd string is never echoed.
     */
    fun maskPhone(raw: String?): String? {
        val digits = raw.orEmpty().filter { it.isDigit() }
        if (digits.length < 8) return null
        val country = digits.take(2)
        val tail = digits.takeLast(3)
        return "+$country ••••• $tail"
    }

    /** "Gửi lại mã (45s)" while Telegram's resend timer runs, plain label afterwards. */
    fun resendLabel(secondsLeft: Int): String =
        if (secondsLeft > 0) "Gửi lại mã (${secondsLeft}s)" else "Gửi lại mã"

    /**
     * Fixed Vietnamese sentence for a failed sign-in request. The raw TDLib message is only
     * matched, never displayed, so it cannot leak a code, phone or password into the UI.
     */
    fun errorFor(tdlibMessage: String, field: Field): String {
        val m = tdlibMessage.uppercase()
        return when {
            "PHONE_CODE_EXPIRED" in m || "CODE_EXPIRED" in m ->
                "Mã đã hết hạn. Bấm “Gửi lại mã” để nhận mã mới."
            "PHONE_CODE_INVALID" in m || "PHONE_CODE_EMPTY" in m || "CODE_INVALID" in m ->
                "Mã không đúng. Kiểm tra lại mã Telegram vừa gửi."
            "PASSWORD_HASH_INVALID" in m || "PASSWORD_INVALID" in m ->
                "Mật khẩu cấp 2 không đúng. Hãy thử lại."
            "PHONE_NUMBER_INVALID" in m || "PHONE_NUMBER_BANNED" in m ->
                "Số điện thoại không hợp lệ. Kiểm tra mã nước rồi thử lại."
            "EMAIL_INVALID" in m || "EMAIL_ADDRESS_INVALID" in m ->
                "Địa chỉ email không hợp lệ."
            "FLOOD" in m || "TOO MANY REQUESTS" in m ->
                "Thử quá nhiều lần. Hãy đợi một lúc rồi thử lại."
            "RESEND" in m || "UNAVAILABLE" in m ->
                "Chưa gửi lại mã được lúc này. Hãy đợi rồi thử lại."
            else -> when (field) {
                Field.CODE, Field.EMAIL_CODE -> "Không xác minh được mã. Hãy thử lại."
                Field.PASSWORD -> "Không xác minh được mật khẩu. Hãy thử lại."
                Field.PHONE -> "Không gửi được số điện thoại. Hãy thử lại."
                Field.EMAIL -> "Không gửi được email. Hãy thử lại."
            }
        }
    }

    /** Not signed in: the player bar, bottom nav and "Đăng xuất" have nothing to act on. */
    fun showMiniPlayer(authorized: Boolean): Boolean = authorized

    fun showLogout(authorized: Boolean): Boolean = authorized

    private fun cleanHint(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        return if (t.length > MAX_HINT_CHARS) t.take(MAX_HINT_CHARS - 1) + "…" else t
    }
}
