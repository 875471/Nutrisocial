package com.example.nutrisocial.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.example.nutrisocial.ui.recipes.listStateModifier
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/**
 * Segmento "Explorar" del inicio: orden (recientes, más rápidas o más elaboradas) y los resultados
 * con la misma tarjeta que el feed. Sin texto, salen todas las recetas de la comunidad, de la más
 * reciente a la más antigua (el feed global). El campo de búsqueda está plegado: lo despliega la
 * lupa de la cabecera ([searchExpanded]) con el foco y el teclado ya puestos, y al plegarlo
 * ([onCloseSearch]) se borra el texto y vuelve el listado sin búsqueda.
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
    snackbarHostState: SnackbarHostState,
    searchExpanded: Boolean = true,
    onCloseSearch: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Al desplegarlo, el campo recibe el foco y se abre el teclado. Si ya estaba desplegado (al
    // volver del detalle de una receta), no se fuerza el teclado otra vez.
    var wasExpanded by rememberSaveable { mutableStateOf(searchExpanded) }
    LaunchedEffect(searchExpanded) {
        if (searchExpanded && !wasExpanded) {
            // Espera un fotograma a que el campo esté en pantalla.
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
        wasExpanded = searchExpanded
    }
    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = searchExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            SearchField(
                query = params.query,
                onQueryChange = onQueryChange,
                onSearch = {
                    onSearch()
                    focusManager.clearFocus()
                },
                onClose = {
                    focusManager.clearFocus()
                    onCloseSearch()
                },
                modifier = Modifier.focusRequester(focusRequester)
            )
        }
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
                modifier = listStateModifier()
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
            modifier = listStateModifier()
        )
    }
}

/** Campo de búsqueda desplegable de "Explorar". La × borra el texto y pliega el buscador a la vez. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Buscar por nombre o ingrediente") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Clear, contentDescription = "Cerrar la búsqueda")
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
    )
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
            modifier = listStateModifier()
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
            snackbarHostState = SnackbarHostState(),
            searchExpanded = true
        )
    }
}
