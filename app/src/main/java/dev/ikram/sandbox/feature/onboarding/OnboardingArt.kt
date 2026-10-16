package dev.ikram.sandbox.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme

/** The design's little landscape: a stand-in for any picture. Same colours in light and dark. */
@Composable
fun Landscape(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFFF2D9B8))
        drawCircle(Color(0xFFF6B26B), radius = w * 0.11f, center = Offset(w * 0.67f, h * 0.35f))
        val hills = Path().apply {
            moveTo(0f, h * 0.6f)
            lineTo(w * 0.3f, h * 0.44f)
            lineTo(w * 0.52f, h * 0.55f)
            lineTo(w * 0.76f, h * 0.41f)
            lineTo(w, h * 0.53f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(hills, Color(0xFF8FB3AA))
        drawRect(Color(0xFF5E8F8C), topLeft = Offset(0f, h * 0.7f))
    }
}

/** Page 1: a browser with a download button. */
@Composable
fun BrowseArt() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(250.dp)
            .shadow(12.dp, RoundedCornerShape(18.dp))
            .background(colors.surfaceContainerLowest, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(colors.surfaceContainerHigh, RoundedCornerShape(13.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(SandboxIcons.Search, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(12.dp))
            Text(
                stringResource(R.string.browser_address_hint),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant,
            )
        }
        Landscape(
            Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(0.8f, 1f, 0.64f).forEach { fraction ->
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(colors.outlineVariant, RoundedCornerShape(3.dp)),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(colors.surfaceContainer, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(SandboxIcons.Pdf, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(
                "trail-guide.pdf",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Badge(SandboxIcons.Download, colors.primary, colors.onPrimary, 26)
        }
    }
}

/** Page 2: the vault, with phone storage and other apps on the far side of a line. */
@Composable
fun VaultArt() {
    val colors = MaterialTheme.colorScheme
    val semantic = SandboxTheme.semanticColors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(contentAlignment = Alignment.TopCenter) {
            Row(
                modifier = Modifier
                    .padding(top = 22.dp)
                    .width(224.dp)
                    .border(2.dp, colors.primary, RoundedCornerShape(26.dp))
                    .background(colors.background, RoundedCornerShape(26.dp))
                    .padding(start = 18.dp, end = 18.dp, top = 34.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val tile = Modifier
                    .weight(1f)
                    .height(62.dp)
                    .clip(RoundedCornerShape(12.dp))
                Landscape(tile)
                Tile(tile.background(colors.surfaceContainerHigh), SandboxIcons.Pdf, colors.onSurfaceVariant)
                Tile(tile.background(semantic.blockedContainer), SandboxIcons.Blocked, semantic.onBlockedContainer)
            }
            Badge(SandboxIcons.Lock, colors.primary, colors.onPrimary, 44)
        }
        DashedLine(Modifier.width(260.dp), colors.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.alpha(OUTSIDE_ALPHA)) {
            Pill(SandboxIcons.Folder, stringResource(R.string.onboarding_phone_storage))
            Pill(SandboxIcons.GridView, stringResource(R.string.onboarding_other_apps))
        }
    }
}

/** Page 3: the original with its hidden data, and the clean safe copy. */
@Composable
fun ShareArt() {
    val colors = MaterialTheme.colorScheme
    val semantic = SandboxTheme.semanticColors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    // The same room the safe copy's outline takes, so the labels line up.
                    .padding(OUTLINE_SPACE)
                    .size(108.dp, 132.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                Landscape(Modifier.fillMaxSize().alpha(ORIGINAL_ALPHA))
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(R.string.onboarding_location, R.string.onboarding_hidden_data).forEach { label ->
                        Text(
                            stringResource(label),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = semantic.onCautionContainer,
                            modifier = Modifier
                                .background(semantic.cautionContainer, RoundedCornerShape(9.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.onboarding_original),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .padding(bottom = 26.dp)
                .size(40.dp)
                .background(colors.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SandboxIcons.Forward, null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .border(2.dp, colors.primary, RoundedCornerShape(19.dp))
                    .padding(5.dp)
                    .size(108.dp, 132.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                Landscape(Modifier.fillMaxSize())
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) { Badge(SandboxIcons.Check, colors.primary, colors.onPrimary, 28) }
            }
            Text(
                stringResource(R.string.onboarding_safe_copy),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
            )
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, icon: ImageVector, tint: Color) {
    Box(modifier, contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
}

@Composable
private fun Badge(icon: ImageVector, container: Color, content: Color, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
private fun Pill(icon: ImageVector, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .height(32.dp)
            .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
    }
}

@Composable
fun DashedLine(modifier: Modifier, color: Color) {
    Canvas(modifier.height(2.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
        )
    }
}

private const val OUTSIDE_ALPHA = 0.8f
private const val ORIGINAL_ALPHA = 0.9f
private const val DASH = 12f
private val OUTLINE_SPACE = 7.dp
