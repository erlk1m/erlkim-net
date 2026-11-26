package com.erlkim.mikrotik

import com.erlkim.mikrotik.data.model.RouterConfig
import com.erlkim.mikrotik.data.network.MikroTikRestClient
import com.sun.net.httpserver.HttpServer
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MikroTikRestClientTest {

    private val client = MikroTikRestClient()

    @Test
    fun `parseNormalizedHostAndPort extracts embedded port and strips scheme`() {
        val cfg = RouterConfig(host = "https://192.168.88.1:8443/rest", port = 80, useHttps = true)
        val (host, port) = client.parseNormalizedHostAndPort(cfg)
        assertEquals("192.168.88.1", host)
        assertEquals(8443, port)
        assertEquals("https://192.168.88.1:8443/rest", client.baseUrl(cfg))
    }

    @Test
    fun `buildEndpointUrl encodes special characters as RFC 3986 path segments`() {
        val cfg = RouterConfig(host = "192.168.88.1", port = 80)
        val url = client.buildEndpointUrl(cfg, "ip", "hotspot", "user", "user test*1").toString()
        assertEquals("http://192.168.88.1/rest/ip/hotspot/user/user%20test*1", url)
    }

    @Test
    fun `wrapConnectionError identifies WinBox and native API ports`() {
        val cfg = RouterConfig(host = "192.168.88.1", port = 8291)
        val err = client.wrapConnectionError(cfg, ConnectException("Connection refused"))
        assertTrue(err.message!!.contains("8291"))
        assertTrue(err.message!!.contains("REST API"))
    }

    @Test
    fun `wrapConnectionError gives actionable guidance for SSL, timeout, and unknown host`() {
        val sslErr = client.wrapConnectionError(
            RouterConfig(host = "192.168.88.1", port = 443, useHttps = true),
            SSLException("handshake failed"),
        )
        assertTrue(sslErr.message!!.contains("Self-Signed"))

        val timeoutErr = client.wrapConnectionError(
            RouterConfig(host = "10.10.10.1", port = 80),
            SocketTimeoutException("timeout"),
        )
        assertTrue(timeoutErr.message!!.contains("Timeout"))

        val dnsErr = client.wrapConnectionError(
            RouterConfig(host = "router.local", port = 80),
            UnknownHostException("router.local"),
        )
        assertTrue(dnsErr.message!!.contains("resolve"))
    }

    @Test
    fun `fetchSnapshot and pollTrafficAndResource parse RouterOS v7 JSON concurrently`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/") { exchange ->
            val path = exchange.requestURI.path
            val (status, body) = when (path) {
                "/rest/system/resource" -> 200 to
                    """{"board-name":"hAP ax3","version":"7.15.3","uptime":"3d02:10:05","cpu-load":"14","total-memory":"1073741824","free-memory":"671088640","total-hdd-space":"134217728","free-hdd-space":"100663296"}"""
                "/rest/system/identity" -> 200 to """{"name":"MikroTik-Pusat"}"""
                "/rest/system/clock" -> 200 to """{"time":"09:30:00","date":"nov/26/2026","time-zone-name":"Asia/Jakarta"}"""
                "/rest/interface" -> 200 to
                    """[{".id":"*1","name":"ether1-WAN","type":"ether","running":"true","disabled":"false","mac-address":"D4:01:C3:00:00:01","actual-mtu":"1500"}]"""
                "/rest/interface/wireless" -> 400 to """{"detail":"no such command"}"""
                "/rest/interface/wifi" -> 200 to
                    """[{".id":"*A","name":"wifi1","configuration.ssid":"Erlkim-5G","channel.frequency":"5180","running":"true","disabled":"false"}]"""
                "/rest/interface/wifi/registration-table" -> 200 to
                    """[{"interface":"wifi1"},{"interface":"wifi1"}]"""
                "/rest/ip/hotspot/active" -> 200 to
                    """[{".id":"*10","user":"budi","address":"10.10.0.12","mac-address":"AA:BB:CC:DD:EE:FF","uptime":"10m","bytes-in":"1048576","bytes-out":"524288"}]"""
                "/rest/ip/hotspot/user" -> 200 to
                    """[{".id":"*20","name":"budi","password":"123","profile":"aktif","bytes-in":"1048576","bytes-out":"524288","disabled":"false"}]"""
                "/rest/interface/monitor-traffic" -> 200 to
                    """[{"name":"ether1-WAN","rx-bits-per-second":"42500000","tx-bits-per-second":"12000000"}]"""
                else -> 200 to "[]"
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val cfg = RouterConfig(
                host = "127.0.0.1",
                port = server.address.port,
                username = "admin",
                password = "secret",
            )
            val snapResult = client.fetchSnapshot(cfg)
            assertTrue("fetchSnapshot failed: ${snapResult.exceptionOrNull()}", snapResult.isSuccess)
            val snap = snapResult.getOrThrow()
            assertEquals("MikroTik-Pusat", snap.resource.identity)
            assertEquals(14, snap.resource.cpuLoadPercent)
            assertEquals(1, snap.wirelessInterfaces.size)
            assertTrue(snap.wirelessInterfaces.first().isWifiV7)
            assertEquals(2, snap.wirelessInterfaces.first().connectedClients)
            assertEquals(1, snap.hotspotUsers.size)
            assertTrue(snap.hotspotUsers.first().isActive)

            val pollResult = client.pollTrafficAndResource(cfg, "ether1-WAN")
            assertTrue(pollResult.isSuccess)
            val (_, sample) = pollResult.getOrThrow()
            assertEquals(42.5f, sample.downloadMbps, 0.01f)
            assertEquals(12.0f, sample.uploadMbps, 0.01f)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `toggleWirelessInterface targets wifi vs wireless endpoint accurately`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = CopyOnWriteArrayList<String>()
        server.createContext("/rest/") { exchange ->
            requests.add("${exchange.requestMethod} ${exchange.requestURI.path}")
            val bytes = "{}".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val cfg = RouterConfig(host = "127.0.0.1", port = server.address.port, username = "admin", password = "pw")
            val toggleV7 = client.toggleWirelessInterface(cfg, "*A", disabled = true, isWifiV7 = true)
            assertTrue("toggleV7 failed: ${toggleV7.exceptionOrNull()}", toggleV7.isSuccess)
            val toggleLegacy = client.toggleWirelessInterface(cfg, "*B", disabled = false, isWifiV7 = false)
            assertTrue("toggleLegacy failed: ${toggleLegacy.exceptionOrNull()}", toggleLegacy.isSuccess)
            assertEquals(
                listOf("PATCH /rest/interface/wifi/*A", "PATCH /rest/interface/wireless/*B"),
                requests.toList(),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `PATCH payloads omit blank optional fields and normalize None session timeout`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val bodies = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        server.createContext("/rest/") { exchange ->
            val rawBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
            bodies.add("${exchange.requestMethod} ${exchange.requestURI.path}" to JSONObject(rawBody))
            val bytes = "{}".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val cfg = RouterConfig(host = "127.0.0.1", port = server.address.port, username = "admin", password = "pw")

            val wgResult = client.updateWalledGarden(
                config = cfg,
                id = "*W1",
                dstHost = "*.whatsapp.net",
                dstPort = " ",
                action = "allow",
                comment = "wa",
            )
            assertTrue("updateWalledGarden failed: ${wgResult.exceptionOrNull()}", wgResult.isSuccess)

            val bindResult = client.updateIpBinding(
                config = cfg,
                id = "*B1",
                macAddress = "AA:BB:CC:DD:EE:01",
                address = "",
                toAddress = "",
                server = "",
                type = "bypassed",
                comment = "ap",
            )
            assertTrue("updateIpBinding failed: ${bindResult.exceptionOrNull()}", bindResult.isSuccess)

            val profileResult = client.updateUserProfile(
                config = cfg,
                id = "*P1",
                name = "vip",
                rateLimit = "Unlimited",
                sharedUsers = "",
                sessionTimeout = "None",
            )
            assertTrue("updateUserProfile failed: ${profileResult.exceptionOrNull()}", profileResult.isSuccess)

            assertEquals(3, bodies.size)
            assertEquals("PATCH /rest/ip/hotspot/walled-garden/*W1", bodies[0].first)
            assertFalse(bodies[0].second.has("dst-port"))

            assertEquals("PATCH /rest/ip/hotspot/ip-binding/*B1", bodies[1].first)
            assertEquals("AA:BB:CC:DD:EE:01", bodies[1].second.getString("mac-address"))
            assertFalse(bodies[1].second.has("address"))
            assertFalse(bodies[1].second.has("to-address"))
            assertEquals("all", bodies[1].second.getString("server"))

            assertEquals("PATCH /rest/ip/hotspot/user/profile/*P1", bodies[2].first)
            assertEquals("", bodies[2].second.getString("rate-limit"))
            assertEquals("1", bodies[2].second.getString("shared-users"))
            assertEquals("0s", bodies[2].second.getString("session-timeout"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `clearSystemLogs uses native clear on v7_20+ and falls back to memory-lines flush`() = runBlocking {
        // Case 1: Native POST /rest/system/logging/action/clear succeeds
        val nativeServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val nativeCalls = CopyOnWriteArrayList<Pair<String, String>>()
        nativeServer.createContext("/rest/") { exchange ->
            val reqBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
            nativeCalls.add("${exchange.requestMethod} ${exchange.requestURI.path}" to reqBody)
            val bytes = "{}".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        nativeServer.start()
        try {
            val cfg = RouterConfig(host = "127.0.0.1", port = nativeServer.address.port, username = "admin", password = "pw")
            val res = client.clearSystemLogs(cfg)
            assertTrue("native clearSystemLogs failed: ${res.exceptionOrNull()}", res.isSuccess)
            assertEquals(1, nativeCalls.size)
            assertEquals("POST /rest/system/logging/action/clear", nativeCalls[0].first)
            assertEquals("memory", JSONObject(nativeCalls[0].second).getString("action"))
        } finally {
            nativeServer.stop(0)
        }

        // Case 2: Older RouterOS rejects /clear -> GET actions, PATCH memory-lines=1, then restore original
        val fallbackServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val fallbackCalls = CopyOnWriteArrayList<Pair<String, String>>()
        fallbackServer.createContext("/rest/") { exchange ->
            val path = exchange.requestURI.path
            val method = exchange.requestMethod
            val reqBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
            fallbackCalls.add("$method $path" to reqBody)
            val (status, resp) = when {
                method == "POST" && path == "/rest/system/logging/action/clear" ->
                    400 to """{"detail":"no such command"}"""
                method == "GET" && path == "/rest/system/logging/action" ->
                    200 to """[{".id":"*1","name":"memory","target":"memory","memory-lines":"1000"},{".id":"*2","name":"disk","target":"disk"}]"""
                else -> 200 to "{}"
            }
            val bytes = resp.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        fallbackServer.start()
        try {
            val cfg = RouterConfig(host = "127.0.0.1", port = fallbackServer.address.port, username = "admin", password = "pw")
            val res = client.clearSystemLogs(cfg)
            assertTrue("fallback clearSystemLogs failed: ${res.exceptionOrNull()}", res.isSuccess)
            assertEquals(4, fallbackCalls.size)
            assertEquals("POST /rest/system/logging/action/clear", fallbackCalls[0].first)
            assertEquals("GET /rest/system/logging/action", fallbackCalls[1].first)
            assertEquals("PATCH /rest/system/logging/action/*1", fallbackCalls[2].first)
            assertEquals("1", JSONObject(fallbackCalls[2].second).getString("memory-lines"))
            assertEquals("PATCH /rest/system/logging/action/*1", fallbackCalls[3].first)
            assertEquals("1000", JSONObject(fallbackCalls[3].second).getString("memory-lines"))
        } finally {
            fallbackServer.stop(0)
        }
    }

    @Test
    fun `makeDhcpLeaseStatic, addUserProfile, and updateWirelessInterface send valid RouterOS v7 payloads`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        server.createContext("/rest/") { exchange ->
            val raw = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
            calls.add("${exchange.requestMethod} ${exchange.requestURI.path}" to JSONObject(raw))
            val bytes = "{}".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val cfg = RouterConfig(host = "127.0.0.1", port = server.address.port, username = "admin", password = "pw")

            val staticRes = client.makeDhcpLeaseStatic(cfg, "*86")
            assertTrue("makeDhcpLeaseStatic failed: ${staticRes.exceptionOrNull()}", staticRes.isSuccess)

            val profileRes = client.addUserProfile(
                config = cfg,
                name = "vip_new",
                rateLimit = "Unlimited",
                sharedUsers = "",
                sessionTimeout = "None",
            )
            assertTrue("addUserProfile failed: ${profileRes.exceptionOrNull()}", profileRes.isSuccess)

            val wifiRes = client.updateWirelessInterface(
                config = cfg,
                id = "*A",
                ssid = "Erlkim-5G",
                frequency = "auto",
                comment = "Lobby",
                isWifiV7 = true,
            )
            assertTrue("updateWirelessInterface failed: ${wifiRes.exceptionOrNull()}", wifiRes.isSuccess)

            assertEquals("POST /rest/ip/dhcp-server/lease/make-static", calls[0].first)
            assertEquals("*86", calls[0].second.getString("numbers"))
            assertFalse(calls[0].second.has(".id"))

            assertEquals("PUT /rest/ip/hotspot/user/profile", calls[1].first)
            assertFalse(calls[1].second.has("rate-limit"))
            assertEquals("1", calls[1].second.getString("shared-users"))
            assertFalse(calls[1].second.has("session-timeout"))

            assertEquals("PATCH /rest/interface/wifi/*A", calls[2].first)
            assertEquals("Erlkim-5G", calls[2].second.getString("configuration.ssid"))
            assertEquals("", calls[2].second.getString("channel.frequency"))
        } finally {
            server.stop(0)
        }
    }
}
