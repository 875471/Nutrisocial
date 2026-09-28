package com.example.nutrisocial.data.ocr

import java.text.Normalizer

/**
 * Métricas de calidad del reconocimiento de texto para la evaluación del OCR (ver
 * `androidTest/.../OcrRecognitionEvalTest.kt` e informe 17): Character Error Rate (CER) y
 * Word Error Rate (WER), calculadas con la distancia de edición de Levenshtein, sin librerías.
 *
 *   CER = (sustituciones + borrados + inserciones de caracteres) / caracteres de la referencia
 *   WER = lo mismo contando palabras en lugar de caracteres
 *
 * Antes de comparar, los espacios y saltos de línea se reducen a un solo espacio: la línea en
 * blanco que la app pone entre bloques de ML Kit, o dónde parte una línea la transcripción, no
 * son errores de lectura. Las mayúsculas, las tildes y la puntuación sí cuentan (un "1" leído
 * como "l" es un error real); [foldCaseAndAccents] da la variante que no las tiene en cuenta.
 */
object OcrTextMetrics {

    /** Errores y tamaño de la referencia, en caracteres y en palabras, de una comparación. */
    data class Score(val charErrors: Int, val referenceChars: Int, val wordErrors: Int, val referenceWords: Int) {
        val cer: Double get() = if (referenceChars == 0) 0.0 else charErrors.toDouble() / referenceChars
        val wer: Double get() = if (referenceWords == 0) 0.0 else wordErrors.toDouble() / referenceWords

        operator fun plus(other: Score) = Score(
            charErrors + other.charErrors,
            referenceChars + other.referenceChars,
            wordErrors + other.wordErrors,
            referenceWords + other.referenceWords
        )
    }

    private val WHITESPACE = Regex("\\s+")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /** Todo espacio, tabulador o salto de línea seguido pasa a ser un solo espacio. */
    fun normalizeWhitespace(text: String): String = text.trim().replace(WHITESPACE, " ")

    /** Minúsculas y sin tildes ni diéresis ("Añadir" → "anadir"), para la variante tolerante. */
    fun foldCaseAndAccents(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

    /** Distancia de Levenshtein entre dos secuencias, con dos filas de memoria. */
    fun <T> editDistance(a: List<T>, b: List<T>): Int {
        var previous = IntArray(b.size + 1) { it }
        var current = IntArray(b.size + 1)
        for (i in 1..a.size) {
            current[0] = i
            for (j in 1..b.size) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.size]
    }

    fun editDistance(a: String, b: String): Int = editDistance(a.toList(), b.toList())

    /** Compara el texto reconocido ([hypothesis]) con la transcripción correcta ([reference]). */
    fun score(reference: String, hypothesis: String, tolerant: Boolean = false): Score {
        val ref = normalizeWhitespace(reference).let { if (tolerant) foldCaseAndAccents(it) else it }
        val hyp = normalizeWhitespace(hypothesis).let { if (tolerant) foldCaseAndAccents(it) else it }
        val refWords = if (ref.isEmpty()) emptyList() else ref.split(' ')
        val hypWords = if (hyp.isEmpty()) emptyList() else hyp.split(' ')
        return Score(
            charErrors = editDistance(ref, hyp),
            referenceChars = ref.length,
            wordErrors = editDistance(refWords, hypWords),
            referenceWords = refWords.size
        )
    }
}
