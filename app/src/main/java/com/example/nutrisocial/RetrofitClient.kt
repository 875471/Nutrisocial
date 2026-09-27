package com.example.nutrisocial

import android.content.Context
import com.example.nutrisocial.data.AuthInterceptor
import com.example.nutrisocial.data.SessionManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Backend real desplegado en Render (plan gratuito).
    private const val BASE_URL = "https://nutrisocial.onrender.com/"

    private lateinit var appContext: Context

    /** Se llama una vez desde [NutriSocialApp.onCreate], antes de usar [api]. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val api: ApiService by lazy {
        // El plan gratuito de Render "duerme" el servidor tras 15 min de inactividad.
        // El primer request tras dormirse puede tardar 30-60s en despertar,
        // por eso usamos timeouts largos en vez de los 10s por defecto.
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(SessionManager(appContext)))
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
