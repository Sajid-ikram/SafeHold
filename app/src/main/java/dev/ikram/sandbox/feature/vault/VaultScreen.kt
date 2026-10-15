package dev.ikram.sandbox.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.feature.share.ShareSheet
import dev.ikram.sandbox.feature.vault.components.DownloadCard
import dev.ikram.sandbox.feature.vault.components.DownloadRow
import dev.ikram.sandbox.feature.vault.components.VaultEmpty
import dev.ikram.sandbox.feature.vault.components.VaultItemCard
import dev.ikram.sandbox.feature.vault.components.VaultItemRow
import dev.ikram.sandbox.feature.vault.components.VaultNoMatch
import dev.ikram.sandbox.util.Formatters
import java.time.LocalDate

@Composable
fun VaultRoute(
    onOpenFile: (fileId: String) -> Unit,
    onStartBrowsing: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(VaultEvent.VisibilityChanged(true))
        onPauseOrDispose { viewModel.onEvent(VaultEvent.VisibilityChanged(false)) }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is VaultEffect.OpenViewer -> onOpenFile(effect.fileId)
            }
        }
    }
    VaultScreen(state = state, onEvent = viewModel::onEvent, onStartBrowsing = onStartBrowsing, modifier = modifier)
}

@Composable
fun VaultScreen(
    state: VaultUiState,
    onEvent: (VaultEvent) -> Unit,
    onStartBrowsing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.text()
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onEvent(VaultEvent.MessageShown)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        VaultTopBar(layout = state.layout, showLayoutToggle = !state.isEmpty, onLayout = {
            onEvent(VaultEvent.LayoutSelected(it))
        })
        SearchField(
            query = state.query,
            enabled = !state.isEmpty,
            onQuery = { onEvent(VaultEvent.QueryChanged(it)) },
        )
        if (!state.isEmpty && !state.isLoading) {
            FilterChips(selected = state.filter, onSelect = { onEvent(VaultEvent.FilterSelected(it)) })
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> Unit
                state.isEmpty -> VaultEmpty(onStartBrowsing = onStartBrowsing)
                state.layout == VaultLayout.LIST -> VaultList(state, onEvent)
                else -> VaultGrid(state, onEvent)
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }

    state.details?.let { file ->
        FileDetailsSheet(
            file = file,
            onOpen = { onEvent(VaultEvent.OpenTapped(file.id)) },
            onShare = { onEvent(VaultEvent.ShareTapped(file.id)) },
            onDelete = { onEvent(VaultEvent.DeleteTapped(file.id)) },
            onHashCopied = { onEvent(VaultEvent.HashCopied) },
            onDismiss = { onEvent(VaultEvent.DetailsDismissed) },
        )
    }
    state.shareFileId?.let { fileId ->
        ShareSheet(fileId = fileId, onDismiss = { onEvent(VaultEvent.ShareDismissed) })
    }
    state.mismatch?.let { file ->
        MismatchWarningDialog(
            file = file,
            onAccept = { onEvent(VaultEvent.MismatchAccepted) },
            onDelete = { onEvent(VaultEvent.MismatchDelete) },
            onDismiss = { onEvent(VaultEvent.MismatchDismissed) },
        )
    }
    state.confirmDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { onEvent(VaultEvent.DeleteDismissed) },
            title = { Text(stringResource(R.string.delete_title, file.displayName)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(VaultEvent.DeleteConfirmed) }) {
                    Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onEvent(VaultEvent.DeleteDismissed)
                }) { Text(stringResource(R.string.delete_cancel)) }
            },
        )
    }
}

@Composable
private fun VaultTopBar(layout: VaultLayout, showLayoutToggle: Boolean, onLayout: (VaultLayout) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.vault_title), style = MaterialTheme.typography.titleLarge)
        Icon(
            imageVector = SandboxIcons.Lock,
            contentDescription = stringResource(R.string.vault_locked),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Box(Modifier.weight(1f))
        if (showLayoutToggle) LayoutToggle(layout, onLayout)
    }
}

/** The design's two-segment list/grid switch. */
@Composable
private fun LayoutToggle(layout: VaultLayout, onLayout: (VaultLayout) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Segment(SandboxIcons.ListView, R.string.vault_list_view, layout == VaultLayout.LIST) {
            onLayout(VaultLayout.LIST)
        }
        Segment(SandboxIcons.GridView, R.string.vault_grid_view, layout == VaultLayout.GRID) {
            onLayout(VaultLayout.GRID)
        }
    }
}

