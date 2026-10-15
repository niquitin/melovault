package com.bsdung.musicassistantcar.data.telegram

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Only message IDs, never content, filenames, session tokens, or file paths.
 * Stored in noBackup app-private space using an Android Keystore AES-GCM key.
 * Authenticated getMe identity is mandatory and used as associated data.
 */
internal object TelegramPlaylistPolicy {
    const val MAX_IDS = 300
    fun toggle(ids: List<Long>, targetId: Long): List<Long> {
        require(targetId > 0)
        val deduped = ids.filter { it > 0 }.distinct().take(MAX_IDS)
        return if (targetId in deduped) deduped.filterNot { it == targetId }
        else (deduped + targetId).takeLast(MAX_IDS)
    }
}

internal class TelegramSavedPlaylistStore(private val noBackupRoot: File) {
    private val keyAlias = "melovault_e91_saved_playlist_aes_v1"
    private fun accountFile(user: TelegramUserId): File {
        val parent = File(noBackupRoot, "tg_playlists_v1")
        check(parent.isDirectory || parent.mkdirs())
        return File(parent, TelegramAccountStoragePolicy.directoryName(user) + ".bin")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(keyAlias)) {
            val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generator.generateKey()
        }
        return checkNotNull(store.getKey(keyAlias, null) as? SecretKey)
    }

    fun load(user: TelegramUserId): List<Long> {
        val file = accountFile(user)
        if (!file.isFile) return emptyList()
        val bytes = AtomicFile(file).openRead().use { it.readBytes() }
        require(bytes.size in 29..8192)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        cipher.updateAAD(TelegramAccountStoragePolicy.directoryName(user).toByteArray())
        val clear = cipher.doFinal(bytes, 12, bytes.size - 12)
        return clear.toString(Charsets.UTF_8)
            .lineSequence()
            .mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0 } }
            .distinct()
            .take(TelegramPlaylistPolicy.MAX_IDS)
            .toList()
    }

    fun save(user: TelegramUserId, ids: List<Long>) {
        val normalized = ids.filter { it > 0L }.distinct().take(TelegramPlaylistPolicy.MAX_IDS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(TelegramAccountStoragePolicy.directoryName(user).toByteArray())
        val blob = cipher.iv + cipher.doFinal(
            normalized.joinToString("\n").toByteArray(Charsets.UTF_8)
        )
        val file = AtomicFile(accountFile(user))
        val stream = file.startWrite()
        try {
            stream.write(blob)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}
