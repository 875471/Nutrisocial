package com.example.nutrisocial.ui.explore

import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.FeedPage
import com.example.nutrisocial.ui.home.FeedViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Orden de los resultados del buscador, con los parámetros que espera GET /recipes/search. */
enum class SearchSort(val label: String, val sortBy: String, val order: String) {
    NEWEST("Más recientes", "createdAt", "desc"),
    QUICKEST("Más rápidas", "prepMinutes", "asc"),
    LONGEST("Más elaboradas", "prepMinutes", "desc")
}

data class SearchParams(val query: String = "", val sort: SearchSort = SearchSort.NEWEST)

/**
 * "Explorar": buscador de recetas de cualquier autor. Es un [FeedViewModel] cuyas páginas salen
 * del buscador, así que las tarjetas tienen los mismos "me gusta", guardados y comentarios.
 */
class RecipeSearchViewModel : FeedViewModel() {

    // Lo que se ve en el campo y en los filtros.
    private val _params = MutableStateFlow(SearchParams())
    val params: StateFlow<SearchParams> = _params.asStateFlow()

    // Con lo que se ha pedido la lista actual: "Cargar más" sigue con la misma búsqueda aunque
    // el texto del campo ya haya cambiado y la nueva todavía no haya empezado.
    private var activeParams = SearchParams()
    private var debounceJob: Job? = null

    init {
        search()
    }

    override suspend fun fetchPage(cursor: Int?): ApiResult<FeedPage> {
        val params = activeParams
        return feedRepository.search(params.query, params.sort.sortBy, params.sort.order, cursor)
    }

    /** Busca al dejar de escribir un momento, no con cada letra. */
    fun onQueryChange(query: String) {
        _params.update { it.copy(query = query) }
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(SEARCH_DELAY_MS)
            search()
        }
    }

    fun onSortChange(sort: SearchSort) {
        if (sort == _params.value.sort) return
        _params.update { it.copy(sort = sort) }
        search()
    }

    /** Borra el texto (al plegar el buscador) y vuelve al listado sin búsqueda, sin esperar. */
    fun clearQuery() {
        if (_params.value.query.isEmpty() && activeParams.query.isEmpty()) return
        _params.update { it.copy(query = "") }
        search()
    }

    /** Busca ya con lo que haya en el campo (p. ej. al pulsar la lupa del teclado). */
    fun search() {
        debounceJob?.cancel()
        activeParams = _params.value.copy(query = _params.value.query.trim())
        restart()
    }

    private companion object {
        const val SEARCH_DELAY_MS = 350L
    }
}
