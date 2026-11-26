package com.erlkim.mikrotik

import com.erlkim.mikrotik.data.backup.RouterBackupManager
import com.erlkim.mikrotik.data.backup.RouterBackupPayload
import com.erlkim.mikrotik.data.model.QuotaAlertConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuotaAndLogsTest {

    @Test
    fun `default quota limit is per-user only and survives backup roundtrip`() {
        val defaultCfg = QuotaAlertConfig()
        assertEquals(0L, defaultCfg.globalQuotaLimitMb)

        val payload = RouterBackupPayload(
            exportedAt = "2026-11-26 11:15:00",
            routerIdentity = "RB3011UiAS",
            boardName = "RB3011UiAS",
            routerOsVersion = "7.15",
            routerAddress = "192.168.88.1",
            ipAddresses = emptyList(),
            userProfiles = emptyList(),
            hotspotUsers = emptyList(),
            ipBindings = emptyList(),
            walledGarden = emptyList(),
            quotaAlertConfig = defaultCfg,
        )
        val json = RouterBackupManager.serializeToJson(payload)
        val parsed = RouterBackupManager.parseBackupContent(json)
        assertTrue(parsed.isSuccess)
        assertEquals(0L, parsed.getOrThrow().quotaAlertConfig?.globalQuotaLimitMb)
    }
}
