package com.example.nutrisocial.data

/**
 * Aviso de GET /notifications: [actorName] ha empezado a seguir al usuario ([type] "follow"),
 * ha dado "me gusta" a su receta [recipeTitle] ("like") o la ha comentado ("comment").
 */
data class AppNotification(
    val id: Int,
    val type: String,
    val actorId: Int,
    val actorName: String = "",
    val recipeId: Int? = null,
    val recipeTitle: String? = null,
    val read: Boolean = false,
    val createdAt: String = ""
)

/** Página de notificaciones. [unreadCount] solo viene en la primera. */
data class NotificationsPage(
    val notifications: List<AppNotification> = emptyList(),
    val nextCursor: Int? = null,
    val unreadCount: Int? = null
)

/** Respuesta de POST /notifications/read-all. */
data class ReadAllResponse(val updated: Int = 0, val unreadCount: Int = 0)
