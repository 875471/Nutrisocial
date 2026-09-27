package com.example.nutrisocial.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/** Lanzadores para obtener una foto de la cámara o de la galería (ver [rememberPhotoPicker]). */
class PhotoPicker(
    val takePhoto: () -> Unit,
    val pickFromGallery: () -> Unit
)

/**
 * Cámara y galería con el mismo mecanismo en toda la app: la cámara escribe la foto en un
 * archivo de la caché ([cacheSubdir], compartido con FileProvider, ver res/xml/file_paths.xml)
 * y pide el permiso si hace falta; la galería usa el selector de fotos del sistema, que no
 * necesita permisos. [onNotice] recibe los avisos para el usuario (sin cámara, permiso denegado).
 */
@Composable
fun rememberPhotoPicker(
    cacheSubdir: String,
    onImageSelected: (Uri) -> Unit,
    onNotice: (String) -> Unit
): PhotoPicker {
    val context = LocalContext.current
    // Uri de la foto en curso: se guarda para sobrevivir a que el sistema recree la actividad
    // mientras la app de cámara está en primer plano.
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingPhoto
        if (saved && uri != null) onImageSelected(uri)
    }

    fun launchCamera() {
        val uri = createPhotoUri(context, cacheSubdir)
        pendingPhoto = uri
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            onNotice("No hay ninguna app de cámara disponible. Elige una foto de la galería.")
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            onNotice(
                "Sin permiso de cámara no se pueden hacer fotos. Puedes concederlo en los ajustes " +
                    "de Android o elegir una foto de la galería."
            )
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImageSelected(uri)
    }

    return PhotoPicker(
        takePhoto = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
        },
        pickFromGallery = {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    )
}

/** Crea un archivo vacío en la caché y devuelve su Uri de FileProvider para la app de cámara. */
private fun createPhotoUri(context: Context, cacheSubdir: String): Uri {
    val dir = File(context.cacheDir, cacheSubdir).apply { mkdirs() }
    // Las fotos solo se necesitan mientras se procesan: se borran las anteriores.
    dir.listFiles()?.forEach { it.delete() }
    val file = File.createTempFile("foto_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
