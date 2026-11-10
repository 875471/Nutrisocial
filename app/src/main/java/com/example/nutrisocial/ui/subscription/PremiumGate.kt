package com.example.nutrisocial.ui.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardElevation
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/**
 * Pinta [content] si el usuario es Premium y, si no, una tarjeta "Función Premium" con
 * [description] y un botón a la pantalla de planes. La función se ve bloqueada, no se esconde.
 * [isPremium] null (todavía se está comprobando) muestra un indicador de carga, para que a un
 * usuario Premium no le aparezca un momento la tarjeta bloqueada.
 */
@Composable
fun PremiumGate(
    isPremium: Boolean?,
    onUpgrade: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when (isPremium) {
        true -> content()
        false -> PremiumLockedCard(description = description, onUpgrade = onUpgrade, modifier = modifier)
        null -> Box(modifier = modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun PremiumLockedCard(description: String, onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CardElevation),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = "Función Premium",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = Spacing.sm)
                )
            }
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onUpgrade, shape = ButtonShape) { Text("Ver planes Premium") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PremiumGatePreview() {
    NutriSocialTheme {
        PremiumGate(
            isPremium = false,
            onUpgrade = {},
            description = "Recibe recetas que encajan con las kcal y los macros que te quedan cada día.",
            modifier = Modifier.padding(Spacing.md)
        ) {}
    }
}
