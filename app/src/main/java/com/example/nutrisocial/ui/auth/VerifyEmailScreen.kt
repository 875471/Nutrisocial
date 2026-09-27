package com.example.nutrisocial.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.ui.theme.NutriSocialTheme

/**
 * Tras registrarse: hay que confirmar el correo antes de iniciar sesión. Si el servidor no pudo
 * enviarlo ([emailSent] = false), se dice claramente y se ofrece reenviarlo.
 */
@Composable
fun VerifyEmailScreen(
    email: String,
    emailSent: Boolean,
    resendState: ResendState,
    onResend: () -> Unit,
    onGoToLogin: () -> Unit
) {
    AuthFormLayout(title = "Confirma tu correo") {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
        Text(
            text = if (emailSent) {
                "Te hemos enviado un correo a $email. Abre el enlace que contiene para confirmar tu " +
                    "dirección antes de iniciar sesión."
            } else {
                "Tu cuenta está creada, pero no hemos podido enviar el correo de confirmación a $email. " +
                    "Prueba a reenviarlo dentro de un momento."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        // Solo tiene sentido si el correo ha salido.
        if (emailSent) {
            Text(
                text = "Si no lo ves, revisa la carpeta de spam. El enlace caduca en 24 horas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        ResendButton(resendState = resendState, onResend = onResend)
        AuthSubmitButton(text = "Ya lo he confirmado: iniciar sesión", isLoading = false, onClick = onGoToLogin)
    }
}

@Preview(showBackground = true)
@Composable
private fun VerifyEmailScreenPreview() {
    NutriSocialTheme {
        VerifyEmailScreen("ana@nutrisocial.example", emailSent = true, resendState = ResendState(), onResend = {}, onGoToLogin = {})
    }
}
