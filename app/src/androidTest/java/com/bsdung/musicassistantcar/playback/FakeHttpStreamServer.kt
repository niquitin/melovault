package com.bsdung.musicassistantcar.playback

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-process fake HTTP stream server bound strictly to localhost (127.0.0.1).
 * Allows fault injection without disabling or affecting external host/Tailscale networking.
 *
 * Provides endpoints:
 * - /stream/valid: serves valid WAV audio with correct Content-Type and Length.
 * - /stream/401: HTTP 401 Unauthorized (fatal error).
 * - /stream/404: HTTP 404 Not Found (fatal error).
 * - /stream/503: HTTP 503 Service Unavailable (server error).
 * - /stream/timeout: stalls response beyond standard client timeout.
 * - /stream/partial: sends truncated payload and abruptly closes socket.
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
                    // Set client socket timeout to prevent hung connections
                    client.soTimeout = 5000
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
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
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
                        // Stalls response in short increments to allow graceful shutdown
                        val deadline = System.currentTimeMillis() + 8000L
                        while (System.currentTimeMillis() < deadline && running.get()) {
                            try {
                                Thread.sleep(200L)
                            } catch (_: InterruptedException) {
                                break
                            }
                        }
                        if (running.get()) {
                            serveStatus(out, 504, "Gateway Timeout")
                        }
                    }
                    path.startsWith("/stream/partial") -> {
                        // Send partial header with partial body and terminate abruptly
                        val partialData = ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: audio/wav\r\n" +
                            "Content-Length: 1048576\r\n\r\n" +
                            "RIFF\u0000\u0000\u0000\u0000WAVEfmt ").toByteArray(Charsets.US_ASCII)
                        out.write(partialData)
                        out.flush()
                        // Socket closure upon exiting use block simulates TCP RST/FIN abort
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
            while (fis.read(buffer).also { read = it } != -1 && running.get()) {
                out.write(buffer, 0, read)
            }
        }
        out.flush()
    }
}
