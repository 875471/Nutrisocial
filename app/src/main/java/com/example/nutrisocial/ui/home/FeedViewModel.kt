package com.example.nutrisocial.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.Comment
import com.example.nutrisocial.data.CommentPreview
import com.example.nutrisocial.data.CommentRepository
import com.example.nutrisocial.data.FeedPage
import com.example.nutrisocial.data.FeedRecipe
import com.example.nutrisocial.data.FeedRepository
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.data.RecipeRepository
import com.example.nutrisocial.data.UserRepository
import com.example.nutrisocial.data.toPreview
import com.example.nutrisocial.ui.recipes.CommentDraft
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
    val message: String? = null,
    // Lo escrito en el campo de comentario de cada tarjeta, por id de receta.
    val commentDrafts: Map<Int, CommentDraft> = emptyMap(),
    // Número de resultados (solo en el buscador).
    val total: Int? = null
) {
    fun draftFor(recipeId: Int): CommentDraft = commentDrafts[recipeId] ?: CommentDraft()

    val canLoadMore: Boolean get() = nextCursor != null
}

/**
 * Lista paginada de tarjetas de receta con "me gusta", comentarios y "Seguir" desde la propia
 * tarjeta. Es la base del feed de amigos, del buscador y de las guardadas, que solo cambian de
 * dónde sale cada página ([fetchPage]). No carga nada al crearse: cada subclase decide cuándo
 * (en su propio `init`, cuando sus propiedades ya están inicializadas, o al abrirse su pantalla).
 */
