package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.model.DetectedType

/**
 * OLE compound files hold both Windows installers (MSI, MSP, MST) and old Office
 * documents. The root storage's class ID tells them apart. Anything unreadable is
 * reported as an installer, the stricter of the two.
 */
internal object OleInspector {
    private const val HEADER_SIZE = 512
    private const val SECTOR_SHIFT_OFFSET = 30
    private const val FIRST_DIRECTORY_SECTOR_OFFSET = 48
    private const val ROOT_CLSID_OFFSET = 80
    private const val CLSID_SIZE = 16
    private const val MAX_REGULAR_SECTOR = 0xFFFFFFFAL

    /**
     * {000C1084-0000-0000-C000-000000000046} is MSI; ...1082 is a transform (MST) and
     * ...1086 a patch (MSP). Stored little-endian, so the distinguishing byte comes first.
     */
    private val INSTALLER_FIRST_BYTES = setOf(0x84, 0x82, 0x86)
    private val INSTALLER_CLSID_REST = bytesOf(0x10, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x00, 0xC0, 0, 0, 0, 0, 0, 0, 0x46)

    fun classify(source: ByteSource): DetectedType {
        val header = source.readFully(0, HEADER_SIZE) ?: return DetectedType.MSI
        val sectorShift = header.u16le(SECTOR_SHIFT_OFFSET)
        if (sectorShift != 9 && sectorShift != 12) return DetectedType.MSI
        val firstDirectorySector = header.u32le(FIRST_DIRECTORY_SECTOR_OFFSET)
        if (firstDirectorySector >= MAX_REGULAR_SECTOR) return DetectedType.MSI

        val sectorSize = 1L shl sectorShift
        // Sector n starts after the header, which occupies one sector-sized slot.
        val rootEntry = (firstDirectorySector + 1) * sectorSize
        val clsid = source.readFully(rootEntry + ROOT_CLSID_OFFSET, CLSID_SIZE) ?: return DetectedType.MSI
        val isInstaller = clsid.u8(0) in INSTALLER_FIRST_BYTES && clsid.hasBytesAt(1, INSTALLER_CLSID_REST)
        return if (isInstaller) DetectedType.MSI else DetectedType.OLE_DOCUMENT
    }
}
