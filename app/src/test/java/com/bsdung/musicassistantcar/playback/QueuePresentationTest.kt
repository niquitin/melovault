package com.bsdung.musicassistantcar.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueuePresentationTest {
    @Test fun emptyQueuePromptsLibrarySelection() {
        assertEquals("Mở Thư viện để chọn bài", QueueHelper.formatQueueSummary(-1, 0))
    }

    @Test fun oneItemQueueDoesNotSuggestReordering() {
        assertEquals(
            "Không còn bài tiếp theo · Chạm bài để phát lại",
            QueueHelper.formatQueueSummary(0, 1),
        )
    }

    @Test fun multipleUpcomingItemsExplainsDirectPlayAndReorder() {
        assertEquals(
            "3 bài tiếp theo · Chạm bài để phát · Kéo để sắp xếp",
            QueueHelper.formatQueueSummary(1, 5),
        )
    }

    @Test fun invalidIndexesStaySafeAndAccurate() {
        assertEquals("Mở Thư viện để chọn bài", QueueHelper.formatQueueSummary(-1, 3))
        assertEquals("Mở Thư viện để chọn bài", QueueHelper.formatQueueSummary(3, 3))
    }

    @Test fun currentRowNeverClaimsPlayingWhenPaused() {
        assertEquals("Bài hiện tại", QueueHelper.currentTrackBadge(false))
        assertEquals("Đang phát", QueueHelper.currentTrackBadge(true))
    }
}
