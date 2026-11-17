package com.example.nutrisocial.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.LogRepository
import com.example.nutrisocial.data.LogStats
import com.example.nutrisocial.ui.todayIso
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Periodos que ofrece la pantalla de estadísticas (los mismos que acepta GET /log/stats). */
enum class StatsPeriod(val days: Int, val label: String) {
    WEEK(7, "7 días"),
    MONTH(30, "30 días")
}

sealed interface LogStatsUiState {
    data object Loading : LogStatsUiState
    data class Success(val stats: LogStats) : LogStatsUiState
    data class Error(val message: String) : LogStatsUiState
}

/**
 * Estadísticas del diario: calorías y macronutrientes de los últimos 7 o 30 días. [today] se
 * inyecta para poder probarlo con una fecha fija; el servidor recibe el "hoy" del móvil, que es
 * quien conoce la zona horaria.
 */
class LogStatsViewModel(
    private val repository: LogRepository = LogRepository(),
    private val today: () -> String = ::todayIso
) : ViewModel() {

    private val _period = MutableStateFlow(StatsPeriod.WEEK)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    private val _state = MutableStateFlow<LogStatsUiState>(LogStatsUiState.Loading)
    val state: StateFlow<LogStatsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun selectPeriod(period: StatsPeriod) {
        if (period == _period.value) return
        _period.value = period
        load()
    }

    fun load() {
        // Si se cambia de periodo con una carga en marcha, la anterior ya no interesa.
        loadJob?.cancel()
        _state.value = LogStatsUiState.Loading
        val requested = _period.value
        loadJob = viewModelScope.launch {
            _state.value = when (val result = repository.getStats(requested.days, today())) {
                is ApiResult.Success -> LogStatsUiState.Success(result.data)
                is ApiResult.Error -> LogStatsUiState.Error(result.message)
            }
        }
    }
}
