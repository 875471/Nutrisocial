package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient

class RecipeRepository(
    private val api: ApiService = RetrofitClient.api
) {
    suspend fun createRecipe(request: CreateRecipeRequest): ApiResult<Recipe> =
        safeApiCall { api.createRecipe(request) }

    suspend fun getMyRecipes(): ApiResult<List<Recipe>> =
        safeApiCall { api.getMyRecipes() }

    suspend fun getRecipe(id: Int): ApiResult<Recipe> =
        safeApiCall(defaultErrorMessage = { code -> if (code == 404) "Receta no encontrada" else null }) {
            api.getRecipe(id)
        }

    suspend fun searchFoods(query: String): ApiResult<List<FoodSuggestion>> =
        safeApiCall { api.searchFoods(query) }

    suspend fun parseOcr(rawText: String): ApiResult<OcrRecipeProposal> =
        safeApiCall { api.parseOcr(ParseOcrRequest(rawText)) }
}
