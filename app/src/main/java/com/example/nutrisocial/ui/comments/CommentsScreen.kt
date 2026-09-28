package com.example.nutrisocial.ui.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.R
import com.example.nutrisocial.data.Comment
import com.example.nutrisocial.ui.InitialsAvatar
import com.example.nutrisocial.ui.formatRelativeTime
import com.example.nutrisocial.ui.recipes.CenteredMessage
import com.example.nutrisocial.ui.recipes.CommentInput
import com.example.nutrisocial.ui.recipes.LoadingBox
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/** Acciones de la pantalla de comentarios. */
data class CommentsActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onDraftChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onDelete: (Int) -> Unit,
    val onMessageShown: () -> Unit
) {
    companion object {
        val Noop = CommentsActions({}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * Todos los comentarios de una receta, del más reciente al más antiguo, con el campo para
 * escribir uno nuevo fijo abajo. Los propios se pueden borrar (con confirmación).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    state: CommentsUiState,
    currentUserId: Int?,
    currentUserName: String,
    actions: CommentsActions
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    var confirmDeleteId by rememberSaveable { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.loaded && state.total > 0) "Comentarios (${state.total})" else "Comentarios") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            // Solo tiene sentido comentar si la receta existe y se ha podido cargar.
            if (state.loaded) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                    CommentInput(
                        userName = currentUserName,
                        draft = state.draft,
                        onDraftChange = actions.onDraftChange,
                        onSend = actions.onSend,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            state.isLoading -> LoadingBox(contentModifier)
            state.error != null -> CenteredMessage(
                icon = rememberVectorPainter(Icons.Filled.Warning),
                isError = true,
                title = "No se pudieron cargar los comentarios",
                message = state.error,
                actionLabel = "Reintentar",
                onAction = actions.onRetry,
                modifier = contentModifier
            )
            state.comments.isEmpty() -> CenteredMessage(
                icon = painterResource(R.drawable.ic_chat_bubble_outline),
                title = "Todavía no hay comentarios",
                message = "Sé la primera persona en decir qué te parece esta receta.",
                modifier = contentModifier
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = Spacing.sm),
                modifier = contentModifier
            ) {
                items(state.comments, key = { it.id }) { comment ->
                    CommentRow(
                        comment = comment,
                        isOwn = comment.authorId == currentUserId,
                        isDeleting = comment.id in state.deletingIds,
                        onDelete = { confirmDeleteId = comment.id }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = Spacing.md + 36.dp + Spacing.md)
                    )
                }
                if (state.canLoadMore || state.isLoadingMore) {
                    item(key = "footer") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md)
                        ) {
                            if (state.isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            } else {
                                OutlinedButton(onClick = actions.onLoadMore, shape = ButtonShape) { Text("Cargar más") }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("¿Borrar el comentario?") },
            text = { Text("Desaparecerá para todo el mundo. No se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteId = null
                        actions.onDelete(id)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun CommentRow(comment: Comment, isOwn: Boolean, isDeleting: Boolean, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm)
    ) {
        InitialsAvatar(name = comment.authorName, size = 36.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.md, end = Spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val time = formatRelativeTime(comment.createdAt)
                if (time.isNotEmpty()) {
                    Text(
                        text = " · $time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
        }
        if (isOwn) {
            if (isDeleting) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Borrar mi comentario",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun CommentsScreenPreview() {
    NutriSocialTheme {
        CommentsScreen(
            state = CommentsUiState(
                recipeId = 1,
                loaded = true,
                isLoading = false,
                total = 3,
                comments = listOf(
                    Comment(3, "¡Me ha encantado! La repetiré el finde.", "2026-09-28T09:00:00.000Z", 1, "Ana"),
                    Comment(2, "¿Se puede congelar?", "2026-09-27T18:00:00.000Z", 2, "Hugo Fernández"),
                    Comment(1, "Con un poco de jengibre queda genial", "2026-09-20T12:00:00.000Z", 3, "Carmen Ruiz")
                ),
                nextCursor = 1
            ),
            currentUserId = 1,
            currentUserName = "Ana",
            actions = CommentsActions.Noop
        )
    }
}
