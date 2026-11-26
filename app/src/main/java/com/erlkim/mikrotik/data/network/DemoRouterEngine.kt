package com.erlkim.mikrotik.data.network

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
import com.erlkim.mikrotik.data.model.RouterInterface
import com.erlkim.mikrotik.data.model.RouterLogEntry
import com.erlkim.mikrotik.data.model.RouterResource
import com.erlkim.mikrotik.data.model.RouterSystemUser
import com.erlkim.mikrotik.data.model.TrafficSample
import com.erlkim.mikrotik.data.model.WalledGardenEntry
import com.erlkim.mikrotik.data.model.WirelessInterface
import com.erlkim.mikrotik.data.model.parseIpv4Cidr
import kotlin.math.sin
import kotlin.random.Random

/**
 * Interactive local simulator used ONLY when the user explicitly enables "Mode Demo (Simulasi)"
 * to preview and test Erlkim NET without a physical MikroTik RouterOS device on the local network.
 */
class DemoRouterEngine {

    private var tick = 0
    private var idCounter = 100

    private var resource = RouterResource(
        identity = "Router RB3011UiAS",
        boardName = "RB3011UiAS",
        version = "Router OS v7.12",
        uptime = "1d 04h 38m",
        cpuLoadPercent = 25,
        ramUsagePercent = 35,
        storageUsagePercent = 10,
        totalMemoryMb = 1024,
        freeMemoryMb = 665,
        totalHddMb = 128,
        freeHddMb = 115,
        clockTime = "12:35:18",
        clockDate = "Nov/25/2026",
        timeZoneName = "Asia/Jakarta",
    )

    private val interfaces = mutableListOf(
        RouterInterface("*1", "ether1-WAN", "ether", running = true, disabled = false, rxMbps = 45.3f, txMbps = 12.4f, macAddress = "D4:01:C3:8A:11:01", mtu = "1500", comment = "Uplink ISP Fiber 100Mbps", rxBytes = 14_820_000_000L, txBytes = 3_190_000_000L),
        RouterInterface("*2", "bridge-LAN", "bridge", running = true, disabled = false, rxMbps = 18.5f, txMbps = 2.4f, macAddress = "D4:01:C3:8A:11:02", mtu = "1500", comment = "Jaringan Kantor & CCTV", rxBytes = 6_410_000_000L, txBytes = 1_520_000_000L),
        RouterInterface("*3", "ether2-Hotspot", "ether", running = true, disabled = false, rxMbps = 14.2f, txMbps = 2.1f, macAddress = "D4:01:C3:8A:11:03", mtu = "1500", comment = "Voucher Hotspot Publik", rxBytes = 8_950_000_000L, txBytes = 1_120_000_000L),
        RouterInterface("*4", "ether3-AP-Lobby", "ether", running = true, disabled = false, rxMbps = 6.8f, txMbps = 1.2f, macAddress = "D4:01:C3:8A:11:04", mtu = "1500", comment = "Access Point Lobby Lantai 1", rxBytes = 2_380_000_000L, txBytes = 410_000_000L),
        RouterInterface("*5", "sfp1-Uplink", "ether", running = false, disabled = true, rxMbps = 0f, txMbps = 0f, macAddress = "D4:01:C3:8A:11:05", mtu = "1500", comment = "Backup Fiber Link", rxBytes = 0L, txBytes = 0L),
    )

    private val ipAddresses = mutableListOf(
        IpAddressEntry("*1", "192.168.88.1/24", "192.168.88.0", "bridge-LAN", comment = "Gateway LAN Utama"),
        IpAddressEntry("*2", "10.10.0.1/22", "10.10.0.0", "ether2-Hotspot", comment = "Network Hotspot Erlkim"),
        IpAddressEntry("*3", "103.42.118.24/30", "103.42.118.24", "ether1-WAN", comment = "IP Publik ISP", dynamic = true),
    )

    private val dhcpServers = mutableListOf(
        DhcpServerEntry("*1", "dhcp-lan", "bridge-LAN", "pool-lan", "1h"),
        DhcpServerEntry("*2", "dhcp-hotspot", "ether2-Hotspot", "hs-pool-1", "30m"),
    )

