package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The open (friends beta) build says it is unofficial and hides test tools and QR; the private build is unchanged. */
class MeloPublicBuildPolicyTest {
    private val srcRoot = listOf(File("src"), File("app/src")).first { it.isDirectory }
    private val gradle = File(srcRoot.parentFile, "build.gradle").readText().replace("\r\n", "\n")
    private val telegramDir = File(srcRoot, "shareable/java/com/bsdung/musicassistantcar/data/telegram")
    private fun source(name: String) = File(telegramDir, name).readText().replace("\r\n", "\n")

    @Test fun headerSaysUnofficialOnlyInOpenBuild() {
        assertEquals("Nhạc từ Telegram của bạn · Ứng dụng không chính thức", MeloPublicBuildPolicy.headerSubtitle(true))
        assertEquals("Telegram · Nhạc cá nhân · Preview", MeloPublicBuildPolicy.headerSubtitle(false))
    }

    @Test fun eyebrowOnScreenSaysUnofficialOnlyInOpenBuild() {
        assertTrue(MeloPublicBuildPolicy.eyebrow(true).contains("KHÔNG CHÍNH THỨC"))
        assertFalse(MeloPublicBuildPolicy.eyebrow(true).contains("PREVIEW"))
        assertTrue(MeloPublicBuildPolicy.eyebrow(false).endsWith("PREVIEW"))
        assertTrue(source("TelegramPreviewActivity.kt").contains("MeloPublicBuildPolicy.eyebrow(BuildConfig.MELO_OPEN_BUILD)"))
    }

    @Test fun qrLoginIsHiddenOnlyInOpenBuild() {
        assertFalse(MeloPublicBuildPolicy.showQrLogin(true))
        assertTrue(MeloPublicBuildPolicy.showQrLogin(false))
    }

    @Test fun registrationMessageSendsFriendsToOfficialTelegram() {
        assertEquals(
            "Tài khoản mới cần đăng ký bằng ứng dụng Telegram chính thức trước.",
            MeloPublicBuildPolicy.waitRegistrationMessage(true)
        )
        assertTrue(MeloPublicBuildPolicy.waitRegistrationMessage(false).contains("Preview"))
    }

    @Test fun developerToolsNeverExistInOpenBuildEvenWhenUnlocked() {
        assertFalse(MeloPublicBuildPolicy.developerToolsAvailable(openBuild = true, unlockedByOwner = true))
        assertFalse(MeloPublicBuildPolicy.developerToolsAvailable(openBuild = true, unlockedByOwner = false))
        assertTrue(MeloPublicBuildPolicy.developerToolsAvailable(openBuild = false, unlockedByOwner = true))
        assertFalse(MeloPublicBuildPolicy.developerToolsAvailable(openBuild = false, unlockedByOwner = false))
        assertFalse(MeloPublicBuildPolicy.versionTapUnlocksDeveloperTools(true))
        assertTrue(MeloPublicBuildPolicy.versionTapUnlocksDeveloperTools(false))
    }

    @Test fun unofficialNoticeAndPrivacyLink() {
        assertTrue(MeloPublicBuildPolicy.showUnofficialNotice(true))
        assertFalse(MeloPublicBuildPolicy.showUnofficialNotice(false))
        assertEquals(
            "MeloVault là ứng dụng không chính thức, dùng Telegram API. Không liên kết với Telegram.",
            MeloPublicBuildPolicy.UNOFFICIAL_NOTICE
        )
        assertEquals("https://niquitin.github.io/melovault/#quyen-rieng-tu", MeloPublicBuildPolicy.PRIVACY_URL)
    }

    @Test fun screensAreWiredToThePolicy() {
        val preview = source("TelegramPreviewActivity.kt")
        assertTrue(preview.contains("MeloPublicBuildPolicy.headerSubtitle(BuildConfig.MELO_OPEN_BUILD)"))
        assertTrue(preview.contains("MeloPublicBuildPolicy.waitRegistrationMessage(BuildConfig.MELO_OPEN_BUILD)"))
        assertFalse(preview.contains("bản Preview chưa hỗ trợ"))
        val panel = source("TelegramLoginPanel.kt")
        assertTrue(panel.contains("MeloPublicBuildPolicy.showQrLogin("))
        assertTrue(panel.contains("MeloPublicBuildPolicy.showUnofficialNotice("))
        val lab = source("MeloUiLabActivity.kt")
        assertEquals(2, Regex("MeloPublicBuildPolicy\\.developerToolsAvailable\\(").findAll(lab).count())
        assertTrue(lab.contains("MeloPublicBuildPolicy.versionTapUnlocksDeveloperTools("))
    }

    @Test fun shareableBackupRulesExcludeAccountDataButPersonalRulesStayUntouched() {
        val shareable = File(srcRoot, "shareable/res/xml")
        listOf("backup_rules.xml", "data_extraction_rules.xml").forEach { name ->
            val text = File(shareable, name).readText().replace("\r\n", "\n")
            assertTrue(name, text.contains("<exclude domain=\"database\" path=\".\" />"))
            assertTrue(name, text.contains("e91_tdlib_preview.xml"))
            assertTrue(name, text.contains("melovault_secure_auth.xml"))
            assertFalse(name, text.contains("<include"))
        }
        // Personal keeps the main rules, which have no database exclusion.
        assertFalse(File(srcRoot, "main/res/xml/backup_rules.xml").readText().contains("database"))
        assertFalse(File(srcRoot, "personal/res/xml/backup_rules.xml").exists())
        assertFalse(File(srcRoot, "personal/res/xml/data_extraction_rules.xml").exists())
    }

    @Test fun openBuildIsCalledMeloVaultAndPrivateBuildKeepsPreview() {
        assertTrue(gradle.contains("resValue 'string', 'app_name', meloOpen ? 'MeloVault' : 'MeloVault Preview'"))
        assertFalse(File(srcRoot, "shareable/res/values/strings.xml").exists())
        assertTrue(gradle.contains("versionCode 122"))
        assertTrue(gradle.contains("signingConfig signingConfigs.debug"))
        assertFalse(gradle.contains("meloPublicRelease"))
    }
}
