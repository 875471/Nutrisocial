package com.example.nutrisocial.data

/** Ingrediente de la despensa. [food] es el alimento que el servidor le ha asociado, si lo encontró. */
data class PantryItem(
    val id: Int,
    val name: String,
    val food: FoodRef? = null,
    val createdAt: String = ""
)

data class AddPantryItemRequest(val name: String)

/** Receta que se puede cocinar (o casi) con la despensa. [coverage] va de 0,5 a 1. */
data class PantryRecipeMatch(
    val id: Int,
    val title: String,
    val totalIngredients: Int,
    val matchedCount: Int,
    val missingIngredients: List<String> = emptyList(),
    val coverage: Double,
    val imageBase64: String? = null,
    val authorName: String = ""
)

/** Respuesta de GET /recipes/by-pantry, ya separada por el servidor. */
data class PantrySearchResult(
    val pantrySize: Int = 0,
    // Cobertura del 100 %.
    val readyToCook: List<PantryRecipeMatch> = emptyList(),
    // Les falta algún ingrediente, pero tienen al menos la mitad.
    val almostReady: List<PantryRecipeMatch> = emptyList()
)
