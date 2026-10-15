package dev.ikram.sandbox.feature.vault.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.data.scan.ScanRepository
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.ScanError
import dev.ikram.sandbox.core.model.ScanStatus
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.util.Formatters
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class VirusTotalState(val status: ScanStatus = ScanStatus.NotChecked, val hasKey: Boolean = false)

/** Follows one file's VirusTotal result live, wherever its details sheet is shown. */
@HiltViewModel
class VirusTotalViewModel @Inject constructor(
    repository: VaultRepository,
    settings: SettingsRepository,
    private val scans: ScanRepository,
    private val clock: Clock,
) : ViewModel() {
    private val fileId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<VirusTotalState> = combine(
        fileId.filterNotNull().flatMapLatest(repository::file),
        settings.settings,
    ) { file, current ->
        VirusTotalState(file?.scanStatus ?: ScanStatus.NotChecked, hasKey = current.virusTotalKey != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), VirusTotalState())

    fun show(id: String) {
        fileId.value = id
    }

    fun check() {
        fileId.value?.let(scans::requestCheck)
    }

    fun now(): Instant = clock.instant()

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}

/** "Check with VirusTotal": the latest answer for this file's hash, and a tap to ask again. */
@Composable
fun VirusTotalRow(file: VaultFile, viewModel: VirusTotalViewModel = hiltViewModel(key = "virustotal:${file.id}")) {
    LaunchedEffect(file.id) { viewModel.show(file.id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status = state.status
    val semantic = SandboxTheme.semanticColors
    val colors = MaterialTheme.colorScheme
    val canCheck = state.hasKey && status != ScanStatus.Checking
    val flaggedColor = if (file.category == FileCategory.BLOCKED) semantic.blocked else semantic.caution
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHigh)
            .clickable(enabled = canCheck, onClick = viewModel::check)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (status is ScanStatus.Flagged) SandboxIcons.Warning else SandboxIcons.ScanCheck,
            contentDescription = null,
            tint = if (status is ScanStatus.Flagged) flaggedColor else colors.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.virustotal_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = summary(status, state.hasKey, viewModel.now()),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (status == ScanStatus.Checking) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            ResultBadge(status, file.category == FileCategory.BLOCKED)
        }
    }
}

@Composable
private fun ResultBadge(status: ScanStatus, blocked: Boolean) {
    when (status) {
        is ScanStatus.Clean ->
            StatusBadge(stringResource(R.string.virustotal_badge_clean), SandboxIcons.Check, BadgeTone.SAFE)

        is ScanStatus.Flagged -> StatusBadge(
            stringResource(R.string.virustotal_badge_flagged),
            SandboxIcons.Warning,
            if (blocked) BadgeTone.BLOCKED else BadgeTone.CAUTION,
        )

        is ScanStatus.NotFound ->
            StatusBadge(stringResource(R.string.virustotal_badge_unknown), SandboxIcons.NotChecked, BadgeTone.NEUTRAL)

        else -> StatusBadge(
            stringResource(R.string.virustotal_badge_not_checked),
            SandboxIcons.NotChecked,
            BadgeTone.NEUTRAL,
        )
    }
}

@Composable
private fun summary(status: ScanStatus, hasKey: Boolean, now: Instant): String = when (status) {
    ScanStatus.Checking -> stringResource(R.string.virustotal_checking)

    is ScanStatus.Clean ->
        pluralStringResource(
            R.plurals.virustotal_clean,
            status.engines,
            checkedAgo(status.checkedAt, now),
            status.engines,
        )

    is ScanStatus.Flagged ->
        pluralStringResource(
            R.plurals.virustotal_flagged,
            status.engines,
            checkedAgo(status.checkedAt, now),
            status.count,
            status.engines,
        )

    is ScanStatus.NotFound -> stringResource(R.string.virustotal_not_found, checkedAgo(status.checkedAt, now))

    is ScanStatus.Error -> stringResource(
        when {
            !hasKey -> R.string.virustotal_no_key
            status.reason == ScanError.BAD_KEY -> R.string.virustotal_bad_key
            status.reason == ScanError.RATE_LIMITED -> R.string.virustotal_rate_limited
            else -> R.string.virustotal_network
        },
    )

    ScanStatus.NotChecked -> stringResource(if (hasKey) R.string.virustotal_not_checked else R.string.virustotal_no_key)
}

/** "just now", "5 min ago", "3 h ago", then the date. */
@Composable
private fun checkedAgo(checkedAt: Instant, now: Instant): String {
    val elapsed = Duration.between(checkedAt, now)
    return when {
        elapsed.toMinutes() < 1 -> stringResource(R.string.virustotal_just_now)
        elapsed.toHours() < 1 -> stringResource(R.string.virustotal_minutes_ago, elapsed.toMinutes())
        elapsed.toDays() < 1 -> stringResource(R.string.virustotal_hours_ago, elapsed.toHours())
        else -> Formatters.day(checkedAt.atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())
    }
}
