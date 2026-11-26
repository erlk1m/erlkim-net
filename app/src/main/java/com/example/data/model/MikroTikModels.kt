package com.example.data.model

enum class ThemeMode(val label: String) {
    DARK("Gelap"),
    LIGHT("Terang"),
    SAKURA_ANIME("Anime Sakura"),
    SYSTEM("Ikuti Sistem"),
}

enum class UserFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    EXPIRED("Expired"),
    VIP("VIP"),
}

data class RouterConfig(
    val host: String = "192.168.88.1",
    val port: Int = 80,
    val username: String = "admin",
    val password: String = "",
    val useHttps: Boolean = false,
    val trustSelfSigned: Boolean = true,
    val demoMode: Boolean = false,
) {
    val displayAddress: String
        get() = if ((useHttps && port == 443) || (!useHttps && port == 80)) host else "$host:$port"
}

sealed interface ConnectionStatus {
    data object Connecting : ConnectionStatus
    data object ConnectedLive : ConnectionStatus
    data object ConnectedDemo : ConnectionStatus
    data class Disconnected(val reason: String) : ConnectionStatus

    val isConnected: Boolean
        get() = this is ConnectedLive || this is ConnectedDemo
}

data class RouterResource(
    val identity: String = "Router RB3011UiAS",
    val boardName: String = "RB3011UiAS",
    val version: String = "Router OS v7.12",
    val uptime: String = "0d 00h 00m",
    val cpuLoadPercent: Int = 0,
    val ramUsagePercent: Int = 0,
    val storageUsagePercent: Int = 0,
    val totalMemoryMb: Long = 0,
    val freeMemoryMb: Long = 0,
    val totalHddMb: Long = 0,
    val freeHddMb: Long = 0,
    val clockTime: String = "--:--:--",
    val clockDate: String = "--/--/----",
    val timeZoneName: String = "Asia/Jakarta",
)

data class TrafficSample(
    val downloadMbps: Float,
    val uploadMbps: Float,
    val timestampMs: Long = System.currentTimeMillis(),
)

data class RouterInterface(
    val id: String,
    val name: String,
    val type: String,
    val running: Boolean,
    val disabled: Boolean,
    val rxMbps: Float = 0f,
    val txMbps: Float = 0f,
    val macAddress: String = "",
    val mtu: String = "1500",
    val comment: String = "",
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
)

data class IpAddressEntry(
    val id: String,
    val address: String,
    val network: String,
    val interfaceName: String,
    val disabled: Boolean = false,
    val comment: String = "",
    val dynamic: Boolean = false,
    val invalid: Boolean = false,
)

data class Ipv4CidrInfo(
    val ip: String,
    val prefix: Int,
    val networkAddress: String,
    val usableHosts: Long,
)

fun parseIpv4Cidr(input: String): Ipv4CidrInfo? {
    val trimmed = input.trim()
    val parts = trimmed.split("/")
    if (parts.size != 2) return null
    val ipStr = parts[0]
    val prefix = parts[1].toIntOrNull() ?: return null
    if (prefix !in 0..32) return null
    val octets = ipStr.split(".")
    if (octets.size != 4) return null
    val nums = octets.map { it.toIntOrNull() ?: return null }
    if (nums.any { it !in 0..255 }) return null

    val ipLong = ((nums[0].toLong() shl 24) or
        (nums[1].toLong() shl 16) or
        (nums[2].toLong() shl 8) or
        nums[3].toLong()) and 0xFFFFFFFFL
    val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    val net = ipLong and mask
    val netStr = "${(net shr 24) and 0xFF}.${(net shr 16) and 0xFF}.${(net shr 8) and 0xFF}.${net and 0xFF}"
    val total = 1L shl (32 - prefix).coerceAtMost(31)
    val usable = when (prefix) {
        32 -> 1L
        31 -> 2L
        0 -> 4294967294L
        else -> (total - 2L).coerceAtLeast(0L)
    }
    return Ipv4CidrInfo(
        ip = ipStr,
        prefix = prefix,
        networkAddress = netStr,
        usableHosts = usable,
    )
}

data class DhcpServerEntry(
    val id: String,
    val name: String,
    val interfaceName: String,
    val addressPool: String,
    val leaseTime: String,
    val disabled: Boolean = false,
    val authoritative: String = "yes",
)

data class DhcpLeaseEntry(
    val id: String,
    val address: String,
    val macAddress: String,
    val hostName: String,
    val status: String,
    val server: String,
    val dynamic: Boolean = true,
    val disabled: Boolean = false,
    val expiresAfter: String = "",
    val comment: String = "",
)

data class WirelessInterface(
    val id: String,
    val name: String,
    val ssid: String,
    val band: String,
    val frequency: String,
    val running: Boolean,
    val disabled: Boolean,
    val connectedClients: Int = 0,
    val mode: String = "ap-bridge",
    val securityProfile: String = "default",
    val channelWidth: String = "20/40mhz-XX",
    val comment: String = "",
    val isWifiV7: Boolean = false,
)

