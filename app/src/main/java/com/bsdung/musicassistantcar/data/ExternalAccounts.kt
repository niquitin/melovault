package com.bsdung.musicassistantcar.data

import org.json.JSONObject

enum class AccountProvider(val id: String, val displayLabel: String) {
    APPLE_MUSIC("apple_music", "Apple Music"),
    YOUTUBE("youtube", "YouTube");

    companion object {
        fun fromId(id: String): AccountProvider? =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

enum class AccountConnectionState(val wireValue: String, val vietnameseLabel: String) {
    NOT_CONFIGURED("NOT_CONFIGURED", "Chưa cấu hình"),
    NOT_CONNECTED("NOT_CONNECTED", "Chưa kết nối"),
    CONNECTING("CONNECTING", "Đang kết nối"),
    CONNECTED("CONNECTED", "Đã kết nối"),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Token hết hạn"),
    REAUTH_REQUIRED("REAUTH_REQUIRED", "Cần ủy quyền lại"),
    ERROR("ERROR", "Lỗi kết nối"),
    VERIFICATION_UNAVAILABLE("VERIFICATION_UNAVAILABLE", "Chưa thể xác thực");

    companion object {
        fun fromWire(value: String?): AccountConnectionState =
            values().firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: ERROR
    }
}

data class ProviderCapabilities(
    val accountAuthorizationSupported: Boolean,
    val catalogSearchAvailable: Boolean,
    val personalLibraryAvailable: Boolean,
    val playlistReadAvailable: Boolean,
    val playlistWriteAvailable: Boolean,
    val playbackAvailable: Boolean = false,
    val offlineDownloadPermitted: Boolean = false,
) {
    companion object {
        fun fromJson(json: JSONObject?): ProviderCapabilities {
            if (json == null) {
                return ProviderCapabilities(
                    accountAuthorizationSupported = true,
                    catalogSearchAvailable = false,
                    personalLibraryAvailable = false,
                    playlistReadAvailable = false,
                    playlistWriteAvailable = false,
                    playbackAvailable = false,
                    offlineDownloadPermitted = false,
                )
            }
            return ProviderCapabilities(
                accountAuthorizationSupported = json.optBoolean("account_authorization_supported", true),
                catalogSearchAvailable = json.optBoolean("catalog_search_available", false),
                personalLibraryAvailable = json.optBoolean("personal_library_available", false),
                playlistReadAvailable = json.optBoolean("playlist_read_available", false),
                playlistWriteAvailable = json.optBoolean("playlist_write_available", false),
                playbackAvailable = json.optBoolean("playback_available", false),
                offlineDownloadPermitted = json.optBoolean("offline_download_permitted", false),
            )
        }
    }
}

data class AccountStatusDto(
    val provider: String,
    val state: AccountConnectionState,
    val displayLabel: String,
    val capabilities: ProviderCapabilities,
    val lastVerifiedAt: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): AccountStatusDto {
            val provider = json.optString("provider", "")
            val stateStr = json.optString("state", "ERROR")
            val displayLabel = json.optString("display_label", provider)
            val capsJson = json.optJSONObject("capabilities")
            val lastVerified = json.optString("last_verified_at").takeIf { it.isNotBlank() }
            val errCode = json.optString("error_code").takeIf { it.isNotBlank() }
            val errMsg = json.optString("error_message").takeIf { it.isNotBlank() }

            return AccountStatusDto(
                provider = provider,
                state = AccountConnectionState.fromWire(stateStr),
                displayLabel = displayLabel,
                capabilities = ProviderCapabilities.fromJson(capsJson),
                lastVerifiedAt = lastVerified,
                errorCode = errCode,
                errorMessage = errMsg,
            )
        }
    }
}
