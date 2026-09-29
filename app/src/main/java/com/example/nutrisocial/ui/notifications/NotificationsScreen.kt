package com.example.nutrisocial.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.AppNotification
import com.example.nutrisocial.ui.InitialsAvatar
import com.example.nutrisocial.ui.formatRelativeTime
import com.example.nutrisocial.ui.recipes.CenteredMessage
import com.example.nutrisocial.ui.recipes.LoadingBox
import com.example.nutrisocial.ui.recipes.listStateModifier
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/** Acciones de la pantalla de notificaciones. */
data class NotificationsActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onLoadMore: () -> Unit,
    val onOpenRecipe: (Int) -> Unit,
    val onMessageShown: () -> Unit
)

/**
 * Lista de notificaciones, de la más reciente a la más antigua. Las de "me gusta" y comentario
 * abren la receta; las de seguidor no llevan a ningún sitio (no hay perfil de otros usuarios).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(state: NotificationsUiState, actions: NotificationsActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notificaciones") },
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
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Todo dentro de la lista para poder tirar hacia abajo también desde la carga o el error.
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                when {
                    state.isLoading -> item { LoadingBox(listStateModifier()) }
                    state.error != null -> item {
                        CenteredMessage(
                            icon = rememberVectorPainter(Icons.Filled.Warning),
                            isError = true,
                            title = "No se pudieron cargar tus notificaciones",
                            message = state.error,
                            actionLabel = "Reintentar",
                            onAction = actions.onRefresh,
                            modifier = listStateModifier()
                        )
                    }
                    state.notifications.isEmpty() -> item {
                        CenteredMessage(
                            icon = rememberVectorPainter(Icons.Filled.Notifications),
                            title = "No tienes notificaciones",
                            message = "Aquí verás quién empieza a seguirte y quién da me gusta o comenta tus recetas.",
                            modifier = listStateModifier()
                        )
                    }
                    else -> {
                        items(state.notifications, key = { it.id }) { notification ->
                            NotificationRow(
                                notification = notification,
                                onClick = notification.recipeId
                                    ?.takeIf { notification.type != "follow" }
                                    ?.let { recipeId -> { actions.onOpenRecipe(recipeId) } }
                            )
                        }
                        item(key = "footer") {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md)
                            ) {
                                when {
                                    state.isLoadingMore -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    state.canLoadMore -> OutlinedButton(onClick = actions.onLoadMore, shape = ButtonShape) { Text("Cargar más") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Texto de la notificación, con el nombre y la receta en negrita. */
private fun notificationText(n: AppNotification) = buildAnnotatedString {
    val actor = n.actorName.ifBlank { "Alguien" }
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(actor) }
    val recipe = n.recipeTitle ?: "tu receta"
    when (n.type) {
        "follow" -> append(" ha empezado a seguirte")
        "like" -> {
            append(" ha dado me gusta a ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(recipe) }
        }
        "comment" -> {
            append(" ha comentado en ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(recipe) }
        }
        else -> append(" ha interactuado contigo")
    }
}

/** Avatar del actor, el texto y hace cuánto; las no leídas, con fondo y un punto. [onClick] null: no se puede pulsar. */
@Composable
private fun NotificationRow(notification: AppNotification, onClick: (() -> Unit)?) {
    val unreadBackground = if (notification.read) Modifier else Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .then(unreadBackground)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Abrir la receta", onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        InitialsAvatar(name = notification.actorName.ifBlank { "?" })
        Column(modifier = Modifier.weight(1f)) {
            Text(text = notificationText(notification), style = MaterialTheme.typography.bodyMedium)
            val time = formatRelativeTime(notification.createdAt)
            if (time.isNotEmpty()) {
                Text(text = time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!notification.read) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun NotificationsScreenPreview() {
    NutriSocialTheme {
        NotificationsScreen(
            state = NotificationsUiState(
                isLoading = false,
                loaded = true,
                notifications = listOf(
                    AppNotification(3, "comment", 2, "Luis Pérez", 10, "Crema de calabaza", createdAt = "2026-09-29T10:00:00.000Z"),
                    AppNotification(2, "like", 3, "Marta Ruiz", 10, "Crema de calabaza", read = true, createdAt = "2026-09-28T10:00:00.000Z"),
                    AppNotification(1, "follow", 4, "Pablo Gil", read = true, createdAt = "2026-09-27T10:00:00.000Z")
                )
            ),
            actions = NotificationsActions({}, {}, {}, {}, {})
        )
    }
}
