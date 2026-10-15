package dev.ikram.sandbox.core.model

/** Everything a person can change in Settings, with SafeHold's defaults. */
data class Settings(
    val askBeforeDownload: Boolean = true,
    val autoDelete: AutoDelete = AutoDelete.NEVER,
    /** Look every new file up on VirusTotal by its hash. Needs [virusTotalKey]. */
    val autoScan: Boolean = false,
    /** The person's own VirusTotal API key. SafeHold ships none. */
    val virusTotalKey: String? = null,
    val vaultLock: Boolean = false,
    val onboardingDone: Boolean = false,
)

/** How long files stay in the vault. Nothing is deleted unless someone chooses to. */
enum class AutoDelete(val days: Int?) {
    NEVER(null),
    AFTER_7_DAYS(7),
    AFTER_30_DAYS(30),
    AFTER_90_DAYS(90),
}
