package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient

class AuthRepository(
    private val api: ApiService = RetrofitClient.api
) {
    suspend fun register(name: String, email: String, password: String): ApiResult<RegisterResponse> =
        safeApiCall(::authErrorMessage) {
            api.register(RegisterRequest(email = email, password = password, name = name))
        }

    suspend fun login(email: String, password: String): ApiResult<LoginResponse> =
        safeApiCall(::authErrorMessage) { api.login(LoginRequest(email = email, password = password)) }

    /** Reenvía el correo de verificación. La respuesta es la misma exista o no la cuenta. */
    suspend fun resendVerification(email: String): ApiResult<MessageResponse> =
        safeApiCall(::authErrorMessage) { api.resendVerification(EmailRequest(email)) }

    /** Pide el código de un solo uso para cambiar la contraseña. */
    suspend fun forgotPassword(email: String): ApiResult<MessageResponse> =
        safeApiCall(::authErrorMessage) { api.forgotPassword(EmailRequest(email)) }

    suspend fun resetPassword(email: String, code: String, password: String): ApiResult<MessageResponse> =
        safeApiCall(::authErrorMessage) { api.resetPassword(ResetPasswordRequest(email, code, password)) }

    /** Borra la cuenta tras comprobar [password] en el servidor. */
    suspend fun deleteAccount(password: String): ApiResult<Unit> =
        safeApiCallNoContent(defaultErrorMessage = { code ->
            when (code) {
                403 -> "La contraseña no es correcta"
                404 -> "La cuenta ya no existe"
                429 -> "Demasiados intentos. Espera unos minutos."
                else -> null
            }
        }) { api.deleteAccount(DeleteAccountRequest(password)) }

    suspend fun isBackendReachable(): Boolean =
        safeApiCall { api.getHealth() } is ApiResult.Success

    private fun authErrorMessage(code: Int): String? = when (code) {
        401 -> "Email o contraseña incorrectos"
        409 -> "Ya existe una cuenta con ese email"
        429 -> "Demasiados intentos. Espera unos minutos antes de volver a probar."
        else -> null
    }
}
