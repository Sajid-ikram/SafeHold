package dev.ikram.sandbox.fakes

import dev.ikram.sandbox.core.data.db.VaultFileEntity
import dev.ikram.sandbox.core.model.DetectedType
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking

/** Adds a vault row and returns its id. Defaults describe an ordinary, openable file. */
fun FakeVaultFileDao.addFile(
    name: String,
    type: DetectedType,
    createdAt: Instant = Instant.EPOCH,
    sha256: String = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
    openFailed: Boolean = false,
    id: String = UUID.randomUUID().toString(),
): String {
    runBlocking {
        insert(
            VaultFileEntity(
                id = id,
                displayName = name,
                sizeBytes = 41_000_000,
                detectedType = type.name,
                claimedType = null,
                sha256 = sha256,
                sourceUrl = "https://podcasts.example/$name",
                sourceHost = "podcasts.example",
                createdAtMillis = createdAt.toEpochMilli(),
                scanStatus = "NOT_CHECKED",
                scanFlaggedCount = null,
                isMismatch = false,
                mismatchAcknowledged = false,
                openFailed = openFailed,
            ),
        )
    }
    return id
}
