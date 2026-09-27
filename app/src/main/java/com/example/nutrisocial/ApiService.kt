package com.example.nutrisocial

import com.example.nutrisocial.data.CreateLogEntryRequest
import com.example.nutrisocial.data.CreateRecipeRequest
import com.example.nutrisocial.data.DailyLog
import com.example.nutrisocial.data.DailyRecommendations
import com.example.nutrisocial.data.FoodSuggestion
import com.example.nutrisocial.data.LogEntry
import com.example.nutrisocial.data.LoginRequest
import com.example.nutrisocial.data.LoginResponse
import com.example.nutrisocial.data.OcrRecipeProposal
import com.example.nutrisocial.data.ParseOcrRequest
import com.example.nutrisocial.data.Profile
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.data.RegisterRequest
import com.example.nutrisocial.data.UpdateProfileRequest
import com.example.nutrisocial.data.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("health")
    suspend fun getHealth(): Response<Map<String, String>>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<User>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // Rutas protegidas: AuthInterceptor añade "Authorization: Bearer <token>".
    @POST("recipes")
    suspend fun createRecipe(@Body request: CreateRecipeRequest): Response<Recipe>

    @GET("recipes/mine")
    suspend fun getMyRecipes(): Response<List<Recipe>>

    @GET("recipes/{id}")
    suspend fun getRecipe(@Path("id") id: Int): Response<Recipe>

    @GET("foods/search")
    suspend fun searchFoods(@Query("q") query: String): Response<List<FoodSuggestion>>

    @POST("recipes/parse-ocr")
    suspend fun parseOcr(@Body request: ParseOcrRequest): Response<OcrRecipeProposal>

    @GET("profile")
    suspend fun getProfile(): Response<Profile>

    @PUT("profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<Profile>

    @GET("log")
    suspend fun getDailyLog(@Query("date") date: String): Response<DailyLog>

    @GET("log/recommendations")
    suspend fun getRecommendations(@Query("date") date: String): Response<DailyRecommendations>

    @POST("log")
    suspend fun createLogEntry(@Body request: CreateLogEntryRequest): Response<LogEntry>

    // 204 sin cuerpo: el repositorio usa safeApiCallNoContent.
    @DELETE("log/{id}")
    suspend fun deleteLogEntry(@Path("id") id: Int): Response<Unit>
}
