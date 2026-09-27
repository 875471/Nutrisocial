package com.example.nutrisocial.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.FoodSuggestion
import com.example.nutrisocial.data.PantryItem
import com.example.nutrisocial.data.PantryRepository
import com.example.nutrisocial.data.PantrySearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PantryItemsState {
    data object Loading : PantryItemsState
    data class Success(val items: List<PantryItem>) : PantryItemsState
    data class Error(val message: String) : PantryItemsState
}

sealed interface PantrySearchState {
    /** Todavía no se ha buscado, o la despensa ha cambiado desde la última búsqueda. */
    data object Idle : PantrySearchState
    data object Loading : PantrySearchState
    data class Success(val result: PantrySearchResult) : PantrySearchState
    data class Error(val message: String) : PantrySearchState
}

/** Campo para añadir un ingrediente, con las sugerencias de /foods/search. */
data class PantryInputState(
    val query: String = "",
    val suggestions: List<FoodSuggestion> = emptyList(),
    val isAdding: Boolean = false,
    val error: String? = null
)

class PantryViewModel(
    private val repository: PantryRepository = PantryRepository()
) : ViewModel() {

    private val _itemsState = MutableStateFlow<PantryItemsState>(PantryItemsState.Loading)
    val itemsState: StateFlow<PantryItemsState> = _itemsState.asStateFlow()

    private val _input = MutableStateFlow(PantryInputState())
    val input: StateFlow<PantryInputState> = _input.asStateFlow()

    private val _searchState = MutableStateFlow<PantrySearchState>(PantrySearchState.Idle)
    val searchState: StateFlow<PantrySearchState> = _searchState.asStateFlow()

    // Avisos puntuales (p. ej. un borrado que ha fallado), para un snackbar.
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadPantry()
    }

    fun loadPantry() {
        if (_itemsState.value !is PantryItemsState.Success) {
            _itemsState.value = PantryItemsState.Loading
        }
        viewModelScope.launch {
            _itemsState.value = when (val result = repository.getPantry()) {
                is ApiResult.Success -> PantryItemsState.Success(result.data)
                is ApiResult.Error -> PantryItemsState.Error(result.message)
            }
        }
    }

    // ---- Añadir ----

    private var suggestionsJob: Job? = null

    fun onQueryChange(value: String) {
        _input.update { it.copy(query = value, error = null) }
        suggestionsJob?.cancel()
        if (value.trim().length < 2) {
            dismissSuggestions()
            return
        }
        // Igual que en el formulario de recetas: se espera un poco para no buscar en cada tecla.
        suggestionsJob = viewModelScope.launch {
            delay(300)
            val result = repository.searchFoods(value.trim())
            if (result is ApiResult.Success) _input.update { it.copy(suggestions = result.data) }
        }
    }

    /** Elegir una sugerencia la añade directamente con el nombre del alimento. */
    fun onSuggestionSelected(food: FoodSuggestion) {
        _input.update { it.copy(query = food.name) }
        addItem()
    }

    fun dismissSuggestions() {
        suggestionsJob?.cancel()
        _input.update { it.copy(suggestions = emptyList()) }
    }

    fun addItem() {
        val current = _input.value
        val name = current.query.trim()
        if (current.isAdding || name.isEmpty()) return

        dismissSuggestions()
        _input.update { it.copy(isAdding = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.addItem(name)) {
                is ApiResult.Success -> {
                    val items = (_itemsState.value as? PantryItemsState.Success)?.items.orEmpty()
                    _itemsState.value = PantryItemsState.Success(listOf(result.data) + items)
                    _input.value = PantryInputState()
                    onPantryChanged()
                }
                is ApiResult.Error -> _input.update { it.copy(isAdding = false, error = result.message) }
            }
        }
    }

    // ---- Borrar ----

    fun deleteItem(item: PantryItem) {
        val before = (_itemsState.value as? PantryItemsState.Success)?.items ?: return
        // Se quita al momento y se restaura si el servidor falla.
        _itemsState.value = PantryItemsState.Success(before.filterNot { it.id == item.id })
        viewModelScope.launch {
            when (val result = repository.deleteItem(item.id)) {
                is ApiResult.Success -> onPantryChanged()
                is ApiResult.Error -> if (result.code == 404) {
                    // Ya no estaba en el servidor: la lista local queda como debe.
                    onPantryChanged()
                } else {
                    _itemsState.value = PantryItemsState.Success(before)
                    _message.value = "No se pudo quitar «${item.name}»: ${result.message}"
                }
            }
        }
    }

    // ---- Buscar recetas ----

    private var searchJob: Job? = null

    fun searchRecipes() {
        searchJob?.cancel()
        _searchState.value = PantrySearchState.Loading
        searchJob = viewModelScope.launch {
            _searchState.value = when (val result = repository.findRecipes()) {
                is ApiResult.Success -> PantrySearchState.Success(result.data)
                is ApiResult.Error -> PantrySearchState.Error(result.message)
            }
        }
    }

    /** Si ya se estaban mostrando resultados, se recalculan con la despensa nueva. */
    private fun onPantryChanged() {
        when (_searchState.value) {
            is PantrySearchState.Success, PantrySearchState.Loading -> searchRecipes()
            else -> _searchState.value = PantrySearchState.Idle
        }
    }

    fun onMessageShown() {
        _message.value = null
    }
}
