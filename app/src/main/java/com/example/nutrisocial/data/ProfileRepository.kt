package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

class ProfileRepository(
    private val api: ApiService = RetrofitClient.api
) {
    suspend fun getProfile(): ApiResult<Profile> =
        safeApiCall { api.getProfile() }

    suspend fun updateProfile(request: UpdateProfileRequest): ApiResult<Profile> =
        safeApiCall { api.updateProfile(request) }

    /**
     * Descarga la exportación de la cuenta (GET /profile/export) y la escribe en el flujo que abre
     * [openOutput] (el archivo que ha elegido el usuario). Devuelve los bytes escritos.
     */
    suspend fun exportAccount(openOutput: () -> OutputStream?): ApiResult<Long> = withContext(Dispatchers.IO) {
        when (val result = safeApiCall { api.exportAccount() }) {
            is ApiResult.Error -> result
            is ApiResult.Success -> try {
                val output = openOutput() ?: return@withContext ApiResult.Error("No se pudo crear el archivo")
                val bytes = result.data.use { body -> output.use { out -> body.byteStream().copyTo(out) } }
                ApiResult.Success(bytes)
            } catch (e: IOException) {
                ApiResult.Error("No se pudo guardar el archivo: ${e.message ?: "error de escritura"}")
            }
        }
    }
}
