package com.example.nutrisocial.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.Plan
import com.example.nutrisocial.data.SubscriptionRepository
import com.example.nutrisocial.data.SubscriptionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la suscripción Premium. [status] es null mientras no se sabe (primera carga);
 * [statusError] indica que esa carga falló. [message] es un error que enseña la pantalla de
 * planes y [confirmation] el aviso de alta o baja, que se muestra ya fuera de ella.
 */
data class SubscriptionUiState(
    val status: SubscriptionStatus? = null,
    val statusError: String? = null,
    val plans: List<Plan> = emptyList(),
    val plansLoading: Boolean = false,
    val plansError: String? = null,
    val submittingPlanId: String? = null,
    val canceling: Boolean = false,
    val message: String? = null,
    val confirmation: String? = null
) {
    /**
     * true o false si se sabe; null mientras carga. Si la carga falla se trata como gratis: el
     * backend no daría acceso a las funciones Premium sin poder comprobarlo tampoco.
     */
    val isPremium: Boolean?
        get() = status?.isPremium ?: if (statusError != null) false else null

    val busy: Boolean get() = submittingPlanId != null || canceling
}

/**
 * Suscripción del usuario. Con ámbito en HomeScreen: el Diario, el escáner y el perfil leen de
 * aquí si es Premium, y la pantalla de planes cambia ese estado para todos a la vez.
 */
class SubscriptionViewModel(
    private val repository: SubscriptionRepository = SubscriptionRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(SubscriptionUiState())
    val state: StateFlow<SubscriptionUiState> = _state.asStateFlow()

    private var statusJob: Job? = null

    init {
        loadStatus()
    }

    fun loadStatus() {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            when (val result = repository.getStatus()) {
                is ApiResult.Success -> _state.update { it.copy(status = result.data, statusError = null) }
                is ApiResult.Error -> _state.update { it.copy(statusError = result.message) }
            }
        }
    }

    /** Al entrar en una pantalla con funciones Premium: reintenta si la primera carga falló. */
    fun ensureStatus() {
        if (_state.value.statusError != null && statusJob?.isActive != true) loadStatus()
    }

    /** Al abrir la pantalla de planes: los precios y el estado, por si han cambiado. */
    fun open() {
        loadStatus()
        if (_state.value.plans.isNotEmpty()) return
        _state.update { it.copy(plansLoading = true, plansError = null) }
        viewModelScope.launch {
            when (val result = repository.getPlans()) {
                is ApiResult.Success -> _state.update { it.copy(plans = result.data, plansLoading = false) }
                is ApiResult.Error -> _state.update { it.copy(plansError = result.message, plansLoading = false) }
            }
        }
    }

    fun subscribe(plan: Plan) {
        if (_state.value.busy) return
        _state.update { it.copy(submittingPlanId = plan.id) }
        viewModelScope.launch {
            when (val result = repository.subscribe(plan.id)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        status = result.data,
                        statusError = null,
                        submittingPlanId = null,
                        confirmation = "¡Ya eres Premium! Plan ${plan.label.lowercase()} activado."
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(submittingPlanId = null, message = result.message) }
            }
        }
    }

    fun cancel() {
        if (_state.value.busy) return
        _state.update { it.copy(canceling = true) }
        viewModelScope.launch {
            when (val result = repository.cancel()) {
                is ApiResult.Success -> _state.update {
                    it.copy(status = result.data, canceling = false, confirmation = "Suscripción cancelada. Ya no tienes Premium.")
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(canceling = false, message = result.message) }
                    // 404: ya no había suscripción activa (cancelada desde otro dispositivo, por ejemplo).
                    if (result.code == 404) loadStatus()
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onConfirmationShown() = _state.update { it.copy(confirmation = null) }
}
