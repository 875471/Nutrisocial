package com.example.nutrisocial

import com.example.nutrisocial.data.CalendarDay
import com.example.nutrisocial.ui.log.calendarDayDescription
import com.example.nutrisocial.ui.monthGrid
import com.example.nutrisocial.ui.monthLabel
import com.example.nutrisocial.ui.monthOf
import com.example.nutrisocial.ui.shiftMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Utilidades del calendario mensual del diario. */
class MonthCalendarTest {

    @Test
    fun shiftMonth_crossesYearBoundaries() {
        assertEquals("2026-10", shiftMonth("2026-09", 1))
        assertEquals("2027-01", shiftMonth("2026-12", 1))
        assertEquals("2025-12", shiftMonth("2026-01", -1))
        assertEquals("2026-09", monthOf("2026-09-27"))
    }

    @Test
    fun monthGrid_startsOnMonday() {
        // El 1 de septiembre de 2026 es martes: un hueco (el lunes) antes del día 1.
        val september = monthGrid("2026-09")
        assertNull(september[0])
        assertEquals("2026-09-01", september[1])
        assertEquals("2026-09-30", september.last())
        assertEquals(1 + 30, september.size)
        // El 1 de febrero de 2026 es domingo: seis huecos, y febrero de 2026 tiene 28 días.
        val february = monthGrid("2026-02")
        assertEquals(6, february.takeWhile { it == null }.size)
        assertEquals("2026-02-28", february.last())
        // Año bisiesto.
        assertEquals("2028-02-29", monthGrid("2028-02").last())
        assertEquals(emptyList<String?>(), monthGrid("no es un mes"))
    }

    @Test
    fun monthLabel_isCapitalizedSpanish() {
        assertEquals("Septiembre 2026", monthLabel("2026-09"))
    }

    @Test
    fun dayDescription_readsKcalAndStatus() {
        assertEquals("3: sin registros", calendarDayDescription(3, null))
        assertEquals(
            "15: 2.400 kcal, excesivo",
            calendarDayDescription(15, CalendarDay("2026-09-15", 2400, CalendarDay.STATUS_EXCESS))
        )
        assertEquals("15: 900 kcal", calendarDayDescription(15, CalendarDay("2026-09-15", 900, null)))
    }
}
