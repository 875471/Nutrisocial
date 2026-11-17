package com.example.nutrisocial

import com.example.nutrisocial.ui.home.formatSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tamaño del archivo de "Descargar mis datos" en el aviso que se muestra al terminar. */
class DataExportTest {

    @Test
    fun `el tamano se muestra en bytes, KB o MB con coma decimal`() {
        assertEquals("900 B", formatSize(900))
        assertEquals("5,6 KB", formatSize(5730))
        assertEquals("2,3 MB", formatSize(2_400_000))
    }
}
