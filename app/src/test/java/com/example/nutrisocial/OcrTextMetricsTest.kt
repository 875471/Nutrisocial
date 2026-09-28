package com.example.nutrisocial

import com.example.nutrisocial.data.ocr.OcrTextMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cálculo de CER y WER de la evaluación del OCR. */
class OcrTextMetricsTest {

    @Test
    fun editDistance_countsSubstitutionsDeletionsAndInsertions() {
        assertEquals(0, OcrTextMetrics.editDistance("cebolla", "cebolla"))
        assertEquals(1, OcrTextMetrics.editDistance("cebolla", "cebola"))
        assertEquals(1, OcrTextMetrics.editDistance("100", "l00"))
        assertEquals(3, OcrTextMetrics.editDistance("kitten", "sitting"))
        assertEquals(4, OcrTextMetrics.editDistance("", "abcd"))
        assertEquals(1, OcrTextMetrics.editDistance(listOf("2", "huevos"), listOf("2", "huevo")))
    }

    @Test
    fun score_ignoresLineBreaksAndBlankLinesBetweenBlocks() {
        val score = OcrTextMetrics.score("Ingredientes\n2 huevos", "Ingredientes\n\n2  huevos\n")
        assertEquals(0, score.charErrors)
        assertEquals(0, score.wordErrors)
    }

    @Test
    fun score_cerAndWerOverTheReference() {
        // "300 g de lentejas" (17 caracteres, 4 palabras) leído como "300 9 de lentejas".
        val score = OcrTextMetrics.score("300 g de lentejas", "300 9 de lentejas")
        assertEquals(1, score.charErrors)
        assertEquals(17, score.referenceChars)
        assertEquals(1.0 / 17, score.cer, 1e-9)
        assertEquals(0.25, score.wer, 1e-9)
    }

    @Test
    fun score_tolerantVariantIgnoresCaseAndAccents() {
        assertEquals(2, OcrTextMetrics.score("Preparación", "PreparAcion").charErrors)
        assertEquals(0, OcrTextMetrics.score("Preparación", "PreparAcion", tolerant = true).charErrors)
    }

    @Test
    fun scores_addUpForAGlobalRate() {
        val total = OcrTextMetrics.score("abcd", "abcx") + OcrTextMetrics.score("efgh ij", "efgh ij")
        assertEquals(1, total.charErrors)
        assertEquals(11, total.referenceChars)
        assertEquals(1.0 / 3, total.wer, 1e-9)
    }
}
