package com.example.nutrisocial.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Fechas de calendario como texto "AAAA-MM-DD", el formato que usa la API. Se trabaja con
// Calendar y SimpleDateFormat porque minSdk 24 no incluye java.time sin desugaring.

private val SpanishLocale = Locale("es", "ES")

private fun isoFormat(timeZone: TimeZone = TimeZone.getDefault()) =
    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        this.timeZone = timeZone
        isLenient = false
    }

private fun parseIso(iso: String, timeZone: TimeZone = TimeZone.getDefault()): Calendar? = try {
    Calendar.getInstance(timeZone).apply { time = isoFormat(timeZone).parse(iso)!! }
} catch (e: Exception) {
    null
}

/** Hoy en la zona horaria del teléfono. */
fun todayIso(): String = isoFormat().format(Calendar.getInstance().time)

/** El día [iso] desplazado [days] días (negativo hacia atrás). */
fun shiftDay(iso: String, days: Int): String {
    val calendar = parseIso(iso) ?: Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_MONTH, days)
    return isoFormat().format(calendar.time)
}

// El DatePicker de Material 3 trabaja con milisegundos a las 00:00 UTC del día elegido.
fun isoToPickerMillis(iso: String): Long? = parseIso(iso, TimeZone.getTimeZone("UTC"))?.timeInMillis

fun pickerMillisToIso(millis: Long): String = isoFormat(TimeZone.getTimeZone("UTC")).format(millis)

/** "Hoy", "Ayer", "Mañana" o "sáb, 26 sept 2026". */
fun dayLabel(iso: String): String {
    val today = todayIso()
    return when (iso) {
        today -> "Hoy"
        shiftDay(today, -1) -> "Ayer"
        shiftDay(today, 1) -> "Mañana"
        else -> parseIso(iso)?.let {
            SimpleDateFormat("EEE, d MMM yyyy", SpanishLocale).format(it.time)
        } ?: iso
    }
}

/** "1996-09-27" → "27/09/1996". */
fun displayDate(iso: String): String =
    parseIso(iso)?.let { SimpleDateFormat("dd/MM/yyyy", SpanishLocale).format(it.time) } ?: iso

// Instantes que envía el servidor (createdAt): ISO 8601 en UTC, como "2026-09-27T14:19:15.860Z".
private fun parseServerInstant(iso: String): Long? = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
    .firstNotNullOfOrNull { pattern ->
        try {
            SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)?.time
        } catch (e: Exception) {
            null
        }
    }

/**
 * Tiempo transcurrido desde [createdAt] en lenguaje natural: "ahora mismo", "hace 5 minutos",
 * "hace 2 horas", "hace 3 días", "hace 2 semanas"... Vacío si la fecha no se entiende. Si el
 * reloj del móvil va por detrás del servidor, una fecha "futura" también es "ahora mismo".
 */
fun formatRelativeTime(createdAt: String, now: Long = System.currentTimeMillis()): String {
    val then = parseServerInstant(createdAt) ?: return ""
    val minutes = (now - then) / 60_000
    fun ago(amount: Long, singular: String, plural: String) = "hace $amount ${if (amount == 1L) singular else plural}"
    return when {
        minutes < 1 -> "ahora mismo"
        minutes < 60 -> ago(minutes, "minuto", "minutos")
        minutes < 60 * 24 -> ago(minutes / 60, "hora", "horas")
        minutes < 60 * 24 * 7 -> ago(minutes / (60 * 24), "día", "días")
        minutes < 60 * 24 * 30 -> ago(minutes / (60 * 24 * 7), "semana", "semanas")
        minutes < 60 * 24 * 365 -> ago(minutes / (60 * 24 * 30), "mes", "meses")
        else -> ago(minutes / (60 * 24 * 365), "año", "años")
    }
}
