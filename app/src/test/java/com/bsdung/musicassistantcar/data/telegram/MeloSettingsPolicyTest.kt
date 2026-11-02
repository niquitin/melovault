package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloSettingsPolicy.HomeSection
import com.bsdung.musicassistantcar.data.telegram.MeloSettingsPolicy.MenuId
import com.bsdung.musicassistantcar.data.telegram.MeloSettingsPolicy.Page
import com.bsdung.musicassistantcar.data.telegram.MeloSettingsPolicy.SyncButton
import com.bsdung.musicassistantcar.data.telegram.MeloSettingsPolicy.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MeloSettingsPolicyTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance(utc).apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute)
    }.timeInMillis

    private val now = at(26, 18, 0)

    private fun card(
        auth: Boolean = true,
        syncing: Boolean = false,
        paused: Boolean = false,
        songs: Int? = 1234,
        last: Long = at(26, 9, 5)
    ) = MeloSettingsPolicy.syncCard(auth, syncing, paused, songs, last, now, utc)

    // ---- sync card: the five states ----

    @Test
    fun signedOutHidesTheCard() {
        val c = card(auth = false, syncing = true)
        assertEquals(SyncState.SIGNED_OUT, c.state)
        assertFalse(c.visible)
        assertEquals("Chưa đăng nhập Telegram", c.line1)
        assertNull(c.primary)
        assertNull(c.secondary)
    }

    @Test
    fun runningOffersOnlyPause() {
        val c = card(syncing = true)
        assertEquals(SyncState.RUNNING, c.state)
        assertEquals("Đang đồng bộ…", c.line1)
        assertEquals("1.234 bài", c.line2)
        assertEquals(SyncButton.PAUSE, c.primary)
        assertNull(c.secondary)
        assertEquals("Tạm dừng", SyncButton.PAUSE.label)
    }

    @Test
    fun pausedOffersResumeAndFullScan() {
        val c = card(syncing = false, paused = true)
        assertEquals(SyncState.PAUSED, c.state)
        assertEquals("Đã tạm dừng đồng bộ", c.line1)
        assertNull(c.line2)
        assertEquals(SyncButton.RESUME, c.primary)
        assertEquals(SyncButton.FULL_SCAN, c.secondary)
        assertEquals("Tiếp tục", SyncButton.RESUME.label)
        assertEquals("Quét toàn bộ", SyncButton.FULL_SCAN.label)
    }

    @Test
    fun pausedWinsEvenIfTheBusyFlagIsStillSet() {
        assertEquals(SyncState.PAUSED, card(syncing = true, paused = true).state)
    }

    @Test
    fun idleAfterASyncShowsTimeAndCount() {
        val c = card(last = at(26, 9, 5))
        assertEquals(SyncState.IDLE_SYNCED, c.state)
        assertEquals("Thư viện đã đồng bộ", c.line1)
        assertEquals("Cập nhật lúc 09:05 · 1.234 bài", c.line2)
        assertEquals(SyncButton.FETCH_NEW, c.primary)
        assertEquals(SyncButton.FULL_SCAN, c.secondary)
        assertEquals("Lấy bài mới", SyncButton.FETCH_NEW.label)
    }

    @Test
    fun idleSyncedOnAnotherDayShowsTheDate() {
        assertEquals("Cập nhật lúc 25/10 23:40 · 1.234 bài", card(last = at(25, 23, 40)).line2)
    }

    @Test
    fun idleNeverSyncedHasNoSecondLine() {
        val c = card(last = 0L)
        assertEquals(SyncState.IDLE_NEVER, c.state)
        assertEquals("Chưa đồng bộ", c.line1)
        assertNull(c.line2)
        assertEquals(SyncButton.FETCH_NEW, c.primary)
        assertEquals(SyncButton.FULL_SCAN, c.secondary)
    }

    @Test
    fun songCountMissingZeroOrNegative() {
        assertEquals("Cập nhật lúc 09:05", card(songs = null).line2)
        assertEquals("Cập nhật lúc 09:05", card(songs = -1).line2)
        assertEquals("Cập nhật lúc 09:05 · 0 bài", card(songs = 0).line2)
        assertNull(card(syncing = true, songs = null).line2)
        assertNull(card(syncing = true, songs = -5).line2)
        assertEquals("0 bài", card(syncing = true, songs = 0).line2)
    }

    @Test
    fun syncTimeSameDayDifferentDayAndZero() {
        assertEquals("09:05", MeloSettingsPolicy.formatSyncTime(at(26, 9, 5), now, utc))
        assertEquals("25/10 23:40", MeloSettingsPolicy.formatSyncTime(at(25, 23, 40), now, utc))
        assertEquals("chưa rõ", MeloSettingsPolicy.formatSyncTime(0L, now, utc))
        assertEquals("chưa rõ", MeloSettingsPolicy.formatSyncTime(-5L, now, utc))
    }

    @Test
    fun syncTimeFollowsTheTimeZoneForTheDayBoundary() {
        // 20:00 UTC on the 25th is already 03:00 on the 26th in Vietnam, the same day as `now` there.
        val vn = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        assertEquals("03:00", MeloSettingsPolicy.formatSyncTime(at(25, 20, 0), at(26, 8, 0), vn))
    }

    // ---- ⋮ menu ----

    @Test
    fun menuHasFiveRowsWithDeviceMusicAndOrderIsFixed() {
        val ids = MeloSettingsPolicy.menuItems(authReady = true, showLocal = true).map { it.id }
        assertEquals(listOf(MenuId.SAVED, MenuId.LOCAL, MenuId.SOURCES, MenuId.OPEN_FILE, MenuId.SETTINGS), ids)
    }

    @Test
    fun menuWithoutDeviceMusicDropsOnlyThatRow() {
        val ids = MeloSettingsPolicy.menuItems(authReady = true, showLocal = false).map { it.id }
        assertEquals(listOf(MenuId.SAVED, MenuId.SOURCES, MenuId.OPEN_FILE, MenuId.SETTINGS), ids)
    }

    @Test
    fun signedOutLocksTheRowsThatNeedAnAccount() {
        val items = MeloSettingsPolicy.menuItems(authReady = false, showLocal = true).associateBy { it.id }
        assertFalse(items.getValue(MenuId.SAVED).enabled)
        assertFalse(items.getValue(MenuId.SOURCES).enabled)
        assertFalse(items.getValue(MenuId.OPEN_FILE).enabled)
        assertTrue(items.getValue(MenuId.LOCAL).enabled)
        assertTrue(items.getValue(MenuId.SETTINGS).enabled)
    }

    @Test
    fun signedInEnablesEveryRow() {
        assertTrue(MeloSettingsPolicy.menuItems(authReady = true, showLocal = true).all { it.enabled })
    }

    @Test
    fun menuLabelsKeepTheOldWordsAndDropMovedEntries() {
        val items = MeloSettingsPolicy.menuItems(authReady = true, showLocal = true)
        val labels = items.map { it.label }
        assertEquals("☆ Tin đã lưu", labels[0])
        assertEquals("♪ Nhạc trên máy", labels[1])
        assertEquals("Nhóm / kênh / bot Telegram", labels[2])
        assertEquals("Mở tệp nhạc (Drive / thiết bị)", labels[3])
        assertEquals("Cài đặt", labels[4])
        assertEquals("Giao diện, tài khoản…", items[4].subtitle)
        val all = labels.joinToString("|")
        assertFalse(all.contains("Đăng xuất"))
        assertFalse(all.contains("Giao diện:"))
        assertFalse(all.contains("Hiển thị nguồn"))
        assertFalse(all.contains("Đồng bộ"))
    }

    // ---- home summaries ----

    @Test
    fun homeSummaryCoversEveryCombination() {
        val styles = MeloLibraryPolicy.LibraryStyle.entries
        val themes = MeloThemePolicy.ColorTheme.entries
        val modes = TelegramVaultBentoPolicy.SourceDisplayMode.entries
        val seen = mutableSetOf<String>()
        for (s in styles) for (t in themes) for (m in modes) seen += MeloSettingsPolicy.homeSummary(s, t, m)
        assertEquals(styles.size * themes.size * modes.size, seen.size)
    }

    @Test
    fun homeSummaryDefaultsReadNaturally() {
        assertEquals(
            "Thư viện Mới · Màu Hiện tại · Nguồn dạng Danh sách",
            MeloSettingsPolicy.homeSummary(
                MeloLibraryPolicy.LibraryStyle.NEW, MeloThemePolicy.ColorTheme.CURRENT,
                TelegramVaultBentoPolicy.SourceDisplayMode.LIST
            )
        )
        assertEquals(
            "Thư viện Cũ · Màu Teal sáng · Nguồn dạng Bento",
            MeloSettingsPolicy.homeSummary(
                MeloLibraryPolicy.LibraryStyle.LEGACY, MeloThemePolicy.ColorTheme.TEAL_BRIGHT,
                TelegramVaultBentoPolicy.SourceDisplayMode.BENTO
            )
        )
    }

    @Test
    fun nowPlayingAndDeviceNamesCoverEveryEnum() {
        assertEquals("Studio Deck", MeloSettingsPolicy.nowPlayingName(MeloNowPlayingPolicy.NowPlayingStyle.STUDIO_DECK))
        assertEquals("Màn cũ", MeloSettingsPolicy.nowPlayingName(MeloNowPlayingPolicy.NowPlayingStyle.LEGACY))
        MeloCompactPolicy.CompactSetting.entries.forEach { assertTrue(MeloSettingsPolicy.compactName(it).isNotBlank()) }
        MeloCarPolicy.CarSetting.entries.forEach { assertTrue(MeloSettingsPolicy.carName(it).isNotBlank()) }
        assertEquals("Tự động", MeloSettingsPolicy.compactName(MeloCompactPolicy.CompactSetting.AUTO))
        assertEquals("Bật", MeloSettingsPolicy.carName(MeloCarPolicy.CarSetting.ON))
    }

    @Test
    fun accountSubtitleOmitsMissingNumbers() {
        assertEquals("Đã đăng nhập · 24.601 bài · 38 nguồn", MeloSettingsPolicy.accountSubtitle(24601, 38))
        assertEquals("Đã đăng nhập · 24.601 bài", MeloSettingsPolicy.accountSubtitle(24601, null))
        assertEquals("Đã đăng nhập · 38 nguồn", MeloSettingsPolicy.accountSubtitle(-1, 38))
        assertEquals("Đã đăng nhập", MeloSettingsPolicy.accountSubtitle(null, -1))
    }

    @Test
    fun accountSyncLineOnlyWhenASyncFinished() {
        assertEquals("Đồng bộ xong lúc 09:05", MeloSettingsPolicy.accountSyncLine(at(26, 9, 5), now, utc))
        assertNull(MeloSettingsPolicy.accountSyncLine(0L, now, utc))
    }

    // ---- appearance / devices ----

    @Test
    fun presetListShowsOnlyForTheOldLibrary() {
        assertTrue(MeloSettingsPolicy.presetVisible(MeloLibraryPolicy.LibraryStyle.LEGACY))
        assertFalse(MeloSettingsPolicy.presetVisible(MeloLibraryPolicy.LibraryStyle.NEW))
    }

    @Test
    fun compactHintExistsForEveryOptionAndDiffers() {
        val hints = MeloCompactPolicy.CompactSetting.entries.map { MeloSettingsPolicy.compactHint(it) }
        assertTrue(hints.all { it.isNotBlank() })
        assertEquals(hints.size, hints.toSet().size)
    }

    @Test
    fun bandHintExistsForEveryOptionAndDiffers() {
        val hints = WindowInsetsPolicy.HandleBand.entries.map { MeloSettingsPolicy.bandHint(it) }
        assertTrue(hints.all { it.isNotBlank() })
        assertEquals(hints.size, hints.toSet().size)
    }

    // ---- advanced: short measurement list ----

    private val fullInfo = listOf(
        "Tấm nền (vật lý)" to "768 × 1280 px",
        "Android" to "10 (API 29)",
        "Máy" to "Shanling M3 Ultra",
        "Kích thước ước tính" to "60 × 100 mm",
        "Mật độ Android" to "320 dpi",
        "Cạnh ngắn nhất" to "240 dp",
        "Độ rộng × cao (dp)" to "240 × 400 dp",
        "Bố cục thực tế đang dùng" to "Gọn (máy nhỏ)",
        "Display ID" to "0"
    )

    @Test
    fun shortInfoHasSixRowsInTheFixedOrder() {
        val short = MeloSettingsPolicy.shortInfo(fullInfo)
        assertEquals(
            listOf(
                "Bố cục thực tế đang dùng", "Độ rộng × cao (dp)", "Cạnh ngắn nhất",
                "Mật độ Android", "Kích thước ước tính", "Máy + Android"
            ),
            short.map { it.first }
        )
        assertEquals("Shanling M3 Ultra · Android 10 (API 29)", short.last().second)
        assertEquals("Gọn (máy nhỏ)", short.first().second)
    }

    @Test
    fun shortInfoSkipsMissingLabelsAndInventsNothing() {
        val short = MeloSettingsPolicy.shortInfo(listOf("Cạnh ngắn nhất" to "240 dp", "Display ID" to "0"))
        assertEquals(listOf("Cạnh ngắn nhất" to "240 dp"), short)
        assertTrue(MeloSettingsPolicy.shortInfo(emptyList()).isEmpty())
    }

    @Test
    fun shortInfoWithOnlyOneOfDeviceAndAndroid() {
        assertEquals(
            listOf("Máy + Android" to "Sony XQ-AS52"),
            MeloSettingsPolicy.shortInfo(listOf("Máy" to "Sony XQ-AS52"))
        )
        assertEquals(
            listOf("Máy + Android" to "Android 12 (API 31)"),
            MeloSettingsPolicy.shortInfo(listOf("Android" to "12 (API 31)"))
        )
    }

    @Test
    fun shortLabelsAreARealSubsetOfTheFullListLabels() {
        // Guards against renaming a label in the full list without updating the short list.
        val labels = fullInfo.map { it.first }.toSet()
        assertTrue(labels.containsAll(MeloSettingsPolicy.SHORT_INFO_LABELS))
    }

    // ---- pages ----

    @Test
    fun pageNamesRoundTripAndUnknownIsHome() {
        Page.entries.forEach { assertEquals(it, Page.fromName(it.name, devUnlocked = true)) }
        assertEquals(Page.HOME, Page.fromName(null))
        assertEquals(Page.HOME, Page.fromName("nope"))
        assertFalse(Page.HOME.isSubPage)
        assertTrue(Page.APPEARANCE.isSubPage && Page.DEVICES.isSubPage && Page.ADVANCED.isSubPage)
    }

    @Test
    fun hiddenPagesFallBackToHomeWhileDeveloperToolsAreLocked() {
        val hidden = Page.entries.filter { it.needsDevTools }
        assertEquals(setOf(Page.DEV_TOOLS, Page.DEVICES, Page.ADVANCED), hidden.toSet())
        hidden.forEach {
            assertEquals("${it.name} must not reopen from a saved state", Page.HOME, Page.fromName(it.name))
            assertEquals(Page.HOME, Page.fromName(it.name, devUnlocked = false))
            assertEquals(it, Page.fromName(it.name, devUnlocked = true))
        }
        // The pages that are always allowed, including Car Mode (the way out of a Car Mode left on).
        Page.entries.filter { !it.needsDevTools }.forEach { assertEquals(it, Page.fromName(it.name)) }
    }

    @Test
    fun everyPageHasATitleAndSubPagesGoBackStepByStep() {
        Page.entries.forEach { assertTrue(it.name, it.title.isNotBlank()) }
        assertEquals("Cài đặt", Page.HOME.title)
        assertEquals(Page.HOME, Page.APPEARANCE.parent)
        assertEquals(Page.HOME, Page.NOW_PLAYING.parent)
        assertEquals(Page.HOME, Page.CAR.parent)
        assertEquals(Page.HOME, Page.ABOUT.parent)
        assertEquals(Page.ABOUT, Page.PRIVACY.parent)
        assertEquals(Page.ABOUT, Page.LICENSES.parent)
        assertEquals(Page.HOME, Page.DEV_TOOLS.parent)
        assertEquals(Page.DEV_TOOLS, Page.DEVICES.parent)
        assertEquals(Page.DEV_TOOLS, Page.ADVANCED.parent)
        // Every chain of parents ends at HOME.
        Page.entries.forEach { var p = it; repeat(4) { p = p.parent }; assertEquals(it.name, Page.HOME, p) }
    }

    @Test
    fun nowPlayingHasItsOwnPageApartFromAppearance() {
        assertTrue(Page.NOW_PLAYING != Page.APPEARANCE)
        assertEquals("Màn Đang phát", Page.NOW_PLAYING.title)
    }

    // ---- developer tools: 7 taps ----

    @Test
    fun sevenQuickTapsUnlock() {
        val taps = MeloSettingsPolicy.DevTapCounter()
        val results = (0 until 7).map { taps.tap(it * 300L, alreadyUnlocked = false) }
        assertTrue(results.take(6).none { it.unlocked })
        assertTrue(results.last().unlocked)
        assertEquals("Đã bật Công cụ nhà phát triển", results.last().message)
    }

    @Test
    fun tapsFourToSixCountDown() {
        val taps = MeloSettingsPolicy.DevTapCounter()
        val messages = (0 until 6).map { taps.tap(it * 100L, false).message }
        assertEquals(listOf(null, null, null), messages.take(3))
        assertEquals("Còn 3 lần nữa để bật Công cụ nhà phát triển", messages[3])
        assertEquals("Còn 2 lần nữa để bật Công cụ nhà phát triển", messages[4])
        assertEquals("Còn 1 lần nữa để bật Công cụ nhà phát triển", messages[5])
    }

    @Test
    fun aPauseOverTwoSecondsStartsTheCountAgain() {
        val taps = MeloSettingsPolicy.DevTapCounter()
        repeat(6) { taps.tap(it * 100L, false) }
        // The 7th tap comes 2.1 s after the 6th: only one tap counts now.
        val late = taps.tap(500L + 2_100L, false)
        assertFalse(late.unlocked)
        assertNull(late.message)
        // Exactly 2 s later still counts as a quick tap.
        val taps2 = MeloSettingsPolicy.DevTapCounter()
        repeat(6) { taps2.tap(it * 100L, false) }
        assertTrue(taps2.tap(500L + 2_000L, false).unlocked)
    }

    @Test
    fun tappingWhileUnlockedOnlyTellsSo() {
        val taps = MeloSettingsPolicy.DevTapCounter()
        val r = taps.tap(0L, alreadyUnlocked = true)
        assertFalse(r.unlocked)
        assertEquals("Công cụ nhà phát triển đang bật", r.message)
        // After hiding the tools again, a fresh count of seven is needed.
        val results = (0 until 6).map { taps.tap(1_000L + it * 100L, false) }
        assertTrue(results.none { it.unlocked })
    }

    @Test
    fun unlockCountResetsAfterUnlocking() {
        val taps = MeloSettingsPolicy.DevTapCounter()
        repeat(7) { taps.tap(it * 100L, false) }
        assertFalse(taps.tap(800L, false).unlocked)
    }

    // ---- main page layout and the way out of a test state ----

    private val compactAuto = MeloCompactPolicy.CompactSetting.AUTO
    private val carOff = MeloCarPolicy.CarSetting.OFF
    private val noEmulation = MeloScreenPolicy.Emulation.OFF

    @Test
    fun defaultsShowNoAttentionRows() {
        assertTrue(MeloSettingsPolicy.attentionRows(compactAuto, carOff, noEmulation, false).isEmpty())
        // A real Gọn choice is not a test state.
        listOf(MeloCompactPolicy.CompactSetting.ON, MeloCompactPolicy.CompactSetting.OFF).forEach {
            assertTrue(MeloSettingsPolicy.attentionRows(it, carOff, noEmulation, false).isEmpty())
        }
    }

    @Test
    fun carModeOnAlwaysShowsItsRow() {
        val rows = MeloSettingsPolicy.attentionRows(compactAuto, MeloCarPolicy.CarSetting.ON, noEmulation, false)
        assertEquals(1, rows.size)
        assertEquals(MeloSettingsPolicy.AttentionKind.CAR_ON, rows[0].kind)
        assertEquals("Car Mode", rows[0].title)
        assertEquals("Đang bật", rows[0].value)
    }

    @Test
    fun everyKindOfEmulationShowsTheEmulationRow() {
        val cases = listOf(
            listOf(MeloCompactPolicy.CompactSetting.EMULATE_SHANLING, carOff, noEmulation, false),
            listOf(compactAuto, MeloCarPolicy.CarSetting.EMULATE_FYT_160, noEmulation, false),
            listOf(compactAuto, MeloCarPolicy.CarSetting.EMULATE_FYT_240, noEmulation, false),
            listOf(compactAuto, carOff, MeloScreenPolicy.Emulation.SHANLING_M3_ULTRA, false),
            listOf(compactAuto, carOff, MeloScreenPolicy.Emulation.FYT_7870_160, false),
            listOf(compactAuto, carOff, MeloScreenPolicy.Emulation.FYT_7870_240, false),
            listOf(compactAuto, carOff, noEmulation, true)
        )
        cases.forEach { c ->
            val rows = MeloSettingsPolicy.attentionRows(
                c[0] as MeloCompactPolicy.CompactSetting, c[1] as MeloCarPolicy.CarSetting,
                c[2] as MeloScreenPolicy.Emulation, c[3] as Boolean
            )
            assertEquals("$c", listOf(MeloSettingsPolicy.AttentionKind.EMULATION), rows.map { it.kind })
            assertEquals("Đang thử khung giả lập", rows[0].title)
            assertEquals("Tắt", rows[0].value)
        }
    }

    @Test
    fun carOnAndEmulationShowTwoRowsInOrder() {
        val rows = MeloSettingsPolicy.attentionRows(
            MeloCompactPolicy.CompactSetting.EMULATE_SHANLING, MeloCarPolicy.CarSetting.ON, noEmulation, true
        )
        assertEquals(
            listOf(MeloSettingsPolicy.AttentionKind.CAR_ON, MeloSettingsPolicy.AttentionKind.EMULATION), rows.map { it.kind }
        )
    }

    @Test
    fun turningEmulationOffTouchesOnlyTestValues() {
        assertEquals(
            MeloCompactPolicy.CompactSetting.AUTO,
            MeloSettingsPolicy.compactAfterEmulationOff(MeloCompactPolicy.CompactSetting.EMULATE_SHANLING)
        )
        listOf(MeloCompactPolicy.CompactSetting.AUTO, MeloCompactPolicy.CompactSetting.ON, MeloCompactPolicy.CompactSetting.OFF)
            .forEach { assertEquals(it, MeloSettingsPolicy.compactAfterEmulationOff(it)) }
        assertEquals(carOff, MeloSettingsPolicy.carAfterEmulationOff(MeloCarPolicy.CarSetting.EMULATE_FYT_160))
        assertEquals(carOff, MeloSettingsPolicy.carAfterEmulationOff(MeloCarPolicy.CarSetting.EMULATE_FYT_240))
        // A real Car Mode choice is not a test frame: it stays (its own row turns it off).
        assertEquals(MeloCarPolicy.CarSetting.ON, MeloSettingsPolicy.carAfterEmulationOff(MeloCarPolicy.CarSetting.ON))
        assertEquals(carOff, MeloSettingsPolicy.carAfterEmulationOff(carOff))
    }

    @Test
    fun afterTurningEmulationOffNoEmulationRowRemains() {
        val compact = MeloSettingsPolicy.compactAfterEmulationOff(MeloCompactPolicy.CompactSetting.EMULATE_SHANLING)
        val car = MeloSettingsPolicy.carAfterEmulationOff(MeloCarPolicy.CarSetting.EMULATE_FYT_240)
        assertFalse(MeloSettingsPolicy.isEmulating(compact, car, noEmulation, previewCar = false))
    }

    @Test
    fun mainPageSectionsFollowTheDesign() {
        val none =emptyList<MeloSettingsPolicy.AttentionRow>()
        val one = listOf(MeloSettingsPolicy.AttentionRow(MeloSettingsPolicy.AttentionKind.CAR_ON, "Car Mode", "Đang bật"))
        assertEquals(
            listOf(HomeSection.ACCOUNT, HomeSection.APPEARANCE, HomeSection.LIBRARY, HomeSection.ABOUT, HomeSection.LOGOUT),
            MeloSettingsPolicy.homeSections(signedIn = true, devUnlocked = false, attention = none)
        )
        assertEquals(
            listOf(HomeSection.APPEARANCE, HomeSection.LIBRARY, HomeSection.ABOUT),
            MeloSettingsPolicy.homeSections(signedIn = false, devUnlocked = false, attention = none)
        )
        assertEquals(
            listOf(HomeSection.ACCOUNT, HomeSection.APPEARANCE, HomeSection.LIBRARY, HomeSection.ATTENTION, HomeSection.DEVELOPER, HomeSection.ABOUT, HomeSection.LOGOUT),
            MeloSettingsPolicy.homeSections(signedIn = true, devUnlocked = true, attention = one)
        )
        // Hidden tools never show a developer section, an attention row shows even with the tools hidden.
        assertFalse(MeloSettingsPolicy.homeSections(true, false, one).contains(HomeSection.DEVELOPER))
        assertTrue(MeloSettingsPolicy.homeSections(true, false, one).contains(HomeSection.ATTENTION))
    }

    @Test
    fun syncValueSaysBatOrTatOnlyWhenKnown() {
        assertEquals("Bật", MeloSettingsPolicy.syncValue(true))
        assertEquals("Tắt", MeloSettingsPolicy.syncValue(false))
        assertNull(MeloSettingsPolicy.syncValue(null))
    }

    @Test
    fun devUnlockedKeyIsNewAndOffByDefault() {
        assertEquals("dev_tools_unlocked", MeloSettingsPolicy.KEY_DEV_UNLOCKED)
        assertEquals(7, MeloSettingsPolicy.DEV_TAPS_REQUIRED)
        assertEquals(2_000L, MeloSettingsPolicy.DEV_TAP_WINDOW_MS)
    }

    @Test
    fun lyricsShortSubtitleIsOneLineAndTheFullDisclosureIsKept() {
        assertTrue(MeloLyricsUiPolicy.SETTINGS_SUBTITLE_SHORT.length < MeloLyricsUiPolicy.SETTINGS_SUBTITLE.length)
        assertFalse(MeloLyricsUiPolicy.SETTINGS_SUBTITLE_SHORT.contains("\n"))
        assertTrue(MeloLyricsUiPolicy.SETTINGS_SUBTITLE_SHORT.contains("LRCLIB"))
        // The words that tell what is sent stay in the text shown when the switch is turned on, and on the privacy page.
        listOf("tên bài", "nghệ sĩ", "album", "thời lượng", "LRCLIB").forEach {
            assertTrue(it, MeloLyricsUiPolicy.CONSENT_TEXT.contains(it))
            assertTrue(it, MeloLyricsUiPolicy.SETTINGS_SUBTITLE.contains(it))
        }
    }
}
