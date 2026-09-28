package com.example.nutrisocial.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

/**
 * Colores de los estados del calendario nutricional, que Material 3 no tiene: el "error" del
 * esquema no vale para el rojo porque haría falta además un verde y un azul equivalentes.
 */
@Immutable
data class NutritionStatusColors(
    val adequate: Color,
    val excess: Color,
    val insufficient: Color,
    // Texto sobre cualquiera de los tres.
    val onStatus: Color
)

private val LightStatusColors = NutritionStatusColors(StatusAdequateLight, StatusExcessLight, StatusInsufficientLight, OnStatusLight)
private val DarkStatusColors = NutritionStatusColors(StatusAdequateDark, StatusExcessDark, StatusInsufficientDark, OnStatusDark)

private val LocalNutritionStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Colores de estado del tema actual (claro u oscuro). */
val MaterialTheme.statusColors: NutritionStatusColors
    @Composable @ReadOnlyComposable get() = LocalNutritionStatusColors.current

// Sin color dinámico (Material You): la app mantiene siempre su propia identidad visual.
@Composable
fun NutriSocialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalNutritionStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
