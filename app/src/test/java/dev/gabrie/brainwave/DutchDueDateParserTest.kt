package dev.gabrie.brainwave

import dev.gabrie.brainwave.ai.DueDateParser
import dev.gabrie.brainwave.settings.NoteLanguage
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DutchDueDateParserTest {

    // Woensdag 12 augustus 2026, 10:00.
    private val now = LocalDateTime.of(2026, 8, 12, 10, 0)

    private fun parse(text: String) =
        DueDateParser.parse(text, now, defaultHour = 9, language = NoteLanguage.DUTCH)

    @Test
    fun `geen datum levert null op`() {
        assertNull(parse("nog eens nadenken over de schutting"))
    }

    @Test
    fun `morgen is de volgende dag`() {
        val result = parse("morgen de loodgieter bellen")!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 9, 0), result.dateTime)
        assertTrue(!result.hasTime)
    }

    @Test
    fun `overmorgen wint van morgen`() {
        val result = parse("overmorgen de ferry boeken")!!
        assertEquals(LocalDateTime.of(2026, 8, 14, 9, 0), result.dateTime)
    }

    /** "vanmorgen" means *this* morning — it must not be read as "morgen". */
    @Test
    fun `vanmorgen is vandaag en niet morgen`() {
        val result = parse("vanmorgen de post ophalen")!!
        assertEquals(LocalDateTime.of(2026, 8, 12, 9, 0), result.dateTime)
        assertTrue(result.hasTime)
    }

    @Test
    fun `tijd met om wordt herkend`() {
        val result = parse("morgen om 15:30 naar de tandarts")!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 15, 30), result.dateTime)
        assertTrue(result.hasTime)
    }

    @Test
    fun `om vijf uur wordt als middag gelezen`() {
        val result = parse("morgen om 5 uur de kinderen ophalen")!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 17, 0), result.dateTime)
    }

    @Test
    fun `op vrijdag is de eerstvolgende vrijdag`() {
        val result = parse("op vrijdag de factuur versturen")!!
        assertEquals(LocalDateTime.of(2026, 8, 14, 9, 0), result.dateTime)
    }

    @Test
    fun `volgende vrijdag slaat een week over`() {
        val result = parse("volgende vrijdag de factuur versturen")!!
        assertEquals(LocalDateTime.of(2026, 8, 21, 9, 0), result.dateTime)
    }

    @Test
    fun `over twee weken telt vooruit`() {
        val result = parse("over twee weken het paspoort verlengen")!!
        assertEquals(LocalDateTime.of(2026, 8, 26, 9, 0), result.dateTime)
    }

    @Test
    fun `over twee uur noemt een moment`() {
        val result = parse("over twee uur het brood uit de oven halen")!!
        assertEquals(LocalDateTime.of(2026, 8, 12, 12, 0), result.dateTime)
        assertTrue(result.hasTime)
    }

    @Test
    fun `nederlandse maandnaam met ordinaal`() {
        val result = parse("de belasting betalen voor 15 september")!!
        assertEquals(LocalDateTime.of(2026, 9, 15, 9, 0), result.dateTime)
    }

    @Test
    fun `ordinaal met e-suffix werkt`() {
        val result = parse("uiterlijk de 3e maart de ferry boeken")!!
        assertEquals(LocalDateTime.of(2027, 3, 3, 9, 0), result.dateTime)
    }

    @Test
    fun `vanavond is vandaag om zeven uur`() {
        val result = parse("vanavond de vuilnis buiten zetten")!!
        assertEquals(LocalDateTime.of(2026, 8, 12, 19, 0), result.dateTime)
        assertTrue(result.hasTime)
    }

    @Test
    fun `eind van de maand is de laatste dag`() {
        val result = parse("het abonnement opzeggen voor het eind van de maand")!!
        assertEquals(LocalDateTime.of(2026, 8, 31, 9, 0), result.dateTime)
    }

    @Test
    fun `volgende week begint op maandag`() {
        val result = parse("volgende week de auto naar de garage")!!
        assertEquals(LocalDateTime.of(2026, 8, 17, 9, 0), result.dateTime)
    }

    /** A Dutch note on an English phone must still read 15/09 as 15 September. */
    @Test
    fun `numerieke datum is dag voor maand`() {
        val result = parse("de keuring is op 09-10-2026")!!
        assertEquals(LocalDateTime.of(2026, 10, 9, 9, 0), result.dateTime)
    }
}
