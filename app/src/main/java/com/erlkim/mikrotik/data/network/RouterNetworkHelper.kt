package com.erlkim.mikrotik.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class LocalNetworkStatus(
    val transportLabel: String,
    val isLocalLanReady: Boolean,
    val isCellularOnly: Boolean,
    val isVpnActive: Boolean,
    val detectedGatewayIp: String?,
    val deviceIp: String?,
)

object RouterNetworkHelper {

    fun inspectActiveNetwork(context: Context): LocalNetworkStatus {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return LocalNetworkStatus(
                transportLabel = "Tidak diketahui",
                isLocalLanReady = false,
                isCellularOnly = false,
                isVpnActive = false,
                detectedGatewayIp = null,
                deviceIp = null,
            )

        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }

        val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val gatewayIp = linkProps?.routes
            ?.firstOrNull { route ->
                route.isDefaultRoute && route.gateway is Inet4Address && !route.gateway!!.isAnyLocalAddress
            }
            ?.gateway
            ?.hostAddress
            ?: linkProps?.routes
                ?.firstOrNull { route ->
                    route.gateway is Inet4Address && !route.gateway!!.isAnyLocalAddress && !route.gateway!!.isLoopbackAddress
                }
                ?.gateway
                ?.hostAddress

        val deviceIp = linkProps?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?.address
            ?.hostAddress
            ?: findLocalIpv4Fallback()

        val transportLabel = when {
            hasWifi && hasVpn -> "Wi-Fi + VPN"
            hasWifi -> "Wi-Fi"
            hasEthernet -> "Ethernet"
            hasVpn -> "VPN"
            hasCellular -> "Data Seluler"
            else -> "Offline"
        }

        val isLocalLanReady = hasWifi || hasEthernet || hasVpn
        val isCellularOnly = hasCellular && !hasWifi && !hasEthernet && !hasVpn

        return LocalNetworkStatus(
            transportLabel = transportLabel,
            isLocalLanReady = isLocalLanReady,
            isCellularOnly = isCellularOnly,
            isVpnActive = hasVpn,
            detectedGatewayIp = gatewayIp,
            deviceIp = deviceIp,
        )
    }

    private fun findLocalIpv4Fallback(): String? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching null
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            if (!iface.isUp || iface.isLoopback) continue
            val addrs = iface.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr: InetAddress = addrs.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                    return@runCatching addr.hostAddress
                }
            }
        }
        null
    }.getOrNull()
}
