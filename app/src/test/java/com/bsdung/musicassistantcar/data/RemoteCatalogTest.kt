package com.bsdung.musicassistantcar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCatalogTest {
    @Test
    fun playbackQualityTogglesCorrectly() {
        PlaybackQuality.current = StreamQuality.ORIGINAL
        assertEquals(StreamQuality.ORIGINAL, PlaybackQuality.current)

        val next = PlaybackQuality.toggle()
        assertEquals(StreamQuality.MOBILE_320, next)
        assertEquals(StreamQuality.MOBILE_320, PlaybackQuality.current)

        val back = PlaybackQuality.toggle()
        assertEquals(StreamQuality.ORIGINAL, back)
        assertEquals(StreamQuality.ORIGINAL, PlaybackQuality.current)
    }

    @Test
    fun streamQualityValuesMatchBackendExpectations() {
        assertEquals("original", StreamQuality.ORIGINAL.queryValue)
        assertEquals("mobile", StreamQuality.MOBILE_320.queryValue)
    }

    @Test
    fun catalogPrefixesAreStandardized() {
        assertEquals("root", RemoteCatalog.ROOT_ID)
        assertEquals("category:device_local", RemoteCatalog.DEVICE_LOCAL_ID)
        assertEquals("category:downloaded", RemoteCatalog.DOWNLOADED_ID)
        assertEquals("category:recent", RemoteCatalog.RECENT_ID)
        assertEquals("source:all", RemoteCatalog.ALL_ID)
        assertEquals("source:drive", RemoteCatalog.DRIVE_ID)
        assertEquals("source:telegram", RemoteCatalog.TELEGRAM_ID)
        assertEquals("category:playlists", RemoteCatalog.PLAYLISTS_ID)
        assertEquals("drive-file:", RemoteCatalog.DRIVE_FILE_PREFIX)
        assertEquals("drive-root:", RemoteCatalog.DRIVE_ROOT_PREFIX)
        assertEquals("drive-folder:", RemoteCatalog.DRIVE_FOLDER_PREFIX)
        assertEquals("track:", RemoteCatalog.TRACK_PREFIX)
        assertEquals("device:", RemoteCatalog.DEVICE_PREFIX)
    }

    @Test
    fun testRootChildrenReturnsExpectedItems() {
        val catalog = RemoteCatalog()
        val root = catalog.rootChildren()
        println("ROOT_ITEMS_COUNT = ${root.size}")
        root.forEach { item ->
            println("ITEM: id=${item.mediaId}, title=${item.mediaMetadata.title}, browsable=${item.mediaMetadata.isBrowsable}")
        }
        assertEquals(7, root.size)
        assertEquals(
            listOf(
                "category:device_local",
                "category:downloaded",
                "category:recent",
                "source:all",
                "source:drive",
                "source:telegram",
                "category:playlists",
            ),
            root.map { it.mediaId },
        )
    }
}
