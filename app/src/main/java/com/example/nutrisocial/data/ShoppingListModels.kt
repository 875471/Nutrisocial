package com.example.nutrisocial.data

/**
 * Elemento de la lista de la compra. [quantity] es texto libre ("200 g"): el servidor no suma
 * cantidades ni convierte unidades, y el mismo ingrediente añadido dos veces son dos elementos.
 * [sourceRecipeId] y [sourceRecipeTitle] dicen de qué receta salió (null si se añadió a mano).
 */
data class ShoppingListItem(
    val id: Int,
    val label: String,
    val quantity: String? = null,
    val checked: Boolean = false,
    val sourceRecipeId: Int? = null,
    val sourceRecipeTitle: String? = null,
    val createdAt: String = ""
)

data class AddShoppingListItemRequest(val label: String, val quantity: String? = null)

/** Cuerpo de PATCH /shopping-list/{id}. Gson omite los null: solo se envía lo que cambia. */
data class UpdateShoppingListItemRequest(
    val checked: Boolean? = null,
    val label: String? = null,
    // "" la quita (el servidor guarda null).
    val quantity: String? = null
)

/** Respuesta de POST /shopping-list/from-recipe/{id}: lo añadido y cuántos ya cubría la despensa. */
data class FromRecipeResult(
    val added: List<ShoppingListItem> = emptyList(),
    val alreadyInPantry: Int = 0
)

/** Respuesta de DELETE /shopping-list?checked=true. */
data class ClearCheckedResult(val deleted: Int = 0)

/**
 * Orden de la lista que devuelve el servidor (sin marcar primero y, dentro de cada grupo, por orden
 * de alta), para recolocar en local tras marcar o añadir sin volver a pedirla.
 */
fun List<ShoppingListItem>.sortedForShopping(): List<ShoppingListItem> =
    sortedWith(compareBy<ShoppingListItem> { it.checked }.thenBy { it.createdAt }.thenBy { it.id })

/** Texto del aviso tras pulsar "Añadir lo que falta" en el detalle de una receta. */
fun fromRecipeMessage(result: FromRecipeResult): String {
    val added = result.added.size
    val covered = result.alreadyInPantry
    return when {
        added == 0 -> "Ya tienes todo en la despensa: no falta nada."
        covered == 0 -> if (added == 1) "Añadido 1 ingrediente a la lista de la compra." else "Añadidos $added ingredientes a la lista de la compra."
        else -> (if (added == 1) "Añadido 1 ingrediente" else "Añadidos $added ingredientes") +
            " a la lista de la compra ($covered ya en tu despensa)."
    }
}
