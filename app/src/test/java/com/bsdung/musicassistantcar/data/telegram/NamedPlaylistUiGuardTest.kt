package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Nhiều Playlist có tên, S11-S18: source guards for the screens, in the style of the other source guards of this package. The Views
 * cannot run in a JVM test, so these pin the rules that matter: who may write, which theme a box uses, how big a touch target is, where
 * the playlists are opened and closed, that Car Mode was not given the feature, and that the old Playlist wording is gone.
 */
class NamedPlaylistUiGuardTest {
    private val root: File = listOf(File("src"), File("app/src")).first { it.isDirectory }
    private val telegram = File(root, "shareable/java/com/bsdung/musicassistantcar/data/telegram")
    private fun text(name: String): String = File(telegram, name).readText().replace("\r\n", "\n")

    @Test fun onlyTheControllerTouchesTheNamedSessionFromTheScreens() {
        val ui = text("NamedPlaylistUi.kt")
        for (forbidden in listOf("NamedPlaylistSessionApi", "namedSession(", "TelegramPlaylistRuntime", "NamedPlaylistStore", "NamedPlaylistSession(")) {
            assertFalse("the dialogs must not use $forbidden", ui.contains(forbidden))
        }
        val activity = text("TelegramPreviewActivity.kt")
        val writes = Regex("\\.(createList|renameList|deleteList|addTelegram|removeTelegram|moveItem|addLocal|removeLocal|moveLocal|rebalance)\\(")
        assertTrue("the activity never writes a named playlist itself", writes.findAll(activity).none())
        for (file in listOf("MeloLibraryPane.kt", "MeloCompactScreen.kt", "MeloQueuePane.kt", "MeloNamedPlaylistViews.kt")) {
            assertFalse("$file must not know the session", text(file).contains("NamedPlaylistSession"))
        }
    }

    @Test fun theControllerQueuesEveryReadAndWriteOnThePlaylistThread() {
        val controller = text("NamedPlaylistController.kt")
        for (forbidden in listOf("Thread", "Executor", "runOnUiThread", "Handler", "android.")) {
            assertFalse("the controller owns no thread or Android type: $forbidden", controller.contains(forbidden))
        }
        // every call into the session sits inside a submit { ... } block
        val sessionCalls = Regex("session\\.(open|close|snapshotFor|entries|listsContaining|createList|renameList|deleteList|addTelegram|removeTelegram|moveItem|addLocal|removeLocal|moveLocal)\\(")
        val lines = controller.lines()
        for ((i, line) in lines.withIndex()) {
            if (!sessionCalls.containsMatchIn(line)) continue
            val before = lines.subList(maxOf(0, i - 25), i + 1).joinToString("\n")
            assertTrue("line ${i + 1} is outside a queued task: ${line.trim()}",
                before.contains("submit {") || before.contains("write({") || before.contains("private fun addOne") || line.contains("{ session.") || before.contains("= write("))
        }
    }

    @Test fun everyBoxOfTheNamedPlaylistsUsesTheDarkDialogTheme() {
        val ui = text("NamedPlaylistUi.kt")
        val builders = Regex("AlertDialog\\.Builder\\(([^)]*)\\)").findAll(ui).toList()
        assertTrue(builders.size >= 6)
        for (b in builders) assertTrue(b.value, b.groupValues[1].contains("R.style.Theme_MeloVault_Dialog"))
        assertFalse(ui.contains("setTextColor"))
        assertTrue(ui.contains("ContextThemeWrapper(activity, R.style.Theme_MeloVault_Dialog)"))
    }

    @Test fun everyTouchTargetOfTheNamedScreensIsAtLeast48dp() {
        val ui = text("NamedPlaylistUi.kt")
        assertTrue(ui.contains("minWidth = dp(48)") && ui.contains("minHeight = dp(48)"))
        val views = text("MeloNamedPlaylistViews.kt")
        assertTrue("an index row is at least one touch target high", views.contains("minimumHeight = maxOf(s.minTouch, s.rowHeight)"))
        assertTrue("a row carries its own spoken description", views.contains("contentDescription = row.description"))
        assertTrue(ui.contains("sortUpDescription") && ui.contains("sortDownDescription"))
        assertFalse("no Compose", (ui + views).contains("androidx.compose"))
    }

    @Test fun theNamedPlaylistsAreOpenedAndClosedWhereTheFavouritesAre() {
        val activity = text("TelegramPreviewActivity.kt")
        fun body(name: String): String {
            val start = activity.indexOf("private fun $name(")
            assertTrue(name, start >= 0)
            val next = activity.indexOf("\n    private fun ", start + 10)
            return activity.substring(start, next)
        }
        assertTrue(body("loadSavedPlaylist").contains("namedController.open(user)"))
        assertTrue(body("closeSavedPlaylist").contains("namedController.close()"))
        assertTrue(body("reloadSavedPlaylistLive").contains("namedController.refresh()"))
        assertTrue("the screens ask the playlist thread, with the same account guard as a Save", activity.contains("currentAccount = { library.authorizedUser() }"))
        assertTrue(activity.contains("alive = { !isDestroyed && !isFinishing }"))
    }

