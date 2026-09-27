package com.example.nutrisocial.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Escala de espaciado de la app: todos los márgenes y separaciones salen de aquí. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

/**
 * Elevación única de todas las tarjetas: separa la tarjeta del fondo crema sin recargar.
 * Antes convivían 3 dp (recetas) y 1 dp (diario), y la despensa no tenía sombra.
 */
val CardElevation = 2.dp

/** Radio común de tarjetas y botones. */
val CornerRadius = 16.dp
val CardShape = RoundedCornerShape(CornerRadius)
val ButtonShape = RoundedCornerShape(CornerRadius)

// Los campos de texto usan extraSmall/small; tarjetas y diálogos, medium/large.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(CornerRadius),
    large = RoundedCornerShape(CornerRadius),
    extraLarge = RoundedCornerShape(24.dp)
)
