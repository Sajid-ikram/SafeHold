package dev.ikram.sandbox.feature.viewer.media

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.vault.DayLabel
import dev.ikram.sandbox.feature.viewer.ViewerRouterViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PlaybackStatus { PREPARING, READY, ENDED, FAILED }

data class MediaUiState(
    val file: VaultFile? = null,
    /** Drawn by the video surface. Null until the file has been looked up. */
    val player: Player? = null,
    val status: PlaybackStatus = PlaybackStatus.PREPARING,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    /** Zero while unknown. */
    val durationMs: Long = 0,
    val speed: Float = 1f,
    /** The picture in pixels, zero until known. */
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /** The file has a picture to show. An MP4 holding only sound plays in the audio layout. */
    val hasVideo: Boolean = true,
    val fullscreen: Boolean = false,
    val savedOn: DayLabel = DayLabel.Today,
    val today: LocalDate = LocalDate.MIN,
    val showDetails: Boolean = false,
    val confirmDelete: Boolean = false,
    val sharing: Boolean = false,
    val closed: Boolean = false,
) {
    val showsVideo: Boolean get() = file?.category == FileCategory.VIDEO && hasVideo

    /** "1080p": the shorter side, as video quality is usually named. Zero until known. */
    val videoLines: Int get() = minOf(videoWidth, videoHeight)

    val isPortraitVideo: Boolean get() = videoHeight > videoWidth
}

sealed interface MediaEvent {
    data object PlayPause : MediaEvent

    data object SeekBack : MediaEvent

    data object SeekForward : MediaEvent

    data class SeekTo(val positionMs: Long) : MediaEvent

    data object CycleSpeed : MediaEvent

    data object ToggleFullscreen : MediaEvent

    /** The app went to the background: playback stops, as there is no background playback. */
    data object Backgrounded : MediaEvent

    data object ShareTapped : MediaEvent

    data object DetailsTapped : MediaEvent

    data object DetailsDismissed : MediaEvent

    data object DeleteTapped : MediaEvent

    data object DeleteConfirmed : MediaEvent

    data object DeleteDismissed : MediaEvent

    data object ShareDismissed : MediaEvent
}

/**
 * Plays one video or audio file. The player belongs to this view model, so it outlives
 * rotation and is released when the viewer closes.
 */
@HiltViewModel
class MediaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
    private val players: PlayerFactory,
    private val clock: Clock,
) : ViewModel() {
    private val fileId: String? = savedStateHandle.get<String>(ViewerRouterViewModel.FILE_ID)
    private val state = MutableStateFlow(MediaUiState(today = LocalDate.now(clock)))
    val uiState: StateFlow<MediaUiState> = state.asStateFlow()

    private var player: Player? = null
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            state.update { it.copy(isPlaying = isPlaying) }
            syncPosition()
            ticker?.cancel()
            if (isPlaying) ticker = viewModelScope.launch { tick() }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val status = when (playbackState) {
                Player.STATE_READY -> PlaybackStatus.READY
                Player.STATE_ENDED -> PlaybackStatus.ENDED
                else -> null
            }
            if (status != null && state.value.status != PlaybackStatus.FAILED) state.update { it.copy(status = status) }
            syncPosition()
        }

        override fun onPlayerError(error: PlaybackException) {
            state.update { it.copy(status = PlaybackStatus.FAILED, isPlaying = false, fullscreen = false) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (!tracks.isEmpty) state.update { it.copy(hasVideo = tracks.containsType(C.TRACK_TYPE_VIDEO)) }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            state.update { it.copy(videoWidth = videoSize.width, videoHeight = videoSize.height) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            state.update { it.copy(speed = playbackParameters.speed) }
        }
    }

    init {
        viewModelScope.launch {
            val file = fileId?.let { repository.file(it).first() }
            if (file == null || file.category !in PLAYABLE || file.openFailed) {
                state.update { it.copy(file = file, status = PlaybackStatus.FAILED) }
                return@launch
            }
            val created = players.create(file).also { it.addListener(listener) }
            player = created
            val today = LocalDate.now(clock)
            state.update {
                it.copy(
                    file = file,
                    player = created,
                    hasVideo = file.category == FileCategory.VIDEO,
                    savedOn = DayLabel.of(file.createdAt.atZone(clock.zone).toLocalDate(), today),
                    today = today,
                )
            }
            created.play()
        }
    }

    fun onEvent(event: MediaEvent) {
        when (event) {
            MediaEvent.PlayPause -> playPause()

            MediaEvent.SeekBack -> withPlayer { seekBack() }

            MediaEvent.SeekForward -> withPlayer { seekForward() }

            is MediaEvent.SeekTo -> withPlayer { seekTo(event.positionMs.coerceAtLeast(0)) }

            MediaEvent.CycleSpeed -> withPlayer { setPlaybackSpeed(nextSpeed(playbackParameters.speed)) }

            MediaEvent.ToggleFullscreen -> state.update { it.copy(fullscreen = !it.fullscreen && it.showsVideo) }

            MediaEvent.Backgrounded -> player?.pause()

            MediaEvent.ShareTapped -> {
                player?.pause()
                state.update { it.copy(sharing = true, showDetails = false) }
            }

            MediaEvent.DetailsTapped -> state.update { it.copy(showDetails = true) }

            MediaEvent.DetailsDismissed -> state.update { it.copy(showDetails = false) }

            MediaEvent.DeleteTapped -> state.update { it.copy(confirmDelete = true) }

            MediaEvent.DeleteConfirmed -> delete()

            MediaEvent.DeleteDismissed -> state.update { it.copy(confirmDelete = false) }

            MediaEvent.ShareDismissed -> state.update { it.copy(sharing = false) }
        }
    }

    private fun playPause() = withPlayer {
        when {
            isPlaying -> pause()

            playbackState == Player.STATE_ENDED -> {
                seekTo(0)
                play()
            }

            else -> play()
        }
    }

    private inline fun withPlayer(action: Player.() -> Unit) {
        val current = player ?: return
        if (state.value.status == PlaybackStatus.FAILED) return
        current.action()
        syncPosition()
    }

    private suspend fun tick() {
        while (viewModelScope.isActive) {
            delay(TICK_MS)
            syncPosition()
        }
    }

    private fun syncPosition() {
        val current = player ?: return
        val duration = current.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
        state.update { it.copy(positionMs = current.currentPosition.coerceAtLeast(0), durationMs = duration) }
    }

    private fun delete() {
        val id = state.value.file?.id ?: return
        player?.stop()
        state.update { it.copy(confirmDelete = false, showDetails = false, closed = true) }
        viewModelScope.launch { repository.delete(id) }
    }

    override fun onCleared() {
        ticker?.cancel()
        player?.run {
            removeListener(listener)
            release()
        }
        player = null
    }

    companion object {
        private val PLAYABLE = setOf(FileCategory.VIDEO, FileCategory.AUDIO)
        private const val TICK_MS = 250L

        /** The speed chip steps through these, back to normal speed after the fastest. */
        val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)

        fun nextSpeed(current: Float): Float {
            val index = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < SPEED_TOLERANCE }
            return SPEEDS[(index + 1).mod(SPEEDS.size)]
        }

        private const val SPEED_TOLERANCE = 0.01f
    }
}
