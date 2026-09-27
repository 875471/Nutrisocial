package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient

class FeedRepository(
    private val api: ApiService = RetrofitClient.api
) {
    /** Página del feed a partir de [cursor] (id de la última receta recibida; null para empezar). */
    suspend fun getFeed(cursor: Int? = null, limit: Int = PAGE_SIZE): ApiResult<FeedPage> =
        safeApiCall { api.getFeed(cursor, limit) }

    companion object {
        const val PAGE_SIZE = 20
    }
}
