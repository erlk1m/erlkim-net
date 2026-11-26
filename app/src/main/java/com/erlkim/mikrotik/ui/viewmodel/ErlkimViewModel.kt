package com.erlkim.mikrotik.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erlkim.mikrotik.data.backup.BackupExportFormat
import com.erlkim.mikrotik.data.backup.BackupRestoreOptions
import com.erlkim.mikrotik.data.backup.ImportedBackupPreview
import com.erlkim.mikrotik.data.backup.RestoreSummary
import com.erlkim.mikrotik.data.backup.RouterBackupManager
import com.erlkim.mikrotik.data.backup.RouterBackupPayload
import com.erlkim.mikrotik.data.model.BandwidthQuotaAlert
import com.erlkim.mikrotik.data.model.ConnectionStatus
import com.erlkim.mikrotik.data.model.DhcpLeaseEntry
import com.erlkim.mikrotik.data.model.DhcpServerEntry
import com.erlkim.mikrotik.data.model.HotspotActiveSession
import com.erlkim.mikrotik.data.model.HotspotServer
import com.erlkim.mikrotik.data.model.HotspotUser
import com.erlkim.mikrotik.data.model.HotspotUserProfile
import com.erlkim.mikrotik.data.model.IpAddressEntry
import com.erlkim.mikrotik.data.model.IpBindingEntry
import com.erlkim.mikrotik.data.model.LogSeverity
import com.erlkim.mikrotik.data.model.QuotaAlertConfig
import com.erlkim.mikrotik.data.model.QuotaAlertSeverity
import com.erlkim.mikrotik.data.model.ResourceAlertConfig
import com.erlkim.mikrotik.data.model.ResourceAlertEvent
import com.erlkim.mikrotik.data.model.ResourceMetricType
import com.erlkim.mikrotik.data.model.RouterConfig
import com.erlkim.mikrotik.data.model.RouterInterface
import com.erlkim.mikrotik.data.model.RouterLogEntry
import com.erlkim.mikrotik.data.model.RouterResource
import com.erlkim.mikrotik.data.model.RouterSystemUser
import com.erlkim.mikrotik.data.model.ThemeMode
import com.erlkim.mikrotik.data.model.ThroughputAlertConfig
import com.erlkim.mikrotik.data.model.ThroughputAlertEvent
import com.erlkim.mikrotik.data.model.TrafficSample
import com.erlkim.mikrotik.data.model.UserFilter
import com.erlkim.mikrotik.data.model.WalledGardenEntry
import com.erlkim.mikrotik.data.model.WirelessInterface
import com.erlkim.mikrotik.data.model.formatBytes
import com.erlkim.mikrotik.data.network.ConnectionTestResult
import com.erlkim.mikrotik.data.network.DemoRouterEngine
import com.erlkim.mikrotik.data.network.MikroTikRestClient
import com.erlkim.mikrotik.data.network.OperationalSnapshot
import com.erlkim.mikrotik.data.network.PingDiagnosticEngine
import com.erlkim.mikrotik.data.network.PingDiagnosticState
import com.erlkim.mikrotik.data.network.RouterSnapshot
import com.erlkim.mikrotik.data.preferences.SettingsDataStore
import com.erlkim.mikrotik.notifications.QuotaNotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ErlkimUiState(
    val routerConfig: RouterConfig = RouterConfig(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Connecting,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val resource: RouterResource = RouterResource(),
    val trafficHistory: List<TrafficSample> = emptyList(),
    val currentDownloadMbps: Float = 0f,
    val currentUploadMbps: Float = 0f,
    val interfaces: List<RouterInterface> = emptyList(),
    val ipAddresses: List<IpAddressEntry> = emptyList(),
    val dhcpServers: List<DhcpServerEntry> = emptyList(),
    val dhcpLeases: List<DhcpLeaseEntry> = emptyList(),
    val wirelessInterfaces: List<WirelessInterface> = emptyList(),
    val hotspotServers: List<HotspotServer> = emptyList(),
    val hotspotUsers: List<HotspotUser> = emptyList(),
    val activeSessions: List<HotspotActiveSession> = emptyList(),
    val userProfiles: List<HotspotUserProfile> = emptyList(),
    val ipBindings: List<IpBindingEntry> = emptyList(),
    val walledGarden: List<WalledGardenEntry> = emptyList(),
    val systemUsers: List<RouterSystemUser> = emptyList(),
    val logs: List<RouterLogEntry> = emptyList(),
    val quotaAlertConfig: QuotaAlertConfig = QuotaAlertConfig(),
    val quotaAlerts: List<BandwidthQuotaAlert> = emptyList(),
    val throughputAlertConfig: ThroughputAlertConfig = ThroughputAlertConfig(),
    val throughputAlerts: List<ThroughputAlertEvent> = emptyList(),
    val resourceAlertConfig: ResourceAlertConfig = ResourceAlertConfig(),
    val resourceAlerts: List<ResourceAlertEvent> = emptyList(),
    val pingState: PingDiagnosticState = PingDiagnosticState(),
    val userSearchQuery: String = "",
    val selectedUserFilter: UserFilter = UserFilter.ALL,
    val settingsSearchQuery: String = "",
    val selectedTrafficInterface: String = "",
    val activeModal: ActiveModal? = null,
    val dashboardFeatures: DashboardFeaturesUiState = DashboardFeaturesUiState(),
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val isPollingActive: Boolean = false,
    val lastTelemetrySyncMs: Long = 0L,
    val isLoadingProfiles: Boolean = false,
    val profilesFetchError: String? = null,
    val isBulkDeleting: Boolean = false,
) {
    val currentTotalThroughputMbps: Float
        get() = currentDownloadMbps + currentUploadMbps

    val isThroughputThresholdExceeded: Boolean
        get() = throughputAlertConfig.enabled &&
            connectionStatus.isConnected &&
            currentTotalThroughputMbps > throughputAlertConfig.thresholdMbps

    val unreadThroughputAlertCount: Int
        get() = throughputAlerts.count { !it.isAcknowledged }

    val isCpuThresholdExceeded: Boolean
        get() = resourceAlertConfig.enabled &&
            connectionStatus.isConnected &&
            resource.cpuLoadPercent > resourceAlertConfig.cpuThresholdPercent

    val isMemoryThresholdExceeded: Boolean
        get() = resourceAlertConfig.enabled &&
            connectionStatus.isConnected &&
            resource.ramUsagePercent > resourceAlertConfig.memoryThresholdPercent

    val isResourceThresholdExceeded: Boolean
        get() = isCpuThresholdExceeded || isMemoryThresholdExceeded

    val unreadResourceAlertCount: Int
        get() = resourceAlerts.count { !it.isAcknowledged }

    val unreadQuotaAlertCount: Int
        get() = quotaAlerts.count { !it.isAcknowledged }

    val exceededQuotaCount: Int
        get() = quotaAlerts.count { it.severity == QuotaAlertSeverity.EXCEEDED }

    fun quotaAlertForUser(username: String): BandwidthQuotaAlert? =
        quotaAlerts.firstOrNull { it.username.equals(username, ignoreCase = true) }

    val activeHotspotCount: Int
        get() = if (connectionStatus is ConnectionStatus.ConnectedDemo) {
            // Reflect realistic active counter plus any added/kicked sessions
            (39 + activeSessions.size).coerceAtLeast(activeSessions.size)
        } else {
            activeSessions.size
        }

    val primaryHotspotName: String
        get() = hotspotServers.firstOrNull()?.name ?: "My_MikroTik_Hotspot"

    fun activeIpForUser(username: String): String? =
        activeSessions.firstOrNull { it.user.equals(username, ignoreCase = true) }?.address?.takeIf { it.isNotBlank() }

    val filteredUsers: List<HotspotUser>
        get() {
            val query = userSearchQuery.trim()
            val sessionsByUser = activeSessions.associateBy { it.user.lowercase() }
            return hotspotUsers.filter { user ->
                val session = sessionsByUser[user.name.lowercase()]
                val matchesQuery = query.isBlank() ||
                    user.name.contains(query, ignoreCase = true) ||
                    user.profile.contains(query, ignoreCase = true) ||
                    user.comment.contains(query, ignoreCase = true) ||
                    (session?.address?.contains(query, ignoreCase = true) == true) ||
                    (session?.macAddress?.contains(query, ignoreCase = true) == true)

                val matchesFilter = when (selectedUserFilter) {
                    UserFilter.ALL -> true
                    UserFilter.ACTIVE -> user.isActive && !user.disabled
                    UserFilter.EXPIRED -> user.isExpired || user.disabled
                    UserFilter.VIP -> user.isVip
                }
                matchesQuery && matchesFilter
            }
        }
}

class ErlkimViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)
    private val restClient = MikroTikRestClient()
    private val demoEngine = DemoRouterEngine()
    private val pingEngine = PingDiagnosticEngine(restClient)

    private val _uiState = MutableStateFlow(ErlkimUiState())
    val uiState: StateFlow<ErlkimUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private var connectJob: Job? = null
    private var refreshJob: Job? = null
    private var pollingJob: Job? = null
    private var pingJob: Job? = null
    private var interfacesRefreshJob: Job? = null
    private var ipAddressesRefreshJob: Job? = null
    private var dhcpRefreshJob: Job? = null
    private var systemHealthRefreshJob: Job? = null
    private var ipBindingsRefreshJob: Job? = null
    private var consecutivePollFailures: Int = 0
    private var isAppInForeground: Boolean = true
    private val cacheStaleThresholdMs: Long = 8_000L
    private val notifiedAlertKeys = mutableSetOf<String>()
    private var lastThroughputAlertEpochMs: Long = 0L
    private var lastCpuAlertEpochMs: Long = 0L
    private var lastMemoryAlertEpochMs: Long = 0L
    private val interfaceHistoryBuffers = mutableMapOf<String, MutableList<TrafficSample>>()

    private val timeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
    private val exportTimestampFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    private fun currentTimeString(): String = timeFormat.get()!!.format(Date())

    init {
        QuotaNotificationHelper.ensureChannel(application)
        viewModelScope.launch {
            val saved = dataStore.settingsFlow.first()
            _uiState.update {
                it.copy(
                    routerConfig = saved.routerConfig,
                    themeMode = saved.themeMode,
                    dynamicColor = saved.dynamicColor,
                    quotaAlertConfig = saved.quotaAlertConfig,
                    throughputAlertConfig = saved.throughputAlertConfig,
                    resourceAlertConfig = saved.resourceAlertConfig,
                    pingState = it.pingState.copy(
                        targetHost = saved.routerConfig.host.ifBlank { "192.168.88.1" },
                    ),
                )
            }
            connectWithConfig(saved.routerConfig, persist = false)
        }
    }

    fun openModal(modal: ActiveModal) {
        _uiState.update { it.copy(activeModal = modal) }
        val now = System.currentTimeMillis()
        val state = _uiState.value
        val features = state.dashboardFeatures
        when (modal) {
            ActiveModal.Interfaces -> {
                ensureTelemetryAlive()
                if (state.connectionStatus.isConnected && now - features.interfacesState.lastSyncedMs > cacheStaleThresholdMs) {
                    refreshInterfacesSection(silent = true)
                }
            }
            ActiveModal.IpAddresses -> {
                ensureTelemetryAlive()
                if (state.connectionStatus.isConnected && now - features.ipAddressesState.lastSyncedMs > cacheStaleThresholdMs) {
                    refreshIpAddressesSection(silent = true)
                }
            }
            ActiveModal.DhcpServer -> {
                ensureTelemetryAlive()
                if (state.connectionStatus.isConnected && now - features.dhcpState.lastSyncedMs > cacheStaleThresholdMs) {
                    refreshDhcpSection(silent = true)
                }
            }
            ActiveModal.RebootShutdown -> {
                ensureTelemetryAlive()
                if (state.connectionStatus.isConnected && now - features.systemState.lastSyncedMs > cacheStaleThresholdMs) {
                    refreshSystemHealthSection(silent = true)
                }
            }
            ActiveModal.IpBindings -> {
                ensureTelemetryAlive()
                if (state.connectionStatus.isConnected && now - features.ipBindingsState.lastSyncedMs > cacheStaleThresholdMs) {
                    refreshIpBindingsSection(silent = true)
                }
            }
            ActiveModal.Wireless -> ensureTelemetryAlive()
            else -> Unit
        }
    }

    fun closeModal() {
        _uiState.update { it.copy(activeModal = null) }
    }

    fun updateInterfacesUiState(transform: (InterfacesFeatureUiState) -> InterfacesFeatureUiState) {
        _uiState.update { state ->
            state.copy(
                dashboardFeatures = state.dashboardFeatures.copy(
                    interfacesState = transform(state.dashboardFeatures.interfacesState),
                ),
            )
        }
    }

    fun updateIpAddressesUiState(transform: (IpAddressesFeatureUiState) -> IpAddressesFeatureUiState) {
        _uiState.update { state ->
            state.copy(
                dashboardFeatures = state.dashboardFeatures.copy(
                    ipAddressesState = transform(state.dashboardFeatures.ipAddressesState),
                ),
            )
        }
    }

    fun updateDhcpUiState(transform: (DhcpServerFeatureUiState) -> DhcpServerFeatureUiState) {
        _uiState.update { state ->
            state.copy(
                dashboardFeatures = state.dashboardFeatures.copy(
                    dhcpState = transform(state.dashboardFeatures.dhcpState),
                ),
            )
        }
    }

    fun updateIpBindingsUiState(transform: (IpBindingsFeatureUiState) -> IpBindingsFeatureUiState) {
        _uiState.update { state ->
            state.copy(
                dashboardFeatures = state.dashboardFeatures.copy(
                    ipBindingsState = transform(state.dashboardFeatures.ipBindingsState),
                ),
            )
        }
    }

    fun toggleSettingsSection(sectionId: String) {
        _uiState.update { state ->
            val current = state.dashboardFeatures.settingsState.expandedSections
            val updated = if (sectionId in current) current - sectionId else current + sectionId
            state.copy(
                dashboardFeatures = state.dashboardFeatures.copy(
                    settingsState = state.dashboardFeatures.settingsState.copy(expandedSections = updated),
                ),
            )
        }
    }

    fun setAllSettingsSectionsExpanded(sectionIds: Set<String>) {
        _uiState.update { state ->
            state.copy(
                dashboardFeatures = state.dashboardFeatures.copy(
                    settingsState = state.dashboardFeatures.settingsState.copy(expandedSections = sectionIds),
                ),
            )
        }
    }

    fun onTabChanged(@Suppress("UNUSED_PARAMETER") route: String) {
        // Keep shared telemetry and cached dashboard data uninterrupted across tab switches
        ensureTelemetryAlive()
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        isAppInForeground = inForeground
        if (inForeground) {
            ensureTelemetryAlive()
        } else {
            pollingJob?.cancel()
            pollingJob = null
            _uiState.update { it.copy(isPollingActive = false) }
        }
    }

    fun ensureTelemetryAlive() {
        val state = _uiState.value
        if (isAppInForeground && state.connectionStatus.isConnected && pollingJob?.isActive != true) {
            startTelemetryPolling()
        }
    }

    fun reconnectNow() {
        connectWithConfig(_uiState.value.routerConfig, persist = false)
    }

    fun refreshInterfacesSection(silent: Boolean = false) {
        if (interfacesRefreshJob?.isActive == true) return
        val state = _uiState.value
        interfacesRefreshJob = viewModelScope.launch {
            if (!silent) {
                updateInterfacesUiState { it.copy(isRefreshingSection = true) }
            }
            try {
                if (state.routerConfig.demoMode || state.connectionStatus is ConnectionStatus.ConnectedDemo) {
                    if (!silent) delay(220L)
                    val snap = demoEngine.snapshot()
                    val now = System.currentTimeMillis()
                    _uiState.update { current ->
                        current.copy(
                            interfaces = snap.interfaces,
                            lastTelemetrySyncMs = now,
                            dashboardFeatures = current.dashboardFeatures.copy(
                                interfacesState = current.dashboardFeatures.interfacesState.copy(
                                    lastSyncedMs = now,
                                    isRefreshingSection = false,
                                ),
                            ),
                        )
                    }
                    if (!silent) _snackbarMessage.tryEmit("Daftar interface diperbarui")
                    ensureTelemetryAlive()
                    return@launch
                }

                restClient.fetchInterfaces(state.routerConfig)
                    .onSuccess { list ->
                        val now = System.currentTimeMillis()
                        consecutivePollFailures = 0
                        _uiState.update { current ->
                            current.copy(
                                connectionStatus = ConnectionStatus.ConnectedLive,
                                interfaces = list,
                                lastTelemetrySyncMs = now,
                                dashboardFeatures = current.dashboardFeatures.copy(
                                    interfacesState = current.dashboardFeatures.interfacesState.copy(
                                        lastSyncedMs = now,
                                        isRefreshingSection = false,
                                    ),
                                ),
                            )
                        }
                        if (!silent) _snackbarMessage.tryEmit("Daftar interface diperbarui (${list.size} port)")
                        ensureTelemetryAlive()
                    }
                    .onFailure { err ->
                        if (!silent) {
                            _snackbarMessage.tryEmit("Gagal menyegarkan interface: ${err.message ?: "Error RouterOS"}")
                        }
                    }
            } finally {
                updateInterfacesUiState { it.copy(isRefreshingSection = false) }
            }
        }
    }

    fun refreshIpAddressesSection(silent: Boolean = false) {
        if (ipAddressesRefreshJob?.isActive == true) return
        val state = _uiState.value
        ipAddressesRefreshJob = viewModelScope.launch {
            if (!silent) {
                updateIpAddressesUiState { it.copy(isRefreshingSection = true) }
            }
            try {
                if (state.routerConfig.demoMode || state.connectionStatus is ConnectionStatus.ConnectedDemo) {
                    if (!silent) delay(220L)
                    val snap = demoEngine.snapshot()
                    val now = System.currentTimeMillis()
                    _uiState.update { current ->
                        current.copy(
                            ipAddresses = snap.ipAddresses,
                            interfaces = snap.interfaces,
                            lastTelemetrySyncMs = now,
                            dashboardFeatures = current.dashboardFeatures.copy(
                                ipAddressesState = current.dashboardFeatures.ipAddressesState.copy(
                                    lastSyncedMs = now,
                                    isRefreshingSection = false,
                                ),
                            ),
                        )
                    }
                    if (!silent) _snackbarMessage.tryEmit("Daftar IP Address diperbarui")
                    ensureTelemetryAlive()
                    return@launch
                }

                restClient.fetchIpAddresses(state.routerConfig)
                    .onSuccess { list ->
                        val now = System.currentTimeMillis()
                        consecutivePollFailures = 0
                        _uiState.update { current ->
                            current.copy(
                                connectionStatus = ConnectionStatus.ConnectedLive,
                                ipAddresses = list,
                                lastTelemetrySyncMs = now,
                                dashboardFeatures = current.dashboardFeatures.copy(
                                    ipAddressesState = current.dashboardFeatures.ipAddressesState.copy(
                                        lastSyncedMs = now,
                                        isRefreshingSection = false,
                                    ),
                                ),
                            )
                        }
                        if (!silent) _snackbarMessage.tryEmit("Daftar IP Address diperbarui (${list.size} entri)")
                        ensureTelemetryAlive()
                    }
                    .onFailure { err ->
                        if (!silent) {
                            _snackbarMessage.tryEmit("Gagal menyegarkan IP Address: ${err.message ?: "Error RouterOS"}")
                        }
                    }
            } finally {
                updateIpAddressesUiState { it.copy(isRefreshingSection = false) }
            }
        }
    }

    fun refreshDhcpSection(silent: Boolean = false) {
        if (dhcpRefreshJob?.isActive == true) return
        val state = _uiState.value
        dhcpRefreshJob = viewModelScope.launch {
            if (!silent) {
                updateDhcpUiState { it.copy(isRefreshingSection = true) }
            }
            try {
                if (state.routerConfig.demoMode || state.connectionStatus is ConnectionStatus.ConnectedDemo) {
                    if (!silent) delay(220L)
                    val snap = demoEngine.snapshot()
                    val now = System.currentTimeMillis()
                    _uiState.update { current ->
                        current.copy(
                            dhcpServers = snap.dhcpServers,
                            dhcpLeases = snap.dhcpLeases,
                            lastTelemetrySyncMs = now,
                            dashboardFeatures = current.dashboardFeatures.copy(
                                dhcpState = current.dashboardFeatures.dhcpState.copy(
                                    lastSyncedMs = now,
                                    isRefreshingSection = false,
                                ),
                            ),
                        )
                    }
                    if (!silent) _snackbarMessage.tryEmit("Data DHCP Server & Leases diperbarui")
                    ensureTelemetryAlive()
                    return@launch
                }

                restClient.fetchDhcpData(state.routerConfig)
                    .onSuccess { (servers, leases) ->
                        val now = System.currentTimeMillis()
                        consecutivePollFailures = 0
                        _uiState.update { current ->
                            current.copy(
                                connectionStatus = ConnectionStatus.ConnectedLive,
                                dhcpServers = servers,
                                dhcpLeases = leases,
                                lastTelemetrySyncMs = now,
                                dashboardFeatures = current.dashboardFeatures.copy(
                                    dhcpState = current.dashboardFeatures.dhcpState.copy(
                                        lastSyncedMs = now,
                                        isRefreshingSection = false,
                                    ),
                                ),
                            )
                        }
                        if (!silent) _snackbarMessage.tryEmit("Data DHCP diperbarui (${servers.size} server, ${leases.size} lease)")
                        ensureTelemetryAlive()
                    }
                    .onFailure { err ->
                        if (!silent) {
                            _snackbarMessage.tryEmit("Gagal menyegarkan DHCP: ${err.message ?: "Error RouterOS"}")
                        }
                    }
            } finally {
                updateDhcpUiState { it.copy(isRefreshingSection = false) }
            }
        }
    }

    fun refreshSystemHealthSection(silent: Boolean = false) {
        if (systemHealthRefreshJob?.isActive == true) return
        val state = _uiState.value
        systemHealthRefreshJob = viewModelScope.launch {
            if (!silent) {
                _uiState.update { current ->
                    current.copy(
                        dashboardFeatures = current.dashboardFeatures.copy(
                            systemState = current.dashboardFeatures.systemState.copy(isRefreshingSection = true),
                        ),
                    )
                }
            }
            try {
                if (state.routerConfig.demoMode || state.connectionStatus is ConnectionStatus.ConnectedDemo) {
                    if (!silent) delay(220L)
                    val (res, sample) = demoEngine.nextTelemetryTick()
                    pushTelemetry(res, sample)
                    if (!silent) _snackbarMessage.tryEmit("Status sistem router diperbarui")
                    ensureTelemetryAlive()
                    return@launch
                }

                restClient.fetchSystemHealth(state.routerConfig)
                    .onSuccess { res ->
                        val now = System.currentTimeMillis()
                        consecutivePollFailures = 0
                        _uiState.update { current ->
                            current.copy(
                                connectionStatus = ConnectionStatus.ConnectedLive,
                                resource = res,
                                lastTelemetrySyncMs = now,
                                dashboardFeatures = current.dashboardFeatures.copy(
                                    systemState = current.dashboardFeatures.systemState.copy(
                                        lastSyncedMs = now,
                                        isRefreshingSection = false,
                                    ),
                                ),
                            )
                        }
                        evaluateResourceAlerts()
                        if (!silent) _snackbarMessage.tryEmit("Status sistem router diperbarui")
                        ensureTelemetryAlive()
                    }
                    .onFailure { err ->
                        if (!silent) {
                            _snackbarMessage.tryEmit("Gagal menyegarkan status sistem: ${err.message ?: "Error RouterOS"}")
                        }
                    }
            } finally {
                _uiState.update { current ->
                    current.copy(
                        dashboardFeatures = current.dashboardFeatures.copy(
                            systemState = current.dashboardFeatures.systemState.copy(isRefreshingSection = false),
                        ),
                    )
                }
            }
        }
    }

    fun refreshIpBindingsSection(silent: Boolean = false) {
        if (ipBindingsRefreshJob?.isActive == true) return
        val state = _uiState.value
        ipBindingsRefreshJob = viewModelScope.launch {
            if (!silent) {
                updateIpBindingsUiState { it.copy(isRefreshingSection = true) }
            }
            try {
                if (state.routerConfig.demoMode || state.connectionStatus is ConnectionStatus.ConnectedDemo) {
                    if (!silent) delay(220L)
                    val snap = demoEngine.snapshot()
                    val now = System.currentTimeMillis()
                    _uiState.update { current ->
                        current.copy(
                            ipBindings = snap.ipBindings,
                            hotspotServers = snap.hotspotServers,
                            activeSessions = snap.activeSessions,
                            dhcpLeases = snap.dhcpLeases,
                            lastTelemetrySyncMs = now,
                            dashboardFeatures = current.dashboardFeatures.copy(
                                ipBindingsState = current.dashboardFeatures.ipBindingsState.copy(
                                    lastSyncedMs = now,
                                    isRefreshingSection = false,
                                ),
                            ),
                        )
                    }
                    if (!silent) _snackbarMessage.tryEmit("Daftar IP Bindings diperbarui")
                    ensureTelemetryAlive()
                    return@launch
                }

                restClient.fetchIpBindings(state.routerConfig)
                    .onSuccess { list ->
                        val now = System.currentTimeMillis()
                        consecutivePollFailures = 0
                        _uiState.update { current ->
                            current.copy(
                                connectionStatus = ConnectionStatus.ConnectedLive,
                                ipBindings = list,
                                lastTelemetrySyncMs = now,
                                dashboardFeatures = current.dashboardFeatures.copy(
                                    ipBindingsState = current.dashboardFeatures.ipBindingsState.copy(
                                        lastSyncedMs = now,
                                        isRefreshingSection = false,
                                    ),
                                ),
                            )
                        }
                        if (!silent) _snackbarMessage.tryEmit("Daftar IP Bindings diperbarui (${list.size} aturan)")
                        ensureTelemetryAlive()
                    }
                    .onFailure { err ->
                        if (!silent) {
                            _snackbarMessage.tryEmit("Gagal menyegarkan IP Bindings: ${err.message ?: "Error RouterOS"}")
                        }
                    }
            } finally {
                updateIpBindingsUiState { it.copy(isRefreshingSection = false) }
            }
        }
    }

    fun connectWithConfig(config: RouterConfig, persist: Boolean = true) {
        connectJob?.cancel()
        pollingJob?.cancel()
        consecutivePollFailures = 0
        connectJob = viewModelScope.launch {
            try {
                if (persist) {
                    dataStore.saveRouterConfig(config)
                }
                _uiState.update {
                    val updatedPing = if (!it.pingState.hasCustomTarget && config.host.isNotBlank()) {
                        it.pingState.copy(targetHost = config.host)
                    } else {
                        it.pingState
                    }
                    it.copy(
                        routerConfig = config,
                        connectionStatus = ConnectionStatus.Connecting,
                        isRefreshing = true,
                        isPollingActive = false,
                        pingState = updatedPing,
                    )
                }

                if (config.demoMode) {
                    val snap = demoEngine.snapshot()
                    val history = demoEngine.initialTrafficHistory()
                    val last = history.last()
                    applySnapshot(
                        snapshot = snap,
                        status = ConnectionStatus.ConnectedDemo,
                        initialHistory = history,
                        rx = last.downloadMbps,
                        tx = last.uploadMbps,
                    )
                    appendLog("Mode Demo Aktif", "Simulasi lokal RB3011UiAS (RouterOS v7.12) berjalan.")
                    startTelemetryPolling()
                    return@launch
                }

                val result = restClient.fetchSnapshot(config)
                result.onSuccess { snap ->
                    val initialHistory = List(12) { TrafficSample(0f, 0f) }
                    applySnapshot(
                        snapshot = snap,
                        status = ConnectionStatus.ConnectedLive,
                        initialHistory = initialHistory,
                        rx = 0f,
                        tx = 0f,
                    )
                    appendLog("Terhubung ke RouterOS", "${snap.resource.identity} (${config.displayAddress}) online.")
                    _snackbarMessage.tryEmit("Terhubung ke ${snap.resource.identity}")
                    startTelemetryPolling()
                }.onFailure { err ->
                    val msg = err.message ?: "Router tidak dapat dijangkau"
                    _uiState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.Disconnected(msg),
                            isRefreshing = false,
                            isPollingActive = false,
                            trafficHistory = emptyList(),
                            currentDownloadMbps = 0f,
                            currentUploadMbps = 0f,
                        )
                    }
                    appendLog("Koneksi Gagal", "${config.displayAddress}: $msg", isAlert = true)
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun saveRouterSettingsOnly(config: RouterConfig) {
        viewModelScope.launch {
            dataStore.saveRouterConfig(config)
            _uiState.update { it.copy(routerConfig = config) }
            appendLog("Router Settings Disimpan", "Konfigurasi ${config.displayAddress} (${config.username}) tersimpan di DataStore.")
            _snackbarMessage.tryEmit("Pengaturan router berhasil disimpan")
            ensureTelemetryAlive()
        }
    }

    fun testRouterConnection(
        config: RouterConfig,
        onResult: (Result<ConnectionTestResult>) -> Unit,
    ) {
        viewModelScope.launch {
            if (config.demoMode) {
                delay(280L)
                val res = demoEngine.snapshot().resource
                onResult(
                    Result.success(
                        ConnectionTestResult(
                            identity = res.identity,
                            boardName = res.boardName,
                            routerOsVersion = res.version,
                            latencyMs = 12L,
                            endpointUrl = "Mode Demo (Simulasi Lokal)",
                        ),
                    ),
                )
                return@launch
            }
            val result = restClient.testConnection(config)
            onResult(result)
        }
    }

    fun enableDemoMode() {
        val demoConfig = _uiState.value.routerConfig.copy(demoMode = true)
        connectWithConfig(demoConfig, persist = true)
        _snackbarMessage.tryEmit("Mode Demo (Simulasi Lokal) diaktifkan")
    }

    fun refreshNow() {
        val current = _uiState.value
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isRefreshing = true) }
                val config = current.routerConfig
                if (config.demoMode) {
                    delay(350L)
                    val (res, sample) = demoEngine.nextTelemetryTick()
                    val snap = demoEngine.snapshot().copy(resource = res)
                    applySnapshot(
                        snapshot = snap,
                        status = ConnectionStatus.ConnectedDemo,
                        rx = sample.downloadMbps,
                        tx = sample.uploadMbps,
                    )
                    pushTelemetry(res, sample)
                    ensureTelemetryAlive()
                    appendLog("Data Diperbarui", "Refresh manual Mode Demo selesai.")
                    _snackbarMessage.tryEmit("Data router diperbarui")
                    return@launch
                }

                val result = restClient.fetchSnapshot(config)
                result.onSuccess { snap ->
                    consecutivePollFailures = 0
                    applySnapshot(snapshot = snap, status = ConnectionStatus.ConnectedLive)
                    appendLog("Data Diperbarui", "Sinkronisasi RouterOS (${config.displayAddress}) berhasil.")
                    _snackbarMessage.tryEmit("Data router diperbarui")
                    ensureTelemetryAlive()
                }.onFailure { err ->
                    val msg = err.message ?: "Router tidak dapat dijangkau"
                    _uiState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.Disconnected(msg),
                            isRefreshing = false,
                        )
                    }
                    appendLog("Gagal Memperbarui", "${config.displayAddress}: $msg", isAlert = true)
                    _snackbarMessage.tryEmit("Gagal memuat data: $msg")
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun applySnapshot(
        snapshot: RouterSnapshot,
        status: ConnectionStatus,
        initialHistory: List<TrafficSample>? = null,
        rx: Float? = null,
        tx: Float? = null,
    ) {
        _uiState.update { state ->
            val defaultIface = state.selectedTrafficInterface.ifBlank {
                snapshot.interfaces.firstOrNull {
                    it.running && !it.disabled && (it.name.contains("wan", true) || it.name.startsWith("ether1", true))
                }?.name ?: snapshot.interfaces.firstOrNull { it.running && !it.disabled }?.name.orEmpty()
            }
            val mergedLogs = if (snapshot.systemLogs.isNotEmpty()) {
                val localOnly = state.logs.filter { it.id.startsWith("local_") }
                (localOnly + snapshot.systemLogs).distinctBy { it.id }.take(140)
            } else {
                state.logs
            }
            val resolvedHistory = if (initialHistory != null) {
                val buf = interfaceHistoryBuffers.getOrPut(defaultIface) { mutableListOf() }
                buf.clear()
                buf.addAll(initialHistory.takeLast(60))
                buf.toList()
            } else {
                interfaceHistoryBuffers[defaultIface]?.toList() ?: state.trafficHistory
            }
            val now = System.currentTimeMillis()
            val updatedFeatures = state.dashboardFeatures.copy(
                interfacesState = state.dashboardFeatures.interfacesState.copy(lastSyncedMs = now),
                ipAddressesState = state.dashboardFeatures.ipAddressesState.copy(lastSyncedMs = now),
                dhcpState = state.dashboardFeatures.dhcpState.copy(lastSyncedMs = now),
                systemState = state.dashboardFeatures.systemState.copy(lastSyncedMs = now),
                ipBindingsState = state.dashboardFeatures.ipBindingsState.copy(lastSyncedMs = now),
            )
            state.copy(
                connectionStatus = status,
                isRefreshing = false,
                lastTelemetrySyncMs = now,
                dashboardFeatures = updatedFeatures,
                resource = snapshot.resource,
                trafficHistory = resolvedHistory,
                currentDownloadMbps = rx ?: state.currentDownloadMbps,
                currentUploadMbps = tx ?: state.currentUploadMbps,
                interfaces = snapshot.interfaces,
                selectedTrafficInterface = defaultIface,
                ipAddresses = snapshot.ipAddresses,
                dhcpServers = snapshot.dhcpServers,
                dhcpLeases = snapshot.dhcpLeases,
                wirelessInterfaces = snapshot.wirelessInterfaces,
                hotspotServers = snapshot.hotspotServers,
                hotspotUsers = snapshot.hotspotUsers,
                activeSessions = snapshot.activeSessions,
                userProfiles = snapshot.userProfiles,
                ipBindings = snapshot.ipBindings,
                walledGarden = snapshot.walledGarden,
                systemUsers = snapshot.systemUsers,
                logs = mergedLogs,
            )
        }
        evaluateQuotaAlerts()
        evaluateThroughputAlert()
        evaluateResourceAlerts()
    }

    private fun startTelemetryPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            _uiState.update { it.copy(isPollingActive = true) }
            var pollCycle = 0
            try {
                while (isActive) {
                    delay(2500L)
                    pollCycle++
                    val state = _uiState.value
                    when (state.connectionStatus) {
                        ConnectionStatus.ConnectedDemo -> {
                            val (res, baseSample) = demoEngine.nextTelemetryTick()
                            val selectedIface = state.interfaces.firstOrNull { it.name == state.selectedTrafficInterface }
                            val scale = when {
                                selectedIface == null || selectedIface.name.contains("wan", true) -> 1f
                                selectedIface.name.contains("lan", true) -> 0.55f
                                selectedIface.name.contains("hotspot", true) -> 0.42f
                                else -> 0.28f
                            }
                            val scaledSample = baseSample.copy(
                                downloadMbps = baseSample.downloadMbps * scale,
                                uploadMbps = baseSample.uploadMbps * scale,
                            )
                            val snap = demoEngine.snapshot()
                            val now = System.currentTimeMillis()
                            _uiState.update { s ->
                                s.copy(
                                    interfaces = snap.interfaces,
                                    ipAddresses = snap.ipAddresses,
                                    dhcpServers = snap.dhcpServers,
                                    dhcpLeases = snap.dhcpLeases,
                                    wirelessInterfaces = snap.wirelessInterfaces,
                                    hotspotUsers = snap.hotspotUsers,
                                    activeSessions = snap.activeSessions,
                                    ipBindings = snap.ipBindings,
                                    lastTelemetrySyncMs = now,
                                    dashboardFeatures = s.dashboardFeatures.copy(
                                        interfacesState = s.dashboardFeatures.interfacesState.copy(lastSyncedMs = now),
                                        ipAddressesState = s.dashboardFeatures.ipAddressesState.copy(lastSyncedMs = now),
                                        dhcpState = s.dashboardFeatures.dhcpState.copy(lastSyncedMs = now),
                                        systemState = s.dashboardFeatures.systemState.copy(lastSyncedMs = now),
                                        ipBindingsState = s.dashboardFeatures.ipBindingsState.copy(lastSyncedMs = now),
                                    ),
                                )
                            }
                            pushTelemetry(res, scaledSample)
                            evaluateQuotaAlerts()
                        }
                        ConnectionStatus.ConnectedLive -> {
                            val targetIface = state.selectedTrafficInterface.ifBlank {
                                state.interfaces.firstOrNull {
                                    it.running && !it.disabled && (it.name.contains("wan", true) || it.name.startsWith("ether1", true))
                                }?.name ?: state.interfaces.firstOrNull { it.running && !it.disabled }?.name.orEmpty()
                            }

                            val pollResult = restClient.pollTrafficAndResource(state.routerConfig, targetIface)
                            pollResult
                                .onSuccess { (res, sample) ->
                                    consecutivePollFailures = 0
                                    pushTelemetry(res, sample)
                                }
                                .onFailure { err ->
                                    consecutivePollFailures++
                                    if (consecutivePollFailures >= 3) {
                                        val msg = err.message ?: "Koneksi ke router terputus"
                                        _uiState.update {
                                            it.copy(connectionStatus = ConnectionStatus.Disconnected(msg))
                                        }
                                        appendLog("Koneksi Terputus", msg, isAlert = true)
                                    }
                                }

                            // Silent targeted background refresh every ~5s when viewing one of the dashboard modals
                            if (pollCycle % 2 == 0 && consecutivePollFailures == 0) {
                                when (_uiState.value.activeModal) {
                                    ActiveModal.Interfaces -> refreshInterfacesSection(silent = true)
                                    ActiveModal.IpAddresses -> refreshIpAddressesSection(silent = true)
                                    ActiveModal.DhcpServer -> refreshDhcpSection(silent = true)
                                    ActiveModal.RebootShutdown -> refreshSystemHealthSection(silent = true)
                                    ActiveModal.IpBindings -> refreshIpBindingsSection(silent = true)
                                    else -> Unit
                                }
                            }

                            // Lightweight operational sync every ~15s (interfaces + hotspot sessions/users)
                            if (pollCycle % 18 == 0 && consecutivePollFailures == 0) {
                                restClient.fetchSnapshot(state.routerConfig).onSuccess { snap ->
                                    applySnapshot(snap, ConnectionStatus.ConnectedLive)
                                }
                            } else if (pollCycle % 6 == 0 && consecutivePollFailures == 0) {
                                restClient.fetchOperationalSnapshot(state.routerConfig).onSuccess { op ->
                                    applyOperationalSnapshot(op)
                                }
                            }
                        }
                        ConnectionStatus.Connecting -> {
                            // Wait for in-flight connection attempt to settle instead of killing polling
                            continue
                        }
                        is ConnectionStatus.Disconnected -> break
                    }
                }
            } finally {
                _uiState.update { it.copy(isPollingActive = false) }
            }
        }
    }

    private fun applyOperationalSnapshot(op: OperationalSnapshot) {
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            state.copy(
                connectionStatus = ConnectionStatus.ConnectedLive,
                interfaces = op.interfaces,
                hotspotUsers = op.hotspotUsers,
                activeSessions = op.activeSessions,
                lastTelemetrySyncMs = now,
                dashboardFeatures = state.dashboardFeatures.copy(
                    interfacesState = state.dashboardFeatures.interfacesState.copy(lastSyncedMs = now),
                ),
            )
        }
        evaluateQuotaAlerts()
    }

    private fun pushTelemetry(resource: RouterResource, sample: TrafficSample) {
        _uiState.update { state ->
            val now = System.currentTimeMillis()
            val ifaceKey = state.selectedTrafficInterface
            val buf = interfaceHistoryBuffers.getOrPut(ifaceKey) { state.trafficHistory.toMutableList() }
            buf.add(sample)
            while (buf.size > 60) buf.removeAt(0)
            // Preserve cached identity & clock metadata if lightweight live poll omitted them
            val mergedResource = resource.copy(
                identity = resource.identity.ifBlank { state.resource.identity },
                clockTime = if (resource.clockTime == "--:--:--" || resource.clockTime.isBlank()) {
                    state.resource.clockTime
                } else {
                    resource.clockTime
                },
                clockDate = if (resource.clockDate == "--/--/----" || resource.clockDate.isBlank()) {
                    state.resource.clockDate
                } else {
                    resource.clockDate
                },
                timeZoneName = resource.timeZoneName.ifBlank { state.resource.timeZoneName },
            )
            state.copy(
                resource = mergedResource,
                trafficHistory = buf.toList(),
                currentDownloadMbps = sample.downloadMbps,
                currentUploadMbps = sample.uploadMbps,
                lastTelemetrySyncMs = now,
                dashboardFeatures = state.dashboardFeatures.copy(
                    systemState = state.dashboardFeatures.systemState.copy(lastSyncedMs = now),
                ),
            )
        }
        evaluateThroughputAlert()
        evaluateResourceAlerts()
    }

    private fun evaluateThroughputAlert() {
        val state = _uiState.value
        val config = state.throughputAlertConfig
        if (!config.enabled || !state.connectionStatus.isConnected) return

        val totalMbps = state.currentDownloadMbps + state.currentUploadMbps
        if (totalMbps <= config.thresholdMbps) return

        val nowMs = System.currentTimeMillis()
        val cooldownMs = config.cooldownSeconds.coerceIn(5, 3600) * 1000L
        if (nowMs - lastThroughputAlertEpochMs < cooldownMs) return

        lastThroughputAlertEpochMs = nowMs
        val time = currentTimeString()
        val ifaceName = state.selectedTrafficInterface.ifBlank {
            state.interfaces.firstOrNull { it.running && !it.disabled }?.name ?: "WAN"
        }
        val event = ThroughputAlertEvent(
            id = "tp_${nowMs}",
            interfaceName = ifaceName,
            totalMbps = totalMbps,
            downloadMbps = state.currentDownloadMbps,
            uploadMbps = state.currentUploadMbps,
            thresholdMbps = config.thresholdMbps,
            timestamp = time,
            isAcknowledged = false,
        )

        _uiState.update { current ->
            current.copy(throughputAlerts = (listOf(event) + current.throughputAlerts).take(30))
        }

        val totalStr = String.format(Locale.US, "%.1f Mbps", totalMbps)
        val limitStr = String.format(Locale.US, "%.0f Mbps", config.thresholdMbps)
        appendLog(
            title = "Peringatan Lonjakan Trafik",
            detail = "$ifaceName mencapai $totalStr (melebihi batas $limitStr • Unduh ${String.format(Locale.US, "%.1f", state.currentDownloadMbps)} / Unggah ${String.format(Locale.US, "%.1f", state.currentUploadMbps)} Mbps)",
            severity = LogSeverity.WARNING,
            topics = "traffic,warning,threshold",
        )
        if (config.systemNotificationsEnabled) {
            QuotaNotificationHelper.showThroughputAlert(getApplication(), event)
        }
        _snackbarMessage.tryEmit("Lonjakan Trafik Tinggi: $totalStr melebihi batas $limitStr")
    }

    fun updateThroughputAlertConfig(config: ThroughputAlertConfig) {
        viewModelScope.launch {
            dataStore.saveThroughputAlertConfig(config)
            lastThroughputAlertEpochMs = 0L
            _uiState.update { it.copy(throughputAlertConfig = config) }
            val limitLabel = String.format(Locale.US, "%.0f Mbps", config.thresholdMbps)
            appendLog(
                title = "Pengaturan Peringatan Trafik Diperbarui",
                detail = if (config.enabled) {
                    "Batas diatur ke $limitLabel (jeda ${config.cooldownSeconds}d)"
                } else {
                    "Peringatan batas throughput dinonaktifkan"
                },
                severity = LogSeverity.INFO,
                topics = "system,info",
            )
            _snackbarMessage.tryEmit(
                if (config.enabled) "Peringatan trafik disimpan ($limitLabel)" else "Peringatan trafik dinonaktifkan"
            )
            evaluateThroughputAlert()
        }
    }

    fun acknowledgeThroughputAlert(alertId: String) {
        _uiState.update { state ->
            state.copy(
                throughputAlerts = state.throughputAlerts.map {
                    if (it.id == alertId) it.copy(isAcknowledged = true) else it
                }
            )
        }
    }

    fun acknowledgeAllThroughputAlerts() {
        _uiState.update { state ->
            state.copy(
                throughputAlerts = state.throughputAlerts.map { it.copy(isAcknowledged = true) }
            )
        }
    }

    fun clearThroughputAlerts() {
        _uiState.update { state ->
            state.copy(throughputAlerts = emptyList())
        }
    }

    private fun evaluateResourceAlerts() {
        val state = _uiState.value
        val config = state.resourceAlertConfig
        if (!config.enabled || !state.connectionStatus.isConnected) return

        val resource = state.resource
        val nowMs = System.currentTimeMillis()
        val cooldownMs = config.cooldownSeconds.coerceIn(5, 3600) * 1000L
        val time = currentTimeString()
        val newEvents = mutableListOf<ResourceAlertEvent>()

        if (resource.cpuLoadPercent > config.cpuThresholdPercent &&
            nowMs - lastCpuAlertEpochMs >= cooldownMs
        ) {
            lastCpuAlertEpochMs = nowMs
            val event = ResourceAlertEvent(
                id = "res_cpu_$nowMs",
                metricType = ResourceMetricType.CPU,
                currentPercent = resource.cpuLoadPercent,
                thresholdPercent = config.cpuThresholdPercent,
                routerIdentity = resource.identity,
                detail = "${resource.boardName} • ${resource.version}",
                timestamp = time,
                isAcknowledged = false,
            )
            newEvents += event
        }

        if (resource.ramUsagePercent > config.memoryThresholdPercent &&
            nowMs - lastMemoryAlertEpochMs >= cooldownMs
        ) {
            lastMemoryAlertEpochMs = nowMs
            val usedMb = (resource.totalMemoryMb - resource.freeMemoryMb).coerceAtLeast(0L)
            val memDetail = if (resource.totalMemoryMb > 0L) {
                "Terpakai $usedMb / ${resource.totalMemoryMb} MB"
            } else {
                "${resource.boardName} • ${resource.version}"
            }
            val event = ResourceAlertEvent(
                id = "res_mem_${nowMs + 1}",
                metricType = ResourceMetricType.MEMORY,
                currentPercent = resource.ramUsagePercent,
                thresholdPercent = config.memoryThresholdPercent,
                routerIdentity = resource.identity,
                detail = memDetail,
                timestamp = time,
                isAcknowledged = false,
            )
            newEvents += event
        }

        if (newEvents.isEmpty()) return

        _uiState.update { current ->
            current.copy(resourceAlerts = (newEvents + current.resourceAlerts).take(30))
        }

        val appContext = getApplication<Application>()
        for (event in newEvents) {
            val metricLabel = if (event.metricType == ResourceMetricType.CPU) "CPU" else "Memori"
            appendLog(
                title = "$metricLabel Router Tinggi: ${event.currentPercent}%",
                detail = "$metricLabel ${event.routerIdentity} melebihi batas ${event.thresholdPercent}% (${event.detail})",
                isAlert = true,
                severity = LogSeverity.WARNING,
                topics = "system,resource,warning",
            )
            if (config.systemNotificationsEnabled) {
                QuotaNotificationHelper.showResourceAlert(appContext, event)
            }
        }

        val snackbarSummary = if (newEvents.size == 1) {
            val e = newEvents.first()
            val metricLabel = if (e.metricType == ResourceMetricType.CPU) "CPU" else "Memori"
            "Peringatan $metricLabel Router: ${e.currentPercent}% melebihi batas ${e.thresholdPercent}%"
        } else {
            "Peringatan Router: CPU ${resource.cpuLoadPercent}% & Memori ${resource.ramUsagePercent}% melebihi batas"
        }
        _snackbarMessage.tryEmit(snackbarSummary)
    }

    fun updateResourceAlertConfig(config: ResourceAlertConfig) {
        viewModelScope.launch {
            val normalized = config.copy(
                cpuThresholdPercent = config.cpuThresholdPercent.coerceIn(10, 99),
                memoryThresholdPercent = config.memoryThresholdPercent.coerceIn(10, 99),
                cooldownSeconds = config.cooldownSeconds.coerceIn(10, 3600),
            )
            dataStore.saveResourceAlertConfig(normalized)
            lastCpuAlertEpochMs = 0L
            lastMemoryAlertEpochMs = 0L
            _uiState.update { it.copy(resourceAlertConfig = normalized) }
            appendLog(
                title = "Pengaturan Peringatan CPU & Memori Diperbarui",
                detail = if (normalized.enabled) {
                    "Batas CPU ${normalized.cpuThresholdPercent}%, batas Memori ${normalized.memoryThresholdPercent}% (jeda ${normalized.cooldownSeconds}d)"
                } else {
                    "Peringatan batas CPU & Memori dinonaktifkan"
                },
                severity = LogSeverity.INFO,
                topics = "system,info",
            )
            _snackbarMessage.tryEmit(
                if (normalized.enabled) {
                    "Peringatan resource disimpan (CPU ${normalized.cpuThresholdPercent}% / RAM ${normalized.memoryThresholdPercent}%)"
                } else {
                    "Peringatan CPU & Memori dinonaktifkan"
                },
            )
            evaluateResourceAlerts()
        }
    }

    fun acknowledgeResourceAlert(alertId: String) {
        _uiState.update { state ->
            state.copy(
                resourceAlerts = state.resourceAlerts.map {
                    if (it.id == alertId) it.copy(isAcknowledged = true) else it
                },
            )
        }
    }

    fun acknowledgeAllResourceAlerts() {
        _uiState.update { state ->
            state.copy(
                resourceAlerts = state.resourceAlerts.map { it.copy(isAcknowledged = true) },
            )
        }
    }

    fun clearResourceAlerts() {
        _uiState.update { state ->
            state.copy(resourceAlerts = emptyList())
        }
    }

    fun selectTrafficInterface(interfaceName: String) {
        val state = _uiState.value
        val existing = interfaceHistoryBuffers[interfaceName]
        val nextHistory = when {
            existing != null && existing.isNotEmpty() -> existing.toList()
            state.connectionStatus is ConnectionStatus.ConnectedDemo -> {
                val ifaceObj = state.interfaces.firstOrNull { it.name == interfaceName }
                val seeded = demoEngine.initialTrafficHistoryFor(ifaceObj)
                interfaceHistoryBuffers[interfaceName] = seeded.toMutableList()
                seeded
            }
            else -> emptyList()
        }
        val latest = nextHistory.lastOrNull()
        _uiState.update {
            it.copy(
                selectedTrafficInterface = interfaceName,
                trafficHistory = nextHistory,
                currentDownloadMbps = latest?.downloadMbps ?: 0f,
                currentUploadMbps = latest?.uploadMbps ?: 0f,
            )
        }
        if (state.connectionStatus is ConnectionStatus.ConnectedLive) {
            viewModelScope.launch {
                restClient.pollTrafficAndResource(state.routerConfig, interfaceName)
                    .onSuccess { (res, sample) ->
                        if (_uiState.value.selectedTrafficInterface == interfaceName) {
                            pushTelemetry(res, sample)
                        }
                    }
            }
        }
    }

    fun refreshSystemLogs() {
        val current = _uiState.value
        if (current.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            if (current.routerConfig.demoMode) {
                delay(400L)
                val demoLogs = demoEngine.snapshot().systemLogs
                _uiState.update { state ->
                    val localOnly = state.logs.filter { it.id.startsWith("local_") }
                    state.copy(
                        isRefreshing = false,
                        logs = (localOnly + demoLogs).distinctBy { it.id }.take(140),
                    )
                }
                _snackbarMessage.tryEmit("System Logs diperbarui")
                return@launch
            }
            restClient.fetchSystemLogs(current.routerConfig)
                .onSuccess { fetched ->
                    _uiState.update { state ->
                        val localOnly = state.logs.filter { it.id.startsWith("local_") }
                        state.copy(
                            isRefreshing = false,
                            logs = (localOnly + fetched).distinctBy { it.id }.take(140),
                        )
                    }
                    _snackbarMessage.tryEmit("System Logs diperbarui (${fetched.size} entri)")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isRefreshing = false) }
                    _snackbarMessage.tryEmit("Gagal memuat log: ${err.message ?: "Error RouterOS"}")
                }
        }
    }

    fun updateUserSearch(query: String) {
        _uiState.update { it.copy(userSearchQuery = query) }
    }

    fun selectUserFilter(filter: UserFilter) {
        _uiState.update { it.copy(selectedUserFilter = filter) }
    }

    fun updateSettingsSearch(query: String) {
        _uiState.update { it.copy(settingsSearchQuery = query) }
    }

    fun updateTheme(themeMode: ThemeMode, dynamicColor: Boolean) {
        viewModelScope.launch {
            dataStore.saveTheme(themeMode, dynamicColor)
            _uiState.update { it.copy(themeMode = themeMode, dynamicColor = dynamicColor) }
        }
    }

    fun toggleDarkMode(isCurrentlyDark: Boolean) {
        val nextMode = if (isCurrentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
        updateTheme(nextMode, _uiState.value.dynamicColor)
        _snackbarMessage.tryEmit(
            if (nextMode == ThemeMode.DARK) "Mode Gelap diaktifkan" else "Mode Terang diaktifkan",
        )
    }

    fun updateQuotaAlertConfig(config: QuotaAlertConfig) {
        viewModelScope.launch {
            val normalized = config.copy(
                globalQuotaLimitMb = config.globalQuotaLimitMb.coerceAtLeast(0L),
                warningThresholdPercent = config.warningThresholdPercent.coerceIn(50, 99),
            )
            dataStore.saveQuotaAlertConfig(normalized)
            notifiedAlertKeys.clear()
            _uiState.update { it.copy(quotaAlertConfig = normalized) }
            evaluateQuotaAlerts()
            _snackbarMessage.tryEmit(
                if (normalized.enabled) {
                    "Notifikasi kuota disimpan (Batas ${normalized.globalQuotaLimitMb} MB, Peringatan ${normalized.warningThresholdPercent}%)"
                } else {
                    "Notifikasi kuota bandwidth dinonaktifkan"
                },
            )
        }
    }

    fun acknowledgeQuotaAlert(alertId: String) {
        _uiState.update { state ->
            state.copy(
                quotaAlerts = state.quotaAlerts.map { alert ->
                    if (alert.id == alertId) alert.copy(isAcknowledged = true) else alert
                },
            )
        }
    }

    fun acknowledgeAllQuotaAlerts() {
        _uiState.update { state ->
            state.copy(quotaAlerts = state.quotaAlerts.map { it.copy(isAcknowledged = true) })
        }
        _snackbarMessage.tryEmit("Semua peringatan kuota ditandai telah dibaca")
    }

    fun disconnectUserByName(username: String) {
        val state = _uiState.value
        val activeSession = state.activeSessions.firstOrNull { it.user.equals(username, ignoreCase = true) }
        if (activeSession != null) {
            kickActiveSession(activeSession)
            return
        }
        val user = state.hotspotUsers.firstOrNull { it.name.equals(username, ignoreCase = true) }
        if (user != null && !user.disabled) {
            toggleHotspotUserDisabled(user)
        } else {
            _snackbarMessage.tryEmit("User '$username' sudah tidak memiliki sesi aktif")
        }
    }

    private fun evaluateQuotaAlerts() {
        val state = _uiState.value
        val config = state.quotaAlertConfig
        if (!config.enabled) {
            if (state.quotaAlerts.isNotEmpty()) {
                _uiState.update { it.copy(quotaAlerts = emptyList()) }
            }
            return
        }

        val sessionsByUser = state.activeSessions.associateBy { it.user.lowercase() }
        val prevById = state.quotaAlerts.associateBy { it.id }
        val globalLimitBytes = config.globalQuotaLimitMb * 1024L * 1024L
        val nowTime = currentTimeString()

        val activeAlerts = mutableListOf<BandwidthQuotaAlert>()
        val newlyTriggered = mutableListOf<BandwidthQuotaAlert>()

        for (user in state.hotspotUsers) {
            val session = sessionsByUser[user.name.lowercase()]
            val sessionBytes = if (session != null) session.bytesIn + session.bytesOut else 0L
            val effectiveUsed = maxOf(user.usedBytes, sessionBytes)
            val effectiveLimit = when {
                user.limitBytesTotal > 0L -> user.limitBytesTotal
                globalLimitBytes > 0L -> globalLimitBytes
                else -> 0L
            }
            if (effectiveLimit <= 0L || effectiveUsed <= 0L) continue

            val pct = ((effectiveUsed * 100L) / effectiveLimit).toInt()
            val severity = when {
                pct >= 100 -> QuotaAlertSeverity.EXCEEDED
                pct >= config.warningThresholdPercent -> QuotaAlertSeverity.WARNING
                else -> null
            } ?: continue

            val alertId = "quota_${user.name.lowercase()}_${severity.name}"
            val existing = prevById[alertId]
            val alert = BandwidthQuotaAlert(
                id = alertId,
                username = user.name,
                profile = user.profile,
                ipAddress = session?.address.orEmpty(),
                usedBytes = effectiveUsed,
                limitBytes = effectiveLimit,
                usagePercent = pct,
                severity = severity,
                timestamp = existing?.timestamp ?: nowTime,
                isAcknowledged = existing?.isAcknowledged ?: false,
            )
            activeAlerts += alert

            if (notifiedAlertKeys.add(alertId)) {
                newlyTriggered += alert
            }
        }

        // Remove stale keys if a user dropped below threshold or was deleted
        val currentIds = activeAlerts.map { it.id }.toSet()
        notifiedAlertKeys.retainAll(currentIds)

        val sortedAlerts = activeAlerts.sortedWith(
            compareByDescending<BandwidthQuotaAlert> { it.severity == QuotaAlertSeverity.EXCEEDED }
                .thenBy { it.isAcknowledged }
                .thenByDescending { it.usagePercent },
        )

        _uiState.update { it.copy(quotaAlerts = sortedAlerts) }

        if (newlyTriggered.isNotEmpty()) {
            val appContext = getApplication<Application>()
            for (alert in newlyTriggered) {
                if (config.systemNotificationsEnabled) {
                    QuotaNotificationHelper.showQuotaAlert(appContext, alert)
                }
                val isExceeded = alert.severity == QuotaAlertSeverity.EXCEEDED
                appendLog(
                    title = if (isExceeded) "Kuota Terlampaui: ${alert.username}" else "Peringatan Kuota: ${alert.username}",
                    detail = "Penggunaan ${formatBytes(alert.usedBytes)} / ${formatBytes(alert.limitBytes)} (${alert.usagePercent}%) pada profil ${alert.profile}",
                    isAlert = isExceeded,
                    severity = if (isExceeded) LogSeverity.ERROR else LogSeverity.WARNING,
                    topics = if (isExceeded) "hotspot,bandwidth,error" else "hotspot,bandwidth,warning",
                )
            }
            val top = newlyTriggered.firstOrNull { it.severity == QuotaAlertSeverity.EXCEEDED } ?: newlyTriggered.first()
            val summary = if (newlyTriggered.size == 1) {
                "Kuota ${top.username} mencapai ${top.usagePercent}% (${formatBytes(top.usedBytes)} / ${formatBytes(top.limitBytes)})"
            } else {
                "${newlyTriggered.size} user melewati ambang batas kuota bandwidth"
            }
            _snackbarMessage.tryEmit(summary)
        }
    }

    fun fetchHotspotUserProfiles() {
        val state = _uiState.value
        if (state.isLoadingProfiles) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProfiles = true, profilesFetchError = null) }
            if (state.routerConfig.demoMode || state.connectionStatus is ConnectionStatus.ConnectedDemo) {
                delay(220L)
                val demoProfiles = demoEngine.snapshot().userProfiles
                _uiState.update {
                    it.copy(
                        userProfiles = demoProfiles,
                        isLoadingProfiles = false,
                        profilesFetchError = null,
                    )
                }
                return@launch
            }

            restClient.fetchHotspotUserProfiles(state.routerConfig)
                .onSuccess { fetched ->
                    _uiState.update {
                        it.copy(
                            userProfiles = fetched,
                            isLoadingProfiles = false,
                            profilesFetchError = null,
                        )
                    }
                }
                .onFailure { err ->
                    val msg = err.message ?: "Failed to load hotspot profiles from router"
                    _uiState.update {
                        it.copy(
                            isLoadingProfiles = false,
                            profilesFetchError = msg,
                        )
                    }
                }
        }
    }

    fun addHotspotUser(
        username: String,
        password: String,
        profile: String,
        quotaMb: Long,
        comment: String,
        onComplete: (Result<Unit>) -> Unit = {},
    ) {
        val cleanName = username.trim()
        val cleanComment = comment.trim()
        if (cleanName.isBlank()) {
            val err = IllegalArgumentException("Username cannot be empty.")
            _snackbarMessage.tryEmit(err.message ?: "Username tidak boleh kosong")
            onComplete(Result.failure(err))
            return
        }
        val limitBytes = if (quotaMb > 0L) quotaMb * 1024L * 1024L else 0L
        viewModelScope.launch {
            val state = _uiState.value
            if (state.routerConfig.demoMode || state.connectionStatus is ConnectionStatus.ConnectedDemo) {
                delay(350L)
                demoEngine.addUser(cleanName, password, profile, limitBytes, cleanComment)
                applySnapshot(demoEngine.snapshot(), ConnectionStatus.ConnectedDemo)
                appendLog(
                    title = "Hotspot User Ditambahkan",
                    detail = "User $cleanName (Profil: $profile)${if (cleanComment.isNotBlank()) " — $cleanComment" else ""}",
                    severity = LogSeverity.INFO,
                    topics = "hotspot,info",
                )
                _snackbarMessage.tryEmit("User '$cleanName' berhasil ditambahkan")
                onComplete(Result.success(Unit))
                return@launch
            }

            restClient.addHotspotUser(
                config = state.routerConfig,
                name = cleanName,
                password = password,
                profile = profile,
                limitBytesTotal = limitBytes,
                comment = cleanComment,
            ).onSuccess {
                appendLog(
                    title = "Hotspot User Ditambahkan",
                    detail = "User $cleanName (Profil: $profile)${if (cleanComment.isNotBlank()) " — $cleanComment" else ""}",
                    severity = LogSeverity.INFO,
                    topics = "hotspot,info",
                )
                _snackbarMessage.tryEmit("User '$cleanName' berhasil dikirim ke MikroTik")
                restClient.fetchSnapshot(state.routerConfig).onSuccess { snap ->
                    applySnapshot(snap, ConnectionStatus.ConnectedLive)
                }
                onComplete(Result.success(Unit))
            }.onFailure { err ->
                val msg = err.message ?: "Gagal mengirim user ke RouterOS"
                appendLog(
                    title = "Gagal Tambah User",
                    detail = "$cleanName: $msg",
                    isAlert = true,
                    severity = LogSeverity.ERROR,
                    topics = "hotspot,error",
                )
                onComplete(Result.failure(err))
            }
        }
    }

    fun toggleHotspotUserDisabled(user: HotspotUser) {
        val nextDisabled = !user.disabled
        executeMutation(
            demoAction = { demoEngine.toggleUserDisabled(user.id, nextDisabled) },
            liveAction = { restClient.toggleHotspotUserDisabled(it, user.id, nextDisabled) },
            successMessage = if (nextDisabled) "User '${user.name}' dinonaktifkan" else "User '${user.name}' diaktifkan",
            logTitle = if (nextDisabled) "User Dinonaktifkan" else "User Diaktifkan",
            logDetail = "Status hotspot user ${user.name} diperbarui",
        )
    }

    fun updateHotspotUserSessionTimeout(user: HotspotUser, newTimeout: String) {
        val trimmed = newTimeout.trim()
        val isUnlimited = trimmed.isEmpty() ||
            trimmed.equals("unlimited", ignoreCase = true) ||
            trimmed.equals("none", ignoreCase = true) ||
            trimmed == "0s" ||
            trimmed == "0" ||
            trimmed == "00:00:00"
        val routerValue = if (isUnlimited) "0s" else trimmed
        val displayLabel = if (isUnlimited) "Unlimited" else trimmed
        executeMutation(
            demoAction = { demoEngine.updateUserSessionTimeout(user.id, if (isUnlimited) "" else trimmed) },
            liveAction = { restClient.updateHotspotUserSessionTimeout(it, user.id, routerValue) },
            successMessage = "Session Timeout '${user.name}' set to $displayLabel",
            logTitle = "Session Timeout Updated",
            logDetail = "${user.name} -> limit-uptime: $displayLabel",
        )
    }

    fun updateHotspotUserPassword(user: HotspotUser, newPassword: String) {
        val cleanPassword = newPassword.trim()
        if (cleanPassword.isEmpty()) {
            _snackbarMessage.tryEmit("Password baru tidak boleh kosong")
            return
        }
        executeMutation(
            demoAction = { demoEngine.updateUserPassword(user.id, cleanPassword) },
            liveAction = { restClient.updateHotspotUserPassword(it, user.id, cleanPassword) },
            successMessage = "Password '${user.name}' berhasil diperbarui",
            logTitle = "Password User Diperbarui",
            logDetail = "Password akun hotspot ${user.name} berhasil diubah",
        )
    }

    fun deleteHotspotUser(user: HotspotUser) {
        executeMutation(
            demoAction = { demoEngine.deleteUser(user.id) },
            liveAction = { restClient.deleteHotspotUser(it, user.id) },
            successMessage = "User '${user.name}' dihapus",
            logTitle = "Hotspot User Dihapus",
            logDetail = "Akun ${user.name} dihapus dari router",
        )
    }

    fun deleteHotspotUsersBulk(
        users: List<HotspotUser>,
        onComplete: (deletedCount: Int, failedCount: Int) -> Unit = { _, _ -> },
    ) {
        if (users.isEmpty() || _uiState.value.isBulkDeleting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBulkDeleting = true) }
            val state = _uiState.value
            val namesPreview = users.take(4).joinToString(", ") { it.name } +
                if (users.size > 4) " (+${users.size - 4} lainnya)" else ""

            if (state.routerConfig.demoMode || state.connectionStatus is ConnectionStatus.ConnectedDemo) {
                delay(320L)
                users.forEach { demoEngine.deleteUser(it.id) }
                applySnapshot(demoEngine.snapshot(), ConnectionStatus.ConnectedDemo)
                _uiState.update { it.copy(isBulkDeleting = false) }
                appendLog(
                    title = "Hapus Massal Hotspot User",
                    detail = "${users.size} user dihapus: $namesPreview",
                    severity = LogSeverity.INFO,
                    topics = "hotspot,info",
                )
                _snackbarMessage.tryEmit("${users.size} user hotspot berhasil dihapus")
                onComplete(users.size, 0)
                return@launch
            }

            val (deleted, failed) = restClient.deleteHotspotUsersBulk(
                config = state.routerConfig,
                userIds = users.map { it.id },
            )

            if (deleted > 0) {
                restClient.fetchOperationalSnapshot(state.routerConfig).onSuccess { op ->
                    applyOperationalSnapshot(op)
                }
            }
            _uiState.update { it.copy(isBulkDeleting = false) }

            appendLog(
                title = "Hapus Massal Hotspot User",
                detail = if (failed == 0) {
                    "$deleted user dihapus dari MikroTik: $namesPreview"
                } else {
                    "$deleted berhasil dihapus, $failed gagal: $namesPreview"
                },
                isAlert = failed > 0,
                severity = if (failed > 0) LogSeverity.WARNING else LogSeverity.INFO,
                topics = if (failed > 0) "hotspot,warning" else "hotspot,info",
            )
            _snackbarMessage.tryEmit(
                when {
                    failed == 0 -> "$deleted user hotspot berhasil dihapus"
                    deleted == 0 -> "Gagal menghapus $failed user hotspot dari router"
                    else -> "$deleted user dihapus, $failed gagal dihapus"
                },
            )
            onComplete(deleted, failed)
        }
    }

    fun kickActiveSession(session: HotspotActiveSession) {
        executeMutation(
            demoAction = { demoEngine.kickSession(session.id) },
            liveAction = { restClient.kickActiveSession(it, session.id) },
            successMessage = "Koneksi '${session.user}' berhasil diputuskan",
            logTitle = "Koneksi Diputuskan",
            logDetail = "${session.user} (${session.address}) dikeluarkan dari Hotspot",
        )
    }

    fun disconnectHotspotUser(user: HotspotUser) {
        val activeSession = _uiState.value.activeSessions.firstOrNull {
            it.user.equals(user.name, ignoreCase = true)
        }
        if (activeSession != null) {
            kickActiveSession(activeSession)
        } else {
            toggleHotspotUserDisabled(user)
        }
    }

    fun addWalledGarden(dstHost: String, dstPort: String, action: String, comment: String) {
        if (dstHost.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.addWalledGarden(dstHost.trim(), dstPort.trim(), action, comment.trim()) },
            liveAction = { restClient.addWalledGarden(it, dstHost.trim(), dstPort.trim(), action, comment.trim()) },
            successMessage = "Walled Garden '${dstHost.trim()}' ditambahkan",
            logTitle = "Walled Garden Diperbarui",
            logDetail = "Host ${dstHost.trim()} ($action)",
        )
    }

    fun updateWalledGarden(entry: WalledGardenEntry, dstHost: String, dstPort: String, action: String, comment: String) {
        if (dstHost.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.updateWalledGarden(entry.id, dstHost.trim(), dstPort.trim(), action, comment.trim()) },
            liveAction = { restClient.updateWalledGarden(it, entry.id, dstHost.trim(), dstPort.trim(), action, comment.trim()) },
            successMessage = "Walled Garden '${dstHost.trim()}' diperbarui",
            logTitle = "Walled Garden Diubah",
            logDetail = "Host ${dstHost.trim()} ($action, port ${dstPort.ifBlank { "Any" }})",
        )
    }

    fun toggleWalledGardenDisabled(entry: WalledGardenEntry) {
        val nextDisabled = !entry.disabled
        executeMutation(
            demoAction = { demoEngine.toggleWalledGardenDisabled(entry.id, nextDisabled) },
            liveAction = { restClient.toggleWalledGardenDisabled(it, entry.id, nextDisabled) },
            successMessage = if (nextDisabled) "Aturan '${entry.dstHost}' dinonaktifkan" else "Aturan '${entry.dstHost}' diaktifkan",
            logTitle = "Status Walled Garden",
            logDetail = "${entry.dstHost} -> ${if (nextDisabled) "Disabled" else "Enabled"}",
        )
    }

    fun deleteWalledGarden(entry: WalledGardenEntry) {
        executeMutation(
            demoAction = { demoEngine.deleteWalledGarden(entry.id) },
            liveAction = { restClient.deleteWalledGarden(it, entry.id) },
            successMessage = "Aturan '${entry.dstHost}' dihapus",
            logTitle = "Walled Garden Dihapus",
            logDetail = entry.dstHost,
        )
    }

    fun addHotspotServer(name: String, interfaceName: String, addressPool: String, profile: String) {
        if (name.isBlank() || interfaceName.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.addHotspotServer(name.trim(), interfaceName.trim(), addressPool.trim(), profile.trim()) },
            liveAction = { restClient.addHotspotServer(it, name.trim(), interfaceName.trim(), addressPool.trim(), profile.trim()) },
            successMessage = "Hotspot Server '${name.trim()}' ditambahkan",
            logTitle = "Hotspot Server Ditambahkan",
            logDetail = "${name.trim()} pada interface ${interfaceName.trim()}",
        )
    }

    fun toggleHotspotServerDisabled(server: HotspotServer) {
        val nextDisabled = !server.disabled
        executeMutation(
            demoAction = { demoEngine.toggleHotspotServerDisabled(server.id, nextDisabled) },
            liveAction = { restClient.toggleHotspotServerDisabled(it, server.id, nextDisabled) },
            successMessage = if (nextDisabled) "Server '${server.name}' dinonaktifkan" else "Server '${server.name}' diaktifkan",
            logTitle = "Status Hotspot Server",
            logDetail = "${server.name} -> ${if (nextDisabled) "Disabled" else "Active"}",
        )
    }

    fun deleteHotspotServer(server: HotspotServer) {
        executeMutation(
            demoAction = { demoEngine.deleteHotspotServer(server.id) },
            liveAction = { restClient.deleteHotspotServer(it, server.id) },
            successMessage = "Hotspot Server '${server.name}' dihapus",
            logTitle = "Hotspot Server Dihapus",
            logDetail = server.name,
        )
    }

    fun addIpBinding(
        mac: String,
        address: String,
        toAddress: String = "",
        server: String = "all",
        type: String,
        comment: String,
    ) {
        val cleanMac = mac.trim().uppercase()
        val cleanAddr = address.trim()
        val cleanToAddr = toAddress.trim()
        val cleanServer = server.trim().ifBlank { "all" }
        val cleanComment = comment.trim()
        if (cleanMac.isBlank() && cleanAddr.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.addIpBinding(cleanMac, cleanAddr, type, cleanComment, cleanToAddr, cleanServer) },
            liveAction = { restClient.addIpBinding(it, cleanMac, cleanAddr, type, cleanComment, cleanToAddr, cleanServer) },
            successMessage = "IP Binding '${cleanMac.ifBlank { cleanAddr }}' ditambahkan",
            logTitle = "IP Binding Ditambahkan",
            logDetail = "${cleanMac.ifBlank { cleanAddr }} ($type, server: $cleanServer)",
        )
    }

    fun updateIpBinding(
        entry: IpBindingEntry,
        mac: String,
        address: String,
        toAddress: String,
        server: String,
        type: String,
        comment: String,
    ) {
        val cleanMac = mac.trim().uppercase()
        val cleanAddr = address.trim()
        val cleanToAddr = toAddress.trim()
        val cleanServer = server.trim().ifBlank { "all" }
        val cleanComment = comment.trim()
        if (cleanMac.isBlank() && cleanAddr.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.updateIpBinding(entry.id, cleanMac, cleanAddr, cleanToAddr, cleanServer, type, cleanComment) },
            liveAction = { restClient.updateIpBinding(it, entry.id, cleanMac, cleanAddr, cleanToAddr, cleanServer, type, cleanComment) },
            successMessage = "IP Binding '${cleanMac.ifBlank { cleanAddr }}' diperbarui",
            logTitle = "IP Binding Diubah",
            logDetail = "${cleanMac.ifBlank { cleanAddr }} -> $type (server: $cleanServer)",
        )
    }

    fun toggleIpBindingDisabled(entry: IpBindingEntry) {
        val nextDisabled = !entry.disabled
        val label = entry.macAddress.ifBlank { entry.address }
        executeMutation(
            demoAction = { demoEngine.toggleIpBindingDisabled(entry.id, nextDisabled) },
            liveAction = { restClient.toggleIpBindingDisabled(it, entry.id, nextDisabled) },
            successMessage = if (nextDisabled) "IP Binding '$label' dinonaktifkan" else "IP Binding '$label' diaktifkan",
            logTitle = "Status IP Binding",
            logDetail = "$label -> ${if (nextDisabled) "Nonaktif" else "Aktif"}",
        )
    }

    fun deleteIpBinding(entry: IpBindingEntry) {
        val label = entry.macAddress.ifBlank { entry.address }
        executeMutation(
            demoAction = { demoEngine.deleteIpBinding(entry.id) },
            liveAction = { restClient.deleteIpBinding(it, entry.id) },
            successMessage = "IP Binding '$label' dihapus",
            logTitle = "IP Binding Dihapus",
            logDetail = label,
        )
    }

    fun addUserProfile(name: String, rateLimit: String, sharedUsers: String, timeout: String) {
        if (name.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.addProfile(name.trim(), rateLimit.trim(), sharedUsers.trim(), timeout.trim()) },
            liveAction = { restClient.addUserProfile(it, name.trim(), rateLimit.trim(), sharedUsers.trim(), timeout.trim()) },
            successMessage = "Profil '${name.trim()}' disimpan",
            logTitle = "Profil Hotspot Baru",
            logDetail = "${name.trim()} (${rateLimit.ifBlank { "Unlimited" }})",
        )
    }

    fun updateUserProfile(
        profile: HotspotUserProfile,
        name: String,
        rateLimit: String,
        sharedUsers: String,
        timeout: String,
    ) {
        if (name.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.updateProfile(profile.id, name.trim(), rateLimit.trim(), sharedUsers.trim(), timeout.trim()) },
            liveAction = { restClient.updateUserProfile(it, profile.id, name.trim(), rateLimit.trim(), sharedUsers.trim(), timeout.trim()) },
            successMessage = "Profil '${name.trim()}' diperbarui",
            logTitle = "Profil Hotspot Diperbarui",
            logDetail = "${name.trim()} (${rateLimit.ifBlank { "Unlimited" }}, shared ${sharedUsers.ifBlank { "1" }})",
        )
    }

    fun deleteUserProfile(profile: HotspotUserProfile) {
        executeMutation(
            demoAction = { demoEngine.deleteProfile(profile.id) },
            liveAction = { restClient.deleteUserProfile(it, profile.id) },
            successMessage = "Profil '${profile.name}' dihapus",
            logTitle = "Profil Hotspot Dihapus",
            logDetail = profile.name,
        )
    }

    fun addIpAddress(address: String, iface: String, comment: String) {
        if (address.isBlank() || iface.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.addIpAddress(address.trim(), iface.trim(), comment.trim()) },
            liveAction = { restClient.addIpAddress(it, address.trim(), iface.trim(), comment.trim()) },
            successMessage = "IP Address '${address.trim()}' ditambahkan",
            logTitle = "IP Address Ditambahkan",
            logDetail = "${address.trim()} pada $iface",
        )
    }

    fun updateIpAddress(entry: IpAddressEntry, address: String, iface: String, comment: String) {
        if (address.isBlank() || iface.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.updateIpAddress(entry.id, address.trim(), iface.trim(), comment.trim()) },
            liveAction = { restClient.updateIpAddress(it, entry.id, address.trim(), iface.trim(), comment.trim()) },
            successMessage = "IP Address '${address.trim()}' diperbarui",
            logTitle = "IP Address Diperbarui",
            logDetail = "${address.trim()} pada ${iface.trim()}",
        )
    }

    fun toggleIpAddressDisabled(entry: IpAddressEntry) {
        val nextDisabled = !entry.disabled
        executeMutation(
            demoAction = { demoEngine.toggleIpAddressDisabled(entry.id, nextDisabled) },
            liveAction = { restClient.toggleIpAddressDisabled(it, entry.id, nextDisabled) },
            successMessage = if (nextDisabled) "IP '${entry.address}' dinonaktifkan" else "IP '${entry.address}' diaktifkan",
            logTitle = "Status IP Address Berubah",
            logDetail = "${entry.address} (${entry.interfaceName}) -> ${if (nextDisabled) "Disabled" else "Enabled"}",
        )
    }

    fun deleteIpAddress(entry: IpAddressEntry) {
        executeMutation(
            demoAction = { demoEngine.deleteIpAddress(entry.id) },
            liveAction = { restClient.deleteIpAddress(it, entry.id) },
            successMessage = "IP '${entry.address}' dihapus",
            logTitle = "IP Address Dihapus",
            logDetail = entry.address,
        )
    }

    fun addDhcpServer(name: String, iface: String, addressPool: String, leaseTime: String) {
        if (name.isBlank() || iface.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.addDhcpServer(name.trim(), iface.trim(), addressPool.trim(), leaseTime.trim()) },
            liveAction = { restClient.addDhcpServer(it, name.trim(), iface.trim(), addressPool.trim(), leaseTime.trim()) },
            successMessage = "DHCP Server '${name.trim()}' ditambahkan",
            logTitle = "DHCP Server Ditambahkan",
            logDetail = "${name.trim()} pada ${iface.trim()} (pool: ${addressPool.ifBlank { "static-only" }})",
        )
    }

    fun updateDhcpServer(
        server: DhcpServerEntry,
        name: String,
        iface: String,
        addressPool: String,
        leaseTime: String,
    ) {
        if (name.isBlank() || iface.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.updateDhcpServer(server.id, name.trim(), iface.trim(), addressPool.trim(), leaseTime.trim()) },
            liveAction = { restClient.updateDhcpServer(it, server.id, name.trim(), iface.trim(), addressPool.trim(), leaseTime.trim()) },
            successMessage = "DHCP Server '${name.trim()}' diperbarui",
            logTitle = "DHCP Server Diperbarui",
            logDetail = "${name.trim()} pada ${iface.trim()} (lease ${leaseTime.ifBlank { "10m" }})",
        )
    }

    fun toggleDhcpServerDisabled(server: DhcpServerEntry) {
        val nextDisabled = !server.disabled
        executeMutation(
            demoAction = { demoEngine.toggleDhcpServerDisabled(server.id, nextDisabled) },
            liveAction = { restClient.toggleDhcpServerDisabled(it, server.id, nextDisabled) },
            successMessage = if (nextDisabled) "DHCP Server '${server.name}' dinonaktifkan" else "DHCP Server '${server.name}' diaktifkan",
            logTitle = "Status DHCP Server Berubah",
            logDetail = "${server.name} (${server.interfaceName}) -> ${if (nextDisabled) "Disabled" else "Enabled"}",
        )
    }

    fun deleteDhcpServer(server: DhcpServerEntry) {
        executeMutation(
            demoAction = { demoEngine.deleteDhcpServer(server.id) },
            liveAction = { restClient.deleteDhcpServer(it, server.id) },
            successMessage = "DHCP Server '${server.name}' dihapus",
            logTitle = "DHCP Server Dihapus",
            logDetail = "${server.name} (${server.interfaceName})",
        )
    }

    fun addStaticDhcpLease(address: String, macAddress: String, server: String, comment: String) {
        if (address.isBlank() || macAddress.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.addStaticDhcpLease(address.trim(), macAddress.trim(), server.trim(), comment.trim()) },
            liveAction = { restClient.addStaticDhcpLease(it, address.trim(), macAddress.trim(), server.trim(), comment.trim()) },
            successMessage = "Static Lease '${address.trim()}' ditambahkan",
            logTitle = "Static DHCP Lease Ditambahkan",
            logDetail = "${address.trim()} -> ${macAddress.trim().uppercase()}",
        )
    }

    fun makeDhcpLeaseStatic(lease: DhcpLeaseEntry) {
        executeMutation(
            demoAction = { demoEngine.makeDhcpLeaseStatic(lease.id) },
            liveAction = { restClient.makeDhcpLeaseStatic(it, lease.id) },
            successMessage = "Lease '${lease.address}' dijadikan Static",
            logTitle = "DHCP Lease Jadi Static",
            logDetail = "${lease.hostName} (${lease.address} • ${lease.macAddress})",
        )
    }

    fun toggleDhcpLeaseDisabled(lease: DhcpLeaseEntry) {
        val nextDisabled = !lease.disabled
        executeMutation(
            demoAction = { demoEngine.toggleDhcpLeaseDisabled(lease.id, nextDisabled) },
            liveAction = { restClient.toggleDhcpLeaseDisabled(it, lease.id, nextDisabled) },
            successMessage = if (nextDisabled) "Lease '${lease.address}' dinonaktifkan" else "Lease '${lease.address}' diaktifkan",
            logTitle = "Status DHCP Lease Berubah",
            logDetail = "${lease.address} (${lease.hostName}) -> ${if (nextDisabled) "Disabled" else "Enabled"}",
        )
    }

    fun deleteDhcpLease(lease: DhcpLeaseEntry) {
        executeMutation(
            demoAction = { demoEngine.deleteDhcpLease(lease.id) },
            liveAction = { restClient.deleteDhcpLease(it, lease.id) },
            successMessage = "Lease '${lease.address}' dihapus",
            logTitle = "DHCP Lease Dihapus",
            logDetail = "${lease.hostName} (${lease.address} • ${lease.macAddress})",
        )
    }

    fun toggleInterface(id: String, name: String, currentlyDisabled: Boolean) {
        val nextDisabled = !currentlyDisabled
        executeMutation(
            demoAction = { demoEngine.toggleInterface(id, nextDisabled) },
            liveAction = { restClient.toggleInterface(it, id, nextDisabled) },
            successMessage = if (nextDisabled) "Interface '$name' dinonaktifkan" else "Interface '$name' diaktifkan",
            logTitle = "Status Interface Berubah",
            logDetail = "$name -> ${if (nextDisabled) "Disabled" else "Enabled"}",
            refreshScope = MutationRefreshScope.INTERFACES,
        )
    }

    fun toggleWirelessInterface(wlan: WirelessInterface) {
        val nextDisabled = !wlan.disabled
        executeMutation(
            demoAction = { demoEngine.toggleWirelessDisabled(wlan.id, nextDisabled) },
            liveAction = { restClient.toggleWirelessInterface(it, wlan.id, nextDisabled, wlan.isWifiV7) },
            successMessage = if (nextDisabled) "Wi-Fi '${wlan.ssid}' (${wlan.name}) dinonaktifkan" else "Wi-Fi '${wlan.ssid}' (${wlan.name}) diaktifkan",
            logTitle = "Status Wi-Fi Berubah",
            logDetail = "${wlan.name} (${wlan.ssid}) -> ${if (nextDisabled) "Disabled" else "Enabled"}",
            refreshScope = MutationRefreshScope.FULL,
        )
    }

    fun updateInterface(iface: RouterInterface, comment: String, mtu: String) {
        executeMutation(
            demoAction = { demoEngine.updateInterface(iface.id, comment.trim(), mtu.trim()) },
            liveAction = { restClient.updateInterface(it, iface.id, comment.trim(), mtu.trim()) },
            successMessage = "Interface '${iface.name}' diperbarui",
            logTitle = "Interface Diperbarui",
            logDetail = "${iface.name} (MTU: ${mtu.ifBlank { iface.mtu }})",
            refreshScope = MutationRefreshScope.INTERFACES,
        )
    }

    fun resetInterfaceCounters(iface: RouterInterface) {
        executeMutation(
            demoAction = { demoEngine.resetInterfaceCounters(iface.id) },
            liveAction = { restClient.resetInterfaceCounters(it, iface.id) },
            successMessage = "Counter trafik '${iface.name}' di-reset",
            logTitle = "Counter Interface Di-reset",
            logDetail = iface.name,
            refreshScope = MutationRefreshScope.INTERFACES,
        )
    }

    fun updateWirelessInterface(
        wlan: WirelessInterface,
        ssid: String,
        frequency: String,
        comment: String,
    ) {
        if (ssid.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.updateWireless(wlan.id, ssid.trim(), frequency.trim(), comment.trim()) },
            liveAction = {
                restClient.updateWirelessInterface(
                    config = it,
                    id = wlan.id,
                    ssid = ssid.trim(),
                    frequency = frequency.trim(),
                    comment = comment.trim(),
                    isWifiV7 = wlan.isWifiV7,
                )
            },
            successMessage = "Wi-Fi '${ssid.trim()}' (${wlan.name}) diperbarui",
            logTitle = "Konfigurasi Wi-Fi Diperbarui",
            logDetail = "${wlan.name} -> SSID: ${ssid.trim()}, Freq: ${frequency.ifBlank { "auto" }}",
        )
    }

    fun updateIdentity(newName: String) {
        if (newName.isBlank()) return
        executeMutation(
            demoAction = { demoEngine.setIdentity(newName.trim()) },
            liveAction = { restClient.setIdentity(it, newName.trim()) },
            successMessage = "Identitas router diubah menjadi '${newName.trim()}'",
            logTitle = "Identitas Router Diubah",
            logDetail = newName.trim(),
        )
    }

    fun createBackup(backupName: String) {
        val name = backupName.trim().ifBlank { "erlkim_backup" }
        executeMutation(
            demoAction = { /* recorded in log */ },
            liveAction = { restClient.saveBackup(it, name) },
            successMessage = "Backup '$name.backup' berhasil dibuat di router",
            logTitle = "Backup Disimpan",
            logDetail = "File $name.backup tersimpan di RouterOS",
        )
    }

    fun loadRouterBackup(backupName: String) {
        val name = backupName.trim().ifBlank { return }
        executeMutation(
            demoAction = { /* simulated on-router load */ },
            liveAction = { restClient.loadRouterBackup(it, name) },
            successMessage = "Perintah restore '$name' dikirim ke RouterOS",
            logTitle = "Restore RouterOS",
            logDetail = "Memuat file backup internal: $name",
            isAlert = true,
        )
    }

    fun buildCurrentBackupPayload(): RouterBackupPayload {
        val state = _uiState.value
        val exportedAt = exportTimestampFormat.get()!!.format(Date())
        return RouterBackupPayload(
            schemaVersion = 1,
            sourceFormat = BackupExportFormat.JSON,
            exportedAt = exportedAt,
            routerIdentity = state.resource.identity,
            boardName = state.resource.boardName,
            routerOsVersion = state.resource.version,
            routerAddress = state.routerConfig.displayAddress,
            ipAddresses = state.ipAddresses,
            userProfiles = state.userProfiles,
            hotspotUsers = state.hotspotUsers,
            ipBindings = state.ipBindings,
            walledGarden = state.walledGarden,
            quotaAlertConfig = state.quotaAlertConfig,
        )
    }

    fun exportBackupToUri(
        uri: Uri,
        format: BackupExportFormat,
        onResult: (Result<String>) -> Unit,
    ) {
        viewModelScope.launch {
            val payload = buildCurrentBackupPayload()
            val content = when (format) {
                BackupExportFormat.JSON -> RouterBackupManager.serializeToJson(payload)
                BackupExportFormat.RSC -> RouterBackupManager.serializeToRsc(payload)
            }
            val result = RouterBackupManager.writeTextToUri(getApplication(), uri, content)
            result.onSuccess { bytesWritten ->
                val kb = (bytesWritten / 1024.0).coerceAtLeast(0.1)
                val summary = String.format(
                    Locale.US,
                    "Exported %s (%d items, %.1f KB)",
                    format.extension.uppercase(Locale.US),
                    payload.totalItemCount,
                    kb,
                )
                appendLog(
                    title = "Config Exported",
                    detail = "${payload.routerIdentity} -> ${format.label} (${payload.totalItemCount} items)",
                    severity = LogSeverity.INFO,
                    topics = "system,backup,info",
                )
                _snackbarMessage.tryEmit(summary)
                onResult(Result.success(summary))
            }.onFailure { err ->
                val msg = err.message ?: "Failed to export backup file"
                _snackbarMessage.tryEmit("Export failed: $msg")
                onResult(Result.failure(err))
            }
        }
    }

    fun parseBackupFromUri(
        uri: Uri,
        onResult: (Result<ImportedBackupPreview>) -> Unit,
    ) {
        viewModelScope.launch {
            val readResult = RouterBackupManager.readTextFromUri(getApplication(), uri)
            readResult.onSuccess { (fileName, rawText) ->
                val parsed = RouterBackupManager.parseBackupContent(rawText)
                parsed.onSuccess { payload ->
                    onResult(
                        Result.success(
                            ImportedBackupPreview(
                                fileName = fileName,
                                fileSizeBytes = rawText.toByteArray().size,
                                payload = payload,
                            )
                        )
                    )
                }.onFailure { err ->
                    onResult(Result.failure(err))
                }
            }.onFailure { err ->
                onResult(Result.failure(err))
            }
        }
    }

    fun restoreFromBackup(
        payload: RouterBackupPayload,
        options: BackupRestoreOptions,
        onResult: (Result<RestoreSummary>) -> Unit,
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            val quotaCfg = payload.quotaAlertConfig
            val restoreQuota = options.restoreQuotaConfig && quotaCfg != null

            when (state.connectionStatus) {
                ConnectionStatus.ConnectedDemo -> {
                    val baseSummary = demoEngine.applyBackup(payload, options)
                    if (restoreQuota) {
                        dataStore.saveQuotaAlertConfig(quotaCfg)
                        _uiState.update { it.copy(quotaAlertConfig = quotaCfg) }
                    }
                    val finalSummary = baseSummary.copy(quotaConfigRestored = restoreQuota)
                    applySnapshot(demoEngine.snapshot(), ConnectionStatus.ConnectedDemo)
                    appendLog(
                        title = "Config Restored",
                        detail = "Restored ${finalSummary.totalApplied} items from ${payload.routerIdentity} (${payload.sourceFormat.extension.uppercase(Locale.US)})",
                        severity = LogSeverity.INFO,
                        topics = "system,backup,info",
                    )
                    _snackbarMessage.tryEmit("Configuration restored (${finalSummary.totalApplied} items applied)")
                    onResult(Result.success(finalSummary))
                }
                ConnectionStatus.ConnectedLive -> {
                    val liveResult = restClient.restoreBackupPayload(state.routerConfig, payload, options)
                    liveResult.onSuccess { baseSummary ->
                        if (restoreQuota) {
                            dataStore.saveQuotaAlertConfig(quotaCfg)
                            _uiState.update { it.copy(quotaAlertConfig = quotaCfg) }
                        }
                        val finalSummary = baseSummary.copy(quotaConfigRestored = restoreQuota)
                        appendLog(
                            title = "Config Restored",
                            detail = "Applied ${finalSummary.totalApplied} items to ${state.routerConfig.displayAddress} (skipped ${finalSummary.skippedCount})",
                            severity = LogSeverity.INFO,
                            topics = "system,backup,info",
                        )
                        _snackbarMessage.tryEmit("Configuration restored (${finalSummary.totalApplied} applied, ${finalSummary.skippedCount} skipped)")
                        restClient.fetchSnapshot(state.routerConfig).onSuccess { snap ->
                            applySnapshot(snap, ConnectionStatus.ConnectedLive)
                        }
                        onResult(Result.success(finalSummary))
                    }.onFailure { err ->
                        val msg = err.message ?: "RouterOS restore error"
                        _snackbarMessage.tryEmit("Restore failed: $msg")
                        onResult(Result.failure(err))
                    }
                }
                else -> {
                    val err = IllegalStateException("Connect to a MikroTik router or enable Demo Mode before restoring.")
                    _snackbarMessage.tryEmit(err.message!!)
                    onResult(Result.failure(err))
                }
            }
        }
    }

    fun rebootRouter() {
        executeMutation(
            demoAction = { /* simulated reboot */ },
            liveAction = { restClient.rebootRouter(it) },
            successMessage = "Perintah Reboot dikirim ke router",
            logTitle = "Router Reboot",
            logDetail = "Perintah mulai ulang perangkat telah dieksekusi",
            isAlert = true,
            refreshScope = MutationRefreshScope.NONE,
        )
    }

    fun shutdownRouter() {
        executeMutation(
            demoAction = { /* simulated shutdown */ },
            liveAction = { restClient.shutdownRouter(it) },
            successMessage = "Perintah Shutdown dikirim ke router",
            logTitle = "Router Shutdown",
            logDetail = "Perintah mematikan perangkat telah dieksekusi",
            isAlert = true,
            refreshScope = MutationRefreshScope.NONE,
        )
    }

    private enum class MutationRefreshScope {
        FULL,
        OPERATIONAL,
        INTERFACES,
        NONE,
    }

    private fun executeMutation(
        demoAction: () -> Unit,
        liveAction: suspend (RouterConfig) -> Result<Unit>,
        successMessage: String,
        logTitle: String,
        logDetail: String,
        isAlert: Boolean = false,
        refreshScope: MutationRefreshScope = MutationRefreshScope.FULL,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            try {
                val state = _uiState.value
                when (state.connectionStatus) {
                    ConnectionStatus.ConnectedDemo -> {
                        demoAction()
                        applySnapshot(demoEngine.snapshot(), ConnectionStatus.ConnectedDemo)
                        appendLog(logTitle, logDetail, isAlert)
                        _snackbarMessage.tryEmit(successMessage)
                        ensureTelemetryAlive()
                    }
                    ConnectionStatus.ConnectedLive -> {
                        liveAction(state.routerConfig)
                            .onSuccess {
                                appendLog(logTitle, logDetail, isAlert)
                                _snackbarMessage.tryEmit(successMessage)
                                when (refreshScope) {
                                    MutationRefreshScope.FULL -> {
                                        restClient.fetchSnapshot(state.routerConfig).onSuccess { snap ->
                                            applySnapshot(snap, ConnectionStatus.ConnectedLive)
                                        }
                                    }
                                    MutationRefreshScope.OPERATIONAL -> {
                                        restClient.fetchOperationalSnapshot(state.routerConfig).onSuccess { op ->
                                            applyOperationalSnapshot(op)
                                        }
                                    }
                                    MutationRefreshScope.INTERFACES -> refreshInterfacesSection(silent = true)
                                    MutationRefreshScope.NONE -> Unit
                                }
                                ensureTelemetryAlive()
                            }
                            .onFailure { err ->
                                _snackbarMessage.tryEmit("Gagal: ${err.message ?: "Error RouterOS"}")
                            }
                    }
                    else -> {
                        _snackbarMessage.tryEmit("Hubungkan ke Router MikroTik atau aktifkan Mode Demo terlebih dahulu")
                    }
                }
            } finally {
                _uiState.update { it.copy(isMutating = false) }
            }
        }
    }

    private fun appendLog(
        title: String,
        detail: String,
        isAlert: Boolean = false,
        severity: LogSeverity = if (isAlert) LogSeverity.ERROR else LogSeverity.INFO,
        topics: String = if (isAlert) "system,error" else "system,info",
    ) {
        val time = currentTimeString()
        val entry = RouterLogEntry(
            id = "local_${System.currentTimeMillis()}_${title.hashCode()}",
            title = title,
            detail = detail,
            time = time,
            topics = topics,
            severity = severity,
            isAlert = isAlert || severity == LogSeverity.ERROR,
        )
        _uiState.update { state ->
            state.copy(logs = (listOf(entry) + state.logs).take(140))
        }
    }

    fun updatePingTarget(host: String) {
        _uiState.update { state ->
            state.copy(
                pingState = state.pingState.copy(
                    targetHost = host,
                    hasCustomTarget = true,
                    lastError = null,
                )
            )
        }
    }

    fun updatePingCount(count: Int) {
        val clamped = count.coerceIn(1, 10)
        _uiState.update { state ->
            state.copy(pingState = state.pingState.copy(packetCount = clamped))
        }
    }

    fun togglePingSource(useRouterApi: Boolean) {
        _uiState.update { state ->
            state.copy(pingState = state.pingState.copy(useRouterApi = useRouterApi))
        }
    }

    fun clearPingResults() {
        if (_uiState.value.pingState.isRunning) return
        _uiState.update { state ->
            state.copy(
                pingState = state.pingState.copy(
                    replies = emptyList(),
                    lastError = null,
                )
            )
        }
    }

    fun stopPing() {
        pingJob?.cancel()
        pingJob = null
        _uiState.update { state ->
            state.copy(pingState = state.pingState.copy(isRunning = false))
        }
    }

    fun startPing() {
        val state = _uiState.value
        val sanitized = runCatching {
            pingEngine.sanitizeHost(state.pingState.targetHost)
        }.getOrElse { err ->
            _uiState.update {
                it.copy(pingState = it.pingState.copy(lastError = err.message ?: "Invalid host"))
            }
            return
        }

        pingJob?.cancel()
        val totalPackets = state.pingState.packetCount.coerceIn(1, 10)
        val useRouter = state.pingState.useRouterApi && state.connectionStatus is ConnectionStatus.ConnectedLive
        val routerCfg = state.routerConfig

        _uiState.update {
            it.copy(
                pingState = it.pingState.copy(
                    targetHost = sanitized,
                    isRunning = true,
                    replies = emptyList(),
                    lastError = null,
                )
            )
        }

        pingJob = viewModelScope.launch {
            try {
                for (seq in 1..totalPackets) {
                    if (!isActive) break
                    val reply = if (useRouter) {
                        pingEngine.pingOnceFromRouterOs(routerCfg, sanitized, seq)
                    } else {
                        pingEngine.pingOnceFromDevice(
                            host = sanitized,
                            seq = seq,
                            fallbackTcpPort = routerCfg.port,
                        )
                    }
                    _uiState.update { current ->
                        current.copy(
                            pingState = current.pingState.copy(
                                replies = current.pingState.replies + reply,
                            )
                        )
                    }
                    if (seq < totalPackets) {
                        delay(420L)
                    }
                }
            } finally {
                val finalPing = _uiState.value.pingState
                _uiState.update { current ->
                    current.copy(pingState = current.pingState.copy(isRunning = false))
                }
                if (finalPing.replies.isNotEmpty()) {
                    val avgText = finalPing.avgMs?.let { String.format(Locale.US, "avg %.1f ms", it) } ?: "timeout"
                    appendLog(
                        title = "Ping Diagnostic",
                        detail = "$sanitized — $avgText (${finalPing.receivedCount}/${finalPing.sentCount} received, ${finalPing.packetLossPercent}% loss)",
                        severity = if (finalPing.receivedCount == 0) LogSeverity.WARNING else LogSeverity.INFO,
                        topics = "diagnostic,ping,info",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        connectJob?.cancel()
        refreshJob?.cancel()
        pollingJob?.cancel()
        pingJob?.cancel()
        super.onCleared()
    }
}