    @Test fun theRollbackFlagIsOnAndGatesEveryEntryPointOfTheActivity() {
        assertTrue(text("NamedPlaylistFlags.kt").contains("const val NAMED_ENABLED = true"))
        val activity = text("TelegramPreviewActivity.kt")
        // open, close, refresh, show-index, reset, the host, the detail test, the key list and the two menu entries
        assertTrue(Regex("NamedPlaylistFlags\\.NAMED_ENABLED").findAll(activity).count() >= 10)
        assertTrue(activity.contains("if (NamedPlaylistFlags.NAMED_ENABLED) namedController.open(user)"))
        assertTrue(activity.contains("if (NamedPlaylistFlags.NAMED_ENABLED) namedController.close()"))
        assertTrue(activity.contains("override fun named(): MeloNamedPlaylistHost? = if (NamedPlaylistFlags.NAMED_ENABLED) namedUi else null"))
        // the favourites' own file and flag are untouched
        assertFalse(text("TelegramPlaylistV2Session.kt").contains("NAMED_ENABLED"))
    }

    @Test fun carModeWasNotGivenTheFeature() {
        for (file in listOf("MeloCarScreen.kt", "MeloCarPolicy.kt", "MeloCarLayoutPolicy.kt")) {
            val t = text(file)
            assertFalse("$file knows nothing of named playlists", t.contains("named()") || t.contains("NamedPlaylist") || t.contains("MeloNamedPlaylist"))
        }
        assertTrue("the host default keeps every other host as it was", text("MeloScreenHost.kt").contains("fun named(): MeloNamedPlaylistHost? = null"))
    }

    @Test fun theOldPlaylistWordingIsGoneFromTheScreensWhereItMeantTheFavourites() {
        val old = listOf(
            "Lưu vào Playlist", "Bỏ lưu khỏi Playlist", "Xóa khỏi Playlist", "\"☆ Thêm vào Playlist\"", "★ Bỏ Playlist", "☆ Lưu Playlist",
            "Bỏ lưu bài đang phát khỏi Playlist", "Lưu bài đang phát vào Playlist", "☷ Playlist\"", "bài trong Playlist", "Không đọc được Playlist trên máy",
            "\"☆ Playlist\", \"Playlist các bài yêu thích\"",
        )
        val files = telegram.listFiles { f -> f.isFile && f.extension == "kt" && (f.name.startsWith("Melo") || f.name == "TelegramPreviewActivity.kt" || f.name == "PlaylistSyncStatusPolicy.kt") }
            .orEmpty().filterNot { it.name == "MeloUiPreviewActivity.kt" }
        assertTrue(files.size > 20)
        for (file in files) {
            val t = file.readText().replace("\r\n", "\n")
            for (phrase in old) assertFalse("${file.name} still says: $phrase", t.contains(phrase))
        }
    }

    @Test fun theAppendixATextsExistExactlyWhereTheScreensShowThem() {
        assertEquals("★ Bỏ khỏi Yêu thích", Regex("\"(★ Bỏ khỏi Yêu thích)\"").find(text("MeloCompactScreen.kt"))?.groupValues?.get(1))
        assertTrue(text("MeloLibraryPolicy.kt").contains("\"☆ Thêm vào Yêu thích\""))
        assertTrue(text("MeloLibraryPolicy.kt").contains("\"★ Bỏ khỏi Yêu thích\""))
        assertTrue(text("MeloSavePolicy.kt").contains("Không đọc được Yêu thích trên máy"))
        assertTrue(text("MeloSyncSettingsActivity.kt").contains("Tự đồng bộ Yêu thích & Playlist"))
        assertTrue(text("MeloCarPolicy.kt").contains("PLAYLIST(\"☆\", \"Yêu thích\")"))
        assertTrue(text("TelegramPreviewActivity.kt").contains("vào Yêu thích."))
        assertTrue(text("TelegramPreviewActivity.kt").contains("≡ Thêm vào Playlist…"))
    }

    @Test fun theLibraryPaneShowsTheIndexInPlaceOfTheSongListOnlyOnThePlaylistTab() {
        val pane = text("MeloLibraryPane.kt")
        assertTrue(pane.contains("host.named()?.takeIf { snap.mode == Mode.PLAYLIST && !snap.showingSources && !snap.local }"))
        assertTrue(pane.contains("namedScreen == MeloNamedPlaylistPolicy.Screen.Index"))
        assertTrue("a named playlist keeps its own order: no sort chip, no letter bar", pane.contains("val ownOrder = namedScreen is MeloNamedPlaylistPolicy.Screen.Detail"))
        val compact = text("MeloCompactScreen.kt")
        assertTrue(compact.contains("openPlaylistTab()") && compact.contains("host.named()?.openIndexDialog()"))
    }
}
