package com.example.nutrisocial.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

/**
 * Jerarquía tipográfica de la app, de más a menos peso visual:
 * - display / headline: la cifra o el título principal de una pantalla (kcal del día, título
 *   de la receta, saludo del inicio). Negrita.
 * - title: títulos de tarjeta y de sección. Seminegrita; titleSmall en media, para los
 *   elementos de lista (entradas del diario) que no deben competir con el título de su sección.
 * - body: texto corrido, en peso normal. bodySmall para lo secundario (autor, avisos).
 * - label: pastillas, botones y contadores. Seminegrita en los botones, media en el resto.
 * El contraste viene del peso además del tamaño: un título y un texto del mismo tamaño se
 * distinguen igualmente.
 */
val Typography = Typography(
    displaySmall = Default.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = Default.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = Default.bodyMedium.copy(lineHeight = 21.sp),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontWeight = FontWeight.Medium)
)
