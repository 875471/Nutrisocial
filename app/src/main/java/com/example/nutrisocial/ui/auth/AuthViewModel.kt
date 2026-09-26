package com.example.nutrisocial.ui.auth

import android.app.Application
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.AuthRepository
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.SessionManager
import com.example.nutrisocial.data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object Success : AuthUiState
    data class Error(val message: String) : AuthUiState
}

sealed interface SessionState {
    data object Checking : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: User) : SessionState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository()
    private val sessionManager = SessionManager(application)

    // Estado global de sesión, leído de DataStore. Empieza en Checking hasta la primera lectura.
    val sessionState: StateFlow<SessionState> = sessionManager.session
        .map { session -> if (session != null) SessionState.LoggedIn(session.user) else SessionState.LoggedOut }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Checking)

    private val _loginState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val loginState: StateFlow<AuthUiState> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val registerState: StateFlow<AuthUiState> = _registerState.asStateFlow()

    init {
        // Comprobación interna de conectividad; no afecta a la UI.
        viewModelScope.launch {
            val reachable = repository.isBackendReachable()
            Log.d(TAG, "Backend /health accesible: $reachable")
        }
    }

    fun login(email: String, password: String) {
        if (_loginState.value is AuthUiState.Loading) return
        val cleanEmail = email.trim()
        validate(name = null, email = cleanEmail, password = password)?.let {
            _loginState.value = AuthUiState.Error(it)
            return
        }

        _loginState.value = AuthUiState.Loading
        viewModelScope.launch {
            _loginState.value = loginAndSave(cleanEmail, password)
        }
    }

    fun register(name: String, email: String, password: String) {
        if (_registerState.value is AuthUiState.Loading) return
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        validate(name = cleanName, email = cleanEmail, password = password)?.let {
            _registerState.value = AuthUiState.Error(it)
            return
        }

        _registerState.value = AuthUiState.Loading
        viewModelScope.launch {
            _registerState.value = when (val result = repository.register(cleanName, cleanEmail, password)) {
                // /auth/register no devuelve token, así que se inicia sesión automáticamente.
                is ApiResult.Success -> loginAndSave(cleanEmail, password)
                is ApiResult.Error -> AuthUiState.Error(result.message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.clearSession()
            _loginState.value = AuthUiState.Idle
            _registerState.value = AuthUiState.Idle
        }
    }

    /** Limpia el mensaje de error/éxito, p. ej. al cambiar de pantalla. */
    fun resetStates() {
        if (_loginState.value !is AuthUiState.Loading) _loginState.value = AuthUiState.Idle
        if (_registerState.value !is AuthUiState.Loading) _registerState.value = AuthUiState.Idle
    }

    private suspend fun loginAndSave(email: String, password: String): AuthUiState =
        when (val result = repository.login(email, password)) {
            is ApiResult.Success -> {
                sessionManager.saveSession(result.data.token, result.data.user)
                AuthUiState.Success
            }
            is ApiResult.Error -> AuthUiState.Error(result.message)
        }

    /** Devuelve el mensaje de error de validación, o null si los datos son válidos. */
    private fun validate(name: String?, email: String, password: String): String? = when {
        name != null && name.isBlank() -> "El nombre no puede estar vacío"
        email.isBlank() -> "El email no puede estar vacío"
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "El formato del email no es válido"
        password.isBlank() -> "La contraseña no puede estar vacía"
        password.length < MIN_PASSWORD_LENGTH -> "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres"
        else -> null
    }

    private companion object {
        const val TAG = "AuthViewModel"
        const val MIN_PASSWORD_LENGTH = 4
    }
}
