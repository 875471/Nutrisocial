package com.example.nutrisocial.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.example.nutrisocial.data.UserSummary
import com.example.nutrisocial.ui.InitialsAvatar
import com.example.nutrisocial.ui.recipes.CenteredMessage
import com.example.nutrisocial.ui.recipes.FollowButton
import com.example.nutrisocial.ui.recipes.LoadingBox
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/** Acciones del buscador de personas. */
data class PeopleSearchActions(
    val onBack: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onSearch: () -> Unit,
    val onToggleFollow: (Int) -> Unit,
    val onMessageShown: () -> Unit
)

/** Buscador de personas por nombre, con "Seguir"/"Siguiendo" en cada fila. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleSearchScreen(state: PeopleSearchUiState, actions: PeopleSearchActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar personas") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                placeholder = { Text("Nombre de la persona") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { actions.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Borrar la búsqueda")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    actions.onSearch()
                    focusManager.clearFocus()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
            when {
                state.isLoading && state.users.isEmpty() -> LoadingBox()
                state.error != null -> CenteredMessage(
                    icon = rememberVectorPainter(Icons.Filled.Warning),
                    isError = true,
                    title = "No se pudo buscar",
                    message = state.error,
                    actionLabel = "Reintentar",
                    onAction = actions.onSearch
                )
                state.users.isEmpty() -> CenteredMessage(
                    icon = rememberVectorPainter(Icons.Filled.Search),
                    title = "Sin resultados",
                    message = if (state.query.isBlank()) "Todavía no hay nadie más en NutriSocial."
                    else "Nadie se llama «${state.query.trim()}»."
                )
                else -> {
                    // Al cambiar la búsqueda se siguen viendo los resultados anteriores, con una barra fina.
                    if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    LazyColumn(contentPadding = PaddingValues(vertical = Spacing.sm), modifier = Modifier.fillMaxSize()) {
                        items(state.users, key = { it.id }) { user ->
                            PersonRow(user = user, onToggleFollow = { actions.onToggleFollow(user.id) })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(user: UserSummary, onToggleFollow: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        InitialsAvatar(name = user.name)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (user.recipesCount) {
                    0 -> "Sin recetas todavía"
                    1 -> "1 receta"
                    else -> "${user.recipesCount} recetas"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FollowButton(isFollowing = user.isFollowedByMe, onClick = onToggleFollow)
    }
}

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun PeopleSearchScreenPreview() {
    NutriSocialTheme {
        PeopleSearchScreen(
            state = PeopleSearchUiState(
                query = "ma",
                isLoading = false,
                users = listOf(
                    UserSummary(2, "Marta Ruiz", isFollowedByMe = true, recipesCount = 4),
                    UserSummary(3, "Omar Sanz", recipesCount = 1),
                    UserSummary(4, "Manuel Gil")
                )
            ),
            actions = PeopleSearchActions({}, {}, {}, {}, {})
        )
    }
}
