package com.example.nutrisocial.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Calidad aproximada de una foto antes de pasarla por el OCR.
 *
 * - [brightness]: luminancia media, de 0 (negro) a 255 (blanco).
 * - [sharpness]: varianza del laplaciano dividida por el brillo medio al cuadrado (× 1000).
 *   El laplaciano responde a los bordes: un texto enfocado tiene muchos y muy marcados, y
 *   uno borroso casi ninguno. Se divide por el brillo al cuadrado para que la medida no
 *   dependa de la exposición (una foto oscura pero enfocada tiene bordes más débiles en
 *   valor absoluto, no menos bordes). Normalizar por la varianza de toda la imagen, que es
 *   lo habitual, fallaba con las fotos con sombra: el degradado inflaba la varianza.
 *
 * Umbrales calibrados con una receta manuscrita de ejemplo (ver informe 14): la foto nítida
 * da ~15, con poco contraste (lápiz flojo) ~3,6, desenfocada con radio 4 ~1,8 y con radio 6 ~0,5.
 */
data class ImageQuality(val brightness: Double, val sharpness: Double) {
    val isDark: Boolean get() = brightness < DARK_THRESHOLD
    val isBlurry: Boolean get() = sharpness < BLUR_THRESHOLD
    val isPoor: Boolean get() = isDark || isBlurry

    companion object {
        const val DARK_THRESHOLD = 65.0
        const val BLUR_THRESHOLD = 1.0
    }
}

/**
 * Calcula la calidad a partir de la luminancia (0-255) de una imagen de [width] × [height],
 * por filas. Función pura, sin Android, para poder probarla en la JVM.
 */
fun assessImageQuality(luminance: DoubleArray, width: Int, height: Int): ImageQuality {
    require(luminance.size == width * height) { "Tamaño de imagen incoherente" }
    val brightness = luminance.average()
    if (width < 3 || height < 3) return ImageQuality(brightness, 0.0)

    // Laplaciano de 4 vecinos en los píxeles interiores; varianza en una sola pasada.
    var sum = 0.0
    var sumSq = 0.0
    var n = 0
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val i = y * width + x
            val lap = 4 * luminance[i] - luminance[i - 1] - luminance[i + 1] - luminance[i - width] - luminance[i + width]
            sum += lap
            sumSq += lap * lap
            n++
        }
    }
    val mean = sum / n
    val variance = sumSq / n - mean * mean
    val sharpness = 1000 * variance / (brightness * brightness + 1e-9)
    return ImageQuality(brightness, sharpness)
}

/** Lado máximo de la versión reducida sobre la que se mide: rápido y suficiente para ver bordes. */
private const val ANALYSIS_SIZE = 256

/**
 * Mide la calidad de la foto de [uri] sobre una copia reducida a 256 px de lado (unos 50 000
 * píxeles, unos milisegundos). Se hace en un hilo de fondo.
 */
suspend fun measureImageQuality(context: Context, uri: Uri): ImageQuality = withContext(Dispatchers.Default) {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    (resolver.openInputStream(uri) ?: throw IOException("No se pudo abrir la imagen"))
        .use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ANALYSIS_SIZE) sample *= 2
    val decoded = (resolver.openInputStream(uri) ?: throw IOException("No se pudo abrir la imagen"))
        .use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: throw IOException("El archivo no es una imagen")

    val scale = ANALYSIS_SIZE.toFloat() / max(decoded.width, decoded.height)
    val small = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
            .also { decoded.recycle() }
    } else {
        decoded
    }
    val pixels = IntArray(small.width * small.height)
    small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
    val luminance = DoubleArray(pixels.size) { i ->
        val p = pixels[i]
        0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)
    }
    assessImageQuality(luminance, small.width, small.height).also { small.recycle() }
}
