package com.example.nutrisocial.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    state: RecipeListUiState,
    onRecipeClick: (Int) -> Unit,
    onCreateRecipe: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis recetas") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateRecipe,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nueva receta") },
                shape = ButtonShape,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (state) {
            RecipeListUiState.Loading -> LoadingBox(contentModifier)

            is RecipeListUiState.Error -> CenteredMessage(
                title = "No se pudieron cargar tus recetas",
                message = state.message,
                actionLabel = "Reintentar",
                onAction = onRetry,
                modifier = contentModifier
            )

            is RecipeListUiState.Success -> if (state.recipes.isEmpty()) {
                CenteredMessage(
                    title = "Todavía no tienes recetas",
                    message = "Pulsa «Nueva receta» para guardar la primera.",
                    modifier = contentModifier
                )
            } else {
                LazyColumn(
                    modifier = contentModifier,
                    // Hueco inferior extra para que el FAB no tape la última tarjeta.
                    contentPadding = PaddingValues(
                        start = Spacing.md,
                        end = Spacing.md,
                        top = Spacing.sm,
                        bottom = Spacing.xl * 3
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    items(state.recipes, key = { it.id }) { recipe ->
                        RecipeCard(recipe = recipe, onClick = { onRecipeClick(recipe.id) })
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecipeListScreenPreview() {
    NutriSocialTheme {
        RecipeListScreen(
            state = RecipeListUiState.Success(
                listOf(
                    Recipe(1, "Ensalada de garbanzos", emptyList(), emptyList(), 2, 15, 1, ""),
                    Recipe(2, "Crema de calabaza", emptyList(), emptyList(), 4, 45, 1, ""),
                    Recipe(3, "Tostada de aguacate", emptyList(), emptyList(), 1, null, 1, "")
                )
            ),
            onRecipeClick = {},
            onCreateRecipe = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecipeListEmptyPreview() {
    NutriSocialTheme {
        RecipeListScreen(RecipeListUiState.Success(emptyList()), {}, {}, {})
    }
}
