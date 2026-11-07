package com.bsdung.musicassistantcar.data.telegram

/**
 * What the open (friends beta, `app.melovault.player.open`) build shows differently, as pure functions of one flag
 * (`BuildConfig.MELO_OPEN_BUILD`). The private build (flag false) keeps every earlier word and screen exactly.
 */
internal object MeloPublicBuildPolicy {

    const val PRIVACY_URL = "https://niquitin.github.io/melovault/#quyen-rieng-tu"

    /** The unofficial-app notice shown on the sign-in screen and the About page of the open build. */
    const val UNOFFICIAL_NOTICE = "MeloVault là ứng dụng không chính thức, dùng Telegram API. Không liên kết với Telegram."

    const val PRIVACY_LINK_LABEL = "Quyền riêng tư"

    private const val PRIVATE_SUBTITLE = "Telegram · Nhạc cá nhân · Preview"
    private const val OPEN_SUBTITLE = "Nhạc từ Telegram của bạn · Ứng dụng không chính thức"

    private const val PRIVATE_REGISTRATION = "Tài khoản mới cần đăng ký; bản Preview chưa hỗ trợ."
    private const val OPEN_REGISTRATION = "Tài khoản mới cần đăng ký bằng ứng dụng Telegram chính thức trước."

    /** First text of the header's second line, before the library fills it with counts. */
    fun headerSubtitle(openBuild: Boolean): String = if (openBuild) OPEN_SUBTITLE else PRIVATE_SUBTITLE

    /** The QR sign-in asks the user to scan on another machine of the owner; it is not offered to friends. */
    fun showQrLogin(openBuild: Boolean): Boolean = !openBuild

    fun waitRegistrationMessage(openBuild: Boolean): String = if (openBuild) OPEN_REGISTRATION else PRIVATE_REGISTRATION

    /** Device emulation and "copy measurements" are layout test tools: never in the open build, even once unlocked. */
    fun developerToolsAvailable(openBuild: Boolean, unlockedByOwner: Boolean): Boolean = !openBuild && unlockedByOwner

    /** Seven taps on the version only switch the developer tools on where they can exist. */
    fun versionTapUnlocksDeveloperTools(openBuild: Boolean): Boolean = !openBuild

    fun showUnofficialNotice(openBuild: Boolean): Boolean = openBuild
}
