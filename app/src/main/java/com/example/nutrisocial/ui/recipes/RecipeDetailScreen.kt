package com.example.nutrisocial.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    state: RecipeDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (state) {
            RecipeDetailUiState.Loading -> LoadingBox(contentModifier)
            is RecipeDetailUiState.Error -> CenteredMessage(
                title = "No se pudo cargar la receta",
                message = state.message,
                actionLabel = "Reintentar",
                onAction = onRetry,
                modifier = contentModifier
            )
            is RecipeDetailUiState.Success -> RecipeDetailContent(state.recipe, contentModifier)
        }
    }
}

@Composable
private fun RecipeDetailContent(recipe: Recipe, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = recipe.title, style = MaterialTheme.typography.headlineMedium)
            RecipeInfoRow(servings = recipe.servings, prepMinutes = recipe.prepMinutes)
        }

        DetailSection(title = "Ingredientes") {
            recipe.ingredients.forEach { ingredient ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(Spacing.sm)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Text(
                        text = ingredient,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = Spacing.md)
                    )
                }
            }
        }

        DetailSection(title = "Preparación") {
            recipe.steps.forEachIndexed { index, step ->
                Row {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(Spacing.xl)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "${index + 1}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Spacing.md, top = Spacing.xs)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = RecipeCardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun RecipeDetailScreenPreview() {
    NutriSocialTheme {
        RecipeDetailScreen(
            state = RecipeDetailUiState.Success(
                Recipe(
                    id = 1,
                    title = "Crema de calabaza",
                    ingredients = listOf("1 calabaza mediana", "1 cebolla", "Aceite de oliva", "Sal y pimienta"),
                    steps = listOf(
                        "Pelar y trocear la calabaza y la cebolla.",
                        "Pochar la cebolla con un poco de aceite.",
                        "Añadir la calabaza, cubrir de agua y cocer 20 minutos. Triturar."
                    ),
                    servings = 4,
                    prepMinutes = 35,
                    authorId = 1,
                    createdAt = ""
                )
            ),
            onBack = {},
            onRetry = {}
        )
    }
}
