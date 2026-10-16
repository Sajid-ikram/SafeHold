package dev.ikram.sandbox.feature.explainer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.SandboxTopBar
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.feature.onboarding.DashedLine
import dev.ikram.sandbox.feature.vault.components.BadgeTone
import dev.ikram.sandbox.feature.vault.components.StatusBadge

@Composable
fun HowProtectionWorksRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    HowProtectionWorksScreen(onBack = onBack, modifier = modifier)
}

@Composable
fun HowProtectionWorksScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        SandboxTopBar(
            title = stringResource(R.string.explainer_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back),
        )
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
        ) {
            Diagram(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp))
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Point(1, R.string.explainer_point_storage_title, R.string.explainer_point_storage)
                Point(2, R.string.explainer_point_type_title, R.string.explainer_point_type)
                Point(3, R.string.explainer_point_viewers_title, R.string.explainer_point_viewers)
                Point(4, R.string.explainer_point_copies_title, R.string.explainer_point_copies)
                Honesty()
            }
        }
    }
}

/** Website, download, the walled-off inside of SafeHold, and the rest of the phone it cannot reach. */
@Composable
private fun Diagram(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .height(52.dp)
                .background(colors.surfaceContainerHigh, RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(SandboxIcons.Browser, null, tint = colors.onSurfaceVariant)
            Text(stringResource(R.string.explainer_website), style = MaterialTheme.typography.titleSmall)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 2.dp)) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(colors.primary),
            )
            Icon(SandboxIcons.ChevronDown, null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.explainer_download),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
            )
        }
        Inside(Modifier.padding(top = 6.dp))
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DashedLine(Modifier.weight(1f), colors.outline)
            StatusBadge(stringResource(R.string.explainer_no_way_through), SandboxIcons.Blocked, BadgeTone.BLOCKED)
            DashedLine(Modifier.weight(1f), colors.outline)
        }
        Outside()
    }
}

@Composable
private fun Inside(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, colors.primary, RoundedCornerShape(24.dp))
            .background(colors.primary.copy(alpha = INSIDE_TINT), RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(SandboxIcons.ShieldCheck, null, tint = colors.primary, modifier = Modifier.size(14.dp))
            Text(
                stringResource(R.string.explainer_inside),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
            )
        }
        // Both cards as tall as the taller one.
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InsideCard(SandboxIcons.Lock, R.string.explainer_vault, Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        colors.surfaceContainerHighest,
                        colors.surfaceContainerHighest,
                        SandboxTheme.semanticColors.blockedContainer,
                    )
                        .forEach { tint ->
                            Box(
                                Modifier
                                    .size(22.dp, 26.dp)
                                    .background(tint, RoundedCornerShape(5.dp)),
                            )
                        }
                }
            }
            Icon(SandboxIcons.Forward, null, tint = colors.primary, modifier = Modifier.size(18.dp))
            InsideCard(SandboxIcons.Open, R.string.explainer_viewer, Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.explainer_viewer_note),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InsideCard(icon: ImageVector, title: Int, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        }
        content()
    }
}

@Composable
private fun Outside() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.explainer_rest_of_phone),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutsideTile(SandboxIcons.GridView, R.string.onboarding_other_apps, Modifier.weight(1f))
            OutsideTile(SandboxIcons.Folder, R.string.explainer_shared_storage, Modifier.weight(1f))
            OutsideTile(SandboxIcons.Download, R.string.explainer_app_installer, Modifier.weight(1f))
        }
    }
}

@Composable
private fun OutsideTile(icon: ImageVector, label: Int, modifier: Modifier) {
    Column(
        modifier = modifier
            .alpha(OUTSIDE_ALPHA)
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(label),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Point(number: Int, title: Int, body: Int) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(24.dp)
                .background(colors.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onPrimaryContainer,
            )
        }
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(title)) }
                append(' ')
                append(stringResource(body))
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** What SafeHold does not do. The protection is about files, never about the web itself. */
@Composable
private fun Honesty() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(SandboxIcons.Info, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(stringResource(R.string.explainer_browsing_title))
                }
                append(' ')
                append(stringResource(R.string.explainer_browsing))
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

private const val INSIDE_TINT = 0.06f
private const val OUTSIDE_ALPHA = 0.85f

@Preview(showBackground = true)
@Composable
private fun HowProtectionWorksScreenPreview() {
    SandboxTheme { HowProtectionWorksScreen(onBack = {}) }
}
