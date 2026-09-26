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
fun LoginScreen(
    state: AuthUiState,
    onLogin: (email: String, password: String) -> Unit,
    onGoToRegister: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val isLoading = state is AuthUiState.Loading

    AuthFormLayout(title = "NutriSocial") {
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
            label = "Contraseña",
            enabled = !isLoading,
            keyboardType = KeyboardType.Password,
            isPassword = true,
            imeAction = ImeAction.Done
        )
        AuthErrorText(state)
        AuthSubmitButton(
            text = "Iniciar sesión",
            isLoading = isLoading,
            onClick = { onLogin(email, password) }
        )
        TextButton(onClick = onGoToRegister, enabled = !isLoading) {
            Text("¿No tienes cuenta? Regístrate")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    NutriSocialTheme {
        LoginScreen(
            state = AuthUiState.Error("Email o contraseña incorrectos"),
            onLogin = { _, _ -> },
            onGoToRegister = {}
        )
    }
}