    private val dhcpLeases = mutableListOf(
        DhcpLeaseEntry("*1", "192.168.88.10", "A4:83:E7:21:9B:02", "Laptop-Admin", "bound", "dhcp-lan", dynamic = false, expiresAfter = "52m10s", comment = "PC Admin Utama"),
        DhcpLeaseEntry("*2", "10.10.0.42", "5C:C9:D3:44:12:88", "Galaxy-Andri", "bound", "dhcp-hotspot", dynamic = true, expiresAfter = "18m44s"),
        DhcpLeaseEntry("*3", "10.10.0.58", "B8:27:EB:61:A0:19", "iPhone-Sisca", "bound", "dhcp-hotspot", dynamic = true, expiresAfter = "24m12s"),
        DhcpLeaseEntry("*4", "192.168.88.22", "DC:A6:32:10:8F:44", "CCTV-Gudang", "bound", "dhcp-lan", dynamic = false, expiresAfter = "48m05s", comment = "NVR Gudang"),
    )

    private val wireless = mutableListOf(
        WirelessInterface("*1", "wlan1-2.4G", "My_MikroTik_Hotspot", "2ghz-b/g/n", "2437", running = true, disabled = false, connectedClients = 27, mode = "ap-bridge", securityProfile = "open-hotspot", channelWidth = "20/40mhz-XX", comment = "AP Area Kafe & Kasir"),
        WirelessInterface("*2", "wlan2-5G", "Erlkim_NET_5G_VIP", "5ghz-a/n/ac", "5180", running = true, disabled = false, connectedClients = 15, mode = "ap-bridge", securityProfile = "wpa2-vip", channelWidth = "20/40/80mhz-Ceee", comment = "AP Ruang VIP & Staff"),
    )

    private val hotspotServers = mutableListOf(
        HotspotServer("*1", "My_MikroTik_Hotspot", "ether2-Hotspot", "hs-pool-1", "hsprof-erlkim", disabled = false),
        HotspotServer("*2", "Erlkim_VIP_Zone", "wlan2-5G", "hs-pool-vip", "hsprof-vip", disabled = false),
    )

    private val userProfiles = mutableListOf(
        HotspotUserProfile("*1", "aktif", sharedUsers = "1", rateLimit = "5M/10M", sessionTimeout = "1d"),
        HotspotUserProfile("*2", "sisca_VIP", sharedUsers = "3", rateLimit = "25M/50M", sessionTimeout = "30d"),
        HotspotUserProfile("*3", "voucher_3jam", sharedUsers = "1", rateLimit = "3M/5M", sessionTimeout = "3h"),
        HotspotUserProfile("*4", "staff_unlimited", sharedUsers = "5", rateLimit = "Unlimited", sessionTimeout = "None"),
    )

    private val hotspotUsers = mutableListOf(
        HotspotUser("*1", "user_andri", "12345", "aktif", usedBytes = 340L * 1024 * 1024, limitBytesTotal = 1024L * 1024 * 1024, uptime = "4h 12m", sessionTimeout = "1d", isActive = true),
        HotspotUser("*2", "Sisca_VIP", "vip2026", "sisca_VIP", usedBytes = 420L * 1024 * 1024, limitBytesTotal = 0L, uptime = "11h 40m", sessionTimeout = "", isActive = true, comment = "Pelanggan VIP"),
        HotspotUser("*3", "budi_kafe", "kafe99", "aktif", usedBytes = 1060L * 1024 * 1024, limitBytesTotal = 1024L * 1024 * 1024, uptime = "2h 05m", sessionTimeout = "3h", isActive = true, comment = "Meja 04"),
        HotspotUser("*4", "rani_guest", "tamu01", "voucher_3jam", usedBytes = 512L * 1024 * 1024, limitBytesTotal = 512L * 1024 * 1024, uptime = "3h 00m", sessionTimeout = "3h", isActive = false, isExpired = true, disabled = true),
        HotspotUser("*5", "dimas_VIP", "vip889", "sisca_VIP", usedBytes = 13L * 1024 * 1024, limitBytesTotal = 0L, uptime = "0s", sessionTimeout = "30d", isActive = false, isExpired = false, comment = "VIP Bulanan"),
    )

