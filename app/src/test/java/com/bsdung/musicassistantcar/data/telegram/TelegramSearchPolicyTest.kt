package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class TelegramSearchPolicyTest {

    private fun song(title: String, performer: String? = null, filename: String = "$title.mp3") =
        IndexedTelegramSong(
            accountId = 1L, chatId = 2L, messageId = 3L, songKey = 3L, fileId = 1,
            title = title, filename = filename, performer = performer
        )

    @Test
    fun accentCaseAndOrderVariantsAllMatch() {
        val s = song("Sử Thanh Hóa")
        for (q in listOf("Sử Thanh Hóa", "su thanh hoa", "SU THANH HOA", "hoa su", "SU THANH", "  su   thanh  ")) {
            assertTrue(q, TelegramSearchPolicy.matches(q, s))
        }
    }

    @Test
    fun vietnameseLetters() {
        val key = TelegramSearchPolicy.songSearchKey("Đường Ưa Ơi Ăn Âm Êm Ôi", null, "x")
        assertEquals("duong ua oi an am em oi x", key)
    }

    @Test
    fun nfdAndNfcGiveSameKey() {
        val nfc = Normalizer.normalize("Sử Thanh Hóa", Normalizer.Form.NFC)
        val nfd = Normalizer.normalize("Sử Thanh Hóa", Normalizer.Form.NFD)
        assertEquals(
            TelegramSearchPolicy.songSearchKey(nfc, null, ""),
            TelegramSearchPolicy.songSearchKey(nfd, null, "")
        )
    }

    @Test
    fun blankQueryHasNoFilter() {
        assertTrue(TelegramSearchPolicy.queryTerms("").isEmpty())
        assertTrue(TelegramSearchPolicy.queryTerms("   ").isEmpty())
        assertNull(TelegramSearchPolicy.whereClause(null))
        assertNull(TelegramSearchPolicy.whereClause("  "))
        assertTrue(TelegramSearchPolicy.matches("", song("Anything")))
    }

    @Test
    fun likeWildcardsAreEscaped() {
        assertEquals("%50\\%%", TelegramSearchPolicy.likePattern("50%"))
        assertEquals("%a\\_b%", TelegramSearchPolicy.likePattern("a_b"))
        assertEquals("%a\\\\b%", TelegramSearchPolicy.likePattern("a\\b"))
    }

    @Test
    fun termsCappedAtEight() {
        assertEquals(8, TelegramSearchPolicy.queryTerms("a b c d e f g h i j").size)
        val where = TelegramSearchPolicy.whereClause("a b c d e f g h i j")!!
        assertEquals(16, where.args.size)
    }

    @Test
    fun performerAndFilenameAreSearched() {
        val s = song("Bai Hat", performer = "Đàm Vĩnh Hưng", filename = "track_01.mp3")
        assertTrue(TelegramSearchPolicy.matches("dam vinh hung", s))
        assertTrue(TelegramSearchPolicy.matches("track", s))
    }

    @Test
    fun unrelatedWordDoesNotMatch() {
        assertFalse(TelegramSearchPolicy.matches("lakhouse", song("Sử Thanh Hóa")))
    }
}
