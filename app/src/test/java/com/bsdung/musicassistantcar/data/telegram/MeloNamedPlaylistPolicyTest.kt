package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.AddTarget
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.DetailKind
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.NameCheck
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.PlayEntry
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.PlayPlan
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.QueueCandidate
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.QueueSavePlan
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.RowKind
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.RowTap
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nhiều Playlist có tên, S12: the pure rules of the named-playlist screens. */
class MeloNamedPlaylistPolicyTest {
    private val account = 1001L
    private val a = ListId("dev-a", 1L)
    private val b = ListId("dev-a", 2L)
    private fun lists(vararg named: Pair<ListId, String>) = named.mapIndexed { i, (id, name) -> NamedListView(id, name, i + 3) }

    // ---- screens ---------------------------------------------------------------------------------------

    @Test fun aPlaylistThatVanishedSendsTheDetailBackToTheIndex() {
        val present = lists(a to "Một")
        assertEquals(Screen.Detail(a), MeloNamedPlaylistPolicy.resolve(Screen.Detail(a), present))
        assertEquals(Screen.Index, MeloNamedPlaylistPolicy.resolve(Screen.Detail(b), present))
        assertEquals(Screen.Index, MeloNamedPlaylistPolicy.resolve(Screen.Detail(a), emptyList()))
        assertEquals(Screen.Favourites, MeloNamedPlaylistPolicy.resolve(Screen.Favourites, emptyList()))
        assertEquals(Screen.Index, MeloNamedPlaylistPolicy.resolve(Screen.Index, present))
    }

    @Test fun backGoesFromLevelTwoToTheIndexAndStaysThere() {
        assertEquals(Screen.Index, MeloNamedPlaylistPolicy.back(Screen.Favourites))
        assertEquals(Screen.Index, MeloNamedPlaylistPolicy.back(Screen.Detail(a)))
        assertEquals(Screen.Index, MeloNamedPlaylistPolicy.back(Screen.Index))
    }

    // ---- the index ---------------------------------------------------------------------------------------

    @Test fun theIndexIsFavouritesThenPlaylistsInOrderThenCreate() {
        val rows = MeloNamedPlaylistPolicy.indexRows(12, lists(a to "Nhạc đi xe", b to "Gym"))
        assertEquals(listOf(RowKind.FAVOURITES, RowKind.NAMED, RowKind.NAMED, RowKind.CREATE), rows.map { it.kind })
        assertEquals("★ Yêu thích · 12 bài", rows[0].title)
        assertEquals("Nhạc đi xe · 3 bài", rows[1].title)
        assertEquals("Gym · 4 bài", rows[2].title)
        assertEquals("+ Tạo Playlist", rows[3].title)
        assertEquals(listOf<ListId?>(null, a, b, null), rows.map { it.id })
        assertTrue(rows.all { it.enabled })
        assertTrue(rows.all { it.description.isNotBlank() })
    }

    @Test fun anUnreadFavouritesCountIsNotMadeUpAndAnEmptyIndexStillOffersCreate() {
        val rows = MeloNamedPlaylistPolicy.indexRows(null, emptyList())
        assertEquals("★ Yêu thích · đang đọc…", rows[0].title)
        assertEquals(listOf(RowKind.FAVOURITES, RowKind.CREATE), rows.map { it.kind })
        assertEquals("★ Yêu thích · 0 bài", MeloNamedPlaylistPolicy.indexRows(0, emptyList())[0].title)
    }

    @Test fun at100PlaylistsCreateIsDisabledWithTheReason() {
        val full = (1..100).map { NamedListView(ListId("dev-a", it.toLong()), "P$it", 0) }
        val last = MeloNamedPlaylistPolicy.indexRows(0, full).last()
        assertEquals(RowKind.CREATE, last.kind)
        assertFalse(last.enabled)
        assertEquals("Đã đủ 100 Playlist", last.title)
        assertTrue(MeloNamedPlaylistPolicy.indexRows(0, full.take(99)).last().enabled)
    }

