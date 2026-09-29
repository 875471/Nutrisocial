package com.example.nutrisocial.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.AppNotification
import com.example.nutrisocial.data.NotificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de las notificaciones. [unreadCount] alimenta el punto rojo de la campana; [error] solo
 * se usa si la primera carga falla y no hay nada que enseñar.
 */
data class NotificationsUiState(
    val notifications: List<AppNotification> = emptyList(),
    val nextCursor: Int? = null,
    val unreadCount: Int = 0,
    val loaded: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val message: String? = null
) {
    val canLoadMore: Boolean get() = nextCursor != null
}

/**
 * Campana del inicio y pantalla de notificaciones. Con ámbito en HomeScreen: el recuento de no
 * leídas se pide al entrar en Inicio ([refreshUnreadCount]) y la lista al abrir la pantalla
 * ([open]), que además las marca todas como leídas.
 */
class NotificationsViewModel(
    private val repository: NotificationRepository = NotificationRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private var pageJob: Job? = null
    private var countJob: Job? = null

    /** Solo el número de no leídas. Un fallo no se enseña: el punto se queda como estaba. */
    fun refreshUnreadCount() {
        countJob?.cancel()
        countJob = viewModelScope.launch {
            (repository.getUnreadCount() as? ApiResult.Success)?.let { result ->
                _state.update { it.copy(unreadCount = result.data) }
            }
        }
    }

    /**
     * Al abrir la pantalla: carga la primera página y después marca todo como leído. La lista
     * conserva el `read` con que llegó, así que esta vez las nuevas se siguen viendo destacadas.
     */
    fun open() = loadFirstPage(showLoading = !_state.value.loaded)

    /** Tirar hacia abajo o reintentar. */
    fun refresh() = loadFirstPage(showLoading = _state.value.notifications.isEmpty())

    private fun loadFirstPage(showLoading: Boolean) {
        pageJob?.cancel()
        countJob?.cancel()
        _state.update { if (showLoading) it.copy(isLoading = true, error = null) else it.copy(isRefreshing = true) }
        pageJob = viewModelScope.launch {
            when (val result = repository.getNotifications()) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            notifications = result.data.notifications,
                            nextCursor = result.data.nextCursor,
                            unreadCount = result.data.unreadCount ?: it.unreadCount,
                            loaded = true,
                            isLoading = false,
                            isRefreshing = false,
                            error = null
                        )
                    }
                    markAllRead()
                }
                is ApiResult.Error -> _state.update {
                    if (it.notifications.isEmpty()) it.copy(isLoading = false, isRefreshing = false, error = result.message)
                    else it.copy(isRefreshing = false, message = "No se pudo actualizar: ${result.message}")
                }
            }
        }
    }

    private suspend fun markAllRead() {
        if (_state.value.unreadCount == 0) return
        // Si falla, el punto sigue encendido y se reintentará la próxima vez que se abra.
        if (repository.markAllRead() is ApiResult.Success) _state.update { it.copy(unreadCount = 0) }
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.isLoadingMore || current.isLoading || current.isRefreshing) return
        _state.update { it.copy(isLoadingMore = true) }
        pageJob = viewModelScope.launch {
            when (val result = repository.getNotifications(cursor)) {
                is ApiResult.Success -> _state.update { state ->
                    val known = state.notifications.map { it.id }.toSet()
                    state.copy(
                        notifications = state.notifications + result.data.notifications.filterNot { it.id in known },
                        nextCursor = result.data.nextCursor,
                        isLoadingMore = false
                    )
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isLoadingMore = false, message = "No se pudieron cargar más notificaciones: ${result.message}")
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}
