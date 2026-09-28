package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient

class FeedRepository(
    private val api: ApiService = RetrofitClient.api
) {
    /** Página del feed a partir de [cursor] (id de la última receta recibida; null para empezar). */
    suspend fun getFeed(cursor: Int? = null, limit: Int = PAGE_SIZE): ApiResult<FeedPage> =
        safeApiCall { api.getFeed(cursor, limit) }

    /** Página de las recetas guardadas a partir de [cursor] (id de la última recibida). */
    suspend fun getSaved(cursor: Int? = null, limit: Int = PAGE_SIZE): ApiResult<FeedPage> =
        safeApiCall { api.getSavedRecipes(cursor, limit) }

    /**
     * Búsqueda de recetas de cualquier autor por texto en el título y los ingredientes. Sin
     * texto, todas. [sortBy] es "createdAt" o "prepMinutes"; [order], "asc" o "desc".
     */
    suspend fun search(query: String, sortBy: String, order: String, cursor: Int? = null, limit: Int = PAGE_SIZE): ApiResult<FeedPage> =
        safeApiCall { api.searchRecipes(query.trim().ifEmpty { null }, sortBy, order, cursor, limit) }

    companion object {
        const val PAGE_SIZE = 20
    }
}
