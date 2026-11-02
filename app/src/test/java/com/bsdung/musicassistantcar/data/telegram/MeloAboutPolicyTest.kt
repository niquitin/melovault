package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloAboutPolicyTest {

    @Test
    fun nameAndCopyrightAreTheOwners() {
        assertEquals("Nguyễn Dũng", MeloAboutPolicy.AUTHOR)
        assertEquals("© 2026 Nguyễn Dũng", MeloAboutPolicy.COPYRIGHT)
        assertEquals("MeloVault", MeloAboutPolicy.APP_NAME)
    }

    @Test
    fun versionLineReadsLikeTheBuild() {
        assertEquals("0.6.96-beta (vc121)", MeloAboutPolicy.versionLine("0.6.96-beta", 121))
    }

    @Test
    fun sourceLinkPointsToThePublicRepository() {
        assertEquals("https://github.com/niquitin/melovault", MeloAboutPolicy.SOURCE_URL)
        assertTrue(MeloAboutPolicy.SOURCE_URL.endsWith(MeloAboutPolicy.SOURCE_LABEL))
        assertTrue(MeloAboutPolicy.SOURCE_URL.startsWith("https://"))
    }

    @Test
    fun feedbackSubjectCarriesTheVersion() {
        assertEquals("Góp ý MeloVault 0.6.96-beta (vc121)", MeloAboutPolicy.feedbackSubject("0.6.96-beta", 121))
    }

    @Test
    fun feedbackBodyHasOnlyVersionPhoneAndAndroid() {
        val body = MeloAboutPolicy.feedbackBody("0.6.96-beta", 121, "XQ-AS52", "12")
        assertTrue(body.contains("Phiên bản: 0.6.96-beta (vc121)"))
        assertTrue(body.contains("Máy: XQ-AS52"))
        assertTrue(body.contains("Android: 12"))
        assertEquals("version, phone, Android and the separator: nothing else", 4, body.trim().lines().size)
        // Nothing about the account or library.
        listOf("Telegram", "bài", "tài khoản", "kênh").forEach { assertFalse(it, body.contains(it)) }
    }

    @Test
    fun mailtoPercentEncodesSpacesAndVietnamese() {
        val uri = MeloAboutPolicy.mailtoUri("a@b.vn", "Góp ý MeloVault (vc121)", "Máy: XQ AS52\nAndroid: 12")
        assertTrue(uri.startsWith("mailto:a@b.vn?subject="))
        assertTrue(uri.contains("&body="))
        assertFalse("spaces must be %20, not +", uri.contains("+"))
        assertFalse(uri.contains(" "))
        assertFalse(uri.contains("\n"))
        assertTrue(uri.contains("G%C3%B3p%20%C3%BD%20MeloVault"))
        assertTrue(uri.contains("%0A"))
    }

    @Test
    fun feedbackEmailIsTheOneTheOwnerConfirmed() {
        // CĐR1: the address is public once shipped, so it is only the one the owner gave in chat (2026-11-02).
        assertEquals("ntdung.md@gmail.com", MeloAboutPolicy.FEEDBACK_EMAIL)
        assertTrue(MeloAboutPolicy.feedbackAvailable())
        assertFalse("a blank address shows no email row", MeloAboutPolicy.feedbackAvailable("   "))
        assertFalse(MeloAboutPolicy.feedbackAvailable(""))
    }

    @Test
    fun privacyPageKeepsTheLyricsDisclosureWordForWord() {
        assertTrue(MeloAboutPolicy.PRIVACY_POINTS.any { it.contains(MeloLyricsUiPolicy.SETTINGS_SUBTITLE) })
        assertTrue(MeloAboutPolicy.PRIVACY_POINTS.all { it.isNotBlank() })
    }

    @Test
    fun licensesNameTheComponentsOfThisBuild() {
        val names = MeloAboutPolicy.LICENSES.joinToString(" ") { it.name }
        listOf("TDLib", "ALAC", "AndroidX", "Media3", "Material", "Coil", "Kotlin").forEach { assertTrue(it, names.contains(it)) }
        val alac = MeloAboutPolicy.LICENSES.first { it.name.contains("ALAC") }
        assertEquals("BSD 3-Clause", alac.license)
        MeloAboutPolicy.LICENSES.forEach {
            assertTrue(it.name, it.license.isNotBlank())
            assertTrue(it.name, it.url.startsWith("https://"))
        }
        assertTrue(MeloAboutPolicy.LRCLIB_NOTE.contains("LRCLIB"))
    }
}
