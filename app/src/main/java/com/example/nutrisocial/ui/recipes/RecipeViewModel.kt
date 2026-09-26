package com.example.nutrisocial.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.CreateRecipeRequest
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.data.RecipeRepository
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

/** Contenido del formulario de creación. Los números se guardan como texto tal cual se escriben. */
data class RecipeFormState(
    val title: String = "",
    val ingredients: List<String> = listOf(""),
    val steps: List<String> = listOf(""),
    val servings: String = "",
    val prepMinutes: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

enum class RecipeListField { INGREDIENTS, STEPS }

class RecipeViewModel(
    private val repository: RecipeRepository = RecipeRepository()
) : ViewModel() {

    private val _listState = MutableStateFlow<RecipeListUiState>(RecipeListUiState.Loading)
    val listState: StateFlow<RecipeListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow<RecipeDetailUiState>(RecipeDetailUiState.Loading)
    val detailState: StateFlow<RecipeDetailUiState> = _detailState.asStateFlow()

    private val _formState = MutableStateFlow(RecipeFormState())
    val formState: StateFlow<RecipeFormState> = _formState.asStateFlow()

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

    // ---- Detalle ----

    fun loadRecipe(id: Int) {
        // Se muestra al instante la copia de la lista (si existe) y se actualiza con el servidor.
        val cached = (_listState.value as? RecipeListUiState.Success)?.recipes?.find { it.id == id }
        _detailState.value = cached?.let { RecipeDetailUiState.Success(it) } ?: RecipeDetailUiState.Loading

        viewModelScope.launch {
            when (val result = repository.getRecipe(id)) {
                is ApiResult.Success -> _detailState.value = RecipeDetailUiState.Success(result.data)
                is ApiResult.Error -> if (cached == null) {
                    _detailState.value = RecipeDetailUiState.Error(result.message)
                }
            }
        }
    }

    // ---- Formulario ----

    fun onTitleChange(value: String) = _formState.update { it.copy(title = value, error = null) }

    fun onServingsChange(value: String) =
        _formState.update { it.copy(servings = value.filter(Char::isDigit).take(3), error = null) }

    fun onPrepMinutesChange(value: String) =
        _formState.update { it.copy(prepMinutes = value.filter(Char::isDigit).take(4), error = null) }

    fun onItemChange(field: RecipeListField, index: Int, value: String) = updateList(field) { list ->
        list.toMutableList().also { if (index in it.indices) it[index] = value }
    }

    fun addItem(field: RecipeListField) = updateList(field) { it + "" }

    fun removeItem(field: RecipeListField, index: Int) = updateList(field) { list ->
        // Siempre queda al menos un campo visible.
        if (list.size <= 1) listOf("") else list.filterIndexed { i, _ -> i != index }
    }

    private fun updateList(field: RecipeListField, transform: (List<String>) -> List<String>) {
        _formState.update { state ->
            when (field) {
                RecipeListField.INGREDIENTS -> state.copy(ingredients = transform(state.ingredients), error = null)
                RecipeListField.STEPS -> state.copy(steps = transform(state.steps), error = null)
            }
        }
    }

    fun saveRecipe() {
        val form = _formState.value
        if (form.isSaving) return

        val title = form.title.trim()
        val ingredients = form.ingredients.map(String::trim).filter(String::isNotEmpty)
        val steps = form.steps.map(String::trim).filter(String::isNotEmpty)
        val servings = form.servings.toIntOrNull()
        val prepMinutes = form.prepMinutes.toIntOrNull()

        val error = when {
            title.isEmpty() -> "El título no puede estar vacío"
            ingredients.isEmpty() -> "Añade al menos un ingrediente"
            steps.isEmpty() -> "Añade al menos un paso"
            servings == null || servings < 1 -> "Indica cuántas raciones salen (mínimo 1)"
            else -> null
        }
        if (error != null) {
            _formState.update { it.copy(error = error) }
            return
        }

        _formState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val request = CreateRecipeRequest(title, ingredients, steps, servings!!, prepMinutes)
            when (val result = repository.createRecipe(request)) {
                is ApiResult.Success -> {
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

    /** Deja el formulario vacío para la próxima receta (tras guardar o al abrirlo de nuevo). */
    fun resetForm() {
        if (!_formState.value.isSaving) _formState.value = RecipeFormState()
    }
}
