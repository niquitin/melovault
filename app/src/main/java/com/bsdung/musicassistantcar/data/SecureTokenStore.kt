package com.bsdung.musicassistantcar.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.bsdung.musicassistantcar.BuildConfig
import java.io.File
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Centralized encrypted runtime storage for the MeloVault Car API token.
 *
 * Security Guarantees:
 * 1. Encrypts the runtime token using AES-256-GCM (`AES/GCM/NoPadding`, 128-bit auth tag)
 *    backed by the hardware/system `AndroidKeyStore`.
 * 2. Stores only the encrypted ciphertext inside `Context.noBackupFilesDir`
 *    (`melovault_secure_token.enc`), NEVER as plaintext in `SharedPreferences`.
 * 3. Excluded from Android Auto Backup and Device-to-Device transfer via both
 *    `noBackupFilesDir` OS enforcement and `@xml/backup_rules` / `@xml/data_extraction_rules`.
 * 4. Never logs or exposes token values in exceptions, diagnostics, or UI dumps.
 */
object SecureTokenStore {
    private const val TAG = "SecureTokenStore"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "melovault_car_api_token_aes256_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val FORMAT_VERSION_PREFIX = "v1"

    const val TOKEN_FILE_NAME = "melovault_secure_token.enc"
    const val CLEARED_FLAG_FILE_NAME = "melovault_token_cleared.flag"

    interface CipherEngine {
        fun encrypt(plaintext: ByteArray): EncryptedPayload
        fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray
    }

    data class EncryptedPayload(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    class AndroidKeystoreAesGcmCipherEngine : CipherEngine {
        private fun getOrCreateSecretKey(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            if (existing != null) {
                return existing
            }
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }

        override fun encrypt(plaintext: ByteArray): EncryptedPayload {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv ?: throw IllegalStateException("Missing GCM IV")
            val ciphertext = cipher.doFinal(plaintext)
            return EncryptedPayload(iv = iv, ciphertext = ciphertext)
        }

        override fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val secretKey = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                ?: throw IllegalStateException("Missing AndroidKeyStore key for alias $KEY_ALIAS")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            return cipher.doFinal(ciphertext)
        }
    }

    @Volatile
    private var storageDir: File? = null

    @Volatile
    private var cipherEngine: CipherEngine = AndroidKeystoreAesGcmCipherEngine()

    @Volatile
    private var buildConfigTokenSupplier: () -> String = { BuildConfig.CAR_API_TOKEN }

    @Volatile
    private var cachedRuntimeToken: String? = null

    @Volatile
    private var isCacheLoaded: Boolean = false

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Synchronized
    fun init(context: Context?) {
        val appCtx = context?.applicationContext ?: context ?: return
        val dir = appCtx.noBackupFilesDir
        if (dir != null && storageDir?.absolutePath != dir.absolutePath) {
            storageDir = dir
            isCacheLoaded = false
        }
    }

    @Synchronized
    internal fun initForTesting(
        dir: File?,
        customCipherEngine: CipherEngine,
        fallbackTokenSupplier: () -> String = { "" },
    ) {
        storageDir = dir
        cipherEngine = customCipherEngine
        buildConfigTokenSupplier = fallbackTokenSupplier
        cachedRuntimeToken = null
        isCacheLoaded = false
        listeners.clear()
    }

    @Synchronized
    internal fun resetAfterTesting() {
        storageDir = null
        cipherEngine = AndroidKeystoreAesGcmCipherEngine()
        buildConfigTokenSupplier = { BuildConfig.CAR_API_TOKEN }
        cachedRuntimeToken = null
        isCacheLoaded = false
        listeners.clear()
    }

    fun addTokenChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeTokenChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun resolveStorageDir(): File? {
        storageDir?.let { return it }
        return synchronized(this) {
            storageDir ?: runCatching {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val app = activityThreadClass.getMethod("currentApplication").invoke(null) as? Context
                app?.noBackupFilesDir?.also { storageDir = it }
            }.getOrNull()
        }
    }

    private fun tokenFile(): File? {
        val dir = resolveStorageDir() ?: return null
        if (!dir.exists()) {
            runCatching { dir.mkdirs() }
        }
        return File(dir, TOKEN_FILE_NAME)
    }

    private fun clearedFlagFile(): File? {
        val dir = resolveStorageDir() ?: return null
        if (!dir.exists()) {
            runCatching { dir.mkdirs() }
        }
        return File(dir, CLEARED_FLAG_FILE_NAME)
    }

