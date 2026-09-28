package com.example.nutrisocial

import com.example.nutrisocial.ui.avatarColorIndex
import com.example.nutrisocial.ui.avatarInitials
import com.example.nutrisocial.ui.formatRelativeTime
import com.example.nutrisocial.ui.recipes.CardIngredient
import com.example.nutrisocial.ui.recipes.likersText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Textos de la tarjeta del feed: tiempo relativo, avatar de iniciales y "Le gusta a...". */
class FeedCardTextTest {

    private val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .parse("2026-09-28T12:00:00Z")!!.time

    private fun ago(minutes: Long) = formatRelativeTime(isoMinutesBefore(minutes), now)

    private fun isoMinutesBefore(minutes: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(now - minutes * 60_000)

    @Test
    fun relativeTime_usesTheLargestWholeUnitInSpanish() {
        assertEquals("ahora mismo", ago(0))
        assertEquals("hace 1 minuto", ago(1))
        assertEquals("hace 59 minutos", ago(59))
        assertEquals("hace 1 hora", ago(60))
        assertEquals("hace 2 horas", ago(150))
        assertEquals("hace 1 día", ago(60 * 24))
        assertEquals("hace 3 días", ago(60 * 24 * 3))
        assertEquals("hace 2 semanas", ago(60 * 24 * 14))
        assertEquals("hace 1 mes", ago(60 * 24 * 30))
        assertEquals("hace 5 meses", ago(60 * 24 * 150))
        assertEquals("hace 1 año", ago(60 * 24 * 400))
    }

    @Test
    fun relativeTime_toleratesClockSkewAndBadInput() {
        // Si el reloj del móvil va por detrás, una fecha "futura" es "ahora mismo", no "hace -3 minutos".
        assertEquals("ahora mismo", ago(-3))
        // El servidor también puede mandar la fecha sin milisegundos.
        assertEquals("hace 2 horas", formatRelativeTime("2026-09-28T10:00:00Z", now))
        assertEquals("", formatRelativeTime("ayer", now))
        assertEquals("", formatRelativeTime("", now))
    }

    @Test
    fun avatarInitials_takesUpToTwoWords() {
        assertEquals("A", avatarInitials("ana"))
        assertEquals("LM", avatarInitials("Lucía Martín"))
        assertEquals("JL", avatarInitials("  Javier   López Demo "))
        assertEquals("?", avatarInitials("   "))
    }

    @Test
    fun avatarColor_isStableForTheSameName() {
        assertEquals(avatarColorIndex("Lucía Martín"), avatarColorIndex("Lucía Martín"))
        // Mayúsculas y espacios de más no cambian la persona.
        assertEquals(avatarColorIndex("Lucía Martín"), avatarColorIndex(" lucía martín "))
        val names = listOf("Ana", "Luis", "Marta", "Pablo", "Hugo", "Carmen", "Elena", "Sergio", "Irene", "Daniel")
        names.forEach { assertTrue(avatarColorIndex(it) in 0 until 6) }
        // Con diez nombres distintos no sale siempre el mismo color.
        assertTrue(names.map(::avatarColorIndex).toSet().size > 1)
    }

    @Test
    fun likersText_namesTheLatestLikerAndCountsTheRest() {
        assertNull(likersText(0, false, emptyList(), "Ana"))
        assertEquals("Le gusta a Luis", likersText(1, false, listOf("Luis"), "Ana")!!.plain)
        assertEquals("Le gusta a Luis y a otra persona", likersText(2, false, listOf("Luis", "Marta"), "Ana")!!.plain)
        assertEquals("Le gusta a Luis y a otras 4 personas", likersText(5, false, listOf("Luis", "Marta", "Pablo"), "Ana")!!.plain)
        // El nombre va aparte para pintarlo en negrita.
        assertEquals("Luis", likersText(5, false, listOf("Luis"), "Ana")!!.name)
        // Sin nombres (vista previa aún sin cargar), solo el número.
        assertEquals("Le gusta a 3 personas", likersText(3, false, emptyList(), "Ana")!!.plain)
    }

    @Test
    fun likersText_speaksToTheUserWhenTheyLikedIt() {
        assertEquals("Te gusta a ti", likersText(1, true, emptyList(), "Ana")!!.plain)
        // Recién dado el like, la lista de nombres aún no incluye al usuario: no se repite.
        assertEquals("Te gusta a ti y a Luis", likersText(2, true, listOf("Luis"), "Ana")!!.plain)
        assertEquals("Te gusta a ti y a Luis", likersText(2, true, listOf("Ana", "Luis"), "Ana")!!.plain)
        assertEquals("Te gusta a ti, a Luis y a otras 2 personas", likersText(4, true, listOf("Ana", "Luis"), "Ana")!!.plain)
        assertEquals("Te gusta a ti y a 2 personas más", likersText(3, true, listOf("Ana"), "Ana")!!.plain)
        // Tras quitar el like, el propio nombre que quedara en la lista no se usa.
        assertEquals("Le gusta a Luis", likersText(1, false, listOf("Ana", "Luis"), "Ana")!!.plain)
    }

    @Test
    fun cardIngredient_label() {
        assertEquals("200 g · Harina de trigo", CardIngredient("200 g", "Harina de trigo").label)
        assertEquals("Sal", CardIngredient(null, "Sal").label)
    }
}