abstract class FeedViewModel(
    protected val feedRepository: FeedRepository = FeedRepository(),
    private val recipeRepository: RecipeRepository = RecipeRepository(),
    private val commentRepository: CommentRepository = CommentRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var pageJob: Job? = null

    /** Página a partir de [cursor] (null para la primera). */
    protected abstract suspend fun fetchPage(cursor: Int?): ApiResult<FeedPage>

    /** Empieza de cero, sin enseñar lo anterior (p. ej. al cambiar la búsqueda). */
    protected fun restart() {
        pageJob?.cancel()
        _state.update { FeedUiState(commentDrafts = it.commentDrafts) }
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
            when (val result = fetchPage(null)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        recipes = result.data.recipes,
                        nextCursor = result.data.nextCursor,
                        total = result.data.total,
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
            when (val result = fetchPage(cursor)) {
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
                // La receta se ha borrado entretanto: se quita del feed.
                is ApiResult.Error -> if (result.code == 404) {
                    removeRecipe(recipeId)
                    _state.update { it.copy(message = result.message) }
                } else {
                    // Sin conexión o error del servidor: el corazón vuelve a como estaba.
                    updateRecipe(recipeId) { it.copy(likedByMe = recipe.likedByMe, likesCount = recipe.likesCount) }
                    _state.update { it.copy(message = "No se pudo guardar el me gusta: ${result.message}") }
                }
            }
        }
    }

    private val pendingSaves = mutableSetOf<Int>()

    /** Guardar o quitar de "Guardadas", con el mismo cambio inmediato que el "me gusta". */
    fun toggleSave(recipeId: Int) {
        val recipe = _state.value.recipes.find { it.id == recipeId } ?: return
        if (!pendingSaves.add(recipeId)) return
        val saved = !recipe.savedByMe
        updateRecipe(recipeId) { it.copy(savedByMe = saved) }
        viewModelScope.launch {
            val result = recipeRepository.setSaved(recipeId, saved)
            pendingSaves.remove(recipeId)
            when (result) {
                is ApiResult.Success -> {
                    updateRecipe(recipeId) { it.copy(savedByMe = result.data.savedByMe) }
                    _state.update { it.copy(message = if (saved) "Guardada en tus recetas guardadas" else "Quitada de guardadas") }
                }
                is ApiResult.Error -> if (result.code == 404) {
                    removeRecipe(recipeId)
                    _state.update { it.copy(message = result.message) }
                } else {
                    updateRecipe(recipeId) { it.copy(savedByMe = recipe.savedByMe) }
                    _state.update { it.copy(message = "No se pudo guardar la receta: ${result.message}") }
                }
            }
        }
    }

    private val pendingFollows = mutableSetOf<Int>()

    /**
     * Seguir o dejar de seguir al autor [authorId], con el mismo cambio inmediato que el "me gusta"
     * en todas sus tarjetas de la lista. [onChanged] recibe el estado final confirmado por el
     * servidor, para copiarlo a las demás listas (ver HomeScreen).
     */
    fun toggleFollow(authorId: Int, onChanged: (authorId: Int, followed: Boolean) -> Unit = { _, _ -> }) {
        val recipe = _state.value.recipes.find { it.authorId == authorId } ?: return
        if (!pendingFollows.add(authorId)) return
        val followed = !recipe.isFollowedByMe
        applyFollow(authorId, followed)
        viewModelScope.launch {
            val result = userRepository.setFollowed(authorId, followed)
            pendingFollows.remove(authorId)
            when (result) {
                is ApiResult.Success -> {
                    applyFollow(authorId, result.data.isFollowedByMe)
                    onChanged(authorId, result.data.isFollowedByMe)
                }
                is ApiResult.Error -> {
                    applyFollow(authorId, recipe.isFollowedByMe)
                    _state.update { it.copy(message = "No se pudo ${if (followed) "seguir" else "dejar de seguir"} a ${recipe.authorName}: ${result.message}") }
                }
            }
        }
    }

    /** Marca como seguido (o no) al autor [authorId] en todas sus tarjetas de esta lista. */
    fun applyFollow(authorId: Int, followed: Boolean) = _state.update { state ->
        if (state.recipes.none { it.authorId == authorId }) state
        else state.copy(recipes = state.recipes.map { if (it.authorId == authorId) it.copy(isFollowedByMe = followed) else it })
    }

    // ---- Comentarios desde la tarjeta ----

    fun onCommentDraftChange(recipeId: Int, text: String) = updateDraft(recipeId) { it.copy(text = text) }

    /** Publica lo escrito en la tarjeta. Si falla, el texto se queda en el campo para reintentar. */
    fun sendComment(recipeId: Int) {
        val draft = _state.value.draftFor(recipeId)
        val text = draft.text.trim()
        if (text.isEmpty() || draft.isSending) return
        updateDraft(recipeId) { it.copy(isSending = true) }
        viewModelScope.launch {
            when (val result = commentRepository.addComment(recipeId, text)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(commentDrafts = it.commentDrafts - recipeId) }
                    onCommentPosted(recipeId, result.data)
                }
                is ApiResult.Error -> {
                    updateDraft(recipeId) { it.copy(isSending = false) }
                    if (result.code == 404) {
                        removeRecipe(recipeId)
                        _state.update { it.copy(message = result.message) }
                    } else {
                        _state.update { it.copy(message = "No se pudo publicar el comentario: ${result.message}") }
                    }
                }
            }
        }
    }

    /** Un comentario nuevo (desde la tarjeta o desde el detalle) pasa a ser el primero de la vista previa. */
    fun onCommentPosted(recipeId: Int, comment: Comment) = updateRecipe(recipeId) {
        it.copy(
            commentsCount = it.commentsCount + 1,
            commentsPreview = (listOf(comment.toPreview()) + it.commentsPreview).take(COMMENTS_PREVIEW)
        )
    }

    /** Recuento y últimos comentarios tal como los ha dejado la pantalla de comentarios. */
    fun syncComments(recipeId: Int, total: Int, latest: List<CommentPreview>) = updateRecipe(recipeId) {
        it.copy(commentsCount = total, commentsPreview = latest.take(COMMENTS_PREVIEW))
    }

    private fun updateDraft(recipeId: Int, transform: (CommentDraft) -> CommentDraft) = _state.update {
        it.copy(commentDrafts = it.commentDrafts + (recipeId to transform(it.draftFor(recipeId))))
    }

    /**
     * Copia en la tarjeta del feed lo que haya cambiado en el detalle de una receta (likes, foto
     * o edición), para que al volver se vea igual en las dos pantallas. Los comentarios no: se
     * sincronizan aparte (onCommentPosted, syncComments) para no contarlos dos veces.
     */
    fun syncRecipe(recipe: Recipe) = updateRecipe(recipe.id) {
        it.copy(
            title = recipe.title,
            description = recipe.description,
            imageBase64 = recipe.imageBase64,
            // Al editar la receta cambian sus raciones, tiempo, valores, ingredientes y pasos.
            servings = recipe.servings,
            prepMinutes = recipe.prepMinutes,
            kcalPerServing = recipe.nutrition.perServing.kcal,
            proteinPerServing = recipe.nutrition.perServing.protein,
            ingredients = recipe.ingredients,
            steps = recipe.steps,
            likesCount = recipe.likesCount,
            likedByMe = recipe.likedByMe,
            likersPreview = recipe.likersPreview,
            savedByMe = recipe.savedByMe,
            isFollowedByMe = recipe.isFollowedByMe
        )
    }

    /** Quita del feed una receta borrada (por su autor, desde aquí o desde otro dispositivo). */
    fun removeRecipe(id: Int) = _state.update { state ->
        state.copy(recipes = state.recipes.filterNot { it.id == id })
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private companion object {
        // Los mismos que devuelve el servidor en commentsPreview.
        const val COMMENTS_PREVIEW = 2
    }

    private fun updateRecipe(id: Int, transform: (FeedRecipe) -> FeedRecipe) = _state.update { state ->
        if (state.recipes.none { it.id == id }) state
        else state.copy(recipes = state.recipes.map { if (it.id == id) transform(it) else it })
    }
}
