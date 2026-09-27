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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object Success : AuthUiState
    data class Error(val message: String) : AuthUiState
    /** Login con la contraseña correcta pero el email sin confirmar (403 del servidor). */
    data class EmailNotVerified(val email: String, val message: String) : AuthUiState
    /** Registro hecho: falta confirmar el correo. [emailSent] es false si el servidor no pudo enviarlo. */
    data class AwaitingVerification(val email: String, val emailSent: Boolean) : AuthUiState
}

/** Reenvío del correo de verificación: en curso y el mensaje resultante. */
data class ResendState(val isSending: Boolean = false, val message: String? = null)

/**
 * Recuperación de contraseña en dos pasos: primero el email ([codeSent] = false) y después el
 * código del correo con la contraseña nueva. [info] es un mensaje neutro (código enviado).
 */
data class PasswordResetState(
    val email: String = "",
    val codeSent: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val done: Boolean = false
)

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

    private val _resendState = MutableStateFlow(ResendState())
    val resendState: StateFlow<ResendState> = _resendState.asStateFlow()

    private val _passwordResetState = MutableStateFlow(PasswordResetState())
    val passwordResetState: StateFlow<PasswordResetState> = _passwordResetState.asStateFlow()

    // Aviso en la pantalla de login (p. ej. "Contraseña cambiada") y email con el que rellenarla.
    private val _loginNotice = MutableStateFlow<String?>(null)
    val loginNotice: StateFlow<String?> = _loginNotice.asStateFlow()

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
        validate(name = null, email = cleanEmail, password = password, isRegistration = false)?.let {
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
        validate(name = cleanName, email = cleanEmail, password = password, isRegistration = true)?.let {
            _registerState.value = AuthUiState.Error(it)
            return
        }

        _registerState.value = AuthUiState.Loading
        viewModelScope.launch {
            _registerState.value = when (val result = repository.register(cleanName, cleanEmail, password)) {
                // Con verificación, hay que confirmar el correo antes de entrar. Un servidor
                // anterior a la verificación no manda el campo: se entra directamente como antes.
                is ApiResult.Success -> if (result.data.emailVerificationRequired) {
                    _resendState.value = ResendState()
                    AuthUiState.AwaitingVerification(cleanEmail, result.data.emailSent)
                } else {
                    loginAndSave(cleanEmail, password)
                }
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
        _resendState.value = ResendState()
        _loginNotice.value = null
    }

    /** Reenvía el correo para confirmar [email] (desde el login o desde la pantalla de registro). */
    fun resendVerification(email: String) {
        if (_resendState.value.isSending) return
        _resendState.value = ResendState(isSending = true)
        viewModelScope.launch {
            _resendState.value = when (val result = repository.resendVerification(email.trim())) {
                is ApiResult.Success -> ResendState(message = "Te hemos enviado un correo nuevo a ${email.trim()}. " +
                    "El enlace anterior ya no sirve.")
                is ApiResult.Error -> ResendState(message = result.message)
            }
        }
    }

    // ---- Recuperación de contraseña ----

    /** Abre el flujo desde el login, con el email que hubiera escrito. */
    fun startPasswordReset(email: String) {
        _passwordResetState.value = PasswordResetState(email = email.trim())
    }

    fun requestResetCode(email: String) {
        val cleanEmail = email.trim()
        if (_passwordResetState.value.isLoading) return
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            _passwordResetState.value = PasswordResetState(email = cleanEmail, error = "El formato del email no es válido")
            return
        }
        _passwordResetState.value = PasswordResetState(email = cleanEmail, isLoading = true)
        viewModelScope.launch {
            _passwordResetState.value = when (val result = repository.forgotPassword(cleanEmail)) {
                is ApiResult.Success -> PasswordResetState(email = cleanEmail, codeSent = true, info = result.data.message)
                is ApiResult.Error -> PasswordResetState(email = cleanEmail, error = result.message)
            }
        }
    }

    fun resetPassword(code: String, password: String, confirmation: String) {
        val current = _passwordResetState.value
        if (current.isLoading) return
        val error = when {
            code.isBlank() -> "Escribe el código que te hemos enviado"
            password.length < MIN_PASSWORD_LENGTH -> "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres"
            password != confirmation -> "Las dos contraseñas no coinciden"
            else -> null
        }
        if (error != null) {
            _passwordResetState.value = current.copy(error = error, info = null)
            return
        }
        _passwordResetState.value = current.copy(isLoading = true, error = null, info = null)
        viewModelScope.launch {
            when (val result = repository.resetPassword(current.email, code.trim(), password)) {
                is ApiResult.Success -> {
                    _loginNotice.value = result.data.message.ifBlank { "Contraseña cambiada. Ya puedes iniciar sesión." }
                    _loginState.value = AuthUiState.Idle
                    _passwordResetState.value = current.copy(isLoading = false, done = true)
                }
                is ApiResult.Error -> _passwordResetState.value = current.copy(isLoading = false, error = result.message)
            }
        }
    }

    /** Vuelve al paso del email para pedir otro código. */
    fun restartPasswordReset() {
        _passwordResetState.update { PasswordResetState(email = it.email) }
    }

    private suspend fun loginAndSave(email: String, password: String): AuthUiState =
        when (val result = repository.login(email, password)) {
            is ApiResult.Success -> {
                sessionManager.saveSession(result.data.token, result.data.user)
                AuthUiState.Success
            }
            // Contraseña correcta pero email sin confirmar: se ofrece reenviar el correo.
            is ApiResult.Error -> if (result.code == 403) {
                AuthUiState.EmailNotVerified(email, result.message)
            } else {
                AuthUiState.Error(result.message)
            }
        }

    /**
     * Devuelve el mensaje de error de validación, o null si los datos son válidos. La longitud
     * mínima solo se exige al registrarse (igual que el servidor): las cuentas creadas antes de
     * subirla a 8 caracteres pueden tener contraseñas más cortas y tienen que poder entrar.
     */
    private fun validate(name: String?, email: String, password: String, isRegistration: Boolean): String? = when {
        name != null && name.isBlank() -> "El nombre no puede estar vacío"
        email.isBlank() -> "El email no puede estar vacío"
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "El formato del email no es válido"
        password.isBlank() -> "La contraseña no puede estar vacía"
        isRegistration && password.length < MIN_PASSWORD_LENGTH ->
            "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres"
        else -> null
    }

    private companion object {
        const val TAG = "AuthViewModel"
        // El mismo mínimo que exige el servidor (backend/src/routes/auth.js).
        const val MIN_PASSWORD_LENGTH = 8
    }
}
