package com.example.nutrisocial.data

data class Recipe(
    val id: Int,
    val title: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val servings: Int,
    val prepMinutes: Int?,
    val authorId: Int,
    val createdAt: String
)

data class CreateRecipeRequest(
    val title: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val servings: Int,
    // Gson omite los null, así que si no se indica no se envía el campo.
    val prepMinutes: Int?
)
