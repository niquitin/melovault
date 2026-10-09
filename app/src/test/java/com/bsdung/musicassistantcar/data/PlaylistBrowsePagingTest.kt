package com.bsdung.musicassistantcar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistBrowsePagingTest {
    private val songs = listOf("+ Playlist mới") + (1..65).map { "Playlist $it" }

    @Test fun firstPageContainsCreateActionOnlyOnce() {
        val page = PlaylistBrowsePaging.page(songs, 0, 30)
        assertEquals(30, page.size)
        assertEquals("+ Playlist mới", page.first())
        assertEquals("Playlist 29", page.last())
    }

    @Test fun subsequentPagesDoNotRepeatOrSkipPlaylists() {
        val second = PlaylistBrowsePaging.page(songs, 1, 30)
        assertEquals("Playlist 30", second.first())
        assertEquals("Playlist 59", second.last())
        val third = PlaylistBrowsePaging.page(songs, 2, 30)
        assertEquals(listOf("Playlist 60", "Playlist 61", "Playlist 62", "Playlist 63", "Playlist 64", "Playlist 65"), third)
        assertEquals(songs, (0..2).flatMap { PlaylistBrowsePaging.page(songs, it, 30) })
    }

    @Test fun shortCatalogReturnsOnlyFirstPage() {
        assertEquals(listOf("+ Playlist mới", "Playlist 1"), PlaylistBrowsePaging.page(songs.take(2), 0, 30))
        assertTrue(PlaylistBrowsePaging.page(songs.take(2), 1, 30).isEmpty())
    }

    @Test fun invalidPageAndSizesCannotCrashOrOverflow() {
        assertEquals(songs.take(30), PlaylistBrowsePaging.page(songs, -1, 30))
        assertTrue(PlaylistBrowsePaging.page(songs, Int.MAX_VALUE, 30).isEmpty())
        assertTrue(PlaylistBrowsePaging.page(songs, 0, 0).isEmpty())
        assertTrue(PlaylistBrowsePaging.page(songs, 0, -9).isEmpty())
    }
}
