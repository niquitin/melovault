package com.bsdung.musicassistantcar.ui.util

import com.bsdung.musicassistantcar.data.SignalPathInfo
import java.net.URI
import java.util.Locale

object SignalPathFormatter {

    /**
     * Determines network stage label from an active backend URL/endpoint.
     * Rules:
     * - 100.x Tailscale endpoint: "Tailscale"
     * - 192.168.x / 10.x / 172.16-31.x: "LAN"
     * - 127.0.0.1 / localhost: "Local"
     * - Otherwise: "Network"
     *
     * Never exposes IP addresses or credentials.
     */
    fun formatNetworkStage(endpointUrl: String?): String {
        if (endpointUrl.isNullOrBlank()) return "Network"
        val host = try {
            val uri = URI(endpointUrl)
            uri.host ?: extractHostFallback(endpointUrl)
        } catch (_: Exception) {
            extractHostFallback(endpointUrl)
        }.trim()

        if (host.equals("localhost", ignoreCase = true) || host == "127.0.0.1") {
            return "Local"
        }

        if (host.startsWith("100.")) {
            return "Tailscale"
        }

        if (host.startsWith("192.168.") || host.startsWith("10.")) {
            return "LAN"
        }

        val rfc1918_172 = Regex("""^172\.(1[6-9]|2[0-9]|3[0-1])(\.|$)""")
        if (rfc1918_172.containsMatchIn(host)) {
            return "LAN"
        }

        return "Network"
    }

    private fun extractHostFallback(url: String): String {
        return url.removePrefix("http://")
            .removePrefix("https://")
            .substringBefore(':')
            .substringBefore('/')
    }

    /**
     * Formats Source technical detail line:
     * Example: "FLAC · 44.1 kHz · 912 kbps · 30.8 MB"
     * Example: "M4A / ALAC · 44.1 kHz · 850 kbps"
     * Omits unavailable attributes. Never renders null, 0, or Unknown.
     */
    fun formatSourceDetails(info: SignalPathInfo): String {
        val parts = mutableListOf<String>()

        val fmt = info.sourceFormat?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() && it != "UNKNOWN" }
        val cod = info.sourceCodec?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() && it != "UNKNOWN" }

        val formatCodecStr = when {
            fmt != null && cod != null && fmt != cod -> "$fmt / $cod"
            cod != null -> cod
            fmt != null -> fmt
            else -> null
        }
        formatCodecStr?.let { parts.add(it) }

        AudioInfoFormatter.formatSampleRate(info.sourceSampleRate)?.let { parts.add(it) }
        AudioInfoFormatter.formatBitrate(info.sourceBitrate)?.let { parts.add(it) }
        AudioInfoFormatter.formatFileSize(info.sourceSizeBytes)?.let { parts.add(it) }

        return parts.joinToString(" · ")
    }

    /**
     * Formats Server Action lines:
     * Returns Pair(actionTitle, actionSubtitle?)
     */
    fun formatServerStage(info: SignalPathInfo): Pair<String, String?> {
        val action = info.serverAction?.trim()?.lowercase(Locale.US)
        return when {
            action == "transcode_mobile" || info.quality == "mobile" -> {
                val label = info.serverActionLabel?.takeIf { it.isNotBlank() } ?: "Mobile transcode"
                Pair(label, null)
            }
            info.compatibilityConversion || action == "compatibility_transcode" -> {
                val label = info.serverActionLabel?.takeIf { it.isNotBlank() } ?: "Compatibility conversion"
                val from = info.compatibilityFrom ?: "ALAC"
                val to = info.compatibilityTo ?: "FLAC"
                Pair(label, "$from → $to")
            }
            else -> {
                val label = info.serverActionLabel?.takeIf { it.isNotBlank() } ?: "Direct stream"
                Pair(label, "No transcode")
            }
        }
    }

    /**
     * Formats Delivery stage lines:
     * Returns Pair(deliveryTitle, deliveryDetails)
     * Mobile 320: "Mobile 320", "AAC · 48 kHz · 320 kbps"
     * Original ALAC: "Original · Lossless compatibility stream", "FLAC · 44.1 kHz"
     * Original FLAC: "Original", "FLAC · 44.1 kHz · 912 kbps"
     */
    fun formatDeliveryStage(info: SignalPathInfo): Pair<String, String> {
        val isMobile = info.quality == "mobile" || info.serverAction == "transcode_mobile"
        val title = when {
            isMobile -> "Mobile 320"
            info.compatibilityConversion -> "Original · Lossless compatibility stream"
            else -> "Original"
        }

        val parts = mutableListOf<String>()
        val cod = info.deliveryCodec?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() && it != "UNKNOWN" }
        val fmt = info.deliveryFormat?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() && it != "UNKNOWN" }
        val codecStr = cod ?: fmt
        codecStr?.let { parts.add(it) }

        AudioInfoFormatter.formatSampleRate(info.deliverySampleRate)?.let { parts.add(it) }
        AudioInfoFormatter.formatBitrate(info.deliveryBitrate)?.let { parts.add(it) }

        return Pair(title, parts.joinToString(" · "))
    }
}
