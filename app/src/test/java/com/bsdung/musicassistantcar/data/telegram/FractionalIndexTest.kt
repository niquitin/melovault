package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random

/** Nhiều Playlist có tên, S1: the order keys. Pure JVM. */
class FractionalIndexTest {
    private fun assertBetween(a: String?, b: String?, key: String) {
        assertTrue("key=$key must not be empty", key.isNotEmpty())
        assertFalse("key=$key must not end in 0", key.endsWith("0"))
        if (a != null) assertTrue("a=$a < key=$key", a < key)
        if (b != null) assertTrue("key=$key < b=$b", key < b)
    }

    @Test fun middleKeyWhenBothEndsAreOpen() {
        val key = FractionalIndex.between(null, null)
        assertBetween(null, null, key)
        assertTrue(FractionalIndex.isValid(key))
    }

    @Test fun insertingAtTheStartAlwaysGoesBeforeTheFirstKey() {
        var first = FractionalIndex.between(null, null)
        repeat(300) {
            val key = FractionalIndex.between(null, first)
            assertBetween(null, first, key)
            first = key
        }
    }

    @Test fun insertingAtTheEndAlwaysGoesAfterTheLastKey() {
        var last = FractionalIndex.between(null, null)
        repeat(300) {
            val key = FractionalIndex.between(last, null)
            assertBetween(last, null, key)
            last = key
        }
    }

    @Test fun adjacentDigitsAndPrefixesStillHaveRoomBetween() {
        val pairs = listOf(
            "1" to "2", "0V" to "1", "z" to null, "Vz" to "W", "A" to "A1", "A" to "A0V", "01" to "02", "zz" to "zzz1",
            "1" to "10001", "Az" to "B", "a" to "b", "09" to "1",
        )
        for ((a, b) in pairs) {
            val key = FractionalIndex.between(a, b)
            assertBetween(a, b, key)
        }
    }

    @Test fun neverReturnsAnEmptyKeyAndKeepsTheOrderOver10000RandomInserts() {
        val random = Random(20261101L)
        val keys = ArrayList<String>()
        repeat(10_000) {
            val at = random.nextInt(keys.size + 1)
            val a = keys.getOrNull(at - 1)
            val b = keys.getOrNull(at)
            val key = FractionalIndex.between(a, b)
            assertBetween(a, b, key)
            keys.add(at, key)
        }
        assertEquals(10_000, keys.size)
        assertEquals(keys.sorted(), keys)
        assertEquals("every key is distinct", keys.size, keys.toSet().size)
        assertTrue("random inserts stay short", keys.maxOf { it.length } < 40)
    }

    @Test fun repeatedInsertsInOneSpotGrowSlowlyAndCanBeRespaced() {
        val a = "1"
        var b = "2"
        var longest = 0
        repeat(400) {
            b = FractionalIndex.between(a, b)
            assertBetween(a, null, b)
            longest = maxOf(longest, b.length)
        }
        assertTrue("grows by about one character per few inserts", longest > FractionalIndex.MAX_LEN)
        assertFalse(FractionalIndex.isValid(b))
        val respaced = FractionalIndex.evenly(401)
        assertTrue(respaced.all { FractionalIndex.isValid(it) })
    }

    @Test fun evenlyGivesStrictlyIncreasingShortKeys() {
        for (count in listOf(0, 1, 2, 3, 7, 100, 1000)) {
            val keys = FractionalIndex.evenly(count)
            assertEquals(count, keys.size)
            assertEquals(keys.sorted(), keys)
            assertEquals(count, keys.toSet().size)
            assertTrue(keys.all { FractionalIndex.isValid(it) })
            if (count > 0) assertTrue("count=$count keeps keys short", keys.maxOf { it.length } <= 16)
        }
    }

    @Test fun evenlyKeysStillLeaveRoomAroundAndBetweenThem() {
        val keys = FractionalIndex.evenly(50)
        assertBetween(null, keys.first(), FractionalIndex.between(null, keys.first()))
        assertBetween(keys.last(), null, FractionalIndex.between(keys.last(), null))
        for (i in 0 until keys.size - 1) assertBetween(keys[i], keys[i + 1], FractionalIndex.between(keys[i], keys[i + 1]))
    }

    @Test fun isValidRejectsEmptyTrailingZeroForeignCharactersAndTooLong() {
        assertTrue(FractionalIndex.isValid("V"))
        assertTrue(FractionalIndex.isValid("a1Z"))
        assertTrue(FractionalIndex.isValid("1".repeat(FractionalIndex.MAX_LEN)))
        assertFalse(FractionalIndex.isValid(""))
        assertFalse(FractionalIndex.isValid("10"))
        assertFalse(FractionalIndex.isValid("0"))
        assertFalse(FractionalIndex.isValid("a b"))
        assertFalse(FractionalIndex.isValid("a-b"))
        assertFalse(FractionalIndex.isValid("é"))
        assertFalse(FractionalIndex.isValid("1".repeat(FractionalIndex.MAX_LEN + 1)))
    }

    @Test fun badArgumentsAreRefused() {
        for ((a, b) in listOf("2" to "1", "1" to "1", "10" to null, "" to null, null to "10", null to "")) {
            try {
                FractionalIndex.between(a, b)
                fail("a=$a b=$b")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
