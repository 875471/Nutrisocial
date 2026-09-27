package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient

class ProfileRepository(
    private val api: ApiService = RetrofitClient.api
) {
    suspend fun getProfile(): ApiResult<Profile> =
        safeApiCall { api.getProfile() }

    suspend fun updateProfile(request: UpdateProfileRequest): ApiResult<Profile> =
        safeApiCall { api.updateProfile(request) }
}
