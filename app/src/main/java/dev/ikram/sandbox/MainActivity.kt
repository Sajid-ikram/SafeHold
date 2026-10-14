package dev.ikram.sandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.feature.browser.web.LocalWebViewPool
import dev.ikram.sandbox.feature.browser.web.WebViewPool
import dev.ikram.sandbox.navigation.SandboxNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** The tabs' WebViews live exactly as long as this activity. */
    private val webViewPool by lazy { WebViewPool(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SandboxTheme {
                CompositionLocalProvider(LocalWebViewPool provides webViewPool) {
                    SandboxNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webViewPool.resume()
    }

    override fun onPause() {
        webViewPool.pause()
        super.onPause()
    }

    override fun onDestroy() {
        webViewPool.destroyAll()
        super.onDestroy()
    }
}