    @Test fun theDetailTitleNamesThePlaylistAndItsCount() {
        assertEquals("Nhạc đi xe · 4 bài", MeloNamedPlaylistPolicy.detailTitle("Nhạc đi xe", 4))
        assertEquals("‹ Playlist", MeloNamedPlaylistPolicy.BACK_LABEL)
    }

    // ---- names ---------------------------------------------------------------------------------------------

    @Test fun namesAreCheckedAgainstTheOtherPlaylistsIgnoringAccentsAndCase() {
        val existing = lists(a to "Nhạc Xưa", b to "Gym")
        assertEquals(NameCheck.Ok("Nhạc mới"), MeloNamedPlaylistPolicy.checkName("  Nhạc mới ", existing))
        assertEquals(NameCheck.Duplicate, MeloNamedPlaylistPolicy.checkName("nhac xua", existing))
        assertEquals(NameCheck.Duplicate, MeloNamedPlaylistPolicy.checkName("GYM", existing))
        assertEquals(NameCheck.Empty, MeloNamedPlaylistPolicy.checkName("   ", existing))
        assertEquals(NameCheck.Empty, MeloNamedPlaylistPolicy.checkName("", existing))
        assertEquals(NameCheck.TooLong, MeloNamedPlaylistPolicy.checkName("x".repeat(61), existing))
        assertEquals(NameCheck.Ok("x".repeat(60)), MeloNamedPlaylistPolicy.checkName("x".repeat(60), existing))
        assertEquals(NameCheck.BadCharacters, MeloNamedPlaylistPolicy.checkName("a\tb", existing))
    }

    @Test fun aRenameMayKeepItsOwnNameOrChangeOnlyTheCaseButNotTakeAnothers() {
        val existing = lists(a to "Nhạc Xưa", b to "Gym")
        assertEquals(NameCheck.Ok("NHẠC XƯA"), MeloNamedPlaylistPolicy.checkName("NHẠC XƯA", existing, except = a))
        assertEquals(NameCheck.Ok("Gym"), MeloNamedPlaylistPolicy.checkName("Gym", existing, except = b))
        assertEquals(NameCheck.Duplicate, MeloNamedPlaylistPolicy.checkName("gym", existing, except = a))
    }

    @Test fun everyBadNameHasASentenceAndAGoodOneHasNone() {
        assertNull(MeloNamedPlaylistPolicy.nameMessage(NameCheck.Ok("x")))
        assertEquals("Hãy nhập tên Playlist.", MeloNamedPlaylistPolicy.nameMessage(NameCheck.Empty))
        assertEquals("Tên dài tối đa 60 ký tự.", MeloNamedPlaylistPolicy.nameMessage(NameCheck.TooLong))
        assertEquals("Đã có Playlist tên này.", MeloNamedPlaylistPolicy.nameMessage(NameCheck.Duplicate))
        assertNotNull(MeloNamedPlaylistPolicy.nameMessage(NameCheck.BadCharacters))
    }

    @Test fun theNameRulesAgreeWithTheModelSoACheckedNameIsNeverRefusedLater() {
        val names = listOf("Nhạc đi xe", "🎵".repeat(60), "a b  c", "Đêm — mưa", "x".repeat(60))
        for (raw in names) {
            val check = MeloNamedPlaylistPolicy.checkName(raw, emptyList())
            assertTrue(raw, check is NameCheck.Ok)
            val made = NamedPlaylistState.empty(account, "dev-a").createList((check as NameCheck.Ok).name)
            assertNull(raw, made.refusal)
        }
        for (raw in listOf("x".repeat(61), " ", "a\nb")) {
            val check = MeloNamedPlaylistPolicy.checkName(raw, emptyList())
            assertFalse(raw, check is NameCheck.Ok)
            assertNotNull(NamedPlaylistState.empty(account, "dev-a").createList(raw).refusal)
        }
    }

    // ---- what can be added -----------------------------------------------------------------------------------------

    private fun key(chat: Long, raw: Long) = TelegramSongKey.forMessage(chat, raw, account)