    private val activeSessions = mutableListOf(
        HotspotActiveSession("*1", "user_andri", "10.10.0.42", "5C:C9:D3:44:12:88", "04:12:08", 290L * 1024 * 1024, 50L * 1024 * 1024, "My_MikroTik_Hotspot"),
        HotspotActiveSession("*2", "Sisca_VIP", "10.10.0.58", "B8:27:EB:61:A0:19", "11:40:22", 360L * 1024 * 1024, 60L * 1024 * 1024, "My_MikroTik_Hotspot"),
        HotspotActiveSession("*3", "budi_kafe", "10.10.0.71", "3C:22:FB:90:4D:11", "02:05:44", 920L * 1024 * 1024, 140L * 1024 * 1024, "My_MikroTik_Hotspot"),
    )

    private val ipBindings = mutableListOf(
        IpBindingEntry("*1", "DC:A6:32:10:8F:44", "10.10.0.5", "10.10.0.5", "bypassed", "Printer Kasir"),
        IpBindingEntry("*2", "A4:83:E7:21:9B:02", "10.10.0.2", "10.10.0.2", "bypassed", "Access Point Utama"),
        IpBindingEntry("*3", "00:1A:2B:3C:4D:5E", "10.10.0.99", "", "blocked", "Perangkat Diblokir"),
    )

    private val walledGarden = mutableListOf(
        WalledGardenEntry("*1", "*.erlkim.net", "443", "allow", "Portal Pembayaran"),
        WalledGardenEntry("*2", "*.whatsapp.net", "443", "allow", "Chat CS Voucher"),
        WalledGardenEntry("*3", "qris.bankindonesia.id", "443", "allow", "Verifikasi QRIS"),
    )

    private val systemUsers = mutableListOf(
        RouterSystemUser("*1", "admin", "full", "Nov/25/2026 12:20:04"),
        RouterSystemUser("*2", "operator_erlkim", "write", "Nov/25/2026 09:15:11"),
        RouterSystemUser("*3", "monitor_noc", "read", "Nov/24/2026 22:08:40"),
    )

    private val systemLogs = mutableListOf(
        RouterLogEntry("*l1", "Hotspot", "user_andri (10.10.0.42): logged in via My_MikroTik_Hotspot", "12:34:51", "hotspot,info,debug", LogSeverity.INFO),
        RouterLogEntry("*l2", "DHCP", "dhcp-hotspot assigned 10.10.0.58 for B8:27:EB:61:A0:19 (iPhone-Sisca)", "12:33:14", "dhcp,info", LogSeverity.INFO),
        RouterLogEntry("*l3", "Firewall", "Possible port scan detected from 103.42.118.91 on ether1-WAN (dropped)", "12:30:02", "firewall,warning", LogSeverity.WARNING),
        RouterLogEntry("*l4", "Interface", "sfp1-Uplink link down — SFP optical signal lost", "12:26:40", "interface,error,critical", LogSeverity.ERROR),
        RouterLogEntry("*l5", "Hotspot", "Authentication failed for user 'guest_09': invalid password (10.10.0.84)", "12:22:19", "hotspot,warning", LogSeverity.WARNING),
        RouterLogEntry("*l6", "System", "User admin logged in via REST API from 192.168.88.10", "12:20:04", "system,info,account", LogSeverity.INFO),
        RouterLogEntry("*l7", "Wireless", "wlan1-2.4G high channel interference detected on 2437 MHz (58%)", "12:15:33", "wireless,warning", LogSeverity.WARNING),
        RouterLogEntry("*l8", "Radius", "RADIUS server 192.168.88.250 did not respond (timeout after 3000ms)", "12:08:11", "radius,error", LogSeverity.ERROR),
        RouterLogEntry("*l9", "System", "NTP client synchronized clock with id.pool.ntp.org", "11:55:00", "system,info", LogSeverity.INFO),
    )

