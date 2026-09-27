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

/**
 * Respuesta de POST /auth/register. Con [emailVerificationRequired] hay que confirmar el correo
 * antes de iniciar sesión; [emailSent] es false si el servidor no pudo enviarlo.
 */
data class RegisterResponse(
    val id: Int,
    val email: String,
    val name: String,
    val emailVerificationRequired: Boolean = false,
    val emailSent: Boolean = false
)

/** Cuerpo de las rutas que solo necesitan el email (reenviar verificación, olvidé la contraseña). */
data class EmailRequest(val email: String)

/** Cuerpo de POST /auth/reset-password: el código recibido por correo y la contraseña nueva. */
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val password: String
)

/** Respuesta con un mensaje para el usuario (p. ej. "Si hay una cuenta con ese email..."). */
data class MessageResponse(val message: String = "")
