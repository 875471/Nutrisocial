package com.example.nutrisocial.ui.explore

import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.FeedPage
import com.example.nutrisocial.ui.home.FeedViewModel

/**
 * "Guardadas": las recetas que el usuario ha guardado con el marcador. Es un [FeedViewModel]
 * cuyas páginas salen de GET /recipes/saved. Al quitar el marcador desde aquí la tarjeta no
 * desaparece al momento (se puede volver a guardar si fue sin querer): sale de la lista al
 * refrescar.
 */
class SavedRecipesViewModel : FeedViewModel(loadOnInit = false) {
    // La primera carga la pide la pantalla al abrir el segmento (ver HomeScreen).

    override suspend fun fetchPage(cursor: Int?): ApiResult<FeedPage> = feedRepository.getSaved(cursor)
}
