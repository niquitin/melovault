package com.bsdung.musicassistantcar.data.telegram

import java.net.URLEncoder

/**
 * "Giới thiệu": the words and links of the About page, as pure functions (no Android). The page only draws these answers.
 * Every sentence in [PRIVACY_POINTS] was checked against the code of this build (docs/plans/2026-11-02-thiet-ke-lai-cai-dat-bao-cao.md).
 */
internal object MeloAboutPolicy {

    const val APP_NAME = "MeloVault"
    const val TAGLINE = "Nghe nhạc từ kênh Telegram của bạn"
    const val AUTHOR = "Nguyễn Dũng"
    const val COPYRIGHT = "© 2026 Nguyễn Dũng"

    const val SOURCE_URL = "https://github.com/niquitin/melovault"
    const val SOURCE_LABEL = "github.com/niquitin/melovault"

    /**
     * Where feedback mail goes. Public once shipped, so it is only the address the owner confirmed (CĐR1).
     * Empty = not confirmed yet: the page shows no email row.
     */
    const val FEEDBACK_EMAIL = ""

    fun feedbackAvailable(email: String = FEEDBACK_EMAIL): Boolean = email.isNotBlank()

    /** "0.6.96-beta (vc121)". */
    fun versionLine(versionName: String, versionCode: Int): String = "$versionName (vc$versionCode)"

    fun feedbackSubject(versionName: String, versionCode: Int): String = "Góp ý MeloVault ${versionLine(versionName, versionCode)}"

    /** Only the app version, the phone model and the Android version: no account, library or song information. */
    fun feedbackBody(versionName: String, versionCode: Int, model: String, androidRelease: String): String =
        "\n\n---\nPhiên bản: ${versionLine(versionName, versionCode)}\nMáy: $model\nAndroid: $androidRelease"

    /** `mailto:` link with a percent-encoded subject and body (spaces as %20, not +). */
    fun mailtoUri(email: String, subject: String, body: String): String =
        "mailto:$email?subject=${encode(subject)}&body=${encode(body)}"

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** Short statements of the Quyền riêng tư page, in the order shown. */
    val PRIVACY_POINTS: List<String> = listOf(
        "Đăng nhập, danh sách kênh và tải nhạc đi thẳng giữa máy bạn và Telegram. MeloVault không có máy chủ riêng.",
        "Yêu thích và Playlist chỉ đồng bộ qua chính tài khoản Telegram của bạn, và chỉ khi bạn bật Đồng bộ.",
        "${MeloLyricsUiPolicy.SETTINGS_SUBTITLE} Lời nhạc chỉ hoạt động khi bạn bật.",
        "Không quảng cáo, không công cụ theo dõi hay thống kê trong ứng dụng."
    )

    /** One open-source component for the Giấy phép page. */
    data class License(val name: String, val license: String, val url: String)

    /** Taken from THIRD_PARTY_NOTICES.md and app/build.gradle: no versions are listed, so none can be wrong. */
    val LICENSES: List<License> = listOf(
        License("TDLib (Telegram Database Library)", "Boost Software License 1.0", "https://github.com/tdlib/td"),
        License("Bộ giải mã ALAC bằng Java (fgp/AirReceiver)", "BSD 3-Clause", "https://github.com/fgp/AirReceiver"),
        License("AndroidX (Core, Activity, AppCompat, RecyclerView, WorkManager)", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
        License("AndroidX Media3 (ExoPlayer, MediaSession)", "Apache License 2.0", "https://github.com/androidx/media"),
        License("Material Components for Android", "Apache License 2.0", "https://github.com/material-components/material-components-android"),
        License("Coil", "Apache License 2.0", "https://github.com/coil-kt/coil"),
        License("Kotlin", "Apache License 2.0", "https://github.com/JetBrains/kotlin"),
        License("Material Icons (một số biểu tượng)", "Apache License 2.0", "https://github.com/google/material-design-icons")
    )

    /** LRCLIB is a data service, not code in the app. */
    const val LRCLIB_NOTE = "Lời nhạc lấy từ dịch vụ công cộng LRCLIB (lrclib.net), không thuộc MeloVault."
}
