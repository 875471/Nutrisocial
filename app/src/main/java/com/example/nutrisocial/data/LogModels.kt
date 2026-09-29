package com.example.nutrisocial.data

/**
 * Entrada del registro diario. Los valores se calculan al registrarla y solo cambian si se edita
 * su cantidad (PUT /log/:id), que los recalcula con la receta o el alimento actuales.
 */
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
 * Día del registro (GET /log?date=): entradas, totales y comparación con los objetivos de kcal y
 * de gramos de proteína, hidratos y grasas (los mismos que usa el recomendador). Los campos de
 * los objetivos son null si el perfil está incompleto ([missingProfileFields]).
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
    val proteinGoal: Double? = null,
    val remainingProtein: Double? = null,
    val excessProtein: Double? = null,
    val proteinProgress: Double? = null,
    val carbsGoal: Double? = null,
    val remainingCarbs: Double? = null,
    val excessCarbs: Double? = null,
    val carbsProgress: Double? = null,
    val fatGoal: Double? = null,
    val remainingFat: Double? = null,
    val excessFat: Double? = null,
    val fatProgress: Double? = null,
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

/**
 * Cuerpo de PUT /log/:id: raciones si la entrada es de una receta o gramos si es de un alimento.
 * El que no corresponde va a null y Gson lo omite.
 */
data class UpdateLogEntryRequest(
    val servings: Double? = null,
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
