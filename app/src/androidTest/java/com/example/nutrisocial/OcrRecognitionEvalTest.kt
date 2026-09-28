package com.example.nutrisocial

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nutrisocial.data.ocr.OcrTextMetrics
import com.example.nutrisocial.data.ocr.RecipeTextRecognizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Evaluación del reconocimiento de texto (núcleo 3.1, parte del móvil): CER y WER de ML Kit sobre
 * fotos reales de recetas frente a su transcripción hecha a mano. Ver informe 17.
 *
 * Datos: `app/src/androidTest/assets/ocr_eval/`, con un par de archivos por receta:
 *   receta01.jpg  la foto (también .jpeg o .png)
 *   receta01.txt  la transcripción correcta, en UTF-8, línea a línea en orden de lectura
 *
 * Cada foto pasa por [RecipeTextRecognizer], el mismo código que usa el escáner de la app
 * (incluida la orientación EXIF y la reconstrucción por bloques y líneas). Los resultados se
 * escriben en Logcat (etiqueta OCR_EVAL), en el informe de la instrumentación y en
 * `Android/data/com.example.nutrisocial/files/ocr_eval/`, junto con el texto reconocido de cada
 * foto (sirve para evaluar después la segmentación con esos textos reales).
 *
 * Ejecutar con un dispositivo o emulador conectado:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.example.nutrisocial.OcrRecognitionEvalTest
 * Gradle desinstala la app al terminar (y con ella la sesión iniciada y los resultados guardados en
 * el móvil); para conservarlos, añadir -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
 * o lanzar la prueba desde Android Studio.
 *
 * No es una prueba de que el código funcione, sino una medición: no falla por un CER alto. Si la
 * carpeta no tiene imágenes, la prueba se omite.
 */
@RunWith(AndroidJUnit4::class)
class OcrRecognitionEvalTest {

    private data class Sample(val name: String, val image: String, val transcription: String)

    private data class Result(val name: String, val strict: OcrTextMetrics.Score, val tolerant: OcrTextMetrics.Score)

    // Con cuerpo de bloque y no "= runBlocking { ... }": JUnit exige que el método devuelva void.
    @Test
    fun characterAndWordErrorRates() {
        runBlocking { evaluate() }
    }

    private suspend fun evaluate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Los assets de androidTest están en el APK de pruebas, no en el de la app.
        val assets = instrumentation.context.assets
        val targetContext = instrumentation.targetContext

        val files = assets.list(DIR).orEmpty().toSet()
        val images = files.filter { name -> IMAGE_EXTENSIONS.any { name.lowercase(Locale.ROOT).endsWith(it) } }.sorted()
        assumeTrue("No hay imágenes en androidTest/assets/$DIR: nada que evaluar", images.isNotEmpty())

        val samples = images.map { image ->
            val name = image.substringBeforeLast('.')
            Sample(name, image, "$name.txt")
        }
        val missing = samples.filter { it.transcription !in files }.map { it.transcription }
        assertTrue("Faltan transcripciones: ${missing.joinToString()}", missing.isEmpty())

        val outDir = File(targetContext.getExternalFilesDir(null), DIR).apply { mkdirs() }
        val results = RecipeTextRecognizer(targetContext).use { recognizer ->
            samples.map { sample ->
                // RecipeTextRecognizer trabaja con Uri, como con la cámara y la galería: se copia
                // la foto a un archivo y se le pasa su Uri, igual que al escanear.
                val imageFile = File(targetContext.cacheDir, "ocr_eval_${sample.image}")
                assets.open("$DIR/${sample.image}").use { input -> imageFile.outputStream().use { input.copyTo(it) } }
                val recognized = recognizer.recognize(Uri.fromFile(imageFile))
                imageFile.delete()

                val reference = assets.open("$DIR/${sample.transcription}").bufferedReader(Charsets.UTF_8).use { it.readText() }
                File(outDir, "${sample.name}.reconocido.txt").writeText(recognized)
                Result(
                    name = sample.name,
                    strict = OcrTextMetrics.score(reference, recognized),
                    tolerant = OcrTextMetrics.score(reference, recognized, tolerant = true)
                )
            }
        }

        val report = buildReport(results)
        File(outDir, "resultados.md").writeText(report)
        report.lines().forEach { Log.i(TAG, it) }
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$report\n") })
        Log.i(TAG, "Resultados y textos reconocidos guardados en ${outDir.absolutePath}")
    }

    private fun buildReport(results: List<Result>): String = buildString {
        fun pct(value: Double) = String.format(Locale("es", "ES"), "%.1f %%", value * 100)
        appendLine("# CER y WER de ML Kit (${results.size} fotos)")
        appendLine()
        appendLine("| Foto | CER | WER | CER sin mayúsculas ni tildes | WER sin mayúsculas ni tildes | Caracteres | Palabras |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        for (r in results) {
            appendLine(
                "| ${r.name} | ${pct(r.strict.cer)} | ${pct(r.strict.wer)} | ${pct(r.tolerant.cer)} | " +
                    "${pct(r.tolerant.wer)} | ${r.strict.referenceChars} | ${r.strict.referenceWords} |"
            )
        }
        val strictTotal = results.map { it.strict }.reduce(OcrTextMetrics.Score::plus)
        val tolerantTotal = results.map { it.tolerant }.reduce(OcrTextMetrics.Score::plus)
        appendLine()
        appendLine("Media por foto: CER ${pct(results.map { it.strict.cer }.average())}, WER ${pct(results.map { it.strict.wer }.average())}")
        appendLine("Media por foto sin mayúsculas ni tildes: CER ${pct(results.map { it.tolerant.cer }.average())}, WER ${pct(results.map { it.tolerant.wer }.average())}")
        appendLine("Global (errores totales / tamaño total de las referencias): CER ${pct(strictTotal.cer)}, WER ${pct(strictTotal.wer)}")
        appendLine("Global sin mayúsculas ni tildes: CER ${pct(tolerantTotal.cer)}, WER ${pct(tolerantTotal.wer)}")
    }

    private companion object {
        const val DIR = "ocr_eval"
        const val TAG = "OCR_EVAL"
        val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png")
    }
}
