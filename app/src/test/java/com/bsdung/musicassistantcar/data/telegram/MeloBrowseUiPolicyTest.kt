package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloBrowseUiPolicy.Kind
import com.bsdung.musicassistantcar.data.telegram.MeloBrowseUiPolicy.LoadState
import com.bsdung.musicassistantcar.data.telegram.MeloBrowseUiPolicy.Request
import com.bsdung.musicassistantcar.data.telegram.MeloBrowseUiPolicy.Scope
import org.junit.Assert.*
import org.junit.Test

class MeloBrowseUiPolicyTest {
    @Test fun sourceListLoadingEmptyAndFailureKeepTheirOwnScope() {
        val scope = Scope(Kind.SOURCES)
        assertEquals("Đang tải danh sách…", present(scope, LoadState.INITIAL_LOADING).message)
        assertEquals("Chưa có nguồn Telegram", present(scope).message)
        assertEquals("Không tìm thấy nguồn Telegram", present(scope, query = "a").message)
        assertTrue(present(scope, LoadState.ERROR, rows = 2).retry)
    }
    private fun present(scope: Scope, state: LoadState = LoadState.READY, rows: Int = 0, query: String = "") =
        MeloBrowseUiPolicy.present(scope, "Nhạc Việt", state, rows, query)

    @Test fun sevenScopesHaveExplicitSearchHints() {
        val hints = listOf(
            Scope(Kind.LIBRARY) to "Tìm trong thư viện đã đồng bộ…",
            Scope(Kind.CHANNEL, -11L) to "Tìm trong kênh Nhạc Việt…",
            Scope(Kind.DOWNLOADED) to "Tìm trong Đã tải…",
            Scope(Kind.PLAYLIST) to "Tìm trong Playlist…",
            Scope(Kind.SOURCES) to "Tìm nguồn Telegram…",
            Scope(Kind.SAVED_MESSAGES) to "Tìm trong Tin đã lưu…",
            Scope(Kind.LOCAL) to "Tìm trong Nhạc trên máy…"
        )
        hints.forEach { (scope, hint) -> assertEquals(hint, present(scope).hint) }
    }

    @Test fun downloadedWithinChannelNeverClaimsTheWholeLibrary() {
        val ui = present(Scope(Kind.DOWNLOADED, -11L), query = "abc")
        assertEquals("Phạm vi: Đã tải · kênh Nhạc Việt", ui.scopeLabel)
        assertEquals("Không tìm thấy bài trong Đã tải · kênh Nhạc Việt", ui.message)
        assertFalse(ui.scopeLabel.contains("thư viện"))
    }

    @Test fun playlistDownloadedCombinationIsExplicit() {
        val scope = MeloBrowseUiPolicy.scope(false, true, true, true, null)
        assertEquals(Scope(Kind.PLAYLIST, downloadedOnly = true), scope)
        assertEquals("Phạm vi: Playlist · Đã tải", present(scope).scopeLabel)
    }

    @Test fun sourceSelectionUsesIdsAndIgnoresLeftoverChannelForSavedMessages() {
        assertEquals(Scope(Kind.CHANNEL, -5), MeloBrowseUiPolicy.scope(false, false, false, true, -5))
        assertEquals(Scope(Kind.SAVED_MESSAGES), MeloBrowseUiPolicy.scope(false, false, false, false, -5))
        assertEquals(Scope(Kind.SOURCES), MeloBrowseUiPolicy.scope(true, false, true, true, -5))
    }

    @Test(expected = IllegalArgumentException::class) fun channelRequiresId() { Scope(Kind.CHANNEL) }
    @Test(expected = IllegalArgumentException::class) fun channelCannotUseAllSourcesSentinel() { Scope(Kind.CHANNEL, 0) }

    @Test fun duplicateSourceNamesDoNotShareIdentityOrRows() {
        val a = Request(1, 10, Scope(Kind.CHANNEL, -11))
        val b = Request(1, 11, Scope(Kind.CHANNEL, -22))
        assertEquals(present(a.scope).hint, present(b.scope).hint)
        assertNotEquals(a.scope, b.scope)
        assertFalse(MeloBrowseUiPolicy.canKeepRows(a, b))
    }

    @Test fun queryACompletingAfterBIsDroppedIncludingAtoBtoA() {
        val a = Request(1, 10, Scope(Kind.LIBRARY))
        val b = a.copy(generation = 11)
        assertFalse(MeloBrowseUiPolicy.accepts(a, 1, 11, true))
        assertTrue(MeloBrowseUiPolicy.accepts(b, 1, 11, true))
        assertFalse(MeloBrowseUiPolicy.accepts(a, 1, 12, true))
    }

    @Test fun differentOwnerAndEndedLifecycleRejectSuccessAndErrorCallbacks() {
        val request = Request(1, 10, Scope(Kind.PLAYLIST))
        assertFalse(MeloBrowseUiPolicy.accepts(request, 2, 10, true))
        assertFalse(MeloBrowseUiPolicy.accepts(request, 1, 10, false))
        assertFalse(MeloBrowseUiPolicy.accepts(request, 0, 10, true))
        assertFalse(MeloBrowseUiPolicy.accepts(null, 1, 10, true))
    }

    @Test fun refreshKeepsRowsOnlyForTheSameOwnerAndScope() {
        val before = Request(1, 10, Scope(Kind.DOWNLOADED, -11))
        assertTrue(MeloBrowseUiPolicy.canKeepRows(before, before.copy(generation = 11)))
        assertFalse(MeloBrowseUiPolicy.canKeepRows(before, before.copy(ownerId = 2)))
        assertFalse(MeloBrowseUiPolicy.canKeepRows(before, before.copy(scope = Scope(Kind.LIBRARY))))
        assertFalse(MeloBrowseUiPolicy.canKeepRows(null, before))
    }

