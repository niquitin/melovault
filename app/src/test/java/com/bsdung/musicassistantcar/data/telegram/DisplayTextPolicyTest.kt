package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

/** E96 P5B3: display-only normalization does not rewrite Telegram source identities or stored data. */
class DisplayTextPolicyTest {
    @Test fun d01_plainVietnamesePreservedWithoutLosingAccents() {
        assertEquals("Nhạc Việt – Lưu Playlist", DisplayTextPolicy.clean("Nhạc Việt – Lưu Playlist"))
    }

    @Test fun d02_decomposedVietnameseAccentsAreComposedForConsistentRendering() {
        val source = Normalizer.normalize("Tiếng Việt", Normalizer.Form.NFD)
        assertTrue(DisplayTextPolicy.needsClean(source))
        assertEquals("Tiếng Việt", DisplayTextPolicy.clean(source))
    }

    @Test fun d03_mathematicalBoldLettersAreRenderedAsOrdinaryLetters() {
        val input = String(Character.toChars(0x1D400)) + String(Character.toChars(0x1D401))
        assertEquals("AB", DisplayTextPolicy.clean(input))
        assertTrue(DisplayTextPolicy.needsClean(input))
    }

    @Test fun d04_fullwidthAndCircledCharactersAreSafeForDisplay() {
        assertEquals("MS 1", DisplayTextPolicy.clean("\uFF2D\uFF33 \u2460"))
    }

    @Test fun d05_invisibleFormatAndBidiOverrideAreRemoved() {
        assertEquals("NhạcTest", DisplayTextPolicy.clean("Nhạc\u200B\u202ETest\uFEFF"))
    }

    @Test fun d06_blankAndNullHandlingIsPredictable() {
        assertEquals("", DisplayTextPolicy.clean(""))
        assertEquals("", DisplayTextPolicy.clean("   "))
        assertNull(DisplayTextPolicy.cleanOrNull(null))
        assertEquals("Playlist", DisplayTextPolicy.cleanOrNull(" Playlist "))
    }

    @Test fun d07_lineBreaksBecomeSpacesInSingleLineMetadata() {
        assertEquals("Nguồn Nhạc Việt", DisplayTextPolicy.clean("Nguồn\nNhạc\tViệt"))
    }

    @Test fun d08_unrelatedEmojiAndPunctuationAreNotAltered() {
        val sample = "🎵 Trữ nhạc — 2026!"
        assertEquals(sample, DisplayTextPolicy.clean(sample))
        assertFalse(DisplayTextPolicy.needsClean(sample))
    }

    @Test fun d09_sourceInputIsUnchangedAfterRendering() {
        val original = "\uFF21BCD\u200B"
        val before = original.toCharArray()
        assertEquals("ABCD", DisplayTextPolicy.clean(original))
        assertEquals(String(before), original)
    }
}
