package com.example.nutrisocial.data

/**
 * Etiquetas dietéticas de una receta, con el identificador que espera el backend (ver
 * backend/src/nutrition/dietTags.js). Las DECLARA quien publica la receta: no se deducen de los
 * ingredientes ni se verifican (ver [DIET_TAGS_DISCLAIMER]).
 */
enum class DietTag(val id: String, val label: String) {
    VEGANO("vegano", "Vegano"),
    VEGETARIANO("vegetariano", "Vegetariano"),
    SIN_GLUTEN("sin_gluten", "Sin gluten"),
    SIN_LACTOSA("sin_lactosa", "Sin lactosa"),
    SIN_FRUTOS_SECOS("sin_frutos_secos", "Sin frutos secos");

    companion object {
        fun fromId(id: String): DietTag? = entries.firstOrNull { it.id == id }

        /** Las etiquetas conocidas de una receta, en el orden de la lista (se ignoran las desconocidas). */
        fun fromIds(ids: List<String>): List<DietTag> = entries.filter { it.id in ids }

        /** Identificadores en el orden de la lista, para el cuerpo de POST/PUT /recipes. */
        fun toIds(tags: Set<DietTag>): List<String> = entries.filter { it in tags }.map { it.id }

        /** Valor del parámetro `?dietTags=` de los listados; null (sin filtro) si no hay ninguna. */
        fun toQuery(tags: Set<DietTag>): String? = toIds(tags).takeIf { it.isNotEmpty() }?.joinToString(",")
    }
}

/** Aviso que acompaña a las etiquetas: alguien con alergia o intolerancia no debe fiarse solo de ellas. */
const val DIET_TAGS_DISCLAIMER =
    "Etiqueta declarada por quien publica la receta, no verificada por NutriSocial — revisa siempre los ingredientes."
