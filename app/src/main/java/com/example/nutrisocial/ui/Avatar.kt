package com.example.nutrisocial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nutrisocial.ui.theme.AmberContainer
import com.example.nutrisocial.ui.theme.AmberSecondary
import com.example.nutrisocial.ui.theme.EarthContainer
import com.example.nutrisocial.ui.theme.EarthTertiary
import com.example.nutrisocial.ui.theme.GreenContainer
import com.example.nutrisocial.ui.theme.GreenPrimary
import com.example.nutrisocial.ui.theme.OnAmberContainer
import com.example.nutrisocial.ui.theme.OnAmberSecondary
import com.example.nutrisocial.ui.theme.OnEarthContainer
import com.example.nutrisocial.ui.theme.OnEarthTertiary
import com.example.nutrisocial.ui.theme.OnGreenContainer
import com.example.nutrisocial.ui.theme.OnGreenPrimary

// La app no tiene fotos de perfil: cada usuario se representa con sus iniciales sobre un color
// de la paleta de NutriSocial. El color sale del nombre, así que es siempre el mismo para la
// misma persona (en el feed, en los comentarios y en cualquier móvil).

/** Parejas fondo/texto, todas con contraste suficiente para las iniciales. */
private val AvatarColors = listOf(
    GreenPrimary to OnGreenPrimary,
    AmberSecondary to OnAmberSecondary,
    EarthTertiary to OnEarthTertiary,
    GreenContainer to OnGreenContainer,
    AmberContainer to OnAmberContainer,
    EarthContainer to OnEarthContainer
)

/** "Ana" → "A", "Lucía Martín" → "LM", "  " → "?". Como mucho dos letras. */
fun avatarInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    return words.take(2).joinToString("") { it.first().uppercase() }
}

/**
 * Posición en la paleta a partir de un hash simple del nombre (el de String.hashCode, que
 * está definido por el lenguaje y no cambia entre ejecuciones ni dispositivos).
 */
fun avatarColorIndex(name: String, paletteSize: Int = AvatarColors.size): Int =
    Math.floorMod(name.trim().lowercase().hashCode(), paletteSize)

/** Círculo con las iniciales de [name]. Decorativo: el nombre siempre se lee al lado. */
@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val (background, content) = AvatarColors[avatarColorIndex(name)]
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(background, CircleShape)
            .clearAndSetSemantics { }
    ) {
        Text(
            text = avatarInitials(name),
            color = content,
            fontWeight = FontWeight.Bold,
            // Proporcional al círculo: legible tanto en el feed (40 dp) como en los comentarios.
            fontSize = (size.value * 0.4f).sp,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

