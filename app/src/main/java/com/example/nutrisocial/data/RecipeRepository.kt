package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class RecipeRepository(
    private val api: ApiService = RetrofitClient.api
) {
    suspend fun createRecipe(request: CreateRecipeRequest): ApiResult<Recipe> =
        safeApiCall { api.createRecipe(request) }

    suspend fun getMyRecipes(): ApiResult<List<Recipe>> =
        safeApiCall { api.getMyRecipes() }

    suspend fun getRecipe(id: Int): ApiResult<Recipe> =
        safeApiCall(defaultErrorMessage = ::recipeErrorMessage) { api.getRecipe(id) }

    suspend fun updateRecipe(id: Int, request: CreateRecipeRequest): ApiResult<Recipe> =
        safeApiCall(defaultErrorMessage = ::recipeErrorMessage) { api.updateRecipe(id, request) }

    suspend fun deleteRecipe(id: Int): ApiResult<DeleteRecipeResponse> =
        safeApiCall(defaultErrorMessage = ::recipeErrorMessage) { api.deleteRecipe(id) }

    private fun recipeErrorMessage(code: Int): String? = when (code) {
        404 -> RECIPE_GONE_MESSAGE
        403 -> "Solo el autor puede modificar esta receta"
        else -> null
    }

    suspend fun searchFoods(query: String): ApiResult<List<FoodSuggestion>> =
        safeApiCall { api.searchFoods(query) }

    suspend fun parseOcr(rawText: String): ApiResult<OcrRecipeProposal> =
        safeApiCall { api.parseOcr(ParseOcrRequest(rawText)) }

    /** Pone o cambia la foto ([imageBase64] ya comprimida) o, con null, la quita. Solo el autor. */
    suspend fun updateImage(id: Int, imageBase64: String?): ApiResult<Recipe> {
        // Se escribe el JSON a mano porque Gson omitiría el campo si es null.
        val json = if (imageBase64 == null) {
            """{"imageBase64":null}"""
        } else {
            JsonObject().apply { addProperty("imageBase64", imageBase64) }.toString()
        }
        return safeApiCall(defaultErrorMessage = { code -> if (code == 403) "Solo el autor puede cambiar la foto" else null }) {
            api.updateRecipeImage(id, json.toRequestBody("application/json".toMediaType()))
        }
    }

    /** Da ([liked] = true) o quita el "me gusta" y devuelve el recuento actualizado. */
    suspend fun setLiked(id: Int, liked: Boolean): ApiResult<LikeState> =
        safeApiCall { if (liked) api.likeRecipe(id) else api.unlikeRecipe(id) }
}

/** Mensaje para una receta que ya no existe (borrada, quizá desde otro dispositivo). */
const val RECIPE_GONE_MESSAGE = "Esta receta ya no existe"
