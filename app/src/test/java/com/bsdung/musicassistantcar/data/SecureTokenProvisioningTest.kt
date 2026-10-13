package com.bsdung.musicassistantcar.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenProvisioningTest {

    private lateinit var tempDir: File
    private lateinit var jvmAesGcmEngine: SecureTokenStore.CipherEngine

    private class JvmAesGcmTestCipherEngine : SecureTokenStore.CipherEngine {
        private val secretKey: SecretKey = KeyGenerator.getInstance("AES").apply {
            init(256)
        }.generateKey()

        override fun encrypt(plaintext: ByteArray): SecureTokenStore.EncryptedPayload {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            return SecureTokenStore.EncryptedPayload(
                iv = cipher.iv,
                ciphertext = cipher.doFinal(plaintext),
            )
        }

        override fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            return cipher.doFinal(ciphertext)
        }
    }

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("melovault_secure_token_test").toFile()
        jvmAesGcmEngine = JvmAesGcmTestCipherEngine()
        SecureTokenStore.initForTesting(
            dir = tempDir,
            customCipherEngine = jvmAesGcmEngine,
            fallbackTokenSupplier = { "" },
        )
    }

    @After
    fun tearDown() {
        SecureTokenStore.resetAfterTesting()
        tempDir.deleteRecursively()
    }

    @Test
    fun testUnconfiguredStateWhenNoRuntimeOrFallbackTokenExists() {
        assertFalse(SecureTokenStore.hasRuntimeToken())
        assertFalse(CarApiClient.isTokenConfigured())
        assertEquals("", CarApiClient.activeApiToken())
        assertTrue(CarApiClient.currentAuthorizationHeaders().isEmpty())
    }

    @Test
    fun testAesGcmEncryptionStoresOnlyCiphertextOnDiskNeverPlaintext() {
        val sampleToken = "mv_test_ephemeral_token_998877665544332211"
        assertTrue(SecureTokenStore.saveRuntimeToken(sampleToken))

        val encFile = File(tempDir, SecureTokenStore.TOKEN_FILE_NAME)
        assertTrue("Encrypted token file must exist in noBackupFilesDir", encFile.exists())

        val diskContents = encFile.readText(Charsets.UTF_8)
        assertTrue("Envelope must start with v1:", diskContents.startsWith("v1:"))
        assertFalse("Disk file must NEVER contain plaintext token", diskContents.contains(sampleToken))

        // Simulate fresh app restart with the same key and storage dir
        SecureTokenStore.initForTesting(
            dir = tempDir,
            customCipherEngine = jvmAesGcmEngine,
            fallbackTokenSupplier = { "" },
        )

        assertTrue(SecureTokenStore.hasRuntimeToken())
        assertTrue(CarApiClient.isTokenConfigured())
        assertEquals(sampleToken, CarApiClient.activeApiToken())
        assertEquals(
            "Bearer $sampleToken",
            CarApiClient().authorizationHeaders()["Authorization"],
        )
    }

    @Test
    fun testRandomizedGcmIvProducesDistinctCiphertextForSameToken() {
        val sampleToken = "mv_randomized_iv_verification_token"
        assertTrue(SecureTokenStore.saveRuntimeToken(sampleToken))
        val envelope1 = File(tempDir, SecureTokenStore.TOKEN_FILE_NAME).readText(Charsets.UTF_8)

        assertTrue(SecureTokenStore.saveRuntimeToken(sampleToken))
        val envelope2 = File(tempDir, SecureTokenStore.TOKEN_FILE_NAME).readText(Charsets.UTF_8)

        assertNotEquals("Each AES-GCM save must generate a fresh random IV", envelope1, envelope2)
        assertEquals(sampleToken, CarApiClient.activeApiToken())
    }

    @Test
    fun testTamperedCiphertextFailsGcmAuthenticationAndReturnsEmpty() {
        val sampleToken = "mv_tamper_detection_test_token"
        assertTrue(SecureTokenStore.saveRuntimeToken(sampleToken))

        val encFile = File(tempDir, SecureTokenStore.TOKEN_FILE_NAME)
        val parts = encFile.readText(Charsets.UTF_8).trim().split(":")
        assertEquals(3, parts.size)

        val cipherBytes = Base64.getDecoder().decode(parts[2])
        cipherBytes[cipherBytes.lastIndex] = (cipherBytes[cipherBytes.lastIndex].toInt() xor 0xFF).toByte()
        val tamperedB64 = Base64.getEncoder().encodeToString(cipherBytes)
        encFile.writeText("${parts[0]}:${parts[1]}:$tamperedB64", Charsets.UTF_8)

        // Reload from disk after tampering
        SecureTokenStore.initForTesting(
            dir = tempDir,
            customCipherEngine = jvmAesGcmEngine,
            fallbackTokenSupplier = { "" },
        )

        assertFalse("Tampered GCM tag must fail authentication", SecureTokenStore.hasRuntimeToken())
        assertEquals("", CarApiClient.activeApiToken())
    }

    @Test
    fun testReplaceAndClearRuntimeTokenLifecycle() {
        val firstToken = "mv_first_runtime_token_111"
        val secondToken = "mv_second_runtime_token_222"

        assertTrue(SecureTokenStore.saveRuntimeToken(firstToken))
        assertEquals(firstToken, CarApiClient.activeApiToken())

        // Replace token
        assertTrue(SecureTokenStore.saveRuntimeToken(secondToken))
        assertEquals(secondToken, CarApiClient.activeApiToken())

        // Clear token (even if a debug fallback exists, explicit clear transitions to unconfigured)
        SecureTokenStore.initForTesting(
            dir = tempDir,
            customCipherEngine = jvmAesGcmEngine,
            fallbackTokenSupplier = { "debug_fallback_token" },
        )
        assertTrue(SecureTokenStore.clearRuntimeToken())
        assertFalse(File(tempDir, SecureTokenStore.TOKEN_FILE_NAME).exists())
        assertFalse(CarApiClient.hasRuntimeToken())
        assertFalse(CarApiClient.isTokenConfigured())
        assertEquals("", CarApiClient.activeApiToken())
    }

    @Test
    fun testCarApiClientContainsZeroDirectBuildConfigTokenReads() {
        val candidates = listOf(
            File("src/main/java/com/bsdung/musicassistantcar/data/CarApiClient.kt"),
            File("app/src/main/java/com/bsdung/musicassistantcar/data/CarApiClient.kt"),
        )
        val sourceFile = candidates.firstOrNull { it.exists() }
        assertTrue("CarApiClient.kt must exist", sourceFile != null)
        val content = sourceFile!!.readText(Charsets.UTF_8)
        assertFalse(
            "CarApiClient.kt must not read BuildConfig.CAR_API_TOKEN directly",
            content.contains("BuildConfig.CAR_API_TOKEN"),
        )
    }

    @Test
    fun testBackupAndDataExtractionRulesExcludeSecureTokenFromCloudAndDeviceTransfer() {
        val rootPrefix = if (File("src/main/AndroidManifest.xml").exists()) "src/main" else "app/src/main"
        val manifest = File("$rootPrefix/AndroidManifest.xml").readText(Charsets.UTF_8)
        val backupRules = File("$rootPrefix/res/xml/backup_rules.xml").readText(Charsets.UTF_8)
        val extractionRules = File("$rootPrefix/res/xml/data_extraction_rules.xml").readText(Charsets.UTF_8)

        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(backupRules.contains("melovault_secure_token.enc"))
        assertTrue(extractionRules.contains("<cloud-backup>"))
        assertTrue(extractionRules.contains("<device-transfer>"))
        assertTrue(extractionRules.contains("melovault_secure_token.enc"))
    }
}
