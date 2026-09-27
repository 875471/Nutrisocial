package com.example.nutrisocial.ui.recipes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.CreateRecipeRequest
import com.example.nutrisocial.data.FoodRef
import com.example.nutrisocial.data.FoodSuggestion
import com.example.nutrisocial.data.IngredientInput
import com.example.nutrisocial.data.IngredientUnits
import com.example.nutrisocial.data.OcrRecipeProposal
import com.example.nutrisocial.data.RECIPE_GONE_MESSAGE
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.data.RecipeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RecipeListUiState {
    data object Loading : RecipeListUiState
    data class Success(val recipes: List<Recipe>) : RecipeListUiState
    data class Error(val message: String) : RecipeListUiState
}

sealed interface RecipeDetailUiState {
    data object Loading : RecipeDetailUiState
    data class Success(val recipe: Recipe) : RecipeDetailUiState
    data class Error(val message: String) : RecipeDetailUiState
}

/** Acciones del detalle: cambiar la foto, borrar la receta y sus avisos. */
data class RecipeDetailActionState(
    val isUpdatingPhoto: Boolean = false,
    val isDeleting: Boolean = false,
    // Receta recién borrada: HomeScreen vuelve a la lista y llama a onDeletedHandled.
    val deletedRecipeId: Int? = null,
    val message: String? = null
)

/**
 * Fila de ingrediente del formulario. [food] es el alimento elegido en el autocompletado;
 * se descarta en cuanto el usuario vuelve a editar el nombre.
 */
data class IngredientFormItem(
    val name: String = "",
    val quantity: String = "",
    val unit: String = "g",
    val food: FoodRef? = null
)

/** Contenido del formulario de creación. Los números se guardan como texto tal cual se escriben. */
data class RecipeFormState(
    val title: String = "",
    val ingredients: List<IngredientFormItem> = listOf(IngredientFormItem()),
    val steps: List<String> = listOf(""),
    val servings: String = "",
    val prepMinutes: String = "",
    // Foto ya comprimida en Base64 (ver ui/ImageUtils.kt), o null si no se ha elegido.
    val photoBase64: String? = null,
    // Sugerencias de alimentos para el ingrediente que se está escribiendo.
    val suggestionsFor: Int? = null,
    val suggestions: List<FoodSuggestion> = emptyList(),
    // true si el contenido viene de escanear una foto y el usuario aún debe revisarlo.
    val fromOcr: Boolean = false,
    // Id de la receta que se está editando; null al crear una nueva.
    val editingRecipeId: Int? = null,
    // Foto que tenía la receta al empezar a editarla, para saber si ha cambiado.
    val originalPhotoBase64: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
) {
    val isEditing: Boolean get() = editingRecipeId != null
}

