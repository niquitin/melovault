package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramLoginStepPolicyTest {
    private val P = TelegramLoginStepPolicy

    @Test fun phoneStepShowsQrOnly() {
        val c = P.contentFor(TelegramAuthStep.WAIT_PHONE_NUMBER)!!
        assertEquals("Nhập số điện thoại", c.title)
        assertTrue(c.showQr)
        assertFalse(c.showResend || c.showEditPhone || c.showBack || c.showPasswordToggle)
    }

    @Test fun codeStepNamesMaskedPhoneAndOffersResendAndEdit() {
        val c = P.contentFor(TelegramAuthStep.WAIT_CODE, maskedPhone = "+84 ••••• 567")!!
        assertEquals("Nhập mã Telegram đã gửi đến +84 ••••• 567.", c.description)
        assertTrue(c.showResend && c.showEditPhone)
        assertFalse(c.showQr || c.showBack || c.showPasswordToggle)
    }

    @Test fun codeStepWithoutPhoneStillReadable() {
        val c = P.contentFor(TelegramAuthStep.WAIT_CODE)!!
        assertFalse(c.description.contains("null"))
    }

    @Test fun passwordStepHasToggleBackAndHint() {
        val c = P.contentFor(TelegramAuthStep.WAIT_PASSWORD, passwordHint = "  tên con mèo ")!!
        assertEquals("Nhập mật khẩu cấp 2", c.title)
        assertEquals("tên con mèo", c.passwordHint)
        assertTrue(c.showPasswordToggle && c.showBack)
        assertFalse(c.showQr || c.showResend)
    }

    @Test fun blankOrHugeHintHandled() {
        assertNull(P.contentFor(TelegramAuthStep.WAIT_PASSWORD, passwordHint = "  ")!!.passwordHint)
        assertNull(P.contentFor(TelegramAuthStep.WAIT_PASSWORD, passwordHint = null)!!.passwordHint)
        val long = P.contentFor(TelegramAuthStep.WAIT_PASSWORD, passwordHint = "x".repeat(300))!!
        assertEquals(80, long.passwordHint!!.length)
    }

    @Test fun editingPhoneShowsPhoneFormWithBackAndNoQr() {
        for (s in listOf(TelegramAuthStep.WAIT_CODE, TelegramAuthStep.WAIT_PASSWORD)) {
            val c = P.contentFor(s, editingPhone = true)!!
            assertEquals(TelegramLoginStepPolicy.Field.PHONE, c.field)
            assertTrue(c.showBack)
            assertFalse(c.showQr)
        }
    }

    @Test fun editingPhoneIgnoredOnPhoneStep() {
        val c = P.contentFor(TelegramAuthStep.WAIT_PHONE_NUMBER, editingPhone = true)!!
        assertTrue(c.showQr)
        assertFalse(c.showBack)
    }

    @Test fun nonCredentialStepsHaveNoContent() {
        for (s in listOf(TelegramAuthStep.AUTHORIZED, TelegramAuthStep.WAIT_PARAMETERS,
            TelegramAuthStep.LOGGING_OUT, TelegramAuthStep.WAIT_OTHER_DEVICE_CONFIRMATION)) {
            assertNull(P.contentFor(s))
            assertFalse(P.isCredentialStep(s))
        }
        assertTrue(P.isCredentialStep(TelegramAuthStep.WAIT_CODE))
    }

    @Test fun qrOnlyOnPhoneStep() {
        for (s in TelegramAuthStep.values()) {
            val c = P.contentFor(s) ?: continue
            assertEquals(s == TelegramAuthStep.WAIT_PHONE_NUMBER, c.showQr)
        }
    }

    @Test fun maskPhoneHidesMiddle() {
        assertEquals("+84 ••••• 567", P.maskPhone("84901234567"))
        assertEquals("+84 ••••• 567", P.maskPhone("+84 90 123 4567"))
        assertFalse(P.maskPhone("84901234567")!!.contains("0123"))
    }

    @Test fun maskPhoneRefusesShortOrMissing() {
        assertNull(P.maskPhone(null))
        assertNull(P.maskPhone(""))
        assertNull(P.maskPhone("12345"))
    }

    @Test fun errorsAreFixedSentencesNotRawText() {
        val raw = "PHONE_CODE_INVALID 12345 +84901234567"
        val msg = P.errorFor(raw, TelegramLoginStepPolicy.Field.CODE)
        assertEquals("Mã không đúng. Kiểm tra lại mã Telegram vừa gửi.", msg)
        assertFalse(msg.contains("12345") || msg.contains("84901234567"))
    }

    @Test fun errorsCoverExpiredPasswordFlood() {
        assertTrue(P.errorFor("PHONE_CODE_EXPIRED", TelegramLoginStepPolicy.Field.CODE).contains("hết hạn"))
        assertTrue(P.errorFor("PASSWORD_HASH_INVALID", TelegramLoginStepPolicy.Field.PASSWORD).contains("Mật khẩu"))
        assertTrue(P.errorFor("Too Many Requests: retry after 30", TelegramLoginStepPolicy.Field.CODE).contains("quá nhiều"))
        assertTrue(P.errorFor("PHONE_NUMBER_INVALID", TelegramLoginStepPolicy.Field.PHONE).contains("Số điện thoại"))
    }

    @Test fun unknownErrorFallsBackPerField() {
        for (f in TelegramLoginStepPolicy.Field.values()) {
            assertNotNull(P.errorFor("???", f))
            assertFalse(P.errorFor("???", f).contains("???"))
        }
    }

    @Test fun resendLabelCountsDown() {
        assertEquals("Gửi lại mã (45s)", P.resendLabel(45))
        assertEquals("Gửi lại mã", P.resendLabel(0))
    }

    @Test fun chromeHiddenUntilAuthorized() {
        assertFalse(P.showMiniPlayer(false))
        assertFalse(P.showLogout(false))
        assertTrue(P.showMiniPlayer(true))
        assertTrue(P.showLogout(true))
    }
}
