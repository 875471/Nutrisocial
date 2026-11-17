package com.example.nutrisocial

import com.example.nutrisocial.data.LogStatsDay
import com.example.nutrisocial.ui.log.kcalChartDescription
import com.example.nutrisocial.ui.log.shortDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Textos de la pantalla de estadísticas del diario: fechas del eje y descripción para TalkBack. */
class LogStatsTest {

    private fun day(date: String, kcal: Int, entries: Int = 1) = LogStatsDay(date, kcal, 0.0, 0.0, 0.0, entries)

    @Test
    fun `la fecha del eje es dia y mes`() {
        assertEquals("17/11", shortDate("2026-11-17"))
    }

    @Test
    fun `la descripcion del grafico ignora los dias sin registro y cuenta los que pasan del objetivo`() {
        val days = listOf(day("2026-11-15", 1800), day("2026-11-16", 0, entries = 0), day("2026-11-17", 2500))
        assertEquals(
            "Gráfico de barras de calorías de 3 días. Máximo 2500 kcal el 17/11, mínimo 1800 kcal el 15/11. " +
                "1 día por encima del objetivo de 2000 kcal.",
            kcalChartDescription(days, 2000)
        )
        assertEquals("Gráfico de calorías sin datos", kcalChartDescription(listOf(day("2026-11-16", 0, 0)), 2000))
    }
}
