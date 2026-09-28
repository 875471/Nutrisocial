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

// ---- Meses ("AAAA-MM") para el calendario mensual del diario ----

private fun parseMonth(month: String): Calendar? = try {
    val format = SimpleDateFormat("yyyy-MM", Locale.ROOT).apply { isLenient = false }
    Calendar.getInstance().apply {
        time = format.parse(month)!!
        set(Calendar.DAY_OF_MONTH, 1)
    }
} catch (e: Exception) {
    null
}

private fun formatMonth(calendar: Calendar): String =
    SimpleDateFormat("yyyy-MM", Locale.ROOT).format(calendar.time)

/** Mes de un día: "2026-09-27" → "2026-09". */
fun monthOf(iso: String): String = iso.take(7)

/** El mes [month] desplazado [months] meses (negativo hacia atrás). */
fun shiftMonth(month: String, months: Int): String {
    val calendar = parseMonth(month) ?: Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    calendar.add(Calendar.MONTH, months)
    return formatMonth(calendar)
}

/** "2026-09" → "Septiembre 2026". */
fun monthLabel(month: String): String =
    parseMonth(month)?.let { calendar ->
        // "LLLL": nombre del mes en su forma independiente ("septiembre", no "de septiembre").
        SimpleDateFormat("LLLL yyyy", SpanishLocale).format(calendar.time).replaceFirstChar { it.uppercase() }
    } ?: month

/**
 * Casillas del mes para una rejilla de 7 columnas que empieza en lunes: `null` para los huecos
 * anteriores al día 1 y, después, cada día como "AAAA-MM-DD".
 */
fun monthGrid(month: String): List<String?> {
    val calendar = parseMonth(month) ?: return emptyList()
    // DAY_OF_WEEK va de domingo (1) a sábado (7); con la semana en lunes, el lunes es la columna 0.
    val leadingBlanks = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val days = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val prefix = formatMonth(calendar)
    return List(leadingBlanks) { null } + (1..days).map { day -> "$prefix-${day.toString().padStart(2, '0')}" }
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
