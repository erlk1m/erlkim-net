package com.erlkim.mikrotik.data.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.erlkim.mikrotik.data.model.HotspotUser
import com.erlkim.mikrotik.data.model.HotspotUserProfile
import com.erlkim.mikrotik.data.model.IpAddressEntry
import com.erlkim.mikrotik.data.model.IpBindingEntry
import com.erlkim.mikrotik.data.model.QuotaAlertConfig
import com.erlkim.mikrotik.data.model.WalledGardenEntry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class BackupExportFormat(
    val extension: String,
    val mimeType: String,
    val label: String,
    val description: String,
) {
    JSON(
        extension = "json",
        mimeType = "application/json",
        label = "JSON Backup (.json)",
        description = "Full structured backup for instant 1-tap restoration in Erlkim NET",
    ),
    RSC(
        extension = "rsc",
        mimeType = "text/plain",
        label = "RouterOS Script (.rsc)",
        description = "MikroTik v7 CLI script compatible with WinBox, Terminal & Erlkim NET",
    ),
}

data class RouterBackupPayload(
    val schemaVersion: Int = 1,
    val sourceFormat: BackupExportFormat = BackupExportFormat.JSON,
    val exportedAt: String,
    val routerIdentity: String,
    val boardName: String,
    val routerOsVersion: String,
    val routerAddress: String,
    val ipAddresses: List<IpAddressEntry>,
    val userProfiles: List<HotspotUserProfile>,
    val hotspotUsers: List<HotspotUser>,
    val ipBindings: List<IpBindingEntry>,
    val walledGarden: List<WalledGardenEntry>,
    val quotaAlertConfig: QuotaAlertConfig? = null,
) {
    val totalItemCount: Int
        get() = ipAddresses.size + userProfiles.size + hotspotUsers.size + ipBindings.size + walledGarden.size
}

data class ImportedBackupPreview(
    val fileName: String,
    val fileSizeBytes: Int,
    val payload: RouterBackupPayload,
)

data class BackupRestoreOptions(
    val restoreIdentity: Boolean = true,
    val restoreProfiles: Boolean = true,
    val restoreHotspotUsers: Boolean = true,
    val restoreIpBindings: Boolean = true,
    val restoreWalledGarden: Boolean = true,
    val restoreIpAddresses: Boolean = true,
    val restoreQuotaConfig: Boolean = true,
    val replaceExistingDemoData: Boolean = false,
)

data class RestoreSummary(
    val identityUpdated: Boolean = false,
    val profilesRestored: Int = 0,
    val usersRestored: Int = 0,
    val ipBindingsRestored: Int = 0,
    val walledGardenRestored: Int = 0,
    val ipAddressesRestored: Int = 0,
    val quotaConfigRestored: Boolean = false,
    val skippedCount: Int = 0,
) {
    val totalApplied: Int
        get() = (if (identityUpdated) 1 else 0) +
            profilesRestored +
            usersRestored +
            ipBindingsRestored +
            walledGardenRestored +
            ipAddressesRestored +
            (if (quotaConfigRestored) 1 else 0)
}

object RouterBackupManager {

    private const val BACKUP_FORMAT_TAG = "erlkim-routeros-backup"

    fun suggestedFileName(routerIdentity: String, format: BackupExportFormat): String {
        val cleanIdentity = routerIdentity
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "mikrotik_router" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "erlkim_${cleanIdentity}_$stamp.${format.extension}"
    }

