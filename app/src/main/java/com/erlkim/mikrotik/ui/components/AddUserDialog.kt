package com.erlkim.mikrotik.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.erlkim.mikrotik.R
import com.erlkim.mikrotik.data.model.HotspotUserProfile
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import java.security.SecureRandom

private const val PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"

private fun generateHotspotPassword(length: Int = 8): String {
    val random = SecureRandom()
    return buildString(length) {
        repeat(length) {
            append(PASSWORD_ALPHABET[random.nextInt(PASSWORD_ALPHABET.length)])
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUserDialog(
    profiles: List<HotspotUserProfile>,
    isLoadingProfiles: Boolean,
    profilesError: String?,
    existingUsernames: List<String>,
    routerTargetLabel: String,
    isDemoMode: Boolean,
    onRefreshProfiles: () -> Unit,
    onOpenRouterSettings: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (
        username: String,
        password: String,
        profile: String,
        quotaMb: Long,
        comment: String,
        onComplete: (Result<Unit>) -> Unit,
    ) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current

    // Pull the latest available hotspot profiles directly from the MikroTik router API on open
    LaunchedEffect(Unit) {
        onRefreshProfiles()
    }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var selectedProfileName by remember {
        mutableStateOf(
            profiles.firstOrNull { it.name.equals("default", ignoreCase = true) }?.name
                ?: profiles.firstOrNull()?.name.orEmpty(),
        )
    }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }
    var quotaMbText by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var apiError by remember { mutableStateOf<String?>(null) }

    // Keep selected profile synced when fresh profiles arrive from the MikroTik router API
    LaunchedEffect(profiles) {
        if (profiles.isNotEmpty() && profiles.none { it.name == selectedProfileName }) {
            selectedProfileName = profiles.firstOrNull { it.name.equals("default", ignoreCase = true) }?.name
                ?: profiles.first().name
        }
    }

    val trimmedUsername = username.trim()
    val usernameHasSpace = trimmedUsername.any { it.isWhitespace() }
    val isDuplicateUsername = remember(trimmedUsername, existingUsernames) {
        trimmedUsername.isNotEmpty() && existingUsernames.any { it.equals(trimmedUsername, ignoreCase = true) }
    }
    val usernameErrorText = when {
        trimmedUsername.isBlank() -> "Username is required"
        usernameHasSpace -> "Username cannot contain spaces"
        isDuplicateUsername -> "Username '$trimmedUsername' already exists"
        else -> null
    }
    val passwordErrorText = when {
        password.isBlank() -> "Password is required"
        else -> null
    }
    val profileErrorText = when {
        selectedProfileName.isBlank() -> "Select a hotspot profile from the router"
        else -> null
    }
    val selectedProfileObj = remember(selectedProfileName, profiles) {
        profiles.firstOrNull { it.name == selectedProfileName }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isSubmitting,
            dismissOnClickOutside = !isSubmitting,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .testTag("add_user_dialog"),
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
                                imageVector = Icons.Rounded.PersonAdd,
                                contentDescription = null,
                                tint = cs.onPrimaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.add_user_dialog_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = cs.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.add_user_dialog_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = { if (!isSubmitting) onDismiss() },
                        enabled = !isSubmitting,
                        modifier = Modifier.testTag("add_user_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = cs.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Router API target pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Router,
                            contentDescription = null,
                            tint = if (isDemoMode) ext.demoAmber else ext.onlineGreen,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = routerTargetLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (!apiError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ext.offlineContainer,
                        border = BorderStroke(1.dp, ext.offlineRed.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_user_error_banner"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = ext.offlineRed,
                                    modifier = Modifier
                                        .padding(top = 1.dp)
                                        .size(18.dp),
                                )
                                Text(
                                    text = apiError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.offlineRed,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (!isDemoMode) {
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenRouterSettings()
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text(
                                        text = stringResource(R.string.router_settings_title),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = ext.offlineRed,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Username
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        apiError = null
                    },
                    label = { Text(stringResource(R.string.add_user_username_label)) },
                    placeholder = { Text(stringResource(R.string.add_user_username_placeholder)) },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    isError = attemptedSubmit && usernameErrorText != null,
                    supportingText = if (attemptedSubmit && usernameErrorText != null) {
                        { Text(usernameErrorText) }
                    } else {
                        null
                    },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_user_username"),
                    shape = RoundedCornerShape(14.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Password with visibility toggle & random generator
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        apiError = null
                    },
                    label = { Text(stringResource(R.string.add_user_password_label)) },
                    placeholder = { Text(stringResource(R.string.add_user_password_placeholder)) },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    password = generateHotspotPassword(8)
                                    showPassword = true
                                    apiError = null
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier.testTag("add_user_generate_password"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Casino,
                                    contentDescription = stringResource(R.string.cd_generate_password),
                                    tint = cs.primary,
                                )
                            }
                            IconButton(
                                onClick = { showPassword = !showPassword },
                                enabled = !isSubmitting,
                                modifier = Modifier.testTag("add_user_toggle_password"),
                            ) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = stringResource(R.string.cd_toggle_password),
                                )
                            }
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = attemptedSubmit && passwordErrorText != null,
                    supportingText = if (attemptedSubmit && passwordErrorText != null) {
                        { Text(passwordErrorText) }
                    } else {
                        null
                    },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_user_password"),
                    shape = RoundedCornerShape(14.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Live MikroTik Hotspot Profile Selector (M3 ExposedDropdownMenuBox)
                val profileFieldDisplayValue = when {
                    selectedProfileName.isNotBlank() -> selectedProfileName
                    isLoadingProfiles -> stringResource(R.string.add_user_profile_loading)
                    else -> ""
                }
                val canOpenProfileMenu = !isSubmitting && profiles.isNotEmpty()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExposedDropdownMenuBox(
                        expanded = profileMenuExpanded && canOpenProfileMenu,
                        onExpandedChange = {
                            if (canOpenProfileMenu) {
                                profileMenuExpanded = !profileMenuExpanded
                            } else if (!isSubmitting && !isLoadingProfiles && profiles.isEmpty()) {
                                onRefreshProfiles()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = profileFieldDisplayValue,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isSubmitting,
                            label = { Text(stringResource(R.string.add_user_profile_label)) },
                            placeholder = {
                                Text(
                                    text = if (isLoadingProfiles) {
                                        stringResource(R.string.add_user_profile_loading)
                                    } else {
                                        stringResource(R.string.add_user_profile_empty)
                                    },
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                            trailingIcon = {
                                if (isLoadingProfiles) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = cs.primary,
                                    )
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileMenuExpanded && canOpenProfileMenu)
                                }
                            },
                            isError = attemptedSubmit && profileErrorText != null,
                            supportingText = if (attemptedSubmit && profileErrorText != null) {
                                { Text(profileErrorText) }
                            } else {
                                null
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !isSubmitting)
                                .fillMaxWidth()
                                .testTag("add_user_profile"),
                        )
                        ExposedDropdownMenu(
                            expanded = profileMenuExpanded && canOpenProfileMenu,
                            onDismissRequest = { profileMenuExpanded = false },
                            containerColor = cs.surfaceContainerHigh,
                        ) {
                            profiles.forEach { prof ->
                                val isSelected = prof.name == selectedProfileName
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = prof.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                ),
                                                color = if (isSelected) cs.primary else cs.onSurface,
                                            )
                                            Text(
                                                text = "Rate: ${prof.rateLimit}  •  Shared: ${prof.sharedUsers}  •  Timeout: ${prof.sessionTimeout}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                                color = cs.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = cs.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        selectedProfileName = prof.name
                                        profileMenuExpanded = false
                                    },
                                    modifier = Modifier.testTag("add_user_profile_option_${prof.name}"),
                                )
                            }
                        }
                    }

                    // Direct router API refresh button for profiles
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cs.surfaceContainerHigh,
                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.65f)),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        IconButton(
                            onClick = onRefreshProfiles,
                            enabled = !isSubmitting && !isLoadingProfiles,
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("add_user_refresh_profiles"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh_profiles),
                                tint = if (isLoadingProfiles) cs.onSurfaceVariant else cs.primary,
                            )
                        }
                    }
                }

                // Live Profile metadata or Router API error banner
                if (!profilesError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ext.offlineContainer,
                        border = BorderStroke(1.dp, ext.offlineRed.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_user_profiles_error"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = ext.offlineRed,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = profilesError,
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.offlineRed,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = onRefreshProfiles,
                                enabled = !isLoadingProfiles,
                            ) {
                                Text("Retry", color = ext.offlineRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (selectedProfileObj != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${profiles.size} router profile${if (profiles.size == 1) "" else "s"}  •  Rate: ${selectedProfileObj.rateLimit}  •  Shared: ${selectedProfileObj.sharedUsers}  •  Timeout: ${selectedProfileObj.sessionTimeout}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Comment
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.add_user_comment_label)) },
                    placeholder = { Text(stringResource(R.string.add_user_comment_placeholder)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null) },
                    minLines = 1,
                    maxLines = 2,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_user_comment"),
                    shape = RoundedCornerShape(14.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Optional Quota Limit
                OutlinedTextField(
                    value = quotaMbText,
                    onValueChange = { quotaMbText = it.filter(Char::isDigit).take(7) },
                    label = { Text(stringResource(R.string.add_user_quota_label)) },
                    placeholder = { Text("0 (Unlimited)") },
                    leadingIcon = { Icon(Icons.Rounded.DataUsage, contentDescription = null) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_user_quota"),
                    shape = RoundedCornerShape(14.dp),
                )

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.55f))
                Spacer(modifier = Modifier.height(16.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("cancel_add_user"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, cs.outline),
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    Button(
                        onClick = {
                            attemptedSubmit = true
                            apiError = null
                            if (usernameErrorText != null || passwordErrorText != null || profileErrorText != null) return@Button
                            isSubmitting = true
                            onCreate(
                                trimmedUsername,
                                password,
                                selectedProfileName,
                                quotaMbText.toLongOrNull() ?: 0L,
                                comment.trim(),
                            ) { result ->
                                isSubmitting = false
                                result.onSuccess {
                                    onDismiss()
                                }.onFailure { err ->
                                    apiError = err.message ?: "Failed to create hotspot user on MikroTik router."
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .testTag("submit_add_user"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.primary,
                            contentColor = cs.onPrimary,
                        ),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = cs.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.add_user_submitting),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.add_user_submit),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
