package com.bsdung.musicassistantcar.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetAddress
import java.net.URL

/**
 * Release guard for the private Car API. The HTTP port is safe only over a
 * verified OS VPN route to a tailnet IPv4 address. A matching 100.x address
 * alone is NOT proof of an encrypted route.
 *
 * Pure selection policy can be tested without Android system services.
 */
internal object TrustedEndpointPolicy {
    fun isTailnetIpv4(host: String): Boolean {
        val octets = host.split('.')
        if (octets.size != 4 || octets.any { it.isEmpty() || it.length > 3 || it.any { ch -> !ch.isDigit() } }) return false
        val values = octets.map { it.toIntOrNull() ?: return false }
        return values.all { it in 0..255 } && values[0] == 100 && values[1] in 64..127
    }

    fun isApproved(endpoint: String, hasVerifiedVpnRoute: Boolean): Boolean = runCatching {
        val url = URL(endpoint)
        when (url.protocol.lowercase()) {
            "https" -> url.host.isNotBlank()
            "http" -> isTailnetIpv4(url.host) && hasVerifiedVpnRoute
            else -> false
        }
    }.getOrDefault(false)
}

/**
 * Do not use a generic "VPN connected" flag: the active VPN must actually
 * advertise a route covering the target tailnet address.
 *
 * This deliberately fails closed if Android context, route or VPN details
 * cannot be obtained. Changes to Tailscale connectivity require rechecking
 * before every endpoint selection; no cached trust bit is persisted.
 */
internal object AndroidVpnRouteVerifier {
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun hasVerifiedRoute(host: String): Boolean = hasVerifiedRoute(appContext, host)

    fun hasVerifiedRoute(context: Context?, host: String): Boolean {
        if (context == null || !TrustedEndpointPolicy.isTailnetIpv4(host)) return false
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false
            val props = cm.getLinkProperties(network) ?: return false
            val address = InetAddress.getByName(host)
            props.routes.any { it.destination.contains(address) }
        }.getOrDefault(false)
    }
}
