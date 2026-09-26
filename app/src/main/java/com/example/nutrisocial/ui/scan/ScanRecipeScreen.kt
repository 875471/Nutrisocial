package com.example.nutrisocial.ui.scan

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.nutrisocial.R
import com.example.nutrisocial.data.OcrRecipeProposal
import com.example.nutrisocial.ui.recipes.RecipeCardElevation
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanRecipeScreen(
    state: ScanUiState,
    onImageSelected: (Uri) -> Unit,
    onRetry: () -> Unit,
    onProposalReady: (OcrRecipeProposal) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    // Uri de la foto en curso: se guarda para sobrevivir a que el sistema recree la actividad
    // mientras la app de cámara está en primer plano.
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingPhoto
        if (saved && uri != null) onImageSelected(uri)
    }

    fun launchCamera() {
        val uri = createPhotoUri(context)
        pendingPhoto = uri
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            notice = "No hay ninguna app de cámara disponible. Elige una foto de la galería."
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            notice = "Sin permiso de cámara no se pueden hacer fotos. Puedes concederlo en los ajustes " +
                "de Android o elegir una foto de la galería."
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImageSelected(uri)
    }

    LaunchedEffect(state) {
        if (state is ScanUiState.Ready) onProposalReady(state.proposal)
    }

    val busy = state is ScanUiState.Recognizing || state is ScanUiState.Analyzing || state is ScanUiState.Ready

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escanear receta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "Haz una foto a una receta escrita a mano o impresa. El texto se lee en tu teléfono " +
                    "y te propondremos los ingredientes y los pasos para que los revises antes de guardar.",
                style = MaterialTheme.typography.bodyLarge
            )

            TipsCard()

            Button(
                onClick = {
                    notice = null
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
                },
                enabled = !busy,
                shape = ButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Hacer una foto", modifier = Modifier.padding(start = Spacing.sm))
            }
            OutlinedButton(
                onClick = {
                    notice = null
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !busy,
                shape = ButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(painterResource(R.drawable.ic_photo_library), contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Elegir de la galería", modifier = Modifier.padding(start = Spacing.sm))
            }

            notice?.let { StatusCard(message = it) }

            when (state) {
                ScanUiState.Recognizing -> ProgressRow("Leyendo el texto de la foto…")
                ScanUiState.Analyzing, is ScanUiState.Ready -> ProgressRow("Identificando ingredientes y pasos…")
                ScanUiState.NoText -> StatusCard(
                    title = "No se ha reconocido texto",
                    message = "Prueba con más luz, acercando la cámara a la hoja o con otra imagen.",
                    isError = true
                )
                is ScanUiState.Error -> StatusCard(
                    title = "No se pudo completar el escaneo",
                    message = state.message,
                    isError = true,
                    actionLabel = if (state.canRetrySameImage) "Reintentar" else null,
                    onAction = onRetry
                )
                ScanUiState.Idle -> Unit
            }
        }
    }
}

/** Crea un archivo vacío en la caché y devuelve su Uri de FileProvider para la app de cámara. */
private fun createPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "ocr").apply { mkdirs() }
    // Las fotos solo se necesitan mientras se procesan: se borran las de escaneos anteriores.
    dir.listFiles()?.forEach { it.delete() }
    val file = File.createTempFile("receta_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun TipsCard() {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = RecipeCardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Para una buena lectura", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            listOf(
                "Buena luz y sin sombras sobre la hoja.",
                "Hoja plana, recta y ocupando casi toda la foto.",
                "Cada ingrediente en su línea, con la cantidad delante: «200 g de arroz».",
                "Si puedes, separa los apartados «Ingredientes» y «Preparación»."
            ).forEach { tip ->
                Text("•  $tip", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProgressRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.sm)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.md))
    }
}

@Composable
private fun StatusCard(
    message: String,
    title: String? = null,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    val container: Color
    val content: Color
    if (isError) {
        container = MaterialTheme.colorScheme.errorContainer
        content = MaterialTheme.colorScheme.onErrorContainer
    } else {
        container = MaterialTheme.colorScheme.secondaryContainer
        content = MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(shape = CardShape, color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null) {
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                    Text(actionLabel, color = content)
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ScanRecipeScreenPreview() {
    NutriSocialTheme {
        ScanRecipeScreen(
            state = ScanUiState.NoText,
            onImageSelected = {}, onRetry = {}, onProposalReady = {}, onBack = {}
        )
    }
}
