package dev.gabrie.brainwave

import dev.gabrie.brainwave.ai.TitleGenerator
import dev.gabrie.brainwave.settings.NoteLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class DutchTitleGeneratorTest {

    private fun generate(body: String, due: String? = null) =
        TitleGenerator.generate(body, due, NoteLanguage.DUTCH)

    @Test
    fun `nederlandse stopwoorden vallen weg`() {
        assertEquals("De ferry boeken", generate("eh, denk eraan om de ferry boeken"))
    }

    @Test
    fun `vergeet niet wordt gestript`() {
        assertEquals("De vuilnis buiten zetten", generate("vergeet niet de vuilnis buiten zetten"))
    }

    @Test
    fun `de datumzin verdwijnt uit de titel`() {
        assertEquals("De factuur versturen", generate("de factuur versturen voor volgende vrijdag", "volgende vrijdag"))
    }
}