data class HotspotServer(
    val id: String,
    val name: String,
    val interfaceName: String,
    val addressPool: String,
    val profile: String,
    val disabled: Boolean = false,
)

data class HotspotUser(
    val id: String,
    val name: String,
    val password: String = "",
    val profile: String = "default",
    val usedBytes: Long = 0L,
    val limitBytesTotal: Long = 0L,
    val uptime: String = "0s",
    val sessionTimeout: String = "",
    val disabled: Boolean = false,
    val isActive: Boolean = false,
    val isExpired: Boolean = false,
    val comment: String = "",
) {
    val isVip: Boolean
        get() = profile.contains("vip", ignoreCase = true) ||
            name.contains("vip", ignoreCase = true) ||
            comment.contains("vip", ignoreCase = true)

    fun formattedQuota(): String {
        val used = formatBytes(usedBytes)
        return if (limitBytesTotal > 0L) {
            "$used / ${formatBytes(limitBytesTotal)}"
        } else {
            used
        }
    }

    fun formattedSessionTimeout(): String {
        val t = sessionTimeout.trim()
        return if (t.isEmpty() || t == "0s" || t == "0" || t == "00:00:00" || t.equals("none", true) || t.equals("unlimited", true)) {
            "Unlimited"
        } else {
            t
        }
    }
}

data class HotspotActiveSession(
    val id: String,
    val user: String,
    val address: String,
    val macAddress: String,
    val uptime: String,
    val bytesIn: Long,
    val bytesOut: Long,
    val server: String = "hotspot1",
)

data class HotspotUserProfile(
    val id: String,
    val name: String,
    val sharedUsers: String = "1",
    val rateLimit: String = "Unlimited",
    val sessionTimeout: String = "None",
)

data class IpBindingEntry(
    val id: String,
    val macAddress: String,
    val address: String,
    val toAddress: String,
    val type: String, // bypassed, blocked, regular
    val comment: String = "",
    val disabled: Boolean = false,
    val server: String = "all",
)

data class WalledGardenEntry(
    val id: String,
    val dstHost: String,
    val dstPort: String = "",
    val action: String = "allow",
    val comment: String = "",
    val disabled: Boolean = false,
)

data class RouterSystemUser(
    val id: String,
    val name: String,
    val group: String,
    val lastLoggedIn: String = "-",
    val disabled: Boolean = false,
)

enum class LogSeverity(val label: String) {
    INFO("Info"),
    WARNING("Warning"),
    ERROR("Error"),
}

enum class LogSeverityFilter(val label: String) {
    ALL("All"),
    INFO("Info"),
    WARNING("Warning"),
    ERROR("Error"),
}

data class RouterLogEntry(
    val id: String,
    val title: String,
    val detail: String,
    val time: String,
    val topics: String = "system,info",
    val severity: LogSeverity = LogSeverity.INFO,
    val isAlert: Boolean = severity == LogSeverity.ERROR,
)

enum class QuotaAlertSeverity(val label: String) {
    WARNING("Mendekati Batas"),
    EXCEEDED("Melebihi Kuota"),
}

data class QuotaAlertConfig(
    val enabled: Boolean = true,
    val globalQuotaLimitMb: Long = 500L,
    val warningThresholdPercent: Int = 80,
    val systemNotificationsEnabled: Boolean = true,
)

data class BandwidthQuotaAlert(
    val id: String,
    val username: String,
    val profile: String,
    val ipAddress: String,
    val usedBytes: Long,
    val limitBytes: Long,
    val usagePercent: Int,
    val severity: QuotaAlertSeverity,
    val timestamp: String,
    val isAcknowledged: Boolean = false,
)

data class ThroughputAlertConfig(
    val enabled: Boolean = true,
    val thresholdMbps: Float = 50f,
    val cooldownSeconds: Int = 30,
    val systemNotificationsEnabled: Boolean = true,
)

data class ThroughputAlertEvent(
    val id: String,
    val interfaceName: String,
    val totalMbps: Float,
    val downloadMbps: Float,
    val uploadMbps: Float,
    val thresholdMbps: Float,
    val timestamp: String,
    val isAcknowledged: Boolean = false,
)

enum class ResourceMetricType(val label: String) {
    CPU("CPU"),
    MEMORY("Memory"),
}

data class ResourceAlertConfig(
    val enabled: Boolean = true,
    val cpuThresholdPercent: Int = 80,
    val memoryThresholdPercent: Int = 80,
    val cooldownSeconds: Int = 60,
    val systemNotificationsEnabled: Boolean = true,
)

data class ResourceAlertEvent(
    val id: String,
    val metricType: ResourceMetricType,
    val currentPercent: Int,
    val thresholdPercent: Int,
    val routerIdentity: String,
    val detail: String,
    val timestamp: String,
    val isAcknowledged: Boolean = false,
)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.0f MB", mb)
        else -> String.format("%.0f KB", kb)
    }
}
