package com.example.nutrisocial

import com.example.nutrisocial.data.DietTag
import com.example.nutrisocial.data.FromRecipeResult
import com.example.nutrisocial.data.ShoppingListItem
import com.example.nutrisocial.data.fromRecipeMessage
import com.example.nutrisocial.data.sortedForShopping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Etiquetas dietéticas (lo que se envía al backend) y utilidades de la lista de la compra. */
class DietTagsAndShoppingListTest {

    @Test
    fun `los identificadores coinciden con la lista fija del backend`() {
        assertEquals(
            listOf("vegano", "vegetariano", "sin_gluten", "sin_lactosa", "sin_frutos_secos"),
            DietTag.entries.map { it.id }
        )
    }

    @Test
    fun `el filtro va en el orden de la lista y sin etiquetas no se envia`() {
        assertNull(DietTag.toQuery(emptySet()))
        assertEquals("vegano,sin_gluten", DietTag.toQuery(setOf(DietTag.SIN_GLUTEN, DietTag.VEGANO)))
        assertEquals(listOf("vegetariano", "sin_lactosa"), DietTag.toIds(setOf(DietTag.SIN_LACTOSA, DietTag.VEGETARIANO)))
    }

    @Test
    fun `las etiquetas desconocidas de una receta se ignoran`() {
        assertEquals(listOf(DietTag.VEGANO, DietTag.SIN_GLUTEN), DietTag.fromIds(listOf("sin_gluten", "keto", "vegano")))
    }

    @Test
    fun `la lista se ordena con lo pendiente primero y por orden de alta`() {
        val items = listOf(
            ShoppingListItem(3, "Pan", checked = true, createdAt = "2026-11-12T10:00:01.000Z"),
            ShoppingListItem(2, "Leche", createdAt = "2026-11-12T10:00:02.000Z"),
            ShoppingListItem(1, "Huevos", createdAt = "2026-11-12T10:00:00.000Z"),
            ShoppingListItem(4, "Sal", checked = true, createdAt = "2026-11-12T10:00:00.000Z")
        )
        assertEquals(listOf("Huevos", "Leche", "Sal", "Pan"), items.sortedForShopping().map { it.label })
    }

    @Test
    fun `aviso de anadir lo que falta segun lo anadido y lo cubierto`() {
        val one = listOf(ShoppingListItem(1, "Harina"))
        assertEquals("Ya tienes todo en la despensa: no falta nada.", fromRecipeMessage(FromRecipeResult(emptyList(), 4)))
        assertEquals("Añadido 1 ingrediente a la lista de la compra.", fromRecipeMessage(FromRecipeResult(one, 0)))
        assertEquals(
            "Añadidos 2 ingredientes a la lista de la compra (3 ya en tu despensa).",
            fromRecipeMessage(FromRecipeResult(one + ShoppingListItem(2, "Azúcar"), 3))
        )
    }
}
