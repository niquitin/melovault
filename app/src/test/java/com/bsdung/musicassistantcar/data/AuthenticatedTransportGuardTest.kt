package com.bsdung.musicassistantcar.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedTransportGuardTest {
    private val primary = "http://100.82.19.36:8790"

    @Test fun tokenNeverLeavesConfiguredBackendOrigin() {
        val origins = listOf(primary, "http://192.168.68.66:8790")
        assertTrue(AuthenticatedTransportGuard.isApprovedForCredentials(
            "$primary/v1/tracks/5/stream?quality=original", origins, true))
        assertFalse(AuthenticatedTransportGuard.isApprovedForCredentials(
            "http://192.168.68.66:8790/v1/tracks/5/stream", origins, true))
        assertFalse(AuthenticatedTransportGuard.isApprovedForCredentials(
            "http://100.82.19.36:8790/v1/tracks/5/stream", origins, false))
        assertFalse(AuthenticatedTransportGuard.isApprovedForCredentials(
            "http://100.82.19.36.evil.example:8790/v1/cover", origins, true))
        assertFalse(AuthenticatedTransportGuard.isApprovedForCredentials(
            "http://100.82.19.36:8791/v1/tracks/5/stream", origins, true))
        assertFalse(AuthenticatedTransportGuard.isApprovedForCredentials(
            "https://100.82.19.36:8790/v1/tracks/5/stream", origins, true))
        assertFalse(AuthenticatedTransportGuard.isApprovedForCredentials(
            "file:///storage/emulated/0/music.flac", origins, true))
    }

    @Test fun httpsOriginMustAlsoBeExplicitlyConfigured() {
        val origins = listOf("https://music.example.org")
        assertTrue(AuthenticatedTransportGuard.isApprovedForCredentials(
            "https://music.example.org/v1/cover", origins, false))
        assertFalse(AuthenticatedTransportGuard.isApprovedForCredentials(
            "https://example.org/v1/cover", origins, false))
        assertFalse(AuthenticatedTransportGuard.isApprovedForCredentials(
            "http://music.example.org/v1/cover", origins, true))
    }
}