    fun initialTrafficHistory(): List<TrafficSample> {
        val now = System.currentTimeMillis()
        val baseDownload = listOf(28f, 24f, 19f, 23f, 34f, 44f, 30f, 22f, 27f, 38f, 31f, 21f, 39f, 35f, 45.3f)
        val baseUpload = listOf(8.2f, 10.4f, 8.8f, 7.6f, 11.2f, 10.1f, 9.4f, 10.8f, 9.6f, 10.5f, 13.8f, 9.7f, 11.9f, 10.6f, 12.4f)
        return baseDownload.indices.map { i ->
            val offsetMs = (baseDownload.lastIndex - i) * 2500L
            TrafficSample(
                downloadMbps = baseDownload[i],
                uploadMbps = baseUpload[i],
                timestampMs = now - offsetMs,
            )
        }
    }

    fun initialTrafficHistoryFor(iface: RouterInterface?): List<TrafficSample> {
        val scale = when {
            iface == null || iface.name.contains("wan", true) -> 1f
            iface.name.contains("lan", true) -> 0.55f
            iface.name.contains("hotspot", true) -> 0.42f
            iface.disabled || !iface.running -> 0f
            else -> 0.28f
        }
        return initialTrafficHistory().map { sample ->
            sample.copy(
                downloadMbps = (sample.downloadMbps * scale).coerceAtLeast(0f),
                uploadMbps = (sample.uploadMbps * scale).coerceAtLeast(0f),
            )
        }
    }

    fun snapshot(): RouterSnapshot = RouterSnapshot(
        resource = resource,
        interfaces = interfaces.toList(),
        ipAddresses = ipAddresses.toList(),
        dhcpServers = dhcpServers.toList(),
        dhcpLeases = dhcpLeases.toList(),
        wirelessInterfaces = wireless.toList(),
        hotspotServers = hotspotServers.toList(),
        hotspotUsers = hotspotUsers.toList(),
        activeSessions = activeSessions.toList(),
        userProfiles = userProfiles.toList(),
        ipBindings = ipBindings.toList(),
        walledGarden = walledGarden.toList(),
        systemUsers = systemUsers.toList(),
        systemLogs = systemLogs.toList(),
    )

    fun nextTelemetryTick(): Pair<RouterResource, TrafficSample> {
        tick++
        val wave = sin(tick * 0.65).toFloat()
        val rx = (34f + wave * 11f + Random.nextFloat() * 5f).coerceIn(12f, 58f)
        val tx = (10.5f + wave * 2.5f + Random.nextFloat() * 2.2f).coerceIn(3f, 20f)
        val cpu = (22 + (wave * 7).toInt() + Random.nextInt(-2, 4)).coerceIn(8, 65)
        val ram = (34 + Random.nextInt(-1, 3)).coerceIn(20, 75)
        val nowClock = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        resource = resource.copy(
            cpuLoadPercent = cpu,
            ramUsagePercent = ram,
            clockTime = nowClock,
        )

        // Increment running interface RX/TX counters to reflect continuous live telemetry
        for (i in interfaces.indices) {
            val iface = interfaces[i]
            if (iface.running && !iface.disabled) {
                val scale = when {
                    iface.name.contains("wan", true) -> 1.0
                    iface.name.contains("lan", true) -> 0.55
                    iface.name.contains("hotspot", true) -> 0.42
                    else -> 0.25
                }
                val rxDelta = ((rx * 125_000.0 * 2.5) * scale).toLong().coerceAtLeast(1024L)
                val txDelta = ((tx * 125_000.0 * 2.5) * scale).toLong().coerceAtLeast(512L)
                interfaces[i] = iface.copy(
                    rxMbps = (rx * scale).toFloat(),
                    txMbps = (tx * scale).toFloat(),
                    rxBytes = iface.rxBytes + rxDelta,
                    txBytes = iface.txBytes + txDelta,
                )
            }
        }

        // Increment active session byte counters to reflect ongoing usage
        for (i in activeSessions.indices) {
            val s = activeSessions[i]
            val deltaIn = (Random.nextLong(600_000L, 2_400_000L))
            val deltaOut = (Random.nextLong(150_000L, 600_000L))
            val updatedSession = s.copy(bytesIn = s.bytesIn + deltaIn, bytesOut = s.bytesOut + deltaOut)
            activeSessions[i] = updatedSession
            val uIdx = hotspotUsers.indexOfFirst { it.name == s.user }
            if (uIdx >= 0) {
                val u = hotspotUsers[uIdx]
                val newUsed = maxOf(u.usedBytes + deltaIn + deltaOut, updatedSession.bytesIn + updatedSession.bytesOut)
                hotspotUsers[uIdx] = u.copy(usedBytes = newUsed)
            }
        }
        return resource to TrafficSample(downloadMbps = rx, uploadMbps = tx)
    }

