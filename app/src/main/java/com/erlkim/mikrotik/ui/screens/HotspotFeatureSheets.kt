package com.erlkim.mikrotik.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.erlkim.mikrotik.R
import com.erlkim.mikrotik.data.model.HotspotActiveSession
import com.erlkim.mikrotik.data.model.HotspotUser
import com.erlkim.mikrotik.data.model.formatBytes
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import java.security.SecureRandom

private const val DETAIL_PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"

private fun generateNewHotspotPassword(length: Int = 8): String {
    val random = SecureRandom()
    return buildString(length) {
        repeat(length) {
            append(DETAIL_PASSWORD_ALPHABET[random.nextInt(DETAIL_PASSWORD_ALPHABET.length)])
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotUserDetailSheet(
    user: HotspotUser,
    onDismiss: () -> Unit,
    onShowQrCode: () -> Unit,
    onUpdatePassword: (String) -> Unit,
    onUpdateSessionTimeout: (String) -> Unit,
    onToggleDisabled: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var passwordInput by remember(user.id, user.password) { mutableStateOf(user.password) }
    var showPassword by remember { mutableStateOf(false) }
    val trimmedPassword = passwordInput.trim()
    val hasPasswordChanged = trimmedPassword.isNotEmpty() && trimmedPassword != user.password

    val initialTimeout = remember(user.id, user.sessionTimeout) {
        if (user.formattedSessionTimeout() == "Unlimited") "" else user.sessionTimeout.trim()
    }
    var timeoutInput by remember(user.id, user.sessionTimeout) { mutableStateOf(initialTimeout) }

    val durationRegex = remember {
        Regex("""^(\d+[wdhms](\s*\d+[wdhms])*|\d{1,2}:\d{2}(:\d{2})?)$""", RegexOption.IGNORE_CASE)
    }
    val trimmedInput = timeoutInput.trim()
    val isValidDuration = trimmedInput.isEmpty() ||
        trimmedInput.equals("unlimited", true) ||
        trimmedInput.equals("none", true) ||
        durationRegex.matches(trimmedInput)
    val hasTimeoutChanged = trimmedInput != initialTimeout

    val timeoutPresets = listOf(
        "Unlimited" to "",
        "30m" to "30m",
        "1h" to "1h",
        "3h" to "3h",
        "12h" to "12h",
        "1d" to "1d",
        "7d" to "7d",
    )

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
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(user.name, style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (user.disabled) "Status: Nonaktif" else if (user.isActive) "Status: Sedang Online" else "Status: Siap Digunakan",
                style = MaterialTheme.typography.bodyMedium,
                color = if (user.isActive && !user.disabled) ext.onlineGreen else cs.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            DetailRow("Profil", user.profile)
            DetailRow("Kuota Terpakai", user.formattedQuota())
            DetailRow("Uptime", user.uptime)
            DetailRow(stringResource(R.string.session_timeout_label), user.formattedSessionTimeout())
            if (user.comment.isNotBlank()) DetailRow("Catatan", user.comment)

            Spacer(modifier = Modifier.height(16.dp))

            // Change Password Editor Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_change_password_card"),
                shape = RoundedCornerShape(18.dp),
                color = cs.surfaceContainerHigh,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.change_password_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = cs.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.change_password_helper),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.change_password_input_label)) },
                        placeholder = { Text(stringResource(R.string.change_password_placeholder)) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        passwordInput = generateNewHotspotPassword(8)
                                        showPassword = true
                                    },
                                    modifier = Modifier.testTag("detail_generate_password"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Casino,
                                        contentDescription = stringResource(R.string.cd_generate_password),
                                        tint = cs.primary,
                                    )
                                }
                                IconButton(
                                    onClick = { showPassword = !showPassword },
                                    modifier = Modifier.testTag("detail_toggle_password"),
                                ) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = stringResource(R.string.cd_toggle_password),
                                    )
                                }
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_change_password_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onUpdatePassword(trimmedPassword) },
                        enabled = hasPasswordChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("detail_change_password_save"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.change_password_save))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Individual Session Timeout Editor Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_session_timeout_card"),
                shape = RoundedCornerShape(18.dp),
                color = cs.surfaceContainerHigh,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.session_timeout_label),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = cs.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.session_timeout_helper),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(timeoutPresets, key = { it.first }) { (label, presetVal) ->
                            val isSelected = trimmedInput.equals(presetVal, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { timeoutInput = presetVal },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                    )
                                },
                                modifier = Modifier.testTag("timeout_preset_${label.lowercase()}"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = timeoutInput,
                            onValueChange = { timeoutInput = it },
                            label = { Text(stringResource(R.string.session_timeout_input_label)) },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.session_timeout_placeholder),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                )
                            },
                            isError = !isValidDuration,
                            supportingText = if (!isValidDuration) {
                                { Text(stringResource(R.string.session_timeout_invalid)) }
                            } else {
                                null
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("detail_session_timeout_input"),
                        )

                        Button(
                            onClick = { onUpdateSessionTimeout(trimmedInput) },
                            enabled = isValidDuration && hasTimeoutChanged,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .height(50.dp)
                                .testTag("detail_session_timeout_save"),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.session_timeout_save),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onShowQrCode()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("detail_show_qr_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = cs.primaryContainer,
                    contentColor = cs.onPrimaryContainer,
                ),
            ) {
                Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Login QR Code", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onToggleDisabled()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (user.disabled) "Aktifkan" else "Nonaktifkan")
                }
                Button(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ext.offlineRed, contentColor = cs.onPrimary),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hapus")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionsSheet(
    sessions: List<HotspotActiveSession>,
    onKick: (HotspotActiveSession) -> Unit,
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
            Text("Active Sessions (${sessions.size})", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
            Spacer(modifier = Modifier.height(12.dp))
            if (sessions.isEmpty()) {
                Text("Belum ada sesi hotspot aktif.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(sessions, key = { it.id }) { session ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cs.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(session.user, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                                    Text(
                                        text = "${session.address}  •  ${session.macAddress}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                        color = cs.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "Uptime: ${session.uptime}  •  Traffic: ${formatBytes(session.bytesIn + session.bytesOut)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onKick(session) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.PersonOff,
                                        contentDescription = "Putuskan sesi",
                                        tint = ext.offlineRed,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontFamily = JetBrainsMono),
            color = cs.onSurface,
        )
    }
    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
}
