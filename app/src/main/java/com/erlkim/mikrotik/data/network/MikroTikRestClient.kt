package com.erlkim.mikrotik.data.network

import android.annotation.SuppressLint
import com.erlkim.mikrotik.data.backup.BackupRestoreOptions
import com.erlkim.mikrotik.data.backup.RestoreSummary
import com.erlkim.mikrotik.data.backup.RouterBackupPayload
import com.erlkim.mikrotik.data.model.DhcpLeaseEntry
import com.erlkim.mikrotik.data.model.DhcpServerEntry
import com.erlkim.mikrotik.data.model.HotspotActiveSession
import com.erlkim.mikrotik.data.model.HotspotServer
import com.erlkim.mikrotik.data.model.HotspotUser
import com.erlkim.mikrotik.data.model.HotspotUserProfile
import com.erlkim.mikrotik.data.model.IpAddressEntry
import com.erlkim.mikrotik.data.model.IpBindingEntry
import com.erlkim.mikrotik.data.model.LogSeverity
import com.erlkim.mikrotik.data.model.RouterConfig
import com.erlkim.mikrotik.data.model.RouterInterface
import com.erlkim.mikrotik.data.model.RouterLogEntry
import com.erlkim.mikrotik.data.model.RouterResource
import com.erlkim.mikrotik.data.model.RouterSystemUser
import com.erlkim.mikrotik.data.model.TrafficSample
import com.erlkim.mikrotik.data.model.WalledGardenEntry
import com.erlkim.mikrotik.data.model.WirelessInterface
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

