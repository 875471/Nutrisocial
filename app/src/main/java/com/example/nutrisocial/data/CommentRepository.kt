package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient

class CommentRepository(
    private val api: ApiService = RetrofitClient.api
) {
    /** Página de comentarios a partir de [cursor] (id del último recibido; null para empezar). */
    suspend fun getComments(recipeId: Int, cursor: Int? = null, limit: Int = PAGE_SIZE): ApiResult<CommentsPage> =
        safeApiCall(defaultErrorMessage = ::recipeGoneMessage) { api.getComments(recipeId, cursor, limit) }

    suspend fun addComment(recipeId: Int, text: String): ApiResult<Comment> =
        safeApiCall(defaultErrorMessage = ::recipeGoneMessage) { api.addComment(recipeId, CreateCommentRequest(text)) }

    suspend fun deleteComment(id: Int): ApiResult<Unit> {
        val result = safeApiCall(defaultErrorMessage = { code ->
            when (code) {
                403 -> "Solo puedes borrar tus propios comentarios"
                404 -> "Este comentario ya no existe"
                else -> null
            }
        }) { api.deleteComment(id) }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> result
        }
    }

    private fun recipeGoneMessage(code: Int): String? = if (code == 404) RECIPE_GONE_MESSAGE else null

    companion object {
        const val PAGE_SIZE = 20
    }
}
