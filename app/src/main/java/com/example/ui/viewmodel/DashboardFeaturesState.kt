package com.example.ui.viewmodel

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class InterfaceFilter(val label: String) {
    ALL("Semua"),
    RUNNING("Running"),
    DOWN("Link Down"),
    DISABLED("Disabled"),
}

data class InterfacesFeatureUiState(
    val searchQuery: String = "",
    val selectedFilter: InterfaceFilter = InterfaceFilter.ALL,
    val editingInterfaceId: String? = null,
    val commentDraft: String = "",
    val mtuDraft: String = "1500",
    val lastSyncedMs: Long = 0L,
    val isRefreshingSection: Boolean = false,
)

enum class IpAddressFilter(val label: String) {
    ALL("Semua"),
    ACTIVE("Aktif"),
    DISABLED("Nonaktif"),
    DYNAMIC("Dynamic"),
}

data class IpAddressesFeatureUiState(
    val searchQuery: String = "",
    val selectedFilter: IpAddressFilter = IpAddressFilter.ALL,
    val showForm: Boolean = false,
    val editingEntryId: String? = null,
    val ipDraft: String = "",
    val interfaceDraft: String = "",
    val commentDraft: String = "",
    val lastSyncedMs: Long = 0L,
    val isRefreshingSection: Boolean = false,
)

enum class DhcpFeatureTab {
    SERVERS,
    LEASES,
}

enum class DhcpLeaseFilter(val label: String) {
    ALL("Semua"),
    BOUND("Bound"),
    STATIC("Static"),
    DYNAMIC("Dynamic"),
}

data class DhcpServerFeatureUiState(
    val activeTab: DhcpFeatureTab = DhcpFeatureTab.SERVERS,
    val showServerForm: Boolean = false,
    val editingServerId: String? = null,
    val serverNameDraft: String = "",
    val serverInterfaceDraft: String = "",
    val serverPoolDraft: String = "pool-lan",
    val serverLeaseTimeDraft: String = "1h",
    val showLeaseForm: Boolean = false,
    val leaseIpDraft: String = "",
    val leaseMacDraft: String = "",
    val leaseServerDraft: String = "",
    val leaseCommentDraft: String = "",
    val leaseSearchQuery: String = "",
    val leaseFilter: DhcpLeaseFilter = DhcpLeaseFilter.ALL,
    val lastSyncedMs: Long = 0L,
    val isRefreshingSection: Boolean = false,
)

data class SystemControlFeatureUiState(
    val lastSyncedMs: Long = 0L,
    val isRefreshingSection: Boolean = false,
)

enum class IpBindingFilter(val label: String) {
    ALL("Semua"),
    BYPASSED("Bypassed"),
    BLOCKED("Blocked"),
    REGULAR("Regular"),
    DISABLED("Nonaktif"),
}

data class IpBindingsFeatureUiState(
    val searchQuery: String = "",
    val selectedFilter: IpBindingFilter = IpBindingFilter.ALL,
    val showForm: Boolean = false,
    val editingEntryId: String? = null,
    val macDraft: String = "",
    val addressDraft: String = "",
    val toAddressDraft: String = "",
    val serverDraft: String = "all",
    val typeDraft: String = "bypassed",
    val commentDraft: String = "",
    val lastSyncedMs: Long = 0L,
    val isRefreshingSection: Boolean = false,
)

data class SettingsFeatureUiState(
    val expandedSections: Set<String> = setOf("connection"),
)

data class DashboardFeaturesUiState(
    val interfacesState: InterfacesFeatureUiState = InterfacesFeatureUiState(),
    val ipAddressesState: IpAddressesFeatureUiState = IpAddressesFeatureUiState(),
    val dhcpState: DhcpServerFeatureUiState = DhcpServerFeatureUiState(),
    val systemState: SystemControlFeatureUiState = SystemControlFeatureUiState(),
    val ipBindingsState: IpBindingsFeatureUiState = IpBindingsFeatureUiState(),
    val settingsState: SettingsFeatureUiState = SettingsFeatureUiState(),
)

fun formatFeatureSyncBadge(lastSyncedMs: Long, isConnected: Boolean): String {
    if (lastSyncedMs <= 0L) return if (isConnected) "Menyinkronkan…" else "Belum ada cache"
    val timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastSyncedMs))
    return if (isConnected) "Sinkron $timeText" else "Cache $timeText"
}
