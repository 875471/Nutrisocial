package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient

class PantryRepository(
    private val api: ApiService = RetrofitClient.api
) {
    suspend fun getPantry(): ApiResult<List<PantryItem>> =
        safeApiCall { api.getPantry() }

    suspend fun addItem(name: String): ApiResult<PantryItem> =
        safeApiCall { api.addPantryItem(AddPantryItemRequest(name)) }

    suspend fun deleteItem(id: Int): ApiResult<Unit> =
        safeApiCallNoContent(defaultErrorMessage = { code -> if (code == 404) "El ingrediente ya no está en tu despensa" else null }) {
            api.deletePantryItem(id)
        }

    /** Recetas de cualquier usuario que se pueden cocinar con la despensa actual. */
    suspend fun findRecipes(): ApiResult<PantrySearchResult> =
        safeApiCall { api.getRecipesByPantry() }

    suspend fun searchFoods(query: String): ApiResult<List<FoodSuggestion>> =
        safeApiCall { api.searchFoods(query) }
}
