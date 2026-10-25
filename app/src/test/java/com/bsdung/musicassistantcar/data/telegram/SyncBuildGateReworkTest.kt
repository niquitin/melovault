package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * E96 P5B3 REWORK-01 (c)(f) and T21: the build switches stay off by default, Release can never turn them on, no
 * production caller can send without an explicit bounded number, and nothing in the sync code starts by itself.
 * Reads the real build file and sources, so a regression in wiring fails here and not on a phone.
 */
class SyncBuildGateReworkTest {
    private val srcRoot = listOf(File("src"), File("app/src")).first { it.isDirectory }
    private val gradle = File(srcRoot.parentFile, "build.gradle").readText().replace("\r\n", "\n")
    private val sync = File(srcRoot, "shareable/java/com/bsdung/musicassistantcar/data/telegram")

    private fun source(name: String) = File(sync, name).readText().replace("\r\n", "\n")

    @Test fun t21_aStandardBuildHasBothSwitchesOff() {
        assertFalse(TelegramPlaylistFlags.SYNC_ENABLED)
        assertFalse(TelegramPlaylistFlags.SYNC_CREATE_ALLOWED)
        assertFalse(com.bsdung.musicassistantcar.BuildConfig.MELO_SYNC_QA_ALLOW_CREATE)
    }

    @Test fun t21_releaseHardcodesBothOffAndAskingForThemInReleaseIsABuildError() {
        val release = gradle.substringAfter("        release {").substringBefore("minifyEnabled")
        assertTrue(release.contains("buildConfigField 'boolean', 'MELO_SYNC_QA_BUILD', 'false'"))
        assertTrue(release.contains("buildConfigField 'boolean', 'MELO_SYNC_QA_ALLOW_CREATE', 'false'"))
        assertTrue(gradle.contains("meloSyncQa=true is only allowed for Debug builds"))
        assertTrue(gradle.contains("meloSyncQaAllowCreate=true is only allowed for Debug builds"))
        // the create switch cannot be on without the QA switch, and defaults to off
        assertTrue(gradle.contains("def meloSyncQaAllowCreate = meloSyncQa && (project.findProperty('meloSyncQaAllowCreate') ?: 'false')"))
        val debug = gradle.substringAfter("        debug {").substringBefore("        release {")
        assertTrue(debug.contains("'MELO_SYNC_QA_ALLOW_CREATE', meloSyncQaAllowCreate ? 'true' : 'false'"))
        assertFalse("never read from local.properties", gradle.contains("localProps.getProperty('melo.sync"))
    }

    @Test fun theCreateSwitchNeedsTheSyncSwitchAndTheFlagsAreNotConstants() {
        val flags = File(sync, "TelegramPlaylistV2Session.kt").readText()
        assertTrue(flags.contains("val SYNC_CREATE_ALLOWED: Boolean get() = SYNC_ENABLED && "))
        assertFalse(flags.contains("const val SYNC_ENABLED") || flags.contains("const val SYNC_CREATE_ALLOWED"))
    }

    @Test fun noBudgetHasAnUnlimitedDefaultOrANullableForm() {
        val coordinator = source("TelegramPlaylistSyncCoordinator.kt")
        assertTrue(coordinator.contains("sendBudget: Int = 0"))
        assertFalse(coordinator.contains("sendBudget: Int?"))
        assertFalse(coordinator.contains("Int.MAX_VALUE"))
        val service = source("TdlibPlaylistSyncService.kt")
        assertTrue(service.contains("sendBudget: Int = 0"))
        assertTrue(service.contains("SyncSendBudget.clamp(sendBudget, sendBudgetCap)"))
        assertTrue(service.contains("private val sendBudgetCap: Int = SyncSendBudget.PRODUCTION_MAX"))
        val port = source("SyncSettingsController.kt")
        assertTrue(port.contains("fun run(account: TelegramUserId, mode: SyncMode, sendBudget: Int = 0)"))
        assertFalse(source("TransmitPlanner.kt").contains("Int.MAX_VALUE"))
    }

    @Test fun everyProductionCallToTheCoordinatorOrTheServiceRunnerIsAccountedFor() {
        val calls = ArrayList<Pair<String, String>>()
        sync.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readText().replace("\r\n", "\n").lines().forEach { line ->
                val t = line.trim()
                if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) return@forEach
                if (Regex("\\.runOnce\\(|\\.runSync\\(|actions\\.run\\(|service\\.runSync\\(").containsMatchIn(line)) calls += file.name to t
            }
        }
        val byFile = calls.groupBy({ it.first }, { it.second })
        assertEquals(calls.toString(), setOf("SyncSettingsController.kt", "TdlibPlaylistSyncService.kt", "SyncServiceProvider.kt"), byFile.keys)
        // the controller passes the grant it took out; the service passes the clamped budget; the trigger bridge reads only
        assertTrue(byFile.getValue("SyncSettingsController.kt").single().contains("actions.run(who, mode, granted)"))
        assertTrue(byFile.getValue("SyncServiceProvider.kt").single().contains("service.runSync(account, mode, sendBudget)"))
        val serviceCalls = byFile.getValue("TdlibPlaylistSyncService.kt")
        assertTrue(serviceCalls.toString(), serviceCalls.any { it.contains(".runOnce(account, mode, budget)") })
        assertTrue(serviceCalls.toString(), serviceCalls.any { it.contains("service.runSync(account, if (reason == SyncReason.FULL_REPLAY) SyncMode.FULL_REPLAY else SyncMode.INCREMENTAL)") })
        assertEquals("the trigger bridge has no budget, i.e. zero: it can only read", 2, serviceCalls.size)
        // and nobody constructs the trigger in production
        val users = sync.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "TdlibPlaylistSyncService.kt" }
            .filter { it.readText().contains("SyncTrigger(") }.map { it.name }.toList()
        assertEquals(emptyList<String>(), users)
    }

    @Test fun nothingStartsASyncByItself() {
        val offenders = sync.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val t = f.readText()
                t.contains("WorkManager") && f.name.startsWith("Sync") || t.contains("PeriodicWorkRequest") && f.name.contains("Sync")
            }.map { it.name }.toList()
        assertEquals(emptyList<String>(), offenders)
        val service = source("TdlibPlaylistSyncService.kt")
        assertFalse(service.contains("Timer") || service.contains("Handler(") || service.contains("scheduleAtFixedRate"))
    }

    @Test fun theUnconfirmedRuleIsInThePlannerNotLeftToACaller() {
        val planner = source("TransmitPlanner.kt")
        assertTrue(planner.contains("OutboxStatus.RETRY_REQUIRED"))
        assertTrue(planner.contains("TransmitPlan.UnconfirmedBlocked"))
        val coordinator = source("TelegramPlaylistSyncCoordinator.kt")
        // an Unknown answer flags the operations and never touches the budget back; no code path re-queues RETRY as PENDING
        assertFalse(coordinator.contains("OutboxStatus.PENDING"))
    }
}
