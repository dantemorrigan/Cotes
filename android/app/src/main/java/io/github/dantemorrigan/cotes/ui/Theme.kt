package io.github.dantemorrigan.cotes.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Цвета — из replica/design/tokens.json и tokens.dark.json (те же, что у веб-версии).

private val Light = lightColorScheme(
    primary = Color(0xFF6A3FB5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6D0FF),
    onPrimaryContainer = Color(0xFF1C1A21),
    secondaryContainer = Color(0xFFEBE3F7),
    onSecondaryContainer = Color(0xFF1C1A21),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1C1A21),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1C1A21),
    surfaceVariant = Color(0xFFEBE3F7),
    onSurfaceVariant = Color(0xFF625B6D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F1FB),
    surfaceContainer = Color(0xFFF5F1FB),
    surfaceContainerHigh = Color(0xFFEBE3F7),
    surfaceContainerHighest = Color(0xFFE4DCEF),
    inverseSurface = Color(0xFF1C1A21),
    inverseOnSurface = Color(0xFFFDFBFF),
    inversePrimary = Color(0xFFC7A6FF),
    outline = Color(0xFF857C94),
    outlineVariant = Color(0xFFE4DCEF),
    error = Color(0xFFB8322B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC7A6FF),
    onPrimary = Color(0xFF1A1420),
    primaryContainer = Color(0xFF3B2D55),
    onPrimaryContainer = Color(0xFFECE8F2),
    secondaryContainer = Color(0xFF2A2532),
    onSecondaryContainer = Color(0xFFECE8F2),
    background = Color(0xFF16141A),
    onBackground = Color(0xFFECE8F2),
    surface = Color(0xFF16141A),
    onSurface = Color(0xFFECE8F2),
    surfaceVariant = Color(0xFF2A2532),
    onSurfaceVariant = Color(0xFFA8A1B4),
    surfaceContainerLowest = Color(0xFF110F14),
    surfaceContainerLow = Color(0xFF1D1A23),
    surfaceContainer = Color(0xFF1D1A23),
    surfaceContainerHigh = Color(0xFF2A2532),
    surfaceContainerHighest = Color(0xFF322C3B),
    inverseSurface = Color(0xFFECE8F2),
    inverseOnSurface = Color(0xFF16141A),
    inversePrimary = Color(0xFF6A3FB5),
    outline = Color(0xFF7F778C),
    outlineVariant = Color(0xFF322C3B),
    error = Color(0xFFFF8A80),
)

@Immutable
data class CotesExtraColors(val mark: Color)

val LocalCotesColors = staticCompositionLocalOf { CotesExtraColors(mark = Color(0xFFA0F0ED)) }

@Composable
fun CotesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalCotesColors provides CotesExtraColors(mark = if (dark) Color(0xFF1D5552) else Color(0xFFA0F0ED))
    ) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
