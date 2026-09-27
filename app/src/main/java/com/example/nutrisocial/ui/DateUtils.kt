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
