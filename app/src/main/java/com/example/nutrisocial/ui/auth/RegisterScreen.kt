package com.example.nutrisocial.ui.auth

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.example.nutrisocial.ui.theme.NutriSocialTheme

@Composable
fun RegisterScreen(
    state: AuthUiState,
    onRegister: (name: String, email: String, password: String) -> Unit,
    onGoToLogin: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val isLoading = state is AuthUiState.Loading

    AuthFormLayout(title = "Crear cuenta") {
        AuthTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nombre",
            enabled = !isLoading
        )
        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            enabled = !isLoading,
            keyboardType = KeyboardType.Email
        )
        AuthTextField(
            value = password,
            onValueChange = { password = it },
            label = "Contraseña (mín. 4 caracteres)",
            enabled = !isLoading,
            keyboardType = KeyboardType.Password,
            isPassword = true,
            imeAction = ImeAction.Done
        )
        AuthErrorText(state)
        AuthSubmitButton(
            text = "Registrarse",
            isLoading = isLoading,
            onClick = { onRegister(name, email, password) }
        )
        TextButton(onClick = onGoToLogin, enabled = !isLoading) {
            Text("¿Ya tienes cuenta? Inicia sesión")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    NutriSocialTheme {
        RegisterScreen(state = AuthUiState.Idle, onRegister = { _, _, _ -> }, onGoToLogin = {})
    }
}
