package dev.ikram.sandbox.feature.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ikram.sandbox.BuildConfig
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.SandboxTopBar
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.model.AutoDelete
import dev.ikram.sandbox.feature.browser.web.LocalWebViewPool
import dev.ikram.sandbox.feature.lock.Authenticator

@Composable
fun SettingsRoute(
    onHowProtectionWorks: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val pool = LocalWebViewPool.current
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_settings_subtitle)
    SettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        // Turning the lock on or off both need the fingerprint or screen lock first.
        onVaultLockChange = { wanted ->
            when {
                !Authenticator.isAvailable(context) -> viewModel.onEvent(SettingsEvent.NoScreenLock)

                activity != null -> Authenticator.authenticate(activity, title, subtitle) {
                    viewModel.onEvent(SettingsEvent.VaultLockConfirmed(wanted))
                }
            }
        },
        onClearBrowsing = { pool.clearBrowsingData { viewModel.onEvent(SettingsEvent.BrowsingDataCleared) } },
        onHowProtectionWorks = onHowProtectionWorks,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onVaultLockChange: (Boolean) -> Unit,
    onClearBrowsing: () -> Unit,
    onHowProtectionWorks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SandboxTopBar(title = stringResource(R.string.settings_title))
            if (!state.loaded) return@Column
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                section(R.string.settings_section_downloads) {
                    SwitchRow(
                        icon = SandboxIcons.Download,
                        title = R.string.settings_ask_before_download,
                        summary = stringResource(R.string.settings_ask_before_download_summary),
                        checked = settings.askBeforeDownload,
                        onCheckedChange = { onEvent(SettingsEvent.AskBeforeDownloadChanged(it)) },
                    )
                    AutoDeleteRow(settings.autoDelete) { onEvent(SettingsEvent.AutoDeleteChanged(it)) }
                }
                section(R.string.settings_section_protection) {
                    SwitchRow(
                        icon = SandboxIcons.ScanCheck,
                        title = R.string.settings_virustotal,
                        summary = stringResource(R.string.settings_virustotal_summary),
                        checked = settings.autoScan,
                        onCheckedChange = { onEvent(SettingsEvent.AutoScanChanged(it)) },
                    )
                    ActionRow(
                        icon = SandboxIcons.Key,
                        title = R.string.settings_virustotal_key,
                        summary = stringResource(
                            if (settings.virusTotalKey == null) {
                                R.string.settings_virustotal_key_missing
                            } else {
                                R.string.settings_virustotal_key_added
                            },
                        ),
                        onClick = { onEvent(SettingsEvent.EditKeyTapped) },
                    )
                    SwitchRow(
                        icon = SandboxIcons.Fingerprint,
                        title = R.string.settings_vault_lock,
                        summary = stringResource(R.string.settings_vault_lock_summary),
                        checked = settings.vaultLock,
                        onCheckedChange = onVaultLockChange,
                    )
                }
                section(R.string.settings_section_privacy) {
                    ActionRow(
                        icon = SandboxIcons.Delete,
                        title = R.string.settings_clear_browsing,
                        summary = stringResource(R.string.settings_clear_browsing_summary),
                        onClick = { onEvent(SettingsEvent.ClearBrowsingTapped) },
                    )
                }
                section(R.string.settings_section_about, divider = false) {
                    ActionRow(
                        icon = SandboxIcons.Vault,
                        title = R.string.settings_how_protection_works,
                        summary = stringResource(R.string.settings_how_protection_works_summary),
                        onClick = onHowProtectionWorks,
                    )
                    InfoRow(SandboxIcons.Info, R.string.settings_version, BuildConfig.VERSION_NAME)
                }
            }
        }
        Messages(state, onEvent, Modifier.align(Alignment.BottomCenter))
    }
    if (state.editingKey) {
        KeyDialog(hasKey = settings.virusTotalKey != null, showError = state.keyError, onEvent = onEvent)
    }
    if (state.confirmClearBrowsing) {
        AlertDialog(
            onDismissRequest = { onEvent(SettingsEvent.ClearBrowsingDismissed) },
            title = { Text(stringResource(R.string.settings_clear_browsing_title)) },
            text = { Text(stringResource(R.string.settings_clear_browsing_body)) },
            confirmButton = { TextButton(onClick = onClearBrowsing) { Text(stringResource(R.string.settings_clear)) } },
            dismissButton = {
                TextButton(onClick = { onEvent(SettingsEvent.ClearBrowsingDismissed) }) {
                    Text(stringResource(R.string.share_cancel))
                }
            },
        )
    }
}

