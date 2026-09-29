package com.example.nutrisocial.ui.recipes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.example.nutrisocial.R
import androidx.compose.ui.tooling.preview.Preview
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/** Segmentos de las recetas del perfil. */
enum class RecipesTab(val label: String) {
    MINE("Mis recetas"),
    SAVED("Guardadas")
}

/**
 * Pestaña Perfil: [header] con los datos del usuario y, debajo, sus dos listas personales de
 * recetas: las propias ("Mis recetas") y las que ha guardado de cualquier autor ("Guardadas").
 * El contenido de "Guardadas" lo pone quien llama, con su propio ViewModel; recibe el
 * [SnackbarHostState] de la pantalla para sus avisos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    state: RecipeListUiState,
    onRecipeClick: (Int) -> Unit,
    onCreateRecipe: () -> Unit,
    onRetry: () -> Unit,
    onScanRecipe: () -> Unit = {},
    // Aviso puntual, p. ej. tras eliminar una receta; se muestra en un snackbar.
    message: String? = null,
    onMessageShown: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    selectedTab: RecipesTab = RecipesTab.MINE,
    onTabSelected: (RecipesTab) -> Unit = {},
    savedContent: @Composable (SnackbarHostState) -> Unit = {},
    header: @Composable () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                actions = {
                    // Como el botón de crear: escanear es cosa de "Mis recetas".
                    if (selectedTab == RecipesTab.MINE) TextButton(onClick = onScanRecipe) {
                        Icon(
                            painterResource(R.drawable.ic_photo_camera),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Escanear", modifier = Modifier.padding(start = Spacing.sm))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            // Crear es cosa de "Mis recetas"; en los demás segmentos taparía los resultados.
            if (selectedTab == RecipesTab.MINE) ExtendedFloatingActionButton(
                onClick = onCreateRecipe,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nueva receta") },
                shape = ButtonShape,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            header()
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                RecipesTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(tab.label, maxLines = 1) }
                    )
                }
            }
            when (selectedTab) {
                // Como en el inicio: tirar hacia abajo recarga la lista, también desde un error o vacía.
                RecipesTab.MINE -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    RecipeListContent(state = state, onRecipeClick = onRecipeClick, onRetry = onRetry)
                }
                RecipesTab.SAVED -> savedContent(snackbarHostState)
            }
        }
    }
}

@Composable
private fun RecipeListContent(state: RecipeListUiState, onRecipeClick: (Int) -> Unit, onRetry: () -> Unit) {
    when (state) {
        RecipeListUiState.Loading -> PullableFullScreen { LoadingBox(it) }

        is RecipeListUiState.Error -> PullableFullScreen {
            CenteredMessage(
                icon = rememberVectorPainter(Icons.Filled.Warning),
                isError = true,
                title = "No se pudieron cargar tus recetas",
                message = state.message,
                actionLabel = "Reintentar",
                onAction = onRetry,
                modifier = it
            )
        }

        is RecipeListUiState.Success -> if (state.recipes.isEmpty()) {
            PullableFullScreen {
                CenteredMessage(
                    icon = painterResource(R.drawable.ic_brand_plate),
                    title = "Todavía no tienes recetas",
                    message = "Pulsa «Nueva receta» para guardar la primera, o «Escanear» para " +
                        "pasar una receta en papel con la cámara.",
                    modifier = it
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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

/**
 * Estado a pantalla completa (carga, error, vacío) dentro de una lista de un solo elemento:
 * PullToRefreshBox solo reacciona a contenido desplazable, y así se puede tirar también de ellos.
 */
@Composable
private fun PullableFullScreen(content: @Composable (Modifier) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { content(Modifier.fillParentMaxSize()) }
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
