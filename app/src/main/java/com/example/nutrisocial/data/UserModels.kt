package com.example.nutrisocial.data

/** Persona en el buscador (GET /users/search): nombre, si ya se la sigue y cuántas recetas ha publicado. */
data class UserSummary(
    val id: Int,
    val name: String,
    val isFollowedByMe: Boolean = false,
    val recipesCount: Int = 0
)

/** Respuesta de GET /users/search. */
data class UserSearchResult(val users: List<UserSummary> = emptyList())

/** Respuesta de POST/DELETE /users/{id}/follow. */
data class FollowState(val isFollowedByMe: Boolean)
