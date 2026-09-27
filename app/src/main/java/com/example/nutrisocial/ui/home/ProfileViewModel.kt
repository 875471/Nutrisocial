package com.example.nutrisocial.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.Profile
import com.example.nutrisocial.data.ProfileRepository
import com.example.nutrisocial.data.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la pestaña Perfil. [profile] es la última versión guardada en el servidor (con el
 * objetivo calórico calculado); los campos del formulario se guardan como texto tal cual se escriben.
 * Con [isEditing] a false se muestran los datos guardados, sin formulario.
 */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val profile: Profile? = null,
    // "AAAA-MM-DD"
    val birthDate: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val sex: String? = null,
    val activityLevel: String? = null,
    val goal: String? = null,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    // Aviso puntual tras guardar; la pantalla lo muestra y llama a onSavedMessageShown.
    val savedMessage: String? = null
)

class ProfileViewModel(
    private val repository: ProfileRepository = ProfileRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        _state.update { it.copy(isLoading = it.profile == null, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.getProfile()) {
                // La primera vez (perfil sin objetivo calculable) se abre directamente el formulario.
                is ApiResult.Success -> _state.update {
                    it.withProfile(result.data).copy(isLoading = false, isEditing = !result.data.isComplete)
                }
                is ApiResult.Error -> _state.update { it.copy(isLoading = false, loadError = result.message) }
            }
        }
    }

    /**
     * "Modificar" abre el formulario con los datos guardados; "Cancelar" lo cierra y descarta lo
     * escrito, volviendo a los valores del último perfil guardado.
     */
    fun onEditToggle() = _state.update { current ->
        when {
            current.isSaving -> current
            !current.isEditing -> current.copy(isEditing = true, saveError = null)
            current.profile != null -> current.withProfile(current.profile).copy(isEditing = false, saveError = null)
            else -> current
        }
    }

    fun onBirthDateChange(value: String) = _state.update { it.copy(birthDate = value, saveError = null) }
    fun onHeightChange(value: String) = _state.update { it.copy(heightCm = decimalInput(value), saveError = null) }
    fun onWeightChange(value: String) = _state.update { it.copy(weightKg = decimalInput(value), saveError = null) }
    fun onSexChange(value: String) = _state.update { it.copy(sex = value, saveError = null) }
    fun onActivityChange(value: String) = _state.update { it.copy(activityLevel = value, saveError = null) }
    fun onGoalChange(value: String) = _state.update { it.copy(goal = value, saveError = null) }
    fun onSavedMessageShown() = _state.update { it.copy(savedMessage = null) }

    fun save() {
        val current = _state.value
        if (current.isSaving) return
        val height = parseDecimal(current.heightCm)
        val weight = parseDecimal(current.weightKg)
        // Mismos rangos que valida el servidor, para avisar sin esperar a la red.
        val error = when {
            current.heightCm.isNotBlank() && (height == null || height !in 100.0..250.0) ->
                "La altura debe estar entre 100 y 250 cm"
            current.weightKg.isNotBlank() && (weight == null || weight !in 30.0..300.0) ->
                "El peso debe estar entre 30 y 300 kg"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(saveError = error) }
            return
        }

        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            val request = UpdateProfileRequest(
                birthDate = current.birthDate.ifBlank { null },
                heightCm = height,
                weightKg = weight,
                sex = current.sex,
                activityLevel = current.activityLevel,
                goal = current.goal
            )
            when (val result = repository.updateProfile(request)) {
                // Si aún faltan datos para el objetivo, el formulario sigue abierto para completarlos.
                is ApiResult.Success -> _state.update {
                    it.withProfile(result.data).copy(
                        isSaving = false,
                        isEditing = !result.data.isComplete,
                        savedMessage = "Perfil guardado"
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(isSaving = false, saveError = result.message) }
            }
        }
    }

    private fun ProfileUiState.withProfile(profile: Profile) = copy(
        profile = profile,
        birthDate = profile.birthDate.orEmpty(),
        heightCm = profile.heightCm?.let(::formatDecimal).orEmpty(),
        weightKg = profile.weightKg?.let(::formatDecimal).orEmpty(),
        sex = profile.sex,
        activityLevel = profile.activityLevel,
        goal = profile.goal
    )
}

/** Perfil con todos los datos necesarios para calcular el objetivo calórico. */
internal val Profile.isComplete: Boolean get() = dailyCalorieGoal != null

/** Solo dígitos y un separador decimal (coma o punto), como el campo de cantidad de las recetas. */
internal fun decimalInput(value: String, maxLength: Int = 6): String {
    var separatorSeen = false
    return value.filter { c ->
        c.isDigit() || ((c == ',' || c == '.') && !separatorSeen).also { if (it) separatorSeen = true }
    }.take(maxLength)
}

internal fun parseDecimal(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

/** 72.0 → "72", 72.5 → "72,5". */
internal fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else "%.1f".format(java.util.Locale.ROOT, value).replace('.', ',')
