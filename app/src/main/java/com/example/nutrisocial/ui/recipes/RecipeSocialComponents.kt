package com.example.nutrisocial.ui.recipes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.R
import com.example.nutrisocial.ui.decodeBase64Cached
import com.example.nutrisocial.ui.rememberPhotoPicker
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.Spacing
import com.example.nutrisocial.ui.uriToCompressedBase64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Foto guardada en Base64. Se decodifica fuera del hilo principal (con caché) y, mientras
 * tanto, se ve el fondo neutro del tamaño final para que la lista no salte. Con [maxDimension]
 * se decodifica ya reducida, para las miniaturas.
 */
@Composable
fun Base64Image(
    base64: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxDimension: Int? = null
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, base64, maxDimension) {
        value = decodeBase64Cached(base64, maxDimension)?.asImageBitmap()
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Miniatura de la receta en las tarjetas: su foto si tiene, o el cuadro con la inicial. */
@Composable
fun RecipeThumbnail(title: String, imageBase64: String?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    if (imageBase64 != null) {
        Base64Image(
            base64 = imageBase64,
            contentDescription = null,
            // Se decodifica a 3 px por dp: nítida hasta en pantallas xxhdpi sin cargar la foto entera.
            maxDimension = (size.value * 3).toInt(),
            modifier = modifier
                .size(size)
                .clip(CardShape)
        )
    } else {
        InitialBadge(text = title, modifier = modifier.size(size))
    }
}

/** Corazón de "me gusta" con el contador. El cambio se pinta al momento (ver toggleLike). */
@Composable
fun LikeButton(liked: Boolean, count: Int, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (liked) "Quitar me gusta" else "Me gusta",
                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // El IconButton ya deja aire a su izquierda; el número va pegado al corazón.
            modifier = Modifier.padding(end = Spacing.sm)
        )
    }
}

/** Cámara o galería para la foto de una receta, que se entrega ya comprimida en Base64. */
class RecipePhotoPicker(
    val takePhoto: () -> Unit,
    val pickFromGallery: () -> Unit,
    val isProcessing: Boolean
)

/**
 * Como [rememberPhotoPicker], pero comprime la foto elegida (800 px, JPEG al 70 %) antes de
 * entregarla en [onPhotoReady]. Los avisos y errores llegan a [onMessage].
 */
@Composable
fun rememberRecipePhotoPicker(onPhotoReady: (String) -> Unit, onMessage: (String) -> Unit): RecipePhotoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    val picker = rememberPhotoPicker(
        cacheSubdir = "photos",
        onImageSelected = { uri ->
            isProcessing = true
            scope.launch {
                try {
                    onPhotoReady(uriToCompressedBase64(context, uri))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onMessage("No se pudo leer la foto. Prueba con otra imagen.")
                } finally {
                    isProcessing = false
                }
            }
        },
        onNotice = onMessage
    )
    return RecipePhotoPicker(picker.takePhoto, picker.pickFromGallery, isProcessing)
}

/** Botón que despliega "Hacer una foto" / "Elegir de la galería". */
@Composable
fun PhotoSourceButton(
    label: String,
    picker: RecipePhotoPicker,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled && !picker.isProcessing,
            shape = ButtonShape
        ) {
            if (picker.isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(label, modifier = Modifier.padding(start = Spacing.sm))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Hacer una foto") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = null) },
                onClick = {
                    expanded = false
                    picker.takePhoto()
                }
            )
            DropdownMenuItem(
                text = { Text("Elegir de la galería") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_photo_library), contentDescription = null) },
                onClick = {
                    expanded = false
                    picker.pickFromGallery()
                }
            )
        }
    }
}