    @Test fun aVerifiedTelegramSongIsAddedByItsChatAndMessage() {
        val target = MeloNamedPlaylistPolicy.telegramTarget(key(-1001L, 77L), -1001L, 77L, account, placeholder = false, playlistsReady = true)
        assertEquals(AddTarget.Telegram(PlaylistItemId.Telegram(-1001L, 77L)), target)
    }

    @Test fun legacyUnknownPlaceholderAndNotReadyAreRefusedWithTheirOwnReason() {
        fun refused(songKey: Long, chat: Long, raw: Long, placeholder: Boolean = false, ready: Boolean = true) =
            (MeloNamedPlaylistPolicy.telegramTarget(songKey, chat, raw, account, placeholder, ready) as AddTarget.Refused).message
        assertEquals(MeloNamedPlaylistPolicy.REFUSED_LEGACY, refused(key(-1001L, 77L), 0L, 0L))
        assertEquals(MeloNamedPlaylistPolicy.REFUSED_LEGACY, refused(key(-1001L, 77L), -1001L, 78L)) // the key does not agree with the message
        assertEquals(MeloNamedPlaylistPolicy.REFUSED_PLACEHOLDER, refused(key(-1001L, 77L), -1001L, 77L, placeholder = true))
        assertEquals(MeloNamedPlaylistPolicy.REFUSED_NOT_READY, refused(key(-1001L, 77L), -1001L, 77L, ready = false))
    }

    @Test fun aSongOfTheDeviceIsAddedByItsIdAndAFingerprintNeverAPath() {
        val target = MeloNamedPlaylistPolicy.localTarget(900L, "Bài máy.mp3", 215_000L, 5_000_000L, playlistsReady = true) as AddTarget.Local
        assertEquals(900L, target.localId)
        assertEquals(LocalFingerprint.of("Bài máy.mp3", 215_000L, 5_000_000L), target.fingerprint)
        assertTrue(LocalFingerprint.isValid(target.fingerprint))
        assertTrue(MeloNamedPlaylistPolicy.localTarget(0L, "x", 1, 1, true) is AddTarget.Refused)
        assertTrue(MeloNamedPlaylistPolicy.localTarget(900L, "x", 1, 1, false) is AddTarget.Refused)
    }

    // ---- the box ------------------------------------------------------------------------------------------------------

    @Test fun theBoxShowsACheckWhereTheSongIsAndEndsWithNewPlaylist() {
        val rows = MeloNamedPlaylistPolicy.choiceRows(lists(a to "Một", b to "Hai"), setOf(b), "Bài X")
        assertEquals(listOf("Một", "✓ Hai", "+ Playlist mới…"), rows.map { it.label })
        assertEquals(listOf(false, true, false), rows.map { it.checked })
        assertEquals(listOf<ListId?>(a, b, null), rows.map { it.id })
        assertEquals(listOf(true, false, true), rows.map { MeloNamedPlaylistPolicy.tapAdds(it) })
        assertTrue(rows[1].description.contains("Chạm để bỏ"))
        assertTrue(rows[0].description.contains("Chạm để thêm"))
    }

    @Test fun withNoPlaylistTheBoxStillOffersTheNewOne() {
        val rows = MeloNamedPlaylistPolicy.choiceRows(emptyList(), emptySet(), "Bài X")
        assertEquals(listOf("+ Playlist mới…"), rows.map { it.label })
        assertNull(rows.single().id)
    }

    // ---- messages ---------------------------------------------------------------------------------------------------------

    @Test fun theSentencesAreExactlyThoseOfAppendixA() {
        assertEquals("Đã thêm 'Bài X' vào ‘Gym’.", MeloNamedPlaylistPolicy.added("Bài X", "Gym"))
        assertEquals("Đã bỏ 'Bài X' khỏi ‘Gym’.", MeloNamedPlaylistPolicy.removed("Bài X", "Gym"))
        assertEquals("Playlist ‘Gym’ đã đủ 1000 bài.", MeloNamedPlaylistPolicy.listFull("Gym"))
        assertEquals("Đã có Playlist tên này.", MeloNamedPlaylistPolicy.nameMessage(NameCheck.Duplicate))
        assertEquals("Xóa Playlist ‘Gym’? Bài hát không bị xóa khỏi Telegram hay máy.", MeloNamedPlaylistPolicy.deleteQuestion("Gym"))
    }

