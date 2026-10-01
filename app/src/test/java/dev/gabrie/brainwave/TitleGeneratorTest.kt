package dev.gabrie.brainwave

import dev.gabrie.brainwave.ai.TitleGenerator
import dev.gabrie.brainwave.settings.NoteLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleGeneratorTest {

    @Test
    fun `spoken filler is stripped from the front`() {
        assertEquals(
            "Book the ferry tickets",
            TitleGenerator.generate("Um, note to self, book the ferry tickets."),
        )
    }

    @Test
    fun `only the first sentence becomes the title`() {
        assertEquals(
            "Call the roofer about the leak",
            TitleGenerator.generate("Call the roofer about the leak. He said Tuesday works best."),
        )
    }

    @Test
    fun `the matched date phrase is removed`() {
        val title = TitleGenerator.generate("Pay the tax bill by next friday", "next friday")
        assertEquals("Pay the tax bill", title)
    }

    @Test
    fun `long titles are cut at a word boundary`() {
        val title = TitleGenerator.generate(
            "Reorganise the entire garage including the shelving units and the bicycle rack"
        )
        assertTrue(title.length <= 65)
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun `empty text falls back to a dated title`() {
        assertTrue(TitleGenerator.generate("   ").startsWith("Brainwave "))
    }
}
