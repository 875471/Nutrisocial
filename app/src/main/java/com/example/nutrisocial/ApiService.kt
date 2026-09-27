package com.example.nutrisocial

import com.example.nutrisocial.data.AddPantryItemRequest
import com.example.nutrisocial.data.CreateLogEntryRequest
import com.example.nutrisocial.data.CreateRecipeRequest
import com.example.nutrisocial.data.DailyLog
import com.example.nutrisocial.data.DailyRecommendations
import com.example.nutrisocial.data.DeleteAccountRequest
import com.example.nutrisocial.data.DeleteRecipeResponse
import com.example.nutrisocial.data.FeedPage
import com.example.nutrisocial.data.FoodSuggestion
import com.example.nutrisocial.data.LikeState
import com.example.nutrisocial.data.LogEntry
import com.example.nutrisocial.data.LoginRequest
import com.example.nutrisocial.data.LoginResponse
import com.example.nutrisocial.data.OcrRecipeProposal
import com.example.nutrisocial.data.PantryItem
import com.example.nutrisocial.data.PantrySearchResult
import com.example.nutrisocial.data.ParseOcrRequest
import com.example.nutrisocial.data.Profile
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.data.RegisterRequest
import com.example.nutrisocial.data.UpdateProfileRequest
import com.example.nutrisocial.data.User
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
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

    // @DELETE no admite cuerpo en Retrofit; la contraseña va en el cuerpo, no en la URL,
    // para que no quede en los registros del servidor. 204 sin cuerpo.
    @HTTP(method = "DELETE", path = "auth/me", hasBody = true)
    suspend fun deleteAccount(@Body request: DeleteAccountRequest): Response<Unit>

    // Rutas protegidas: AuthInterceptor añade "Authorization: Bearer <token>".
    @POST("recipes")
    suspend fun createRecipe(@Body request: CreateRecipeRequest): Response<Recipe>

    @GET("recipes/mine")
    suspend fun getMyRecipes(): Response<List<Recipe>>

    // Recetas de cualquier autor que se pueden cocinar con la despensa del usuario.
    @GET("recipes/by-pantry")
    suspend fun getRecipesByPantry(): Response<PantrySearchResult>

    @GET("recipes/{id}")
    suspend fun getRecipe(@Path("id") id: Int): Response<Recipe>

    // Solo el autor (403 si no). Mismo cuerpo que al crear; la foto se cambia aparte.
    @PUT("recipes/{id}")
    suspend fun updateRecipe(@Path("id") id: Int, @Body request: CreateRecipeRequest): Response<Recipe>

    @DELETE("recipes/{id}")
    suspend fun deleteRecipe(@Path("id") id: Int): Response<DeleteRecipeResponse>

    // Recetas de todos los usuarios, de 20 en 20. Sin cursor, la primera página.
    @GET("recipes/feed")
    suspend fun getFeed(@Query("cursor") cursor: Int?, @Query("limit") limit: Int): Response<FeedPage>

    // Cuerpo JSON escrito a mano: Gson omite los null y para quitar la foto hay que mandar
    // {"imageBase64": null} explícitamente (ver RecipeRepository.updateImage).
    @PUT("recipes/{id}/image")
    suspend fun updateRecipeImage(@Path("id") id: Int, @Body body: RequestBody): Response<Recipe>

    @POST("recipes/{id}/like")
    suspend fun likeRecipe(@Path("id") id: Int): Response<LikeState>

    @DELETE("recipes/{id}/like")
    suspend fun unlikeRecipe(@Path("id") id: Int): Response<LikeState>

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

    @GET("pantry")
    suspend fun getPantry(): Response<List<PantryItem>>

    // 409 si el ingrediente ya está en la despensa.
    @POST("pantry")
    suspend fun addPantryItem(@Body request: AddPantryItemRequest): Response<PantryItem>

    @DELETE("pantry/{id}")
    suspend fun deletePantryItem(@Path("id") id: Int): Response<Unit>
}
