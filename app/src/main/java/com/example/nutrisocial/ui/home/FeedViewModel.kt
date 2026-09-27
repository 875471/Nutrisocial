package com.example.nutrisocial.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.FeedRecipe
import com.example.nutrisocial.data.FeedRepository
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.data.RecipeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del feed. [error] solo se usa cuando no hay nada que enseñar (primera carga fallida);
 * los fallos al refrescar o al cargar más, con recetas ya en pantalla, van a [message].
 */
data class FeedUiState(
    val recipes: List<FeedRecipe> = emptyList(),
    val nextCursor: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val message: String? = null
) {
    val canLoadMore: Boolean get() = nextCursor != null
}

class FeedViewModel(
    private val feedRepository: FeedRepository = FeedRepository(),
    private val recipeRepository: RecipeRepository = RecipeRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var pageJob: Job? = null

    init {
        refresh()
    }

    /** Vuelve a pedir la primera página (al abrir, al tirar hacia abajo o al reintentar). */
    fun refresh() {
        pageJob?.cancel()
        _state.update {
            if (it.recipes.isEmpty()) it.copy(isLoading = true, error = null)
            else it.copy(isRefreshing = true, isLoadingMore = false)
        }
        pageJob = viewModelScope.launch {
            when (val result = feedRepository.getFeed()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        recipes = result.data.recipes,
                        nextCursor = result.data.nextCursor,
                        isLoading = false,
                        isRefreshing = false,
                        error = null
                    )
                }
                is ApiResult.Error -> _state.update {
                    if (it.recipes.isEmpty()) it.copy(isLoading = false, isRefreshing = false, error = result.message)
                    else it.copy(isRefreshing = false, message = "No se pudo actualizar: ${result.message}")
                }
            }
        }
    }

    /** Pide la página siguiente a partir de la última receta recibida. */
    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.isLoadingMore || current.isRefreshing || current.isLoading) return
        _state.update { it.copy(isLoadingMore = true) }
        pageJob = viewModelScope.launch {
            when (val result = feedRepository.getFeed(cursor)) {
                is ApiResult.Success -> _state.update { state ->
                    // Si entre medias se publicó otra receta, podría repetirse alguna: se filtran.
                    val known = state.recipes.map { it.id }.toSet()
                    state.copy(
                        recipes = state.recipes + result.data.recipes.filterNot { it.id in known },
                        nextCursor = result.data.nextCursor,
                        isLoadingMore = false
                    )
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isLoadingMore = false, message = "No se pudieron cargar más recetas: ${result.message}")
                }
            }
        }
    }

    private val pendingLikes = mutableSetOf<Int>()

    /** Mismo comportamiento que en el detalle: cambio inmediato y se deshace si el servidor falla. */
    fun toggleLike(recipeId: Int) {
        val recipe = _state.value.recipes.find { it.id == recipeId } ?: return
        if (!pendingLikes.add(recipeId)) return
        val liked = !recipe.likedByMe
        updateRecipe(recipeId) {
            it.copy(likedByMe = liked, likesCount = (it.likesCount + if (liked) 1 else -1).coerceAtLeast(0))
        }
        viewModelScope.launch {
            val result = recipeRepository.setLiked(recipeId, liked)
            pendingLikes.remove(recipeId)
            when (result) {
                is ApiResult.Success -> updateRecipe(recipeId) {
                    it.copy(likedByMe = result.data.likedByMe, likesCount = result.data.likesCount)
                }
                is ApiResult.Error -> {
                    updateRecipe(recipeId) { it.copy(likedByMe = recipe.likedByMe, likesCount = recipe.likesCount) }
                    _state.update { it.copy(message = "No se pudo guardar el me gusta: ${result.message}") }
                }
            }
        }
    }

    /**
     * Copia en la tarjeta del feed lo que haya cambiado en el detalle de una receta (likes o
     * foto), para que al volver se vea igual en las dos pantallas.
     */
    fun syncRecipe(recipe: Recipe) = updateRecipe(recipe.id) {
        it.copy(
            title = recipe.title,
            imageBase64 = recipe.imageBase64,
            likesCount = recipe.likesCount,
            likedByMe = recipe.likedByMe
        )
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun updateRecipe(id: Int, transform: (FeedRecipe) -> FeedRecipe) = _state.update { state ->
        if (state.recipes.none { it.id == id }) state
        else state.copy(recipes = state.recipes.map { if (it.id == id) transform(it) else it })
    }
}
