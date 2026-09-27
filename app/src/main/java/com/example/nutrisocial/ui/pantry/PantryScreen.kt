package com.example.nutrisocial.ui.pantry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.FoodRef
import com.example.nutrisocial.data.FoodSuggestion
import com.example.nutrisocial.data.PantryItem
import com.example.nutrisocial.data.PantryRecipeMatch
import com.example.nutrisocial.data.PantrySearchResult
import com.example.nutrisocial.ui.recipes.FoodSuggestionList
import com.example.nutrisocial.ui.recipes.InfoPill
import com.example.nutrisocial.ui.recipes.RecipeCardElevation
import com.example.nutrisocial.ui.recipes.RecipeThumbnail
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/** Acciones de la pantalla, agrupadas para no alargar su firma. */
data class PantryActions(
    val onQueryChange: (String) -> Unit,
    val onSuggestionSelected: (FoodSuggestion) -> Unit,
    val onDismissSuggestions: () -> Unit,
    val onAdd: () -> Unit,
    val onDelete: (PantryItem) -> Unit,
    val onRetryPantry: () -> Unit,
    val onSearchRecipes: () -> Unit,
    val onRecipeClick: (Int) -> Unit,
    val onMessageShown: () -> Unit
) {
    companion object {
        val Noop = PantryActions({}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(
    itemsState: PantryItemsState,
    input: PantryInputState,
    searchState: PantrySearchState,
    message: String?,
    actions: PantryActions
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi despensa") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item(key = "input") { AddIngredientField(input = input, actions = actions) }
            item(key = "items") { PantryItemsCard(state = itemsState, actions = actions) }
            item(key = "search") {
                val hasItems = (itemsState as? PantryItemsState.Success)?.items?.isNotEmpty() == true
                Button(
                    onClick = actions.onSearchRecipes,
                    enabled = hasItems && searchState != PantrySearchState.Loading,
                    shape = ButtonShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Buscar recetas", modifier = Modifier.padding(start = Spacing.sm))
                }
            }
            searchResults(state = searchState, actions = actions)
        }
    }
}

@Composable
private fun AddIngredientField(input: PantryInputState, actions: PantryActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Apunta lo que tienes en casa y te diremos qué recetas de NutriSocial puedes cocinar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = input.query,
            onValueChange = actions.onQueryChange,
            label = { Text("Añadir ingrediente") },
            singleLine = true,
            enabled = !input.isAdding,
            isError = input.error != null,
            supportingText = input.error?.let { { Text(it) } },
            trailingIcon = {
                if (input.isAdding) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = actions.onAdd, enabled = input.query.isNotBlank()) {
                        Icon(Icons.Filled.Add, contentDescription = "Añadir a la despensa")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { actions.onAdd() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) actions.onDismissSuggestions() }
        )
        if (input.suggestions.isNotEmpty()) {
            FoodSuggestionList(suggestions = input.suggestions, onSelect = actions.onSuggestionSelected)
        }
    }
}

@Composable
private fun PantryItemsCard(state: PantryItemsState, actions: PantryActions) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
            val title = when (state) {
                is PantryItemsState.Success -> "En tu despensa (${state.items.size})"
                else -> "En tu despensa"
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
            when (state) {
                PantryItemsState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is PantryItemsState.Error -> Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = actions.onRetryPantry, shape = ButtonShape) { Text("Reintentar") }
                }

                is PantryItemsState.Success -> if (state.items.isEmpty()) {
                    Text(
                        text = "Aún no has añadido nada. Empieza por lo básico: huevos, aceite, sal…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    )
                } else {
                    state.items.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                        PantryItemRow(item = item, onDelete = { actions.onDelete(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PantryItemRow(item: PantryItem, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
            // Si el servidor lo ha asociado a un alimento con otro nombre, se indica cuál.
            item.food?.takeIf { !it.name.equals(item.name, ignoreCase = true) }?.let { food ->
                Text(
                    text = "Reconocido como «${food.name}»",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (item.food != null) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Alimento reconocido",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Quitar ${item.name}")
        }
    }
}

private fun LazyListScope.searchResults(state: PantrySearchState, actions: PantryActions) {
    when (state) {
        PantrySearchState.Idle -> Unit

        PantrySearchState.Loading -> item(key = "search-loading") {
            Box(modifier = Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is PantrySearchState.Error -> item(key = "search-error") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("No se pudieron buscar recetas", style = MaterialTheme.typography.titleMedium)
                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = actions.onSearchRecipes, shape = ButtonShape) { Text("Reintentar") }
            }
        }

        is PantrySearchState.Success -> {
            val result = state.result
            if (result.readyToCook.isEmpty() && result.almostReady.isEmpty()) {
                item(key = "search-empty") {
                    Text(
                        text = "Ninguna receta tiene al menos la mitad de sus ingredientes en tu despensa. " +
                            "Prueba a añadir algún ingrediente más.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            resultSection("ready", "Puedes cocinar ya", result.readyToCook, actions.onRecipeClick)
            resultSection("almost", "Te faltan pocos ingredientes", result.almostReady, actions.onRecipeClick)
        }
    }
}

private fun LazyListScope.resultSection(
    key: String,
    title: String,
    recipes: List<PantryRecipeMatch>,
    onRecipeClick: (Int) -> Unit
) {
    if (recipes.isEmpty()) return
    item(key = "$key-title") {
        Text(
            text = "$title (${recipes.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.sm)
        )
    }
    items(recipes, key = { "$key-${it.id}" }) { recipe ->
        PantryRecipeCard(recipe = recipe, onClick = { onRecipeClick(recipe.id) })
    }
}

@Composable
private fun PantryRecipeCard(recipe: PantryRecipeMatch, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = RecipeCardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            RecipeThumbnail(title = recipe.title, imageBase64 = recipe.imageBase64)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Column {
                    Text(
                        text = recipe.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (recipe.authorName.isNotBlank()) {
                        Text(
                            text = "Por ${recipe.authorName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val complete = recipe.missingIngredients.isEmpty()
                InfoPill(
                    text = "Tienes ${recipe.matchedCount} de ${recipe.totalIngredients} ingredientes",
                    containerColor = if (complete) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (complete) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (!complete) {
                    Text(
                        text = "Te falta: " + recipe.missingIngredients.joinToString(", ") { it.lowercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PantryScreenPreview() {
    NutriSocialTheme {
        PantryScreen(
            itemsState = PantryItemsState.Success(
                listOf(
                    PantryItem(1, "Huevos", FoodRef(10, "Huevo de gallina fresco")),
                    PantryItem(2, "Patatas", FoodRef(20, "Patata, cruda")),
                    PantryItem(3, "Sal")
                )
            ),
            input = PantryInputState(query = "Ceb"),
            searchState = PantrySearchState.Success(
                PantrySearchResult(
                    pantrySize = 3,
                    readyToCook = listOf(PantryRecipeMatch(1, "Huevos fritos con patatas", 3, 3, emptyList(), 1.0)),
                    almostReady = listOf(
                        PantryRecipeMatch(2, "Tortilla de patatas", 4, 3, listOf("Aceite de oliva"), 0.75),
                        PantryRecipeMatch(3, "Patatas a la importancia", 6, 3, listOf("Harina", "Ajo", "Perejil"), 0.5)
                    )
                )
            ),
            message = null,
            actions = PantryActions.Noop
        )
    }
}
