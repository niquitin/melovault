package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only the API credential file belongs to this store. Call from a background thread. */
internal class TelegramApiCredentialStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "e104_tg_api_v1.enc")
    private val part = File(context.noBackupFilesDir, "e104_tg_api_v1.enc.part")
    private val alias = "melovault_e104_tg_api_v1"

    fun read(): TelegramApiCredentialPolicy.Credentials? = runCatching {
        if (!file.isFile || file.length() !in 29L..256L) return null
        val key = key(create = false) ?: return null
        val bytes = file.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val fields = cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8).split('\n')
        if (fields.size != 2) return null
        TelegramApiCredentialPolicy.validate(fields[0], fields[1]).credentials
    }.getOrNull()

    fun write(credentials: TelegramApiCredentialPolicy.Credentials) {
        val valid = TelegramApiCredentialPolicy.validate(credentials.apiId.toString(), credentials.apiHash)
            .credentials ?: error("Invalid API credential format")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        check(cipher.iv.size == 12) { "Unexpected credential IV length" }
        val payload = "${valid.apiId}\n${valid.apiHash}".toByteArray(Charsets.UTF_8)
        try {
            val encrypted = cipher.doFinal(payload)
            part.outputStream().use { output ->
                output.write(cipher.iv)
                output.write(encrypted)
                output.fd.sync()
            }
            check(part.renameTo(file)) { "Could not save API credentials" }
        } finally {
            payload.fill(0)
        }
    }

    fun clear() {
        check(!part.exists() || part.delete()) { "Could not clear pending API credentials" }
        check(!file.exists() || file.delete()) { "Could not clear API credentials" }
    }

    private fun key(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) return store.getKey(alias, null) as? SecretKey
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }
}
