package dev.ikram.sandbox.navigation

import kotlinx.serialization.Serializable

/** Type safe navigation destinations. */
sealed interface Routes {
    @Serializable
    data object Onboarding : Routes

    @Serializable
    data object Browser : Routes

    @Serializable
    data object Vault : Routes

    @Serializable
    data object Settings : Routes

    @Serializable
    data object HowProtectionWorks : Routes

    /** Opens a vault file in whichever built in viewer its category allows. */
    @Serializable
    data class Viewer(val fileId: String) : Routes
}
