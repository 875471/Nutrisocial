package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient

/** Suscripción Premium simulada: no hay cobro, la llamada a subscribe hace de pago. */
class SubscriptionRepository(
    private val api: ApiService = RetrofitClient.api
) {
    /** Los planes ordenados de periodo más corto a más largo (el backend los da como mapa por id). */
    suspend fun getPlans(): ApiResult<List<Plan>> =
        when (val result = safeApiCall { api.getPlans() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.values.sortedBy { it.periodMonths })
            is ApiResult.Error -> result
        }

    suspend fun getStatus(): ApiResult<SubscriptionStatus> = safeApiCall { api.getSubscriptionStatus() }

    suspend fun subscribe(planId: String): ApiResult<SubscriptionStatus> =
        safeApiCall { api.subscribe(SubscribeRequest(planId)) }

    suspend fun cancel(): ApiResult<SubscriptionStatus> = safeApiCall { api.cancelSubscription() }
}
