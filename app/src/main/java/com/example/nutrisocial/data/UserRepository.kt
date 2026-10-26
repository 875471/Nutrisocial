package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient

class UserRepository(
    private val api: ApiService = RetrofitClient.api
) {
    /** Personas cuyo nombre contiene [query] (sin tildes ni mayúsculas). Sin texto, todas. */
    suspend fun search(query: String, limit: Int = PAGE_SIZE): ApiResult<List<UserSummary>> =
        when (val result = safeApiCall { api.searchUsers(query.trim().ifEmpty { null }, limit) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.users)
            is ApiResult.Error -> result
        }

    /** Sigue ([followed] = true) o deja de seguir al usuario [id]. */
    suspend fun setFollowed(id: Int, followed: Boolean): ApiResult<FollowState> =
        safeApiCall(defaultErrorMessage = { code -> if (code == 404) USER_GONE_MESSAGE else null }) {
            if (followed) api.followUser(id) else api.unfollowUser(id)
        }

    /** Gente a la que sigue el usuario, por nombre (a quién puede compartir una receta). */
    suspend fun getFollowing(): ApiResult<List<UserSummary>> =
        safeApiCall { api.getFollowing() }

    companion object {
        const val PAGE_SIZE = 20
    }
}

/** Mensaje para un usuario que ya no existe (cuenta borrada). */
const val USER_GONE_MESSAGE = "Este usuario ya no existe"
