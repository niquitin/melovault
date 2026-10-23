package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * E96 P5-A (A): with the build switch off the production composition is inert - no owner is asked, no host is read, no
 * consent file is opened and no TDLib request exists - on launch, resume, Save, Settings and account changes. Fake TDLib only.
 */
class P5ReadinessInertnessTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val other = TelegramUserId(2002L)
    private val rigs = ArrayList<SyncRig>()

    @After fun tearDown() = rigs.forEach { it.stop() }

    private fun rig(build: Boolean, consent: Boolean): SyncRig =
        SyncRig("p5-dev", folder.newFolder(), FakeTdlibWorld(), acct, channelId = 0L, seed = 41, buildEnabled = build, consent = consent)
            .also { rigs.add(it) }

    private class Counters {
        val owner = AtomicInteger()
        val host = AtomicInteger()
    }

    private fun provider(r: SyncRig, build: Boolean, c: Counters = Counters(), host: PlaylistSyncHost? = r.device.session) = SyncServiceProvider(
        buildEnabled = build,
        owner = { c.owner.incrementAndGet(); r.tdlib.core },
        host = { c.host.incrementAndGet(); host },
        store = r.consentStore,
        clock = { r.device.nowMs },
        timeouts = TdlibTransportTimeouts(requestMs = 2_000L, sendAckMs = 400L),
    )

    @Test fun a01_withTheSwitchOffTheProviderAnswersDisabledWithoutAskingOwnerHostOrStore() {
        val r = rig(build = false, consent = true)
        val c = Counters()
        val p = provider(r, build = false, c)
        repeat(5) { assertSame(DisabledSyncActions, p.actions()) } // launch, resume, Settings open, rotate, account change
        assertEquals(0, c.owner.get())
        assertEquals(0, c.host.get())
        assertFalse(p.created())
    }

    @Test fun a02_withTheSwitchOffEveryPortCallIsDeniedAndNoRequestReachesTheFakeTdlib() {
        val r = rig(build = false, consent = true) // even with all three consents on the file
        val before = r.tdlib.gateway.requests.size
        val clients = r.tdlib.gateway.clientsCreated
        val port = provider(r, build = false).actions()
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), port.discover(acct))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), port.create(acct))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), port.bind(acct, -5L))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), port.run(acct, SyncMode.INCREMENTAL))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), port.run(acct, SyncMode.FULL_REPLAY))
        port.close()
        assertEquals(before, r.tdlib.gateway.requests.size)
        assertEquals("no new TDLib client either", clients, r.tdlib.gateway.clientsCreated)
        assertFalse("no sink was ever bound", r.tdlib.core.syncIsBound())
    }

    @Test fun a03_savingUnsavingAndReadingTheStatusWithTheSwitchOffSendNothing() {
        val r = rig(build = false, consent = false)
        val before = r.tdlib.gateway.requests.size
        val port = provider(r, build = false).actions()
        r.device.toggle(-1_001_000_000_900L, songId(1))
        r.device.toggle(-1_001_000_000_900L, songId(2))
        r.device.toggle(-1_001_000_000_900L, songId(1))
        PlaylistSyncStatusPolicy.evaluate(acct.value, r.device.session.statusFacts(acct), SyncContext(false, SyncConsent()))
        port.close()
        assertEquals(before, r.tdlib.gateway.requests.size)
        assertEquals(0, r.tdlib.gateway.count("sendMessage"))
        assertEquals("two adds and a remove: one song left", 1, r.device.session.statusFacts(acct)!!.savedCount)
    }

    @Test fun a04_theDefaultProductionHolderIsDisabledAndDoesNotTouchTheTdlibOwner() {
        assertFalse(TelegramPlaylistFlags.SYNC_ENABLED)
        val provider = SyncRuntime.provider(folder.newFolder())
        assertSame(DisabledSyncActions, provider.actions())
        assertFalse(provider.created())
    }

    @Test fun a05_aPlaylistThatCannotBeSynchronisedKeepsTheProviderDisabledEvenWithTheSwitchOn() {
        val r = rig(build = true, consent = true)
        val c = Counters()
        val p = provider(r, build = true, c, host = null) // a V1-only session is not a PlaylistSyncHost
        assertSame(DisabledSyncActions, p.actions())
        assertEquals(0, c.owner.get())
        assertFalse(p.created())
    }

    @Test fun a06_aSwitchedOnProviderCreatesTheServiceOnlyWhenAskedAndOnlyOnce() {
        val r = rig(build = true, consent = true)
        val c = Counters()
        val p = provider(r, build = true, c)
        assertFalse("nothing exists before the first request", p.created())
        val first = p.actions()
        assertTrue(first is TdlibSyncActions)
        assertSame(first, p.actions())
        assertEquals("one service, one owner lookup", 1, c.owner.get())
        assertEquals("constructing it asked TDLib nothing", 0, r.tdlib.gateway.count("getChats"))
    }

    @Test fun a07_noProductionFileOtherThanTheProviderReachesTheSyncServiceAndTheProviderChecksTheSwitchFirst() {
        val srcRoot = listOf(File("src"), File("app/src")).first { it.isDirectory }
        val offenders = File(srcRoot, "shareable").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "TdlibPlaylistSyncService.kt" }
            .filter { it.readText().contains("TdlibPlaylistSyncService(") }.map { it.name }.toList()
        assertEquals("the service is constructed in exactly one place", listOf("SyncServiceProvider.kt"), offenders)
        val text = File(srcRoot, "shareable/java/com/bsdung/musicassistantcar/data/telegram/SyncServiceProvider.kt").readText()
        val guard = text.indexOf("if (!buildEnabled) return DisabledSyncActions")
        val build = text.indexOf("TdlibPlaylistSyncService(\n")
        assertTrue(guard in 0 until build)
        assertFalse("no trigger, timer or worker is created for sync", text.contains("SyncTrigger") || text.contains("WorkManager"))
    }

    @Test fun a08_noBootReceiverNewServiceOrWorkerWasAddedForSync() {
        val srcRoot = listOf(File("src"), File("app/src")).first { it.isDirectory }
        for (manifest in listOf("main", "shareable")) {
            val text = File(srcRoot, "$manifest/AndroidManifest.xml").readText()
            assertFalse(manifest, text.contains("BOOT_COMPLETED"))
            assertFalse(manifest, text.contains("RECEIVE_BOOT"))
        }
        val shareable = File(srcRoot, "shareable/AndroidManifest.xml").readText()
        assertEquals("only the three services that already existed", 3, Regex("<service ").findAll(shareable).count())
        assertFalse(shareable.contains("<receiver"))
        val sources = File(srcRoot, "shareable").walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.readText() }.toList()
        assertFalse("no periodic or one-time sync work request",
            sources.any { it.contains("PeriodicWorkRequest") && it.contains("PlaylistSync") })
    }

    @Test fun a09_aLogoutClearsTheLastReportSoNoStatusOutlivesItsSession() {
        val log = SyncRunLog()
        log.record(LastSyncRun(acct.value, SyncStatus.OK, opCounterAtRun = 3L))
        log.record(LastSyncRun(other.value, SyncStatus.BACKOFF))
        assertEquals(SyncStatus.OK, log.last(acct.value)!!.status)
        log.clear()
        assertEquals(null, log.last(acct.value))
        assertEquals(null, log.last(other.value))
    }

    @Test fun a10_theBuildAskedFlagOffMeansTheStatusLineCanNeverSaySyncedEvenWithAnOkReportOnRecord() {
        val r = rig(build = false, consent = true)
        r.device.toggle(-1_001_000_000_900L, songId(1))
        val f = r.device.session.statusFacts(acct)!!
        val ok = LastSyncRun(acct.value, SyncStatus.OK, opCounterAtRun = f.opCounter)
        val line = PlaylistSyncStatusPolicy.evaluate(acct.value, f, SyncContext(false, r.consentStore.load(acct), false, ok))
        assertEquals(PlaylistSyncStatusPolicy.Kind.LOCAL_SYNC_OFF, line.kind)
    }
}