    @Test fun initialLoadingIsNotAnEmptyResult() {
        val ui = present(Scope(Kind.LIBRARY), LoadState.INITIAL_LOADING, query = "abc")
        assertEquals("Đang tải danh sách…", ui.message)
        assertFalse(ui.retry)
    }

    @Test fun updatingWithRowsSaysSoInsteadOfClaimingNewResults() {
        assertEquals(LoadState.UPDATING, MeloBrowseUiPolicy.loading(true))
        assertEquals(LoadState.INITIAL_LOADING, MeloBrowseUiPolicy.loading(false))
        assertEquals("Đang cập nhật danh sách…", present(Scope(Kind.CHANNEL, -11), LoadState.UPDATING, 2).message)
    }

    @Test fun emptyWithQueryAndEmptyWithoutQueryHaveDifferentMessagesForEveryScope() {
        val scopes = Kind.entries.map { kind -> when (kind) {
            Kind.CHANNEL, Kind.FORUM_TOPICS -> Scope(kind, -11L)
            Kind.FORUM_TOPIC -> Scope(kind, -11L, forumTopicId = 71)
            else -> Scope(kind)
        } }
        scopes.forEach { scope ->
            assertTrue(present(scope).message!!.startsWith("Chưa có"))
            assertTrue(present(scope, query = "  nhạc  ").message!!.startsWith("Không tìm thấy"))
            assertEquals(present(scope).message, present(scope, query = "  ").message)
            assertNull(present(scope, rows = 1).message)
        }
    }

    @Test fun downloadedScanDoesNotShowEmptyEvenWhenNoFilesAreVerifiedYet() {
        for (state in listOf(LoadState.INITIAL_LOADING, LoadState.UPDATING)) {
            assertEquals("Đang kiểm tra tệp trên máy…", present(Scope(Kind.DOWNLOADED), state).message)
            assertEquals("Đang kiểm tra tệp trên máy…", present(Scope(Kind.DOWNLOADED, -11), state, 3).message)
        }
    }

    @Test fun localLoadingNeverUsesTelegramOrOfflineScanningText() {
        assertEquals("Đang tải danh sách…", present(Scope(Kind.LOCAL), LoadState.INITIAL_LOADING).message)
        assertEquals("Đang cập nhật danh sách…", present(Scope(Kind.LOCAL), LoadState.UPDATING, 3).message)
    }

    @Test fun failureOffersRetryAndLabelsOldRows() {
        val empty = present(Scope(Kind.PLAYLIST), LoadState.ERROR)
        assertEquals("Chưa tải được danh sách. Thử lại", empty.message)
        assertTrue(empty.retry)
        val retained = present(Scope(Kind.PLAYLIST), LoadState.ERROR, 4)
        assertTrue(retained.message!!.contains("dữ liệu trước đó"))
        assertEquals(empty.scopeLabel, retained.scopeLabel)
    }

    @Test fun unknownAndPartialCountsNeverTurnIntoAuthoritativeZero() {
        LoadState.entries.filter { it != LoadState.READY }.forEach { state ->
            assertNull(MeloBrowseUiPolicy.authoritativeCount(state, 0))
            assertNull(MeloBrowseUiPolicy.authoritativeCount(state, 20))
        }
        assertNull(MeloBrowseUiPolicy.authoritativeCount(LoadState.READY, null))
        assertNull(MeloBrowseUiPolicy.authoritativeCount(LoadState.READY, -1))
        assertEquals(0, MeloBrowseUiPolicy.authoritativeCount(LoadState.READY, 0))
    }

    @Test fun realPerformerAndChannelAreSeparateMetadata() {
        assertEquals("Ca sĩ A · Kênh: Nhạc Việt", MeloBrowseUiPolicy.rowMetadata("Ca sĩ A", "Nhạc Việt", false, false))
        assertEquals("Kênh: Nhạc Việt", MeloBrowseUiPolicy.rowMetadata(null, "Nhạc Việt", false, false))
        assertEquals("Ca sĩ A", MeloBrowseUiPolicy.rowMetadata("Ca sĩ A", null, false, false))
        assertNull(MeloBrowseUiPolicy.rowMetadata(null, null, false, false))
    }

    @Test fun localFolderIsNeverInventedArtistAndMissingTagsAreOmitted() {
        assertEquals("Thư mục: Music", MeloBrowseUiPolicy.rowMetadata(null, "Music", true, true))
        assertEquals("Ca sĩ A", MeloBrowseUiPolicy.rowMetadata(null, "Ca sĩ A", true, false))
        assertNull(MeloBrowseUiPolicy.rowMetadata("<unknown>", "Unknown artist", false, false))
        assertNull(MeloBrowseUiPolicy.rowMetadata(null, "Nghệ sĩ không rõ", true, false))
    }

    @Test fun metadataForBothThemesKeepsSourceLabelAndDoesNotInventQuality() {
        val metadata = MeloBrowseUiPolicy.rowMetadata("Ca sĩ A", "Nguồn", false, false)
        assertEquals("Ca sĩ A · Kênh: Nguồn", MeloComponentPolicy.themedSecondLine(
            MeloComponentPolicy.Status.TELEGRAM, "Telegram · Chưa tải", metadata))
        assertEquals("Đang phát · Ca sĩ A · Kênh: Nguồn", MeloComponentPolicy.themedSecondLine(
            MeloComponentPolicy.Status.PLAYING, "Đang phát", metadata))
        assertNull(MeloComponentPolicy.qualityLabel(null, null, null, null))
    }
}
