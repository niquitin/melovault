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

    sealed class ToggleResult {
        /** [ids] is the whole playlist after the add, oldest first, nothing dropped. */
        data class Added(val ids: List<Long>) : ToggleResult()
        data class Removed(val ids: List<Long>) : ToggleResult()
        /** Adding was refused: the playlist already holds [size] >= [MAX_IDS] songs. Nothing changes. */
        data class Full(val size: Int) : ToggleResult()
    }

    /**
     * Never evicts: a full playlist refuses a new song (removing is always allowed) and a list that
     * is somehow longer than [MAX_IDS] is kept whole, never cut.
     */
    fun toggle(ids: List<Long>, targetId: Long): ToggleResult {
        require(targetId > 0)
        val current = ids.filter { it > 0 }.distinct()
        return when {
            targetId in current -> ToggleResult.Removed(current.filterNot { it == targetId })
            current.size >= MAX_IDS -> ToggleResult.Full(current.size)
            else -> ToggleResult.Added(current + targetId)
        }
    }
}

/** Why a playlist file could not be turned into a list. Never carries file content. */
internal enum class PlaylistUnreadableReason { IO, SIZE, DECRYPT, FORMAT }

internal sealed class PlaylistLoadResult {
    /** No file yet: a playlist that was never saved. The only state in which an empty list is "true". */
    object Missing : PlaylistLoadResult()

    /** Authenticated and parsed. An empty [ids] is a real, saved, empty playlist (28-byte blob). */
    data class Ok(val ids: List<Long>) : PlaylistLoadResult()

    /** A file exists but cannot be trusted. It must be left byte-for-byte alone. */
    data class Unreadable(val reason: PlaylistUnreadableReason) : PlaylistLoadResult()
}

internal class PlaylistUnreadableException(val reason: PlaylistUnreadableReason) :
    RuntimeException("Saved playlist is unreadable: $reason")

internal interface PlaylistPersistence {
    fun load(user: TelegramUserId): PlaylistLoadResult

    /**
     * Replaces the stored list. Throws [PlaylistUnreadableException] without touching the file when
     * the existing file cannot be read; any other failure leaves the old file in place.
     */
    fun save(user: TelegramUserId, ids: List<Long>)
}

/** AES-GCM blob layout: 12-byte IV, then ciphertext, then the 16-byte tag. */
internal interface PlaylistCipher {
    fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray
    fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray
}

internal interface PlaylistFileIo {
    fun read(file: File): ByteArray
    fun write(file: File, bytes: ByteArray)
}

internal object AtomicPlaylistFileIo : PlaylistFileIo {
    override fun read(file: File): ByteArray = AtomicFile(file).openRead().use { it.readBytes() }

    override fun write(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }
}

/** Same alias, algorithm and blob layout as every earlier build, so existing files stay readable. */
internal class KeystorePlaylistCipher : PlaylistCipher {
    private val keyAlias = "melovault_e91_saved_playlist_aes_v1"

    /**
     * Decrypting never creates a key: a Keystore that briefly reports no alias must not be allowed
     * to mint a new key that would make the saved file permanently undecryptable.
     */
    private fun key(createIfMissing: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(keyAlias)) {
            check(createIfMissing) { "Playlist key is not available" }
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

    override fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(createIfMissing = true))
        cipher.updateAAD(aad)
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(createIfMissing = false), GCMParameterSpec(128, blob, 0, IV_BYTES))
        cipher.updateAAD(aad)
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    companion object {
        const val IV_BYTES = 12
    }
}

internal class TelegramSavedPlaylistStore(
    private val noBackupRoot: File,
    private val cipher: PlaylistCipher = KeystorePlaylistCipher(),
    private val fileIo: PlaylistFileIo = AtomicPlaylistFileIo,
) : PlaylistPersistence {
    private fun accountFile(user: TelegramUserId): File {
        val parent = File(noBackupRoot, "tg_playlists_v1")
        check(parent.isDirectory || parent.mkdirs())
        return File(parent, TelegramAccountStoragePolicy.directoryName(user) + ".bin")
    }

    private fun aad(user: TelegramUserId): ByteArray =
        TelegramAccountStoragePolicy.directoryName(user).toByteArray()

    @Synchronized
    override fun load(user: TelegramUserId): PlaylistLoadResult {
        val file = try {
            accountFile(user)
        } catch (_: Exception) {
            return PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.IO)
        }
        if (!file.exists()) return PlaylistLoadResult.Missing
        if (!file.isFile) return PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.IO)
        val bytes = try {
            fileIo.read(file)
        } catch (_: Exception) {
            return PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.IO)
        }
        // 12-byte IV + 16-byte tag is the smallest valid blob: an empty playlist is exactly 28 bytes.
        if (bytes.size < MIN_BLOB_BYTES || bytes.size > MAX_BLOB_BYTES) {
            return PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.SIZE)
        }
        val clear = try {
            cipher.decrypt(aad(user), bytes).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            return PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT)
        }
        return parse(clear)
    }

    @Synchronized
    override fun save(user: TelegramUserId, ids: List<Long>) {
        require(ids.all { it > 0L }) { "Playlist ids must be positive" }
        require(ids.size <= MAX_STORED_IDS) { "Playlist is too large to store" }
        val existing = load(user)
        if (existing is PlaylistLoadResult.Unreadable) throw PlaylistUnreadableException(existing.reason)
        val clear = ids.distinct().joinToString("\n").toByteArray(Charsets.UTF_8)
        val blob = cipher.encrypt(aad(user), clear)
        // Prove the new blob reads back before the old file is replaced.
        check(cipher.decrypt(aad(user), blob).contentEquals(clear)) { "Playlist blob failed verification" }
        fileIo.write(accountFile(user), blob)
    }

    private fun parse(clear: String): PlaylistLoadResult {
        val ids = ArrayList<Long>()
        for (line in clear.split('\n')) {
            if (line.isBlank()) continue
            val id = line.trim().toLongOrNull()?.takeIf { it > 0L }
                ?: return PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.FORMAT)
            ids.add(id)
        }
        return PlaylistLoadResult.Ok(ids.distinct())
    }

    companion object {
        const val MIN_BLOB_BYTES = 12 + 16
        const val MAX_BLOB_BYTES = 64 * 1024

        /** A safety bound against a runaway list, far above [TelegramPlaylistPolicy.MAX_IDS]; never a silent cut. */
        const val MAX_STORED_IDS = 1000
    }
}
