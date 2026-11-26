package com.erlkim.mikrotik.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.erlkim.mikrotik.data.model.QuotaAlertConfig
import com.erlkim.mikrotik.data.model.ResourceAlertConfig
import com.erlkim.mikrotik.data.model.RouterConfig
import com.erlkim.mikrotik.data.model.ThemeMode
import com.erlkim.mikrotik.data.model.ThroughputAlertConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "erlkim_net_prefs")

data class PersistedSettings(
    val routerConfig: RouterConfig = RouterConfig(),
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val quotaAlertConfig: QuotaAlertConfig = QuotaAlertConfig(),
    val throughputAlertConfig: ThroughputAlertConfig = ThroughputAlertConfig(),
    val resourceAlertConfig: ResourceAlertConfig = ResourceAlertConfig(),
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("router_host")
        val PORT = intPreferencesKey("router_port")
        val USERNAME = stringPreferencesKey("router_username")
        val PASSWORD = stringPreferencesKey("router_password")
        val USE_HTTPS = booleanPreferencesKey("router_use_https")
        val TRUST_SELF_SIGNED = booleanPreferencesKey("router_trust_self_signed")
        val DEMO_MODE = booleanPreferencesKey("router_demo_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val QUOTA_ALERT_ENABLED = booleanPreferencesKey("quota_alert_enabled")
        val QUOTA_GLOBAL_LIMIT_MB = longPreferencesKey("quota_global_limit_mb")
        val QUOTA_WARNING_PERCENT = intPreferencesKey("quota_warning_percent")
        val QUOTA_SYSTEM_NOTIF = booleanPreferencesKey("quota_system_notif")
        val THROUGHPUT_ALERT_ENABLED = booleanPreferencesKey("throughput_alert_enabled")
        val THROUGHPUT_THRESHOLD_MBPS = floatPreferencesKey("throughput_threshold_mbps")
        val THROUGHPUT_COOLDOWN_SEC = intPreferencesKey("throughput_cooldown_sec")
        val THROUGHPUT_SYSTEM_NOTIF = booleanPreferencesKey("throughput_system_notif")
        val RESOURCE_ALERT_ENABLED = booleanPreferencesKey("resource_alert_enabled")
        val RESOURCE_CPU_THRESHOLD = intPreferencesKey("resource_cpu_threshold")
        val RESOURCE_MEM_THRESHOLD = intPreferencesKey("resource_mem_threshold")
        val RESOURCE_COOLDOWN_SEC = intPreferencesKey("resource_cooldown_sec")
        val RESOURCE_SYSTEM_NOTIF = booleanPreferencesKey("resource_system_notif")
    }

    val settingsFlow: Flow<PersistedSettings> = context.dataStore.data.map { prefs ->
        val modeName = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        val themeMode = runCatching { ThemeMode.valueOf(modeName) }.getOrDefault(ThemeMode.DARK)
        PersistedSettings(
            routerConfig = RouterConfig(
                host = prefs[Keys.HOST] ?: "192.168.88.1",
                port = prefs[Keys.PORT] ?: 80,
                username = prefs[Keys.USERNAME] ?: "admin",
                password = prefs[Keys.PASSWORD] ?: "",
                useHttps = prefs[Keys.USE_HTTPS] ?: false,
                trustSelfSigned = prefs[Keys.TRUST_SELF_SIGNED] ?: true,
                demoMode = prefs[Keys.DEMO_MODE] ?: false,
            ),
            themeMode = themeMode,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            quotaAlertConfig = QuotaAlertConfig(
                enabled = prefs[Keys.QUOTA_ALERT_ENABLED] ?: true,
                globalQuotaLimitMb = (prefs[Keys.QUOTA_GLOBAL_LIMIT_MB] ?: 500L).coerceAtLeast(0L),
                warningThresholdPercent = (prefs[Keys.QUOTA_WARNING_PERCENT] ?: 80).coerceIn(50, 99),
                systemNotificationsEnabled = prefs[Keys.QUOTA_SYSTEM_NOTIF] ?: true,
            ),
            throughputAlertConfig = ThroughputAlertConfig(
                enabled = prefs[Keys.THROUGHPUT_ALERT_ENABLED] ?: true,
                thresholdMbps = (prefs[Keys.THROUGHPUT_THRESHOLD_MBPS] ?: 50f).coerceIn(1f, 10_000f),
                cooldownSeconds = (prefs[Keys.THROUGHPUT_COOLDOWN_SEC] ?: 30).coerceIn(5, 3600),
                systemNotificationsEnabled = prefs[Keys.THROUGHPUT_SYSTEM_NOTIF] ?: true,
            ),
            resourceAlertConfig = ResourceAlertConfig(
                enabled = prefs[Keys.RESOURCE_ALERT_ENABLED] ?: true,
                cpuThresholdPercent = (prefs[Keys.RESOURCE_CPU_THRESHOLD] ?: 80).coerceIn(10, 99),
                memoryThresholdPercent = (prefs[Keys.RESOURCE_MEM_THRESHOLD] ?: 80).coerceIn(10, 99),
                cooldownSeconds = (prefs[Keys.RESOURCE_COOLDOWN_SEC] ?: 60).coerceIn(10, 3600),
                systemNotificationsEnabled = prefs[Keys.RESOURCE_SYSTEM_NOTIF] ?: true,
            ),
        )
    }

    suspend fun saveRouterConfig(config: RouterConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST] = config.host.trim()
            prefs[Keys.PORT] = config.port
            prefs[Keys.USERNAME] = config.username.trim()
            prefs[Keys.PASSWORD] = config.password
            prefs[Keys.USE_HTTPS] = config.useHttps
            prefs[Keys.TRUST_SELF_SIGNED] = config.trustSelfSigned
            prefs[Keys.DEMO_MODE] = config.demoMode
        }
    }

    suspend fun saveTheme(themeMode: ThemeMode, dynamicColor: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = themeMode.name
            prefs[Keys.DYNAMIC_COLOR] = dynamicColor
        }
    }

    suspend fun saveQuotaAlertConfig(config: QuotaAlertConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.QUOTA_ALERT_ENABLED] = config.enabled
            prefs[Keys.QUOTA_GLOBAL_LIMIT_MB] = config.globalQuotaLimitMb.coerceAtLeast(0L)
            prefs[Keys.QUOTA_WARNING_PERCENT] = config.warningThresholdPercent.coerceIn(50, 99)
            prefs[Keys.QUOTA_SYSTEM_NOTIF] = config.systemNotificationsEnabled
        }
    }

    suspend fun saveThroughputAlertConfig(config: ThroughputAlertConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THROUGHPUT_ALERT_ENABLED] = config.enabled
            prefs[Keys.THROUGHPUT_THRESHOLD_MBPS] = config.thresholdMbps.coerceIn(1f, 10_000f)
            prefs[Keys.THROUGHPUT_COOLDOWN_SEC] = config.cooldownSeconds.coerceIn(5, 3600)
            prefs[Keys.THROUGHPUT_SYSTEM_NOTIF] = config.systemNotificationsEnabled
        }
    }

    suspend fun saveResourceAlertConfig(config: ResourceAlertConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RESOURCE_ALERT_ENABLED] = config.enabled
            prefs[Keys.RESOURCE_CPU_THRESHOLD] = config.cpuThresholdPercent.coerceIn(10, 99)
            prefs[Keys.RESOURCE_MEM_THRESHOLD] = config.memoryThresholdPercent.coerceIn(10, 99)
            prefs[Keys.RESOURCE_COOLDOWN_SEC] = config.cooldownSeconds.coerceIn(10, 3600)
            prefs[Keys.RESOURCE_SYSTEM_NOTIF] = config.systemNotificationsEnabled
        }
    }
}
