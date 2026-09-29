package com.example.nutrisocial.ui.home

import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.CommentPreview
import com.example.nutrisocial.data.FeedRecipe
import com.example.nutrisocial.ui.recipes.RecipeCardActions
import com.example.nutrisocial.ui.recipes.RecipeFeedCard
import com.example.nutrisocial.ui.recipes.toCardData
import com.example.nutrisocial.ui.recipes.CenteredMessage
import com.example.nutrisocial.ui.recipes.LoadingBox
import com.example.nutrisocial.ui.recipes.listStateModifier
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/** Acciones del feed, agrupadas para no alargar la firma de la pantalla. */
data class FeedActions(
    val onRefresh: () -> Unit,
    val onLoadMore: () -> Unit,
    val onToggleLike: (Int) -> Unit,
    val onRecipeClick: (Int) -> Unit,
    val onCreateRecipe: () -> Unit,
    val onMessageShown: () -> Unit,
    val onOpenComments: (Int) -> Unit,
    val onCommentDraftChange: (Int, String) -> Unit,
    val onSendComment: (Int) -> Unit,
    val onToggleSave: (Int) -> Unit = {},
    // Recibe el id del autor, no el de la receta.
    val onToggleFollow: (Int) -> Unit = {}
) {
    companion object {
        val Noop = FeedActions({}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {})
    }
}

/** Segmentos de la pestaña Inicio. */
enum class HomeFeedTab(val label: String) {
    EXPLORE("Explorar"),
    FRIENDS("Amigos")
}

/**
 * Pestaña "Inicio", con la cabecera de Hevy: a la izquierda, el segmento activo ("Explorar" o
 * "Amigos") con una flecha que despliega el otro; a la derecha, la lupa (solo en "Explorar", abre
 * y cierra el buscador desplegable) y la campana de notificaciones, con un punto rojo si hay alguna sin leer. El
 * contenido de cada segmento lo pone quien llama, con su propio ViewModel; recibe el
 * [SnackbarHostState] de la pantalla para sus avisos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabScreen(
    selectedTab: HomeFeedTab,
    onTabSelected: (HomeFeedTab) -> Unit,
    onCreateRecipe: () -> Unit,
    exploreContent: @Composable (SnackbarHostState) -> Unit,
    friendsContent: @Composable (SnackbarHostState) -> Unit,
    unreadNotifications: Int = 0,
    // Si el buscador de "Explorar" está desplegado: la lupa se ve entonces marcada.
    searchExpanded: Boolean = false,
    onSearchClick: () -> Unit = {},
    onOpenNotifications: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { FeedTabSelector(selectedTab = selectedTab, onTabSelected = onTabSelected) },
                actions = {
                    if (selectedTab == HomeFeedTab.EXPLORE) {
                        IconToggleButton(checked = searchExpanded, onCheckedChange = { onSearchClick() }) {
                            Icon(Icons.Filled.Search, contentDescription = if (searchExpanded) "Cerrar la búsqueda" else "Buscar recetas")
                        }
                    }
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(badge = { if (unreadNotifications > 0) Badge() }) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = if (unreadNotifications > 0) "Notificaciones, $unreadNotifications sin leer" else "Notificaciones"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                HomeFeedTab.EXPLORE -> exploreContent(snackbarHostState)
                HomeFeedTab.FRIENDS -> friendsContent(snackbarHostState)
            }
        }
    }
}

/** Título pulsable con el segmento activo y una flecha; al pulsarlo, un menú con los dos segmentos. */
@Composable
private fun FeedTabSelector(selectedTab: HomeFeedTab, onTabSelected: (HomeFeedTab) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = "Cambiar entre Explorar y Amigos") { expanded = true }
                .padding(vertical = Spacing.xs)
        ) {
            Text(selectedTab.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HomeFeedTab.entries.forEach { tab ->
                DropdownMenuItem(
                    text = { Text(tab.label, fontWeight = if (tab == selectedTab) FontWeight.Bold else null) },
                    trailingIcon = if (tab == selectedTab) ({ Icon(Icons.Filled.Check, contentDescription = "Seleccionado") }) else null,
                    onClick = {
                        expanded = false
                        onTabSelected(tab)
                    }
                )
            }
        }
    }
}

/**
 * Tarjetas de receta de una lista paginada ([FeedUiState]) con "Cargar más" al final o, en su
 * lugar, la carga, el error o [empty]. La usan el feed de amigos, el buscador y las recetas
 * guardadas. Las recetas de [currentUserId] no llevan el botón "Seguir".
 */
fun LazyListScope.feedCardItems(
    state: FeedUiState,
    userName: String,
    currentUserId: Int?,
    actions: FeedActions,
    errorTitle: String,
    empty: @Composable LazyItemScope.() -> Unit
) {
    when {
        state.isLoading -> item(key = "loading") { LoadingBox(listStateModifier()) }

        state.error != null -> item(key = "error") {
            CenteredMessage(
                icon = rememberVectorPainter(Icons.Filled.Warning),
                isError = true,
                title = errorTitle,
                message = state.error,
                actionLabel = "Reintentar",
                onAction = actions.onRefresh,
                modifier = listStateModifier()
            )
        }

        state.recipes.isEmpty() -> item(key = "empty") { empty() }

        else -> {
            items(state.recipes, key = { it.id }) { recipe ->
                RecipeFeedCard(
                    recipe = recipe.toCardData(),
                    currentUserName = userName,
                    draft = state.draftFor(recipe.id),
                    actions = RecipeCardActions(
                        onOpen = { actions.onRecipeClick(recipe.id) },
                        onToggleLike = { actions.onToggleLike(recipe.id) },
                        onOpenComments = { actions.onOpenComments(recipe.id) },
                        onDraftChange = { actions.onCommentDraftChange(recipe.id, it) },
                        onSendComment = { actions.onSendComment(recipe.id) },
                        onToggleSave = { actions.onToggleSave(recipe.id) },
                        onToggleFollow = if (currentUserId == null || recipe.authorId == currentUserId) null
                        else ({ actions.onToggleFollow(recipe.authorId) })
                    )
                )
            }
            item(key = "footer") { FeedFooter(state = state, onLoadMore = actions.onLoadMore) }
        }
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

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun HomeTabScreenPreview() {
    NutriSocialTheme {
        HomeTabScreen(
            selectedTab = HomeFeedTab.FRIENDS,
            onTabSelected = {},
            onCreateRecipe = {},
            unreadNotifications = 2,
            exploreContent = {},
            friendsContent = {
                LazyColumn(
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    feedCardItems(
                        state = FeedUiState(
                            isLoading = false,
                            recipes = listOf(
                                FeedRecipe(
                                    3, "Tortilla de patatas", authorId = 2, authorName = "Luis", kcalPerServing = 410.0,
                                    proteinPerServing = 14.5, prepMinutes = 40, likesCount = 5, likedByMe = true,
                                    likersPreview = listOf("Marta"), steps = listOf("Pelar y freír las patatas.", "Batir los huevos y cuajar."),
                                    commentsCount = 1, commentsPreview = listOf(CommentPreview(1, "¡Con cebolla, por favor!", "Marta")),
                                    isFollowedByMe = true
                                )
                            )
                        ),
                        userName = "Ana",
                        currentUserId = 1,
                        actions = FeedActions.Noop,
                        errorTitle = ""
                    ) {}
                }
            }
        )
    }
}
