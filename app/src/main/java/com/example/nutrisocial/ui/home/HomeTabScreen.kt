package com.example.nutrisocial.ui.home

import com.example.nutrisocial.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.FeedRecipe
import com.example.nutrisocial.ui.recipes.CenteredMessage
import com.example.nutrisocial.ui.recipes.InfoPill
import com.example.nutrisocial.ui.recipes.LikeButton
import com.example.nutrisocial.ui.recipes.LoadingBox
import com.example.nutrisocial.ui.recipes.RecipeThumbnail
import com.example.nutrisocial.ui.recipes.formatNumber
import com.example.nutrisocial.ui.recipes.prepTimeLabel
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardElevation
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/** Acciones del feed, agrupadas para no alargar la firma de la pantalla. */
data class FeedActions(
    val onRefresh: () -> Unit,
    val onLoadMore: () -> Unit,
    val onToggleLike: (Int) -> Unit,
    val onRecipeClick: (Int) -> Unit,
    val onCreateRecipe: () -> Unit,
    val onMessageShown: () -> Unit
) {
    companion object {
        val Noop = FeedActions({}, {}, {}, {}, {}, {})
    }
}

/** Pestaña "Inicio": el feed con las recetas de toda la comunidad, de la más reciente a la más antigua. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabScreen(
    userName: String,
    currentUserId: Int?,
    state: FeedUiState,
    actions: FeedActions
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onCreateRecipe,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nueva receta") },
                shape = ButtonShape,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Todo va dentro de una lista (también la carga y los errores) para que se pueda
            // tirar hacia abajo para refrescar en cualquier estado.
            LazyColumn(
                // Hueco inferior extra para que el FAB no tape la última tarjeta.
                contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.lg, bottom = Spacing.xl * 3),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "header") { FeedHeader(userName) }
                when {
                    state.isLoading -> item(key = "loading") { LoadingBox(Modifier.fillParentMaxSize(0.6f)) }

                    state.error != null -> item(key = "error") {
                        CenteredMessage(
                            icon = rememberVectorPainter(Icons.Filled.Warning),
                            isError = true,
                            title = "No se pudo cargar el inicio",
                            message = state.error,
                            actionLabel = "Reintentar",
                            onAction = actions.onRefresh,
                            modifier = Modifier.fillParentMaxSize(0.6f)
                        )
                    }

                    state.recipes.isEmpty() -> item(key = "empty") {
                        CenteredMessage(
                            icon = painterResource(R.drawable.ic_brand_plate),
                            title = "Todavía no hay recetas",
                            message = "Sé la primera persona en compartir una: pulsa «Nueva receta».",
                            modifier = Modifier.fillParentMaxSize(0.6f)
                        )
                    }

                    else -> {
                        items(state.recipes, key = { it.id }) { recipe ->
                            FeedRecipeCard(
                                recipe = recipe,
                                isOwn = recipe.authorId == currentUserId,
                                onClick = { actions.onRecipeClick(recipe.id) },
                                onToggleLike = { actions.onToggleLike(recipe.id) }
                            )
                        }
                        item(key = "footer") { FeedFooter(state = state, onLoadMore = actions.onLoadMore) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedHeader(userName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = if (userName.isBlank()) "Hola" else "Hola, $userName",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Lo último que ha cocinado la comunidad",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "Cargar más" si quedan recetas, o un cierre discreto al llegar al final. */
@Composable
private fun FeedFooter(state: FeedUiState, onLoadMore: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            state.isLoadingMore -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
            state.canLoadMore -> OutlinedButton(onClick = onLoadMore, shape = ButtonShape) { Text("Cargar más") }
            else -> Text(
                text = "No hay más recetas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Misma estructura que [com.example.nutrisocial.ui.recipes.RecipeCard], con autor y "me gusta". */
@Composable
private fun FeedRecipeCard(recipe: FeedRecipe, isOwn: Boolean, onClick: () -> Unit, onToggleLike: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecipeThumbnail(title = recipe.title, imageBase64 = recipe.imageBase64, size = 72.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isOwn) "Receta tuya" else "Por ${recipe.authorName.ifBlank { "otro usuario" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(top = Spacing.xs)
                ) {
                    if (recipe.kcalPerServing > 0) {
                        InfoPill(
                            text = "${formatNumber(recipe.kcalPerServing)} kcal/ración",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    recipe.prepMinutes?.let {
                        InfoPill(
                            text = prepTimeLabel(it),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            LikeButton(liked = recipe.likedByMe, count = recipe.likesCount, onToggle = onToggleLike)
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun HomeTabScreenPreview() {
    NutriSocialTheme {
        HomeTabScreen(
            userName = "Ana",
            currentUserId = 1,
            state = FeedUiState(
                isLoading = false,
                recipes = listOf(
                    FeedRecipe(3, "Tortilla de patatas", authorId = 2, authorName = "Luis", kcalPerServing = 410.0, prepMinutes = 40, likesCount = 5, likedByMe = true),
                    FeedRecipe(2, "Crema de calabaza", authorId = 1, authorName = "Ana", kcalPerServing = 140.0, likesCount = 2),
                    FeedRecipe(1, "Ensalada de garbanzos con atún y huevo duro", authorId = 3, authorName = "Marta", kcalPerServing = 380.0, prepMinutes = 15)
                ),
                nextCursor = 1
            ),
            actions = FeedActions.Noop
        )
    }
}

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun HomeTabEmptyPreview() {
    NutriSocialTheme {
        HomeTabScreen(userName = "Ana", currentUserId = 1, state = FeedUiState(isLoading = false), actions = FeedActions.Noop)
    }
}
