package com.erlkim.mikrotik.ui.viewmodel

import com.erlkim.mikrotik.data.model.HotspotUser

sealed interface ActiveModal {
    data object Connection : ActiveModal
    data object AddUser : ActiveModal
    data class UserDetail(val user: HotspotUser) : ActiveModal
    data class UserQrCode(val user: HotspotUser) : ActiveModal
    data object ActiveSessions : ActiveModal
    data object WalledGarden : ActiveModal
    data object IpBindings : ActiveModal
    data object HotspotServers : ActiveModal
    data object UserProfiles : ActiveModal
    data object Interfaces : ActiveModal
    data object DhcpServer : ActiveModal
    data object Wireless : ActiveModal
    data object IpAddresses : ActiveModal
    data object IdentityTime : ActiveModal
    data object UsersSecurity : ActiveModal
    data object BackupRestore : ActiveModal
    data object RebootShutdown : ActiveModal
    data object ThemeSettings : ActiveModal
    data object AboutApp : ActiveModal
    data object Notifications : ActiveModal
    data object QuotaAlerts : ActiveModal
    data object ThroughputAlerts : ActiveModal
    data object ResourceAlerts : ActiveModal
}
