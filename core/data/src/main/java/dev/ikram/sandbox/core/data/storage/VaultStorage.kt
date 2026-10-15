package dev.ikram.sandbox.core.data.storage

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every vault path, in one place (golden rule 2). Files live only under the app's
 * private `filesDir/vault/` and are always named `<uuid>.bin`: no extension a system
 * could act on, and nothing a server said ever becomes part of a path.
 */
@Singleton
class VaultStorage(private val root: File) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.filesDir)

    /** Files touched before this are leftovers of an earlier process; anything newer may be in use. */
    private val sessionStart = System.currentTimeMillis()

    val vaultDir: File get() = File(root, VAULT)

    private val partialDir: File get() = File(vaultDir, PARTIAL)

    /** Outgoing copies, the only directory FileProvider exposes. Emptied an hour after each share. */
    val shareTmpDir: File get() = File(root, SHARE_TMP)

    /**
     * A new, empty file to share, named `<uuid>.<extension>`. The extension always comes
     * from SafeHold's own type table, never from a server.
     */
    fun newShareFile(extension: String): File {
        require(EXTENSION_PATTERN.matches(extension)) { "Unexpected extension" }
        if (!shareTmpDir.isDirectory && !shareTmpDir.mkdirs()) throw IOException("Cannot create the share folder")
        return File(shareTmpDir, "${UUID.randomUUID()}.$extension")
    }

    fun fileFor(id: String): File = File(vaultDir, "${checkedId(id)}$EXTENSION")

    /** Where a download is written until it has been checked. */
    fun newPartial(id: String): File {
        if (!partialDir.isDirectory && !partialDir.mkdirs()) throw IOException("Cannot create the vault")
        return File(partialDir, checkedId(id))
    }

    /** Moves a finished download into the vault. Same directory tree, so the rename is atomic. */
    fun commit(partial: File, id: String): File {
        val target = fileFor(id)
        if (!partial.renameTo(target)) throw IOException("Could not move the download into the vault")
        return target
    }

    fun delete(id: String): Boolean = fileFor(id).let { !it.exists() || it.delete() }

    /** Ids of the files actually on disk. */
    fun storedIds(): Set<String> = storedFiles().map { it.name.removeSuffix(EXTENSION) }.toSet()

    /** Ids of files written by an earlier process, which no download of this one can still be finishing. */
    fun earlierSessionIds(): Set<String> = storedFiles()
        .filter { it.lastModified() < sessionStart }
        .map { it.name.removeSuffix(EXTENSION) }
        .toSet()

    /** Leftovers from downloads an earlier process did not live to finish. */
    fun clearPartials() {
        partialDir.listFiles().orEmpty().filter { it.lastModified() < sessionStart }.forEach { it.delete() }
    }

    private fun storedFiles(): List<File> = vaultDir.listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(EXTENSION) && isValidId(it.name.removeSuffix(EXTENSION)) }

    // A pre-flight estimate only; running out mid-download is caught as ENOSPC anyway.
    @SuppressLint("UsableSpace")
    fun usableSpace(): Long = root.usableSpace

    private fun checkedId(id: String): String {
        require(isValidId(id)) { "Vault ids are UUIDs" }
        return id
    }

    companion object {
        private const val VAULT = "vault"
        private const val PARTIAL = ".partial"
        private const val SHARE_TMP = "share_tmp"
        private const val EXTENSION = ".bin"
        private val EXTENSION_PATTERN = Regex("^[a-z0-9]{1,5}$")
        private val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

        fun isValidId(id: String): Boolean = UUID_PATTERN.matches(id)
    }
}
