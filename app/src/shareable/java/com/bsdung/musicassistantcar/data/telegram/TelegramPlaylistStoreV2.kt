package com.bsdung.musicassistantcar.data.telegram

import java.io.File

internal sealed class V2LoadResult {
    /** No V2 file: the playlist has not been moved to V2 on this device yet. */
    object Missing : V2LoadResult()

    /** Authenticated, decoded and checked. */
    data class Valid(val state: PlaylistState) : V2LoadResult()

    /** A V2 file exists but cannot be trusted. It is left byte-for-byte alone. */
    data class Unreadable(val reason: PlaylistUnreadableReason) : V2LoadResult()
}

internal interface PlaylistStoreV2 {
    fun load(user: TelegramUserId): V2LoadResult

    /**
     * Replaces the stored state. Throws [PlaylistUnreadableException] without touching anything when a
     * V2 file exists that cannot be read; any other failure leaves the previous file in place.
     */
    fun save(user: TelegramUserId, state: PlaylistState)
}

/**
 * The encrypted, per-account, atomic V2 file in `noBackupFilesDir/tg_playlists_v2/`. A different
 * directory, Keystore alias and associated data than V1, so the two stores cannot be confused or
 * swapped. Reuses [PlaylistCipher] / [PlaylistFileIo] so JVM tests run the same code with fakes.
 */
internal class TelegramPlaylistStoreV2(
    private val noBackupRoot: File,
    private val cipher: PlaylistCipher = KeystorePlaylistCipher(KeystorePlaylistCipher.V2_ALIAS),
    private val fileIo: PlaylistFileIo = AtomicPlaylistFileIo,
) : PlaylistStoreV2 {
    private fun accountFile(user: TelegramUserId): File {
        val parent = File(noBackupRoot, "tg_playlists_v2")
        check(parent.isDirectory || parent.mkdirs())
        return File(parent, TelegramAccountStoragePolicy.directoryName(user) + ".bin")
    }

    private fun aad(user: TelegramUserId): ByteArray =
        ("v2:" + TelegramAccountStoragePolicy.directoryName(user)).toByteArray()

    @Synchronized
    override fun load(user: TelegramUserId): V2LoadResult {
        val file = try {
            accountFile(user)
        } catch (_: Exception) {
            return V2LoadResult.Unreadable(PlaylistUnreadableReason.IO)
        }
        if (!file.exists()) return V2LoadResult.Missing
        if (!file.isFile) return V2LoadResult.Unreadable(PlaylistUnreadableReason.IO)
        val bytes = try {
            fileIo.read(file)
        } catch (_: Exception) {
            return V2LoadResult.Unreadable(PlaylistUnreadableReason.IO)
        }
        if (bytes.size < MIN_BLOB_BYTES || bytes.size > MAX_BLOB_BYTES) {
            return V2LoadResult.Unreadable(PlaylistUnreadableReason.SIZE)
        }
        val clear = try {
            cipher.decrypt(aad(user), bytes)
        } catch (_: Exception) {
            return V2LoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT)
        }
        return when (val decoded = TelegramPlaylistCodec.decode(clear, user.value)) {
            is TelegramPlaylistCodec.Decoded.Ok -> V2LoadResult.Valid(decoded.state)
            is TelegramPlaylistCodec.Decoded.Invalid -> V2LoadResult.Unreadable(decoded.reason)
        }
    }

    @Synchronized
    override fun save(user: TelegramUserId, state: PlaylistState) {
        require(state.accountId == user.value) { "State belongs to another account" }
        // An existing file that cannot be read is never replaced, and no key is created for it.
        val existing = load(user)
        if (existing is V2LoadResult.Unreadable) throw PlaylistUnreadableException(existing.reason)
        val clear = TelegramPlaylistCodec.encode(state)
        val blob = cipher.encrypt(aad(user), clear)
        check(blob.size <= MAX_BLOB_BYTES) { "Playlist state is too large to store" }
        // Prove the new blob reads back to the very same state before the old file is replaced.
        val back = TelegramPlaylistCodec.decode(cipher.decrypt(aad(user), blob), user.value)
        check(back is TelegramPlaylistCodec.Decoded.Ok && back.state == state) { "Playlist blob failed verification" }
        fileIo.write(accountFile(user), blob)
    }

    companion object {
        const val MIN_BLOB_BYTES = 12 + 16
        const val MAX_BLOB_BYTES = 8 * 1024 * 1024
    }
}
