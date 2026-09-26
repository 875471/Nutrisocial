package com.example.nutrisocial.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = OnGreenPrimary,
    primaryContainer = GreenContainer,
    onPrimaryContainer = OnGreenContainer,
    secondary = AmberSecondary,
    onSecondary = OnAmberSecondary,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = EarthTertiary,
    onTertiary = OnEarthTertiary,
    tertiaryContainer = EarthContainer,
    onTertiaryContainer = OnEarthContainer,
    background = CreamBackground,
    onBackground = OnCreamBackground,
    surface = CreamBackground,
    onSurface = OnCreamBackground,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = OnCreamSurfaceVariant,
    surfaceContainerLowest = CreamContainerLowest,
    surfaceContainerLow = CreamContainerLow,
    surfaceContainer = CreamContainer,
    surfaceContainerHigh = CreamContainerHigh,
    surfaceContainerHighest = CreamContainerHighest,
    outline = CreamOutline,
    outlineVariant = CreamOutlineVariant,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = OnGreenPrimaryDark,
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = OnGreenContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = OnAmberSecondaryDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiary = EarthTertiaryDark,
    onTertiary = OnEarthTertiaryDark,
    tertiaryContainer = EarthContainerDark,
    onTertiaryContainer = OnEarthContainerDark,
    background = SoilBackground,
    onBackground = OnSoilBackground,
    surface = SoilBackground,
    onSurface = OnSoilBackground,
    surfaceVariant = SoilSurfaceVariant,
    onSurfaceVariant = OnSoilSurfaceVariant,
    surfaceContainerLowest = SoilContainerLowest,
    surfaceContainerLow = SoilContainerLow,
    surfaceContainer = SoilContainer,
    surfaceContainerHigh = SoilContainerHigh,
    surfaceContainerHighest = SoilContainerHighest,
    outline = SoilOutline,
    outlineVariant = SoilOutlineVariant,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

// Sin color dinámico (Material You): la app mantiene siempre su propia identidad visual.
@Composable
fun NutriSocialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