    @Test fun everyOutcomeThatIsNotOkHasASentenceAndOkHasNone() {
        fun msg(o: NamedOutcome, list: String? = "Gym") = MeloNamedPlaylistPolicy.failureMessage(o, list)
        assertNull(msg(NamedOutcome.Ok()))
        assertEquals("Playlist ‘Gym’ đã đủ 1000 bài.", msg(NamedOutcome.Full(NamedRefusal.ITEM_LIMIT)))
        assertEquals(MeloNamedPlaylistPolicy.TOO_MANY_PLAYLISTS, msg(NamedOutcome.Full(NamedRefusal.LIST_LIMIT)))
        assertEquals(MeloNamedPlaylistPolicy.OUTBOX_FULL, msg(NamedOutcome.OutboxFull))
        assertEquals(MeloNamedPlaylistPolicy.REFUSED_UNREADABLE, msg(NamedOutcome.Blocked(PlaylistUnreadableReason.DECRYPT)))
        assertEquals(MeloNamedPlaylistPolicy.REFUSED_NOT_READY, msg(NamedOutcome.NotOpen))
        assertEquals(MeloNamedPlaylistPolicy.FAILED, msg(NamedOutcome.Failed))
        assertEquals("Đã có Playlist tên này.", msg(NamedOutcome.DuplicateName))
        for (why in NamedRefusal.values()) assertNotNull(why.name, msg(NamedOutcome.Refused(why)))
        assertTrue(msg(NamedOutcome.Full(NamedRefusal.ITEM_LIMIT), null)!!.contains("‘Playlist’"))
    }

    // ---- level 2 ------------------------------------------------------------------------------------------------------------

    @Test fun aSongOfTheDeviceMatchesOnlyWhileItsNameLengthAndSizeAreUnchanged() {
        val fp = LocalFingerprint.of("Bài máy.mp3", 1000L, 2000L)
        val entry = NamedEntry(PlaylistItemId.Local(9L), "V", fp)
        assertTrue(MeloNamedPlaylistPolicy.localMatches(entry, "Bài máy.mp3", 1000L, 2000L))
        assertFalse(MeloNamedPlaylistPolicy.localMatches(entry, "Bài máy.mp3", 1001L, 2000L))
        assertFalse(MeloNamedPlaylistPolicy.localMatches(entry, "Bài khác.mp3", 1000L, 2000L))
        assertFalse(MeloNamedPlaylistPolicy.localMatches(entry, null, 1000L, 2000L))
        assertFalse(MeloNamedPlaylistPolicy.localMatches(NamedEntry(PlaylistItemId.Telegram(-1L, 1L), "V", null), "x", 1, 1))
    }

    @Test fun upAndDownMoveByOneAndStopAtTheEnds() {
        assertEquals(1, MeloNamedPlaylistPolicy.moveTo(2, -1, 4))
        assertEquals(3, MeloNamedPlaylistPolicy.moveTo(2, +1, 4))
        assertNull(MeloNamedPlaylistPolicy.moveTo(0, -1, 4))
        assertNull(MeloNamedPlaylistPolicy.moveTo(3, +1, 4))
        assertNull(MeloNamedPlaylistPolicy.moveTo(0, +1, 1))
        assertNull(MeloNamedPlaylistPolicy.moveTo(5, -1, 4))
        assertNull(MeloNamedPlaylistPolicy.moveTo(1, 2, 4))
    }

    private fun tg(i: Long) = PlaylistItemId.Telegram(-1L, i)
    private fun entry(kind: DetailKind, i: Long = 1L) =
        PlayEntry(if (kind == DetailKind.LOCAL || kind == DetailKind.LOCAL_GONE) PlaylistItemId.Local(i) else tg(i), kind)

    @Test fun playStartsAtTheFirstSongThatCanPlayAndSkipsPlaceholdersAndGoneSongs() {
        val entries = listOf(entry(DetailKind.TELEGRAM_UNKNOWN, 1), entry(DetailKind.LOCAL_GONE, 2), entry(DetailKind.TELEGRAM, 3), entry(DetailKind.TELEGRAM, 4))
        assertEquals(PlayPlan.FromTelegram(2, null), MeloNamedPlaylistPolicy.playPlan(entries, shuffle = false))
    }