    fun serializeToJson(payload: RouterBackupPayload): String {
        val root = JSONObject().apply {
            put("format", BACKUP_FORMAT_TAG)
            put("schemaVersion", payload.schemaVersion)
            put("exportedAt", payload.exportedAt)
            put("router", JSONObject().apply {
                put("identity", payload.routerIdentity)
                put("boardName", payload.boardName)
                put("version", payload.routerOsVersion)
                put("address", payload.routerAddress)
            })

            put("ipAddresses", JSONArray().apply {
                payload.ipAddresses.forEach { ip ->
                    put(JSONObject().apply {
                        put("address", ip.address)
                        put("network", ip.network)
                        put("interface", ip.interfaceName)
                        put("disabled", ip.disabled)
                        put("comment", ip.comment)
                    })
                }
            })

            put("userProfiles", JSONArray().apply {
                payload.userProfiles.forEach { p ->
                    put(JSONObject().apply {
                        put("name", p.name)
                        put("sharedUsers", p.sharedUsers)
                        put("rateLimit", p.rateLimit)
                        put("sessionTimeout", p.sessionTimeout)
                    })
                }
            })

            put("hotspotUsers", JSONArray().apply {
                payload.hotspotUsers.forEach { u ->
                    put(JSONObject().apply {
                        put("name", u.name)
                        put("password", u.password)
                        put("profile", u.profile)
                        put("limitBytesTotal", u.limitBytesTotal)
                        put("sessionTimeout", u.sessionTimeout)
                        put("disabled", u.disabled)
                        put("comment", u.comment)
                    })
                }
            })

            put("ipBindings", JSONArray().apply {
                payload.ipBindings.forEach { b ->
                    put(JSONObject().apply {
                        put("macAddress", b.macAddress)
                        put("address", b.address)
                        put("toAddress", b.toAddress)
                        put("type", b.type)
                        put("comment", b.comment)
                        put("disabled", b.disabled)
                    })
                }
            })

            put("walledGarden", JSONArray().apply {
                payload.walledGarden.forEach { w ->
                    put(JSONObject().apply {
                        put("dstHost", w.dstHost)
                        put("dstPort", w.dstPort)
                        put("action", w.action)
                        put("comment", w.comment)
                        put("disabled", w.disabled)
                    })
                }
            })

            payload.quotaAlertConfig?.let { cfg ->
                put("quotaAlertConfig", JSONObject().apply {
                    put("enabled", cfg.enabled)
                    put("globalQuotaLimitMb", cfg.globalQuotaLimitMb)
                    put("warningThresholdPercent", cfg.warningThresholdPercent)
                    put("systemNotificationsEnabled", cfg.systemNotificationsEnabled)
                })
            }
        }
        return root.toString(2)
    }

    fun serializeToRsc(payload: RouterBackupPayload): String = buildString {
        appendLine("# Erlkim NET — MikroTik RouterOS v7 Configuration Export")
        appendLine("# Exported: ${payload.exportedAt}")
        appendLine("# Identity: ${payload.routerIdentity} (${payload.boardName} / ${payload.routerOsVersion})")
        appendLine("# Host: ${payload.routerAddress}")
        appendLine()

        appendLine("/system identity")
        appendLine("set name=${rscQuote(payload.routerIdentity)}")
        appendLine()

        if (payload.ipAddresses.isNotEmpty()) {
            appendLine("/ip address")
            payload.ipAddresses.forEach { ip ->
                val parts = mutableListOf(
                    "address=${rscQuote(ip.address)}",
                    "interface=${rscQuote(ip.interfaceName)}",
                )
                if (ip.disabled) parts.add("disabled=yes")
                if (ip.comment.isNotBlank()) parts.add("comment=${rscQuote(ip.comment)}")
                appendLine("add ${parts.joinToString(" ")}")
            }
            appendLine()
        }

        if (payload.userProfiles.isNotEmpty()) {
            appendLine("/ip hotspot user profile")
            payload.userProfiles.forEach { p ->
                val parts = mutableListOf(
                    "name=${rscQuote(p.name)}",
                    "shared-users=${rscQuote(p.sharedUsers)}",
                )
                if (p.rateLimit.isNotBlank() && !p.rateLimit.equals("Unlimited", true)) {
                    parts.add("rate-limit=${rscQuote(p.rateLimit)}")
                }
                if (p.sessionTimeout.isNotBlank() && !p.sessionTimeout.equals("None", true)) {
                    parts.add("session-timeout=${rscQuote(p.sessionTimeout)}")
                }
                appendLine("add ${parts.joinToString(" ")}")
            }
            appendLine()
        }

        if (payload.hotspotUsers.isNotEmpty()) {
            appendLine("/ip hotspot user")
            payload.hotspotUsers.forEach { u ->
                val parts = mutableListOf(
                    "name=${rscQuote(u.name)}",
                    "password=${rscQuote(u.password)}",
                    "profile=${rscQuote(u.profile)}",
                )
                if (u.limitBytesTotal > 0L) parts.add("limit-bytes-total=${u.limitBytesTotal}")
                if (u.sessionTimeout.isNotBlank() && u.sessionTimeout != "0s") {
                    parts.add("limit-uptime=${rscQuote(u.sessionTimeout)}")
                }
                if (u.disabled) parts.add("disabled=yes")
                if (u.comment.isNotBlank()) parts.add("comment=${rscQuote(u.comment)}")
                appendLine("add ${parts.joinToString(" ")}")
            }
            appendLine()
        }

        if (payload.ipBindings.isNotEmpty()) {
            appendLine("/ip hotspot ip-binding")
            payload.ipBindings.forEach { b ->
                val parts = mutableListOf<String>()
                if (b.macAddress.isNotBlank()) parts.add("mac-address=${rscQuote(b.macAddress)}")
                if (b.address.isNotBlank()) parts.add("address=${rscQuote(b.address)}")
                if (b.toAddress.isNotBlank()) parts.add("to-address=${rscQuote(b.toAddress)}")
                parts.add("type=${rscQuote(b.type)}")
                if (b.disabled) parts.add("disabled=yes")
                if (b.comment.isNotBlank()) parts.add("comment=${rscQuote(b.comment)}")
                appendLine("add ${parts.joinToString(" ")}")
            }
            appendLine()
        }

        if (payload.walledGarden.isNotEmpty()) {
            appendLine("/ip hotspot walled-garden")
            payload.walledGarden.forEach { w ->
                val parts = mutableListOf(
                    "dst-host=${rscQuote(w.dstHost)}",
                    "action=${rscQuote(w.action)}",
                )
                if (w.dstPort.isNotBlank()) parts.add("dst-port=${rscQuote(w.dstPort)}")
                if (w.disabled) parts.add("disabled=yes")
                if (w.comment.isNotBlank()) parts.add("comment=${rscQuote(w.comment)}")
                appendLine("add ${parts.joinToString(" ")}")
            }
            appendLine()
        }
    }

