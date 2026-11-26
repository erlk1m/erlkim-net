package com.example.data.network

import com.example.data.model.RouterConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class PingSource(val label: String) {
    DEVICE("Device"),
    ROUTER_API("RouterOS"),
}

data class PingReply(
    val seq: Int,
    val target: String,
    val resolvedIp: String,
    val latencyMs: Double?,
    val ttl: Int?,
    val status: String,
    val timestamp: String,
    val source: PingSource,
) {
    val isSuccess: Boolean
        get() = latencyMs != null
}

data class PingDiagnosticState(
    val targetHost: String = "192.168.88.1",
    val packetCount: Int = 4,
    val useRouterApi: Boolean = false,
    val isRunning: Boolean = false,
    val replies: List<PingReply> = emptyList(),
    val lastError: String? = null,
    val hasCustomTarget: Boolean = false,
) {
    val sentCount: Int
        get() = replies.size

    val receivedCount: Int
        get() = replies.count { it.isSuccess }

    val packetLossPercent: Int
        get() = if (sentCount == 0) 0 else (((sentCount - receivedCount) * 100) / sentCount)

    val successfulLatencies: List<Double>
        get() = replies.mapNotNull { it.latencyMs }

    val minMs: Double?
        get() = successfulLatencies.minOrNull()

    val avgMs: Double?
        get() = successfulLatencies.takeIf { it.isNotEmpty() }?.average()

    val maxMs: Double?
        get() = successfulLatencies.maxOrNull()

    val lastLatencyMs: Double?
        get() = replies.lastOrNull { it.isSuccess }?.latencyMs

    val jitterMs: Double?
        get() {
            val vals = successfulLatencies
            if (vals.size < 2) return null
            var sum = 0.0
            for (i in 1 until vals.size) {
                sum += abs(vals[i] - vals[i - 1])
            }
            return sum / (vals.size - 1)
        }
}

