package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloRowArtPolicy.ArtChange
import com.bsdung.musicassistantcar.data.telegram.MeloRowArtPolicy.Blur
import com.bsdung.musicassistantcar.data.telegram.MeloRowArtPolicy.MissCache
import com.bsdung.musicassistantcar.data.telegram.MeloRowArtPolicy.Scheduler
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloRowArtPolicyTest {

    private val miniBytes = byteArrayOf(0x1, 0x2, 0x3, 0x4, 0x5)
    private val miniB64 = java.util.Base64.getEncoder().encodeToString(miniBytes)

    private fun file(id: Int, completed: Boolean, path: String = ""): String =
        """{"@type":"file","id":$id,"size":9000,"local":{"path":"$path","can_be_downloaded":true,"is_downloading_completed":$completed}}"""

    private fun audio(cover: String?, mini: String?): JSONObject {
        val parts = buildList {
            if (mini != null) add(""""album_cover_minithumbnail":$mini""")
            if (cover != null) add(""""album_cover_thumbnail":$cover""")
        }.joinToString(",")
        return JSONObject("""{"@type":"message","content":{"@type":"messageAudio","audio":{"duration":200${if (parts.isEmpty()) "" else ",$parts"}}}}""")
    }

    private fun mini() = """{"@type":"minithumbnail","width":40,"height":40,"data":"$miniB64"}"""

    private fun thumb(format: String, fileJson: String, w: Int = 320, h: Int = 320) =
        """{"@type":"thumbnail","format":{"@type":"$format"},"width":$w,"height":$h,"file":$fileJson}"""

    @Test
    fun audioWithCoverFileAndMinithumb() {
        val t = MeloRowArtPolicy.pickThumb(audio(thumb("thumbnailFormatJpeg", file(7, false)), mini()))!!
        assertArrayEquals(miniBytes, t.mini)
        assertEquals(7, t.fileId)
        assertFalse(t.downloaded)
        assertNull(t.localPath)
        assertEquals(320, t.width)
        assertEquals(320, t.height)
    }

    @Test
    fun audioCoverAlreadyDownloadedKeepsItsPath() {
        val t = MeloRowArtPolicy.pickThumb(audio(thumb("thumbnailFormatJpeg", file(9, true, "/files/thumbs/9.jpg")), null))!!
        assertNull(t.mini)
        assertEquals(9, t.fileId)
        assertTrue(t.downloaded)
        assertEquals("/files/thumbs/9.jpg", t.localPath)
    }

    @Test
    fun completedFlagWithoutPathIsNotDownloaded() {
        val t = MeloRowArtPolicy.pickThumb(audio(thumb("thumbnailFormatJpeg", file(9, true, "")), null))!!
        assertFalse(t.downloaded)
        assertEquals(9, t.fileId)
    }

    @Test
    fun audioWithoutAnyCoverHasNoThumb() {
        assertNull(MeloRowArtPolicy.pickThumb(audio(null, null)))
    }

    @Test
    fun audioWithOnlyMinithumbIsUsable() {
        val t = MeloRowArtPolicy.pickThumb(audio(null, mini()))!!
        assertNull(t.fileId)
        assertNotNull(t.mini)
        assertTrue(t.usable)
    }

    @Test
    fun documentThumbnailIsRead() {
        val json = JSONObject(
            """{"content":{"@type":"messageDocument","document":{"file_name":"a.mp3","minithumbnail":${mini()},"thumbnail":${thumb("thumbnailFormatWebp", file(11, false), 90, 90)}}}}"""
        )
        val t = MeloRowArtPolicy.pickThumb(json)!!
        assertEquals(11, t.fileId)
        assertArrayEquals(miniBytes, t.mini)
        assertEquals(90, t.width)
    }

    @Test
    fun pngIsAcceptedToo() {
        assertEquals(5, MeloRowArtPolicy.pickThumb(audio(thumb("thumbnailFormatPng", file(5, false)), null))!!.fileId)
    }

    @Test
    fun animatedAndStickerFormatsAreSkipped() {
        listOf("thumbnailFormatTgs", "thumbnailFormatWebm", "thumbnailFormatMpeg4", "thumbnailFormatGif", "thumbnailFormatXyz", "").forEach { format ->
            assertNull("$format without mini", MeloRowArtPolicy.pickThumb(audio(thumb(format, file(5, false)), null)))
            val withMini = MeloRowArtPolicy.pickThumb(audio(thumb(format, file(5, false)), mini()))!!
            assertNull("$format keeps no file", withMini.fileId)
            assertNotNull(withMini.mini)
        }
    }

    @Test
    fun otherMessageTypesHaveNoThumb() {
        assertNull(MeloRowArtPolicy.pickThumb(JSONObject("""{"content":{"@type":"messageText","text":{"text":"hi"}}}""")))
        assertNull(MeloRowArtPolicy.pickThumb(JSONObject("""{"content":{"@type":"messagePhoto"}}""")))
        assertNull(MeloRowArtPolicy.pickThumb(JSONObject("{}")))
        assertNull(MeloRowArtPolicy.pickThumb(null))
    }

    @Test
    fun brokenMinithumbIsDropped() {
        val bad = """{"@type":"minithumbnail","width":4,"height":4,"data":"@@not base64@@"}"""
        assertNull(MeloRowArtPolicy.pickThumb(audio(null, bad)))
        val empty = """{"@type":"minithumbnail","width":4,"height":4,"data":""}"""
        assertNull(MeloRowArtPolicy.pickThumb(audio(null, empty)))
        val huge = """{"@type":"minithumbnail","width":4,"height":4,"data":"${"A".repeat(40_000)}"}"""
        assertNull(MeloRowArtPolicy.pickThumb(audio(null, huge)))
    }

    @Test
    fun fileIdZeroOrMissingIsNotAFile() {
        assertNull(MeloRowArtPolicy.pickThumb(audio(thumb("thumbnailFormatJpeg", file(0, false)), null)))
        assertNull(MeloRowArtPolicy.pickThumb(audio("""{"@type":"thumbnail","format":{"@type":"thumbnailFormatJpeg"}}""", null)))
    }

    @Test
    fun chatPhotoSmallFile() {
        val chat = JSONObject("""{"@type":"chat","id":-100,"photo":{"small":${file(21, false)},"big":${file(22, false)},"minithumbnail":${mini()}}}""")
        val t = MeloRowArtPolicy.pickChatPhoto(chat)!!
        assertEquals(21, t.fileId)
        assertArrayEquals(miniBytes, t.mini)
    }

    @Test
    fun chatWithoutPhotoHasNone() {
        assertNull(MeloRowArtPolicy.pickChatPhoto(JSONObject("""{"@type":"chat","id":-100,"title":"x"}""")))
        assertNull(MeloRowArtPolicy.pickChatPhoto(null))
    }

    @Test
    fun completedPathAndFileOfEvents() {
        val done = JSONObject(file(3, true, "/a/b.jpg"))
        assertEquals("/a/b.jpg", MeloRowArtPolicy.completedPath(done))
        assertNull(MeloRowArtPolicy.completedPath(JSONObject(file(3, false, "/a/b.jpg"))))
        assertNull(MeloRowArtPolicy.completedPath(null))
        val update = JSONObject("""{"@type":"updateFile","file":${file(3, true, "/a/b.jpg")}}""")
        assertEquals(3, MeloRowArtPolicy.fileOf(update)!!.getInt("id"))
        assertEquals(3, MeloRowArtPolicy.fileOf(done)!!.getInt("id"))
        assertNull(MeloRowArtPolicy.fileOf(JSONObject("""{"@type":"updateAuthorizationState"}""")))
    }

    @Test
    fun keysCarryTheAccount() {
        assertNotEquals(MeloRowArtPolicy.telegramKey(1, 2, 3), MeloRowArtPolicy.telegramKey(9, 2, 3))
        assertEquals("tg:1:2:3", MeloRowArtPolicy.telegramKey(1, 2, 3))
        assertNotEquals(MeloRowArtPolicy.chatKey(1, 2), MeloRowArtPolicy.chatKey(9, 2))
        assertEquals("local:42", MeloRowArtPolicy.localKey(42))
        assertFalse(MeloRowArtPolicy.telegramKey(1, 2, 3).startsWith("local:"))
    }

    @Test
    fun decodeSampleUsesPixelsNotBytes() {
        assertEquals(8, MeloRowArtPolicy.decodeSample(2000, 2000, 192))
        assertEquals(1, MeloRowArtPolicy.decodeSample(320, 320, 192))
        assertEquals(2, MeloRowArtPolicy.decodeSample(400, 400, 192))
        assertEquals(1, MeloRowArtPolicy.decodeSample(100, 100, 192))
        // The shorter side decides, so a banner is not shrunk below the target.
        assertEquals(2, MeloRowArtPolicy.decodeSample(4000, 400, 192))
        assertEquals(1, MeloRowArtPolicy.decodeSample(0, 100, 192))
        assertEquals(1, MeloRowArtPolicy.decodeSample(100, -1, 192))
        assertEquals(1, MeloRowArtPolicy.decodeSample(2000, 2000, 0))
    }

    @Test
    fun targetNeverExceedsTheCap() {
        assertEquals(192, MeloRowArtPolicy.targetPx(500))
        assertEquals(112, MeloRowArtPolicy.targetPx(112))
        assertEquals(1, MeloRowArtPolicy.targetPx(0))
    }

    @Test
    fun embeddedPictureBounds() {
        assertTrue(MeloRowArtPolicy.embeddedAllowed(1))
        assertTrue(MeloRowArtPolicy.embeddedAllowed(MeloRowArtPolicy.MAX_EMBEDDED_BYTES))
        assertFalse(MeloRowArtPolicy.embeddedAllowed(0))
        assertFalse(MeloRowArtPolicy.embeddedAllowed(MeloRowArtPolicy.MAX_EMBEDDED_BYTES + 1))
    }

    @Test
    fun nothingLoadsWhileFlinging() {
        assertFalse(MeloRowArtPolicy.shouldLoad(MeloRowArtPolicy.SCROLL_STATE_FLING))
        assertTrue(MeloRowArtPolicy.shouldLoad(0))
        assertTrue(MeloRowArtPolicy.shouldLoad(1))
    }

    @Test
    fun rowsToLoadAreVisiblePlusFour() {
        assertEquals(10..18, MeloRowArtPolicy.rowsToLoad(10, 5, 100))
        assertEquals(95..99, MeloRowArtPolicy.rowsToLoad(95, 5, 100))
        assertTrue(MeloRowArtPolicy.rowsToLoad(0, 0, 100).isEmpty())
        assertTrue(MeloRowArtPolicy.rowsToLoad(0, 5, 0).isEmpty())
        assertTrue(MeloRowArtPolicy.rowsToLoad(-1, 5, 100).isEmpty())
        assertTrue(MeloRowArtPolicy.rowsToLoad(100, 5, 100).isEmpty())
    }

    @Test
    fun staleGenerationIsDropped() {
        assertTrue(MeloRowArtPolicy.isCurrent(4, 4))
        assertFalse(MeloRowArtPolicy.isCurrent(3, 4))
    }

    @Test
    fun schedulerSendsNewestFirstAndAtMostThree() {
        val s = Scheduler()
        listOf("a", "b", "c", "d", "e").forEach { assertTrue(s.request(it)) }
        assertEquals("e", s.next())
        assertEquals("d", s.next())
        assertEquals("c", s.next())
        assertNull("limit of 3 in flight", s.next())
        assertEquals(3, s.inFlightCount)
        s.done("d")
        assertEquals("b", s.next())
        assertNull(s.next())
        s.done("e"); s.done("c"); s.done("b")
        assertEquals("a", s.next())
        assertNull(s.next())
    }

    @Test
    fun schedulerIgnoresDuplicatesAndMovesAskedAgainToTheFront() {
        val s = Scheduler()
        assertTrue(s.request("a"))
        assertTrue(s.request("b"))
        assertFalse("already waiting", s.request("a"))
        assertEquals(2, s.pendingCount)
        assertEquals("a", s.next())
        assertFalse("running", s.request("a"))
        assertEquals("b", s.next())
    }

    @Test
    fun schedulerDropsRowsThatLeftTheScreen() {
        val s = Scheduler()
        listOf("a", "b", "c").forEach { s.request(it) }
        assertEquals("c", s.next())
        s.retain(setOf("a"))
        assertEquals(1, s.pendingCount)
        assertEquals("a", s.next())
        assertNull(s.next())
        // A request already running is not cancelled by retain().
        assertEquals(2, s.inFlightCount)
    }

    @Test
    fun schedulerClearForgetsEverything() {
        val s = Scheduler()
        s.request("a"); s.request("b"); s.next()
        s.clear()
        assertEquals(0, s.pendingCount)
        assertEquals(0, s.inFlightCount)
        assertNull(s.next())
    }

    @Test
    fun blurPlanSkipsTheBlurWhenTheSharpCoverIsOnDisk() {
        listOf(true, false).forEach { onDisk ->
            listOf(true, false).forEach { toDownload ->
                assertEquals(Blur.NONE, MeloRowArtPolicy.blurPlan(false, onDisk, toDownload))
            }
        }
        assertEquals(Blur.NONE, MeloRowArtPolicy.blurPlan(true, true, true))
        assertEquals(Blur.NONE, MeloRowArtPolicy.blurPlan(true, true, false))
        assertEquals(Blur.LATER, MeloRowArtPolicy.blurPlan(true, false, true))
        assertEquals(Blur.NOW, MeloRowArtPolicy.blurPlan(true, false, false))
    }

    @Test
    fun rowPictureLoaderWinsLegacyFillsIn() {
        assertEquals("a", MeloRowArtPolicy.rowPicture("a", "b"))
        assertEquals("b", MeloRowArtPolicy.rowPicture(null, "b"))
        assertNull(MeloRowArtPolicy.rowPicture<String>(null, null))
    }

    @Test
    fun artChangeNeverTakesAPictureAway() {
        assertEquals(ArtChange.NONE, MeloRowArtPolicy.artChange(showing = true, sameAsShown = false, incoming = false))
        assertEquals(ArtChange.NONE, MeloRowArtPolicy.artChange(showing = false, sameAsShown = false, incoming = false))
        assertEquals(ArtChange.APPEAR, MeloRowArtPolicy.artChange(showing = false, sameAsShown = false, incoming = true))
        assertEquals(ArtChange.NONE, MeloRowArtPolicy.artChange(showing = true, sameAsShown = true, incoming = true))
        assertEquals(ArtChange.SWAP, MeloRowArtPolicy.artChange(showing = true, sameAsShown = false, incoming = true))
    }

    @Test
    fun sendOrderPutsTheTopRowFirstOnTheScheduler() {
        val rows = (0..8).map { "r$it" }
        assertEquals(rows.reversed(), MeloRowArtPolicy.sendOrder(rows))
        val s = Scheduler()
        MeloRowArtPolicy.sendOrder(rows).forEach { s.request(it) }
        assertEquals("r0", s.next())
        assertEquals("r1", s.next())
        assertEquals("r2", s.next())
        assertNull(s.next())
    }

    @Test
    fun blurAndFadeTimingsAreTheContract() {
        assertEquals(400L, MeloRowArtPolicy.BLUR_DELAY_MS)
        assertEquals(150L, MeloRowArtPolicy.FADE_MS)
    }

    @Test
    fun missCacheIsBounded() {
        val m = MissCache(3)
        listOf("a", "b", "c", "d").forEach { m.add(it) }
        assertEquals(3, m.size)
        assertFalse(m.contains("a"))
        assertTrue(m.contains("d"))
        m.clear()
        assertEquals(0, m.size)
    }
}
