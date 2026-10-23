package com.bsdung.musicassistantcar.data.telegram

/**
 * E96 P5-A (E): the SPECIFICATION of the install gate that the P5-B checklist and `tools/qa/p5-device-preflight.ps1` follow.
 * Pure data in, a decision out: it never runs adb. A [Decision.Proceed] carries the ONE command that may be run
 * (`adb -s <serial> install -r <apk>`); anything doubtful is a [Decision.Halt] with the gate that stopped it and NO fallback:
 * there is no uninstall, no `pm clear`, no downgrade flag and no other package id anywhere in this file.
 */
internal object QaInstallPreflight {
    data class DeviceListing(val serial: String, val state: String, val model: String?)
    data class InstalledApp(val packageName: String, val versionCode: Long, val certSha256: String)
    data class CandidateApk(
        val packageName: String, val versionCode: Long, val certSha256: String, val abis: Set<String>,
        val telegramConfigValid: Boolean, val sourceCommit: String,
    )

    enum class Gate {
        OWNER_APPROVAL_MISSING, SERIAL_NOT_LISTED, DEVICE_NOT_READY, MODEL_MISMATCH, NOT_INSTALLED, PACKAGE_MISMATCH,
        DOWNGRADE, SIGNATURE_MISMATCH, ABI_MISMATCH, TELEGRAM_CONFIG_INVALID, BAD_FINGERPRINT,
    }

    sealed class Decision {
        data class Proceed(val command: List<String>) : Decision()
        data class Halt(val gate: Gate, val detail: String) : Decision()
    }

    private val SHA256 = Regex("[0-9a-f]{64}")

    fun decide(
        expectedSerial: String,
        expectedModel: String,
        devices: List<DeviceListing>,
        deviceAbis: Set<String>,
        installed: InstalledApp?,
        candidate: CandidateApk,
        apkPath: String,
        needsTelegramLogin: Boolean,
        ownerApproved: Boolean,
    ): Decision {
        // Selection first: an exact serial, never "the only device" and never a guess.
        val dev = devices.firstOrNull { it.serial == expectedSerial }
            ?: return halt(Gate.SERIAL_NOT_LISTED, "serial $expectedSerial is not attached")
        if (devices.count { it.serial == expectedSerial } != 1) return halt(Gate.SERIAL_NOT_LISTED, "serial listed more than once")
        if (dev.state != "device") return halt(Gate.DEVICE_NOT_READY, "state=${dev.state}")
        if (dev.model != expectedModel) return halt(Gate.MODEL_MISMATCH, "model=${dev.model}")
        if (installed == null) return halt(Gate.NOT_INSTALLED, "an update needs an installed app; a first install is a separate decision")
        if (installed.packageName != candidate.packageName) return halt(Gate.PACKAGE_MISMATCH, "${installed.packageName} vs ${candidate.packageName}")
        if (!SHA256.matches(installed.certSha256) || !SHA256.matches(candidate.certSha256)) return halt(Gate.BAD_FINGERPRINT, "fingerprint is not a sha-256")
        if (candidate.versionCode < installed.versionCode) return halt(Gate.DOWNGRADE, "${candidate.versionCode} < ${installed.versionCode}")
        if (candidate.certSha256 != installed.certSha256) return halt(Gate.SIGNATURE_MISMATCH, "certificates differ")
        if (candidate.abis.intersect(deviceAbis).isEmpty()) return halt(Gate.ABI_MISMATCH, "apk ${candidate.abis} device $deviceAbis")
        if (needsTelegramLogin && !candidate.telegramConfigValid) return halt(Gate.TELEGRAM_CONFIG_INVALID, "login would not work")
        if (!ownerApproved) return halt(Gate.OWNER_APPROVAL_MISSING, "every technical gate passed; the owner has not approved this install")
        return Decision.Proceed(listOf("adb", "-s", expectedSerial, "install", "-r", apkPath))
    }

    private fun halt(gate: Gate, detail: String) = Decision.Halt(gate, detail)
}
