package com.erlkim.mikrotik.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erlkim.mikrotik.R
import com.erlkim.mikrotik.data.backup.BackupExportFormat
import com.erlkim.mikrotik.data.backup.BackupRestoreOptions
import com.erlkim.mikrotik.data.backup.ImportedBackupPreview
import com.erlkim.mikrotik.data.backup.RestoreSummary
import com.erlkim.mikrotik.data.backup.RouterBackupManager
import com.erlkim.mikrotik.data.backup.RouterBackupPayload
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import com.erlkim.mikrotik.ui.viewmodel.ErlkimUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BackupRestoreSheet(
    uiState: ErlkimUiState,
    onExportToUri: (Uri, BackupExportFormat, (Result<String>) -> Unit) -> Unit,
    onParseBackupUri: (Uri, (Result<ImportedBackupPreview>) -> Unit) -> Unit,
    onRestoreBackup: (RouterBackupPayload, BackupRestoreOptions, (Result<RestoreSummary>) -> Unit) -> Unit,
    onCreateRouterBackup: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Export, 1 = Import & Restore
    var selectedFormat by remember { mutableStateOf(BackupExportFormat.JSON) }
    var routerFlashName by remember { mutableStateOf("erlkim_rb3011_backup") }

    var isBusy by remember { mutableStateOf(false) }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }
    var exportErrorMessage by remember { mutableStateOf<String?>(null) }

    var importedPreview by remember { mutableStateOf<ImportedBackupPreview?>(null) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var restoreResultSummary by remember { mutableStateOf<RestoreSummary?>(null) }
    var restoreOptions by remember { mutableStateOf(BackupRestoreOptions()) }

    val jsonCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BackupExportFormat.JSON.mimeType),
    ) { uri ->
        if (uri != null) {
            isBusy = true
            exportErrorMessage = null
            onExportToUri(uri, BackupExportFormat.JSON) { res ->
                isBusy = false
                res.onSuccess { exportStatusMessage = it }
                    .onFailure { exportErrorMessage = it.message ?: "Export failed" }
            }
        }
    }

    val rscCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BackupExportFormat.RSC.mimeType),
    ) { uri ->
        if (uri != null) {
            isBusy = true
            exportErrorMessage = null
            onExportToUri(uri, BackupExportFormat.RSC) { res ->
                isBusy = false
                res.onSuccess { exportStatusMessage = it }
                    .onFailure { exportErrorMessage = it.message ?: "Export failed" }
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            isBusy = true
            importErrorMessage = null
            restoreResultSummary = null
            onParseBackupUri(uri) { res ->
                isBusy = false
                res.onSuccess { preview ->
                    importedPreview = preview
                    restoreOptions = BackupRestoreOptions(
                        restoreIdentity = preview.payload.routerIdentity.isNotBlank(),
                        restoreProfiles = preview.payload.userProfiles.isNotEmpty(),
                        restoreHotspotUsers = preview.payload.hotspotUsers.isNotEmpty(),
                        restoreIpBindings = preview.payload.ipBindings.isNotEmpty(),
                        restoreWalledGarden = preview.payload.walledGarden.isNotEmpty(),
                        restoreIpAddresses = preview.payload.ipAddresses.isNotEmpty(),
                        restoreQuotaConfig = preview.payload.quotaAlertConfig != null,
                    )
                }.onFailure { err ->
                    importedPreview = null
                    importErrorMessage = err.message ?: "Unable to read backup file"
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudUpload,
                        contentDescription = null,
                        tint = cs.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.backup_sheet_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = cs.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.backup_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode Segmented Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surfaceContainerHigh)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BackupTabPill(
                    label = stringResource(R.string.backup_tab_export),
                    icon = Icons.Rounded.CloudDownload,
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("backup_tab_export"),
                )
                BackupTabPill(
                    label = stringResource(R.string.backup_tab_import),
                    icon = Icons.Rounded.Restore,
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("backup_tab_import"),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // EXPORT TAB
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "CURRENT ROUTER SNAPSHOT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = cs.primary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.resource.identity}  •  ${uiState.routerConfig.displayAddress}",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SnapshotStatChip("Users: ${uiState.hotspotUsers.size}")
                            SnapshotStatChip("Profiles: ${uiState.userProfiles.size}")
                            SnapshotStatChip("IPs: ${uiState.ipAddresses.size}")
                            SnapshotStatChip("Bindings: ${uiState.ipBindings.size}")
                            SnapshotStatChip("Walled Garden: ${uiState.walledGarden.size}")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Backup File Format",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))

                BackupExportFormat.entries.forEach { format ->
                    val isSelected = selectedFormat == format
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedFormat = format }
                            .testTag("backup_format_${format.extension}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) cs.primaryContainer.copy(alpha = 0.55f) else cs.surfaceContainerHigh,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) cs.primary else cs.outlineVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                tint = if (isSelected) cs.primary else cs.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = format.label,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = cs.onSurface,
                                )
                                Text(
                                    text = format.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        val fileName = RouterBackupManager.suggestedFileName(
                            routerIdentity = uiState.resource.identity,
                            format = selectedFormat,
                        )
                        when (selectedFormat) {
                            BackupExportFormat.JSON -> jsonCreateLauncher.launch(fileName)
                            BackupExportFormat.RSC -> rscCreateLauncher.launch(fileName)
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("backup_export_file_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = cs.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.backup_action_export_file))
                }

                exportStatusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    StatusNoticeCard(
                        message = msg,
                        isError = false,
                    )
                }
                exportErrorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    StatusNoticeCard(
                        message = err,
                        isError = true,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Secondary: On-Router MikroTik Flash .backup
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Router,
                        contentDescription = null,
                        tint = cs.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "On-Router Binary Backup (.backup)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Creates a native .backup file directly on MikroTik flash storage via /rest/system/backup/save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = routerFlashName,
                    onValueChange = { routerFlashName = it },
                    label = { Text("Router Backup Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_router_name_input"),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = { onCreateRouterBackup(routerFlashName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("backup_save_router_flash_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.backup_action_router_save))
                }
            } else {
                // IMPORT & RESTORE TAB
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Select Router Backup File",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Supports Erlkim NET JSON (.json) and MikroTik RouterOS scripts (.rsc).",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = {
                                openBackupLauncher.launch(
                                    arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                                )
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("backup_choose_file_button"),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_action_choose_file))
                        }
                    }
                }

                importErrorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(12.dp))
                    StatusNoticeCard(message = err, isError = true)
                }

                importedPreview?.let { preview ->
                    val p = preview.payload
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_import_preview_card"),
                        shape = RoundedCornerShape(18.dp),
                        color = cs.surfaceContainerHigh,
                        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.45f)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preview.fileName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${p.routerIdentity} • ${p.exportedAt}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = cs.primaryContainer,
                                ) {
                                    Text(
                                        text = p.sourceFormat.extension.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                        ),
                                        color = cs.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Select sections to restore:",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            RestoreOptionRow(
                                label = "Router Identity (${p.routerIdentity})",
                                checked = restoreOptions.restoreIdentity,
                                enabled = p.routerIdentity.isNotBlank(),
                                onCheckedChange = { restoreOptions = restoreOptions.copy(restoreIdentity = it) },
                            )
                            RestoreOptionRow(
                                label = "Hotspot User Profiles (${p.userProfiles.size})",
                                checked = restoreOptions.restoreProfiles,
                                enabled = p.userProfiles.isNotEmpty(),
                                onCheckedChange = { restoreOptions = restoreOptions.copy(restoreProfiles = it) },
                            )
                            RestoreOptionRow(
                                label = "Hotspot Users (${p.hotspotUsers.size})",
                                checked = restoreOptions.restoreHotspotUsers,
                                enabled = p.hotspotUsers.isNotEmpty(),
                                onCheckedChange = { restoreOptions = restoreOptions.copy(restoreHotspotUsers = it) },
                            )
                            RestoreOptionRow(
                                label = "IP Bindings (${p.ipBindings.size})",
                                checked = restoreOptions.restoreIpBindings,
                                enabled = p.ipBindings.isNotEmpty(),
                                onCheckedChange = { restoreOptions = restoreOptions.copy(restoreIpBindings = it) },
                            )
                            RestoreOptionRow(
                                label = "Walled Garden (${p.walledGarden.size})",
                                checked = restoreOptions.restoreWalledGarden,
                                enabled = p.walledGarden.isNotEmpty(),
                                onCheckedChange = { restoreOptions = restoreOptions.copy(restoreWalledGarden = it) },
                            )
                            RestoreOptionRow(
                                label = "IP Addresses (${p.ipAddresses.size})",
                                checked = restoreOptions.restoreIpAddresses,
                                enabled = p.ipAddresses.isNotEmpty(),
                                onCheckedChange = { restoreOptions = restoreOptions.copy(restoreIpAddresses = it) },
                            )
                            if (p.quotaAlertConfig != null) {
                                val limitLabel = if (p.quotaAlertConfig.globalQuotaLimitMb == 0L) {
                                    "Per-User"
                                } else {
                                    "${p.quotaAlertConfig.globalQuotaLimitMb} MB"
                                }
                                RestoreOptionRow(
                                    label = "Bandwidth Quota Alert Rules ($limitLabel)",
                                    checked = restoreOptions.restoreQuotaConfig,
                                    enabled = true,
                                    onCheckedChange = { restoreOptions = restoreOptions.copy(restoreQuotaConfig = it) },
                                )
                            }

                            if (uiState.routerConfig.demoMode) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Replace existing entries",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = cs.onSurface,
                                        )
                                        Text(
                                            text = "Off = merge with current configuration",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = cs.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = restoreOptions.replaceExistingDemoData,
                                        onCheckedChange = {
                                            restoreOptions = restoreOptions.copy(replaceExistingDemoData = it)
                                        },
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        importedPreview = null
                                        restoreResultSummary = null
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text("Clear")
                                }
                                Button(
                                    onClick = {
                                        isBusy = true
                                        importErrorMessage = null
                                        onRestoreBackup(p, restoreOptions) { res ->
                                            isBusy = false
                                            res.onSuccess { summary ->
                                                restoreResultSummary = summary
                                            }.onFailure { err ->
                                                importErrorMessage = err.message ?: "Restore failed"
                                            }
                                        }
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .weight(1.6f)
                                        .height(48.dp)
                                        .testTag("backup_restore_confirm_button"),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = cs.onPrimary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(stringResource(R.string.backup_action_restore_now))
                                }
                            }
                        }
                    }
                }

                restoreResultSummary?.let { summary ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = ext.onlineContainer,
                        border = BorderStroke(1.dp, ext.onlineGreen.copy(alpha = 0.45f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = ext.onlineGreen,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Recovery Completed (${summary.totalApplied} applied)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = cs.onSurface,
                                )
                                Text(
                                    text = "Profiles: ${summary.profilesRestored} • Users: ${summary.usersRestored} • IPs: ${summary.ipAddressesRestored} • Skipped: ${summary.skippedCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupTabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) cs.primary else cs.surfaceContainerHigh,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SnapshotStatChip(text: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cs.surfaceContainerHighest,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
            color = cs.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun RestoreOptionRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked && enabled,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusNoticeCard(
    message: String,
    isError: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val bg = if (isError) ext.offlineContainer else ext.onlineContainer
    val fg = if (isError) ext.offlineRed else ext.onlineGreen

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface,
            )
        }
    }
}