    fun addUser(name: String, password: String, profile: String, limitBytes: Long, comment: String) {
        val newId = "*${++idCounter}"
        hotspotUsers.add(
            0,
            HotspotUser(
                id = newId,
                name = name,
                password = password,
                profile = profile,
                usedBytes = 0L,
                limitBytesTotal = limitBytes,
                uptime = "0s",
                disabled = false,
                isActive = true,
                isExpired = false,
                comment = comment,
            )
        )
        activeSessions.add(
            0,
            HotspotActiveSession(
                id = "*s$idCounter",
                user = name,
                address = "10.10.0.${Random.nextInt(80, 220)}",
                macAddress = "E8:4E:06:${Random.nextInt(10, 99)}:${Random.nextInt(10, 99)}:A1",
                uptime = "00:00:05",
                bytesIn = 0L,
                bytesOut = 0L,
                server = hotspotServers.firstOrNull()?.name ?: "My_MikroTik_Hotspot",
            )
        )
    }

    fun toggleUserDisabled(userId: String, disabled: Boolean) {
        val idx = hotspotUsers.indexOfFirst { it.id == userId }
        if (idx >= 0) {
            val u = hotspotUsers[idx]
            hotspotUsers[idx] = u.copy(disabled = disabled, isActive = !disabled, isExpired = disabled)
            if (disabled) activeSessions.removeAll { it.user == u.name }
        }
    }

    fun updateUserSessionTimeout(userId: String, sessionTimeout: String) {
        val idx = hotspotUsers.indexOfFirst { it.id == userId }
        if (idx >= 0) {
            val normalized = sessionTimeout.trim().let {
                if (it == "0s" || it.equals("unlimited", true) || it.equals("none", true)) "" else it
            }
            hotspotUsers[idx] = hotspotUsers[idx].copy(sessionTimeout = normalized)
        }
    }

    fun updateUserPassword(userId: String, newPassword: String) {
        val idx = hotspotUsers.indexOfFirst { it.id == userId }
        if (idx >= 0) {
            hotspotUsers[idx] = hotspotUsers[idx].copy(password = newPassword)
        }
    }

    fun deleteUser(userId: String) {
        val user = hotspotUsers.firstOrNull { it.id == userId }
        hotspotUsers.removeAll { it.id == userId }
        if (user != null) activeSessions.removeAll { it.user == user.name }
    }

    fun kickSession(sessionId: String) {
        val session = activeSessions.firstOrNull { it.id == sessionId }
        activeSessions.removeAll { it.id == sessionId }
        if (session != null) {
            val idx = hotspotUsers.indexOfFirst { it.name == session.user }
            if (idx >= 0) {
                hotspotUsers[idx] = hotspotUsers[idx].copy(isActive = false)
            }
        }
    }

    fun addWalledGarden(dstHost: String, dstPort: String, action: String, comment: String) {
        walledGarden.add(0, WalledGardenEntry("*${++idCounter}", dstHost, dstPort, action, comment))
    }

    fun updateWalledGarden(id: String, dstHost: String, dstPort: String, action: String, comment: String) {
        val idx = walledGarden.indexOfFirst { it.id == id }
        if (idx >= 0) {
            walledGarden[idx] = walledGarden[idx].copy(
                dstHost = dstHost,
                dstPort = dstPort,
                action = action,
                comment = comment,
            )
        }
    }

    fun toggleWalledGardenDisabled(id: String, disabled: Boolean) {
        val idx = walledGarden.indexOfFirst { it.id == id }
        if (idx >= 0) {
            walledGarden[idx] = walledGarden[idx].copy(disabled = disabled)
        }
    }

    fun deleteWalledGarden(id: String) {
        walledGarden.removeAll { it.id == id }
    }

