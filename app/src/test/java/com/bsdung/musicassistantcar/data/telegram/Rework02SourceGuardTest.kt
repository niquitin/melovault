package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** E96 P5B3 REWORK-02: the ordering and wiring that the behavioural tests rely on, pinned in the source so it cannot drift. */
class Rework02SourceGuardTest {
    private val sync = File(listOf(File("src"), File("app/src")).first { it.isDirectory }, "shareable/java/com/bsdung/musicassistantcar/data/telegram")
    private fun source(name: String) = File(sync, name).readText().replace("\r\n", "\n")

    @Test fun theDoubtIsWrittenBeforeTheOnlyTransportSendAndNothingFlagsAfterwards() {
        val coordinator = source("TelegramPlaylistSyncCoordinator.kt")
        val sends = Regex("transport\\.send\\(").findAll(coordinator).toList()
        assertEquals("exactly one dispatch point", 1, sends.size)
        val flags = Regex("acc\\.markRetryRequired\\(id\\)").findAll(coordinator).toList()
        assertEquals("exactly one place flags operations", 1, flags.size)
        assertTrue("the flag is committed first", flags.single().range.first < sends.single().range.first)
        val ahead = coordinator.substring(flags.single().range.first, sends.single().range.first)
        assertTrue("and a write that did not commit stops before the send", ahead.contains("SEND_PRECHECK_PERSIST") && ahead.contains("return stop("))
    }

    @Test fun onlyTheRefusalPathGivesTheFlagBackAndOnlyForADefiniteRefusal() {
        val coordinator = source("TelegramPlaylistSyncCoordinator.kt")
        val reverts = Regex("revertRefusedDispatch").findAll(coordinator).toList()
        assertEquals(1, reverts.size)
        val failedBranch = coordinator.substringAfter("is SendResult.Failed -> {").substringBefore("return stop(failure(")
        assertTrue(failedBranch.contains("revertRefusedDispatch"))
        assertFalse(coordinator.substringAfter("is SendResult.Unknown -> {").substringBefore("is SendResult.Failed").contains("revertRefusedDispatch"))
    }

    @Test fun aSendMessageErrorIsAFailureOnlyForA4xxCode() {
        val transport = source("TdlibPlaylistSyncTransport.kt")
        val errBranch = transport.substringAfter("is TdlibSyncHub.Reply.Err -> {").substringBefore("TdlibSyncHub.Reply.Timeout")
        assertTrue(errBranch.contains("reply.code in 400..499"))
        assertTrue(errBranch.contains("unknown(SyncStage.SEND_DISPATCH_ERROR, ErrorBucket.of(reply.code))"))
    }

    @Test fun onlyABudgetAboveZeroNeedsConsentToTransmitAtTheService() {
        val service = source("TdlibPlaylistSyncService.kt")
        assertTrue(service.contains("if (waiting && budget > 0 && gate.mayTransmit(account) != null) return denied(SyncDenied.TRANSMIT_NOT_ALLOWED)"))
    }

    @Test fun theGrantComesBackOnlyInTheEpochItWasTakenIn() {
        val controller = source("SyncSettingsController.kt")
        assertTrue(controller.contains("val startEpoch = epoch()"))
        assertTrue(controller.contains("epoch() == startEpoch && store.load(who).mayTransmitInitial"))
    }
}
