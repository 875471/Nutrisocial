package com.example.nutrisocial.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.ShoppingListItem
import com.example.nutrisocial.data.ShoppingListRepository
import com.example.nutrisocial.data.fromRecipeMessage
import com.example.nutrisocial.data.sortedForShopping
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la lista de la compra. [items] es null hasta la primera carga; [loadError] es el
 * error de esa carga. [message] es un aviso puntual de la propia pantalla (snackbar).
 */
data class ShoppingListUiState(
    val items: List<ShoppingListItem>? = null,
    val loadError: String? = null,
    val isRefreshing: Boolean = false,
    // Campo para añadir un elemento suelto.
    val newLabel: String = "",
    val newQuantity: String = "",
    val isAdding: Boolean = false,
    val isClearing: Boolean = false,
    val message: String? = null,
    // Receta cuyo "Añadir lo que falta" está en curso, y el aviso con el resultado (para el detalle).
    val addingFromRecipeId: Int? = null,
    val fromRecipeMessage: String? = null
) {
    val checkedCount: Int get() = items?.count { it.checked } ?: 0
}

/**
 * Lista de la compra. Con ámbito en HomeScreen, como SubscriptionViewModel: el detalle de una
 * receta añade aquí lo que falta y la pantalla de la lista lo ve sin recargar.
 */
class ShoppingListViewModel(
    private val repository: ShoppingListRepository = ShoppingListRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ShoppingListUiState())
    val state: StateFlow<ShoppingListUiState> = _state.asStateFlow()

    /** Al abrir la pantalla: primera carga o, si ya había lista, recarga sin borrarla. */
    fun open() {
        if (_state.value.items == null) load() else refresh()
    }

    fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            when (val result = repository.getItems()) {
                is ApiResult.Success -> _state.update { it.copy(items = result.data, loadError = null) }
                is ApiResult.Error -> _state.update { it.copy(loadError = result.message) }
            }
        }
    }

    /** Tirar para refrescar. Si falla con la lista ya cargada, se conserva y se avisa. */
    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            when (val result = repository.getItems()) {
                is ApiResult.Success -> _state.update { it.copy(items = result.data, loadError = null, isRefreshing = false) }
                is ApiResult.Error -> _state.update {
                    if (it.items != null) it.copy(isRefreshing = false, message = "No se pudo actualizar: ${result.message}")
                    else it.copy(isRefreshing = false, loadError = result.message)
                }
            }
        }
    }

    fun onNewLabelChange(value: String) = _state.update { it.copy(newLabel = value.take(MAX_LABEL)) }

    fun onNewQuantityChange(value: String) = _state.update { it.copy(newQuantity = value.take(MAX_QUANTITY)) }

    fun addItem() {
        val current = _state.value
        val label = current.newLabel.trim()
        if (current.isAdding || label.isEmpty()) return
        _state.update { it.copy(isAdding = true) }
        viewModelScope.launch {
            when (val result = repository.addItem(label, current.newQuantity.trim().ifEmpty { null })) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        items = (it.items.orEmpty() + result.data).sortedForShopping(),
                        newLabel = "",
                        newQuantity = "",
                        isAdding = false
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(isAdding = false, message = result.message) }
            }
        }
    }

    /** Marca o desmarca al momento; si el servidor falla, se deshace y se avisa. */
    fun toggleChecked(item: ShoppingListItem) {
        val checked = !item.checked
        replaceItem(item.copy(checked = checked))
        viewModelScope.launch {
            when (val result = repository.setChecked(item.id, checked)) {
                is ApiResult.Success -> replaceItem(result.data)
                is ApiResult.Error -> if (result.code == 404) {
                    removeLocally(item.id)
                    _state.update { it.copy(message = result.message) }
                } else {
                    replaceItem(item)
                    _state.update { it.copy(message = "No se pudo actualizar «${item.label}»: ${result.message}") }
                }
            }
        }
    }

    fun deleteItem(item: ShoppingListItem) {
        val before = _state.value.items ?: return
        removeLocally(item.id)
        viewModelScope.launch {
            val result = repository.deleteItem(item.id)
            // 404: ya no estaba en el servidor, la lista local queda como debe.
            if (result is ApiResult.Error && result.code != 404) {
                _state.update { it.copy(items = before, message = "No se pudo quitar «${item.label}»: ${result.message}") }
            }
        }
    }

    /** Vacía los marcados. No los pasa a la despensa: eso lo hace el usuario a mano. */
    fun clearChecked() {
        val current = _state.value
        if (current.isClearing || current.checkedCount == 0) return
        _state.update { it.copy(isClearing = true) }
        viewModelScope.launch {
            when (val result = repository.clearChecked()) {
                is ApiResult.Success -> _state.update { state ->
                    state.copy(
                        items = state.items?.filterNot { it.checked },
                        isClearing = false,
                        message = if (result.data.deleted == 1) "Quitado 1 elemento comprado" else "Quitados ${result.data.deleted} elementos comprados"
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(isClearing = false, message = result.message) }
            }
        }
    }

    /** Botón del detalle de receta: añade los ingredientes que faltan en la despensa. */
    fun addMissingFromRecipe(recipeId: Int) {
        if (_state.value.addingFromRecipeId != null) return
        _state.update { it.copy(addingFromRecipeId = recipeId) }
        viewModelScope.launch {
            when (val result = repository.addMissingFromRecipe(recipeId)) {
                is ApiResult.Success -> _state.update { state ->
                    state.copy(
                        // Si la lista no se ha cargado todavía, se cargará entera al abrirla.
                        items = state.items?.let { (it + result.data.added).sortedForShopping() },
                        addingFromRecipeId = null,
                        fromRecipeMessage = fromRecipeMessage(result.data)
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(addingFromRecipeId = null, fromRecipeMessage = result.message) }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onFromRecipeMessageShown() = _state.update { it.copy(fromRecipeMessage = null) }

    private fun replaceItem(item: ShoppingListItem) = _state.update { state ->
        state.copy(items = state.items?.map { if (it.id == item.id) item else it }?.sortedForShopping())
    }

    private fun removeLocally(id: Int) = _state.update { state -> state.copy(items = state.items?.filterNot { it.id == id }) }

    private companion object {
        // Los mismos topes que el servidor (backend/src/routes/shoppingList.js).
        const val MAX_LABEL = 100
        const val MAX_QUANTITY = 50
    }
}
