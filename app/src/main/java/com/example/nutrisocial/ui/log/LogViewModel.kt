package com.example.nutrisocial.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.CalendarMonth
import com.example.nutrisocial.data.DailyLog
import com.example.nutrisocial.data.DailyRecommendations
import com.example.nutrisocial.data.FoodSuggestion
import com.example.nutrisocial.data.LogRepository
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.data.RecipeRecommendation
import com.example.nutrisocial.data.RecipeRepository
import com.example.nutrisocial.ui.home.decimalInput
import com.example.nutrisocial.ui.home.parseDecimal
import com.example.nutrisocial.ui.monthOf
import com.example.nutrisocial.ui.shiftDay
import com.example.nutrisocial.ui.shiftMonth
import com.example.nutrisocial.ui.todayIso
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DayUiState {
    data object Loading : DayUiState
    data class Success(val log: DailyLog) : DayUiState
    data class Error(val message: String) : DayUiState
}

sealed interface RecommendationsUiState {
    data object Loading : RecommendationsUiState
    data class Success(val data: DailyRecommendations) : RecommendationsUiState
    data class Error(val message: String) : RecommendationsUiState
}

sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data class Success(val data: CalendarMonth) : CalendarUiState
    data class Error(val message: String) : CalendarUiState
}

/** Vista de la pestaña Diario: un día (con sus entradas) o el calendario del mes. */
enum class LogViewMode { DAY, MONTH }

enum class AddSource { RECIPE, FOOD }

/** Añadir una receta al diario de hoy desde su pantalla de detalle. */
data class QuickAddState(
    val isSaving: Boolean = false,
    // Resultado (añadido o error) para un Snackbar en el detalle.
    val message: String? = null
)

/**
 * Hoja "Añadir al diario". Se elige primero la receta o el alimento y después la cantidad
 * (raciones o gramos), escrita como texto tal cual.
 */
data class AddEntryState(
    val source: AddSource = AddSource.RECIPE,
    val selectedRecipe: Recipe? = null,
    val servings: String = "1",
    val foodQuery: String = "",
    val suggestions: List<FoodSuggestion> = emptyList(),
    val isSearching: Boolean = false,
    val selectedFood: FoodSuggestion? = null,
    val grams: String = "100",
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val servingsValue: Double? get() = parseDecimal(servings)?.takeIf { it in 0.1..20.0 }
    val gramsValue: Double? get() = parseDecimal(grams)?.takeIf { it in 1.0..5000.0 }

    /** Kcal aproximadas de lo que se va a añadir, para mostrarlas antes de confirmar. */
    val previewKcal: Double?
        get() = when (source) {
            AddSource.RECIPE -> selectedRecipe?.let { r -> servingsValue?.let { r.nutrition.perServing.kcal * it } }
            AddSource.FOOD -> selectedFood?.let { f -> gramsValue?.let { f.kcal * it / 100 } }
        }

    val canSave: Boolean
        get() = !isSaving && when (source) {
            AddSource.RECIPE -> selectedRecipe != null && servingsValue != null
            AddSource.FOOD -> selectedFood != null && gramsValue != null
        }
}

