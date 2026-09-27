package com.example.nutrisocial.data.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reconocimiento de texto on-device con ML Kit Text Recognition v2 (modelo latino incluido
 * en el APK): la foto no sale del teléfono, solo el texto reconocido se envía al servidor.
 */
class RecipeTextRecognizer(private val context: Context) : Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Devuelve el texto de la imagen con una línea por cada línea detectada, en orden de lectura,
     * y una línea en blanco entre bloques. Cadena vacía si no se reconoce nada.
     */
    suspend fun recognize(imageUri: Uri): String {
        // fromFilePath lee la orientación EXIF, así que una foto hecha en vertical se procesa derecha.
        val image = InputImage.fromFilePath(context, imageUri)
        val result = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        // Texto plano de ML Kit tal cual, antes de enviarlo al servidor, para diagnosticar fotos
        // que se interpretan mal (filtrar el Logcat por OCR_RAW): revela el orden de lectura real.
        Log.d(OCR_RAW_TAG, result.text)
        return result.textBlocks.joinToString("\n\n") { block ->
            block.lines.joinToString("\n") { it.text }
        }.trim()
    }

    override fun close() = recognizer.close()
}

private const val OCR_RAW_TAG = "OCR_RAW"
