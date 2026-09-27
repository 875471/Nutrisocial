package com.example.nutrisocial.ui.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.nutrisocial.ui.theme.NutriSocialTheme

/**
 * "¿Olvidaste tu contraseña?" en dos pasos: pedir el código con el email y, después, escribir
 * el código recibido junto con la contraseña nueva. Al terminar se llama a [onDone].
 */
@Composable
fun ForgotPasswordScreen(
    state: PasswordResetState,
    onRequestCode: (email: String) -> Unit,
    onReset: (code: String, password: String, confirmation: String) -> Unit,
    onRequestAnotherCode: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }
    var email by rememberSaveable(state.email) { mutableStateOf(state.email) }
    var code by rememberSaveable { mutableStateOf("") }
    // Las contraseñas no se guardan en el estado guardado de la pantalla.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    AuthFormLayout(title = "Cambiar contraseña") {
        if (!state.codeSent) {
            Text(
                "Escribe el email de tu cuenta y te enviaremos un código para elegir una contraseña nueva.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            AuthTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                enabled = !state.isLoading,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            )
            state.error?.let { AuthNotice(message = it, isError = true) }
            AuthSubmitButton(text = "Enviar código", isLoading = state.isLoading, onClick = { onRequestCode(email) })
        } else {
            state.info?.let { AuthNotice(message = "$it Revisa ${state.email} (y la carpeta de spam).") }
            AuthTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(9) },
                label = "Código (p. ej. K7QM-3XPD)",
                enabled = !state.isLoading,
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters
            )
            AuthTextField(
                value = password,
                onValueChange = { password = it },
                label = "Contraseña nueva (mín. 8 caracteres)",
                enabled = !state.isLoading,
                keyboardType = KeyboardType.Password,
                isPassword = true
            )
            AuthTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = "Repite la contraseña",
                enabled = !state.isLoading,
                keyboardType = KeyboardType.Password,
                isPassword = true,
                imeAction = ImeAction.Done
            )
            state.error?.let { AuthNotice(message = it, isError = true) }
            AuthSubmitButton(
                text = "Cambiar contraseña",
                isLoading = state.isLoading,
                onClick = { onReset(code, password, confirmation) }
            )
            TextButton(onClick = onRequestAnotherCode, enabled = !state.isLoading) {
                Text("No me ha llegado: pedir otro código")
            }
        }
        TextButton(onClick = onBack, enabled = !state.isLoading) {
            Text("Volver a iniciar sesión")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ForgotPasswordCodePreview() {
    NutriSocialTheme {
        ForgotPasswordScreen(
            state = PasswordResetState(
                email = "ana@nutrisocial.example",
                codeSent = true,
                info = "Si hay una cuenta con ese email, te hemos enviado un código para cambiar la contraseña."
            ),
            onRequestCode = {}, onReset = { _, _, _ -> }, onRequestAnotherCode = {}, onBack = {}, onDone = {}
        )
    }
}
