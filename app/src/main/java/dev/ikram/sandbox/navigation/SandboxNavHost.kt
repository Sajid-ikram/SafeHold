package dev.ikram.sandbox.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.feature.browser.BrowserRoute
import dev.ikram.sandbox.feature.browser.VaultIconWithBadge
import dev.ikram.sandbox.feature.explainer.HowProtectionWorksRoute
import dev.ikram.sandbox.feature.lock.BiometricGate
import dev.ikram.sandbox.feature.onboarding.OnboardingRoute
import dev.ikram.sandbox.feature.settings.SettingsRoute
import dev.ikram.sandbox.feature.vault.VaultRoute
import dev.ikram.sandbox.feature.viewer.ViewerRouter
import kotlin.reflect.KClass

private enum class TopLevelDestination(
    val route: Routes,
    val routeClass: KClass<out Routes>,
    val icon: ImageVector,
    @param:StringRes val label: Int,
) {
    BROWSER(Routes.Browser, Routes.Browser::class, SandboxIcons.Browser, R.string.nav_browser),
    VAULT(Routes.Vault, Routes.Vault::class, SandboxIcons.Vault, R.string.nav_vault),
    SETTINGS(Routes.Settings, Routes.Settings::class, SandboxIcons.Settings, R.string.nav_settings),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SandboxNavHost(
    navController: NavHostController = rememberNavController(),
    shell: ShellViewModel = hiltViewModel(),
) {
    val newFiles by shell.newFiles.collectAsStateWithLifecycle()
    val onboardingDone by shell.onboardingDone.collectAsStateWithLifecycle()
    // Nothing until settings are read (a moment, under the splash screen). The start is
    // fixed then: changing it later would reset the whole back stack.
    val start = onboardingDone?.let { done -> remember { if (done) Routes.Browser else Routes.Onboarding } } ?: return
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Hidden while typing, so the screen above can shrink to fit beside the keyboard.
    val showBottomBar = TopLevelDestination.entries.any { currentDestination.isOn(it) } && !WindowInsets.isImeVisible

    Scaffold(
        // Each screen owns its insets: viewers draw edge to edge, tabs get a top bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                SandboxBottomBar(
                    currentDestination = currentDestination,
                    newFiles = newFiles,
                    onNavigate = { navController.navigateToTopLevel(it.route) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable<Routes.Onboarding> {
                OnboardingRoute(
                    onDone = {
                        navController.navigate(Routes.Browser) {
                            popUpTo<Routes.Onboarding> { inclusive = true }
                        }
                    },
                )
            }
            composable<Routes.Browser> {
                BrowserRoute(onOpenVault = { navController.navigateToTopLevel(Routes.Vault) })
            }
            composable<Routes.Vault> {
                BiometricGate {
                    VaultRoute(
                        onOpenFile = { fileId -> navController.navigate(Routes.Viewer(fileId)) },
                        onStartBrowsing = { navController.navigateToTopLevel(Routes.Browser) },
                    )
                }
            }
            composable<Routes.Settings> {
                SettingsRoute(onHowProtectionWorks = { navController.navigate(Routes.HowProtectionWorks) })
            }
            composable<Routes.HowProtectionWorks> {
                HowProtectionWorksRoute(onBack = navController::popBackStack)
            }
            composable<Routes.Viewer> {
                BiometricGate { ViewerRouter(onBack = navController::popBackStack) }
            }
        }
    }
}

@Composable
private fun SandboxBottomBar(
    currentDestination: NavDestination?,
    newFiles: Int,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentDestination.isOn(destination),
                onClick = { onNavigate(destination) },
                icon = {
                    if (destination == TopLevelDestination.VAULT) {
                        VaultIconWithBadge(newFiles, contentDescription = null, tint = LocalContentColor.current)
                    } else {
                        Icon(destination.icon, contentDescription = null)
                    }
                },
                label = { Text(stringResource(destination.label), style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true

/** Tab style navigation: one copy of each tab on the back stack, state kept per tab. */
private fun NavHostController.navigateToTopLevel(route: Routes) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
