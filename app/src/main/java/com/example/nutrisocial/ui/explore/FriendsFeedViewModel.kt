package com.example.nutrisocial.ui.explore

import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.FeedPage
import com.example.nutrisocial.ui.home.FeedViewModel

/**
 * "Amigos" del inicio: recetas de los usuarios a los que se sigue, de la más reciente a la más
 * antigua. Es un [FeedViewModel] cuyas páginas salen de GET /recipes/feed/friends.
 */
class FriendsFeedViewModel : FeedViewModel(loadOnInit = false) {
    // La primera carga la pide la pantalla al abrir el segmento (ver HomeScreen): al volver
    // del buscador de personas o de otra pestaña se puede haber empezado a seguir a alguien.

    override suspend fun fetchPage(cursor: Int?): ApiResult<FeedPage> = feedRepository.getFriendsFeed(cursor)
}
