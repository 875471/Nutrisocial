package com.example.nutrisocial.data

/**
 * Perfil devuelto por GET/PUT /profile. [dailyCalorieGoal] es null mientras falten datos;
 * en ese caso [missingFields] dice cuáles (nombres de campo del servidor).
 */
data class Profile(
    val id: Int,
    val email: String,
    val name: String,
    // "AAAA-MM-DD"
    val birthDate: String? = null,
    val age: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val sex: String? = null,
    val activityLevel: String? = null,
    val goal: String? = null,
    val dailyCalorieGoal: Int? = null,
    val bmr: Int? = null,
    val tdee: Int? = null,
    val missingFields: List<String> = emptyList()
)

/** Cuerpo de PUT /profile. Gson omite los null: solo se envían los campos rellenados. */
data class UpdateProfileRequest(
    val birthDate: String?,
    val heightCm: Double?,
    val weightKg: Double?,
    val sex: String?,
    val activityLevel: String?,
    val goal: String?
)

/** Opción de un campo de elección: valor que entiende el servidor y texto para la interfaz. */
data class ProfileOption(val value: String, val label: String, val description: String? = null)

val SexOptions = listOf(
    ProfileOption("F", "Mujer"),
    ProfileOption("M", "Hombre")
)

val ActivityOptions = listOf(
    ProfileOption("sedentario", "Sedentario", "Poco o ningún ejercicio"),
    ProfileOption("ligero", "Ligero", "Ejercicio suave 1-3 días por semana"),
    ProfileOption("moderado", "Moderado", "Ejercicio moderado 3-5 días por semana"),
    ProfileOption("activo", "Activo", "Ejercicio intenso 6-7 días por semana"),
    ProfileOption("muy_activo", "Muy activo", "Trabajo físico o entrenamiento diario muy intenso")
)

val GoalOptions = listOf(
    ProfileOption("perder_peso", "Perder peso"),
    ProfileOption("mantener", "Mantener"),
    ProfileOption("ganar_peso", "Ganar peso")
)

/** Nombre legible de los campos que puede devolver [Profile.missingFields]. */
fun profileFieldLabel(field: String): String = when (field) {
    "birthDate" -> "fecha de nacimiento"
    "heightCm" -> "altura"
    "weightKg" -> "peso"
    "sex" -> "sexo"
    "activityLevel" -> "nivel de actividad"
    "goal" -> "objetivo"
    else -> field
}
