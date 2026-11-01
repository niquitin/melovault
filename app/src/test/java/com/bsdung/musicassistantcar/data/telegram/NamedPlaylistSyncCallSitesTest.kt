package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Nhiều Playlist có tên, S8: the same kind of source guard as [SyncBuildGateReworkTest], for the named playlists. That older guard
 * counts `.runOnce(` / `actions.run(` / `service.runSync(` and is left exactly as it was; the named layer therefore has its own
 * entry names (`runNamedOnce`, `runNamed`, `runNamedSync`) and this test accounts for every production call of them.
 */
class NamedPlaylistSyncCallSitesTest {
    private val sync: File = listOf(File("src"), File("app/src")).first { it.isDirectory }
        .let { File(it, "shareable/java/com/bsdung/musicassistantcar/data/telegram") }

    private fun callsOf(pattern: Regex): Map<String, List<String>> {
        val calls = ArrayList<Pair<String, String>>()
        sync.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readText().replace("\r\n", "\n").lines().forEach { line ->
                val t = line.trim()
                if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) return@forEach
                if (pattern.containsMatchIn(line)) calls += file.name to t
            }
        }
        return calls.groupBy({ it.first }, { it.second })
    }

    @Test fun onlyTheTdlibServiceRunsTheNamedCoordinatorAndItConstructsItOnce() {
        val runs = callsOf(Regex("\\.runNamedOnce\\("))
        assertEquals(runs.toString(), setOf("TdlibPlaylistSyncService.kt"), runs.keys)
        assertEquals(1, runs.getValue("TdlibPlaylistSyncService.kt").size)
        assertTrue(runs.getValue("TdlibPlaylistSyncService.kt").single().contains("runNamedOnce(account, channel, effectiveMode, budget)"))
        val built = callsOf(Regex("NamedPlaylistSyncCoordinator\\("))
        assertEquals(built.toString(), setOf("TdlibPlaylistSyncService.kt"), built.keys.minus("NamedPlaylistSyncCoordinator.kt"))
    }

    @Test fun onlyTheProviderCallsTheNamedServiceRunnerAndOnlyTheSchedulerCallsThePort() {
        val service = callsOf(Regex("service\\.runNamedSync\\("))
        assertEquals(service.toString(), setOf("SyncServiceProvider.kt"), service.keys)
        assertEquals(1, service.getValue("SyncServiceProvider.kt").size)
        val port = callsOf(Regex("actions\\.runNamed\\("))
        assertEquals(port.toString(), setOf("AutomaticPlaylistSync.kt"), port.keys)
        val line = port.getValue("AutomaticPlaylistSync.kt").single()
        assertTrue(line, line.contains("NamedSyncLimits.PRODUCTION_ENVELOPES"))
    }

    @Test fun theSchedulerRunsTheNamedPlaylistsOnlyAfterTheFavouritesAndOnlyWithTheSendingConsent() {
        val text = File(sync, "AutomaticPlaylistSync.kt").readText().replace("\r\n", "\n")
        val favourites = text.indexOf("actions.run(s.who, mode, SyncSendBudget.PRODUCTION_MAX)")
        val named = text.indexOf("actions.runNamed(")
        assertTrue(favourites in 0 until named)
        val guard = text.substring(favourites, named)
        assertTrue(guard.contains("store.load(s.who).automatic"))
        assertTrue(guard.contains("current(s)"))
        assertTrue(guard.contains("NAMED_SKIPPED_AFTER"))
    }

    @Test fun theNamedLayerStartsNoThreadWorkerOrTimerOfItsOwn() {
        for (name in listOf("NamedPlaylistSyncCoordinator.kt", "NamedPlaylistSyncProtocol.kt", "NamedPlaylistSession.kt", "NamedPlaylistStore.kt")) {
            val text = File(sync, name).readText()
            for (forbidden in listOf("Thread", "Executor", "WorkManager", "Handler(", "Timer", "scheduleAtFixedRate", "sleep(", "currentTimeMillis")) {
                assertFalse("$name must not use $forbidden", text.contains(forbidden))
            }
        }
    }

    @Test fun theFavouritesClassesWereNotGivenAnyKnowledgeOfNamedPlaylists() {
        for (name in listOf(
            "TelegramPlaylistModel.kt", "TelegramPlaylistCodec.kt", "TelegramPlaylistStoreV2.kt", "TelegramPlaylistV2Session.kt",
            "TelegramPlaylistSyncProtocol.kt", "TelegramPlaylistSyncCoordinator.kt", "TransmitPlanner.kt", "TelegramPlaylistSyncTransport.kt",
        )) {
            val text = File(sync, name).readText()
            assertFalse("$name must not mention named playlists", text.contains("Named") || text.contains("mvlist"))
        }
    }
}
