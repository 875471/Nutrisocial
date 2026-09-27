package com.example.nutrisocial.ui.auth

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

@Composable
fun LoginScreen(
    state: AuthUiState,
    onLogin: (email: String, password: String) -> Unit,
    onGoToRegister: () -> Unit,
    onForgotPassword: (email: String) -> Unit = {},
    // Aviso tras volver de otro flujo, p. ej. "Contraseña cambiada".
    notice: String? = null,
    resendState: ResendState = ResendState(),
    onResendVerification: (email: String) -> Unit = {}
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val isLoading = state is AuthUiState.Loading

    AuthFormLayout(title = "NutriSocial") {
        if (notice != null) AuthNotice(message = notice)
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
        if (state is AuthUiState.EmailNotVerified) {
            AuthNotice(message = state.message) {
                ResendButton(resendState = resendState, onResend = { onResendVerification(state.email) })
            }
        }
        AuthSubmitButton(
            text = "Iniciar sesión",
            isLoading = isLoading,
            onClick = { onLogin(email, password) }
        )
        TextButton(onClick = { onForgotPassword(email) }, enabled = !isLoading) {
            Text("¿Olvidaste tu contraseña?")
        }
        TextButton(onClick = onGoToRegister, enabled = !isLoading) {
            Text("¿No tienes cuenta? Regístrate")
        }
    }
}

/** "Reenviar correo de confirmación", con su resultado debajo. Se usa en el login y tras registrarse. */
@Composable
internal fun ResendButton(resendState: ResendState, onResend: () -> Unit) {
    TextButton(onClick = onResend, enabled = !resendState.isSending) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (resendState.isSending) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Text(
                "Reenviar correo de confirmación",
                modifier = Modifier.padding(start = if (resendState.isSending) Spacing.sm else 0.dp)
            )
        }
    }
    resendState.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    NutriSocialTheme {
        LoginScreen(
            state = AuthUiState.EmailNotVerified(
                "ana@nutrisocial.example",
                "Confirma tu email antes de iniciar sesión. Revisa tu correo (y la carpeta de spam)."
            ),
            onLogin = { _, _ -> },
            onGoToRegister = {}
        )
    }
}
