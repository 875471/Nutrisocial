package com.example.nutrisocial.ui.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.R
import com.example.nutrisocial.data.FeedRecipe
import com.example.nutrisocial.ui.home.FeedActions
import com.example.nutrisocial.ui.home.FeedUiState
import com.example.nutrisocial.ui.home.feedCardItems
import com.example.nutrisocial.ui.recipes.CenteredMessage
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/**
 * Segmento "Explorar" del inicio: campo de búsqueda con lupa, orden (recientes, más rápidas o
 * más elaboradas) y los resultados con la misma tarjeta que el feed. Sin texto, salen todas las
 * recetas de la comunidad, de la más reciente a la más antigua (el feed global).
 */
@Composable
fun ExploreContent(
    state: FeedUiState,
    params: SearchParams,
    userName: String,
    currentUserId: Int?,
    actions: FeedActions,
    onQueryChange: (String) -> Unit,
    onSortChange: (SearchSort) -> Unit,
    onSearch: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val focusManager = LocalFocusManager.current
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = params.query,
            onValueChange = onQueryChange,
            placeholder = { Text("Buscar por nombre o ingrediente") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (params.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Borrar la búsqueda")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSearch()
                focusManager.clearFocus()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
        ) {
            SearchSort.entries.forEach { sort ->
                FilterChip(
                    selected = params.sort == sort,
                    onClick = { onSortChange(sort) },
                    label = { Text(sort.label) }
                )
            }
        }
        FeedTabList(
            state = state,
            userName = userName,
            currentUserId = currentUserId,
            actions = actions,
            errorTitle = "No se pudo buscar",
            snackbarHostState = snackbarHostState,
            header = state.total?.takeIf { !state.isLoading }?.let { total ->
                { ResultsCount(total) }
            }
        ) {
            CenteredMessage(
                icon = rememberVectorPainter(Icons.Filled.Search),
                title = "Sin resultados",
                message = if (params.query.isBlank()) "Todavía no hay recetas publicadas."
                else "Ninguna receta tiene «${params.query.trim()}» en el nombre o en los ingredientes.",
                modifier = Modifier.fillParentMaxSize(0.6f)
            )
        }
    }
}

/**
 * Segmento "Amigos" del inicio: las recetas de la gente a la que se sigue. Sin seguir a nadie (o
 * si sus seguidos no han publicado nada), invita a buscar personas; con recetas, el enlace al
 * buscador se queda arriba para seguir a más gente.
 */
@Composable
fun FriendsFeedContent(
    state: FeedUiState,
    userName: String,
    currentUserId: Int?,
    actions: FeedActions,
    onFindPeople: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    FeedTabList(
        state = state,
        userName = userName,
        currentUserId = currentUserId,
        actions = actions,
        errorTitle = "No se pudieron cargar las recetas de tus amigos",
        snackbarHostState = snackbarHostState,
        header = if (state.recipes.isEmpty()) null else {
            {
                TextButton(onClick = onFindPeople) {
                    Icon(painterResource(R.drawable.ic_person_add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Buscar personas", modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
    ) {
        CenteredMessage(
            icon = painterResource(R.drawable.ic_person_add),
            title = "Aquí verás las recetas de quien sigas",
            message = "Todavía no sigues a nadie que haya publicado recetas. Busca a tus amigos por su " +
                "nombre, o pulsa «Seguir» en una receta de «Explorar».",
            actionLabel = "Buscar personas",
            onAction = onFindPeople,
            modifier = Modifier.fillParentMaxSize(0.6f)
        )
    }
}

/** Segmento "Guardadas" del perfil: las recetas que el usuario ha guardado con el marcador. */
@Composable
fun SavedRecipesContent(
    state: FeedUiState,
    userName: String,
    currentUserId: Int?,
    actions: FeedActions,
    snackbarHostState: SnackbarHostState
) {
    FeedTabList(
        state = state,
        userName = userName,
        currentUserId = currentUserId,
        actions = actions,
        errorTitle = "No se pudieron cargar tus guardadas",
        snackbarHostState = snackbarHostState
    ) {
        CenteredMessage(
            icon = painterResource(R.drawable.ic_bookmark_border),
            title = "No has guardado ninguna receta",
            message = "Pulsa el marcador de una receta de «Explorar» o de «Amigos» para tenerla aquí a mano.",
            modifier = Modifier.fillParentMaxSize(0.6f)
        )
    }
}

@Composable
private fun ResultsCount(total: Int) {
    Text(
        text = if (total == 1) "1 receta" else "$total recetas",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Lista de tarjetas con tirar para refrescar y sus avisos en el snackbar de la pantalla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedTabList(
    state: FeedUiState,
    userName: String,
    currentUserId: Int?,
    actions: FeedActions,
    errorTitle: String,
    snackbarHostState: SnackbarHostState,
    header: (@Composable () -> Unit)? = null,
    empty: @Composable LazyItemScope.() -> Unit
) {
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = actions.onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.xl * 3),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxSize()
        ) {
            header?.let { item(key = "header") { it() } }
            feedCardItems(
                state = state,
                userName = userName,
                currentUserId = currentUserId,
                actions = actions,
                errorTitle = errorTitle,
                empty = empty
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ExploreContentPreview() {
    NutriSocialTheme {
        ExploreContent(
            state = FeedUiState(
                isLoading = false,
                total = 1,
                recipes = listOf(
                    FeedRecipe(1, "Arroz con pollo", authorId = 2, authorName = "Hugo", kcalPerServing = 520.0, prepMinutes = 45)
                )
            ),
            params = SearchParams(query = "pollo", sort = SearchSort.QUICKEST),
            userName = "Ana",
            currentUserId = 1,
            actions = FeedActions.Noop,
            onQueryChange = {},
            onSortChange = {},
            onSearch = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}
