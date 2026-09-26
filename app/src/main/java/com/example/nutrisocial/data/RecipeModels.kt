package com.example.nutrisocial.data

data class Recipe(
    val id: Int,
    val title: String,
    val ingredients: List<RecipeIngredient>,
    val steps: List<String>,
    val servings: Int,
    val prepMinutes: Int?,
    val authorId: Int,
    val createdAt: String,
    val nutrition: Nutrition = Nutrition()
)

/** Ingrediente guardado: cantidad tal como se escribió, peso estimado y alimento de BEDCA asociado. */
data class RecipeIngredient(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val grams: Double? = null,
    val food: FoodRef? = null
)

data class FoodRef(val id: Int, val name: String)

data class Macros(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0
)

/** Valores calculados por el servidor. Si algún ingrediente no cuenta, el total es parcial. */
data class Nutrition(
    val total: Macros = Macros(),
    val perServing: Macros = Macros(),
    // Peso en crudo de la receta y de cada ración; null si no se pudo estimar.
    val totalWeightGrams: Double? = null,
    val gramsPerServing: Double? = null,
    val uncountedIngredients: List<String> = emptyList()
)

data class CreateRecipeRequest(
    val title: String,
    val ingredients: List<IngredientInput>,
    val steps: List<String>,
    val servings: Int,
    // Gson omite los null, así que si no se indica no se envía el campo.
    val prepMinutes: Int?
)

/** Ingrediente enviado al crear una receta. Sin cantidad ("sal al gusto") no suma en el cálculo. */
data class IngredientInput(
    val name: String,
    val quantity: Double?,
    val unit: String?,
    // Alimento elegido en el autocompletado; si es null el servidor lo busca por nombre.
    val foodId: Int?
)

/** Sugerencia del autocompletado (valores por 100 g). */
data class FoodSuggestion(
    val id: Int,
    val name: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

/** Unidades que admite el servidor (ver backend/src/nutrition/unitConversion.js). */
val IngredientUnits = listOf("g", "kg", "ml", "l", "unidad", "cucharada", "cucharadita", "taza", "pizca")

data class ParseOcrRequest(val rawText: String)

/**
 * Propuesta de receta extraída de una foto por POST /recipes/parse-ocr. No está guardada:
 * se vuelca en el formulario para que el usuario la revise.
 */
data class OcrRecipeProposal(
    val title: String? = null,
    val servings: Int? = null,
    val ingredients: List<OcrIngredient> = emptyList(),
    val steps: List<String> = emptyList()
)

data class OcrIngredient(
    val rawName: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val matched: Boolean = false,
    val foodId: Int? = null,
    val foodName: String? = null
)
