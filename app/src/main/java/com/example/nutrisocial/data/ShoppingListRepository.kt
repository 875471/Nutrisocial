package com.example.nutrisocial.data

import com.example.nutrisocial.ApiService
import com.example.nutrisocial.RetrofitClient

/** Lista de la compra del usuario (gratuita, como la despensa). */
class ShoppingListRepository(
    private val api: ApiService = RetrofitClient.api
) {
    suspend fun getItems(): ApiResult<List<ShoppingListItem>> = safeApiCall { api.getShoppingList() }

    suspend fun addItem(label: String, quantity: String?): ApiResult<ShoppingListItem> =
        safeApiCall { api.addShoppingListItem(AddShoppingListItemRequest(label, quantity)) }

    /** Añade los ingredientes de la receta [recipeId] que no cubre la despensa. */
    suspend fun addMissingFromRecipe(recipeId: Int): ApiResult<FromRecipeResult> =
        safeApiCall(defaultErrorMessage = { code -> if (code == 404) RECIPE_GONE_MESSAGE else null }) {
            api.addMissingFromRecipe(recipeId)
        }

    suspend fun setChecked(id: Int, checked: Boolean): ApiResult<ShoppingListItem> =
        safeApiCall(defaultErrorMessage = ::itemErrorMessage) {
            api.updateShoppingListItem(id, UpdateShoppingListItemRequest(checked = checked))
        }

    suspend fun deleteItem(id: Int): ApiResult<Unit> =
        safeApiCallNoContent(defaultErrorMessage = ::itemErrorMessage) { api.deleteShoppingListItem(id) }

    suspend fun clearChecked(): ApiResult<ClearCheckedResult> = safeApiCall { api.clearCheckedShoppingList() }

    private fun itemErrorMessage(code: Int): String? = if (code == 404) ITEM_GONE_MESSAGE else null

    companion object {
        const val ITEM_GONE_MESSAGE = "Este elemento ya no está en tu lista"
    }
}
