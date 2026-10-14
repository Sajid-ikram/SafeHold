package dev.ikram.sandbox.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours that carry meaning: teal for safe, amber for caution, red only for blocked.
 * Kept out of the Material colour scheme so nothing can remap them.
 */
@Immutable
data class SemanticColors(
    val safe: Color,
    val safeContainer: Color,
    val onSafeContainer: Color,
    val caution: Color,
    val cautionContainer: Color,
    val onCautionContainer: Color,
    val blocked: Color,
    val blockedContainer: Color,
    val onBlockedContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
)

internal val LightSemanticColors = SemanticColors(
    safe = Color(0xFF0B6B62),
    safeContainer = Color(0xFFB4EFE4),
    onSafeContainer = Color(0xFF00201C),
    caution = Color(0xFF7A4A00),
    cautionContainer = Color(0xFFFFDDB3),
    onCautionContainer = Color(0xFF2A1800),
    blocked = Color(0xFFB3261E),
    blockedContainer = Color(0xFFFFDAD6),
    onBlockedContainer = Color(0xFF410002),
    neutral = Color(0xFF6F7976),
    neutralContainer = Color(0xFFD9E1DE),
    onNeutralContainer = Color(0xFF3F4946),
)

internal val DarkSemanticColors = SemanticColors(
    safe = Color(0xFF83D5C7),
    safeContainer = Color(0xFF005048),
    onSafeContainer = Color(0xFFA5F2E3),
    caution = Color(0xFFFFB95A),
    cautionContainer = Color(0xFF5E3800),
    onCautionContainer = Color(0xFFFFDDB3),
    blocked = Color(0xFFFFB4AB),
    blockedContainer = Color(0xFF93000A),
    onBlockedContainer = Color(0xFFFFDAD6),
    neutral = Color(0xFF899390),
    neutralContainer = Color(0xFF2F3635),
    onNeutralContainer = Color(0xFFBFC9C5),
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }
