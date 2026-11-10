package com.example.nutrisocial

import com.example.nutrisocial.data.Plan
import com.example.nutrisocial.data.savingsPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ahorro del plan anual frente al mensual y formato de los precios que llegan del backend. */
class SubscriptionPlansTest {

    private val monthly = Plan(id = "monthly", priceCents = 399, periodMonths = 1, label = "Mensual")
    private val annual = Plan(id = "annual", priceCents = 2999, periodMonths = 12, label = "Anual")
    private val plans = listOf(monthly, annual)

    @Test
    fun `el anual ahorra un 37 por ciento frente a doce meses del mensual`() {
        // 29,99 / (12 · 3,99) = 0,626 → ahorra un 37,4 %.
        assertEquals(37, savingsPercent(annual, plans))
    }

    @Test
    fun `el plan de referencia y uno que no ahorra no llevan porcentaje`() {
        assertNull(savingsPercent(monthly, plans))
        val expensiveAnnual = annual.copy(priceCents = 4788)
        assertNull(savingsPercent(expensiveAnnual, listOf(monthly, expensiveAnnual)))
        assertNull(savingsPercent(annual, listOf(annual)))
    }

    @Test
    fun `el porcentaje se calcula con los precios recibidos`() {
        val cheaperAnnual = annual.copy(priceCents = 2394)
        assertEquals(50, savingsPercent(cheaperAnnual, listOf(monthly, cheaperAnnual)))
    }

    @Test
    fun `precios con formato espanol`() {
        // NumberFormat separa el símbolo con un espacio duro (U+00A0).
        assertEquals("3,99 €", monthly.formattedPrice)
        assertEquals("29,99 €", annual.formattedPrice)
        assertEquals(2999.0 / 12, annual.monthlyCents, 1e-9)
    }
}
