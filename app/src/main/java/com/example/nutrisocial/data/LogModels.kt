package com.example.nutrisocial.data

/** Entrada del registro diario. Los valores se calcularon al registrarla y ya no cambian. */
data class LogEntry(
    val id: Int,
    val date: String,
    // "recipe" o "food"
    val type: String,
    val name: String,
    val recipeId: Int? = null,
    val foodId: Int? = null,
    val servings: Double? = null,
    val grams: Double? = null,
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0
)

/**
 * Día del registro (GET /log?date=): entradas, totales y comparación con el objetivo. Los
 * campos del objetivo son null si el perfil está incompleto ([missingProfileFields]).
 */
data class DailyLog(
    val date: String,
    val entries: List<LogEntry> = emptyList(),
    val totals: Macros = Macros(),
    val dailyCalorieGoal: Int? = null,
    val remainingKcal: Int? = null,
    val excessKcal: Int? = null,
    // Fracción del objetivo consumida; puede ser mayor que 1.
    val progress: Double? = null,
    val missingProfileFields: List<String> = emptyList()
)

/** Cuerpo de POST /log: receta (recipeId + servings) o alimento (foodId + grams). */
data class CreateLogEntryRequest(
    val date: String,
    val recipeId: Int? = null,
    val servings: Double? = null,
    val foodId: Int? = null,
    val grams: Double? = null
)

/** Día del calendario mensual con alguna entrada: kcal totales y estado frente al objetivo. */
data class CalendarDay(
    val date: String,
    val kcal: Int = 0,
    // [STATUS_ADEQUATE], [STATUS_EXCESS], [STATUS_INSUFFICIENT] o null si el perfil está incompleto.
    val status: String? = null
) {
    companion object {
        const val STATUS_ADEQUATE = "adecuado"
        const val STATUS_EXCESS = "excesivo"
        const val STATUS_INSUFFICIENT = "insuficiente"
    }
}

/**
 * Respuesta de GET /log/calendar?month=AAAA-MM. Solo trae los días con entradas; el estado se
 * calcula con el objetivo actual del perfil, no con el que hubiera ese día.
 */
data class CalendarMonth(
    val month: String,
    val dailyCalorieGoal: Int? = null,
    val days: List<CalendarDay> = emptyList(),
    val missingProfileFields: List<String> = emptyList()
)

/** Lo que queda del día frente al objetivo calórico y al reparto de macros; nunca negativo. */
data class RemainingMacros(
    val kcal: Int = 0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0
)

/** Receta recomendada con sus valores por ración y el motivo generado por el servidor. */
data class RecipeRecommendation(
    val id: Int,
    val title: String,
    val kcalPorRacion: Int = 0,
    val proteinPorRacion: Double = 0.0,
    val carbsPorRacion: Double = 0.0,
    val fatPorRacion: Double = 0.0,
    val motivo: String = ""
)

/**
 * Respuesta de GET /log/recommendations. Si no hay recomendaciones, [reason] explica por qué:
 * [REASON_INCOMPLETE_PROFILE] o [REASON_GOAL_COVERED].
 */
data class DailyRecommendations(
    val date: String,
    val remaining: RemainingMacros? = null,
    val recommendations: List<RecipeRecommendation> = emptyList(),
    val reason: String? = null,
    val missingProfileFields: List<String> = emptyList()
) {
    companion object {
        const val REASON_INCOMPLETE_PROFILE = "perfil_incompleto"
        const val REASON_GOAL_COVERED = "objetivo_cubierto"
    }
}
