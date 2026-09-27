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
    val nutrition: Nutrition = Nutrition(),
    // Foto en Base64 (JPEG sin el prefijo "data:"), o null si la receta no tiene.
    val imageBase64: String? = null,
    val authorName: String = "",
    val likesCount: Int = 0,
    val likedByMe: Boolean = false
)

/** Ingrediente guardado: cantidad tal como se escribió, peso estimado y alimento asociado (BEDCA u Open Food Facts). */
data class RecipeIngredient(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val grams: Double? = null,
    val food: FoodRef? = null
)

/** Alimento asociado a un ingrediente. [source] es "BEDCA" u "OpenFoodFacts" (ver [isOpenFoodFacts]). */
data class FoodRef(val id: Int, val name: String, val source: String? = null)

/** Valor de `source` de los alimentos que el servidor ha traído de Open Food Facts. */
const val OPEN_FOOD_FACTS_SOURCE = "OpenFoodFacts"

fun isOpenFoodFacts(source: String?): Boolean = source == OPEN_FOOD_FACTS_SOURCE

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
    val prepMinutes: Int?,
    // Foto ya comprimida en el móvil (ver ui/ImageUtils.kt); null si no hay.
    val imageBase64: String? = null
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
    val fat: Double,
    // "BEDCA" u "OpenFoodFacts": los segundos se marcan en la lista para distinguirlos.
    val source: String? = null
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
    val foodName: String? = null,
    val foodSource: String? = null
)

/** Receta en el feed social (GET /recipes/feed): lo justo para la tarjeta. */
data class FeedRecipe(
    val id: Int,
    val title: String,
    val imageBase64: String? = null,
    val authorId: Int,
    val authorName: String = "",
    val servings: Int = 1,
    val prepMinutes: Int? = null,
    val kcalPerServing: Double = 0.0,
    val likesCount: Int = 0,
    val likedByMe: Boolean = false,
    val createdAt: String = ""
)

/** Página del feed. [nextCursor] es el id que hay que pedir después, o null si no hay más. */
data class FeedPage(
    val recipes: List<FeedRecipe> = emptyList(),
    val nextCursor: Int? = null
)

/** Respuesta de POST/DELETE /recipes/{id}/like. */
data class LikeState(val likesCount: Int, val likedByMe: Boolean)
