package dev.gabrie.brainwave

import dev.gabrie.brainwave.ai.DueDateParser
import dev.gabrie.brainwave.settings.NoteLanguage
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DueDateParserTest {

    // Wednesday, 12 August 2026 at 10:00.
    private val now = LocalDateTime.of(2026, 8, 12, 10, 0)

    private fun parse(text: String) =
        DueDateParser.parse(text, now, defaultHour = 9, language = NoteLanguage.ENGLISH)

    @Test
    fun `no date at all returns null`() {
        assertNull(parse("think about the garden fence again"))
    }

    @Test
    fun `tomorrow resolves to the next day at the default hour`() {
        val result = parse("call the plumber tomorrow")!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 9, 0), result.dateTime)
        assertTrue(!result.hasTime)
    }

    @Test
    fun `explicit time is picked up alongside the day`() {
        val result = parse("dentist tomorrow at 15:30")!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 15, 30), result.dateTime)
        assertTrue(result.hasTime)
    }

    @Test
    fun `bare afternoon hour is read as pm`() {
        val result = parse("pick up the kids tomorrow at 5")!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 17, 0), result.dateTime)
    }

    @Test
    fun `on friday means the coming friday`() {
        val result = parse("send the invoice on friday")!!
        assertEquals(LocalDateTime.of(2026, 8, 14, 9, 0), result.dateTime)
    }

    @Test
    fun `next friday skips a week past the coming one`() {
        val result = parse("send the invoice next friday")!!
        assertEquals(LocalDateTime.of(2026, 8, 21, 9, 0), result.dateTime)
    }

    @Test
    fun `in two weeks counts forward from today`() {
        val result = parse("renew the passport in two weeks")!!
        assertEquals(LocalDateTime.of(2026, 8, 26, 9, 0), result.dateTime)
    }

    @Test
    fun `month and day without a year rolls into next year when already past`() {
        val result = parse("book the ferry for March 3rd")!!
        assertEquals(LocalDateTime.of(2027, 3, 3, 9, 0), result.dateTime)
    }

    @Test
    fun `day of month before month name is understood`() {
        val result = parse("pay the tax by the 15th of September")!!
        assertEquals(LocalDateTime.of(2026, 9, 15, 9, 0), result.dateTime)
    }

    @Test
    fun `a numeric date is preferred over a stray number earlier in the text`() {
        val result = parse("call 5 people about the roof by 15/09/2026")!!
        assertEquals(LocalDateTime.of(2026, 9, 15, 9, 0), result.dateTime)
    }

    @Test
    fun `a bare time with no date means today when it is still ahead`() {
        val result = parse("stand-up at 16:00")!!
        assertEquals(LocalDateTime.of(2026, 8, 12, 16, 0), result.dateTime)
        assertTrue(result.hasTime)
    }

    @Test
    fun `a bare time that already passed rolls to tomorrow`() {
        val result = parse("stand-up at 08:00")!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 8, 0), result.dateTime)
    }

    @Test
    fun `tonight resolves to this evening`() {
        val result = parse("take the bins out tonight")!!
        assertEquals(LocalDateTime.of(2026, 8, 12, 19, 0), result.dateTime)
        assertTrue(result.hasTime)
    }

    @Test
    fun `a dotted numeric date is not mistaken for a clock time`() {
        val result = parse("renew the insurance by 15.09.2026")!!
        assertEquals(LocalDateTime.of(2026, 9, 15, 9, 0), result.dateTime)
        assertTrue(!result.hasTime)
    }

    @Test
    fun `in two hours names a moment rather than a day`() {
        val result = parse("take the bread out in two hours")!!
        assertEquals(LocalDateTime.of(2026, 8, 12, 12, 0), result.dateTime)
        assertTrue(result.hasTime)
    }

    @Test
    fun `end of the month resolves to the last day`() {
        val result = parse("cancel the subscription by the end of the month")!!
        assertEquals(LocalDateTime.of(2026, 8, 31, 9, 0), result.dateTime)
    }
}
