package com.example.nutrisocial

import android.content.Context
import com.example.nutrisocial.data.AuthInterceptor
import com.example.nutrisocial.data.SessionManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 10.0.2.2 es como el emulador ve el "localhost" de tu PC
    private const val BASE_URL = "http://10.0.2.2:3000/"

    private lateinit var appContext: Context

    /** Se llama una vez desde [NutriSocialApp.onCreate], antes de usar [api]. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val api: ApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(SessionManager(appContext)))
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
