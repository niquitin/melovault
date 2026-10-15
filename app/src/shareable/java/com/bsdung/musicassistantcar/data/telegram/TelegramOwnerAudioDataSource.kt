package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * Media3 supports existing local/file/content/HTTP sources unchanged.
 * The private TDLib scheme reads the downloaded prefix as it grows.
 */
internal class TelegramOwnerAudioDataSource(
    private val delegate: DataSource
) : DataSource {
    class Factory(context: Context) : DataSource.Factory {
        private val fallback = DefaultDataSource.Factory(context)
        override fun createDataSource(): DataSource =
            TelegramOwnerAudioDataSource(fallback.createDataSource())
    }

    private var uri: Uri? = null
    private var reader: TelegramProgressiveCache.Reader? = null
    private var remaining = C.LENGTH_UNSET.toLong()
    private var streaming = false

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val fileId = TelegramProgressiveCache.fileId(dataSpec.uri)
        streaming = fileId != null
        if (fileId == null) return delegate.open(dataSpec)
        val opened = TelegramProgressiveCache.Reader(fileId, dataSpec.position)
        reader = opened
        val fromFile = opened.totalRemaining()
        remaining = when {
            dataSpec.length >= 0 && fromFile >= 0 ->
                minOf(dataSpec.length, fromFile)
            dataSpec.length >= 0 -> dataSpec.length
            else -> fromFile
        }
        return remaining
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!streaming) return delegate.read(buffer, offset, length)
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val allowed = if (remaining > 0) minOf(length.toLong(), remaining).toInt()
            else length
        val n = (reader ?: return C.RESULT_END_OF_INPUT).read(buffer, offset, allowed)
        if (n > 0 && remaining > 0) remaining -= n
        return n
    }

    override fun getUri(): Uri? = if (streaming) uri else delegate.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        if (streaming) emptyMap() else delegate.responseHeaders

    @Throws(IOException::class)
    override fun close() {
        val previous = reader
        reader = null
        uri = null
        remaining = C.LENGTH_UNSET.toLong()
        if (streaming) {
            streaming = false
            previous?.close()
        } else delegate.close()
    }
}