class PingDiagnosticEngine(
    private val restClient: MikroTikRestClient,
) {
    private val timeRegex = Regex("""time[=<]\s*([0-9]+(?:\.[0-9]+)?)\s*ms""", RegexOption.IGNORE_CASE)
    private val ttlRegex = Regex("""ttl=(\d+)""", RegexOption.IGNORE_CASE)
    private val ipRegex = Regex("""\(([0-9a-fA-F.:]+)\)""")

    /**
     * Sanitizes host/IP to avoid command injection when calling /system/bin/ping.
     */
    fun sanitizeHost(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .substringBefore(":")
            .trim()
        require(cleaned.isNotEmpty()) { "Please enter an IP address or hostname." }
        require(cleaned.matches(Regex("""^[a-zA-Z0-9.\-_]+$"""))) {
            "Invalid IP address or hostname format."
        }
        return cleaned
    }

    suspend fun pingOnceFromDevice(
        host: String,
        seq: Int,
        fallbackTcpPort: Int = 80,
        timeoutMs: Int = 2500,
    ): PingReply = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val resolvedIp = runCatching {
            InetAddress.getByName(host).hostAddress ?: host
        }.getOrDefault(host)

        // 1. Try native Android ICMP /system/bin/ping first (accurate RTT & TTL without root)
        val icmpAttempt = runSystemIcmpPing(host, timeoutSec = (timeoutMs / 1000).coerceAtLeast(1))
        if (icmpAttempt != null) {
            return@withContext PingReply(
                seq = seq,
                target = host,
                resolvedIp = icmpAttempt.resolvedIp ?: resolvedIp,
                latencyMs = icmpAttempt.latencyMs,
                ttl = icmpAttempt.ttl,
                status = if (icmpAttempt.latencyMs != null) "ICMP OK" else icmpAttempt.errorDetail,
                timestamp = timestamp,
                source = PingSource.DEVICE,
            )
        }

        // 2. Fallback: TCP socket handshake RTT probe (ports: configured port, 80, 443, 53)
        val tcpPorts = listOf(fallbackTcpPort, 80, 443, 53).distinct().filter { it in 1..65535 }
        for (port in tcpPorts) {
            val startNs = System.nanoTime()
            val connected = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(resolvedIp, port), timeoutMs.coerceAtMost(1500))
                }
                true
            }.getOrDefault(false)
            if (connected) {
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
                return@withContext PingReply(
                    seq = seq,
                    target = host,
                    resolvedIp = resolvedIp,
                    latencyMs = elapsedMs,
                    ttl = null,
                    status = "TCP:$port OK",
                    timestamp = timestamp,
                    source = PingSource.DEVICE,
                )
            }
        }

        // 3. Final fallback: InetAddress.isReachable
        val startNs = System.nanoTime()
        val reachable = runCatching {
            InetAddress.getByName(resolvedIp).isReachable(timeoutMs)
        }.getOrDefault(false)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0

        if (reachable) {
            PingReply(
                seq = seq,
                target = host,
                resolvedIp = resolvedIp,
                latencyMs = elapsedMs,
                ttl = null,
                status = "Reachable",
                timestamp = timestamp,
                source = PingSource.DEVICE,
            )
        } else {
            PingReply(
                seq = seq,
                target = host,
                resolvedIp = resolvedIp,
                latencyMs = null,
                ttl = null,
                status = "Request timed out",
                timestamp = timestamp,
                source = PingSource.DEVICE,
            )
        }
    }

    suspend fun pingOnceFromRouterOs(
        config: RouterConfig,
        host: String,
        seq: Int,
    ): PingReply = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val apiResult = restClient.pingHost(config, host, count = 1)
        apiResult.fold(
            onSuccess = { jsonArray ->
                val item = jsonArray.optJSONObject(0)
                if (item == null) {
                    PingReply(
                        seq = seq,
                        target = host,
                        resolvedIp = host,
                        latencyMs = null,
                        ttl = null,
                        status = "No response",
                        timestamp = timestamp,
                        source = PingSource.ROUTER_API,
                    )
                } else {
                    val replyHost = item.optString("host", host).ifBlank { host }
                    val statusText = item.optString("status", "")
                    val ttl = item.optString("ttl", "").toIntOrNull()
                    val latencyMs = parseRouterOsTimeMs(item.optString("time", ""))
                    PingReply(
                        seq = seq,
                        target = host,
                        resolvedIp = replyHost,
                        latencyMs = if (statusText.contains("timeout", true)) null else latencyMs,
                        ttl = ttl,
                        status = statusText.ifBlank { if (latencyMs != null) "RouterOS OK" else "Timeout" },
                        timestamp = timestamp,
                        source = PingSource.ROUTER_API,
                    )
                }
            },
            onFailure = { err ->
                PingReply(
                    seq = seq,
                    target = host,
                    resolvedIp = host,
                    latencyMs = null,
                    ttl = null,
                    status = err.message?.take(36) ?: "Router API error",
                    timestamp = timestamp,
                    source = PingSource.ROUTER_API,
                )
            }
        )
    }

    private data class IcmpProbeResult(
        val latencyMs: Double?,
        val ttl: Int?,
        val resolvedIp: String?,
        val errorDetail: String,
    )

    private fun runSystemIcmpPing(host: String, timeoutSec: Int): IcmpProbeResult? {
        return runCatching {
            val process = ProcessBuilder(
                "/system/bin/ping",
                "-c", "1",
                "-W", timeoutSec.toString(),
                host,
            ).redirectErrorStream(true).start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val exitCode = process.waitFor()

            // If ICMP binary itself is blocked by SELinux/permission, return null to trigger TCP fallback
            if (output.contains("Operation not permitted", ignoreCase = true) ||
                output.contains("Permission denied", ignoreCase = true)
            ) {
                return null
            }

            val resolvedIp = ipRegex.find(output)?.groupValues?.getOrNull(1)
            val latency = timeRegex.find(output)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            val ttl = ttlRegex.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()

            if (exitCode == 0 && latency != null) {
                IcmpProbeResult(latencyMs = latency, ttl = ttl, resolvedIp = resolvedIp, errorDetail = "OK")
            } else if (output.contains("unknown host", ignoreCase = true) ||
                output.contains("Name or service not known", ignoreCase = true)
            ) {
                IcmpProbeResult(latencyMs = null, ttl = null, resolvedIp = null, errorDetail = "Unknown host")
            } else if (output.contains("Network is unreachable", ignoreCase = true)) {
                IcmpProbeResult(latencyMs = null, ttl = null, resolvedIp = resolvedIp, errorDetail = "Network unreachable")
            } else {
                // Return null if ping failed with 0 replies so TCP fallback can still check reachability
                // unless ICMP explicitly reported 100% packet loss and no TCP response.
                null
            }
        }.getOrNull()
    }

    /**
     * Parses RouterOS REST /ping time formats such as "2ms140us", "12ms", "540us", "00:00:00.012", or "18.5ms".
     */
    private fun parseRouterOsTimeMs(raw: String): Double? {
        val text = raw.trim().lowercase(Locale.US)
        if (text.isEmpty()) return null

        val msUsMatch = Regex("""(\d+)ms(\d+)us""").find(text)
        if (msUsMatch != null) {
            val ms = msUsMatch.groupValues[1].toDouble()
            val us = msUsMatch.groupValues[2].toDouble()
            return ms + (us / 1000.0)
        }
        val msMatch = Regex("""([0-9]+(?:\.[0-9]+)?)ms""").find(text)
        if (msMatch != null) {
            return msMatch.groupValues[1].toDoubleOrNull()
        }
        val usMatch = Regex("""([0-9]+(?:\.[0-9]+)?)us""").find(text)
        if (usMatch != null) {
            return usMatch.groupValues[1].toDoubleOrNull()?.div(1000.0)
        }
        val secColonMatch = Regex("""\d+:\d+:(\d+(?:\.\d+)?)""").find(text)
        if (secColonMatch != null) {
            return secColonMatch.groupValues[1].toDoubleOrNull()?.times(1000.0)
        }
        return text.toDoubleOrNull()
    }
}
