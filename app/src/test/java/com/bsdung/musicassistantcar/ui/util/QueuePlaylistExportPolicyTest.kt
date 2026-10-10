package com.bsdung.musicassistantcar.ui.util

import org.junit.Assert.*
import org.junit.Test

class QueuePlaylistExportPolicyTest {
    @Test fun emptyQueueHasNoExport() {
        assertTrue(QueuePlaylistExportPolicy.plan(emptyList()) is QueuePlaylistExport.Empty)
    }

    @Test fun mixedQueueExportsOnlyServerResolvableMediaIdsInOrder() {
        val result = QueuePlaylistExportPolicy.plan(listOf(
            "track:12", "device:42", "drive-file:2482", "track:3",
            "track:12", "drive-file:2482", "https://example.org/item", "drive-file:999",
        )) as QueuePlaylistExport.Ready
        assertEquals(listOf("track:12", "drive-file:2482", "track:3", "drive-file:999"), result.mediaIds)
        assertEquals(8, result.sourceCount)
        assertEquals(2, result.ignoredUnsupported)
        assertEquals(2, result.ignoredDuplicates)
    }

    @Test fun unsupportedOrMalformedOnlyHasNoExport() {
        val result = QueuePlaylistExportPolicy.plan(
            listOf("device:7", "drive-file:abc", "track:0", "track:-1", "track:", "foo")
        )
        assertTrue(result is QueuePlaylistExport.Empty)
    }

    @Test fun exactly100IsOneBatch() {
        val ids = (1..100).map { "track:$it" }
        val result = QueuePlaylistExportPolicy.plan(ids) as QueuePlaylistExport.Ready
        assertEquals(1, result.batches.size)
        assertEquals(ids, result.batches.single())
    }

    @Test fun chunks101To100PlusOneWithoutChangingOrder() {
        val ids = (1..101).map { "drive-file:$it" }
        val result = QueuePlaylistExportPolicy.plan(ids) as QueuePlaylistExport.Ready
        assertEquals(listOf(100, 1), result.batches.map { it.size })
        assertEquals(ids, result.batches.flatten())
    }

    @Test fun chunksExactly2000AndNeverDropsTheLastItem() {
        val ids = (1..QueuePlaylistExportPolicy.MAX_QUEUE_ITEMS).map { "track:$it" }
        val result = QueuePlaylistExportPolicy.plan(ids) as QueuePlaylistExport.Ready
        assertEquals(20, result.batches.size)
        assertEquals(ids, result.batches.flatten())
    }

    @Test fun rejectsMoreThan2000WithoutPartialExport() {
        val result = QueuePlaylistExportPolicy.plan((1..2001).map { "track:$it" })
        assertTrue(result is QueuePlaylistExport.TooLarge)
        result as QueuePlaylistExport.TooLarge
        assertEquals(2001, result.queueSize)
        assertEquals(2000, result.limit)
    }

    @Test fun normalizesWhitespaceOnlyAndDoesNotSilentlyMapLocalIds() {
        val result = QueuePlaylistExportPolicy.plan(
            listOf(" track:22 ", "drive-file:22 ", " device:22 ")
        ) as QueuePlaylistExport.Ready
        assertEquals(listOf("track:22", "drive-file:22"), result.mediaIds)
        assertEquals(1, result.ignoredUnsupported)
    }
}
