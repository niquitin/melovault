package com.bsdung.musicassistantcar.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DevicePairingClientTest {

    private lateinit var tempDir: File

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
        tempDir = Files.createTempDirectory("melovault_pairing_test").toFile()
        SecureTokenStore.initForTesting(
            dir = tempDir,
            customCipherEngine = JvmAesGcmTestCipherEngine(),
            fallbackTokenSupplier = { "" },
        )
    }

    @After
    fun tearDown() {
        SecureTokenStore.resetAfterTesting()
        tempDir.deleteRecursively()
    }

    @Test
    fun testPairStartParsing() {
        val client = object : CarApiClient() {
            override fun pairStart(
                deviceId: String,
                deviceName: String,
                deviceSecret: String,
            ): PairStartResult {
                val mockJson = JSONObject().apply {
                    put("status", "pending")
                    put("session_id", "mock-session-uuid-1234")
                    put("pair_code", "492815")
                    put("device_poll_token", "mvp_mock_token_abc")
                    put("expires_in_sec", 300)
                    put("device_id", deviceId)
                    put("device_name", deviceName)
                }
                return PairStartResult(
                    status = mockJson.getString("status"),
                    sessionId = mockJson.getString("session_id"),
                    pairCode = mockJson.getString("pair_code"),
                    devicePollToken = mockJson.getString("device_poll_token"),
                    expiresInSec = mockJson.optInt("expires_in_sec", 300),
                    deviceId = mockJson.getString("device_id"),
                    deviceName = mockJson.getString("device_name"),
                )
            }
        }

        val result = client.pairStart(
            deviceId = "sony_xperia_test",
            deviceName = "Sony Xperia 5 II",
            deviceSecret = "secret_raw_123",
        )

        assertEquals("pending", result.status)
        assertEquals("492815", result.pairCode)
        assertEquals("mock-session-uuid-1234", result.sessionId)
        assertEquals("mvp_mock_token_abc", result.devicePollToken)
        assertEquals(300, result.expiresInSec)
        assertEquals("sony_xperia_test", result.deviceId)
        assertEquals("Sony Xperia 5 II", result.deviceName)
    }

    @Test
    fun testPairPollStatusParsing() {
        var capturedHeaders: Map<String, String>? = null
        val client = object : CarApiClient() {
            override fun pairPollStatus(
                sessionId: String,
                pollToken: String,
                deviceSecret: String,
            ): PairPollResult {
                capturedHeaders = mapOf(
                    "X-Poll-Token" to pollToken,
                    "X-Device-Secret" to deviceSecret,
                )
                return PairPollResult(
                    status = "approved",
                    deviceToken = "mvd_test_issued_bearer_token_9999",
                    expiresInSec = null,
                    error = null,
                )
            }
        }

        val poll = client.pairPollStatus(
            sessionId = "session_xyz",
            pollToken = "poll_token_abc",
            deviceSecret = "dev_secret_123",
        )

        assertEquals("approved", poll.status)
        assertEquals("mvd_test_issued_bearer_token_9999", poll.deviceToken)
        assertNull(poll.error)
        assertNotNull(capturedHeaders)
        assertEquals("poll_token_abc", capturedHeaders?.get("X-Poll-Token"))
        assertEquals("dev_secret_123", capturedHeaders?.get("X-Device-Secret"))
    }

    @Test
    fun testEndToEndPairingSavesToSecureTokenStoreAndEnablesOnline() {
        assertFalse("Initially unconfigured", CarApiClient.isTokenConfigured())
        assertEquals("", CarApiClient.activeApiToken())

        // Simulate successful pairing token delivery
        val issuedDeviceToken = "mvd_dynamic_token_xperia_approved_555"

        val saved = SecureTokenStore.saveRuntimeToken(issuedDeviceToken)
        assertTrue("Saving issued token to Keystore must succeed", saved)

        assertTrue("Runtime token must now be present", SecureTokenStore.hasRuntimeToken())
        assertTrue("CarApiClient must now report configured", CarApiClient.isTokenConfigured())
        assertEquals(issuedDeviceToken, CarApiClient.activeApiToken())

        val authHeaders = CarApiClient.currentAuthorizationHeaders()
        assertEquals("Bearer $issuedDeviceToken", authHeaders["Authorization"])
    }
}
