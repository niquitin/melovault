package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.QaInstallPreflight.Decision
import com.bsdung.musicassistantcar.data.telegram.QaInstallPreflight.Gate
import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistSession.ToggleOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * E96 P5-A (D, E): local Save stays durable and honest with the cloud OFF, and the install gate halts - never works
 * around - a wrong serial, signature, package, version or build.
 */
class P5LocalSaveAndGatesTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val chat = -1_001_000_000_900L

    private fun device() = FakeSyncDevice("dev-a", folder.newFolder(), acct, FakeTelegramChannel(), 9)

    // ---- D: local Save with the cloud OFF ---------------------------------------------------------------------------------

    @Test fun d01_saveAndUnsaveAreDurableAcrossARestartWithTheCloudOff() {
        val d = device()
        assertTrue(d.toggle(chat, songId(1)) is ToggleOutcome.Added)
        assertTrue(d.toggle(chat, songId(2)) is ToggleOutcome.Added)
        assertTrue(d.toggle(chat, songId(1)) is ToggleOutcome.Removed)
        d.restart()
        assertEquals(listOf(TelegramSongKey.forMessage(chat, songId(2), acct.value)), d.present())
        val line = PlaylistSyncStatusPolicy.evaluate(acct.value, d.session.statusFacts(acct), SyncContext(false, SyncConsent()))
        assertEquals(PlaylistSyncStatusPolicy.Kind.LOCAL_SYNC_OFF, line.kind)
    }

    @Test fun d02_theSaveButtonStaysReachableWithTheCloudOff() {
        val key = TelegramSongKey.forMessage(chat, songId(1), acct.value)
        val state = MeloSavePolicy.buttonState(
            MeloSavePolicy.availability(true, false, key, true, chat, songId(1), acct.value, false, true), saved = false, busy = false,
        )
        assertTrue("a switched-off cloud never disables a local Save", state.actionable)
    }

    @Test fun d03_aFullOutboxRefusesTheNewSaveLoudlyAndKeepsEverythingElse() {
        val d = device()
        d.toggle(chat, songId(1))
        val view = d.session.view(acct)!!
        val filler = (1..PlaylistState.MAX_OUTBOX).map { OutboxEntry(addOp("old", it.toLong(), chat, songId(it + 1_000)), OutboxStatus.PENDING, 0) }
        d.session.update(acct, view.epoch) { it.copy(outbox = filler) }
        assertEquals(ToggleOutcome.OutboxFull, d.toggle(chat, songId(2)))
        assertEquals("the earlier Save is still there", 1, d.present().size)
        val line = PlaylistSyncStatusPolicy.evaluate(acct.value, d.session.statusFacts(acct), SyncContext(true, SyncConsent(true, true, true, SyncConsent.WARNING_VERSION)))
        assertNotEquals(PlaylistSyncStatusPolicy.Kind.SYNCED, line.kind)
    }

    @Test fun d04_rapidTapsAreFoldedByTheGuardIntoTheMinimumNumberOfRealWrites() {
        val d = device()
        val target = PlaylistTarget(TelegramSongKey.forMessage(chat, songId(1), acct.value), chat, songId(1))
        val guard = SaveToggleGuard<PlaylistTarget>()
        var writes = 0
        fun write(t: PlaylistTarget) { writes++; d.session.toggle(acct, t) }
        // Five quick taps while the first write is running: 1 write + (4 extras = even) = exactly one real toggle.
        if (guard.tap(target)) write(target)
        repeat(4) { guard.tap(target) }
        var next = guard.finish(ok = true)
        while (next != null) { write(next); next = guard.finish(ok = true) }
        assertEquals(1, writes)
        assertEquals(1, d.present().size)
        // Six more taps = an even number of intended toggles: 1 write + (5 extras = odd) = two real writes, ending as it started.
        if (guard.tap(target)) write(target)
        repeat(5) { guard.tap(target) }
        next = guard.finish(ok = true)
        while (next != null) { write(next); next = guard.finish(ok = true) }
        assertEquals(3, writes)
        assertEquals("six taps end where they began", 1, d.present().size)
        assertEquals("every real toggle is one outbox operation, none duplicated", 3, d.stored().outbox.size)
    }

    @Test fun d05_aV1ImportThatHadToWaitDoesNotHideTheLocalPlaylistWhileTheCloudIsOff() {
        val d = device()
        d.toggle(chat, songId(1))
        val line = PlaylistSyncStatusPolicy.evaluate(
            acct.value, d.session.statusFacts(acct)!!.copy(importWarning = PlaylistWarning.IMPORT_DEFERRED_OUTBOX_FULL), SyncContext(false, SyncConsent()),
        )
        assertEquals(PlaylistSyncStatusPolicy.Kind.IMPORT_DEFERRED, line.kind)
        assertEquals(1, d.present().size)
    }

    // ---- E: install gate ------------------------------------------------------------------------------------------------

    private val sonyCert = "e".repeat(64)
    private val ni = "a".repeat(64)
    private val sony = QaInstallPreflight.DeviceListing("QV725WEQ54", "device", "XQ-AS52")
    private val installed = QaInstallPreflight.InstalledApp("app.melovault.player", 109, sonyCert)
    private val candidate = QaInstallPreflight.CandidateApk(
        "app.melovault.player", 109, sonyCert, setOf("arm64-v8a"), telegramConfigValid = true, sourceCommit = "abc1234",
    )

    private fun decide(
        devices: List<QaInstallPreflight.DeviceListing> = listOf(sony),
        inst: QaInstallPreflight.InstalledApp? = installed,
        cand: QaInstallPreflight.CandidateApk = candidate,
        approved: Boolean = true,
        needsLogin: Boolean = true,
        abis: Set<String> = setOf("arm64-v8a", "armeabi-v7a"),
    ) = QaInstallPreflight.decide("QV725WEQ54", "XQ-AS52", devices, abis, inst, cand, "C:\\qa\\app.apk", needsLogin, approved)

    private fun assertHalt(gate: Gate, d: Decision) {
        assertTrue("$d", d is Decision.Halt)
        assertEquals(gate, (d as Decision.Halt).gate)
    }

    @Test fun e01_everyGatePassingAndTheOwnerApprovalGivesTheOneReplaceableUpdateCommand() {
        val d = decide()
        assertEquals(listOf("adb", "-s", "QV725WEQ54", "install", "-r", "C:\\qa\\app.apk"), (d as Decision.Proceed).command)
    }

    @Test fun e02_withoutTheOwnersApprovalNothingIsEverRunWhateverElseIsGreen() {
        assertHalt(Gate.OWNER_APPROVAL_MISSING, decide(approved = false))
    }

    @Test fun e03_aDifferentSigningCertificateHaltsAndOffersNoUninstallFallback() {
        val d = decide(cand = candidate.copy(certSha256 = ni))
        assertHalt(Gate.SIGNATURE_MISMATCH, d)
        assertFalse((d as Decision.Halt).detail.contains("uninstall", ignoreCase = true))
    }

    @Test fun e04_aLowerVersionCodeHaltsAsADowngradeAndASameVersionCodeIsAnAllowedUpdate() {
        assertHalt(Gate.DOWNGRADE, decide(cand = candidate.copy(versionCode = 108)))
        assertTrue(decide(cand = candidate.copy(versionCode = 109)) is Decision.Proceed)
        assertTrue(decide(cand = candidate.copy(versionCode = 110)) is Decision.Proceed)
    }

    @Test fun e05_aDifferentPackageIdHaltsInsteadOfBeingInstalledSideBySide() {
        assertHalt(Gate.PACKAGE_MISMATCH, decide(cand = candidate.copy(packageName = "app.melovault.player.qa")))
    }

    @Test fun e06_aMissingInstallHaltsBecauseAFirstInstallIsASeparateDecision() {
        assertHalt(Gate.NOT_INSTALLED, decide(inst = null))
    }

    @Test fun e07_theWrongSerialOrTwoDevicesOrAnUnauthorizedDeviceNeverGetAnInstall() {
        assertHalt(Gate.SERIAL_NOT_LISTED, decide(devices = emptyList()))
        assertHalt(Gate.SERIAL_NOT_LISTED, decide(devices = listOf(sony.copy(serial = "30313860180939"))))
        assertHalt(Gate.DEVICE_NOT_READY, decide(devices = listOf(sony.copy(state = "unauthorized"))))
        assertHalt(Gate.DEVICE_NOT_READY, decide(devices = listOf(sony.copy(state = "offline"))))
        assertHalt(Gate.SERIAL_NOT_LISTED, decide(devices = listOf(sony, sony)))
        // A second, other device present does not change which serial is used.
        val both = decide(devices = listOf(QaInstallPreflight.DeviceListing("30313860180939", "device", "M3 Ultra"), sony))
        assertEquals("QV725WEQ54", ((both as Decision.Proceed).command)[2])
    }

    @Test fun e08_aSerialWhoseModelIsNotTheExpectedOneHalts() {
        assertHalt(Gate.MODEL_MISMATCH, decide(devices = listOf(sony.copy(model = "SM-S928B"))))
        assertHalt(Gate.MODEL_MISMATCH, decide(devices = listOf(sony.copy(model = null))))
    }

    @Test fun e09_anApkForAnotherAbiOrWithoutTelegramConfigHaltsWhenLoginIsNeeded() {
        assertHalt(Gate.ABI_MISMATCH, decide(cand = candidate.copy(abis = setOf("x86_64"))))
        assertHalt(Gate.TELEGRAM_CONFIG_INVALID, decide(cand = candidate.copy(telegramConfigValid = false)))
        assertTrue("a pure UI check without login may use an unconfigured build", decide(cand = candidate.copy(telegramConfigValid = false), needsLogin = false) is Decision.Proceed)
    }

    @Test fun e10_aMalformedFingerprintIsNeverTreatedAsAMatch() {
        assertHalt(Gate.BAD_FINGERPRINT, decide(inst = installed.copy(certSha256 = "")))
        assertHalt(Gate.BAD_FINGERPRINT, decide(cand = candidate.copy(certSha256 = "xyz")))
    }

    @Test fun e11_theOnlyCommandThisGateCanEverProduceIsAReplaceInstallOfTheChosenSerial() {
        val commands = listOf(
            decide(), decide(cand = candidate.copy(versionCode = 200)), decide(needsLogin = false),
        ).map { (it as Decision.Proceed).command }
        for (c in commands) {
            assertEquals(listOf("adb", "-s", "QV725WEQ54", "install", "-r", "C:\\qa\\app.apk"), c)
            assertFalse(c.any { it == "uninstall" || it == "-d" || it == "clear" || it == "-g" || it == "-t" })
        }
        val source = java.io.File(listOf("src", "app/src").map { java.io.File(it) }.first { it.isDirectory },
            "test/java/com/bsdung/musicassistantcar/data/telegram/QaInstallPreflight.kt").readText()
        val code = source.lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") || it.trimStart().startsWith("//") }.joinToString("\n")
        assertFalse(code.contains("\"uninstall\"") || code.contains("\"clear\"") || code.contains("pm clear"))
    }
}
