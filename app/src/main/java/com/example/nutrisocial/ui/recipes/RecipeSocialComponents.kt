package com.example.nutrisocial.ui.recipes

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    // null mientras se decodifica; Failed si el texto no es una imagen válida.
    val result by produceState<DecodedImage?>(initialValue = null, base64, maxDimension) {
        val bitmap = decodeBase64Cached(base64, maxDimension)
        if (bitmap == null) {
            // La longitud distingue un texto vacío o truncado de una foto entera pero corrupta.
            Log.w(PHOTO_LOG_TAG, "No se pudo decodificar una foto de ${base64.length} caracteres")
        }
        value = bitmap?.let { DecodedImage.Loaded(it.asImageBitmap()) } ?: DecodedImage.Failed
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        // Fundido suave al aparecer la foto, en vez de un salto del fondo a la imagen.
        Crossfade(targetState = result, label = "photo") { image ->
            when (image) {
                is DecodedImage.Loaded -> Image(
                    bitmap = image.bitmap,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // En vez de un hueco vacío, un aviso visible de que la foto existe pero no se puede ver.
                DecodedImage.Failed -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "No se pudo cargar la foto",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize(0.4f)
                    )
                }
                null -> Unit
            }
        }
    }
}

private sealed interface DecodedImage {
    data class Loaded(val bitmap: ImageBitmap) : DecodedImage
    data object Failed : DecodedImage
}

/** Etiqueta de Logcat para seguir una foto de receta de principio a fin (filtrar por "FotoReceta"). */
const val PHOTO_LOG_TAG = "FotoReceta"

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

/**
 * Corazón de "me gusta" con el contador. El cambio se pinta al momento (ver toggleLike) y se
 * anima: al dar like el corazón da un pequeño bote y el número se desliza hacia arriba (hacia
 * abajo al quitarlo). No se anima en la primera composición, solo cuando cambia.
 */
@Composable
fun LikeButton(liked: Boolean, count: Int, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    var previousLiked by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked && !previousLiked) {
            scale.animateTo(1.3f, animationSpec = tween(durationMillis = 90))
            scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        previousLiked = liked
    }
    val tint by animateColorAsState(
        targetValue = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "likeTint"
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (liked) "Quitar me gusta" else "Me gusta",
                tint = tint,
                modifier = Modifier.scale(scale.value)
            )
        }
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (up) -it else it } + fadeOut()) using SizeTransform(clip = false)
            },
            label = "likeCount",
            // El IconButton ya deja aire a su izquierda; el número va pegado al corazón.
            modifier = Modifier.padding(end = Spacing.sm)
        ) { value ->
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Suelto, un lector de pantalla solo diría "3"; así dice "3 me gusta".
                modifier = Modifier.semantics { contentDescription = "$value me gusta" }
            )
        }
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
                    val base64 = uriToCompressedBase64(context, uri)
                    Log.d(PHOTO_LOG_TAG, "Foto comprimida: ${base64.length} caracteres Base64 (≈ ${base64.length * 3 / 4 / 1024} KB)")
                    onPhotoReady(base64)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OutOfMemoryError) {
                    // Es un Error, no una Exception: sin capturarlo aparte, la app se cerraría.
                    Log.e(PHOTO_LOG_TAG, "Sin memoria al comprimir la foto $uri", e)
                    onMessage("La foto es demasiado grande para procesarla en este móvil. Prueba con otra.")
                } catch (e: Exception) {
                    Log.e(PHOTO_LOG_TAG, "No se pudo comprimir la foto $uri", e)
                    onMessage("No se pudo leer la foto: ${e.message ?: e::class.java.simpleName}")
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
