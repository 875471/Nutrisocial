package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient

class LogRepository(
    private val api: ApiService = RetrofitClient.api
) {
    /** [date] en formato "AAAA-MM-DD". */
    suspend fun getDailyLog(date: String): ApiResult<DailyLog> =
        safeApiCall { api.getDailyLog(date) }

    /** Recetas recomendadas para completar el día [date]. */
    suspend fun getRecommendations(date: String): ApiResult<DailyRecommendations> =
        safeApiCall { api.getRecommendations(date) }

    suspend fun addRecipe(date: String, recipeId: Int, servings: Double): ApiResult<LogEntry> =
        safeApiCall { api.createLogEntry(CreateLogEntryRequest(date = date, recipeId = recipeId, servings = servings)) }

    suspend fun addFood(date: String, foodId: Int, grams: Double): ApiResult<LogEntry> =
        safeApiCall { api.createLogEntry(CreateLogEntryRequest(date = date, foodId = foodId, grams = grams)) }

    suspend fun deleteEntry(id: Int): ApiResult<Unit> =
        safeApiCallNoContent(defaultErrorMessage = { code -> if (code == 404) "La entrada ya no existe" else null }) {
            api.deleteLogEntry(id)
        }
}
