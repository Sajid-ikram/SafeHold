package dev.ikram.sandbox

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.feature.browser.web.LocalWebViewPool
import dev.ikram.sandbox.feature.browser.web.WebViewPool
import dev.ikram.sandbox.feature.lock.VaultLock
import dev.ikram.sandbox.navigation.SandboxNavHost
import javax.inject.Inject
import kotlinx.coroutines.launch

/** A FragmentActivity because the fingerprint prompt needs one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var vaultLock: VaultLock

    /** The tabs' WebViews live exactly as long as this activity. */
    private val webViewPool by lazy { WebViewPool(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hideFromRecentsWhileLocked()
        setContent {
            SandboxTheme {
                CompositionLocalProvider(LocalWebViewPool provides webViewPool) {
                    SandboxNavHost()
                }
            }
        }
    }

    /** With the vault lock on, the recent apps screen shows no picture of what was open. */
    private fun hideFromRecentsWhileLocked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                vaultLock.enabled.collect { enabled -> setRecentsScreenshotEnabled(enabled != true) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vaultLock.onForeground()
    }

    override fun onResume() {
        super.onResume()
        webViewPool.resume()
    }

    override fun onPause() {
        webViewPool.pause()
        super.onPause()
    }

    override fun onStop() {
        vaultLock.onBackground()
        super.onStop()
    }

    override fun onDestroy() {
        webViewPool.destroyAll()
        super.onDestroy()
    }
}
