package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.ShareableLyricsCache.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShareableLyricsCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 10_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun root() = File(tmp.root, "melo_lyrics_v1")

    private fun cache(maxEntries: Int = 3_000, maxBytes: Long = 20L * 1024 * 1024) =
        ShareableLyricsCache(root(), nowMs = { now }, maxEntries = maxEntries, maxBytes = maxBytes)

    private fun found(manual: Boolean = false, at: Long = now) =
        Entry(lrclibId = 7, synced = "[00:01.00]a", plain = "a", instrumental = false, fetchedAtMs = at, manual = manual)

    private val sig = "a".repeat(64)

    private fun sig(i: Int) = i.toString(16).padStart(64, '0')

    @Test
    fun roundTripKeepsEveryField() {
        val c = cache()
        c.put("user1", sig, Entry(9, "[00:01.00]xin chào", "xin chào", false, now, manual = true))
        val back = c.get("user1", sig)!!
        assertEquals(9L, back.lrclibId)
        assertEquals("[00:01.00]xin chào", back.synced)
        assertEquals("xin chào", back.plain)
        assertFalse(back.instrumental)
        assertTrue(back.manual)
        assertFalse(back.noMatch)
    }

    @Test
    fun nullTextsStayNullAndInstrumentalSurvives() {
        val c = cache()
        c.put("u", sig, Entry(3, null, null, true, now, manual = false))
        val back = c.get("u", sig)!!
        assertNull(back.synced)
        assertNull(back.plain)
        assertTrue(back.instrumental)
    }

    @Test
    fun missingEntryIsNull() {
        assertNull(cache().get("u", sig))
    }

    @Test
    fun accountsDoNotSeeEachOthersLyrics() {
        val c = cache()
        c.put("alice", sig, found())
        assertNotNull(c.get("alice", sig))
        assertNull(c.get("bob", sig))
    }

    @Test
    fun foundLyricsExpireAfterNinetyDays() {
        val c = cache()
        c.put("u", sig, found())
        now += 90 * day
        assertNotNull(c.get("u", sig))
        now += 1
        assertNull(c.get("u", sig))
        assertFalse(File(root(), "u/$sig.json").exists())
    }

    @Test
    fun noMatchExpiresAfterThreeDays() {
        val c = cache()
        c.putNoMatch("u", sig)
        assertTrue(c.get("u", sig)!!.noMatch)
        now += 3 * day + 1
        assertNull(c.get("u", sig))
    }

    @Test
    fun manualChoiceNeverExpires() {
        val c = cache()
        c.put("u", sig, found(manual = true))
        now += 3_650 * day
        assertNotNull(c.get("u", sig))
    }

    @Test
    fun corruptFileIsDroppedNotThrown() {
        val c = cache()
        c.put("u", sig, found())
        val file = File(root(), "u/$sig.json")
        file.writeText("{broken")
        assertNull(c.get("u", sig))
        assertFalse(file.exists())
    }

    @Test
    fun unknownVersionIsIgnored() {
        val c = cache()
        c.put("u", sig, found())
        File(root(), "u/$sig.json").writeText("""{"v":99,"lrclibId":1,"fetchedAtMs":$now}""")
        assertNull(c.get("u", sig))
    }

    @Test
    fun entryLimitDropsLeastRecentlyUsed() {
        val c = cache(maxEntries = 3)
        for (i in 1..3) {
            c.put("u", sig(i), found())
            now += 1_000
        }
        now += 1_000
        assertNotNull(c.get("u", sig(1)))
        now += 1_000
        c.put("u", sig(4), found())
        assertEquals(3, c.entryCount("u"))
        assertNotNull("recently read entry stays", c.get("u", sig(1)))
        assertNull("oldest unread entry goes", c.get("u", sig(2)))
        assertNotNull(c.get("u", sig(4)))
    }

    @Test
    fun byteLimitDropsOldestUntilItFits() {
        val big = "x".repeat(400)
        val c = cache(maxBytes = 1_000)
        for (i in 1..5) {
            c.put("u", sig(i), Entry(i.toLong(), null, big, false, now, false))
            now += 1_000
        }
        val total = File(root(), "u").listFiles { f -> f.name.endsWith(".json") }!!.sumOf { it.length() }
        assertTrue("bytes $total", total <= 1_000)
        assertNotNull(c.get("u", sig(5)))
        assertNull(c.get("u", sig(1)))
    }

    @Test
    fun clearOwnerRemovesOnlyThatAccount() {
        val c = cache()
        c.put("alice", sig, found())
        c.put("bob", sig, found())
        c.clearOwner("alice")
        assertNull(c.get("alice", sig))
        assertNotNull(c.get("bob", sig))
        assertFalse(File(root(), "alice").exists())
    }

    @Test
    fun removeForgetsOneSongIncludingManualChoice() {
        val c = cache()
        c.put("u", sig, found(manual = true))
        c.put("u", sig(2), found())
        c.remove("u", sig)
        assertNull(c.get("u", sig))
        assertNotNull(c.get("u", sig(2)))
    }

    @Test
    fun hostileOwnerIdCannotEscapeTheCacheFolder() {
        val c = cache()
        c.put("../../evil", sig, found())
        c.put("a/b", sig, found())
        assertNotNull(c.get("../../evil", sig))
        val outside = tmp.root.walkTopDown().filter { it.isFile }.filterNot { it.absolutePath.startsWith(root().absolutePath) }.toList()
        assertTrue("files outside root: $outside", outside.isEmpty())
        assertNull(c.get("", sig))
    }

    @Test
    fun hostileSignatureIsRefused() {
        val c = cache()
        c.put("u", "../x", found())
        c.put("u", "", found())
        assertNull(c.get("u", "../x"))
        assertEquals(0, c.entryCount("u"))
    }

    @Test
    fun noTempFilesAreLeftBehind() {
        val c = cache()
        c.put("u", sig, found())
        c.put("u", sig, found())
        val names = File(root(), "u").list()!!.toList()
        assertEquals(listOf("$sig.json"), names)
    }

    @Test
    fun overwritingReplacesTheOldEntry() {
        val c = cache()
        c.put("u", sig, found())
        c.put("u", sig, Entry(1, null, "new", false, now, true))
        val back = c.get("u", sig)!!
        assertEquals("new", back.plain)
        assertTrue(back.manual)
    }

    @Test
    fun cacheNeverTouchesAnythingOutsideItsRoot() {
        val sibling = tmp.newFolder("tdlib_db")
        File(sibling, "keep.bin").writeText("tdlib")
        val c = cache()
        c.put("u", sig, found())
        c.clearOwner("u")
        assertEquals("tdlib", File(sibling, "keep.bin").readText())
    }
}
