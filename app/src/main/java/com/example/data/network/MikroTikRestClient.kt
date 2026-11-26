package com.example.data.network

import android.annotation.SuppressLint
import com.example.data.backup.BackupRestoreOptions
import com.example.data.backup.RestoreSummary
import com.example.data.backup.RouterBackupPayload
import com.example.data.model.DhcpLeaseEntry
import com.example.data.model.DhcpServerEntry
import com.example.data.model.HotspotActiveSession
import com.example.data.model.HotspotServer
import com.example.data.model.HotspotUser
import com.example.data.model.HotspotUserProfile
import com.example.data.model.IpAddressEntry
import com.example.data.model.IpBindingEntry
import com.example.data.model.LogSeverity
import com.example.data.model.RouterConfig
import com.example.data.model.RouterInterface
import com.example.data.model.RouterLogEntry
import com.example.data.model.RouterResource
import com.example.data.model.RouterSystemUser
import com.example.data.model.TrafficSample
import com.example.data.model.WalledGardenEntry
import com.example.data.model.WirelessInterface
import java.io.IOException
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class RouterSnapshot(
    val resource: RouterResource,
    val interfaces: List<RouterInterface>,
    val ipAddresses: List<IpAddressEntry>,
    val dhcpServers: List<DhcpServerEntry>,
    val dhcpLeases: List<DhcpLeaseEntry>,
    val wirelessInterfaces: List<WirelessInterface>,
    val hotspotServers: List<HotspotServer>,
    val hotspotUsers: List<HotspotUser>,
    val activeSessions: List<HotspotActiveSession>,
    val userProfiles: List<HotspotUserProfile>,
    val ipBindings: List<IpBindingEntry>,
    val walledGarden: List<WalledGardenEntry>,
    val systemUsers: List<RouterSystemUser>,
    val systemLogs: List<RouterLogEntry> = emptyList(),
)

data class ConnectionTestResult(
    val identity: String,
    val boardName: String,
    val routerOsVersion: String,
    val latencyMs: Long,
    val endpointUrl: String,
)

/**
 * Real MikroTik RouterOS v7+ REST API client (/rest/...).
 * Communicates over HTTP or HTTPS using HTTP Basic Auth per MikroTik RouterOS documentation.
 */
class MikroTikRestClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun buildClient(config: RouterConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)

        if (config.useHttps && config.trustSelfSigned) {
            configureLanSelfSignedSsl(builder)
        }
        return builder.build()
    }

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun configureLanSelfSignedSsl(builder: OkHttpClient.Builder) {
        try {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { _, _ -> true }
        } catch (_: Exception) {
            // Fall back to default system trust store if TLS initialization fails
        }
    }

    private fun baseUrl(config: RouterConfig): String {
        val scheme = if (config.useHttps) "https" else "http"
        val cleanHost = config.host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        return "$scheme://$cleanHost:${config.port}/rest"
    }

    private fun authHeader(config: RouterConfig): String =
        Credentials.basic(config.username, config.password)

    private fun executeGet(client: OkHttpClient, config: RouterConfig, path: String): String {
        val request = Request.Builder()
            .url("${baseUrl(config)}/$path")
            .header("Authorization", authHeader(config))
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 401) {
                throw IOException("Autentikasi ditolak (401). Periksa username/password RouterOS.")
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${extractErrorMessage(body)}")
            }
            return body
        }
    }

    private fun executeJson(
        client: OkHttpClient,
        config: RouterConfig,
        method: String,
        path: String,
        payload: JSONObject? = null,
    ): String {
        val body = payload?.toString()?.toRequestBody(jsonMediaType)
            ?: if (method == "POST" || method == "PUT" || method == "PATCH") {
                "{}".toRequestBody(jsonMediaType)
            } else {
                null
            }

        val request = Request.Builder()
            .url("${baseUrl(config)}/$path")
            .header("Authorization", authHeader(config))
            .header("Accept", "application/json")
            .method(method, body)
            .build()

        client.newCall(request).execute().use { response ->
            val respBody = response.body?.string().orEmpty()
            if (response.code == 401) {
                throw IOException("Autentikasi ditolak (401). Periksa akun API RouterOS.")
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${extractErrorMessage(respBody)}")
            }
            return respBody
        }
    }

    private fun extractErrorMessage(body: String): String {
        return runCatching {
            val obj = JSONObject(body)
            obj.optString("detail").ifBlank { obj.optString("message").ifBlank { body.take(120) } }
        }.getOrDefault(body.take(120))
    }

    private fun safeGetArray(client: OkHttpClient, config: RouterConfig, path: String): JSONArray {
        return runCatching {
            val raw = executeGet(client, config, path)
            JSONArray(raw)
        }.getOrDefault(JSONArray())
    }

    private fun safeGetObject(client: OkHttpClient, config: RouterConfig, path: String): JSONObject {
        return runCatching {
            val raw = executeGet(client, config, path)
            JSONObject(raw)
        }.getOrDefault(JSONObject())
    }

    suspend fun testConnection(config: RouterConfig): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        runCatching {
            val startNs = System.nanoTime()
            val client = buildClient(config)
            val rawResource = executeGet(client, config, "system/resource")
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            val resObj = JSONObject(rawResource)
            val identityObj = safeGetObject(client, config, "system/identity")
            val board = resObj.optString("board-name", "MikroTik")
            val rawVersion = resObj.optString("version", "7.x")
            val version = if (rawVersion.startsWith("Router", ignoreCase = true)) {
                rawVersion
            } else {
                "RouterOS v$rawVersion"
            }
            val identity = identityObj.optString("name").ifBlank { "Router $board" }
            ConnectionTestResult(
                identity = identity,
                boardName = board,
                routerOsVersion = version,
                latencyMs = elapsedMs,
                endpointUrl = "${baseUrl(config)}/system/resource",
            )
        }
    }

    suspend fun fetchSnapshot(config: RouterConfig): Result<RouterSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            // Primary connectivity check: /rest/system/resource
            val resObj = JSONObject(executeGet(client, config, "system/resource"))
            val identityObj = safeGetObject(client, config, "system/identity")
            val clockObj = safeGetObject(client, config, "system/clock")

            val totalMem = resObj.optString("total-memory").toLongOrNull() ?: 0L
            val freeMem = resObj.optString("free-memory").toLongOrNull() ?: 0L
            val totalHdd = resObj.optString("total-hdd-space").toLongOrNull() ?: 0L
            val freeHdd = resObj.optString("free-hdd-space").toLongOrNull() ?: 0L
            val ramPct = if (totalMem > 0) (((totalMem - freeMem) * 100) / totalMem).toInt().coerceIn(0, 100) else 0
            val hddPct = if (totalHdd > 0) (((totalHdd - freeHdd) * 100) / totalHdd).toInt().coerceIn(0, 100) else 0
            val board = resObj.optString("board-name", "MikroTik")
            val rawVersion = resObj.optString("version", "7.x")

            val resource = RouterResource(
                identity = identityObj.optString("name").ifBlank { "Router $board" },
                boardName = board,
                version = if (rawVersion.startsWith("Router", ignoreCase = true)) rawVersion else "Router OS v$rawVersion",
                uptime = resObj.optString("uptime", "0s"),
                cpuLoadPercent = resObj.optString("cpu-load").toIntOrNull()?.coerceIn(0, 100) ?: 0,
                ramUsagePercent = ramPct,
                storageUsagePercent = hddPct,
                totalMemoryMb = totalMem / (1024 * 1024),
                freeMemoryMb = freeMem / (1024 * 1024),
                totalHddMb = totalHdd / (1024 * 1024),
                freeHddMb = freeHdd / (1024 * 1024),
                clockTime = clockObj.optString("time", "--:--:--"),
                clockDate = clockObj.optString("date", "--/--/----"),
                timeZoneName = clockObj.optString("time-zone-name", "Asia/Jakarta"),
            )

            val interfaces = parseInterfaces(safeGetArray(client, config, "interface"))
            val ipAddresses = parseIpAddresses(safeGetArray(client, config, "ip/address"))
            val dhcpServers = parseDhcpServers(safeGetArray(client, config, "ip/dhcp-server"))
            val dhcpLeases = parseDhcpLeases(safeGetArray(client, config, "ip/dhcp-server/lease"))

            val legacyWirelessArr = safeGetArray(client, config, "interface/wireless")
            val isWifiV7 = legacyWirelessArr.length() == 0
            val wirelessArr = if (!isWifiV7) legacyWirelessArr else safeGetArray(client, config, "interface/wifi")
            val wirelessInterfaces = parseWireless(wirelessArr, isWifiV7)

            val hotspotServers = parseHotspotServers(safeGetArray(client, config, "ip/hotspot"))
            val activeSessions = parseActiveSessions(safeGetArray(client, config, "ip/hotspot/active"))
            val activeUsernames = activeSessions.map { it.user }.toSet()
            val hotspotUsers = parseHotspotUsers(
                safeGetArray(client, config, "ip/hotspot/user"),
                activeUsernames,
            )
            val userProfiles = parseUserProfiles(safeGetArray(client, config, "ip/hotspot/user/profile"))
            val ipBindings = parseIpBindings(safeGetArray(client, config, "ip/hotspot/ip-binding"))
            val walledGarden = parseWalledGarden(safeGetArray(client, config, "ip/hotspot/walled-garden"))
            val systemUsers = parseSystemUsers(safeGetArray(client, config, "user"))
            val systemLogs = parseSystemLogs(safeGetArray(client, config, "log"))

            RouterSnapshot(
                resource = resource,
                interfaces = interfaces,
                ipAddresses = ipAddresses,
                dhcpServers = dhcpServers,
                dhcpLeases = dhcpLeases,
                wirelessInterfaces = wirelessInterfaces,
                hotspotServers = hotspotServers,
                hotspotUsers = hotspotUsers,
                activeSessions = activeSessions,
                userProfiles = userProfiles,
                ipBindings = ipBindings,
                walledGarden = walledGarden,
                systemUsers = systemUsers,
                systemLogs = systemLogs,
            )
        }
    }

    suspend fun pollTrafficAndResource(
        config: RouterConfig,
        interfaceName: String,
    ): Result<Pair<RouterResource, TrafficSample>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val resObj = JSONObject(executeGet(client, config, "system/resource"))
            val identityObj = safeGetObject(client, config, "system/identity")
            val totalMem = resObj.optString("total-memory").toLongOrNull() ?: 0L
            val freeMem = resObj.optString("free-memory").toLongOrNull() ?: 0L
            val totalHdd = resObj.optString("total-hdd-space").toLongOrNull() ?: 0L
            val freeHdd = resObj.optString("free-hdd-space").toLongOrNull() ?: 0L
            val ramPct = if (totalMem > 0) (((totalMem - freeMem) * 100) / totalMem).toInt().coerceIn(0, 100) else 0
            val hddPct = if (totalHdd > 0) (((totalHdd - freeHdd) * 100) / totalHdd).toInt().coerceIn(0, 100) else 0
            val board = resObj.optString("board-name", "MikroTik")
            val rawVersion = resObj.optString("version", "7.x")

            val resource = RouterResource(
                identity = identityObj.optString("name").ifBlank { "Router $board" },
                boardName = board,
                version = if (rawVersion.startsWith("Router", ignoreCase = true)) rawVersion else "Router OS v$rawVersion",
                uptime = resObj.optString("uptime", "0s"),
                cpuLoadPercent = resObj.optString("cpu-load").toIntOrNull()?.coerceIn(0, 100) ?: 0,
                ramUsagePercent = ramPct,
                storageUsagePercent = hddPct,
                totalMemoryMb = totalMem / (1024 * 1024),
                freeMemoryMb = freeMem / (1024 * 1024),
                totalHddMb = totalHdd / (1024 * 1024),
                freeHddMb = freeHdd / (1024 * 1024),
            )

            var rxMbps = 0f
            var txMbps = 0f
            if (interfaceName.isNotBlank()) {
                val payload = JSONObject().apply {
                    put("interface", interfaceName)
                    put("once", "")
                }
                runCatching {
                    val trafficRaw = executeJson(client, config, "POST", "interface/monitor-traffic", payload)
                    val arr = JSONArray(trafficRaw)
                    if (arr.length() > 0) {
                        val item = arr.getJSONObject(0)
                        val rxBps = item.optString("rx-bits-per-second").toDoubleOrNull() ?: 0.0
                        val txBps = item.optString("tx-bits-per-second").toDoubleOrNull() ?: 0.0
                        rxMbps = (rxBps / 1_000_000.0).toFloat()
                        txMbps = (txBps / 1_000_000.0).toFloat()
                    }
                }
            }

            resource to TrafficSample(downloadMbps = rxMbps, uploadMbps = txMbps)
        }
    }

    suspend fun fetchInterfaces(config: RouterConfig): Result<List<RouterInterface>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val raw = executeGet(client, config, "interface").trim()
            val arr = if (raw.startsWith("[")) JSONArray(raw) else JSONArray()
            parseInterfaces(arr)
        }
    }

    suspend fun fetchIpAddresses(config: RouterConfig): Result<List<IpAddressEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val raw = executeGet(client, config, "ip/address").trim()
            val arr = if (raw.startsWith("[")) JSONArray(raw) else JSONArray()
            parseIpAddresses(arr)
        }
    }

    suspend fun fetchDhcpData(
        config: RouterConfig,
    ): Result<Pair<List<DhcpServerEntry>, List<DhcpLeaseEntry>>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val rawServers = executeGet(client, config, "ip/dhcp-server").trim()
            val serversArr = if (rawServers.startsWith("[")) JSONArray(rawServers) else JSONArray()
            val leasesArr = safeGetArray(client, config, "ip/dhcp-server/lease")
            parseDhcpServers(serversArr) to parseDhcpLeases(leasesArr)
        }
    }

    suspend fun fetchSystemHealth(config: RouterConfig): Result<RouterResource> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val resObj = JSONObject(executeGet(client, config, "system/resource"))
            val identityObj = safeGetObject(client, config, "system/identity")
            val clockObj = safeGetObject(client, config, "system/clock")

            val totalMem = resObj.optString("total-memory").toLongOrNull() ?: 0L
            val freeMem = resObj.optString("free-memory").toLongOrNull() ?: 0L
            val totalHdd = resObj.optString("total-hdd-space").toLongOrNull() ?: 0L
            val freeHdd = resObj.optString("free-hdd-space").toLongOrNull() ?: 0L
            val ramPct = if (totalMem > 0) (((totalMem - freeMem) * 100) / totalMem).toInt().coerceIn(0, 100) else 0
            val hddPct = if (totalHdd > 0) (((totalHdd - freeHdd) * 100) / totalHdd).toInt().coerceIn(0, 100) else 0
            val board = resObj.optString("board-name", "MikroTik")
            val rawVersion = resObj.optString("version", "7.x")

            RouterResource(
                identity = identityObj.optString("name").ifBlank { "Router $board" },
                boardName = board,
                version = if (rawVersion.startsWith("Router", ignoreCase = true)) rawVersion else "Router OS v$rawVersion",
                uptime = resObj.optString("uptime", "0s"),
                cpuLoadPercent = resObj.optString("cpu-load").toIntOrNull()?.coerceIn(0, 100) ?: 0,
                ramUsagePercent = ramPct,
                storageUsagePercent = hddPct,
                totalMemoryMb = totalMem / (1024 * 1024),
                freeMemoryMb = freeMem / (1024 * 1024),
                totalHddMb = totalHdd / (1024 * 1024),
                freeHddMb = freeHdd / (1024 * 1024),
                clockTime = clockObj.optString("time", "--:--:--"),
                clockDate = clockObj.optString("date", "--/--/----"),
                timeZoneName = clockObj.optString("time-zone-name", "Asia/Jakarta"),
            )
        }
    }

    suspend fun addHotspotUser(
        config: RouterConfig,
        name: String,
        password: String,
        profile: String,
        limitBytesTotal: Long,
        comment: String,
        sessionTimeout: String = "",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("name", name)
                put("password", password)
                put("profile", profile)
                if (limitBytesTotal > 0L) put("limit-bytes-total", limitBytesTotal.toString())
                if (sessionTimeout.isNotBlank() && sessionTimeout != "0s") {
                    put("limit-uptime", sessionTimeout)
                }
                if (comment.isNotBlank()) put("comment", comment)
            }
            executeJson(client, config, "PUT", "ip/hotspot/user", payload)
            Unit
        }
    }

    suspend fun updateHotspotUserSessionTimeout(
        config: RouterConfig,
        userId: String,
        sessionTimeout: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val encodedId = URLEncoder.encode(userId, "UTF-8")
            val normalized = sessionTimeout.trim().ifBlank { "0s" }
            val payload = JSONObject().put("limit-uptime", normalized)
            executeJson(client, config, "PATCH", "ip/hotspot/user/$encodedId", payload)
            Unit
        }
    }

    suspend fun updateHotspotUserPassword(
        config: RouterConfig,
        userId: String,
        newPassword: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val encodedId = URLEncoder.encode(userId, "UTF-8")
            val payload = JSONObject().put("password", newPassword)
            executeJson(client, config, "PATCH", "ip/hotspot/user/$encodedId", payload)
            Unit
        }
    }

    suspend fun toggleHotspotUserDisabled(
        config: RouterConfig,
        userId: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val encodedId = URLEncoder.encode(userId, "UTF-8")
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", "ip/hotspot/user/$encodedId", payload)
            Unit
        }
    }

    suspend fun deleteHotspotUser(config: RouterConfig, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val encodedId = URLEncoder.encode(userId, "UTF-8")
            executeJson(client, config, "DELETE", "ip/hotspot/user/$encodedId")
            Unit
        }
    }

    suspend fun kickActiveSession(config: RouterConfig, sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val encodedId = URLEncoder.encode(sessionId, "UTF-8")
            executeJson(client, config, "DELETE", "ip/hotspot/active/$encodedId")
            Unit
        }
    }

    suspend fun addWalledGarden(
        config: RouterConfig,
        dstHost: String,
        dstPort: String,
        action: String,
        comment: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("dst-host", dstHost)
                if (dstPort.isNotBlank()) put("dst-port", dstPort)
                put("action", action)
                if (comment.isNotBlank()) put("comment", comment)
            }
            executeJson(client, config, "PUT", "ip/hotspot/walled-garden", payload)
            Unit
        }
    }

    suspend fun updateWalledGarden(
        config: RouterConfig,
        id: String,
        dstHost: String,
        dstPort: String,
        action: String,
        comment: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("dst-host", dstHost)
                put("dst-port", dstPort)
                put("action", action)
                put("comment", comment)
            }
            executeJson(client, config, "PATCH", "ip/hotspot/walled-garden/${URLEncoder.encode(id, "UTF-8")}", payload)
            Unit
        }
    }

    suspend fun toggleWalledGardenDisabled(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", "ip/hotspot/walled-garden/${URLEncoder.encode(id, "UTF-8")}", payload)
            Unit
        }
    }

    suspend fun deleteWalledGarden(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            executeJson(client, config, "DELETE", "ip/hotspot/walled-garden/${URLEncoder.encode(id, "UTF-8")}")
            Unit
        }
    }

    suspend fun addHotspotServer(
        config: RouterConfig,
        name: String,
        interfaceName: String,
        addressPool: String,
        profile: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("name", name)
                put("interface", interfaceName)
                if (addressPool.isNotBlank()) put("address-pool", addressPool)
                if (profile.isNotBlank()) put("profile", profile)
                put("disabled", "false")
            }
            executeJson(client, config, "PUT", "ip/hotspot", payload)
            Unit
        }
    }

    suspend fun toggleHotspotServerDisabled(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", "ip/hotspot/${URLEncoder.encode(id, "UTF-8")}", payload)
            Unit
        }
    }

    suspend fun deleteHotspotServer(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            executeJson(client, config, "DELETE", "ip/hotspot/${URLEncoder.encode(id, "UTF-8")}")
            Unit
        }
    }

    suspend fun fetchIpBindings(
        config: RouterConfig,
    ): Result<List<IpBindingEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val raw = executeGet(client, config, "ip/hotspot/ip-binding").trim()
            val arr = if (raw.startsWith("[")) JSONArray(raw) else JSONArray()
            parseIpBindings(arr)
        }
    }

    suspend fun addIpBinding(
        config: RouterConfig,
        macAddress: String,
        address: String,
        type: String,
        comment: String,
        toAddress: String = "",
        server: String = "all",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                if (macAddress.isNotBlank()) put("mac-address", macAddress)
                if (address.isNotBlank()) put("address", address)
                if (toAddress.isNotBlank()) put("to-address", toAddress)
                if (server.isNotBlank() && !server.equals("all", ignoreCase = true)) {
                    put("server", server)
                }
                put("type", type)
                if (comment.isNotBlank()) put("comment", comment)
            }
            executeJson(client, config, "PUT", "ip/hotspot/ip-binding", payload)
            Unit
        }
    }

    suspend fun updateIpBinding(
        config: RouterConfig,
        id: String,
        macAddress: String,
        address: String,
        toAddress: String,
        server: String,
        type: String,
        comment: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("mac-address", macAddress)
                put("address", address)
                put("to-address", toAddress)
                put("server", server.ifBlank { "all" })
                put("type", type)
                put("comment", comment)
            }
            executeJson(client, config, "PATCH", "ip/hotspot/ip-binding/${URLEncoder.encode(id, "UTF-8")}", payload)
            Unit
        }
    }

    suspend fun toggleIpBindingDisabled(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", "ip/hotspot/ip-binding/${URLEncoder.encode(id, "UTF-8")}", payload)
            Unit
        }
    }

    suspend fun deleteIpBinding(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            executeJson(client, config, "DELETE", "ip/hotspot/ip-binding/${URLEncoder.encode(id, "UTF-8")}")
            Unit
        }
    }

    suspend fun fetchHotspotUserProfiles(
        config: RouterConfig,
    ): Result<List<HotspotUserProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val raw = executeGet(client, config, "ip/hotspot/user/profile").trim()
            val arr = if (raw.startsWith("[")) JSONArray(raw) else JSONArray()
            parseUserProfiles(arr)
        }
    }

    suspend fun addUserProfile(
        config: RouterConfig,
        name: String,
        rateLimit: String,
        sharedUsers: String,
        sessionTimeout: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("name", name)
                if (rateLimit.isNotBlank()) put("rate-limit", rateLimit)
                if (sharedUsers.isNotBlank()) put("shared-users", sharedUsers)
                if (sessionTimeout.isNotBlank()) put("session-timeout", sessionTimeout)
            }
            executeJson(client, config, "PUT", "ip/hotspot/user/profile", payload)
            Unit
        }
    }

    suspend fun updateUserProfile(
        config: RouterConfig,
        id: String,
        name: String,
        rateLimit: String,
        sharedUsers: String,
        sessionTimeout: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("name", name)
                put("rate-limit", if (rateLimit.equals("Unlimited", true)) "" else rateLimit)
                put("shared-users", sharedUsers.ifBlank { "1" })
                put("session-timeout", if (sessionTimeout.equals("None", true)) "" else sessionTimeout)
            }
            executeJson(client, config, "PATCH", "ip/hotspot/user/profile/${URLEncoder.encode(id, "UTF-8")}", payload)
            Unit
        }
    }

    suspend fun deleteUserProfile(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            executeJson(client, config, "DELETE", "ip/hotspot/user/profile/${URLEncoder.encode(id, "UTF-8")}")
            Unit
        }
    }

    suspend fun addIpAddress(
        config: RouterConfig,
        address: String,
        interfaceName: String,
        comment: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("address", address)
                put("interface", interfaceName)
                if (comment.isNotBlank()) put("comment", comment)
            }
            executeJson(client, config, "PUT", "ip/address", payload)
            Unit
        }
    }

    suspend fun updateIpAddress(
        config: RouterConfig,
        id: String,
        address: String,
        interfaceName: String,
        comment: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("address", address)
                put("interface", interfaceName)
                put("comment", comment)
            }
            executeJson(client, config, "PATCH", "ip/address/${URLEncoder.encode(id, "UTF-8")}", payload)
            Unit
        }
    }

    suspend fun toggleIpAddressDisabled(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", "ip/address/${URLEncoder.encode(id, "UTF-8")}", payload)
            Unit
        }
    }

    suspend fun deleteIpAddress(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            executeJson(client, config, "DELETE", "ip/address/${URLEncoder.encode(id, "UTF-8")}")
            Unit
        }
    }

    suspend fun addDhcpServer(
        config: RouterConfig,
        name: String,
        interfaceName: String,
        addressPool: String,
        leaseTime: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("name", name)
                put("interface", interfaceName)
                if (addressPool.isNotBlank()) put("address-pool", addressPool)
                if (leaseTime.isNotBlank()) put("lease-time", leaseTime)
                put("disabled", "false")
            }
            executeJson(client, config, "PUT", "ip/dhcp-server", payload)
            Unit
        }
    }

    suspend fun updateDhcpServer(
        config: RouterConfig,
        id: String,
        name: String,
        interfaceName: String,
        addressPool: String,
        leaseTime: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("name", name)
                put("interface", interfaceName)
                if (addressPool.isNotBlank()) put("address-pool", addressPool)
                if (leaseTime.isNotBlank()) put("lease-time", leaseTime)
            }
            executeJson(client, config, "PATCH", "ip/dhcp-server/${URLEncoder.encode(id, "UTF-8")}", payload)
            Unit
        }
    }

    suspend fun toggleDhcpServerDisabled(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", "ip/dhcp-server/${URLEncoder.encode(id, "UTF-8")}", payload)
            Unit
        }
    }

    suspend fun deleteDhcpServer(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            executeJson(client, config, "DELETE", "ip/dhcp-server/${URLEncoder.encode(id, "UTF-8")}")
            Unit
        }
    }

    suspend fun addStaticDhcpLease(
        config: RouterConfig,
        address: String,
        macAddress: String,
        server: String,
        comment: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("address", address)
                put("mac-address", macAddress)
                if (server.isNotBlank()) put("server", server)
                if (comment.isNotBlank()) put("comment", comment)
            }
            executeJson(client, config, "PUT", "ip/dhcp-server/lease", payload)
            Unit
        }
    }

    suspend fun makeDhcpLeaseStatic(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().put(".id", id)
            executeJson(client, config, "POST", "ip/dhcp-server/lease/make-static", payload)
            Unit
        }
    }

    suspend fun toggleDhcpLeaseDisabled(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", "ip/dhcp-server/lease/${URLEncoder.encode(id, "UTF-8")}", payload)
            Unit
        }
    }

    suspend fun deleteDhcpLease(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            executeJson(client, config, "DELETE", "ip/dhcp-server/lease/${URLEncoder.encode(id, "UTF-8")}")
            Unit
        }
    }

    suspend fun toggleInterface(config: RouterConfig, id: String, disabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = buildClient(config)
                val payload = JSONObject().put("disabled", disabled.toString())
                executeJson(client, config, "PATCH", "interface/${URLEncoder.encode(id, "UTF-8")}", payload)
                Unit
            }
        }

    suspend fun updateInterface(
        config: RouterConfig,
        id: String,
        comment: String,
        mtu: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("comment", comment)
                if (mtu.isNotBlank()) put("mtu", mtu)
            }
            executeJson(client, config, "PATCH", "interface/${URLEncoder.encode(id, "UTF-8")}", payload)
            Unit
        }
    }

    suspend fun resetInterfaceCounters(config: RouterConfig, id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = buildClient(config)
                val payload = JSONObject().put("numbers", id)
                executeJson(client, config, "POST", "interface/reset-counters", payload)
                Unit
            }
        }

    suspend fun updateWirelessInterface(
        config: RouterConfig,
        id: String,
        ssid: String,
        frequency: String,
        comment: String,
        isWifiV7: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val encodedId = URLEncoder.encode(id, "UTF-8")
            val path = if (isWifiV7) "interface/wifi/$encodedId" else "interface/wireless/$encodedId"
            val payload = JSONObject().apply {
                if (isWifiV7) {
                    put("configuration.ssid", ssid)
                    if (frequency.isNotBlank()) put("channel.frequency", frequency)
                } else {
                    put("ssid", ssid)
                    if (frequency.isNotBlank()) put("frequency", frequency)
                }
                put("comment", comment)
            }
            executeJson(client, config, "PATCH", path, payload)
            Unit
        }
    }

    suspend fun setIdentity(config: RouterConfig, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().put("name", newName)
            executeJson(client, config, "POST", "system/identity/set", payload)
            Unit
        }
    }

    suspend fun saveBackup(config: RouterConfig, backupName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().put("name", backupName)
            executeJson(client, config, "POST", "system/backup/save", payload)
            Unit
        }
    }

    suspend fun loadRouterBackup(config: RouterConfig, backupName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().put("name", backupName)
            executeJson(client, config, "POST", "system/backup/load", payload)
            Unit
        }
    }

    suspend fun restoreBackupPayload(
        config: RouterConfig,
        payload: RouterBackupPayload,
        options: BackupRestoreOptions,
    ): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            var identityUpdated = false
            var profilesRestored = 0
            var usersRestored = 0
            var bindingsRestored = 0
            var walledRestored = 0
            var ipsRestored = 0
            var skipped = 0

            if (options.restoreIdentity && payload.routerIdentity.isNotBlank()) {
                setIdentity(config, payload.routerIdentity)
                    .onSuccess { identityUpdated = true }
                    .onFailure { skipped++ }
            }

            if (options.restoreProfiles) {
                payload.userProfiles.forEach { p ->
                    val rl = if (p.rateLimit.equals("Unlimited", true)) "" else p.rateLimit
                    val st = if (p.sessionTimeout.equals("None", true)) "" else p.sessionTimeout
                    addUserProfile(config, p.name, rl, p.sharedUsers, st)
                        .onSuccess { profilesRestored++ }
                        .onFailure { skipped++ }
                }
            }

            if (options.restoreHotspotUsers) {
                payload.hotspotUsers.forEach { u ->
                    addHotspotUser(
                        config = config,
                        name = u.name,
                        password = u.password,
                        profile = u.profile,
                        limitBytesTotal = u.limitBytesTotal,
                        comment = u.comment,
                        sessionTimeout = u.sessionTimeout,
                    ).onSuccess { usersRestored++ }
                        .onFailure { skipped++ }
                }
            }

            if (options.restoreIpBindings) {
                payload.ipBindings.forEach { b ->
                    addIpBinding(config, b.macAddress, b.address, b.type, b.comment)
                        .onSuccess { bindingsRestored++ }
                        .onFailure { skipped++ }
                }
            }

            if (options.restoreWalledGarden) {
                payload.walledGarden.forEach { w ->
                    addWalledGarden(config, w.dstHost, w.dstPort, w.action, w.comment)
                        .onSuccess { walledRestored++ }
                        .onFailure { skipped++ }
                }
            }

            if (options.restoreIpAddresses) {
                payload.ipAddresses.forEach { ip ->
                    addIpAddress(config, ip.address, ip.interfaceName, ip.comment)
                        .onSuccess { ipsRestored++ }
                        .onFailure { skipped++ }
                }
            }

            RestoreSummary(
                identityUpdated = identityUpdated,
                profilesRestored = profilesRestored,
                usersRestored = usersRestored,
                ipBindingsRestored = bindingsRestored,
                walledGardenRestored = walledRestored,
                ipAddressesRestored = ipsRestored,
                quotaConfigRestored = false,
                skippedCount = skipped,
            )
        }
    }

    suspend fun pingHost(
        config: RouterConfig,
        address: String,
        count: Int = 1,
    ): Result<JSONArray> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val payload = JSONObject().apply {
                put("address", address)
                put("count", count.coerceIn(1, 10).toString())
            }
            val raw = executeJson(client, config, "POST", "ping", payload).trim()
            if (raw.startsWith("[")) JSONArray(raw) else JSONArray()
        }
    }

    suspend fun rebootRouter(config: RouterConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            executeJson(client, config, "POST", "system/reboot", JSONObject())
            Unit
        }
    }

    suspend fun shutdownRouter(config: RouterConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            executeJson(client, config, "POST", "system/shutdown", JSONObject())
            Unit
        }
    }

    private fun parseInterfaces(arr: JSONArray): List<RouterInterface> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                RouterInterface(
                    id = o.optString(".id", "$i"),
                    name = o.optString("name", "ether$i"),
                    type = o.optString("type", "ether"),
                    running = o.optString("running") == "true",
                    disabled = o.optString("disabled") == "true",
                    macAddress = o.optString("mac-address", ""),
                    mtu = o.optString("actual-mtu").ifBlank { o.optString("mtu", "1500") },
                    comment = o.optString("comment", ""),
                    rxBytes = o.optString("rx-byte").toLongOrNull() ?: 0L,
                    txBytes = o.optString("tx-byte").toLongOrNull() ?: 0L,
                )
            )
        }
    }

    private fun parseIpAddresses(arr: JSONArray): List<IpAddressEntry> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                IpAddressEntry(
                    id = o.optString(".id", "$i"),
                    address = o.optString("address"),
                    network = o.optString("network"),
                    interfaceName = o.optString("interface"),
                    disabled = o.optString("disabled") == "true",
                    comment = o.optString("comment"),
                    dynamic = o.optString("dynamic") == "true",
                    invalid = o.optString("invalid") == "true",
                )
            )
        }
    }

    private fun parseDhcpServers(arr: JSONArray): List<DhcpServerEntry> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                DhcpServerEntry(
                    id = o.optString(".id", "$i"),
                    name = o.optString("name"),
                    interfaceName = o.optString("interface"),
                    addressPool = o.optString("address-pool"),
                    leaseTime = o.optString("lease-time", "10m"),
                    disabled = o.optString("disabled") == "true",
                    authoritative = o.optString("authoritative", "yes"),
                )
            )
        }
    }

    private fun parseDhcpLeases(arr: JSONArray): List<DhcpLeaseEntry> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val host = o.optString("host-name")
            val comment = o.optString("comment")
            add(
                DhcpLeaseEntry(
                    id = o.optString(".id", "$i"),
                    address = o.optString("address"),
                    macAddress = o.optString("mac-address"),
                    hostName = host.ifBlank { comment.ifBlank { "Perangkat LAN" } },
                    status = o.optString("status", "bound"),
                    server = o.optString("server", "dhcp1"),
                    dynamic = o.optString("dynamic", "true") == "true",
                    disabled = o.optString("disabled") == "true",
                    expiresAfter = o.optString("expires-after", ""),
                    comment = comment,
                )
            )
        }
    }

    private fun parseWireless(arr: JSONArray, isWifiV7: Boolean = false): List<WirelessInterface> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                WirelessInterface(
                    id = o.optString(".id", "$i"),
                    name = o.optString("name", "wlan$i"),
                    ssid = o.optString("ssid").ifBlank { o.optString("configuration.ssid", "MikroTik-WiFi") },
                    band = o.optString("band").ifBlank { o.optString("channel.band", "2ghz-b/g/n") },
                    frequency = o.optString("frequency").ifBlank { o.optString("channel.frequency", "auto") },
                    running = o.optString("running") == "true",
                    disabled = o.optString("disabled") == "true",
                    mode = o.optString("mode").ifBlank { o.optString("configuration.mode", "ap-bridge") },
                    securityProfile = o.optString("security-profile").ifBlank { o.optString("security", "default") },
                    channelWidth = o.optString("channel-width").ifBlank { o.optString("channel.width", "20/40mhz-XX") },
                    comment = o.optString("comment", ""),
                    isWifiV7 = isWifiV7,
                )
            )
        }
    }

    private fun parseHotspotServers(arr: JSONArray): List<HotspotServer> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                HotspotServer(
                    id = o.optString(".id", "$i"),
                    name = o.optString("name", "hotspot1"),
                    interfaceName = o.optString("interface", "bridge"),
                    addressPool = o.optString("address-pool", "hs-pool"),
                    profile = o.optString("profile", "hsprof1"),
                    disabled = o.optString("disabled") == "true",
                )
            )
        }
    }

    private fun parseHotspotUsers(arr: JSONArray, activeNames: Set<String>): List<HotspotUser> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name")
            if (name.isBlank()) continue
            val bytesIn = o.optString("bytes-in").toLongOrNull() ?: 0L
            val bytesOut = o.optString("bytes-out").toLongOrNull() ?: 0L
            val limitBytes = o.optString("limit-bytes-total").toLongOrNull() ?: 0L
            val used = bytesIn + bytesOut
            val disabled = o.optString("disabled") == "true"
            val expired = (limitBytes > 0L && used >= limitBytes) || disabled
            add(
                HotspotUser(
                    id = o.optString(".id", "$i"),
                    name = name,
                    password = o.optString("password"),
                    profile = o.optString("profile", "default"),
                    usedBytes = used,
                    limitBytesTotal = limitBytes,
                    uptime = o.optString("uptime", "0s"),
                    sessionTimeout = o.optString("limit-uptime", ""),
                    disabled = disabled,
                    isActive = name in activeNames && !disabled,
                    isExpired = expired,
                    comment = o.optString("comment"),
                )
            )
        }
    }

    private fun parseActiveSessions(arr: JSONArray): List<HotspotActiveSession> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                HotspotActiveSession(
                    id = o.optString(".id", "$i"),
                    user = o.optString("user"),
                    address = o.optString("address"),
                    macAddress = o.optString("mac-address"),
                    uptime = o.optString("uptime", "0s"),
                    bytesIn = o.optString("bytes-in").toLongOrNull() ?: 0L,
                    bytesOut = o.optString("bytes-out").toLongOrNull() ?: 0L,
                    server = o.optString("server", "hotspot1"),
                )
            )
        }
    }

    private fun parseUserProfiles(arr: JSONArray): List<HotspotUserProfile> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                HotspotUserProfile(
                    id = o.optString(".id", "$i"),
                    name = o.optString("name", "default"),
                    sharedUsers = o.optString("shared-users", "1"),
                    rateLimit = o.optString("rate-limit").ifBlank { "Unlimited" },
                    sessionTimeout = o.optString("session-timeout").ifBlank { "None" },
                )
            )
        }
    }

    private fun parseIpBindings(arr: JSONArray): List<IpBindingEntry> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                IpBindingEntry(
                    id = o.optString(".id", "$i"),
                    macAddress = o.optString("mac-address"),
                    address = o.optString("address"),
                    toAddress = o.optString("to-address"),
                    type = o.optString("type", "bypassed"),
                    comment = o.optString("comment"),
                    disabled = o.optString("disabled") == "true",
                    server = o.optString("server").ifBlank { "all" },
                )
            )
        }
    }

    private fun parseWalledGarden(arr: JSONArray): List<WalledGardenEntry> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                WalledGardenEntry(
                    id = o.optString(".id", "$i"),
                    dstHost = o.optString("dst-host"),
                    dstPort = o.optString("dst-port"),
                    action = o.optString("action", "allow"),
                    comment = o.optString("comment"),
                    disabled = o.optString("disabled") == "true",
                )
            )
        }
    }

    suspend fun fetchSystemLogs(config: RouterConfig): Result<List<RouterLogEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = buildClient(config)
            val raw = executeGet(client, config, "log")
            parseSystemLogs(JSONArray(raw))
        }
    }

    private fun parseSystemLogs(arr: JSONArray): List<RouterLogEntry> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val topics = o.optString("topics", "system,info")
            val message = o.optString("message").ifBlank { continue }
            val time = o.optString("time", "--:--:--")
            val lowerTopics = topics.lowercase()
            val severity = when {
                lowerTopics.contains("error") || lowerTopics.contains("critical") -> LogSeverity.ERROR
                lowerTopics.contains("warning") -> LogSeverity.WARNING
                else -> LogSeverity.INFO
            }
            val primaryTopic = topics.split(",").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "System"
            add(
                RouterLogEntry(
                    id = o.optString(".id", "log_$i"),
                    title = primaryTopic,
                    detail = message,
                    time = time,
                    topics = topics,
                    severity = severity,
                    isAlert = severity == LogSeverity.ERROR,
                )
            )
        }
    }.asReversed().take(120)

    private fun parseSystemUsers(arr: JSONArray): List<RouterSystemUser> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                RouterSystemUser(
                    id = o.optString(".id", "$i"),
                    name = o.optString("name"),
                    group = o.optString("group", "read"),
                    lastLoggedIn = o.optString("last-logged-in", "-"),
                    disabled = o.optString("disabled") == "true",
                )
            )
        }
    }
}
