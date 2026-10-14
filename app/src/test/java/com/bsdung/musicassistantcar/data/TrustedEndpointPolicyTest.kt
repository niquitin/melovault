package com.bsdung.musicassistantcar.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedEndpointPolicyTest {
    @Test fun rejectsOwnerLanAndPlaintextNonVpnEndpoints() {
        assertFalse(TrustedEndpointPolicy.isApproved("http://192.168.68.66:8790", false))
        assertFalse(TrustedEndpointPolicy.isApproved("http://192.168.68.66:8790", true))
        assertFalse(TrustedEndpointPolicy.isApproved("http://10.0.0.1:8790", true))
        assertFalse(TrustedEndpointPolicy.isApproved("http://100.82.19.36:8790", false))
        assertFalse(TrustedEndpointPolicy.isApproved("http://100.128.0.1:8790", true))
        assertFalse(TrustedEndpointPolicy.isApproved("http://100.63.255.255:8790", true))
        assertFalse(TrustedEndpointPolicy.isApproved("ftp://100.82.19.36", true))
    }

    @Test fun permitsTlsAndVpnRoutedTailnetOnly() {
        assertTrue(TrustedEndpointPolicy.isApproved("https://music.example.org/v1", false))
        assertTrue(TrustedEndpointPolicy.isApproved("http://100.82.19.36:8790", true))
        assertTrue(TrustedEndpointPolicy.isApproved("http://100.127.255.254:8790", true))
        assertTrue(TrustedEndpointPolicy.isTailnetIpv4("100.64.0.1"))
        assertFalse(TrustedEndpointPolicy.isTailnetIpv4("100.064.0.1.example.org"))
        assertFalse(TrustedEndpointPolicy.isTailnetIpv4("100.82.19.999"))
    }
}
