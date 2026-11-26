package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.RouterConfig
import com.example.data.network.ConnectionTestResult
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalErlkimColors

private val HOST_REGEX = Regex("^[a-zA-Z0-9._-]+$")

private sealed interface TestConnectionUiState {
    data object Idle : TestConnectionUiState
    data object Testing : TestConnectionUiState
    data class Success(val result: ConnectionTestResult) : TestConnectionUiState
    data class Failure(val message: String) : TestConnectionUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterConnectionSheet(
    currentConfig: RouterConfig,
    onDismiss: () -> Unit,
    onTestConnection: (RouterConfig, (Result<ConnectionTestResult>) -> Unit) -> Unit,
    onSaveOnly: (RouterConfig) -> Unit,
    onConnect: (RouterConfig) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var host by remember(currentConfig) { mutableStateOf(currentConfig.host) }
    var portText by remember(currentConfig) { mutableStateOf(currentConfig.port.toString()) }
    var username by remember(currentConfig) { mutableStateOf(currentConfig.username) }
    var password by remember(currentConfig) { mutableStateOf(currentConfig.password) }
    var useHttps by remember(currentConfig) { mutableStateOf(currentConfig.useHttps) }
    var trustSelfSigned by remember(currentConfig) { mutableStateOf(currentConfig.trustSelfSigned) }
    var demoMode by remember(currentConfig) { mutableStateOf(currentConfig.demoMode) }
    var showPassword by remember { mutableStateOf(false) }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var testState by remember { mutableStateOf<TestConnectionUiState>(TestConnectionUiState.Idle) }

    val isTesting = testState is TestConnectionUiState.Testing
    val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
    val parsedPort = portText.toIntOrNull()
    val hostError = !demoMode && (cleanHost.isBlank() || !HOST_REGEX.matches(cleanHost))
    val portError = !demoMode && (parsedPort == null || parsedPort !in 1..65535)
    val usernameError = !demoMode && username.trim().isBlank()

    fun buildConfigOrNull(): RouterConfig? {
        attemptedSubmit = true
        if (!demoMode && (hostError || portError || usernameError)) return null
        return RouterConfig(
            host = cleanHost.ifBlank { "192.168.88.1" },
            port = (parsedPort ?: if (useHttps) 443 else 80).coerceIn(1, 65535),
            username = username.trim().ifBlank { "admin" },
            password = password,
            useHttps = useHttps,
            trustSelfSigned = trustSelfSigned,
            demoMode = demoMode,
        )
    }

    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current

    ModalBottomSheet(
        onDismissRequest = { if (!isTesting) onDismiss() },
        sheetState = sheetState,
        containerColor = cs.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("router_settings_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Router,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.router_settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = cs.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.router_settings_subtitle),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                TextButton(
                    onClick = {
                        host = "192.168.88.1"
                        portText = "80"
                        username = "admin"
                        password = ""
                        useHttps = false
                        trustSelfSigned = true
                        demoMode = false
                        attemptedSubmit = false
                        testState = TestConnectionUiState.Idle
                    },
                    enabled = !isTesting,
                    modifier = Modifier.testTag("router_settings_reset_button"),
                ) {
                    Text(
                        text = stringResource(R.string.router_settings_reset),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Explicit Mode Demo Toggle
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cs.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mode Demo (Simulasi Lokal)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface,
                        )
                        Text(
                            text = "Uji fitur UI tanpa router MikroTik fisik",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = demoMode,
                        enabled = !isTesting,
                        onCheckedChange = {
                            demoMode = it
                            testState = TestConnectionUiState.Idle
                        },
                        modifier = Modifier.testTag("switch_demo_mode"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        testState = TestConnectionUiState.Idle
                    },
                    label = { Text(stringResource(R.string.router_settings_ip_label)) },
                    placeholder = { Text("192.168.88.1") },
                    leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                    isError = attemptedSubmit && hostError,
                    supportingText = if (attemptedSubmit && hostError) {
                        { Text("Masukkan IP/host valid (tanpa spasi)") }
                    } else {
                        null
                    },
                    singleLine = true,
                    enabled = !demoMode && !isTesting,
                    modifier = Modifier
                        .weight(2.2f)
                        .testTag("input_router_host"),
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = {
                        portText = it.filter(Char::isDigit).take(5)
                        testState = TestConnectionUiState.Idle
                    },
                    label = { Text(stringResource(R.string.router_settings_port_label)) },
                    placeholder = { Text(if (useHttps) "443" else "80") },
                    isError = attemptedSubmit && portError,
                    supportingText = if (attemptedSubmit && portError) {
                        { Text("1-65535") }
                    } else {
                        null
                    },
                    singleLine = true,
                    enabled = !demoMode && !isTesting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_router_port"),
                    shape = RoundedCornerShape(14.dp),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    testState = TestConnectionUiState.Idle
                },
                label = { Text(stringResource(R.string.router_settings_username_label)) },
                placeholder = { Text("admin") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                isError = attemptedSubmit && usernameError,
                supportingText = if (attemptedSubmit && usernameError) {
                    { Text("Username tidak boleh kosong") }
                } else {
                    null
                },
                singleLine = true,
                enabled = !demoMode && !isTesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_router_user"),
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    testState = TestConnectionUiState.Idle
                },
                label = { Text(stringResource(R.string.router_settings_password_label)) },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Tampilkan password",
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                enabled = !demoMode && !isTesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_router_pass"),
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gunakan HTTPS (www-ssl)", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    Text("Aktifkan jika port SSL RouterOS aktif", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Switch(
                    checked = useHttps,
                    enabled = !demoMode && !isTesting,
                    onCheckedChange = { checked ->
                        useHttps = checked
                        if (checked && portText == "80") portText = "443"
                        if (!checked && portText == "443") portText = "80"
                        testState = TestConnectionUiState.Idle
                    },
                )
            }

            if (useHttps && !demoMode) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Terima Sertifikat Self-Signed", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                        Text("Direkomendasikan untuk router LAN lokal", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(
                        checked = trustSelfSigned,
                        enabled = !isTesting,
                        onCheckedChange = {
                            trustSelfSigned = it
                            testState = TestConnectionUiState.Idle
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Test Connection Button + Live API Verification Result
            OutlinedButton(
                onClick = {
                    buildConfigOrNull()?.let { cfg ->
                        testState = TestConnectionUiState.Testing
                        onTestConnection(cfg) { result ->
                            testState = result.fold(
                                onSuccess = { TestConnectionUiState.Success(it) },
                                onFailure = { err ->
                                    TestConnectionUiState.Failure(
                                        err.message ?: "Unable to reach MikroTik RouterOS API.",
                                    )
                                },
                            )
                        }
                    }
                },
                enabled = !isTesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("button_test_connection"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, cs.secondary.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.secondary),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = cs.secondary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.router_settings_testing),
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.router_settings_test_connection),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            when (val currentTest = testState) {
                is TestConnectionUiState.Success -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ext.onlineContainer,
                        border = BorderStroke(1.dp, ext.onlineGreen.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_connection_result_success"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = ext.onlineGreen,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Connection Verified (${currentTest.result.latencyMs} ms)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ext.onlineGreen,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${currentTest.result.identity} • ${currentTest.result.boardName} • ${currentTest.result.routerOsVersion}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                    color = cs.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                is TestConnectionUiState.Failure -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ext.offlineContainer,
                        border = BorderStroke(1.dp, ext.offlineRed.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_connection_result_failure"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = ext.offlineRed,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Connection Failed",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ext.offlineRed,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentTest.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurface,
                                )
                            }
                        }
                    }
                }
                else -> Unit
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = cs.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Kredensial disimpan secara lokal menggunakan Jetpack DataStore. Pastikan service 'www' atau 'www-ssl' aktif di /ip/service RouterOS v7+.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!demoMode) {
                    OutlinedButton(
                        onClick = {
                            buildConfigOrNull()?.let { cfg ->
                                onSaveOnly(cfg)
                                onDismiss()
                            }
                        },
                        enabled = !isTesting,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("submit_router_save_only"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, cs.outline),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.router_settings_save_only),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Button(
                    onClick = {
                        buildConfigOrNull()?.let { cfg ->
                            onConnect(cfg)
                            onDismiss()
                        }
                    },
                    enabled = !isTesting,
                    modifier = Modifier
                        .weight(if (demoMode) 1f else 1.5f)
                        .height(52.dp)
                        .testTag("submit_router_connect"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary,
                    ),
                ) {
                    Text(
                        text = if (demoMode) "Aktifkan Mode Demo" else stringResource(R.string.router_settings_save_connect),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
