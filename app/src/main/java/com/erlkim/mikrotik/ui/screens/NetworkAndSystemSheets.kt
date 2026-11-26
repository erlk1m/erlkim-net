package com.erlkim.mikrotik.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erlkim.mikrotik.R
import com.erlkim.mikrotik.data.model.RouterLogEntry
import com.erlkim.mikrotik.data.model.RouterResource
import com.erlkim.mikrotik.data.model.RouterSystemUser
import com.erlkim.mikrotik.ui.components.ErlkimBrandTitle
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityAndTimeSheet(
    resource: RouterResource,
    onSaveIdentity: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var identity by remember(resource.identity) { mutableStateOf(resource.identity) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cs.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Identitas & Waktu Router", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = identity,
                onValueChange = { identity = it },
                label = { Text("System Identity") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            DetailRow("Board", resource.boardName)
            DetailRow("Waktu Router", "${resource.clockDate} ${resource.clockTime}")
            DetailRow("Zona Waktu", resource.timeZoneName)
            DetailRow("Uptime", resource.uptime)
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = {
                    onSaveIdentity(identity)
                    onDismiss()
                },
                enabled = identity.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Simpan Identitas") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersSecuritySheet(
    systemUsers: List<RouterSystemUser>,
    onOpenApiConnection: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cs.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Pengguna & Keamanan RouterOS", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    onDismiss()
                    onOpenApiConnection()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Ubah Kredensial API RouterOS") }
            Spacer(modifier = Modifier.height(14.dp))
            Text("AKUN ADMIN ROUTER (${systemUsers.size})", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(systemUsers, key = { it.id }) { u ->
                    Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${u.name}  (${u.group})", style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                            Text("Login terakhir: ${u.lastLoggedIn}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppSheet(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("about_app_sheet"),
        ) {
            // Hero Brand Card matching Dashboard ErlkimNET branding
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = cs.surfaceContainerHigh,
                border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.28f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    cs.primary.copy(alpha = 0.14f),
                                    cs.secondary.copy(alpha = 0.08f),
                                    cs.surfaceContainerHigh,
                                ),
                            ),
                        )
                        .padding(18.dp),
                ) {
                    ErlkimBrandTitle(compact = false)
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = cs.primaryContainer.copy(alpha = 0.75f),
                    ) {
                        Text(
                            text = stringResource(R.string.about_version_badge),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = cs.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        lineHeight = 21.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Gemini Neo 4 Co-Creation Highlight Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_gemini_credit_card"),
                shape = RoundedCornerShape(20.dp),
                color = cs.secondaryContainer.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, cs.tertiary.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(cs.primary, cs.tertiary),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = cs.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.about_credit_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.about_credit_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.about_spec_section),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                ),
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = cs.surfaceContainerHigh,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    DetailRow(
                        label = stringResource(R.string.about_spec_version_label),
                        value = stringResource(R.string.about_spec_version_value),
                    )
                    DetailRow(
                        label = stringResource(R.string.about_spec_collab_label),
                        value = stringResource(R.string.about_spec_collab_value),
                    )
                    DetailRow(
                        label = stringResource(R.string.about_spec_protocol_label),
                        value = stringResource(R.string.about_spec_protocol_value),
                    )
                    DetailRow(
                        label = stringResource(R.string.about_spec_auth_label),
                        value = stringResource(R.string.about_spec_auth_value),
                    )
                    DetailRow(
                        label = stringResource(R.string.about_spec_ui_label),
                        value = stringResource(R.string.about_spec_ui_value),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsLogSheet(
    logs: List<RouterLogEntry>,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cs.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Aktivitas & Log Router", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
            Spacer(modifier = Modifier.height(12.dp))
            if (logs.isEmpty()) {
                Text("Belum ada aktivitas tercatat.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(logs, key = { it.id }) { log ->
                        Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = log.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (log.isAlert) ext.offlineRed else cs.onSurface,
                                    )
                                    Text(
                                        text = log.time,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(log.detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
