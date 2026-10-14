package dev.ikram.sandbox.core.data.download

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Files saved since the vault was last looked at: the badge on the vault icon. */
@Singleton
class NewFilesCounter @Inject constructor() {
    private val state = MutableStateFlow(0)
    val count: StateFlow<Int> = state.asStateFlow()

    @Volatile private var vaultVisible = false

    /** A file saved while the vault is on screen is seen already. */
    fun increment() {
        if (!vaultVisible) state.update { it + 1 }
    }

    fun setVaultVisible(visible: Boolean) {
        vaultVisible = visible
        if (visible) state.value = 0
    }
}
