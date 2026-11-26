package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.HotspotUser
import com.example.ui.theme.JetBrainsMono
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLEncoder
import kotlinx.coroutines.delay

private enum class QrPayloadMode {
    LOGIN_URL,
    CREDENTIALS,
}

private fun buildHotspotLoginUrl(host: String, username: String, password: String): String {
    val cleanHost = host.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .trimEnd('/')
        .ifBlank { "192.168.88.1" }
    val encUser = URLEncoder.encode(username, "UTF-8")
    val encPass = URLEncoder.encode(password, "UTF-8")
    return "http://$cleanHost/login?username=$encUser&password=$encPass"
}

private fun buildCredentialsPayload(hotspotName: String, user: HotspotUser): String = buildString {
    append("Hotspot: ").append(hotspotName).append('\n')
    append("Username: ").append(user.name).append('\n')
    append("Password: ").append(user.password.ifBlank { "(none)" }).append('\n')
    append("Profile: ").append(user.profile)
    if (user.comment.isNotBlank()) {
        append('\n').append("Note: ").append(user.comment)
    }
}

private fun encodeQrBitMatrix(content: String): BitMatrix? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1,
    )
    QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
}.getOrNull()

@Composable
fun UserQrCodeDialog(
    user: HotspotUser,
    defaultPortalHost: String,
    hotspotName: String,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var mode by remember { mutableStateOf(QrPayloadMode.LOGIN_URL) }
    var portalHost by remember(defaultPortalHost) {
        mutableStateOf(defaultPortalHost.ifBlank { "192.168.88.1" })
    }
    var revealPassword by remember { mutableStateOf(false) }
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(1800L)
            copiedFeedback = false
        }
    }

    val payload = remember(mode, portalHost, user, hotspotName) {
        when (mode) {
            QrPayloadMode.LOGIN_URL -> buildHotspotLoginUrl(portalHost, user.name, user.password)
            QrPayloadMode.CREDENTIALS -> buildCredentialsPayload(hotspotName, user)
        }
    }

    val bitMatrix = remember(payload) { encodeQrBitMatrix(payload) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 460.dp)
                .testTag("user_qr_code_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainer,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.65f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(cs.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = null,
                                tint = cs.onPrimaryContainer,
                                modifier = Modifier.size(23.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.qr_dialog_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = cs.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.qr_dialog_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("qr_dialog_close"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = cs.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Switcher Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QrModeChip(
                        label = stringResource(R.string.qr_mode_login_url),
                        selected = mode == QrPayloadMode.LOGIN_URL,
                        onClick = { mode = QrPayloadMode.LOGIN_URL },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qr_mode_login_url"),
                    )
                    QrModeChip(
                        label = stringResource(R.string.qr_mode_credentials),
                        selected = mode == QrPayloadMode.CREDENTIALS,
                        onClick = { mode = QrPayloadMode.CREDENTIALS },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qr_mode_credentials"),
                    )
                }

                if (mode == QrPayloadMode.LOGIN_URL) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = portalHost,
                        onValueChange = { portalHost = it },
                        label = { Text(stringResource(R.string.qr_portal_host_label)) },
                        placeholder = { Text(stringResource(R.string.qr_portal_host_placeholder)) },
                        leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qr_portal_host_input"),
                        shape = RoundedCornerShape(14.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // High-contrast white quiet-zone QR scan pad (essential for camera scanning in dark mode)
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = BorderStroke(2.dp, cs.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(236.dp)
                        .testTag("user_qr_code_canvas"),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bitMatrix != null) {
                            QrMatrixCanvas(
                                matrix = bitMatrix,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .aspectRatio(1f),
                            )
                        } else {
                            Text(
                                text = "Unable to generate QR code",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1A1A1A),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // User Credentials Summary Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Username",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                            )
                            Text(
                                text = user.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = cs.onSurface,
                            )
                        }

                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Password",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when {
                                        user.password.isBlank() -> "(empty)"
                                        revealPassword -> user.password
                                        else -> "•".repeat(user.password.length.coerceIn(6, 12))
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = cs.onSurface,
                                )
                                if (user.password.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { revealPassword = !revealPassword },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("qr_toggle_password"),
                                    ) {
                                        Icon(
                                            imageVector = if (revealPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = stringResource(R.string.cd_toggle_password),
                                            tint = cs.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Profile",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cs.secondaryContainer,
                            ) {
                                Text(
                                    text = user.profile,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = cs.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }

                        if (user.comment.isNotBlank()) {
                            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Comment",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cs.onSurfaceVariant,
                                )
                                Text(
                                    text = user.comment,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (mode == QrPayloadMode.LOGIN_URL) {
                        "Connect to $hotspotName Wi-Fi, then scan this QR code to sign in automatically."
                    } else {
                        "Contains hotspot network name, username, password, and profile details."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Copy & Share Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(payload))
                            copiedFeedback = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("qr_copy_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, cs.outline),
                    ) {
                        Icon(
                            imageVector = if (copiedFeedback) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (copiedFeedback) stringResource(R.string.qr_action_copied) else stringResource(R.string.qr_action_copy),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    Button(
                        onClick = {
                            val shareText = buildString {
                                append("Erlkim NET Hotspot Access\n")
                                append("Hotspot: ").append(hotspotName).append('\n')
                                append("Username: ").append(user.name).append('\n')
                                if (user.password.isNotBlank()) {
                                    append("Password: ").append(user.password).append('\n')
                                }
                                append("Profile: ").append(user.profile).append('\n')
                                append("Quick Login: ").append(buildHotspotLoginUrl(portalHost, user.name, user.password))
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Hotspot Login - ${user.name}")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Hotspot Credentials"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("qr_share_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.primary,
                            contentColor = cs.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.qr_action_share),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QrModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.dp, cs.primary.copy(alpha = 0.6f)) else BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QrMatrixCanvas(
    matrix: BitMatrix,
    modifier: Modifier = Modifier,
) {
    val moduleColor = Color(0xFF111113)
    Canvas(modifier = modifier) {
        val cols = matrix.width
        val rows = matrix.height
        if (cols <= 0 || rows <= 0) return@Canvas
        val cellW = size.width / cols.toFloat()
        val cellH = size.height / rows.toFloat()
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (matrix.get(x, y)) {
                    drawRect(
                        color = moduleColor,
                        topLeft = Offset(x * cellW, y * cellH),
                        size = Size(cellW + 0.5f, cellH + 0.5f),
                    )
                }
            }
        }
    }
}
