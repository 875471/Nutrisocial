package com.example.nutrisocial.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.UserRepository
import com.example.nutrisocial.data.UserSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del buscador de personas. [error] solo si no hay resultados que enseñar. */
data class PeopleSearchUiState(
    val query: String = "",
    val users: List<UserSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null
)

/**
 * Buscador de personas para seguirlas. Sin texto enseña a todo el mundo por orden alfabético,
 * para descubrir gente; al escribir, busca al dejar de teclear un momento.
 */
class PeopleSearchViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(PeopleSearchUiState())
    val state: StateFlow<PeopleSearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private val pendingFollows = mutableSetOf<Int>()

    init {
        search()
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        search(debounce = true)
    }

    /** Busca con lo que haya en el campo; con [debounce], tras una pausa al escribir. */
    fun search(debounce: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DELAY_MS)
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = userRepository.search(_state.value.query)) {
                is ApiResult.Success -> _state.update { it.copy(users = result.data, isLoading = false) }
                is ApiResult.Error -> _state.update {
                    if (it.users.isEmpty()) it.copy(isLoading = false, error = result.message)
                    else it.copy(isLoading = false, message = "No se pudo buscar: ${result.message}")
                }
            }
        }
    }

    /**
     * Seguir o dejar de seguir, con cambio inmediato que se deshace si el servidor falla.
     * [onChanged] recibe el estado final, para copiarlo a las tarjetas del inicio y del perfil.
     */
    fun toggleFollow(userId: Int, onChanged: (userId: Int, followed: Boolean) -> Unit = { _, _ -> }) {
        val user = _state.value.users.find { it.id == userId } ?: return
        if (!pendingFollows.add(userId)) return
        val followed = !user.isFollowedByMe
        updateUser(userId) { it.copy(isFollowedByMe = followed) }
        viewModelScope.launch {
            val result = userRepository.setFollowed(userId, followed)
            pendingFollows.remove(userId)
            when (result) {
                is ApiResult.Success -> {
                    updateUser(userId) { it.copy(isFollowedByMe = result.data.isFollowedByMe) }
                    onChanged(userId, result.data.isFollowedByMe)
                }
                is ApiResult.Error -> if (result.code == 404) {
                    // Cuenta borrada entretanto: fuera de la lista.
                    _state.update { state -> state.copy(users = state.users.filterNot { it.id == userId }, message = result.message) }
                } else {
                    updateUser(userId) { it.copy(isFollowedByMe = user.isFollowedByMe) }
                    _state.update { it.copy(message = "No se pudo ${if (followed) "seguir" else "dejar de seguir"} a ${user.name}: ${result.message}") }
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun updateUser(id: Int, transform: (UserSummary) -> UserSummary) = _state.update { state ->
        state.copy(users = state.users.map { if (it.id == id) transform(it) else it })
    }

    private companion object {
        const val SEARCH_DELAY_MS = 350L
    }
}
