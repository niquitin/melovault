package com.bsdung.musicassistantcar.data.telegram

import java.io.File

internal sealed class NamedLoadResult {
    /** No file: no named playlist was ever saved on this device for this account. */
    object Missing : NamedLoadResult()

    /** Authenticated, decoded and checked. */
    data class Valid(val state: NamedPlaylistState) : NamedLoadResult()

    /** A file exists but cannot be trusted. It is left byte-for-byte alone. */
    data class Unreadable(val reason: PlaylistUnreadableReason) : NamedLoadResult()
}

internal interface NamedPlaylistPersistence {
    fun load(user: TelegramUserId): NamedLoadResult

    /**
     * Replaces the stored state. Throws [PlaylistUnreadableException] without touching anything when a
     * file exists that cannot be read; any other failure leaves the previous file in place.
     */
    fun save(user: TelegramUserId, state: NamedPlaylistState)
}

/**
 * Nhiều Playlist có tên, S3: the encrypted, per-account, atomic file in `noBackupFilesDir/tg_named_playlists_v1/`.
 * A different directory, Keystore alias and associated data than V1 and V2, so the stores cannot be confused or
 * swapped. Reuses [PlaylistCipher] / [PlaylistFileIo] so JVM tests run the same code with fakes.
 */
internal class NamedPlaylistStore(
    private val noBackupRoot: File,
    private val cipher: PlaylistCipher = KeystorePlaylistCipher(KeystorePlaylistCipher.NAMED_V1_ALIAS),
    private val fileIo: PlaylistFileIo = AtomicPlaylistFileIo,
) : NamedPlaylistPersistence {
    private fun accountFile(user: TelegramUserId): File {
        val parent = File(noBackupRoot, DIRECTORY)
        check(parent.isDirectory || parent.mkdirs())
        return File(parent, TelegramAccountStoragePolicy.directoryName(user) + ".bin")
    }

    private fun aad(user: TelegramUserId): ByteArray =
        ("nl1:" + TelegramAccountStoragePolicy.directoryName(user)).toByteArray()

    @Synchronized
    override fun load(user: TelegramUserId): NamedLoadResult {
        val file = try {
            accountFile(user)
        } catch (_: Exception) {
            return NamedLoadResult.Unreadable(PlaylistUnreadableReason.IO)
        }
        if (!file.exists()) return NamedLoadResult.Missing
        if (!file.isFile) return NamedLoadResult.Unreadable(PlaylistUnreadableReason.IO)
        val bytes = try {
            fileIo.read(file)
        } catch (_: Exception) {
            return NamedLoadResult.Unreadable(PlaylistUnreadableReason.IO)
        }
        if (bytes.size < MIN_BLOB_BYTES || bytes.size > MAX_BLOB_BYTES) {
            return NamedLoadResult.Unreadable(PlaylistUnreadableReason.SIZE)
        }
        val clear = try {
            cipher.decrypt(aad(user), bytes)
        } catch (_: Exception) {
            return NamedLoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT)
        }
        return when (val decoded = NamedPlaylistCodec.decode(clear, user.value)) {
            is NamedPlaylistCodec.Decoded.Ok -> NamedLoadResult.Valid(decoded.state)
            is NamedPlaylistCodec.Decoded.Invalid -> NamedLoadResult.Unreadable(decoded.reason)
        }
    }

    @Synchronized
    override fun save(user: TelegramUserId, state: NamedPlaylistState) {
        require(state.accountId == user.value) { "State belongs to another account" }
        // An existing file that cannot be read is never replaced, and no key is created for it.
        val existing = load(user)
        if (existing is NamedLoadResult.Unreadable) throw PlaylistUnreadableException(existing.reason)
        val clear = NamedPlaylistCodec.encode(state)
        val blob = cipher.encrypt(aad(user), clear)
        check(blob.size <= MAX_BLOB_BYTES) { "Named playlist state is too large to store" }
        // Prove the new blob reads back to the very same state before the old file is replaced.
        val back = NamedPlaylistCodec.decode(cipher.decrypt(aad(user), blob), user.value)
        check(back is NamedPlaylistCodec.Decoded.Ok && back.state == state) { "Named playlist blob failed verification" }
        fileIo.write(accountFile(user), blob)
    }

    companion object {
        const val DIRECTORY = "tg_named_playlists_v1"
        const val MIN_BLOB_BYTES = 12 + 16
        const val MAX_BLOB_BYTES = 8 * 1024 * 1024
    }
}
