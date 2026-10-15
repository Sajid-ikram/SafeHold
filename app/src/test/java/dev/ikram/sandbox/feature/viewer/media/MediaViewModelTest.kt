package dev.ikram.sandbox.feature.viewer.media

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import dev.ikram.sandbox.MainDispatcherRule
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.fakes.FakeVaultFileDao
import dev.ikram.sandbox.fakes.addFile
import dev.ikram.sandbox.feature.vault.DayLabel
import dev.ikram.sandbox.feature.viewer.ViewerRouterViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @get:Rule val folder = TemporaryFolder()

    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val dao = FakeVaultFileDao()
    private val repository by lazy {
        VaultRepository(dao, VaultStorage(folder.newFolder("files")), Dispatchers.Unconfined)
    }

    private val listener = slot<Player.Listener>()
    private val player = mockk<Player>(relaxed = true) {
        every { addListener(capture(listener)) } returns Unit
        every { playbackParameters } returns PlaybackParameters(1f)
    }
    private var created = 0
    private val factory = PlayerFactory {
        created++
        player
    }

    private fun viewModel(id: String) = MediaViewModel(
        SavedStateHandle(mapOf(ViewerRouterViewModel.FILE_ID to id)),
        repository,
        factory,
        clock,
    )

    private fun video() = dao.addFile("lecture-clip.mp4", DetectedType.MP4, createdAt = now.minusSeconds(DAY))

    private fun audio() = dao.addFile("interview-ep12.mp3", DetectedType.MP3, createdAt = now)

    @Test
    fun startsPlayingAsSoonAsTheFileIsFound() {
        val state = viewModel(video()).uiState.value
        assertEquals(1, created)
        verify { player.play() }
        assertTrue(listener.isCaptured)
        assertEquals(player, state.player)
        assertTrue(state.showsVideo)
        assertEquals(DayLabel.Yesterday, state.savedOn)
    }

    @Test
    fun audioUsesTheAudioLayout() {
        val state = viewModel(audio()).uiState.value
        assertFalse(state.showsVideo)
        assertEquals(DayLabel.Today, state.savedOn)
    }

    @Test
    fun aVideoFileHoldingOnlySoundSwitchesToTheAudioLayout() {
        val viewModel = viewModel(video())
        val tracks = mockk<Tracks> {
            every { isEmpty } returns false
            every { containsType(C.TRACK_TYPE_VIDEO) } returns false
        }
        listener.captured.onTracksChanged(tracks)
        assertFalse(viewModel.uiState.value.showsVideo)
    }

    @Test
    fun filesThatAreNotMediaGetNoPlayer() {
        val viewModel = viewModel(dao.addFile("setup.apk", DetectedType.APK))
        assertEquals(PlaybackStatus.FAILED, viewModel.uiState.value.status)
        assertEquals(0, created)
    }

    @Test
    fun filesThatFailedToOpenBeforeGetNoPlayer() {
        val viewModel = viewModel(dao.addFile("bad.mp4", DetectedType.MP4, openFailed = true))
        assertEquals(PlaybackStatus.FAILED, viewModel.uiState.value.status)
        assertEquals(0, created)
    }

    @Test
    fun followsThePlayer() {
        val viewModel = viewModel(video())
        every { player.currentPosition } returns 252_000
        every { player.duration } returns 760_000
        listener.captured.onPlaybackStateChanged(Player.STATE_READY)
        listener.captured.onIsPlayingChanged(true)
        listener.captured.onVideoSizeChanged(VideoSize(1920, 1080))
        val state = viewModel.uiState.value
        assertEquals(PlaybackStatus.READY, state.status)
        assertTrue(state.isPlaying)
        assertEquals(252_000, state.positionMs)
        assertEquals(760_000, state.durationMs)
        assertEquals(1080, state.videoLines)
        assertFalse(state.isPortraitVideo)
        listener.captured.onIsPlayingChanged(false)
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun anUnknownDurationReadsAsZero() {
        val viewModel = viewModel(audio())
        every { player.duration } returns C.TIME_UNSET
        listener.captured.onPlaybackStateChanged(Player.STATE_READY)
        assertEquals(0, viewModel.uiState.value.durationMs)
    }

    @Test
    fun playPausePausesWhilePlaying() {
        val viewModel = viewModel(audio())
        every { player.isPlaying } returns true
        viewModel.onEvent(MediaEvent.PlayPause)
        verify { player.pause() }
    }

    @Test
    fun playPauseAtTheEndStartsAgainFromTheTop() {
        val viewModel = viewModel(audio())
        every { player.isPlaying } returns false
        every { player.playbackState } returns Player.STATE_ENDED
        viewModel.onEvent(MediaEvent.PlayPause)
        verify { player.seekTo(0) }
        verify(exactly = 2) { player.play() }
    }

    @Test
    fun seeking() {
        val viewModel = viewModel(audio())
        viewModel.onEvent(MediaEvent.SeekBack)
        viewModel.onEvent(MediaEvent.SeekForward)
        viewModel.onEvent(MediaEvent.SeekTo(-5))
        viewModel.onEvent(MediaEvent.SeekTo(90_000))
        verify { player.seekBack() }
        verify { player.seekForward() }
        verify { player.seekTo(0) }
        verify { player.seekTo(90_000) }
    }

    @Test
    fun theSpeedChipStepsThroughSpeedsAndBackToNormal() {
        val viewModel = viewModel(audio())
        viewModel.onEvent(MediaEvent.CycleSpeed)
        verify { player.setPlaybackSpeed(1.25f) }
        assertEquals(1f, MediaViewModel.nextSpeed(0.75f))
        assertEquals(1f, MediaViewModel.nextSpeed(3f))
        listener.captured.onPlaybackParametersChanged(PlaybackParameters(1.5f))
        assertEquals(1.5f, viewModel.uiState.value.speed)
    }

    @Test
    fun anErrorStopsEverything() {
        val viewModel = viewModel(video())
        viewModel.onEvent(MediaEvent.ToggleFullscreen)
        listener.captured.onPlayerError(PlaybackException("bad", null, PlaybackException.ERROR_CODE_DECODING_FAILED))
        val state = viewModel.uiState.value
        assertEquals(PlaybackStatus.FAILED, state.status)
        assertFalse(state.fullscreen)
        // Later state changes do not bring it back, and controls do nothing.
        listener.captured.onPlaybackStateChanged(Player.STATE_READY)
        assertEquals(PlaybackStatus.FAILED, viewModel.uiState.value.status)
        viewModel.onEvent(MediaEvent.SeekForward)
        verify(exactly = 0) { player.seekForward() }
    }

    @Test
    fun onlyVideosGoFullScreen() {
        val videoModel = viewModel(video())
        videoModel.onEvent(MediaEvent.ToggleFullscreen)
        assertTrue(videoModel.uiState.value.fullscreen)
        videoModel.onEvent(MediaEvent.ToggleFullscreen)
        assertFalse(videoModel.uiState.value.fullscreen)

        val audioModel = viewModel(audio())
        audioModel.onEvent(MediaEvent.ToggleFullscreen)
        assertFalse(audioModel.uiState.value.fullscreen)
    }

    @Test
    fun goingToTheBackgroundPauses() {
        viewModel(audio()).onEvent(MediaEvent.Backgrounded)
        verify { player.pause() }
    }

    @Test
    fun deletingStopsPlaybackRemovesTheFileAndCloses() {
        val viewModel = viewModel(audio())
        viewModel.onEvent(MediaEvent.DeleteTapped)
        assertTrue(viewModel.uiState.value.confirmDelete)
        viewModel.onEvent(MediaEvent.DeleteConfirmed)
        verify { player.stop() }
        assertTrue(viewModel.uiState.value.closed)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun closingTheViewerReleasesThePlayer() {
        val id = audio()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, viewModelFactory { initializer { viewModel(id) } })[
            MediaViewModel::class,
        ]
        assertFalse(viewModel.uiState.value.sharing)
        store.clear()
        verify { player.removeListener(listener.captured) }
        verify { player.release() }
    }

    private companion object {
        const val DAY = 24 * 60 * 60L
    }
}
