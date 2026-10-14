package dev.ikram.sandbox.feature.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.feature.browser.AddressInput

/**
 * The design's rounded address field: connection icon, the address, refresh or stop.
 * Shows the short form ("example.org/guides") until tapped, then the full URL, selected.
 */
@Composable
fun AddressBar(
    url: String?,
    isLoading: Boolean,
    onSubmit: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Read inside the focus callback, which outlives the composition that created it.
    val currentUrl by rememberUpdatedState(url)
    var focused by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue(AddressInput.display(url))) }
    LaunchedEffect(url, focused) {
        if (!focused) value = TextFieldValue(AddressInput.display(url))
    }
    // Runs after the tap that focused the field has placed its cursor, so the full URL
    // replaces the short form and is selected, ready to be typed over.
    LaunchedEffect(focused) {
        if (focused) {
            val full = currentUrl.orEmpty()
            value = TextFieldValue(full, selection = TextRange(0, full.length))
        }
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConnectionIcon(url)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.browser_address_hint),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        onSubmit(value.text)
                        keyboard?.hide()
                        focusManager.clearFocus()
                    },
                ),
                modifier = Modifier.onFocusChanged { focused = it.isFocused },
            )
        }
        if (isLoading) {
            IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                Icon(SandboxIcons.Close, stringResource(R.string.browser_stop), Modifier.size(18.dp))
            }
        } else if (url != null) {
            IconButton(onClick = onReload, modifier = Modifier.size(40.dp)) {
                Icon(SandboxIcons.Refresh, stringResource(R.string.browser_reload), Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ConnectionIcon(url: String?) {
    val (icon, description, tint) = when {
        url == null -> Triple(SandboxIcons.Search, null, MaterialTheme.colorScheme.onSurfaceVariant)

        AddressInput.isSecure(url) -> Triple(
            SandboxIcons.Lock,
            stringResource(R.string.browser_secure),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> Triple(
            SandboxIcons.Warning,
            stringResource(R.string.browser_not_secure),
            SandboxTheme.semanticColors.caution,
        )
    }
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
}
