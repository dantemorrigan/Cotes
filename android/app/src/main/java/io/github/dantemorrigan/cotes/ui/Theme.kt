package io.github.dantemorrigan.cotes.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import io.github.dantemorrigan.cotes.Accent
import io.github.dantemorrigan.cotes.AppSettings

// Фирменные цвета Cotes.

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

/** Схема для акцента: фон и поверхности слегка подкрашены выбранным цветом. */
private fun accentScheme(accent: Accent, dark: Boolean, pureBlack: Boolean): ColorScheme {
    if (accent == Accent.LAVENDER && !pureBlack) return if (dark) Dark else Light
    if (!dark) {
        val a = Color(accent.light)
        fun t(f: Float) = lerp(Color.White, a, f)
        return Light.copy(
            primary = a,
            onPrimary = Color.White,
            primaryContainer = t(0.22f),
            onPrimaryContainer = Color(0xFF1C1B1F),
            secondaryContainer = t(0.12f),
            onSecondaryContainer = Color(0xFF1C1B1F),
            background = t(0.015f),
            surface = t(0.015f),
            surfaceVariant = t(0.12f),
            onSurfaceVariant = Color(0xFF5E5C66),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = t(0.045f),
            surfaceContainer = t(0.045f),
            surfaceContainerHigh = t(0.10f),
            surfaceContainerHighest = t(0.14f),
            outlineVariant = t(0.16f),
            inversePrimary = Color(accent.dark),
        )
    }
    val a = Color(accent.dark)
    val base = if (pureBlack) Color.Black else Color(0xFF141416)
    fun t(f: Float) = lerp(base, a, f)
    return Dark.copy(
        primary = a,
        onPrimary = Color(0xFF16141A),
        primaryContainer = t(0.28f),
        onPrimaryContainer = Color(0xFFECE8F2),
        secondaryContainer = t(0.16f),
        onSecondaryContainer = Color(0xFFECE8F2),
        background = base,
        surface = base,
        surfaceVariant = t(0.16f),
        onSurfaceVariant = Color(0xFFA9A6B2),
        surfaceContainerLowest = base,
        surfaceContainerLow = t(0.05f),
        surfaceContainer = t(0.05f),
        surfaceContainerHigh = t(0.12f),
        surfaceContainerHighest = t(0.18f),
        outlineVariant = t(0.20f),
        inversePrimary = Color(accent.light),
    )
}

@Immutable
data class CotesExtraColors(val mark: Color)

val LocalCotesColors = staticCompositionLocalOf { CotesExtraColors(mark = Color(0xFFA0F0ED)) }
val LocalAppSettings = compositionLocalOf { AppSettings() }

val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun CotesTheme(settings: AppSettings, dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var scheme = if (settings.dynamicColor && dynamicColorAvailable) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        accentScheme(settings.accent, dark, settings.pureBlack)
    }
    if (dark && settings.pureBlack && settings.dynamicColor && dynamicColorAvailable) {
        scheme = scheme.copy(background = Color.Black, surface = Color.Black)
    }
    CompositionLocalProvider(
        LocalCotesColors provides CotesExtraColors(mark = if (dark) Color(0xFF1D5552) else Color(0xFFA0F0ED)),
        LocalAppSettings provides settings,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
    // Окно под интерфейсом — того же цвета, что и тема приложения (не системы): без светлых вспышек
    // за статус-баром и при смене темы.
    val window = context.findActivity()?.window
    val windowColor = scheme.background.toArgb()
    SideEffect { window?.setBackgroundDrawable(ColorDrawable(windowColor)) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
