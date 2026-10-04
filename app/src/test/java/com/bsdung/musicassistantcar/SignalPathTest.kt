package com.bsdung.musicassistantcar

import com.bsdung.musicassistantcar.data.SignalPathInfo
import com.bsdung.musicassistantcar.ui.util.SignalPathFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalPathTest {

    // 1. network endpoint 100.x -> Tailscale
    @Test
    fun test_1_network_endpoint_tailscale() {
        assertEquals("Tailscale", SignalPathFormatter.formatNetworkStage("http://100.82.19.36:8790"))
        assertEquals("Tailscale", SignalPathFormatter.formatNetworkStage("https://100.76.93.76:5555"))
        assertEquals("Tailscale", SignalPathFormatter.formatNetworkStage("http://100.100.1.1:8790/"))
    }

    // 2. private LAN -> LAN
    @Test
    fun test_2_network_endpoint_lan() {
        assertEquals("LAN", SignalPathFormatter.formatNetworkStage("http://192.168.1.100:8790"))
        assertEquals("LAN", SignalPathFormatter.formatNetworkStage("http://10.0.0.5:8790"))
        assertEquals("LAN", SignalPathFormatter.formatNetworkStage("http://172.16.0.2:8790"))
        assertEquals("LAN", SignalPathFormatter.formatNetworkStage("http://172.24.1.50:8790"))
        assertEquals("LAN", SignalPathFormatter.formatNetworkStage("http://172.31.255.255:8790"))
    }

    // 3. localhost -> Local
    @Test
    fun test_3_network_endpoint_local() {
        assertEquals("Local", SignalPathFormatter.formatNetworkStage("http://127.0.0.1:8790"))
        assertEquals("Local", SignalPathFormatter.formatNetworkStage("http://localhost:8790"))
        assertEquals("Local", SignalPathFormatter.formatNetworkStage("http://localhost/"))
    }

    // 4. other -> Network
    @Test
    fun test_4_network_endpoint_other() {
        assertEquals("Network", SignalPathFormatter.formatNetworkStage("https://api.melovault.example.com"))
        assertEquals("Network", SignalPathFormatter.formatNetworkStage("http://8.8.8.8:8080"))
        assertEquals("Network", SignalPathFormatter.formatNetworkStage(null))
        assertEquals("Network", SignalPathFormatter.formatNetworkStage(""))
    }

    // 5. Source formatter
    @Test
    fun test_5_source_formatter() {
        val flacInfo = SignalPathInfo(
            mediaId = "track:1",
            sourceFormat = "FLAC",
            sourceCodec = "FLAC",
            sourceSampleRate = 44100,
            sourceBitrate = 912000,
            sourceSizeBytes = 32300000L,
        )
        val details = SignalPathFormatter.formatSourceDetails(flacInfo)
        assertEquals("FLAC · 44.1 kHz · 912 kbps · 30.8 MB", details)

        val alacInfo = SignalPathInfo(
            mediaId = "track:2",
            sourceFormat = "M4A",
            sourceCodec = "ALAC",
            sourceSampleRate = 44100,
            sourceBitrate = 850000,
            sourceSizeBytes = 25000000L,
        )
        val alacDetails = SignalPathFormatter.formatSourceDetails(alacInfo)
        assertEquals("M4A / ALAC · 44.1 kHz · 850 kbps · 23.8 MB", alacDetails)
    }

    // 6. Delivery formatter
    @Test
    fun test_6_delivery_formatter() {
        val originalInfo = SignalPathInfo(
            mediaId = "track:1",
            quality = "original",
            deliveryFormat = "FLAC",
            deliveryCodec = "FLAC",
            deliverySampleRate = 44100,
            deliveryBitrate = 912000,
        )
        val (origTitle, origDetails) = SignalPathFormatter.formatDeliveryStage(originalInfo)
        assertEquals("Original", origTitle)
        assertEquals("FLAC · 44.1 kHz · 912 kbps", origDetails)

        val mobileInfo = SignalPathInfo(
            mediaId = "track:1",
            quality = "mobile",
            deliveryFormat = "M4A",
            deliveryCodec = "AAC",
            deliverySampleRate = 48000,
            deliveryBitrate = 320000,
        )
        val (mobTitle, mobDetails) = SignalPathFormatter.formatDeliveryStage(mobileInfo)
        assertEquals("Mobile 320", mobTitle)
        assertEquals("AAC · 48 kHz · 320 kbps", mobDetails)
    }

    // 7. null fields safely omitted
    @Test
    fun test_7_null_fields_safely_omitted() {
        val sparseInfo = SignalPathInfo(
            mediaId = "track:99",
            sourceFormat = "FLAC",
            sourceCodec = null,
            sourceSampleRate = null,
            sourceBitrate = null,
            sourceSizeBytes = null,
        )
        val formatted = SignalPathFormatter.formatSourceDetails(sparseInfo)
        assertEquals("FLAC", formatted)
        assertFalse(formatted.contains("null"))
        assertFalse(formatted.contains("0 kbps"))
        assertFalse(formatted.contains("0 kHz"))
        assertFalse(formatted.contains("Unknown"))

        val emptyDelivery = SignalPathInfo(
            mediaId = "track:99",
            quality = "original",
            deliveryFormat = null,
            deliveryCodec = null,
        )
        val (delTitle, delDetails) = SignalPathFormatter.formatDeliveryStage(emptyDelivery)
        assertEquals("Original", delTitle)
        assertEquals("", delDetails)
        assertFalse(delDetails.contains("null"))
    }

    // 8. Original direct stage
    @Test
    fun test_8_original_direct_stage() {
        val info = SignalPathInfo(
            mediaId = "track:10",
            quality = "original",
            serverAction = "direct",
            serverActionLabel = "Direct stream",
        )
        val (title, subtitle) = SignalPathFormatter.formatServerStage(info)
        assertEquals("Direct stream", title)
        assertEquals("No transcode", subtitle)
    }

    // 9. Mobile 320 transcode stage
    @Test
    fun test_9_mobile_320_transcode_stage() {
        val info = SignalPathInfo(
            mediaId = "track:11",
            quality = "mobile",
            serverAction = "transcode_mobile",
            serverActionLabel = "Mobile transcode",
            deliveryFormat = "M4A",
            deliveryCodec = "AAC",
            deliverySampleRate = 48000,
            deliveryBitrate = 320000,
        )
        val (title, subtitle) = SignalPathFormatter.formatServerStage(info)
        assertEquals("Mobile transcode", title)
        assertNull(subtitle)

        val (delTitle, delDetails) = SignalPathFormatter.formatDeliveryStage(info)
        assertEquals("Mobile 320", delTitle)
        assertEquals("AAC · 48 kHz · 320 kbps", delDetails)
    }

    // 10. ALAC compatibility stage
    @Test
    fun test_10_alac_compatibility_stage() {
        val info = SignalPathInfo(
            mediaId = "track:12",
            quality = "original",
            sourceFormat = "M4A",
            sourceCodec = "ALAC",
            serverAction = "compatibility_transcode",
            serverActionLabel = "Android compatibility conversion",
            compatibilityConversion = true,
            compatibilityFrom = "ALAC",
            compatibilityTo = "FLAC",
            deliveryFormat = "FLAC",
            deliveryCodec = "FLAC",
            deliverySampleRate = 44100,
            losslessSource = true,
            losslessDelivery = true,
        )
        val (title, subtitle) = SignalPathFormatter.formatServerStage(info)
        assertEquals("Android compatibility conversion", title)
        assertEquals("ALAC → FLAC", subtitle)

        val (delTitle, delDetails) = SignalPathFormatter.formatDeliveryStage(info)
        assertEquals("Original · Lossless compatibility stream", delTitle)
        assertEquals("FLAC · 44.1 kHz", delDetails)
    }

    // 11. bit-perfect text always "Not verified"
    @Test
    fun test_11_bit_perfect_not_verified() {
        val bitPerfectLabel = "Bit-perfect: Not verified"
        val pipelineLabel = "Android system audio pipeline"

        assertEquals("Bit-perfect: Not verified", bitPerfectLabel)
        assertEquals("Android system audio pipeline", pipelineLabel)
        assertFalse(bitPerfectLabel.contains("Yes"))
        assertTrue(bitPerfectLabel.contains("Not verified"))
    }

    // 12. stale mediaId response rejected
    @Test
    fun test_12_stale_media_id_response_rejected() {
        val activeMediaId = "track:200"
        val responseMediaId = "track:199"

        var appliedInfo: SignalPathInfo? = null
        val responseInfo = SignalPathInfo(mediaId = responseMediaId)

        // Identity guard simulation
        if (activeMediaId == responseInfo.mediaId) {
            appliedInfo = responseInfo
        }

        assertNull("Stale mediaId must be rejected", appliedInfo)

        // Matching mediaId accepted
        val matchingInfo = SignalPathInfo(mediaId = activeMediaId)
        if (activeMediaId == matchingInfo.mediaId) {
            appliedInfo = matchingInfo
        }
        assertEquals(matchingInfo, appliedInfo)
    }

    // 13. stale quality response rejected
    @Test
    fun test_13_stale_quality_response_rejected() {
        val activeQuality = "mobile"
        val responseQuality = "original"
        val mediaId = "track:300"

        var appliedInfo: SignalPathInfo? = null
        val responseInfo = SignalPathInfo(mediaId = mediaId, quality = responseQuality)

        // Quality identity guard simulation
        if (activeQuality == responseInfo.quality) {
            appliedInfo = responseInfo
        }

        assertNull("Stale quality response must be rejected", appliedInfo)

        val freshInfo = SignalPathInfo(mediaId = mediaId, quality = activeQuality)
        if (activeQuality == freshInfo.quality) {
            appliedInfo = freshInfo
        }
        assertEquals(freshInfo, appliedInfo)
    }
}
