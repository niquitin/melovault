package com.bsdung.musicassistantcar.playback

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-process fake HTTP stream server bound strictly to localhost (127.0.0.1).
 * Allows fault injection without disabling or affecting external host/Tailscale networking.
 */
class FakeHttpStreamServer(private val validAudioFile: File? = null) {

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private val running = AtomicBoolean(false)
    var port: Int = 0
        private set

    fun start() {
        val loopback = InetAddress.getByName("127.0.0.1")
        val socket = ServerSocket(0, 50, loopback)
        serverSocket = socket
        port = socket.localPort
        running.set(true)

        executor.execute {
            while (running.get() && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    executor.execute { handleClient(client) }
                } catch (_: Exception) {
                    // Socket closed on shutdown
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        executor.shutdownNow()
    }

    fun baseUrl(): String = "http://127.0.0.1:$port"

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.US_ASCII))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val path = parts[1]

                val out = client.getOutputStream()

                when {
                    path.startsWith("/stream/valid") -> serveValidAudio(out)
                    path.startsWith("/stream/401") -> serveStatus(out, 401, "Unauthorized")
                    path.startsWith("/stream/404") -> serveStatus(out, 404, "Not Found")
                    path.startsWith("/stream/503") -> serveStatus(out, 503, "Service Unavailable")
                    path.startsWith("/stream/timeout") -> {
                        // Delay or stall to trigger timeout
                        Thread.sleep(8000L)
                        serveStatus(out, 500, "Internal Server Error")
                    }
                    path.startsWith("/stream/partial") -> {
                        // Send partial header and terminate abruptly
                        val partialHeader = "HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\n\r\nRIFF".toByteArray()
                        out.write(partialHeader)
                        out.flush()
                        // Socket closes on block exit, simulating TCP abort
                    }
                    else -> serveStatus(out, 404, "Not Found")
                }
            } catch (_: Exception) {
                // Client disconnected or timed out
            }
        }
    }

    private fun serveStatus(out: OutputStream, code: Int, reason: String) {
        val body = "$code $reason\n"
        val response = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: text/plain\r\n" +
            "Content-Length: ${body.length}\r\n" +
            "Connection: close\r\n\r\n" +
            body
        out.write(response.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun serveValidAudio(out: OutputStream) {
        val file = validAudioFile
        if (file == null || !file.exists()) {
            serveStatus(out, 404, "Audio file not configured")
            return
        }

        val fileLength = file.length()
        val headers = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: audio/wav\r\n" +
            "Content-Length: $fileLength\r\n" +
            "Accept-Ranges: bytes\r\n" +
            "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.US_ASCII))

        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
            }
        }
        out.flush()
    }
}
