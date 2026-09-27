package com.example.nutrisocial.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

/**
 * Lee la imagen de [uri] (cámara o galería), la gira según su EXIF, la reduce para que su lado
 * más largo no pase de [maxDimension] px y la comprime a JPEG con calidad [quality]. Devuelve el
 * JPEG en Base64, sin el prefijo "data:image/...". Una foto de cámara de varios MB se queda así
 * en unos 50-150 KB, que es lo que se guarda en la base de datos.
 *
 * @throws IOException si la imagen no se puede leer.
 */
suspend fun uriToCompressedBase64(
    context: Context,
    uri: Uri,
    maxDimension: Int = 800,
    quality: Int = 70
): String = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver

    // 1) Solo las dimensiones, para no cargar en memoria la foto completa (12 Mpx ≈ 48 MB).
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: throw IOException("No se pudo abrir la imagen")
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("El archivo no es una imagen")

    // 2) Decodificación reducida por potencias de 2, sin bajar de maxDimension.
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    val sampled = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IOException("No se pudo decodificar la imagen")

    // 3) Escala exacta y giro en una sola transformación.
    val rotation = resolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0
    val scale = minOf(1f, maxDimension.toFloat() / max(sampled.width, sampled.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotation.toFloat())
    }
    val result = Bitmap.createBitmap(sampled, 0, 0, sampled.width, sampled.height, matrix, true)
    if (result !== sampled) sampled.recycle()

    val bytes = ByteArrayOutputStream().use { out ->
        result.compress(Bitmap.CompressFormat.JPEG, quality, out)
        result.recycle()
        out.toByteArray()
    }
    Base64.encodeToString(bytes, Base64.NO_WRAP)
}

/**
 * Decodifica una foto en Base64. Con [maxDimension] se decodifica ya reducida (para miniaturas),
 * lo que ahorra memoria en listas largas. Devuelve null si el texto no es una imagen válida.
 */
fun base64ToBitmap(base64: String, maxDimension: Int? = null): Bitmap? = try {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    val options = BitmapFactory.Options()
    if (maxDimension != null) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        options.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
} catch (e: IllegalArgumentException) {
    null
}

// Caché de fotos ya decodificadas: al hacer scroll en el feed no se vuelve a decodificar cada
// tarjeta. Se limita a 1/8 de la memoria de la app, medido en bytes de bitmap.
private val bitmapCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

/** Como [base64ToBitmap], fuera del hilo principal y con caché. */
suspend fun decodeBase64Cached(base64: String, maxDimension: Int? = null): Bitmap? {
    // La clave no guarda el texto entero (puede ocupar cientos de KB).
    val key = "${base64.length}:${base64.hashCode()}:${maxDimension ?: 0}"
    bitmapCache.get(key)?.let { return it }
    return withContext(Dispatchers.Default) { base64ToBitmap(base64, maxDimension) }
        ?.also { bitmapCache.put(key, it) }
}

/** Mayor potencia de 2 que deja el lado más largo por encima de [maxDimension]. */
private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxDimension) sample *= 2
    return sample
}

private fun exifRotation(stream: java.io.InputStream): Int = try {
    when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
} catch (e: IOException) {
    0
}