@Composable
private fun Segment(icon: ImageVector, label: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(label),
            tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SearchField(query: String, enabled: Boolean, onQuery: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(colors.surfaceContainerHigh, RoundedCornerShape(26.dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SandboxIcons.Search, contentDescription = null, tint = colors.onSurfaceVariant)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.vault_search_hint),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = colors.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }) {
                Icon(SandboxIcons.Close, stringResource(R.string.vault_search_clear), Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FilterChips(selected: VaultFilter, onSelect: (VaultFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(VaultFilter.entries) { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.label())) },
                leadingIcon = if (isSelected) {
                    { Icon(SandboxIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                } else {
                    null
                },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun VaultList(state: VaultUiState, onEvent: (VaultEvent) -> Unit) {
    val today = state.sections.firstOrNull { it.label == DayLabel.Today }
    val others = state.sections.filterNot { it.label == DayLabel.Today }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (state.downloads.isNotEmpty() || today != null) {
            sectionHeader(DayLabel.Today, state.today)
            items(state.downloads, key = { "download-${it.downloadId}" }) { download ->
                DownloadRow(download, onCancel = { onEvent(VaultEvent.CancelDownload(download.downloadId)) })
            }
            today?.let { fileRows(it, state.thumbnails, onEvent) }
        }
        others.forEach { section ->
            sectionHeader(section.label, state.today)
            fileRows(section, state.thumbnails, onEvent)
        }
        if (state.sections.isEmpty() && state.downloads.isEmpty()) {
            item { VaultNoMatch() }
        }
    }
}

private fun LazyListScope.sectionHeader(label: DayLabel, today: LocalDate) {
    item(key = "header-$label") {
        Text(
            text = label.text(today),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, letterSpacing = 0.2.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        )
    }
}

private fun LazyListScope.fileRows(
    section: VaultSection,
    thumbnails: Map<String, ImageBitmap>,
    onEvent: (VaultEvent) -> Unit,
) {
    items(section.files, key = { it.id }) { file ->
        VaultItemRow(file, thumbnails[file.id], onClick = { onEvent(VaultEvent.FileTapped(file.id)) })
    }
}

@Composable
private fun VaultGrid(state: VaultUiState, onEvent: (VaultEvent) -> Unit) {
    val files = state.sections.flatMap { section -> section.files.map { it to section.label } }
    if (files.isEmpty() && state.downloads.isEmpty()) {
        VaultNoMatch()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.downloads, key = { "download-${it.downloadId}" }) { DownloadCard(it) }
        items(files, key = { it.first.id }) { (file, label) ->
            VaultItemCard(
                file = file,
                thumbnail = state.thumbnails[file.id],
                dayText = label.text(state.today),
                onClick = { onEvent(VaultEvent.FileTapped(file.id)) },
            )
        }
    }
}

@Composable
private fun DayLabel.text(today: LocalDate): String = when (this) {
    DayLabel.Today -> stringResource(R.string.vault_section_today)
    DayLabel.Yesterday -> stringResource(R.string.vault_section_yesterday)
    is DayLabel.Day -> Formatters.day(date, today)
}

private fun VaultFilter.label(): Int = when (this) {
    VaultFilter.ALL -> R.string.vault_filter_all
    VaultFilter.IMAGES -> R.string.vault_filter_images
    VaultFilter.VIDEOS -> R.string.vault_filter_videos
    VaultFilter.AUDIO -> R.string.vault_filter_audio
    VaultFilter.PDFS -> R.string.vault_filter_pdfs
    VaultFilter.TEXT -> R.string.vault_filter_text
    VaultFilter.ARCHIVES -> R.string.vault_filter_archives
    VaultFilter.BLOCKED -> R.string.vault_filter_blocked
}

@Composable
private fun VaultMessage.text(): String = when (this) {
    VaultMessage.HashCopied -> stringResource(R.string.vault_message_hash_copied)
    is VaultMessage.Deleted -> stringResource(R.string.vault_message_deleted, name)
}

private const val DISABLED_ALPHA = 0.6f

@Preview(showBackground = true)
@Composable
private fun VaultEmptyPreview() {
    SandboxTheme {
        VaultScreen(
            state = VaultUiState(today = LocalDate.now(), isLoading = false, isEmpty = true),
            onEvent = {},
            onStartBrowsing = {},
        )
    }
}