    fun addHotspotServer(name: String, interfaceName: String, addressPool: String, profile: String) {
        hotspotServers.add(
            HotspotServer(
                id = "*${++idCounter}",
                name = name,
                interfaceName = interfaceName,
                addressPool = addressPool.ifBlank { "hs-pool-1" },
                profile = profile.ifBlank { "hsprof1" },
                disabled = false,
            ),
        )
    }

    fun toggleHotspotServerDisabled(id: String, disabled: Boolean) {
        val idx = hotspotServers.indexOfFirst { it.id == id }
        if (idx >= 0) {
            hotspotServers[idx] = hotspotServers[idx].copy(disabled = disabled)
        }
    }

    fun deleteHotspotServer(id: String) {
        hotspotServers.removeAll { it.id == id }
    }

    fun addIpBinding(
        mac: String,
        address: String,
        type: String,
        comment: String,
        toAddress: String = "",
        server: String = "all",
    ) {
        val resolvedTo = toAddress.ifBlank { if (type == "bypassed") address else "" }
        ipBindings.add(
            0,
            IpBindingEntry(
                id = "*${++idCounter}",
                macAddress = mac.uppercase(),
                address = address,
                toAddress = resolvedTo,
                type = type,
                comment = comment,
                disabled = false,
                server = server.ifBlank { "all" },
            ),
        )
    }

    fun updateIpBinding(
        id: String,
        mac: String,
        address: String,
        toAddress: String,
        server: String,
        type: String,
        comment: String,
    ) {
        val idx = ipBindings.indexOfFirst { it.id == id }
        if (idx >= 0) {
            ipBindings[idx] = ipBindings[idx].copy(
                macAddress = mac.uppercase(),
                address = address,
                toAddress = toAddress,
                server = server.ifBlank { "all" },
                type = type,
                comment = comment,
            )
        }
    }

    fun toggleIpBindingDisabled(id: String, disabled: Boolean) {
        val idx = ipBindings.indexOfFirst { it.id == id }
        if (idx >= 0) {
            ipBindings[idx] = ipBindings[idx].copy(disabled = disabled)
        }
    }

    fun deleteIpBinding(id: String) {
        ipBindings.removeAll { it.id == id }
    }

    fun addProfile(name: String, rateLimit: String, sharedUsers: String, timeout: String) {
        userProfiles.add(
            HotspotUserProfile(
                "*${++idCounter}",
                name,
                sharedUsers.ifBlank { "1" },
                rateLimit.ifBlank { "Unlimited" },
                timeout.ifBlank { "None" },
            ),
        )
    }