data class OperationalSnapshot(
    val interfaces: List<RouterInterface>,
    val hotspotUsers: List<HotspotUser>,
    val activeSessions: List<HotspotActiveSession>,
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
 * Uses a shared OkHttpClient ConnectionPool & Dispatcher for HTTP keep-alive reuse,
 * parallel snapshot fetching, and actionable RouterOS diagnostics.
 */
class MikroTikRestClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val baseDispatcher = Dispatcher().apply {
        maxRequests = 16
        maxRequestsPerHost = 6
    }

    private val sharedConnectionPool = ConnectionPool(6, 45, TimeUnit.SECONDS)

    private val defaultHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(baseDispatcher)
            .connectionPool(sharedConnectionPool)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val selfSignedHttpsClient: OkHttpClient by lazy {
        val builder = defaultHttpClient.newBuilder()
        configureLanSelfSignedSsl(builder)
        builder.build()
    }

    private fun clientFor(config: RouterConfig): OkHttpClient {
        return if (config.useHttps && config.trustSelfSigned) {
            selfSignedHttpsClient
        } else {
            defaultHttpClient
        }
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

    internal fun parseNormalizedHostAndPort(config: RouterConfig): Pair<String, Int> {
        var raw = config.host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .trim()
        var effectivePort = config.port.coerceIn(1, 65535)
        // Extract trailing :port if user pasted host:port for IPv4/domain
        if (!raw.startsWith("[") && raw.count { it == ':' } == 1) {
            val idx = raw.lastIndexOf(':')
            val maybePort = raw.substring(idx + 1).toIntOrNull()
            if (maybePort != null && maybePort in 1..65535) {
                raw = raw.substring(0, idx)
                effectivePort = maybePort
            }
        }
        return raw to effectivePort
    }

    internal fun baseUrl(config: RouterConfig): String {
        val scheme = if (config.useHttps) "https" else "http"
        val (cleanHost, effectivePort) = parseNormalizedHostAndPort(config)
        val formattedHost = if (cleanHost.contains(':') && !cleanHost.startsWith("[")) "[$cleanHost]" else cleanHost
        return "$scheme://$formattedHost:$effectivePort/rest"
    }

    internal fun buildEndpointUrl(config: RouterConfig, vararg pathSegments: String): HttpUrl {
        val base = baseUrl(config).toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Alamat router tidak valid: '${config.host}'")
        val builder = base.newBuilder()
        for (segment in pathSegments) {
            if (segment.isNotEmpty()) {
                builder.addPathSegment(segment)
            }
        }
        return builder.build()
    }

    private fun authHeader(config: RouterConfig): String =
        Credentials.basic(config.username, config.password)

    private fun executeGet(client: OkHttpClient, config: RouterConfig, vararg pathSegments: String): String {
        val url = buildEndpointUrl(config, *pathSegments)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(config))
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                checkHttpSuccess(response.code, body, config)
                return body
            }
        } catch (t: Throwable) {
            throw wrapConnectionError(config, t)
        }
    }

    private fun executeJson(
        client: OkHttpClient,
        config: RouterConfig,
        method: String,
        payload: JSONObject? = null,
        vararg pathSegments: String,
    ): String {
        val body = payload?.toString()?.toRequestBody(jsonMediaType)
            ?: if (method == "POST" || method == "PUT" || method == "PATCH") {
                "{}".toRequestBody(jsonMediaType)
            } else {
                null
            }

        val url = buildEndpointUrl(config, *pathSegments)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(config))
            .header("Accept", "application/json")
            .method(method, body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                checkHttpSuccess(response.code, respBody, config)
                return respBody
            }
        } catch (t: Throwable) {
            throw wrapConnectionError(config, t)
        }
    }

    private fun checkHttpSuccess(code: Int, body: String, config: RouterConfig) {
        when {
            code == 401 -> throw IOException("Autentikasi ditolak (401). Periksa username/password akun RouterOS.")
            code == 403 -> throw IOException("Akses ditolak (403). Pastikan grup user memiliki policy 'rest-api', 'read', dan 'write'.")
            code == 404 -> throw IOException("Endpoint REST API tidak ditemukan (404). Pastikan RouterOS versi v7.1+.")
            code !in 200..299 -> throw IOException("HTTP $code: ${extractErrorMessage(body)}")
        }
    }

    internal fun wrapConnectionError(config: RouterConfig, t: Throwable): IOException {
        if (t is IOException && (
                t.message?.startsWith("Autentikasi ditolak") == true ||
                    t.message?.startsWith("Akses ditolak") == true ||
                    t.message?.startsWith("Endpoint REST API") == true ||
                    t.message?.startsWith("HTTP ") == true
                )
        ) {
            return t
        }
        val (_, effectivePort) = parseNormalizedHostAndPort(config)
        val msg = when {
            effectivePort == 8291 || effectivePort == 8728 || effectivePort == 8729 ->
                "Port $effectivePort adalah port WinBox/API biner, bukan REST API. Gunakan port 80 (HTTP www) atau 443 (HTTPS www-ssl)."
            t is SSLException ->
                "Handshake SSL/TLS gagal (${t.message ?: "sertifikat"}). Aktifkan 'Terima Sertifikat Self-Signed' atau matikan HTTPS jika router hanya mengaktifkan service 'www'."
            t is UnknownHostException ->
                "Host '${config.host}' tidak dapat di-resolve. Periksa alamat IP router dan koneksi Wi-Fi/LAN."
            t is SocketTimeoutException ->
                "Timeout menghubungi ${config.displayAddress}. Pastikan HP terhubung ke Wi-Fi/LAN MikroTik & firewall mengizinkan port $effectivePort."
            t is ConnectException ->
                "Koneksi ditolak oleh ${config.displayAddress}. Aktifkan '/ip service enable ${if (config.useHttps) "www-ssl" else "www"}' di RouterOS."
            else ->
                t.message ?: "Gagal menghubungi RouterOS REST API (${config.displayAddress})."
        }
        return IOException(msg, t)
    }

    private fun extractErrorMessage(body: String): String {
        return runCatching {
            val obj = JSONObject(body)
            obj.optString("detail").ifBlank { obj.optString("message").ifBlank { body.take(120) } }
        }.getOrDefault(body.take(120))
    }

    private fun parseJsonArraySafe(raw: String): JSONArray {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("[")) {
            runCatching { JSONArray(trimmed) }.getOrDefault(JSONArray())
        } else {
            JSONArray()
        }
    }

    private fun safeGetArray(client: OkHttpClient, config: RouterConfig, vararg pathSegments: String): JSONArray {
        return runCatching {
            parseJsonArraySafe(executeGet(client, config, *pathSegments))
        }.getOrDefault(JSONArray())
    }

    private fun safeGetObject(client: OkHttpClient, config: RouterConfig, vararg pathSegments: String): JSONObject {
        return runCatching {
            val raw = executeGet(client, config, *pathSegments).trim()
            if (raw.startsWith("{")) JSONObject(raw) else JSONObject()
        }.getOrDefault(JSONObject())
    }

    suspend fun testConnection(config: RouterConfig): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        runCatching {
            val startNs = System.nanoTime()
            val client = clientFor(config)
            val rawResource = executeGet(client, config, "system", "resource")
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            val resObj = JSONObject(rawResource)
            val identityObj = safeGetObject(client, config, "system", "identity")
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
            val client = clientFor(config)
            // Primary connectivity & auth gate: /rest/system/resource
            val resObj = JSONObject(executeGet(client, config, "system", "resource"))

            coroutineScope {
                val identityDeferred = async { safeGetObject(client, config, "system", "identity") }
                val clockDeferred = async { safeGetObject(client, config, "system", "clock") }
                val interfacesDeferred = async { parseInterfaces(safeGetArray(client, config, "interface")) }
                val ipAddressesDeferred = async { parseIpAddresses(safeGetArray(client, config, "ip", "address")) }
                val dhcpServersDeferred = async { parseDhcpServers(safeGetArray(client, config, "ip", "dhcp-server")) }
                val dhcpLeasesDeferred = async { parseDhcpLeases(safeGetArray(client, config, "ip", "dhcp-server", "lease")) }
                val legacyWirelessDeferred = async { safeGetArray(client, config, "interface", "wireless") }
                val wifiV7Deferred = async { safeGetArray(client, config, "interface", "wifi") }
                val legacyRegDeferred = async { safeGetArray(client, config, "interface", "wireless", "registration-table") }
                val wifiV7RegDeferred = async { safeGetArray(client, config, "interface", "wifi", "registration-table") }
                val hotspotServersDeferred = async { parseHotspotServers(safeGetArray(client, config, "ip", "hotspot")) }
                val activeSessionsDeferred = async { parseActiveSessions(safeGetArray(client, config, "ip", "hotspot", "active")) }
                val hotspotUsersArrDeferred = async { safeGetArray(client, config, "ip", "hotspot", "user") }
                val userProfilesDeferred = async { parseUserProfiles(safeGetArray(client, config, "ip", "hotspot", "user", "profile")) }
                val ipBindingsDeferred = async { parseIpBindings(safeGetArray(client, config, "ip", "hotspot", "ip-binding")) }
                val walledGardenDeferred = async { parseWalledGarden(safeGetArray(client, config, "ip", "hotspot", "walled-garden")) }
                val systemUsersDeferred = async { parseSystemUsers(safeGetArray(client, config, "user")) }
                val systemLogsDeferred = async { parseSystemLogs(safeGetArray(client, config, "log")) }

                val identityObj = identityDeferred.await()
                val clockObj = clockDeferred.await()
                val resource = buildRouterResource(resObj, identityObj, clockObj)

                val legacyWirelessArr = legacyWirelessDeferred.await()
                val isWifiV7 = legacyWirelessArr.length() == 0
                val wirelessArr = if (!isWifiV7) legacyWirelessArr else wifiV7Deferred.await()
                val regArr = if (!isWifiV7) legacyRegDeferred.await() else wifiV7RegDeferred.await()
                val wirelessInterfaces = parseWireless(wirelessArr, isWifiV7, countRegistrationClients(regArr))

                val activeSessions = activeSessionsDeferred.await()
                val activeUsernames = activeSessions.mapTo(HashSet()) { it.user }
                val hotspotUsers = parseHotspotUsers(hotspotUsersArrDeferred.await(), activeUsernames)

                RouterSnapshot(
                    resource = resource,
                    interfaces = interfacesDeferred.await(),
                    ipAddresses = ipAddressesDeferred.await(),
                    dhcpServers = dhcpServersDeferred.await(),
                    dhcpLeases = dhcpLeasesDeferred.await(),
                    wirelessInterfaces = wirelessInterfaces,
                    hotspotServers = hotspotServersDeferred.await(),
                    hotspotUsers = hotspotUsers,
                    activeSessions = activeSessions,
                    userProfiles = userProfilesDeferred.await(),
                    ipBindings = ipBindingsDeferred.await(),
                    walledGarden = walledGardenDeferred.await(),
                    systemUsers = systemUsersDeferred.await(),
                    systemLogs = systemLogsDeferred.await(),
                )
            }
        }
    }

    /**
     * Lightweight operational sync (interfaces, active hotspot sessions, hotspot users)
     * run concurrently for background polling without hitting static tables or full log buffers.
     */
    suspend fun fetchOperationalSnapshot(config: RouterConfig): Result<OperationalSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            coroutineScope {
                val interfacesDeferred = async {
                    parseInterfaces(parseJsonArraySafe(executeGet(client, config, "interface")))
                }
                val activeSessionsDeferred = async {
                    parseActiveSessions(safeGetArray(client, config, "ip", "hotspot", "active"))
                }
                val hotspotUsersArrDeferred = async {
                    safeGetArray(client, config, "ip", "hotspot", "user")
                }

                val activeSessions = activeSessionsDeferred.await()
                val activeUsernames = activeSessions.mapTo(HashSet()) { it.user }
                OperationalSnapshot(
                    interfaces = interfacesDeferred.await(),
                    hotspotUsers = parseHotspotUsers(hotspotUsersArrDeferred.await(), activeUsernames),
                    activeSessions = activeSessions,
                )
            }
        }
    }

    suspend fun pollTrafficAndResource(
        config: RouterConfig,
        interfaceName: String,
    ): Result<Pair<RouterResource, TrafficSample>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            coroutineScope {
                val resDeferred = async {
                    JSONObject(executeGet(client, config, "system", "resource"))
                }
                val trafficDeferred = async {
                    if (interfaceName.isBlank()) {
                        0f to 0f
                    } else {
                        val payload = JSONObject().apply {
                            put("interface", interfaceName)
                            put("once", true)
                            put(".proplist", JSONArray(listOf("name", "rx-bits-per-second", "tx-bits-per-second")))
                        }
                        runCatching {
                            val trafficRaw = executeJson(client, config, "POST", payload, "interface", "monitor-traffic")
                            val arr = parseJsonArraySafe(trafficRaw)
                            if (arr.length() > 0) {
                                val item = arr.getJSONObject(0)
                                val rxBps = item.optString("rx-bits-per-second").toDoubleOrNull() ?: 0.0
                                val txBps = item.optString("tx-bits-per-second").toDoubleOrNull() ?: 0.0
                                (rxBps / 1_000_000.0).toFloat() to (txBps / 1_000_000.0).toFloat()
                            } else {
                                0f to 0f
                            }
                        }.getOrDefault(0f to 0f)
                    }
                }

                val resObj = resDeferred.await()
                val (rxMbps, txMbps) = trafficDeferred.await()
                val resource = buildRouterResource(resObj, identityObj = null, clockObj = null)
                resource to TrafficSample(downloadMbps = rxMbps, uploadMbps = txMbps)
            }
        }
    }

    private fun buildRouterResource(
        resObj: JSONObject,
        identityObj: JSONObject?,
        clockObj: JSONObject?,
    ): RouterResource {
        val totalMem = resObj.optString("total-memory").toLongOrNull() ?: 0L
        val freeMem = resObj.optString("free-memory").toLongOrNull() ?: 0L
        val totalHdd = resObj.optString("total-hdd-space").toLongOrNull() ?: 0L
        val freeHdd = resObj.optString("free-hdd-space").toLongOrNull() ?: 0L
        val ramPct = if (totalMem > 0) (((totalMem - freeMem) * 100) / totalMem).toInt().coerceIn(0, 100) else 0
        val hddPct = if (totalHdd > 0) (((totalHdd - freeHdd) * 100) / totalHdd).toInt().coerceIn(0, 100) else 0
        val board = resObj.optString("board-name", "MikroTik")
        val rawVersion = resObj.optString("version", "7.x")
        val resolvedIdentity = identityObj?.optString("name")?.takeIf { it.isNotBlank() } ?: ""

        return RouterResource(
            identity = resolvedIdentity,
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
            clockTime = clockObj?.optString("time", "--:--:--") ?: "--:--:--",
            clockDate = clockObj?.optString("date", "--/--/----") ?: "--/--/----",
            timeZoneName = clockObj?.optString("time-zone-name", "Asia/Jakarta") ?: "",
        )
    }

    suspend fun fetchInterfaces(config: RouterConfig): Result<List<RouterInterface>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            parseInterfaces(parseJsonArraySafe(executeGet(client, config, "interface")))
        }
    }

    suspend fun fetchIpAddresses(config: RouterConfig): Result<List<IpAddressEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            parseIpAddresses(parseJsonArraySafe(executeGet(client, config, "ip", "address")))
        }
    }

    suspend fun fetchDhcpData(
        config: RouterConfig,
    ): Result<Pair<List<DhcpServerEntry>, List<DhcpLeaseEntry>>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            coroutineScope {
                val serversDeferred = async { parseJsonArraySafe(executeGet(client, config, "ip", "dhcp-server")) }
                val leasesDeferred = async { safeGetArray(client, config, "ip", "dhcp-server", "lease") }
                parseDhcpServers(serversDeferred.await()) to parseDhcpLeases(leasesDeferred.await())
            }
        }
    }

    suspend fun fetchSystemHealth(config: RouterConfig): Result<RouterResource> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val resObj = JSONObject(executeGet(client, config, "system", "resource"))
            coroutineScope {
                val identityDeferred = async { safeGetObject(client, config, "system", "identity") }
                val clockDeferred = async { safeGetObject(client, config, "system", "clock") }
                buildRouterResource(resObj, identityDeferred.await(), clockDeferred.await())
            }
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
            val client = clientFor(config)
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
            executeJson(client, config, "PUT", payload, "ip", "hotspot", "user")
            Unit
        }
    }

    suspend fun updateHotspotUserSessionTimeout(
        config: RouterConfig,
        userId: String,
        sessionTimeout: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val normalized = sessionTimeout.trim().ifBlank { "0s" }
            val payload = JSONObject().put("limit-uptime", normalized)
            executeJson(client, config, "PATCH", payload, "ip", "hotspot", "user", userId)
            Unit
        }
    }

    suspend fun updateHotspotUserPassword(
        config: RouterConfig,
        userId: String,
        newPassword: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put("password", newPassword)
            executeJson(client, config, "PATCH", payload, "ip", "hotspot", "user", userId)
            Unit
        }
    }

    suspend fun toggleHotspotUserDisabled(
        config: RouterConfig,
        userId: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", payload, "ip", "hotspot", "user", userId)
            Unit
        }
    }

    suspend fun deleteHotspotUser(config: RouterConfig, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            executeJson(client, config, "DELETE", null, "ip", "hotspot", "user", userId)
            Unit
        }
    }

    suspend fun deleteHotspotUsersBulk(
        config: RouterConfig,
        userIds: List<String>,
        concurrency: Int = 4,
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(concurrency.coerceIn(1, 8))
        val results = coroutineScope {
            userIds.map { id ->
                async {
                    semaphore.withPermit {
                        deleteHotspotUser(config, id).isSuccess
                    }
                }
            }.awaitAll()
        }
        val deleted = results.count { it }
        deleted to (results.size - deleted)
    }

    suspend fun kickActiveSession(config: RouterConfig, sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            executeJson(client, config, "DELETE", null, "ip", "hotspot", "active", sessionId)
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
            val client = clientFor(config)
            val payload = JSONObject().apply {
                put("dst-host", dstHost)
                if (dstPort.isNotBlank()) put("dst-port", dstPort)
                put("action", action)
                if (comment.isNotBlank()) put("comment", comment)
            }
            executeJson(client, config, "PUT", payload, "ip", "hotspot", "walled-garden")
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
            val client = clientFor(config)
            val payload = JSONObject().apply {
                put("dst-host", dstHost)
                if (dstPort.isNotBlank()) put("dst-port", dstPort.trim())
                put("action", action)
                put("comment", comment)
            }
            executeJson(client, config, "PATCH", payload, "ip", "hotspot", "walled-garden", id)
            Unit
        }
    }

    suspend fun toggleWalledGardenDisabled(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", payload, "ip", "hotspot", "walled-garden", id)
            Unit
        }
    }

    suspend fun deleteWalledGarden(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            executeJson(client, config, "DELETE", null, "ip", "hotspot", "walled-garden", id)
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
            val client = clientFor(config)
            val payload = JSONObject().apply {
                put("name", name)
                put("interface", interfaceName)
                if (addressPool.isNotBlank()) put("address-pool", addressPool)
                if (profile.isNotBlank()) put("profile", profile)
                put("disabled", "false")
            }
            executeJson(client, config, "PUT", payload, "ip", "hotspot")
            Unit
        }
    }

    suspend fun toggleHotspotServerDisabled(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", payload, "ip", "hotspot", id)
            Unit
        }
    }

    suspend fun deleteHotspotServer(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            executeJson(client, config, "DELETE", null, "ip", "hotspot", id)
            Unit
        }
    }

    suspend fun fetchIpBindings(
        config: RouterConfig,
    ): Result<List<IpBindingEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            parseIpBindings(parseJsonArraySafe(executeGet(client, config, "ip", "hotspot", "ip-binding")))
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
            val client = clientFor(config)
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
            executeJson(client, config, "PUT", payload, "ip", "hotspot", "ip-binding")
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
            val client = clientFor(config)
            val payload = JSONObject().apply {
                if (macAddress.isNotBlank()) put("mac-address", macAddress.trim())
                if (address.isNotBlank()) put("address", address.trim())
                if (toAddress.isNotBlank()) put("to-address", toAddress.trim())
                put("server", server.ifBlank { "all" })
                put("type", type)
                put("comment", comment)
            }
            executeJson(client, config, "PATCH", payload, "ip", "hotspot", "ip-binding", id)
            Unit
        }
    }

    suspend fun toggleIpBindingDisabled(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", payload, "ip", "hotspot", "ip-binding", id)
            Unit
        }
    }

    suspend fun deleteIpBinding(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            executeJson(client, config, "DELETE", null, "ip", "hotspot", "ip-binding", id)
            Unit
        }
    }

    suspend fun fetchHotspotUserProfiles(
        config: RouterConfig,
    ): Result<List<HotspotUserProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            parseUserProfiles(parseJsonArraySafe(executeGet(client, config, "ip", "hotspot", "user", "profile")))
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
            val client = clientFor(config)
            val payload = JSONObject().apply {
                put("name", name)
                if (rateLimit.isNotBlank()) put("rate-limit", rateLimit)
                if (sharedUsers.isNotBlank()) put("shared-users", sharedUsers)
                if (sessionTimeout.isNotBlank()) put("session-timeout", sessionTimeout)
            }
            executeJson(client, config, "PUT", payload, "ip", "hotspot", "user", "profile")
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
            val client = clientFor(config)
            val normalizedRateLimit = if (rateLimit.equals("Unlimited", true) || rateLimit.isBlank()) "" else rateLimit.trim()
            val normalizedTimeout = if (sessionTimeout.equals("None", true) || sessionTimeout.isBlank()) "0s" else sessionTimeout.trim()
            val payload = JSONObject().apply {
                put("name", name)
                put("rate-limit", normalizedRateLimit)
                put("shared-users", sharedUsers.ifBlank { "1" })
                put("session-timeout", normalizedTimeout)
            }
            executeJson(client, config, "PATCH", payload, "ip", "hotspot", "user", "profile", id)
            Unit
        }
    }

    suspend fun deleteUserProfile(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            executeJson(client, config, "DELETE", null, "ip", "hotspot", "user", "profile", id)
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
            val client = clientFor(config)
            val payload = JSONObject().apply {
                put("address", address)
                put("interface", interfaceName)
                if (comment.isNotBlank()) put("comment", comment)
            }
            executeJson(client, config, "PUT", payload, "ip", "address")
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
            val client = clientFor(config)
            val payload = JSONObject().apply {
                put("address", address)
                put("interface", interfaceName)
                put("comment", comment)
            }
            executeJson(client, config, "PATCH", payload, "ip", "address", id)
            Unit
        }
    }

    suspend fun toggleIpAddressDisabled(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", payload, "ip", "address", id)
            Unit
        }
    }

    suspend fun deleteIpAddress(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            executeJson(client, config, "DELETE", null, "ip", "address", id)
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
            val client = clientFor(config)
            val payload = JSONObject().apply {
                put("name", name)
                put("interface", interfaceName)
                if (addressPool.isNotBlank()) put("address-pool", addressPool)
                if (leaseTime.isNotBlank()) put("lease-time", leaseTime)
                put("disabled", "false")
            }
            executeJson(client, config, "PUT", payload, "ip", "dhcp-server")
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
            val client = clientFor(config)
            val payload = JSONObject().apply {
                put("name", name)
                put("interface", interfaceName)
                if (addressPool.isNotBlank()) put("address-pool", addressPool)
                if (leaseTime.isNotBlank()) put("lease-time", leaseTime)
            }
            executeJson(client, config, "PATCH", payload, "ip", "dhcp-server", id)
            Unit
        }
    }

    suspend fun toggleDhcpServerDisabled(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", payload, "ip", "dhcp-server", id)
            Unit
        }
    }

    suspend fun deleteDhcpServer(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            executeJson(client, config, "DELETE", null, "ip", "dhcp-server", id)
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
            val client = clientFor(config)
            val payload = JSONObject().apply {
                put("address", address)
                put("mac-address", macAddress)
                if (server.isNotBlank()) put("server", server)
                if (comment.isNotBlank()) put("comment", comment)
            }
            executeJson(client, config, "PUT", payload, "ip", "dhcp-server", "lease")
            Unit
        }
    }

    suspend fun makeDhcpLeaseStatic(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put(".id", id)
            executeJson(client, config, "POST", payload, "ip", "dhcp-server", "lease", "make-static")
            Unit
        }
    }

    suspend fun toggleDhcpLeaseDisabled(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            executeJson(client, config, "PATCH", payload, "ip", "dhcp-server", "lease", id)
            Unit
        }
    }

    suspend fun deleteDhcpLease(config: RouterConfig, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            executeJson(client, config, "DELETE", null, "ip", "dhcp-server", "lease", id)
            Unit
        }
    }

    suspend fun toggleInterface(config: RouterConfig, id: String, disabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = clientFor(config)
                val payload = JSONObject().put("disabled", disabled.toString())
                executeJson(client, config, "PATCH", payload, "interface", id)
                Unit
            }
        }

    suspend fun toggleWirelessInterface(
        config: RouterConfig,
        id: String,
        disabled: Boolean,
        isWifiV7: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put("disabled", disabled.toString())
            val radioSegment = if (isWifiV7) "wifi" else "wireless"
            executeJson(client, config, "PATCH", payload, "interface", radioSegment, id)
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
            val client = clientFor(config)
            val payload = JSONObject().apply {
                put("comment", comment)
                if (mtu.isNotBlank()) put("mtu", mtu)
            }
            executeJson(client, config, "PATCH", payload, "interface", id)
            Unit
        }
    }

    suspend fun resetInterfaceCounters(config: RouterConfig, id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = clientFor(config)
                val payload = JSONObject().put("numbers", id)
                executeJson(client, config, "POST", payload, "interface", "reset-counters")
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
            val client = clientFor(config)
            val radioSegment = if (isWifiV7) "wifi" else "wireless"
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
            executeJson(client, config, "PATCH", payload, "interface", radioSegment, id)
            Unit
        }
    }

    suspend fun setIdentity(config: RouterConfig, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put("name", newName)
            executeJson(client, config, "POST", payload, "system", "identity", "set")
            Unit
        }
    }

    suspend fun saveBackup(config: RouterConfig, backupName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put("name", backupName)
            executeJson(client, config, "POST", payload, "system", "backup", "save")
            Unit
        }
    }

    suspend fun loadRouterBackup(config: RouterConfig, backupName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            val payload = JSONObject().put("name", backupName)
            executeJson(client, config, "POST", payload, "system", "backup", "load")
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
            val client = clientFor(config)
            val payload = JSONObject().apply {
                put("address", address)
                put("count", count.coerceIn(1, 10).toString())
            }
            val raw = executeJson(client, config, "POST", payload, "ping")
            parseJsonArraySafe(raw)
        }
    }

    suspend fun rebootRouter(config: RouterConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            executeJson(client, config, "POST", JSONObject(), "system", "reboot")
            Unit
        }
    }

    suspend fun shutdownRouter(config: RouterConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientFor(config)
            executeJson(client, config, "POST", JSONObject(), "system", "shutdown")
            Unit
        }
    }

    private fun countRegistrationClients(arr: JSONArray): Map<String, Int> {
        if (arr.length() == 0) return emptyMap()
        val counts = HashMap<String, Int>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val iface = o.optString("interface").trim()
            if (iface.isNotEmpty()) {
                counts[iface] = (counts[iface] ?: 0) + 1
            }
        }
        return counts
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

    private fun parseWireless(
        arr: JSONArray,
        isWifiV7: Boolean = false,
        clientsByInterface: Map<String, Int> = emptyMap(),
    ): List<WirelessInterface> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val ifaceName = o.optString("name", "wlan$i")
            add(
                WirelessInterface(
                    id = o.optString(".id", "$i"),
                    name = ifaceName,
                    ssid = o.optString("ssid").ifBlank { o.optString("configuration.ssid", "MikroTik-WiFi") },
                    band = o.optString("band").ifBlank { o.optString("channel.band", "2ghz-b/g/n") },
                    frequency = o.optString("frequency").ifBlank { o.optString("channel.frequency", "auto") },
                    running = o.optString("running") == "true",
                    disabled = o.optString("disabled") == "true",
                    connectedClients = clientsByInterface[ifaceName] ?: 0,
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
            val client = clientFor(config)
            val raw = executeGet(client, config, "log")
            parseSystemLogs(parseJsonArraySafe(raw))
        }
    }

    private fun parseSystemLogs(arr: JSONArray): List<RouterLogEntry> = buildList {
        val startIdx = (arr.length() - 120).coerceAtLeast(0)
        for (i in (arr.length() - 1) downTo startIdx) {
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
    }

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
