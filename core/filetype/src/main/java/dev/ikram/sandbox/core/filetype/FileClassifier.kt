package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.FileCategory

data class Classification(
    val detectedType: DetectedType,
    val claimedType: DetectedType?,
    val category: FileCategory,
    val rules: FileRules,
    val isMismatch: Boolean,
)

/** One call for the downloader: sniff the bytes, apply the policy, compare with the claim. */
object FileClassifier {
    fun classify(source: ByteSource, claimedName: String?, claimedMimeType: String?): Classification {
        val detected = TypePolicy.restrictByName(MagicSniffer.sniff(source), claimedName)
        val claimed = ClaimedType.of(claimedName, claimedMimeType)
        val category = TypePolicy.categoryOf(detected)
        return Classification(
            detectedType = detected,
            claimedType = claimed,
            category = category,
            rules = TypePolicy.rulesFor(category),
            isMismatch = TypePolicy.isMismatch(detected, claimed),
        )
    }
}
