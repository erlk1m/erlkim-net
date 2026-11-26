package com.erlkim.mikrotik.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erlkim.mikrotik.R
import com.erlkim.mikrotik.data.model.RouterConfig
import com.erlkim.mikrotik.data.network.ConnectionTestResult
import com.erlkim.mikrotik.data.network.LocalNetworkStatus
import com.erlkim.mikrotik.data.network.RouterNetworkHelper
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import kotlinx.coroutines.delay

private val HOST_REGEX = Regex("^[a-zA-Z0-9._:\\[\\]-]+$")

private const val MIKROTIK_QUICK_SETUP_SCRIPT = """/ip service enable www
/user group add name=erlkim-api policy=rest-api,read,write,policy,test,reboot,sensitive
/user add name=erlkimadmin password="PasswordKuat123!" group=erlkim-api"""

private sealed interface TestConnectionUiState {
    data object Idle : TestConnectionUiState
    data object Testing : TestConnectionUiState
    data class Success(val result: ConnectionTestResult) : TestConnectionUiState
    data class Failure(val message: String) : TestConnectionUiState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RouterConnectionSheet(
    currentConfig: RouterConfig,
    onDismiss: () -> Unit,
    onTestConnection: (RouterConfig, (Result<ConnectionTestResult>) -> Unit) -> Unit,
    onSaveOnly: (RouterConfig) -> Unit,
    onConnect: (RouterConfig) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
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
    var showSetupGuide by remember { mutableStateOf(false) }
    var copiedScript by remember { mutableStateOf(false) }
    var networkStatus by remember {
        mutableStateOf(
            runCatching { RouterNetworkHelper.inspectActiveNetwork(context) }.getOrNull()
                ?: LocalNetworkStatus(
                    transportLabel = "LAN / Wi-Fi",
                    isLocalLanReady = true,
                    isCellularOnly = false,
                    isVpnActive = false,
                    detectedGatewayIp = null,
                    deviceIp = null,
                ),
        )
    }

    LaunchedEffect(copiedScript) {
        if (copiedScript) {
            delay(1800L)
            copiedScript = false
        }
    }

    val isTesting = testState is TestConnectionUiState.Testing
    val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").substringBefore("/").trim()
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
                        host = networkStatus.detectedGatewayIp ?: "192.168.88.1"
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

            if (!demoMode) {
                Spacer(modifier = Modifier.height(10.dp))

                // Active Network & Detected MikroTik Gateway Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (networkStatus.isCellularOnly) ext.demoContainer else cs.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (networkStatus.isCellularOnly) ext.demoAmber.copy(alpha = 0.5f) else cs.outlineVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = if (networkStatus.isCellularOnly) Icons.Rounded.WarningAmber else Icons.Rounded.Wifi,
                                    contentDescription = null,
                                    tint = if (networkStatus.isCellularOnly) ext.demoAmber else ext.onlineGreen,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = buildString {
                                            append("Jaringan: ${networkStatus.transportLabel}")
                                            networkStatus.deviceIp?.let { append(" • IP $it") }
                                        },
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = JetBrainsMono,
                                        ),
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = when {
                                            networkStatus.isCellularOnly ->
                                                "HP memakai Data Seluler. Hubungkan ke Wi-Fi MikroTik untuk IP LAN."
                                            !networkStatus.detectedGatewayIp.isNullOrBlank() ->
                                                "Gateway terdeteksi: ${networkStatus.detectedGatewayIp}"
                                            else ->
                                                "Pastikan perangkat berada di jaringan yang sama dengan router."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    runCatching { RouterNetworkHelper.inspectActiveNetwork(context) }
                                        .onSuccess { networkStatus = it }
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Deteksi ulang gateway jaringan",
                                    tint = cs.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // Quick Host Presets (Detected Gateway + Common MikroTik IPs)
                        val detectedGw = networkStatus.detectedGatewayIp
                        val quickTargets = buildList {
                            if (!detectedGw.isNullOrBlank()) add(detectedGw to "Gateway ($detectedGw)")
                            if (detectedGw != "192.168.88.1") add("192.168.88.1" to "192.168.88.1")
                            if (detectedGw != "10.10.10.1") add("10.10.10.1" to "10.10.10.1")
                        }
                        if (quickTargets.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                quickTargets.forEach { (ip, label) ->
                                    val isCurrent = cleanHost == ip
                                    AssistChip(
                                        onClick = {
                                            host = ip
                                            testState = TestConnectionUiState.Idle
                                        },
                                        enabled = !isTesting,
                                        label = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (isCurrent) cs.primaryContainer else cs.surfaceContainer,
                                            labelColor = if (isCurrent) cs.onPrimaryContainer else cs.onSurface,
                                        ),
                                        modifier = Modifier.testTag("preset_host_${ip.replace('.', '_')}"),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { rawInput ->
                        val trimmed = rawInput.trim()
                        if (trimmed.startsWith("https://", ignoreCase = true)) {
                            useHttps = true
                            if (portText == "80") portText = "443"
                        } else if (trimmed.startsWith("http://", ignoreCase = true)) {
                            useHttps = false
                            if (portText == "443") portText = "80"
                        }
                        val stripped = trimmed
                            .removePrefix("http://")
                            .removePrefix("https://")
                            .substringBefore("/")
                        if (!stripped.startsWith("[") && stripped.count { it == ':' } == 1) {
                            val idx = stripped.lastIndexOf(':')
                            val maybePort = stripped.substring(idx + 1).toIntOrNull()
                            if (maybePort != null && maybePort in 1..65535) {
                                host = stripped.substring(0, idx)
                                portText = maybePort.toString()
                            } else {
                                host = rawInput
                            }
                        } else {
                            host = rawInput
                        }
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

            if (!demoMode) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showSetupGuide = !showSetupGuide },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
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
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    tint = cs.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Panduan Setup MikroTik (RouterOS v7+)",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = cs.onSurface,
                                )
                            }
                            Icon(
                                imageVector = if (showSetupGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                            )
                        }
                        AnimatedVisibility(visible = showSetupGuide) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    text = "Jalankan di WinBox → New Terminal agar REST API & akun siap dipakai:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = cs.surfaceContainerLowest,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = MIKROTIK_QUICK_SETUP_SCRIPT,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMono,
                                            lineHeight = 16.sp,
                                        ),
                                        color = cs.onSurface,
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(MIKROTIK_QUICK_SETUP_SCRIPT))
                                        copiedScript = true
                                    },
                                ) {
                                    Icon(
                                        imageVector = if (copiedScript) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (copiedScript) "Script disalin!" else "Salin Script Terminal")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

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
