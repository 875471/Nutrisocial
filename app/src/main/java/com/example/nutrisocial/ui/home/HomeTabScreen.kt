package com.example.nutrisocial.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.ui.recipes.RecipeCardElevation
import com.example.nutrisocial.ui.recipes.RecipeListUiState
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/** Pestaña "Inicio": saludo y accesos rápidos. */
@Composable
fun HomeTabScreen(
    userName: String,
    recipesState: RecipeListUiState,
    onOpenRecipes: () -> Unit,
    onCreateRecipe: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Bienvenido, ${userName.ifBlank { "usuario" }}",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "¿Qué cocinamos hoy?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            QuickAccessCard(
                title = "Mis recetas",
                description = recipesSummary(recipesState),
                buttonLabel = "Ver mis recetas",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onOpenRecipes
            )

            QuickAccessCard(
                title = "¿Tienes una idea nueva?",
                description = "Apunta los ingredientes y los pasos antes de que se te olviden.",
                buttonLabel = "Crear receta",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onCreateRecipe,
                showAddIcon = true
            )
        }
    }
}

private fun recipesSummary(state: RecipeListUiState): String = when (state) {
    RecipeListUiState.Loading -> "Cargando tus recetas…"
    is RecipeListUiState.Error -> "No se han podido cargar tus recetas."
    is RecipeListUiState.Success -> when (val count = state.recipes.size) {
        0 -> "Aún no has guardado ninguna receta."
        1 -> "Tienes 1 receta guardada."
        else -> "Tienes $count recetas guardadas."
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    description: String,
    buttonLabel: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    showAddIcon: Boolean = false
) {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = RecipeCardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onClick,
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = contentColor,
                    contentColor = containerColor
                ),
                modifier = Modifier.padding(top = Spacing.sm)
            ) {
                if (showAddIcon) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(buttonLabel, modifier = Modifier.padding(start = Spacing.sm))
                } else {
                    Text(buttonLabel)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeTabScreenPreview() {
    NutriSocialTheme {
        HomeTabScreen(
            userName = "Ana",
            recipesState = RecipeListUiState.Success(emptyList()),
            onOpenRecipes = {},
            onCreateRecipe = {}
        )
    }
}
