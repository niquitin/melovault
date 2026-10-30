package com.bsdung.musicassistantcar.data.telegram

/** Formatting and source selection only. Never logs or exposes credential values in diagnostics. */
internal object TelegramApiCredentialPolicy {
    enum class Source { EMBEDDED, USER }
    data class Credentials(val apiId: Int, val apiHash: String, val source: Source) {
        override fun toString(): String = "Credentials(source=$source)"
    }
    enum class Error(val text: String) {
        ID_EMPTY("Nhập API ID của bạn."),
        ID_INVALID("API ID phải là số nguyên dương hợp lệ."),
        HASH_EMPTY("Nhập API hash của bạn."),
        HASH_INVALID("API hash phải có đúng 32 ký tự từ 0–9 và a–f.")
    }
    data class Result(val credentials: Credentials?, val idError: Error?, val hashError: Error?) {
        val ok: Boolean get() = credentials != null
    }

    /** 2026-10-30 D7: shown when the open build's embedded pair is rejected; offers the E104 fallback. */
    const val OFFER_OWN_API_MESSAGE =
        "Mã kết nối chung của ứng dụng đang bị Telegram từ chối. Bạn có thể dùng API ID/hash của riêng mình."

    /** 2026-10-30 D2/D7: which recovery path a rejected login should offer, if any. */
    enum class RejectAction { NONE, REENTER_USER, OFFER_OWN_API }

    fun parseId(raw: String): Int? = raw.trim().takeIf { it.matches(Regex("[0-9]{1,10}")) }
        ?.toIntOrNull()?.takeIf { it > 0 }

    fun parseHash(raw: String): String? = raw.trim().takeIf { it.matches(Regex("[a-fA-F0-9]{32}")) }
        ?.lowercase(java.util.Locale.ROOT)

    fun validate(rawId: String, rawHash: String): Result {
        val id = parseId(rawId)
        val hash = parseHash(rawHash)
        val idError = if (rawId.isBlank()) Error.ID_EMPTY else if (id == null) Error.ID_INVALID else null
        val hashError = if (rawHash.isBlank()) Error.HASH_EMPTY else if (hash == null) Error.HASH_INVALID else null
        return Result(if (id != null && hash != null) Credentials(id, hash, Source.USER) else null, idError, hashError)
    }

    /**
     * 2026-10-30 D6: [preferStored] lets the open build keep a user's already-saved credentials
     * instead of switching them back to the embedded open pair. The private build always calls
     * this with the default (embedded wins), unchanged from before.
     */
    fun resolve(embeddedId: Int, embeddedHash: String, stored: Credentials?, preferStored: Boolean = false): Credentials? {
        val storedValid = stored?.let { validate(it.apiId.toString(), it.apiHash).credentials }
        if (preferStored && storedValid != null) return storedValid
        val embedded = validate(embeddedId.toString(), embeddedHash).credentials
        if (embedded != null) return embedded.copy(source = Source.EMBEDDED)
        return storedValid
    }

    /** 2026-10-30 D7: NONE unless [message] matches a known API-rejection error. */
    fun rejectionAction(source: Source?, openBuild: Boolean, message: String?): RejectAction {
        if (loginErrorFor(message) == null) return RejectAction.NONE
        return when {
            source == Source.USER -> RejectAction.REENTER_USER
            source == Source.EMBEDDED && openBuild -> RejectAction.OFFER_OWN_API
            else -> RejectAction.NONE
        }
    }

    fun maskId(id: Int): String = "•••• " + id.toString().takeLast(2)

    fun scrub(message: String, secrets: List<String>): String = secrets.filter { it.isNotBlank() }
        .distinct().sortedByDescending { it.length }.fold(message) { text, secret ->
            text.replace(secret, "[redacted]", ignoreCase = true)
        }

    fun loginErrorFor(message: String?): String? = if (message != null && listOf(
            "API_ID_INVALID", "API_HASH_INVALID", "API_ID_PUBLISHED_FLOOD"
        ).any { message.contains(it, ignoreCase = true) }) {
        "Mã API ID/hash không đúng hoặc không được Telegram chấp nhận. Kiểm tra lại ở my.telegram.org."
    } else null
}
