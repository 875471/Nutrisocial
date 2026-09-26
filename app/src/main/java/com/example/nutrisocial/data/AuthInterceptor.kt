package com.example.nutrisocial.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Añade el token guardado en DataStore a cada petición. Si el servidor responde 401 a una
 * petición autenticada (token caducado o inválido), borra la sesión para volver al login.
 *
 * OkHttp ejecuta los interceptores en sus propios hilos, así que runBlocking no bloquea la UI;
 * DataStore mantiene los datos en memoria tras la primera lectura.
 */
class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val isAuthEndpoint = original.url.encodedPath.startsWith("/auth/")
        val token = if (isAuthEndpoint) null else runBlocking { sessionManager.token.first() }

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
