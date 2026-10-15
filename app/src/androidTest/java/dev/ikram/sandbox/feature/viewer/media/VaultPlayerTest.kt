package dev.ikram.sandbox.feature.viewer.media

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.ScanStatus
import dev.ikram.sandbox.core.model.VaultFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The real player, as the media viewers build it. */
@RunWith(AndroidJUnit4::class)
class VaultPlayerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "player-test-${UUID.randomUUID()}")
    private val storage = VaultStorage(root)
    private val factory = VaultPlayerFactory(context, storage)
    private val players = mutableListOf<Player>()

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { players.forEach(Player::release) }
        root.deleteRecursively()
    }

    @Test
    fun onlyPictureAndSoundRenderersExist() {
        val player = prepare(store(wav(), DetectedType.WAV)).player
        val types = mutableListOf<Int>()
        instrumentation.runOnMainSync { repeat(player.rendererCount) { types.add(player.getRendererType(it)) } }
        assertEquals(setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO), types.toSet())
    }

    @Test
    fun playsAudio() {
        val result = prepare(store(wav(), DetectedType.WAV))
        assertNull(result.error)
        var duration = 0L
        instrumentation.runOnMainSync { duration = result.player.duration }
        assertEquals(1_000.0, duration.toDouble(), 50.0)
    }

    @Test
    fun anImageDisguisedAsAVideoIsNeverDecoded() {
        // Media3 can read PNGs, but the vault player has no image extractor or renderer.
        val result = prepare(store(PNG, DetectedType.MP4))
        assertNotNull(result.error)
        assertEquals(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, result.error?.errorCode)
    }

    private class Prepared(val player: ExoPlayer, val error: PlaybackException?)

    /** Builds the player on the main thread and waits until it is ready or has failed. */
    private fun prepare(file: VaultFile): Prepared {
        val settled = CountDownLatch(1)
        var error: PlaybackException? = null
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            player = factory.create(file) as ExoPlayer
            players.add(player)
            player.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) settled.countDown()
                    }

                    override fun onPlayerError(playbackError: PlaybackException) {
                        error = playbackError
                        settled.countDown()
                    }
                },
            )
        }
        assertTrue("player did not settle", settled.await(10, TimeUnit.SECONDS))
        return Prepared(player, error)
    }

    private fun store(bytes: ByteArray, type: DetectedType): VaultFile {
        val id = UUID.randomUUID().toString()
        storage.vaultDir.mkdirs()
        storage.fileFor(id).writeBytes(bytes)
        return VaultFile(
            id = id,
            displayName = "test.${type.extension}",
            sizeBytes = bytes.size.toLong(),
            detectedType = type,
            claimedType = null,
            category = TypePolicy.categoryOf(type),
            sha256 = "00",
            sourceUrl = "https://example.org/test",
            sourceHost = "example.org",
            createdAt = Instant.now(),
            scanStatus = ScanStatus.NotChecked,
            isMismatch = false,
            mismatchAcknowledged = false,
            openFailed = false,
        )
    }

    /** One second of 16-bit mono silence at 8 kHz. */
    private fun wav(): ByteArray {
        val rate = 8_000
        val data = rate * 2
        return ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + data)
            put("WAVEfmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(rate)
            putInt(rate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(data)
        }.array()
    }

    private companion object {
        /** A 1 x 1 transparent PNG. */
        val PNG = listOf(
            0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4,
            0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE,
            0x42, 0x60, 0x82,
        ).map { it.toByte() }.toByteArray()
    }
}