    @Test fun shufflePicksOnlyAmongPlayableSongsForAnyNumber() {
        val entries = listOf(entry(DetailKind.TELEGRAM, 1), entry(DetailKind.TELEGRAM_UNKNOWN, 2), entry(DetailKind.TELEGRAM, 3), entry(DetailKind.LOCAL, 4))
        for (pick in listOf(0, 1, 2, 7, -1, -5, Int.MAX_VALUE, Int.MIN_VALUE)) {
            val plan = MeloNamedPlaylistPolicy.playPlan(entries, shuffle = true, pick = pick) as PlayPlan.FromTelegram
            assertTrue("pick=$pick index=${plan.index}", plan.index == 0 || plan.index == 2)
        }
        assertEquals(0, (MeloNamedPlaylistPolicy.playPlan(entries, true, 0) as PlayPlan.FromTelegram).index)
        assertEquals(2, (MeloNamedPlaylistPolicy.playPlan(entries, true, 1) as PlayPlan.FromTelegram).index)
    }

    @Test fun deviceSongsAreNotInTheQueueAndTheNoteSaysSo() {
        val mixed = listOf(entry(DetailKind.TELEGRAM, 1), entry(DetailKind.LOCAL, 2))
        assertEquals(PlayPlan.FromTelegram(0, MeloNamedPlaylistPolicy.LOCAL_NOT_QUEUED), MeloNamedPlaylistPolicy.playPlan(mixed, false))
        val onlyTelegram = listOf(entry(DetailKind.TELEGRAM, 1), entry(DetailKind.TELEGRAM, 2))
        assertEquals(PlayPlan.FromTelegram(0, null), MeloNamedPlaylistPolicy.playPlan(onlyTelegram, false))
    }

    @Test fun aPlaylistOfOnlyDeviceSongsPlaysThemAndAnEmptyOrDeadOneSaysNothingPlays() {
        val local = listOf(entry(DetailKind.LOCAL_GONE, 1), entry(DetailKind.LOCAL, 2), entry(DetailKind.LOCAL, 3))
        assertEquals(PlayPlan.FromLocal(1), MeloNamedPlaylistPolicy.playPlan(local, false))
        assertEquals(PlayPlan.FromLocal(2), MeloNamedPlaylistPolicy.playPlan(local, true, 1))
        assertEquals(PlayPlan.NothingToPlay(MeloNamedPlaylistPolicy.NOTHING_TO_PLAY), MeloNamedPlaylistPolicy.playPlan(emptyList(), false))
        assertEquals(
            PlayPlan.NothingToPlay(MeloNamedPlaylistPolicy.NOTHING_TO_PLAY),
            MeloNamedPlaylistPolicy.playPlan(listOf(entry(DetailKind.TELEGRAM_UNKNOWN), entry(DetailKind.LOCAL_GONE, 2)), true, 3)
        )
    }

    @Test fun aTapDoesTheRightThingPerKindOfRow() {
        val entries = listOf(entry(DetailKind.TELEGRAM, 1), entry(DetailKind.LOCAL, 2), entry(DetailKind.TELEGRAM_UNKNOWN, 3), entry(DetailKind.LOCAL_GONE, 4))
        assertEquals(
            listOf(RowTap.PLAY_TELEGRAM, RowTap.PLAY_LOCAL, RowTap.EXPLAIN_UNKNOWN, RowTap.OFFER_REMOVE_LOCAL, RowTap.NONE, RowTap.NONE),
            listOf(0, 1, 2, 3, 4, -1).map { MeloNamedPlaylistPolicy.rowTap(entries, it) }
        )
    }

    // ---- saving the queue ------------------------------------------------------------------------------------------------------

    private fun tgSong(raw: Long, chat: Long = -1001L, placeholder: Boolean = false) =
        QueueCandidate(false, key(chat, raw), "Bài $raw", chat, raw, placeholder)