class LogViewModel(
    private val logRepository: LogRepository = LogRepository(),
    private val recipeRepository: RecipeRepository = RecipeRepository()
) : ViewModel() {

    // Día seleccionado ("AAAA-MM-DD"). Por defecto hoy.
    private val _date = MutableStateFlow(todayIso())
    val date: StateFlow<String> = _date.asStateFlow()

    private val _dayState = MutableStateFlow<DayUiState>(DayUiState.Loading)
    val dayState: StateFlow<DayUiState> = _dayState.asStateFlow()

    // null: la hoja de añadir está cerrada.
    private val _addState = MutableStateFlow<AddEntryState?>(null)
    val addState: StateFlow<AddEntryState?> = _addState.asStateFlow()

    private val _recommendationsState = MutableStateFlow<RecommendationsUiState>(RecommendationsUiState.Loading)
    val recommendationsState: StateFlow<RecommendationsUiState> = _recommendationsState.asStateFlow()

    // Id de la receta recomendada que se está añadiendo, para desactivar su botón.
    private val _addingRecommendationId = MutableStateFlow<Int?>(null)
    val addingRecommendationId: StateFlow<Int?> = _addingRecommendationId.asStateFlow()

    // Separado de [message]: lo muestra la pantalla de detalle de receta, no el diario.
    private val _quickAddState = MutableStateFlow(QuickAddState())
    val quickAddState: StateFlow<QuickAddState> = _quickAddState.asStateFlow()

    // Mensajes puntuales (entrada añadida, error al borrar...) para un Snackbar.
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _viewMode = MutableStateFlow(LogViewMode.DAY)
    val viewMode: StateFlow<LogViewMode> = _viewMode.asStateFlow()

    // Mes que enseña el calendario ("AAAA-MM"). Al abrirlo, el del día seleccionado.
    private val _month = MutableStateFlow(monthOf(todayIso()))
    val month: StateFlow<String> = _month.asStateFlow()

    private val _calendarState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val calendarState: StateFlow<CalendarUiState> = _calendarState.asStateFlow()

    private var loadJob: Job? = null
    private var calendarJob: Job? = null
    private var searchJob: Job? = null
    private var recommendationsJob: Job? = null

    init {
        loadDay()
    }

    // ---- Día ----

    fun previousDay() = selectDate(shiftDay(_date.value, -1))
    fun nextDay() = selectDate(shiftDay(_date.value, 1))
    fun goToToday() = selectDate(todayIso())

    fun selectDate(date: String) {
        if (date == _date.value) return
        _date.value = date
        _dayState.value = DayUiState.Loading
        _recommendationsState.value = RecommendationsUiState.Loading
        loadDay()
    }

    /** Al entrar en la pestaña: el objetivo o las entradas pueden haber cambiado desde fuera. */
    fun onScreenShown() {
        loadDay()
        if (_viewMode.value == LogViewMode.MONTH) loadCalendar()
    }

    // ---- Calendario mensual ----

    /** Pasa a la vista de mes, empezando por el mes del día que se estaba viendo. */
    fun showMonth() {
        _viewMode.value = LogViewMode.MONTH
        val month = monthOf(_date.value)
        if (month != _month.value) {
            _month.value = month
            _calendarState.value = CalendarUiState.Loading
        }
        // Se recarga siempre: desde la última vez se pueden haber añadido o quitado entradas.
        loadCalendar()
    }

    fun showDay() {
        _viewMode.value = LogViewMode.DAY
    }

    fun previousMonth() = selectMonth(shiftMonth(_month.value, -1))
    fun nextMonth() = selectMonth(shiftMonth(_month.value, 1))

    private fun selectMonth(month: String) {
        _month.value = month
        _calendarState.value = CalendarUiState.Loading
        loadCalendar()
    }

    fun loadCalendar() {
        val month = _month.value
        calendarJob?.cancel()
        calendarJob = viewModelScope.launch {
            when (val result = logRepository.getCalendar(month)) {
                is ApiResult.Success -> _calendarState.value = CalendarUiState.Success(result.data)
                is ApiResult.Error -> if (_calendarState.value !is CalendarUiState.Success) {
                    _calendarState.value = CalendarUiState.Error(result.message)
                } else {
                    _message.value = result.message
                }
            }
        }
    }

    /** Al tocar un día del calendario se abre la vista de ese día. */
    fun openCalendarDay(date: String) {
        _viewMode.value = LogViewMode.DAY
        selectDate(date)
    }

    /** Recarga el día actual; los datos visibles se mantienen mientras llega la respuesta. */
    fun loadDay() {
        val date = _date.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = logRepository.getDailyLog(date)) {
                is ApiResult.Success -> _dayState.value = DayUiState.Success(result.data)
                is ApiResult.Error -> if (_dayState.value !is DayUiState.Success) {
                    _dayState.value = DayUiState.Error(result.message)
                } else {
                    _message.value = result.message
                }
            }
        }
        loadRecommendations()
    }

    /** Las recomendaciones dependen de lo registrado: se recargan siempre junto con el día. */
    fun loadRecommendations() {
        val date = _date.value
        recommendationsJob?.cancel()
        recommendationsJob = viewModelScope.launch {
            _recommendationsState.value = when (val result = logRepository.getRecommendations(date)) {
                is ApiResult.Success -> RecommendationsUiState.Success(result.data)
                is ApiResult.Error -> RecommendationsUiState.Error(result.message)
            }
        }
    }

    /** Registra una ración de la receta recomendada en el día seleccionado. */
    fun addRecommendation(recommendation: RecipeRecommendation) {
        if (_addingRecommendationId.value != null) return
        _addingRecommendationId.value = recommendation.id
        viewModelScope.launch {
            when (val result = logRepository.addRecipe(_date.value, recommendation.id, 1.0)) {
                is ApiResult.Success -> {
                    _message.value = "Añadido: ${result.data.name}"
                    loadDay()
                }
                is ApiResult.Error -> _message.value = result.message
            }
            _addingRecommendationId.value = null
        }
    }

    /**
     * Registra [servings] raciones de la receta en el diario de hoy, sea cual sea el día que se
     * esté viendo en la pestaña Diario: la acción es "me estoy comiendo esto ahora". Si el diario
     * está mostrando hoy, se recarga como al añadir una recomendación.
     */
    fun addRecipeFromDetail(recipeId: Int, servings: Double) {
        if (_quickAddState.value.isSaving) return
        val today = todayIso()
        _quickAddState.value = QuickAddState(isSaving = true)
        viewModelScope.launch {
            _quickAddState.value = when (val result = logRepository.addRecipe(today, recipeId, servings)) {
                is ApiResult.Success -> {
                    if (_date.value == today) loadDay()
                    QuickAddState(message = "Añadido a tu diario de hoy: ${result.data.name}")
                }
                is ApiResult.Error -> QuickAddState(message = result.message)
            }
        }
    }

    fun onQuickAddMessageShown() = _quickAddState.update { it.copy(message = null) }

    /**
     * Cambia la cantidad de una entrada ([servings] si es de receta, [grams] si es de alimento).
     * La lista se actualiza con lo que devuelve el servidor y el día se recarga para refrescar los
     * totales y los objetivos; si falla, la entrada se queda como estaba.
     */
    fun updateEntry(id: Int, servings: Double?, grams: Double?) {
        viewModelScope.launch {
            when (val result = logRepository.updateEntry(id, servings = servings, grams = grams)) {
                is ApiResult.Success -> {
                    val updated = result.data
                    _dayState.update { state ->
                        if (state is DayUiState.Success) {
                            DayUiState.Success(state.log.copy(entries = state.log.entries.map { if (it.id == id) updated else it }))
                        } else state
                    }
                    _message.value = "Actualizado: ${updated.name}"
                    loadDay()
                }
                is ApiResult.Error -> _message.value = result.message
            }
        }
    }

    fun deleteEntry(id: Int) {
        // Se quita de la lista al momento; los totales se recalculan al recargar el día.
        val current = _dayState.value
        if (current is DayUiState.Success) {
            _dayState.value = DayUiState.Success(current.log.copy(entries = current.log.entries.filterNot { it.id == id }))
        }
        viewModelScope.launch {
            val result = logRepository.deleteEntry(id)
            if (result is ApiResult.Error) _message.value = result.message
            loadDay()
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    // ---- Añadir ----

    fun openAddSheet() {
        _addState.value = AddEntryState()
    }

    fun closeAddSheet() {
        searchJob?.cancel()
        _addState.value = null
    }

    fun onSourceChange(source: AddSource) = updateAdd { it.copy(source = source, error = null) }

    fun onRecipeSelected(recipe: Recipe?) = updateAdd { it.copy(selectedRecipe = recipe, error = null) }

    fun onServingsChange(value: String) = updateAdd { it.copy(servings = decimalInput(value, 4), error = null) }

    fun onGramsChange(value: String) = updateAdd { it.copy(grams = decimalInput(value, 6), error = null) }

    fun onFoodSelected(food: FoodSuggestion?) {
        searchJob?.cancel()
        updateAdd {
            it.copy(selectedFood = food, suggestions = emptyList(), isSearching = false, foodQuery = food?.name ?: it.foodQuery, error = null)
        }
    }

    /** Mismo buscador de alimentos que el formulario de recetas, con una pequeña espera. */
    fun onFoodQueryChange(query: String) {
        updateAdd { it.copy(foodQuery = query, selectedFood = null, error = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            updateAdd { it.copy(suggestions = emptyList(), isSearching = false) }
            return
        }
        updateAdd { it.copy(isSearching = true) }
        searchJob = viewModelScope.launch {
            delay(300)
            val result = recipeRepository.searchFoods(query.trim())
            updateAdd {
                it.copy(
                    suggestions = (result as? ApiResult.Success)?.data.orEmpty(),
                    isSearching = false,
                    error = (result as? ApiResult.Error)?.message
                )
            }
        }
    }

    fun saveEntry() {
        val state = _addState.value ?: return
        if (!state.canSave) return
        val date = _date.value
        updateAdd { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = when (state.source) {
                AddSource.RECIPE -> logRepository.addRecipe(date, state.selectedRecipe!!.id, state.servingsValue!!)
                AddSource.FOOD -> logRepository.addFood(date, state.selectedFood!!.id, state.gramsValue!!)
            }
            when (result) {
                is ApiResult.Success -> {
                    _addState.value = null
                    _message.value = "Añadido: ${result.data.name}"
                    loadDay()
                }
                is ApiResult.Error -> updateAdd { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    private fun updateAdd(transform: (AddEntryState) -> AddEntryState) {
        _addState.update { it?.let(transform) }
    }
}