class RecipeViewModel(
    private val repository: RecipeRepository = RecipeRepository()
) : ViewModel() {

    private val _listState = MutableStateFlow<RecipeListUiState>(RecipeListUiState.Loading)
    val listState: StateFlow<RecipeListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow<RecipeDetailUiState>(RecipeDetailUiState.Loading)
    val detailState: StateFlow<RecipeDetailUiState> = _detailState.asStateFlow()

    private val _formState = MutableStateFlow(RecipeFormState())
    val formState: StateFlow<RecipeFormState> = _formState.asStateFlow()

    private val _detailActionState = MutableStateFlow(RecipeDetailActionState())
    val detailActionState: StateFlow<RecipeDetailActionState> = _detailActionState.asStateFlow()

    // "Tirar para refrescar" en Mis recetas.
    private val _isRefreshingList = MutableStateFlow(false)
    val isRefreshingList: StateFlow<Boolean> = _isRefreshingList.asStateFlow()

    // Aviso para la lista de "Mis recetas" (p. ej. tras borrar una receta).
    private val _listMessage = MutableStateFlow<String?>(null)
    val listMessage: StateFlow<String?> = _listMessage.asStateFlow()

    init {
        loadMyRecipes()
    }

    // ---- Lista ----

    fun loadMyRecipes() {
        // Si ya hay datos se mantienen visibles mientras se refrescan.
        if (_listState.value !is RecipeListUiState.Success) {
            _listState.value = RecipeListUiState.Loading
        }
        viewModelScope.launch {
            _listState.value = when (val result = repository.getMyRecipes()) {
                is ApiResult.Success -> RecipeListUiState.Success(result.data)
                is ApiResult.Error -> RecipeListUiState.Error(result.message)
            }
        }
    }

    /**
     * Recarga pedida al tirar hacia abajo. Si falla con recetas ya en pantalla, se conservan
     * y se avisa, en vez de cambiar la lista por la pantalla de error.
     */
    fun refreshMyRecipes() {
        if (_isRefreshingList.value) return
        _isRefreshingList.value = true
        viewModelScope.launch {
            when (val result = repository.getMyRecipes()) {
                is ApiResult.Success -> _listState.value = RecipeListUiState.Success(result.data)
                is ApiResult.Error -> if (_listState.value is RecipeListUiState.Success) {
                    _listMessage.value = "No se pudo actualizar: ${result.message}"
                } else {
                    _listState.value = RecipeListUiState.Error(result.message)
                }
            }
            _isRefreshingList.value = false
        }
    }

    // ---- Detalle ----

    fun loadRecipe(id: Int) {
        // Se muestra al instante la copia que ya haya (la del propio detalle al volver del
        // formulario, o la de la lista) y se actualiza con el servidor.
        val sameRecipe = currentRecipe(id)
        val cached = sameRecipe ?: (_listState.value as? RecipeListUiState.Success)?.recipes?.find { it.id == id }
        _detailState.value = cached?.let { RecipeDetailUiState.Success(it) } ?: RecipeDetailUiState.Loading
        // Al volver a la misma receta se conservan sus avisos pendientes ("Cambios guardados").
        if (sameRecipe == null) _detailActionState.value = RecipeDetailActionState()

        viewModelScope.launch {
            when (val result = repository.getRecipe(id)) {
                is ApiResult.Success -> {
                    logPhoto("Receta ${id} cargada", result.data.imageBase64)
                    _detailState.value = RecipeDetailUiState.Success(result.data)
                }
                // Borrada (quizá desde otro dispositivo): aunque hubiera copia en la lista, no se
                // enseña una receta que ya no existe.
                is ApiResult.Error -> if (result.code == 404) {
                    onRecipeGone(id)
                } else if (cached == null) {
                    _detailState.value = RecipeDetailUiState.Error(result.message)
                }
            }
        }
    }

    /** La receta ya no está en el servidor: el detalle lo dice y la lista deja de mostrarla. */
    private fun onRecipeGone(id: Int) {
        if (currentRecipe(id) != null || _detailState.value is RecipeDetailUiState.Loading) {
            _detailState.value = RecipeDetailUiState.Error(RECIPE_GONE_MESSAGE)
        }
        _detailActionState.value = RecipeDetailActionState()
        removeFromList(id)
    }

    private fun removeFromList(id: Int) = _listState.update { state ->
        if (state is RecipeListUiState.Success) RecipeListUiState.Success(state.recipes.filterNot { it.id == id }) else state
    }

    /** Borra la receta que se está viendo (solo la propia; el servidor lo comprueba). */
    fun deleteRecipe() {
        val recipe = (_detailState.value as? RecipeDetailUiState.Success)?.recipe ?: return
        if (_detailActionState.value.isDeleting) return
        _detailActionState.value = RecipeDetailActionState(isDeleting = true)
        viewModelScope.launch {
            when (val result = repository.deleteRecipe(recipe.id)) {
                is ApiResult.Success -> {
                    removeFromList(recipe.id)
                    val orphaned = result.data.orphanedLogEntries
                    _listMessage.value = "«${recipe.title}» eliminada" + when (orphaned) {
                        0 -> ""
                        1 -> ". La entrada de tu diario con esta receta se conserva, pero ya no enlaza a ella."
                        else -> ". Las $orphaned entradas de tu diario con esta receta se conservan, pero ya no enlazan a ella."
                    }
                    _detailActionState.value = RecipeDetailActionState(deletedRecipeId = recipe.id)
                }
                is ApiResult.Error -> if (result.code == 404) {
                    // Ya estaba borrada: el resultado es el mismo que se pedía.
                    removeFromList(recipe.id)
                    _detailActionState.value = RecipeDetailActionState(deletedRecipeId = recipe.id)
                } else {
                    _detailActionState.value = RecipeDetailActionState(message = "No se pudo eliminar: ${result.message}")
                }
            }
        }
    }

    fun onDeletedHandled() = _detailActionState.update { it.copy(deletedRecipeId = null) }

    fun onListMessageShown() {
        _listMessage.value = null
    }

    /** Da o quita el "me gusta" al momento y lo confirma con el servidor; si falla, lo deshace. */
    private val pendingLikes = mutableSetOf<Int>()

    fun toggleLike() {
        val recipe = (_detailState.value as? RecipeDetailUiState.Success)?.recipe ?: return
        // Mientras una petición está en curso se ignoran más toques: así el orden de las
        // respuestas no puede dejar el corazón en un estado distinto al del servidor.
        if (!pendingLikes.add(recipe.id)) return
        val liked = !recipe.likedByMe
        replaceRecipe(recipe.copy(likedByMe = liked, likesCount = (recipe.likesCount + if (liked) 1 else -1).coerceAtLeast(0)))

        viewModelScope.launch {
            val result = repository.setLiked(recipe.id, liked)
            pendingLikes.remove(recipe.id)
            val current = currentRecipe(recipe.id) ?: return@launch
            when (result) {
                is ApiResult.Success -> replaceRecipe(current.copy(likedByMe = result.data.likedByMe, likesCount = result.data.likesCount))
                is ApiResult.Error -> if (result.code == 404) {
                    onRecipeGone(recipe.id)
                } else {
                    // Sin conexión o error del servidor: el corazón vuelve a como estaba.
                    replaceRecipe(current.copy(likedByMe = recipe.likedByMe, likesCount = recipe.likesCount))
                    _detailActionState.update { it.copy(message = "No se pudo guardar el me gusta: ${result.message}") }
                }
            }
        }
    }

    /** Pone, cambia o (con null) quita la foto de una receta propia. */
    fun updatePhoto(imageBase64: String?) {
        val recipe = (_detailState.value as? RecipeDetailUiState.Success)?.recipe ?: return
        if (_detailActionState.value.isUpdatingPhoto) return
        _detailActionState.value = RecipeDetailActionState(isUpdatingPhoto = true)
        logPhoto("Enviando foto de la receta ${recipe.id}", imageBase64)
        viewModelScope.launch {
            when (val result = repository.updateImage(recipe.id, imageBase64)) {
                is ApiResult.Success -> {
                    logPhoto("Respuesta al guardar la foto de ${recipe.id}", result.data.imageBase64)
                    replaceRecipe(result.data)
                    val message = if (imageBase64 == null) "Foto quitada" else "Foto guardada"
                    _detailActionState.value = RecipeDetailActionState(message = message)
                }
                is ApiResult.Error -> if (result.code == 404) {
                    onRecipeGone(recipe.id)
                } else {
                    Log.e(PHOTO_LOG_TAG, "El servidor rechazó la foto de ${recipe.id}: ${result.code} ${result.message}")
                    _detailActionState.value = RecipeDetailActionState(message = "No se pudo guardar la foto: ${result.message}")
                }
            }
        }
    }

    fun showDetailMessage(message: String) = _detailActionState.update { it.copy(message = message) }

    fun onDetailMessageShown() = _detailActionState.update { it.copy(message = null) }

    private fun currentRecipe(id: Int): Recipe? =
        (_detailState.value as? RecipeDetailUiState.Success)?.recipe?.takeIf { it.id == id }

    /** Sustituye la receta en el detalle (si es la que se ve) y en "Mis recetas" (si está). */
    private fun replaceRecipe(recipe: Recipe) {
        if (currentRecipe(recipe.id) != null) _detailState.value = RecipeDetailUiState.Success(recipe)
        _listState.update { state ->
            if (state is RecipeListUiState.Success && state.recipes.any { it.id == recipe.id }) {
                RecipeListUiState.Success(state.recipes.map { if (it.id == recipe.id) recipe else it })
            } else {
                state
            }
        }
    }

    // ---- Formulario ----

    /** Abre el formulario con los datos de la receta que se está viendo, para editarla. */
    fun startEditing() {
        val recipe = (_detailState.value as? RecipeDetailUiState.Success)?.recipe ?: return
        searchJob?.cancel()
        _formState.value = RecipeFormState(
            title = recipe.title,
            ingredients = recipe.ingredients.map { ing ->
                IngredientFormItem(
                    name = ing.name,
                    quantity = ing.quantity?.let(::formatQuantity).orEmpty(),
                    unit = ing.unit?.takeIf { it in IngredientUnits } ?: "g",
                    // Se conserva el alimento elegido: al guardar no se vuelve a buscar por nombre.
                    food = ing.food
                )
            }.ifEmpty { listOf(IngredientFormItem()) },
            steps = recipe.steps.ifEmpty { listOf("") },
            servings = recipe.servings.toString(),
            prepMinutes = recipe.prepMinutes?.toString().orEmpty(),
            photoBase64 = recipe.imageBase64,
            editingRecipeId = recipe.id,
            originalPhotoBase64 = recipe.imageBase64
        )
    }

    fun onPhotoChange(photoBase64: String?) = _formState.update { it.copy(photoBase64 = photoBase64, error = null) }

    fun onTitleChange(value: String) = _formState.update { it.copy(title = value, error = null) }

    fun onServingsChange(value: String) =
        _formState.update { it.copy(servings = value.filter(Char::isDigit).take(3), error = null) }

    fun onPrepMinutesChange(value: String) =
        _formState.update { it.copy(prepMinutes = value.filter(Char::isDigit).take(4), error = null) }

    // -- Ingredientes --

    private var searchJob: Job? = null

    fun onIngredientNameChange(index: Int, value: String) {
        updateIngredient(index) { it.copy(name = value, food = null) }
        searchFoods(index, value)
    }

    fun onIngredientQuantityChange(index: Int, value: String) {
        // Solo dígitos y un separador decimal (coma o punto).
        var separatorSeen = false
        val clean = value.filter { c ->
            c.isDigit() || ((c == ',' || c == '.') && !separatorSeen).also { if (it) separatorSeen = true }
        }.take(7)
        updateIngredient(index) { it.copy(quantity = clean) }
    }

    fun onIngredientUnitChange(index: Int, unit: String) = updateIngredient(index) { it.copy(unit = unit) }

    fun onSuggestionSelected(index: Int, food: FoodSuggestion) {
        searchJob?.cancel()
        updateIngredient(index) { it.copy(name = food.name, food = FoodRef(food.id, food.name, food.source)) }
        dismissSuggestions()
    }

    /** Al salir del campo se ocultan sus sugerencias (tocar una no le quita el foco). */
    fun onIngredientFocusLost(index: Int) {
        if (_formState.value.suggestionsFor == index) {
            searchJob?.cancel()
            dismissSuggestions()
        }
    }

    fun dismissSuggestions() = _formState.update { it.copy(suggestionsFor = null, suggestions = emptyList()) }

    fun addIngredient() = _formState.update { it.copy(ingredients = it.ingredients + IngredientFormItem(), error = null) }

    fun removeIngredient(index: Int) {
        searchJob?.cancel()
        _formState.update { state ->
            // Siempre queda al menos una fila visible.
            val list = state.ingredients
            val updated = if (list.size <= 1) listOf(IngredientFormItem()) else list.filterIndexed { i, _ -> i != index }
            state.copy(ingredients = updated, suggestionsFor = null, suggestions = emptyList(), error = null)
        }
    }

    private fun updateIngredient(index: Int, transform: (IngredientFormItem) -> IngredientFormItem) {
        _formState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index in list.indices) list[index] = transform(list[index])
            state.copy(ingredients = list, error = null)
        }
    }

    /** Busca alimentos con una pequeña espera, para no lanzar una petición por cada tecla. */
    private fun searchFoods(index: Int, query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            dismissSuggestions()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val result = repository.searchFoods(query.trim())
            if (result is ApiResult.Success) {
                _formState.update { it.copy(suggestionsFor = index, suggestions = result.data) }
            }
        }
    }

    // -- Pasos --

    fun onStepChange(index: Int, value: String) = updateSteps { list ->
        list.toMutableList().also { if (index in it.indices) it[index] = value }
    }

    fun addStep() = updateSteps { it + "" }

    fun removeStep(index: Int) = updateSteps { list ->
        if (list.size <= 1) listOf("") else list.filterIndexed { i, _ -> i != index }
    }

    private fun updateSteps(transform: (List<String>) -> List<String>) =
        _formState.update { it.copy(steps = transform(it.steps), error = null) }

    fun saveRecipe() {
        val form = _formState.value
        if (form.isSaving) return

        val title = form.title.trim()
        val filledIngredients = form.ingredients.filter { it.name.isNotBlank() }
        val badQuantity = filledIngredients.firstOrNull { it.quantity.isNotEmpty() && parseQuantity(it.quantity) == null }
        val ingredients = filledIngredients.map { item ->
            val quantity = parseQuantity(item.quantity)
            IngredientInput(
                name = item.name.trim(),
                quantity = quantity,
                unit = if (quantity != null) item.unit else null,
                foodId = item.food?.id
            )
        }
        val steps = form.steps.map(String::trim).filter(String::isNotEmpty)
        val servings = form.servings.toIntOrNull()
        val prepMinutes = form.prepMinutes.toIntOrNull()

        val error = when {
            title.isEmpty() -> "El título no puede estar vacío"
            ingredients.isEmpty() -> "Añade al menos un ingrediente"
            badQuantity != null -> "La cantidad de «${badQuantity.name.trim()}» no es válida"
            steps.isEmpty() -> "Añade al menos un paso"
            servings == null || servings < 1 -> "Indica cuántas raciones salen (mínimo 1)"
            else -> null
        }
        if (error != null) {
            _formState.update { it.copy(error = error) }
            return
        }

        searchJob?.cancel()
        _formState.update { it.copy(isSaving = true, error = null, suggestionsFor = null, suggestions = emptyList()) }
        val editingId = form.editingRecipeId
        if (editingId != null) {
            // La foto no va en el PUT: si ha cambiado se guarda después con PUT /image, que
            // además permite quitarla (Gson no enviaría un null en el cuerpo).
            val request = CreateRecipeRequest(title, ingredients, steps, servings!!, prepMinutes)
            viewModelScope.launch { saveEdit(editingId, request, form) }
            return
        }
        logPhoto("Creando receta", form.photoBase64)
        viewModelScope.launch {
            val request = CreateRecipeRequest(title, ingredients, steps, servings!!, prepMinutes, form.photoBase64)
            when (val result = repository.createRecipe(request)) {
                is ApiResult.Success -> {
                    logPhoto("Receta ${result.data.id} creada", result.data.imageBase64)
                    // Se inserta al principio para que la lista esté actualizada al volver,
                    // y se refresca desde el servidor por coherencia.
                    val current = (_listState.value as? RecipeListUiState.Success)?.recipes.orEmpty()
                    _listState.value = RecipeListUiState.Success(listOf(result.data) + current)
                    _formState.update { it.copy(isSaving = false, saved = true) }
                    loadMyRecipes()
                }
                is ApiResult.Error -> _formState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    private suspend fun saveEdit(id: Int, request: CreateRecipeRequest, form: RecipeFormState) {
        when (val result = repository.updateRecipe(id, request)) {
            is ApiResult.Success -> {
                var saved = result.data
                var photoError: String? = null
                if (form.photoBase64 != form.originalPhotoBase64) {
                    logPhoto("Enviando foto editada de la receta $id", form.photoBase64)
                    when (val photo = repository.updateImage(id, form.photoBase64)) {
                        is ApiResult.Success -> saved = photo.data
                        is ApiResult.Error -> {
                            Log.e(PHOTO_LOG_TAG, "El servidor rechazó la foto de $id: ${photo.code} ${photo.message}")
                            photoError = photo.message
                        }
                    }
                }
                replaceRecipe(saved)
                // Los cambios de la receta ya están guardados aunque falle la foto: se avisa en el detalle.
                photoError?.let { msg ->
                    _detailActionState.update { it.copy(message = "Cambios guardados, pero no la foto: $msg") }
                } ?: _detailActionState.update { it.copy(message = "Cambios guardados") }
                _formState.update { it.copy(isSaving = false, saved = true) }
            }
            is ApiResult.Error -> {
                if (result.code == 404) onRecipeGone(id)
                _formState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    /**
     * Vuelca en el formulario la propuesta extraída de una foto. Es un borrador normal: el
     * usuario lo edita con los mismos controles y, al guardar, el servidor recalcula los valores.
     */
    fun loadOcrProposal(proposal: OcrRecipeProposal) {
        searchJob?.cancel()
        val ingredients = proposal.ingredients.map { ing ->
            IngredientFormItem(
                name = ing.rawName,
                quantity = ing.quantity?.let(::formatQuantity).orEmpty(),
                unit = ing.unit?.takeIf { it in IngredientUnits } ?: "g",
                food = if (ing.foodId != null && ing.foodName != null) FoodRef(ing.foodId, ing.foodName, ing.foodSource) else null
            )
        }
        _formState.value = RecipeFormState(
            title = proposal.title.orEmpty(),
            ingredients = ingredients.ifEmpty { listOf(IngredientFormItem()) },
            steps = proposal.steps.ifEmpty { listOf("") },
            servings = proposal.servings?.toString().orEmpty(),
            fromOcr = true
        )
    }

    fun dismissOcrNotice() = _formState.update { it.copy(fromOcr = false) }

    /** Traza de Logcat: si la foto iba (y cuánto ocupaba) en cada paso, para saber dónde se pierde. */
    private fun logPhoto(step: String, imageBase64: String?) {
        Log.d(PHOTO_LOG_TAG, "$step: " + if (imageBase64 == null) "sin foto" else "con foto (${imageBase64.length} caracteres)")
    }

    /** 0.5 → "0,5", 0.333 → "0,33", 2.0 → "2" (el campo acepta coma decimal). */
    private fun formatQuantity(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else "%.2f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.').replace('.', ',')

    private fun parseQuantity(text: String): Double? =
        text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    /** Deja el formulario vacío para la próxima receta (tras guardar o al abrirlo de nuevo). */
    fun resetForm() {
        if (!_formState.value.isSaving) _formState.value = RecipeFormState()
    }
}
