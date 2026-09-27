package com.example.nutrisocial.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Añade el token guardado en DataStore a cada petición (salvo las de [PUBLIC_AUTH_PATHS],
 * que se hacen sin sesión). Si el servidor responde 401 a una petición autenticada (token caducado o
 * inválido), borra la sesión para volver al login. Por eso el servidor responde 403, y no 401,
 * a una contraseña mal escrita al borrar la cuenta (DELETE /auth/me).
 *
 * OkHttp ejecuta los interceptores en sus propios hilos, así que runBlocking no bloquea la UI;
 * DataStore mantiene los datos en memoria tras la primera lectura.
 */
class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        val isPublicEndpoint = PUBLIC_AUTH_PATHS.any { path.endsWith(it) }
        val token = if (isPublicEndpoint) null else runBlocking { sessionManager.token.first() }

        val request = if (token != null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }

        val response = chain.proceed(request)
        if (response.code == 401 && token != null) {
            runBlocking { sessionManager.clearSession() }
        }
        return response
    }
}

/** Rutas de /auth que no llevan token: se usan antes de tener sesión. */
private val PUBLIC_AUTH_PATHS = listOf(
    "/auth/login", "/auth/register", "/auth/resend-verification", "/auth/forgot-password", "/auth/reset-password"
)
