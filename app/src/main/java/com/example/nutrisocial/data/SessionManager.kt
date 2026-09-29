package com.example.nutrisocial.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Una única instancia de DataStore por proceso (requisito de la librería).
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

data class Session(
    val token: String,
    val user: User
)

class SessionManager(context: Context) {

    private val dataStore = context.applicationContext.sessionDataStore

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = intPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_NAME = stringPreferencesKey("user_name")
    }

    /** Sesión guardada, o null si no hay token. */
    val session: Flow<Session?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val token = prefs[Keys.TOKEN]
            if (token.isNullOrBlank()) {
                null
            } else {
                Session(
                    token = token,
                    user = User(
                        id = prefs[Keys.USER_ID] ?: 0,
                        email = prefs[Keys.USER_EMAIL].orEmpty(),
                        name = prefs[Keys.USER_NAME].orEmpty()
                    )
                )
            }
        }

    val token: Flow<String?> = session.map { it?.token }

    suspend fun saveSession(token: String, user: User) {
        dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.USER_EMAIL] = user.email
            prefs[Keys.USER_NAME] = user.name
        }
    }

    suspend fun clearSession() {
        dataStore.edit { it.clear() }
    }
}
