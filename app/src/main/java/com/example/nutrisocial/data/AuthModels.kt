package com.example.nutrisocial.data

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class User(
    val id: Int,
    val email: String,
    val name: String
)

data class LoginResponse(
    val token: String,
    val user: User
)

// Cuerpo que devuelve el backend en respuestas 4xx: { "error": "..." }
data class ApiErrorBody(
    val error: String?
)

/** Cuerpo de DELETE /auth/me: la contraseña actual, para confirmar. */
data class DeleteAccountRequest(
    val password: String
)
