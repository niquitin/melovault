package com.bsdung.musicassistantcar.data

import com.bsdung.musicassistantcar.BuildConfig
import java.io.IOException
import java.net.URL

/**
 * One gate for all credential-bearing API, stream, offline download and cover URLs.
 * Only exact configured backend origins may receive a Personal bearer token.
 * Fail closed if a Tailnet HTTP route is not actively verified.
 */
internal object AuthenticatedTransportGuard {
    fun isApprovedForCredentials(
        resourceUrl: String,
        allowedOrigins: List<String>,
        hasVerifiedVpnRoute: Boolean,
    ): Boolean = runCatching {
        val target = URL(resourceUrl)
        if (target.protocol.lowercase() !in setOf("https", "http")) return false
        allowedOrigins.any { text ->
            val origin = URL(text)
            origin.protocol.equals(target.protocol, ignoreCase = true) &&
                origin.host.equals(target.host, ignoreCase = true) &&
                origin.portOrDefault() == target.portOrDefault() &&
                TrustedEndpointPolicy.isApproved(text, hasVerifiedVpnRoute)
        }
    }.getOrDefault(false)

    fun isApprovedNow(resourceUrl: String): Boolean {
        if (!BuildConfig.PERSONAL_BACKEND_ENABLED) return false
        val host = runCatching { URL(resourceUrl).host }.getOrNull() ?: return false
        val vpn = AndroidVpnRouteVerifier.hasVerifiedRoute(host)
        return isApprovedForCredentials(
            resourceUrl,
            listOf(BuildConfig.CAR_API_PRIMARY, BuildConfig.CAR_API_SECONDARY)
                .filter { it.isNotBlank() },
            vpn,
        )
    }

    @Throws(IOException::class)
    fun requireApprovedNow(resourceUrl: String) {
        if (!isApprovedNow(resourceUrl)) {
            // Avoid including URLs, tokens, queries or hostnames in security errors.
            throw IOException("E9: Protected media transport unavailable")
        }
    }

    private fun URL.portOrDefault(): Int =
        if (port != -1) port else defaultPort
}
