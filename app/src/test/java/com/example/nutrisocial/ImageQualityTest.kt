package com.example.nutrisocial

import com.example.nutrisocial.data.ocr.assessImageQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del indicador de calidad de foto previo al OCR (data/ocr/ImageQuality.kt). */
class ImageQualityTest {

    private val size = 64

    /** "Texto": fondo claro con trazos oscuros de 1 px cada 8 columnas y cada 10 filas. */
    private fun textLike(background: Double = 230.0, ink: Double = 40.0) = DoubleArray(size * size) { i ->
        val x = i % size
        val y = i / size
        if (x % 8 == 0 || y % 10 == 0) ink else background
    }

    /** Desenfoque de caja de radio [r]. Aplicado dos veces se parece a un desenfoque gaussiano. */
    private fun blur(src: DoubleArray, r: Int) = DoubleArray(src.size) { i ->
        val x = i % size
        val y = i / size
        var sum = 0.0
        var n = 0
        for (dy in -r..r) for (dx in -r..r) {
            val xx = (x + dx).coerceIn(0, size - 1)
            val yy = (y + dy).coerceIn(0, size - 1)
            sum += src[yy * size + xx]
            n++
        }
        sum / n
    }

    @Test
    fun textoEnfocadoYBienIluminado_noAvisa() {
        val q = assessImageQuality(textLike(), size, size)
        assertFalse(q.isDark)
        assertFalse(q.isBlurry)
        assertFalse(q.isPoor)
    }

    @Test
    fun fotoMuyDesenfocada_esBorrosa() {
        val q = assessImageQuality(blur(blur(textLike(), 3), 3), size, size)
        assertTrue("nitidez ${q.sharpness}", q.isBlurry)
    }

    @Test
    fun fotoMuyOscura_esOscuraPeroNoBorrosa() {
        // La misma imagen a un 15 % de exposición: oscura, pero los bordes siguen ahí.
        val dark = textLike().map { it * 0.15 }.toDoubleArray()
        val q = assessImageQuality(dark, size, size)
        assertTrue("brillo ${q.brightness}", q.isDark)
        assertFalse("nitidez ${q.sharpness}", q.isBlurry)
    }

    @Test
    fun laNitidezNoDependeDeLaExposicion() {
        val normal = assessImageQuality(textLike(), size, size)
        val dim = assessImageQuality(textLike().map { it * 0.4 }.toDoubleArray(), size, size)
        assertEquals(normal.sharpness, dim.sharpness, normal.sharpness * 0.01)
    }

    @Test
    fun imagenLisa_noTieneBordes() {
        val q = assessImageQuality(DoubleArray(size * size) { 200.0 }, size, size)
        assertEquals(0.0, q.sharpness, 1e-6)
        assertTrue(q.isBlurry)
    }
}
