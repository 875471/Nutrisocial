package com.example.nutrisocial.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.RecipeRepository
import com.example.nutrisocial.data.UserRepository
import com.example.nutrisocial.data.UserSummary
import com.example.nutrisocial.ui.InitialsAvatar
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Hoja de compartir. [recipeId] es la receta que se está compartiendo, o null con la hoja cerrada.
 * [sendingTo] es el usuario al que se está enviando, para bloquear el resto de botones mientras.
 */
data class ShareRecipeState(
    val recipeId: Int? = null,
    val following: List<UserSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sendingTo: Int? = null,
    val message: String? = null
)

/**
 * Compartir una receta con alguien a quien se sigue (POST /recipes/{id}/share): le llega como
 * notificación. La lista de seguidos se pide cada vez que se abre la hoja, porque se puede haber
 * seguido a alguien desde otra pantalla.
 */
class ShareRecipeViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val recipeRepository: RecipeRepository = RecipeRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ShareRecipeState())
    val state: StateFlow<ShareRecipeState> = _state.asStateFlow()

    fun open(recipeId: Int) {
        _state.update { it.copy(recipeId = recipeId, sendingTo = null) }
        loadFollowing()
    }

    fun loadFollowing() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = userRepository.getFollowing()) {
                is ApiResult.Success -> _state.update { it.copy(following = result.data, isLoading = false) }
                is ApiResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun dismiss() {
        _state.update { it.copy(recipeId = null, sendingTo = null) }
    }

    /** Comparte la receta abierta con [user]; al terminar cierra la hoja y deja el aviso en [ShareRecipeState.message]. */
    fun share(user: UserSummary) {
        val recipeId = _state.value.recipeId ?: return
        if (_state.value.sendingTo != null) return
        _state.update { it.copy(sendingTo = user.id) }
        viewModelScope.launch {
            val message = when (val result = recipeRepository.shareRecipe(recipeId, user.id)) {
                is ApiResult.Success -> "Receta compartida con ${user.name}"
                is ApiResult.Error -> "No se pudo compartir: ${result.message}"
            }
            _state.update { it.copy(recipeId = null, sendingTo = null, message = message) }
        }
    }

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }
}

/**
 * Hoja con la gente a la que se sigue y un botón "Compartir" por persona. El resultado se
 * avisa en el snackbar de la pantalla. Sin seguidos, invita a buscar gente: con [onFindPeople],
 * con un botón que lleva al buscador de personas; sin él, solo con el texto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareRecipeSheet(
    viewModel: ShareRecipeViewModel,
    snackbarHostState: SnackbarHostState,
    onFindPeople: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }
    if (state.recipeId == null) return

    ModalBottomSheet(
        onDismissRequest = viewModel::dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg)
        ) {
            Text("Compartir con…", style = MaterialTheme.typography.titleLarge)
            when {
                state.isLoading -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
                    CircularProgressIndicator()
                }

                state.error != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "No se pudo cargar a quién sigues: ${state.error}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = viewModel::loadFollowing) { Text("Reintentar") }
                }

                state.following.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "Todavía no sigues a nadie: busca gente primero.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    onFindPeople?.let { findPeople ->
                        TextButton(onClick = {
                            viewModel.dismiss()
                            findPeople()
                        }) { Text("Buscar personas") }
                    }
                }

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(state.following, key = { it.id }) { user ->
                        FollowedUserRow(
                            user = user,
                            isSending = state.sendingTo == user.id,
                            enabled = state.sendingTo == null,
                            onShare = { viewModel.share(user) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowedUserRow(user: UserSummary, isSending: Boolean, enabled: Boolean, onShare: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxWidth()
    ) {
        InitialsAvatar(user.name)
        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = onShare, enabled = enabled, shape = ButtonShape) {
            if (isSending) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Text("Compartir")
            }
        }
    }
}
