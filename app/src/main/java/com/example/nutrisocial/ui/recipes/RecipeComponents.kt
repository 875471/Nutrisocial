package com.example.nutrisocial.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.FoodSuggestion
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.data.RecipeIngredient
import com.example.nutrisocial.data.isOpenFoodFacts
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing
import kotlin.math.roundToInt

/** Elevación común de las tarjetas: suficiente para separarlas del fondo sin recargar. */
val RecipeCardElevation = 3.dp

/** "32 kcal/100 g", indicando si el dato viene de Open Food Facts en vez de BEDCA. */
fun suggestionDetail(food: FoodSuggestion): String =
    "${food.kcal.roundToInt()} kcal/100 g" + if (isOpenFoodFacts(food.source)) " · Open Food Facts" else ""

/**
 * Sugerencias del autocompletado de alimentos, dibujadas en línea bajo el campo para no quitarle
 * el foco. La usan el formulario de recetas y la despensa.
 */
@Composable
fun FoodSuggestionList(suggestions: List<FoodSuggestion>, onSelect: (FoodSuggestion) -> Unit) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
            suggestions.take(6).forEach { food ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(food) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = food.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = suggestionDetail(food),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.sm)
                    )
                }
            }
        }
    }
}

fun servingsLabel(servings: Int): String = if (servings == 1) "1 ración" else "$servings raciones"

/** Número sin decimales si es entero y con uno (y coma decimal) si no: 800, 2,5. */
fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(java.util.Locale("es", "ES"), "%.1f", value)

fun prepTimeLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

@Composable
fun RecipeCard(
    recipe: Recipe,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = RecipeCardElevation),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecipeThumbnail(title = recipe.title, imageBase64 = recipe.imageBase64)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                RecipeInfoRow(
                    servings = recipe.servings,
                    prepMinutes = recipe.prepMinutes,
                    kcalPerServing = recipe.nutrition?.perServing?.kcal
                )
            }
        }
    }
}

@Composable
fun RecipeInfoRow(servings: Int, prepMinutes: Int?, kcalPerServing: Double? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        InfoPill(
            text = servingsLabel(servings),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        if (prepMinutes != null) {
            InfoPill(
                text = prepTimeLabel(prepMinutes),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        if (kcalPerServing != null && kcalPerServing > 0) {
            InfoPill(
                text = "${formatNumber(kcalPerServing)} kcal/ración",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun InfoPill(text: String, containerColor: Color, contentColor: Color) {
    Surface(shape = CardShape, color = containerColor, contentColor = contentColor) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}

/** Cuadro con la inicial de la receta: da color a la tarjeta mientras no haya fotos. */
@Composable
fun InitialBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Mensaje centrado para estados vacíos o de error, con acción opcional. */
@Composable
fun CenteredMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null) {
            Button(
                onClick = onAction,
                shape = ButtonShape,
                modifier = Modifier.padding(top = Spacing.md)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecipeCardPreview() {
    NutriSocialTheme {
        RecipeCard(
            recipe = Recipe(1, "Ensalada de garbanzos", listOf(RecipeIngredient("Garbanzos")), listOf("Mezclar"), 2, 15, 1, ""),
            onClick = {},
            modifier = Modifier.padding(Spacing.md)
        )
    }
}
