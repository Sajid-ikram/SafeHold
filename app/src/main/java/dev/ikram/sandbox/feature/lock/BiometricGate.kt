package dev.ikram.sandbox.feature.lock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons

/**
 * Wraps the vault and the viewers. With the lock on and the vault locked, shows a lock
 * screen and asks for a fingerprint or the screen lock straight away.
 */
@Composable
fun BiometricGate(viewModel: LockViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val context = LocalContext.current
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    val unlock = {
        activity?.let { Authenticator.authenticate(it, title, subtitle, onSuccess = viewModel::unlocked) }
    }
    when (state) {
        LockState.UNKNOWN -> Box(Modifier.fillMaxSize())

        LockState.OPEN -> content()

        LockState.LOCKED -> {
            LaunchedEffect(Unit) {
                if (Authenticator.isAvailable(context)) unlock() else viewModel.screenLockGone()
            }
            LockedScreen(onUnlock = { unlock() })
        }
    }
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(colors.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SandboxIcons.Lock,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            stringResource(R.string.lock_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.lock_message),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onUnlock, modifier = Modifier.padding(top = 8.dp).height(52.dp)) {
            Icon(SandboxIcons.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.lock_unlock), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
