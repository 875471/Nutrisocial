package com.example.nutrisocial.data

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException

// Resultado de una operación de red: nunca se propagan excepciones a la UI.
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

private val gson = Gson()

/**
 * Ejecuta una llamada de Retrofit y la traduce a [ApiResult]. En respuestas de error usa el
 * mensaje `{ "error": ... }` del backend; si no lo hay, [defaultErrorMessage] o uno genérico.
 */
suspend fun <T> safeApiCall(
    defaultErrorMessage: (code: Int) -> String? = { null },
    call: suspend () -> Response<T>
): ApiResult<T> {
    return try {
        val response = call()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ApiResult.Success(body)
        } else if (response.isSuccessful) {
            ApiResult.Error("Respuesta vacía del servidor", response.code())
        } else {
            val message = parseErrorBody(response)
                ?: defaultErrorMessage(response.code())
                ?: genericErrorMessage(response.code())
            ApiResult.Error(message, response.code())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        ApiResult.Error("No se pudo conectar con el servidor. Comprueba tu conexión.")
    } catch (e: Exception) {
        ApiResult.Error("Error inesperado: ${e.message ?: e::class.java.simpleName}")
    }
}

/**
 * Como [safeApiCall], para respuestas sin cuerpo (204). Retrofit entrega el cuerpo de un 204 como
 * null aunque el tipo sea Unit, y [safeApiCall] lo trataría como "Respuesta vacía".
 */
suspend fun safeApiCallNoContent(
    defaultErrorMessage: (code: Int) -> String? = { null },
    call: suspend () -> Response<Unit>
): ApiResult<Unit> = safeApiCall(defaultErrorMessage) {
    val response = call()
    if (response.isSuccessful) Response.success(Unit, response.raw()) else response
}

private fun parseErrorBody(response: Response<*>): String? = try {
    response.errorBody()?.string()
        ?.let { gson.fromJson(it, ApiErrorBody::class.java)?.error }
        ?.takeIf { it.isNotBlank() }
} catch (e: Exception) {
    null
}

private fun genericErrorMessage(code: Int): String = when (code) {
    400 -> "Datos incorrectos o incompletos"
    401 -> "Tu sesión no es válida. Vuelve a iniciar sesión."
    404 -> "No se ha encontrado lo que buscabas"
    // Si el proxy de Render corta la petición antes de llegar a Express, no hay JSON con el motivo.
    413 -> "La petición es demasiado grande. Si lleva una foto, prueba con otra más pequeña."
    429 -> "Demasiados intentos. Espera unos minutos."
    in 500..599 -> "Error del servidor ($code)"
    else -> "Error inesperado ($code)"
}
