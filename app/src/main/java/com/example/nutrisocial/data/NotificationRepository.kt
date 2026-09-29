package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient

class NotificationRepository(
    private val api: ApiService = RetrofitClient.api
) {
    /** Página de notificaciones a partir de [cursor] (id de la última recibida; null para empezar). */
    suspend fun getNotifications(cursor: Int? = null, limit: Int = PAGE_SIZE): ApiResult<NotificationsPage> =
        safeApiCall { api.getNotifications(cursor, limit) }

    /** Solo el número de no leídas, para el punto de la campana: una página de un elemento. */
    suspend fun getUnreadCount(): ApiResult<Int> =
        when (val result = getNotifications(limit = 1)) {
            is ApiResult.Success -> ApiResult.Success(result.data.unreadCount ?: 0)
            is ApiResult.Error -> result
        }

    suspend fun markAllRead(): ApiResult<ReadAllResponse> = safeApiCall { api.markAllNotificationsRead() }

    companion object {
        const val PAGE_SIZE = 20
    }
}