private fun LazyListScope.section(title: Int, divider: Boolean = true, rows: @Composable () -> Unit) {
    item(key = title) {
        Column {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            rows()
            if (divider) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontSize = 15.sp)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: Int,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        icon = icon,
        title = stringResource(title),
        summary = summary,
        modifier = Modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) },
    ) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: Int, summary: String, onClick: () -> Unit) {
    SettingRow(icon, stringResource(title), summary, Modifier.clickable(onClick = onClick)) {
        Icon(SandboxIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: Int, value: String) {
    SettingRow(icon, stringResource(title), value)
}

@Composable
private fun AutoDeleteRow(current: AutoDelete, onSelect: (AutoDelete) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingRow(
        icon = SandboxIcons.Clock,
        title = stringResource(R.string.settings_auto_delete),
        summary = stringResource(R.string.settings_auto_delete_summary),
        modifier = Modifier.clickable { open = true },
    ) {
        Box {
            AssistChip(
                onClick = { open = true },
                label = { Text(stringResource(labelFor(current))) },
                trailingIcon = {
                    Icon(SandboxIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                shape = RoundedCornerShape(18.dp),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                AutoDelete.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(labelFor(option))) },
                        leadingIcon = if (option == current) {
                            { Icon(SandboxIcons.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            open = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

private fun labelFor(autoDelete: AutoDelete): Int = when (autoDelete) {
    AutoDelete.NEVER -> R.string.settings_auto_delete_never
    AutoDelete.AFTER_7_DAYS -> R.string.settings_auto_delete_7
    AutoDelete.AFTER_30_DAYS -> R.string.settings_auto_delete_30
    AutoDelete.AFTER_90_DAYS -> R.string.settings_auto_delete_90
}

@Composable
private fun KeyDialog(hasKey: Boolean, showError: Boolean, onEvent: (SettingsEvent) -> Unit) {
    var key by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onEvent(SettingsEvent.KeyDialogDismissed) },
        title = { Text(stringResource(R.string.settings_key_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_key_body))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.settings_key_label)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.settings_key_error)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(SettingsEvent.KeyEntered(key)) }) {
                Text(stringResource(R.string.settings_key_save))
            }
        },
        dismissButton = {
            Row {
                if (hasKey) {
                    TextButton(onClick = { onEvent(SettingsEvent.KeyRemoved) }) {
                        Text(stringResource(R.string.settings_key_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { onEvent(SettingsEvent.KeyDialogDismissed) }) {
                    Text(stringResource(R.string.share_cancel))
                }
            }
        },
    )
}

@Composable
private fun Messages(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, modifier: Modifier) {
    val host = remember { SnackbarHostState() }
    val text = when (state.message) {
        null -> null
        SettingsMessage.BROWSING_DATA_CLEARED -> stringResource(R.string.settings_message_cleared)
        SettingsMessage.KEY_SAVED -> stringResource(R.string.settings_message_key_saved)
        SettingsMessage.KEY_REMOVED -> stringResource(R.string.settings_message_key_removed)
        SettingsMessage.NO_SCREEN_LOCK -> stringResource(R.string.settings_message_no_screen_lock)
    }
    LaunchedEffect(state.message) {
        if (text != null) {
            host.showSnackbar(text)
            onEvent(SettingsEvent.MessageShown)
        }
    }
    SnackbarHost(host, modifier)
}