    fun updateProfile(id: String, name: String, rateLimit: String, sharedUsers: String, timeout: String) {
        val idx = userProfiles.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val oldName = userProfiles[idx].name
            userProfiles[idx] = userProfiles[idx].copy(
                name = name,
                sharedUsers = sharedUsers.ifBlank { "1" },
                rateLimit = rateLimit.ifBlank { "Unlimited" },
                sessionTimeout = timeout.ifBlank { "None" },
            )
            if (oldName != name) {
                for (i in hotspotUsers.indices) {
                    if (hotspotUsers[i].profile == oldName) {
                        hotspotUsers[i] = hotspotUsers[i].copy(profile = name)
                    }
                }
            }
        }
    }

    fun deleteProfile(id: String) {
        userProfiles.removeAll { it.id == id }
    }

    fun addIpAddress(address: String, iface: String, comment: String) {
        val network = parseIpv4Cidr(address)?.networkAddress
            ?: (address.substringBefore("/").substringBeforeLast(".") + ".0")
        ipAddresses.add(IpAddressEntry("*${++idCounter}", address, network, iface, comment = comment))
    }

    fun updateIpAddress(id: String, address: String, iface: String, comment: String) {
        val idx = ipAddresses.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val network = parseIpv4Cidr(address)?.networkAddress
                ?: (address.substringBefore("/").substringBeforeLast(".") + ".0")
            ipAddresses[idx] = ipAddresses[idx].copy(
                address = address,
                network = network,
                interfaceName = iface,
                comment = comment,
            )
        }
    }

    fun toggleIpAddressDisabled(id: String, disabled: Boolean) {
        val idx = ipAddresses.indexOfFirst { it.id == id }
        if (idx >= 0) {
            ipAddresses[idx] = ipAddresses[idx].copy(disabled = disabled)
        }
    }

    fun deleteIpAddress(id: String) {
        ipAddresses.removeAll { it.id == id }
    }

    fun addDhcpServer(name: String, iface: String, addressPool: String, leaseTime: String) {
        dhcpServers.add(
            DhcpServerEntry(
                id = "*${++idCounter}",
                name = name,
                interfaceName = iface,
                addressPool = addressPool.ifBlank { "static-only" },
                leaseTime = leaseTime.ifBlank { "10m" },
                disabled = false,
            ),
        )
    }

    fun updateDhcpServer(id: String, name: String, iface: String, addressPool: String, leaseTime: String) {
        val idx = dhcpServers.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val oldName = dhcpServers[idx].name
            dhcpServers[idx] = dhcpServers[idx].copy(
                name = name,
                interfaceName = iface,
                addressPool = addressPool.ifBlank { "static-only" },
                leaseTime = leaseTime.ifBlank { "10m" },
            )
            if (oldName != name) {
                for (i in dhcpLeases.indices) {
                    if (dhcpLeases[i].server == oldName) {
                        dhcpLeases[i] = dhcpLeases[i].copy(server = name)
                    }
                }
            }
        }
    }

    fun toggleDhcpServerDisabled(id: String, disabled: Boolean) {
        val idx = dhcpServers.indexOfFirst { it.id == id }
        if (idx >= 0) {
            dhcpServers[idx] = dhcpServers[idx].copy(disabled = disabled)
        }
    }

    fun deleteDhcpServer(id: String) {
        dhcpServers.removeAll { it.id == id }
    }

    fun addStaticDhcpLease(address: String, macAddress: String, server: String, comment: String) {
        val cleanComment = comment.trim()
        dhcpLeases.add(
            0,
            DhcpLeaseEntry(
                id = "*${++idCounter}",
                address = address,
                macAddress = macAddress.uppercase(),
                hostName = cleanComment.ifBlank { "Static-${address.substringAfterLast('.')}" },
                status = "waiting",
                server = server.ifBlank { dhcpServers.firstOrNull()?.name ?: "all" },
                dynamic = false,
                disabled = false,
                expiresAfter = "",
                comment = cleanComment,
            ),
        )
    }

    fun makeDhcpLeaseStatic(id: String) {
        val idx = dhcpLeases.indexOfFirst { it.id == id }
        if (idx >= 0) {
            dhcpLeases[idx] = dhcpLeases[idx].copy(dynamic = false)
        }
    }

    fun toggleDhcpLeaseDisabled(id: String, disabled: Boolean) {
        val idx = dhcpLeases.indexOfFirst { it.id == id }
        if (idx >= 0) {
            dhcpLeases[idx] = dhcpLeases[idx].copy(disabled = disabled)
        }
    }

    fun deleteDhcpLease(id: String) {
        dhcpLeases.removeAll { it.id == id }
    }

    fun toggleInterface(id: String, disabled: Boolean) {
        val idx = interfaces.indexOfFirst { it.id == id }
        if (idx >= 0) {
            interfaces[idx] = interfaces[idx].copy(disabled = disabled, running = !disabled)
        }
        val wIdx = wireless.indexOfFirst { it.id == id }
        if (wIdx >= 0) {
            wireless[wIdx] = wireless[wIdx].copy(disabled = disabled, running = !disabled)
        }
    }

    fun updateInterface(id: String, comment: String, mtu: String) {
        val idx = interfaces.indexOfFirst { it.id == id }
        if (idx >= 0) {
            interfaces[idx] = interfaces[idx].copy(
                comment = comment,
                mtu = mtu.ifBlank { interfaces[idx].mtu },
            )
        }
    }

    fun resetInterfaceCounters(id: String) {
        val idx = interfaces.indexOfFirst { it.id == id }
        if (idx >= 0) {
            interfaces[idx] = interfaces[idx].copy(rxBytes = 0L, txBytes = 0L)
        }
    }

    fun updateWireless(id: String, ssid: String, frequency: String, comment: String) {
        val idx = wireless.indexOfFirst { it.id == id }
        if (idx >= 0) {
            wireless[idx] = wireless[idx].copy(
                ssid = ssid,
                frequency = frequency.ifBlank { "auto" },
                comment = comment,
            )
        }
    }

    fun toggleWirelessDisabled(id: String, disabled: Boolean) {
        val idx = wireless.indexOfFirst { it.id == id }
        if (idx >= 0) {
            wireless[idx] = wireless[idx].copy(disabled = disabled, running = !disabled)
        }
    }

    fun setIdentity(name: String) {
        resource = resource.copy(identity = name)
    }

    fun applyBackup(
        payload: RouterBackupPayload,
        options: BackupRestoreOptions,
    ): RestoreSummary {
        var identityUpdated = false
        var profilesRestored = 0
        var usersRestored = 0
        var bindingsRestored = 0
        var walledRestored = 0
        var ipsRestored = 0
        var skipped = 0

        if (options.restoreIdentity && payload.routerIdentity.isNotBlank()) {
            resource = resource.copy(identity = payload.routerIdentity)
            identityUpdated = true
        }

        if (options.restoreProfiles) {
            if (options.replaceExistingDemoData && payload.userProfiles.isNotEmpty()) {
                userProfiles.clear()
            }
            payload.userProfiles.forEach { p ->
                val idx = userProfiles.indexOfFirst { it.name.equals(p.name, ignoreCase = true) }
                if (idx >= 0) {
                    userProfiles[idx] = p.copy(id = userProfiles[idx].id)
                } else {
                    userProfiles.add(p.copy(id = "*${++idCounter}"))
                }
                profilesRestored++
            }
        }

        if (options.restoreHotspotUsers) {
            if (options.replaceExistingDemoData && payload.hotspotUsers.isNotEmpty()) {
                hotspotUsers.clear()
            }
            payload.hotspotUsers.forEach { u ->
                val idx = hotspotUsers.indexOfFirst { it.name.equals(u.name, ignoreCase = true) }
                if (idx >= 0) {
                    val existing = hotspotUsers[idx]
                    hotspotUsers[idx] = u.copy(
                        id = existing.id,
                        usedBytes = existing.usedBytes,
                        isActive = existing.isActive && !u.disabled,
                    )
                } else {
                    hotspotUsers.add(
                        0,
                        u.copy(
                            id = "*${++idCounter}",
                            isActive = false,
                        )
                    )
                }
                usersRestored++
            }
        }

        if (options.restoreIpBindings) {
            if (options.replaceExistingDemoData && payload.ipBindings.isNotEmpty()) {
                ipBindings.clear()
            }
            payload.ipBindings.forEach { b ->
                val exists = ipBindings.any {
                    (b.macAddress.isNotBlank() && it.macAddress.equals(b.macAddress, true)) ||
                        (b.address.isNotBlank() && it.address == b.address)
                }
                if (exists) {
                    skipped++
                } else {
                    ipBindings.add(0, b.copy(id = "*${++idCounter}"))
                    bindingsRestored++
                }
            }
        }

        if (options.restoreWalledGarden) {
            if (options.replaceExistingDemoData && payload.walledGarden.isNotEmpty()) {
                walledGarden.clear()
            }
            payload.walledGarden.forEach { w ->
                val exists = walledGarden.any { it.dstHost.equals(w.dstHost, true) && it.dstPort == w.dstPort }
                if (exists) {
                    skipped++
                } else {
                    walledGarden.add(0, w.copy(id = "*${++idCounter}"))
                    walledRestored++
                }
            }
        }

        if (options.restoreIpAddresses) {
            if (options.replaceExistingDemoData && payload.ipAddresses.isNotEmpty()) {
                ipAddresses.clear()
            }
            payload.ipAddresses.forEach { ip ->
                val exists = ipAddresses.any { it.address == ip.address && it.interfaceName == ip.interfaceName }
                if (exists) {
                    skipped++
                } else {
                    ipAddresses.add(ip.copy(id = "*${++idCounter}"))
                    ipsRestored++
                }
            }
        }

        return RestoreSummary(
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