    fun parseBackupContent(rawText: String): Result<RouterBackupPayload> = runCatching {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "Backup file is empty." }
        if (trimmed.startsWith("{")) {
            parseJsonBackup(trimmed)
        } else {
            parseRscBackup(trimmed)
        }
    }

    private fun parseJsonBackup(rawJson: String): RouterBackupPayload {
        val root = JSONObject(rawJson)
        val routerObj = root.optJSONObject("router")

        val ipAddresses = buildList {
            val arr = root.optJSONArray("ipAddresses") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val address = o.optString("address").trim()
                val iface = o.optString("interface").trim()
                if (address.isNotBlank() && iface.isNotBlank()) {
                    add(
                        IpAddressEntry(
                            id = "imp_ip_$i",
                            address = address,
                            network = o.optString("network").ifBlank {
                                address.substringBefore("/").substringBeforeLast(".") + ".0"
                            },
                            interfaceName = iface,
                            disabled = o.optBoolean("disabled", false),
                            comment = o.optString("comment", ""),
                        )
                    )
                }
            }
        }

        val profiles = buildList {
            val arr = root.optJSONArray("userProfiles") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isNotBlank()) {
                    add(
                        HotspotUserProfile(
                            id = "imp_prof_$i",
                            name = name,
                            sharedUsers = o.optString("sharedUsers", "1").ifBlank { "1" },
                            rateLimit = o.optString("rateLimit", "Unlimited").ifBlank { "Unlimited" },
                            sessionTimeout = o.optString("sessionTimeout", "None").ifBlank { "None" },
                        )
                    )
                }
            }
        }

        val hotspotUsers = buildList {
            val arr = root.optJSONArray("hotspotUsers") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isNotBlank()) {
                    add(
                        HotspotUser(
                            id = "imp_user_$i",
                            name = name,
                            password = o.optString("password", ""),
                            profile = o.optString("profile", "default").ifBlank { "default" },
                            limitBytesTotal = o.optLong("limitBytesTotal", 0L).coerceAtLeast(0L),
                            sessionTimeout = o.optString("sessionTimeout", ""),
                            disabled = o.optBoolean("disabled", false),
                            comment = o.optString("comment", ""),
                        )
                    )
                }
            }
        }

        val ipBindings = buildList {
            val arr = root.optJSONArray("ipBindings") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val mac = o.optString("macAddress", "").trim()
                val addr = o.optString("address", "").trim()
                if (mac.isNotBlank() || addr.isNotBlank()) {
                    add(
                        IpBindingEntry(
                            id = "imp_bind_$i",
                            macAddress = mac,
                            address = addr,
                            toAddress = o.optString("toAddress", addr),
                            type = o.optString("type", "bypassed").ifBlank { "bypassed" },
                            comment = o.optString("comment", ""),
                            disabled = o.optBoolean("disabled", false),
                        )
                    )
                }
            }
        }

        val walledGarden = buildList {
            val arr = root.optJSONArray("walledGarden") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val host = o.optString("dstHost", "").trim()
                if (host.isNotBlank()) {
                    add(
                        WalledGardenEntry(
                            id = "imp_wg_$i",
                            dstHost = host,
                            dstPort = o.optString("dstPort", ""),
                            action = o.optString("action", "allow").ifBlank { "allow" },
                            comment = o.optString("comment", ""),
                            disabled = o.optBoolean("disabled", false),
                        )
                    )
                }
            }
        }

        val quotaCfg = root.optJSONObject("quotaAlertConfig")?.let { q ->
            QuotaAlertConfig(
                enabled = q.optBoolean("enabled", true),
                globalQuotaLimitMb = q.optLong("globalQuotaLimitMb", 0L).coerceIn(0L, 10_000_000L),
                warningThresholdPercent = q.optInt("warningThresholdPercent", 80).coerceIn(50, 99),
                systemNotificationsEnabled = q.optBoolean("systemNotificationsEnabled", true),
            )
        }

        val identity = routerObj?.optString("identity")?.trim().orEmpty()
        require(
            identity.isNotBlank() ||
                ipAddresses.isNotEmpty() ||
                profiles.isNotEmpty() ||
                hotspotUsers.isNotEmpty() ||
                ipBindings.isNotEmpty() ||
                walledGarden.isNotEmpty()
        ) {
            "JSON file does not contain recognizable Erlkim NET or RouterOS backup sections."
        }

        return RouterBackupPayload(
            schemaVersion = root.optInt("schemaVersion", 1),
            sourceFormat = BackupExportFormat.JSON,
            exportedAt = root.optString("exportedAt", "Unknown date"),
            routerIdentity = identity.ifBlank { "MikroTik Router" },
            boardName = routerObj?.optString("boardName", "RouterOS") ?: "RouterOS",
            routerOsVersion = routerObj?.optString("version", "RouterOS v7") ?: "RouterOS v7",
            routerAddress = routerObj?.optString("address", "-") ?: "-",
            ipAddresses = ipAddresses,
            userProfiles = profiles,
            hotspotUsers = hotspotUsers,
            ipBindings = ipBindings,
            walledGarden = walledGarden,
            quotaAlertConfig = quotaCfg,
        )
    }

    private fun parseRscBackup(rawRsc: String): RouterBackupPayload {
        var currentSection = ""
        var identity = ""
        var exportedAt = "RouterOS Script"
        var routerAddress = "-"
        val ipAddresses = mutableListOf<IpAddressEntry>()
        val profiles = mutableListOf<HotspotUserProfile>()
        val users = mutableListOf<HotspotUser>()
        val bindings = mutableListOf<IpBindingEntry>()
        val walledGarden = mutableListOf<WalledGardenEntry>()

        rawRsc.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#")) {
                if (line.startsWith("# Exported:", ignoreCase = true)) {
                    exportedAt = line.substringAfter(":").trim()
                } else if (line.startsWith("# Host:", ignoreCase = true)) {
                    routerAddress = line.substringAfter(":").trim()
                }
                return@forEach
            }
            if (line.startsWith("/")) {
                currentSection = line.lowercase(Locale.US)
                return@forEach
            }

            val kv = parseRscKeyValues(line)
            when {
                currentSection == "/system identity" && line.startsWith("set ") -> {
                    kv["name"]?.takeIf { it.isNotBlank() }?.let { identity = it }
                }
                currentSection == "/ip address" && line.startsWith("add ") -> {
                    val addr = kv["address"].orEmpty()
                    val iface = kv["interface"].orEmpty()
                    if (addr.isNotBlank() && iface.isNotBlank()) {
                        ipAddresses.add(
                            IpAddressEntry(
                                id = "rsc_ip_${ipAddresses.size}",
                                address = addr,
                                network = addr.substringBefore("/").substringBeforeLast(".") + ".0",
                                interfaceName = iface,
                                disabled = kv["disabled"].equals("yes", true),
                                comment = kv["comment"].orEmpty(),
                            )
                        )
                    }
                }
                currentSection == "/ip hotspot user profile" && line.startsWith("add ") -> {
                    val name = kv["name"].orEmpty()
                    if (name.isNotBlank()) {
                        profiles.add(
                            HotspotUserProfile(
                                id = "rsc_prof_${profiles.size}",
                                name = name,
                                sharedUsers = kv["shared-users"].orEmpty().ifBlank { "1" },
                                rateLimit = kv["rate-limit"].orEmpty().ifBlank { "Unlimited" },
                                sessionTimeout = kv["session-timeout"].orEmpty().ifBlank { "None" },
                            )
                        )
                    }
                }
                currentSection == "/ip hotspot user" && line.startsWith("add ") -> {
                    val name = kv["name"].orEmpty()
                    if (name.isNotBlank()) {
                        users.add(
                            HotspotUser(
                                id = "rsc_user_${users.size}",
                                name = name,
                                password = kv["password"].orEmpty(),
                                profile = kv["profile"].orEmpty().ifBlank { "default" },
                                limitBytesTotal = kv["limit-bytes-total"]?.toLongOrNull() ?: 0L,
                                sessionTimeout = kv["limit-uptime"].orEmpty(),
                                disabled = kv["disabled"].equals("yes", true),
                                comment = kv["comment"].orEmpty(),
                            )
                        )
                    }
                }
                currentSection == "/ip hotspot ip-binding" && line.startsWith("add ") -> {
                    val mac = kv["mac-address"].orEmpty()
                    val addr = kv["address"].orEmpty()
                    if (mac.isNotBlank() || addr.isNotBlank()) {
                        bindings.add(
                            IpBindingEntry(
                                id = "rsc_bind_${bindings.size}",
                                macAddress = mac,
                                address = addr,
                                toAddress = kv["to-address"].orEmpty().ifBlank { addr },
                                type = kv["type"].orEmpty().ifBlank { "bypassed" },
                                comment = kv["comment"].orEmpty(),
                                disabled = kv["disabled"].equals("yes", true),
                            )
                        )
                    }
                }
                currentSection == "/ip hotspot walled-garden" && line.startsWith("add ") -> {
                    val host = kv["dst-host"].orEmpty()
                    if (host.isNotBlank()) {
                        walledGarden.add(
                            WalledGardenEntry(
                                id = "rsc_wg_${walledGarden.size}",
                                dstHost = host,
                                dstPort = kv["dst-port"].orEmpty(),
                                action = kv["action"].orEmpty().ifBlank { "allow" },
                                comment = kv["comment"].orEmpty(),
                                disabled = kv["disabled"].equals("yes", true),
                            )
                        )
                    }
                }
            }
        }

        require(
            identity.isNotBlank() ||
                ipAddresses.isNotEmpty() ||
                profiles.isNotEmpty() ||
                users.isNotEmpty() ||
                bindings.isNotEmpty() ||
                walledGarden.isNotEmpty()
        ) {
            "File does not contain valid Erlkim JSON or MikroTik .rsc configuration commands."
        }

        return RouterBackupPayload(
            schemaVersion = 1,
            sourceFormat = BackupExportFormat.RSC,
            exportedAt = exportedAt,
            routerIdentity = identity.ifBlank { "MikroTik Router" },
            boardName = "RouterOS Script",
            routerOsVersion = "RouterOS v7",
            routerAddress = routerAddress,
            ipAddresses = ipAddresses,
            userProfiles = profiles,
            hotspotUsers = users,
            ipBindings = bindings,
            walledGarden = walledGarden,
            quotaAlertConfig = null,
        )
    }

    private fun parseRscKeyValues(line: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val regex = Regex("""([a-zA-Z0-9_-]+)=("([^"\\]|\\.)*"|\S+)""")
        regex.findAll(line).forEach { match ->
            val key = match.groupValues[1]
            var value = match.groupValues[2]
            if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
                value = value.substring(1, value.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
            result[key] = value
        }
        return result
    }

    private fun rscQuote(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    suspend fun writeTextToUri(context: Context, uri: Uri, content: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = content.toByteArray(StandardCharsets.UTF_8)
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    OutputStreamWriter(out, StandardCharsets.UTF_8).use { writer ->
                        writer.write(content)
                    }
                } ?: error("Unable to open destination file for writing.")
                bytes.size
            }
        }

    suspend fun readTextFromUri(context: Context, uri: Uri): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileName = resolveDisplayName(context, uri)
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
                } ?: error("Unable to read selected backup file.")
                fileName to text
            }
        }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "router_backup.json"
    }
}
