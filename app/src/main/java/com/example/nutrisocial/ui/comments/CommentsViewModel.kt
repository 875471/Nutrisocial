package com.example.nutrisocial.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.Comment
import com.example.nutrisocial.data.CommentRepository
import com.example.nutrisocial.ui.recipes.CommentDraft
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Comentarios de una receta. [total] es el recuento del servidor, ajustado al publicar o borrar;
 * [loaded] indica que ya es fiable (tras la primera página) y se puede copiar al feed.
 */
data class CommentsUiState(
    val recipeId: Int? = null,
    val comments: List<Comment> = emptyList(),
    val total: Int = 0,
    val nextCursor: Int? = null,
    val loaded: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val draft: CommentDraft = CommentDraft(),
    val deletingIds: Set<Int> = emptySet(),
    val message: String? = null
) {
    val canLoadMore: Boolean get() = nextCursor != null
}

class CommentsViewModel(
    private val repository: CommentRepository = CommentRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private var pageJob: Job? = null

    /** Carga la primera página de [recipeId] (o la recarga, conservando lo escrito). */
    fun load(recipeId: Int) {
        pageJob?.cancel()
        _state.update {
            if (it.recipeId == recipeId) it.copy(isLoading = !it.loaded, error = null)
            else CommentsUiState(recipeId = recipeId)
        }
        pageJob = viewModelScope.launch {
            when (val result = repository.getComments(recipeId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        comments = result.data.comments,
                        total = result.data.total,
                        nextCursor = result.data.nextCursor,
                        loaded = true,
                        isLoading = false,
                        error = null
                    )
                }
                is ApiResult.Error -> _state.update {
                    if (it.loaded) it.copy(isLoading = false, message = "No se pudo actualizar: ${result.message}")
                    else it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun retry() {
        _state.value.recipeId?.let(::load)
    }

    fun loadMore() {
        val current = _state.value
        val recipeId = current.recipeId ?: return
        val cursor = current.nextCursor ?: return
        if (current.isLoadingMore || current.isLoading) return
        _state.update { it.copy(isLoadingMore = true) }
        pageJob = viewModelScope.launch {
            when (val result = repository.getComments(recipeId, cursor)) {
                is ApiResult.Success -> _state.update { state ->
                    // Si entre medias alguien comentó, la página puede repetir alguno: se filtran.
                    val known = state.comments.map { it.id }.toSet()
                    state.copy(
                        comments = state.comments + result.data.comments.filterNot { it.id in known },
                        total = result.data.total,
                        nextCursor = result.data.nextCursor,
                        isLoadingMore = false
                    )
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isLoadingMore = false, message = "No se pudieron cargar más comentarios: ${result.message}")
                }
            }
        }
    }

    fun onDraftChange(text: String) = _state.update { it.copy(draft = it.draft.copy(text = text)) }

    /** Publica el comentario y lo pone el primero de la lista. Si falla, el texto se conserva. */
    fun send() {
        val current = _state.value
        val recipeId = current.recipeId ?: return
        val text = current.draft.text.trim()
        if (text.isEmpty() || current.draft.isSending) return
        _state.update { it.copy(draft = it.draft.copy(isSending = true)) }
        viewModelScope.launch {
            when (val result = repository.addComment(recipeId, text)) {
                is ApiResult.Success -> _state.update {
                    it.copy(comments = listOf(result.data) + it.comments, total = it.total + 1, draft = CommentDraft())
                }
                is ApiResult.Error -> _state.update {
                    it.copy(draft = it.draft.copy(isSending = false), message = "No se pudo publicar el comentario: ${result.message}")
                }
            }
        }
    }

    /** Borra un comentario propio (el servidor comprueba que lo sea). */
    fun delete(commentId: Int) {
        if (commentId in _state.value.deletingIds) return
        _state.update { it.copy(deletingIds = it.deletingIds + commentId) }
        viewModelScope.launch {
            val result = repository.deleteComment(commentId)
            _state.update { state ->
                val deleting = state.deletingIds - commentId
                when {
                    // Si ya no existía, el resultado es el que se buscaba: se quita igualmente.
                    result is ApiResult.Success || (result is ApiResult.Error && result.code == 404) -> state.copy(
                        comments = state.comments.filterNot { it.id == commentId },
                        total = (state.total - 1).coerceAtLeast(0),
                        deletingIds = deleting,
                        message = "Comentario borrado"
                    )
                    else -> state.copy(
                        deletingIds = deleting,
                        message = "No se pudo borrar: ${(result as ApiResult.Error).message}"
                    )
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}
