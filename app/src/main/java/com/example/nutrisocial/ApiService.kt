package com.example.nutrisocial

import com.example.nutrisocial.data.AddPantryItemRequest
import com.example.nutrisocial.data.CalendarMonth
import com.example.nutrisocial.data.Comment
import com.example.nutrisocial.data.CommentsPage
import com.example.nutrisocial.data.CreateCommentRequest
import com.example.nutrisocial.data.CreateLogEntryRequest
import com.example.nutrisocial.data.CreateRecipeRequest
import com.example.nutrisocial.data.DailyLog
import com.example.nutrisocial.data.DailyRecommendations
import com.example.nutrisocial.data.DeleteAccountRequest
import com.example.nutrisocial.data.DeleteRecipeResponse
import com.example.nutrisocial.data.EmailRequest
import com.example.nutrisocial.data.FeedPage
import com.example.nutrisocial.data.FollowState
import com.example.nutrisocial.data.FoodSuggestion
import com.example.nutrisocial.data.LikeState
import com.example.nutrisocial.data.LogEntry
import com.example.nutrisocial.data.LoginRequest
import com.example.nutrisocial.data.LoginResponse
import com.example.nutrisocial.data.MessageResponse
import com.example.nutrisocial.data.OcrRecipeProposal
import com.example.nutrisocial.data.PantryItem
import com.example.nutrisocial.data.PantrySearchResult
import com.example.nutrisocial.data.ParseOcrRequest
import com.example.nutrisocial.data.Profile
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.data.RegisterResponse
import com.example.nutrisocial.data.ResetPasswordRequest
import com.example.nutrisocial.data.SaveState
import com.example.nutrisocial.data.RegisterRequest
import com.example.nutrisocial.data.UpdateLogEntryRequest
import com.example.nutrisocial.data.UpdateProfileRequest
import com.example.nutrisocial.data.User
import com.example.nutrisocial.data.UserSearchResult
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
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("auth/resend-verification")
    suspend fun resendVerification(@Body request: EmailRequest): Response<MessageResponse>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: EmailRequest): Response<MessageResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<MessageResponse>

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

    // Como el feed, solo con recetas de los usuarios que sigue (vacío si no sigue a nadie).
    @GET("recipes/feed/friends")
    suspend fun getFriendsFeed(@Query("cursor") cursor: Int?, @Query("limit") limit: Int): Response<FeedPage>

    // Buscador de recetas de cualquier autor por texto libre, ordenadas por fecha o por tiempo.
    @GET("recipes/search")
    suspend fun searchRecipes(
        @Query("q") query: String?,
        @Query("sortBy") sortBy: String,
        @Query("order") order: String,
        @Query("cursor") cursor: Int?,
        @Query("limit") limit: Int
    ): Response<FeedPage>

    // Cuerpo JSON escrito a mano: Gson omite los null y para quitar la foto hay que mandar
    // {"imageBase64": null} explícitamente (ver RecipeRepository.updateImage).
    @PUT("recipes/{id}/image")
    suspend fun updateRecipeImage(@Path("id") id: Int, @Body body: RequestBody): Response<Recipe>

    @POST("recipes/{id}/like")
    suspend fun likeRecipe(@Path("id") id: Int): Response<LikeState>

    @DELETE("recipes/{id}/like")
    suspend fun unlikeRecipe(@Path("id") id: Int): Response<LikeState>

    // Guardar y dejar de guardar (idempotentes): devuelven el estado final.
    @POST("recipes/{id}/save")
    suspend fun saveRecipe(@Path("id") id: Int): Response<SaveState>

    @DELETE("recipes/{id}/save")
    suspend fun unsaveRecipe(@Path("id") id: Int): Response<SaveState>

    // Recetas guardadas, de la guardada más recientemente a la más antigua, de 20 en 20.
    @GET("recipes/saved")
    suspend fun getSavedRecipes(@Query("cursor") cursor: Int?, @Query("limit") limit: Int): Response<FeedPage>

    // Comentarios de una receta, del más reciente al más antiguo, de 20 en 20.
    @GET("recipes/{id}/comments")
    suspend fun getComments(
        @Path("id") recipeId: Int,
        @Query("cursor") cursor: Int?,
        @Query("limit") limit: Int
    ): Response<CommentsPage>

    @POST("recipes/{id}/comments")
    suspend fun addComment(@Path("id") recipeId: Int, @Body request: CreateCommentRequest): Response<Comment>

    // Solo el autor del comentario (403 si no).
    @DELETE("comments/{id}")
    suspend fun deleteComment(@Path("id") id: Int): Response<Map<String, Boolean>>

    // Personas por nombre (sin incluir al propio usuario). Sin texto, todas por orden alfabético.
    @GET("users/search")
    suspend fun searchUsers(@Query("q") query: String?, @Query("limit") limit: Int): Response<UserSearchResult>

    // Seguir y dejar de seguir (idempotentes): devuelven el estado final. 400 si es uno mismo.
    @POST("users/{id}/follow")
    suspend fun followUser(@Path("id") id: Int): Response<FollowState>

    @DELETE("users/{id}/follow")
    suspend fun unfollowUser(@Path("id") id: Int): Response<FollowState>

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

    // Solo los días del mes con alguna entrada.
    @GET("log/calendar")
    suspend fun getCalendar(@Query("month") month: String): Response<CalendarMonth>

    @GET("log/recommendations")
    suspend fun getRecommendations(@Query("date") date: String): Response<DailyRecommendations>

    @POST("log")
    suspend fun createLogEntry(@Body request: CreateLogEntryRequest): Response<LogEntry>

    // Solo cambia la cantidad; 404 si no es del usuario o su receta/alimento ya no existe.
    @PUT("log/{id}")
    suspend fun updateLogEntry(@Path("id") id: Int, @Body request: UpdateLogEntryRequest): Response<LogEntry>

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
