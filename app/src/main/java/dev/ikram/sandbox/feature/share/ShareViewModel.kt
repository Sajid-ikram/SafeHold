package dev.ikram.sandbox.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.ShareMode
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.share.copy.CopyFailedException
import dev.ikram.sandbox.feature.share.copy.CopyFailure
import dev.ikram.sandbox.feature.share.copy.CopyProgress
import dev.ikram.sandbox.feature.share.copy.PreparedShare
import dev.ikram.sandbox.feature.share.copy.ShareCopier
import java.time.Clock
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a file can never leave SafeHold. */
enum class ShareBlockReason { APP_INSTALLER, PROGRAM, UNKNOWN, OPEN_FAILED }

sealed interface ShareTarget {
    data class File(val share: PreparedShare) : ShareTarget

    data class Link(val url: String) : ShareTarget
}

sealed interface ShareStage {
    data object Loading : ShareStage

    data class Blocked(val reason: ShareBlockReason) : ShareStage

    data object Choosing : ShareStage

    data class Preparing(val mode: ShareMode, val fraction: Float?, val secondsLeft: Int?) : ShareStage

    /** Ready to hand to the system share sheet. */
    data class Ready(val target: ShareTarget) : ShareStage

    data class Failed(val reason: CopyFailure) : ShareStage

    /** Handed over (or the file is gone): the sheet closes. */
    data object Done : ShareStage
}

data class ShareUiState(
    val file: VaultFile? = null,
    val available: Set<ShareMode> = emptySet(),
    val selected: ShareMode = ShareMode.SAFE_COPY,
    val stage: ShareStage = ShareStage.Loading,
    /** The amber "share the original?" question is showing. */
    val confirmOriginal: Boolean = false,
)

sealed interface ShareEvent {
    data class Opened(val fileId: String) : ShareEvent

    data class Selected(val mode: ShareMode) : ShareEvent

    /** The sheet's main button. */
    data object ShareTapped : ShareEvent

    data object OriginalConfirmed : ShareEvent

    data object OriginalDismissed : ShareEvent

    data object CancelTapped : ShareEvent

    data object TryAnotherWay : ShareEvent

    data object Launched : ShareEvent

    data object Dismissed : ShareEvent
}

/**
 * The share flow for one file: safe copy by default, the original only after an amber
 * warning, or just the link. Blocked and unknown files, and files that crashed the
 * renderer, never leave at all.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val copier: ShareCopier,
    private val clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = state.asStateFlow()

    private var work: Job? = null

    fun onEvent(event: ShareEvent) {
        when (event) {
            is ShareEvent.Opened -> open(event.fileId)

            is ShareEvent.Selected -> state.update {
                if (it.stage == ShareStage.Choosing &&
                    event.mode in it.available
                ) {
                    it.copy(selected = event.mode)
                } else {
                    it
                }
            }

            ShareEvent.ShareTapped -> share()

            ShareEvent.OriginalConfirmed -> {
                state.update { it.copy(confirmOriginal = false) }
                prepare(ShareMode.ORIGINAL)
            }

            ShareEvent.OriginalDismissed -> state.update { it.copy(confirmOriginal = false) }

            ShareEvent.CancelTapped, ShareEvent.TryAnotherWay -> {
                stopWork()
                state.update { it.copy(stage = ShareStage.Choosing) }
            }

            ShareEvent.Launched -> state.update { it.copy(stage = ShareStage.Done) }

            ShareEvent.Dismissed -> {
                stopWork()
                state.value = ShareUiState()
            }
        }
    }

    private fun open(fileId: String) {
        stopWork()
        state.value = ShareUiState()
        work = viewModelScope.launch {
            val file = repository.file(fileId).first()
            if (file == null) {
                state.update { it.copy(stage = ShareStage.Done) }
                return@launch
            }
            val blocked = blockReason(file)
            val available = if (blocked == null) modesFor(file) else emptySet()
            state.value = ShareUiState(
                file = file,
                available = available,
                selected = DEFAULT_ORDER.firstOrNull { it in available } ?: ShareMode.SAFE_COPY,
                stage = blocked?.let(ShareStage::Blocked) ?: ShareStage.Choosing,
            )
        }
    }

    private fun share() {
        val current = state.value
        val file = current.file ?: return
        if (current.stage != ShareStage.Choosing || current.selected !in current.available) return
        when (current.selected) {
            ShareMode.SAFE_COPY -> prepare(ShareMode.SAFE_COPY)
            ShareMode.ORIGINAL -> state.update { it.copy(confirmOriginal = true) }
            ShareMode.LINK -> state.update { it.copy(stage = ShareStage.Ready(ShareTarget.Link(file.sourceUrl))) }
        }
    }

    private fun prepare(mode: ShareMode) {
        val file = state.value.file ?: return
        if (mode !in state.value.available) return
        stopWork()
        val started = clock.millis()
        state.update { it.copy(stage = ShareStage.Preparing(mode, fraction = null, secondsLeft = null)) }
        work = viewModelScope.launch {
            copier.copy(file, mode)
                .catch { cause ->
                    val reason = (cause as? CopyFailedException)?.reason ?: CopyFailure.FAILED
                    state.update { it.copy(stage = ShareStage.Failed(reason)) }
                }
                .collect { progress ->
                    val stage = when (progress) {
                        is CopyProgress.Working ->
                            ShareStage.Preparing(mode, progress.fraction, secondsLeft(started, progress.fraction))

                        is CopyProgress.Done -> ShareStage.Ready(ShareTarget.File(progress.share))
                    }
                    state.update { it.copy(stage = stage) }
                }
        }
    }

    /** An estimate once there is enough to go on: a couple of seconds in and some way along. */
    private fun secondsLeft(started: Long, fraction: Float?): Int? {
        val elapsed = clock.millis() - started
        if (fraction == null || fraction !in MIN_FRACTION..<1f) return null
        if (elapsed < MIN_ELAPSED_MS) return null
        val remaining = elapsed / fraction - elapsed
        return ceil(remaining / MILLIS_PER_SECOND).toInt().coerceAtLeast(1)
    }

    private fun stopWork() {
        work?.cancel()
        work = null
    }

    companion object {
        /** The safest choice the file allows comes first. */
        private val DEFAULT_ORDER = listOf(ShareMode.SAFE_COPY, ShareMode.LINK, ShareMode.ORIGINAL)
        private const val MIN_FRACTION = 0.05f
        private const val MIN_ELAPSED_MS = 1_500L
        private const val MILLIS_PER_SECOND = 1000.0

        fun blockReason(file: VaultFile): ShareBlockReason? = when {
            file.openFailed -> ShareBlockReason.OPEN_FAILED
            file.category == FileCategory.UNKNOWN -> ShareBlockReason.UNKNOWN
            file.category != FileCategory.BLOCKED -> null
            TypePolicy.isAppInstaller(file.detectedType) -> ShareBlockReason.APP_INSTALLER
            else -> ShareBlockReason.PROGRAM
        }

        fun modesFor(file: VaultFile): Set<ShareMode> {
            val rules = TypePolicy.rulesFor(file.category)
            return buildSet {
                if (rules.canSafeCopy) add(ShareMode.SAFE_COPY)
                if (rules.canShareOriginal) add(ShareMode.ORIGINAL)
                if (file.sourceUrl.isNotBlank()) add(ShareMode.LINK)
            }
        }
    }
}
