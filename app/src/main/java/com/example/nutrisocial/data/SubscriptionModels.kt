package com.example.nutrisocial.data

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Plan de la suscripción Premium simulada, tal como lo devuelve GET /subscription/plans: los
 * precios vienen siempre del backend, la app no tiene ninguno escrito a mano.
 */
data class Plan(
    val id: String,
    val priceCents: Int,
    val currency: String = "EUR",
    val periodMonths: Int,
    val label: String = ""
) {
    /** Precio con formato español: 2999 céntimos en EUR → "29,99 €". */
    val formattedPrice: String get() = formatCents(priceCents, currency)

    /** Precio por mes, para comparar planes de distinta duración. */
    val monthlyCents: Double get() = priceCents.toDouble() / periodMonths
}

/** Estado de GET /subscription. Sin suscripción solo llega `isPremium: false`. */
data class SubscriptionStatus(
    val isPremium: Boolean = false,
    val plan: String? = null,
    // "active" | "canceled"
    val status: String? = null,
    val startedAt: String? = null,
    val currentPeriodEnd: String? = null
)

data class SubscribeRequest(val plan: String)

/**
 * Porcentaje que ahorra [plan] frente a pagar mes a mes el plan de periodo más corto de
 * [plans], redondeado al entero: con 3,99 €/mes y 29,99 €/año, 37. Null si no ahorra nada o
 * si [plan] es el propio plan de referencia.
 */
fun savingsPercent(plan: Plan, plans: List<Plan>): Int? {
    val reference = plans.minByOrNull { it.periodMonths } ?: return null
    if (reference.id == plan.id || reference.priceCents <= 0) return null
    val percent = ((1 - plan.monthlyCents / reference.monthlyCents) * 100).roundToInt()
    return percent.takeIf { it > 0 }
}

fun formatCents(cents: Number, currency: String): String =
    NumberFormat.getCurrencyInstance(Locale("es", "ES")).apply {
        this.currency = Currency.getInstance(currency)
    }.format(cents.toDouble() / 100)