    private fun deviceSong(id: Long) = QueueCandidate(true, 0L, "Máy $id", mediaStoreId = id, durationMs = 1000L + id, sizeBytes = 2000L + id)

    @Test fun theQueueIsSavedInOrderTelegramAndDeviceSongsTogether() {
        val plan = MeloNamedPlaylistPolicy.planQueueSave(listOf(tgSong(1), deviceSong(9), tgSong(2)), account) as QueueSavePlan.Ready
        assertEquals(3, plan.saved)
        assertEquals(0, plan.skipped)
        assertEquals(AddTarget.Telegram(PlaylistItemId.Telegram(-1001L, 1L)), plan.items[0])
        assertEquals(AddTarget.Local(9L, LocalFingerprint.of("Máy 9", 1009L, 2009L)), plan.items[1])
        assertEquals(AddTarget.Telegram(PlaylistItemId.Telegram(-1001L, 2L)), plan.items[2])
        assertEquals("Đã lưu 3 bài, bỏ qua 0 bài.", MeloNamedPlaylistPolicy.queueSaved(plan.saved, plan.skipped))
    }

    @Test fun placeholdersLegacyUnknownAndRepeatsAreLeftOutAndCounted() {
        val queue = listOf(
            tgSong(1), tgSong(2, placeholder = true), QueueCandidate(false, key(-1001L, 3L), "Cũ", 0L, 0L), tgSong(1), deviceSong(9), deviceSong(9),
            QueueCandidate(true, 0L, "Hỏng", mediaStoreId = 0L),
        )
        val plan = MeloNamedPlaylistPolicy.planQueueSave(queue, account) as QueueSavePlan.Ready
        assertEquals(2, plan.saved)
        assertEquals(5, plan.skipped)
        assertEquals(queue.size, plan.saved + plan.skipped)
    }

    @Test fun anEmptyQueueIsEmptyAndMoreThan1000IsRefusedNotCut() {
        assertEquals(QueueSavePlan.Empty, MeloNamedPlaylistPolicy.planQueueSave(emptyList(), account))
        val big = (1L..1001L).map { tgSong(it) }
        assertEquals(QueueSavePlan.TooMany(1001), MeloNamedPlaylistPolicy.planQueueSave(big, account))
        assertTrue(MeloNamedPlaylistPolicy.planQueueSave(big.take(1000), account) is QueueSavePlan.Ready)
        assertTrue(MeloNamedPlaylistPolicy.queueTooMany(1001).contains("1001"))
    }

    @Test fun aQueueOfOnlyUnsavableSongsIsReadyWithNothingSoTheCallerCanSayThatPlainly() {
        val plan = MeloNamedPlaylistPolicy.planQueueSave(listOf(tgSong(1, placeholder = true), tgSong(2, placeholder = true)), account) as QueueSavePlan.Ready
        assertEquals(0, plan.saved)
        assertEquals(2, plan.skipped)
        assertTrue(plan.items.isEmpty())
    }

    @Test fun aPlanFromTheQueueFitsInsideTheModelWithoutRefusal() {
        val queue = (1L..120L).map { if (it % 4L == 0L) deviceSong(it) else tgSong(it) }
        val plan = MeloNamedPlaylistPolicy.planQueueSave(queue, account) as QueueSavePlan.Ready
        var s = NamedPlaylistState.empty(account, "dev-a")
        val id = checkNotNull(s.createList("Hàng chờ").also { s = it.state }.listId)
        for (target in plan.items) {
            val m = when (target) {
                is AddTarget.Telegram -> s.addTelegram(id, target.item)
                is AddTarget.Local -> s.addLocal(id, target.localId, target.fingerprint)
                is AddTarget.Refused -> error("planned a refused target")
            }
            assertNull(m.refusal)
            s = m.state
        }
        assertEquals(plan.saved, s.itemCount(id))
        val order = s.entries(id).map { it.item }
        assertEquals(plan.items.map { if (it is AddTarget.Telegram) it.item else PlaylistItemId.Local((it as AddTarget.Local).localId) }, order)
    }
}