    /**
     * Returns true if an encrypted runtime token is currently stored on device.
     */
    fun hasRuntimeToken(): Boolean {
        return getRuntimeToken().isNotBlank()
    }

    /**
     * Reads and decrypts the runtime token from `noBackupFilesDir`, caching it in memory.
     * Returns empty string if no runtime token is stored or if decryption fails.
     */
    fun getRuntimeToken(): String {
        if (isCacheLoaded) {
            return cachedRuntimeToken.orEmpty()
        }
        synchronized(this) {
            if (isCacheLoaded) {
                return cachedRuntimeToken.orEmpty()
            }
            val file = tokenFile()
            if (file == null || !file.exists() || file.length() <= 0L) {
                cachedRuntimeToken = ""
                isCacheLoaded = true
                return ""
            }
            val decrypted = runCatching {
                val raw = file.readText(Charsets.UTF_8).trim()
                val parts = raw.split(":")
                if (parts.size != 3 || parts[0] != FORMAT_VERSION_PREFIX) {
                    throw IllegalArgumentException("Invalid encrypted token envelope format")
                }
                val iv = Base64.getDecoder().decode(parts[1])
                val ciphertext = Base64.getDecoder().decode(parts[2])
                val plainBytes = cipherEngine.decrypt(iv, ciphertext)
                String(plainBytes, Charsets.UTF_8).trim()
            }.onFailure {
                runCatching { Log.w(TAG, "Failed to decrypt stored runtime token (${it.javaClass.simpleName})") }
            }.getOrDefault("")

            cachedRuntimeToken = decrypted
            isCacheLoaded = true
            return decrypted
        }
    }

    /**
     * Resolves the active API token:
     * 1. Runtime AES-GCM encrypted token in `noBackupFilesDir` (if present).
     * 2. Empty string if user explicitly cleared the token on this device.
     * 3. `BuildConfig.CAR_API_TOKEN` fallback (which is `""` in Release/RC builds,
     *    and populated only in local Debug development builds).
     */
    fun resolveActiveToken(): String {
        val runtime = getRuntimeToken()
        if (runtime.isNotBlank()) {
            return runtime
        }
        val clearedFile = clearedFlagFile()
        if (clearedFile != null && clearedFile.exists()) {
            return ""
        }
        return buildConfigTokenSupplier().trim()
    }

    /**
     * Encrypts and persists a new runtime token using AES-GCM.
     * Never logs the token value.
     */
    fun saveRuntimeToken(newToken: String): Boolean {
        val trimmed = newToken.trim()
        if (trimmed.isBlank()) {
            return false
        }
        val saved = synchronized(this) {
            val file = tokenFile() ?: return false
            val tempFile = File(file.parentFile, "$TOKEN_FILE_NAME.tmp")
            runCatching {
                val payload = cipherEngine.encrypt(trimmed.toByteArray(Charsets.UTF_8))
                val ivB64 = Base64.getEncoder().encodeToString(payload.iv)
                val cipherB64 = Base64.getEncoder().encodeToString(payload.ciphertext)
                val envelope = "$FORMAT_VERSION_PREFIX:$ivB64:$cipherB64"
                tempFile.writeText(envelope, Charsets.UTF_8)
                if (file.exists()) {
                    file.delete()
                }
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
                clearedFlagFile()?.let { if (it.exists()) it.delete() }
                cachedRuntimeToken = trimmed
                isCacheLoaded = true
                true
            }.onFailure {
                runCatching { tempFile.delete() }
                runCatching { Log.e(TAG, "Failed to save encrypted runtime token (${it.javaClass.simpleName})") }
            }.getOrDefault(false)
        }
        if (saved) {
            notifyListeners()
        }
        return saved
    }

    /**
     * Securely overwrites and deletes the stored runtime token, transitioning to unconfigured state
     * without touching any audio cache or offline downloaded tracks.
     */
    fun clearRuntimeToken(): Boolean {
        synchronized(this) {
            val file = tokenFile()
            runCatching {
                if (file != null && file.exists()) {
                    file.writeBytes(ByteArray(64))
                    file.delete()
                }
                clearedFlagFile()?.writeText("cleared", Charsets.UTF_8)
            }
            cachedRuntimeToken = ""
            isCacheLoaded = true
        }
        notifyListeners()
        return true
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            runCatching { listener() }
        }
    }
}
