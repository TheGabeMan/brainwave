package dev.gabrie.brainwave

import dev.gabrie.brainwave.ai.DueDateParser
import dev.gabrie.brainwave.ai.TitleGenerator
import dev.gabrie.brainwave.settings.NoteLanguage
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses what the speech model actually *writes*, not what a person would type.
 *
 * Vosk emits lower-case words with no punctuation and never digits — "half
 * drie", "vijftien oktober", "three thirty" — so a parser tested only on typed
 * input ("14:30", "15 october") passes every test and still fails on a phone.
 * The transcripts below are real recognizer output: TTS audio run through the
 * exact models the app bundles, plus recordings made on the device itself.
 */
class SpokenTranscriptTest {

    // Wednesday, 12 August 2026, 10:00.
    private val now = LocalDateTime.of(2026, 8, 12, 10, 0)

    private fun nl(text: String) = DueDateParser.parse(text, now, 9, NoteLanguage.DUTCH)
    private fun en(text: String) = DueDateParser.parse(text, now, 9, NoteLanguage.ENGLISH)

    private fun assertDue(expected: LocalDateTime, hasTime: Boolean, actual: dev.gabrie.brainwave.ai.ParsedDue?) {
        assertNotNull("no date was found", actual)
        assertEquals(expected, actual!!.dateTime)
        assertEquals("hasTime", hasTime, actual.hasTime)
    }

    // ---------------------------------------------------------------- Dutch --

    @Test fun `half drie is half three, meaning 14-30`() =
        assertDue(LocalDateTime.of(2026, 8, 13, 14, 30), true, nl("morgen om half drie de planten water geven"))

    @Test fun `half tien in the morning is 9-30`() =
        assertDue(LocalDateTime.of(2026, 8, 15, 9, 30), true, nl("zaterdag om half tien de auto wassen"))

    @Test fun `kwart over vier is 16-15`() =
        assertDue(LocalDateTime.of(2026, 8, 12, 16, 15), true, nl("vanmiddag om kwart over vier boodschappen doen"))

    @Test fun `tien voor acht is 7-50, and eight says it is morning`() =
        assertDue(LocalDateTime.of(2026, 8, 17, 7, 50), true, nl("maandag om tien voor acht de trein nemen"))

    @Test fun `spelled out hour with uur`() =
        assertDue(LocalDateTime.of(2026, 8, 12, 16, 0), true, nl("vanmiddag om vier uur jan bellen"))

    @Test fun `twaalf uur is noon`() =
        assertDue(LocalDateTime.of(2026, 8, 12, 12, 0), true, nl("vandaag om twaalf uur de post ophalen"))

    @Test fun `s middags makes a bare five into 17-00`() =
        assertDue(LocalDateTime.of(2026, 8, 18, 17, 0), true, nl("dinsdag om vijf uur 's middags naar de kapper"))

    @Test fun `volgende week vrijdag is the friday of next week`() =
        assertDue(LocalDateTime.of(2026, 8, 21, 9, 0), false, nl("volgende week vrijdag de facturen versturen"))

    @Test fun `spelled out day of month`() =
        assertDue(LocalDateTime.of(2026, 10, 15, 9, 0), false, nl("op vijftien oktober de verzekering verlengen"))

    @Test fun `accented een is the number one`() =
        assertDue(LocalDateTime.of(2026, 11, 1, 9, 0), false, nl("één november de belasting betalen"))

    @Test fun `compound number with a diaeresis`() =
        assertDue(LocalDateTime.of(2026, 12, 23, 9, 0), false, nl("op drieëntwintig december de cadeaus kopen"))

    @Test fun `ordinal day of month`() =
        assertDue(LocalDateTime.of(2026, 11, 1, 9, 0), false, nl("eerste november de belasting betalen"))

    @Test fun `over twee weken`() =
        assertDue(LocalDateTime.of(2026, 8, 26, 9, 0), false, nl("over twee weken het paspoort verlengen"))

    @Test fun `over vijftien minuten names a moment`() =
        assertDue(LocalDateTime.of(2026, 8, 12, 10, 15), true, nl("over vijftien minuten de oven uitzetten"))

    @Test fun `vanmorgen is this morning`() =
        assertDue(LocalDateTime.of(2026, 8, 12, 9, 0), true, nl("vanmorgen om negen uur de tandarts bellen"))

    @Test fun `eind van de maand`() =
        assertDue(LocalDateTime.of(2026, 8, 31, 9, 0), false, nl("voor het eind van de maand huur betalen"))

    @Test fun `volgende maand`() =
        assertDue(LocalDateTime.of(2026, 9, 1, 9, 0), false, nl("volgende maand de auto laten keuren"))

    // --- recordings made on the phone, which previously had to be fixed by hand

    @Test fun `morgenvroeg om zeven uur is 07-00, not 19-00`() =
        assertDue(
            LocalDateTime.of(2026, 8, 13, 7, 0), true,
            nl("morgenvroeg om zeven uur meteen de te krijgen bij de racefiets poetsen"),
        )

    @Test fun `vrijdag middag om twee uur`() =
        assertDue(
            LocalDateTime.of(2026, 8, 14, 14, 0), true,
            nl("vrijdag middag om twee uur de sleutel ophalen op de rijksweg"),
        )

    @Test fun `vanavond is 19-00`() =
        assertDue(
            LocalDateTime.of(2026, 8, 12, 19, 0), true,
            nl("ik moet aan denken dan vanavond nog even de was te doen aan de stoelen terug te zetten"),
        )

    @Test fun `morgen vroeg is tomorrow early`() =
        assertDue(
            LocalDateTime.of(2026, 8, 13, 7, 0), true,
            nl("morgen vroeg even boodschappen doen bij de een hier aan de beurt"),
        )

    // -------------------------------------------------------------- English --

    @Test fun `three thirty`() =
        assertDue(LocalDateTime.of(2026, 8, 13, 15, 30), true, en("call the plumber tomorrow at three thirty"))

    @Test fun `five pm next friday`() =
        assertDue(LocalDateTime.of(2026, 8, 21, 17, 0), true, en("send the invoice next friday at five pm"))

    @Test fun `march third`() =
        assertDue(LocalDateTime.of(2027, 3, 3, 9, 0), false, en("pay the tax bill by march third"))

    @Test fun `the fifteenth of october`() =
        assertDue(LocalDateTime.of(2026, 10, 15, 9, 0), false, en("look the very on the fifteenth of october"))

    @Test fun `twenty first of november`() =
        assertDue(LocalDateTime.of(2026, 11, 21, 9, 0), false, en("water the plants on twenty first of november"))

    @Test fun `half past two`() =
        assertDue(LocalDateTime.of(2026, 8, 12, 14, 30), true, en("the meeting at half past two"))

    @Test fun `quarter to five`() =
        assertDue(LocalDateTime.of(2026, 8, 13, 16, 45), true, en("dentist tomorrow and quarter to five"))

    @Test fun `tonight at nine is 21-00`() =
        assertDue(LocalDateTime.of(2026, 8, 12, 21, 0), true, en("take the bins out tonight at nine"))

    @Test fun `in two weeks`() =
        assertDue(LocalDateTime.of(2026, 8, 26, 9, 0), false, en("the new the passport in two weeks"))

    @Test fun `friday afternoon with no time is 14-00`() =
        assertDue(LocalDateTime.of(2026, 8, 14, 14, 0), true, en("pick up the keys friday afternoon at to"))

    // ------------------------------------------- things that must NOT match --

    @Test fun `ordinary words are not weekdays`() {
        assertNull(en("the sun is out and he sat down"))
        assertNull(nl("zo snel mogelijk de was doen"))
    }

    @Test fun `a day part alone is not a deadline`() {
        assertNull(en("morning routine ideas"))
        assertNull(nl("ochtend ritueel bedenken"))
    }

    @Test fun `tegen vijf mensen is not 17-00`() = assertNull(nl("ik wil tegen vijf mensen praten"))

    @Test fun `a spoken second is not a day of the month`() =
        assertNull(en("the second thing i need is milk"))

    @Test fun `tegen een datum is a deadline not a time`() =
        assertDue(LocalDateTime.of(2026, 10, 15, 9, 0), false, nl("tegen vijftien oktober de belasting betalen"))

    // ------------------------------------------------------------- titles --

    private fun title(text: String, language: NoteLanguage): String {
        val parsed = DueDateParser.parse(text, now, 9, language)
        return TitleGenerator.generate(text, parsed?.matchedSpans.orEmpty(), language)
    }

    @Test fun `the date and the time both leave the title, even when not adjacent`() =
        assertEquals(
            "De sleutel ophalen op de rijksweg",
            title("vrijdag middag om twee uur de sleutel ophalen op de rijksweg", NoteLanguage.DUTCH),
        )

    @Test fun `half drie leaves the title`() =
        assertEquals(
            "De planten water geven",
            title("morgen om half drie de planten water geven", NoteLanguage.DUTCH),
        )

    @Test fun `spelled out date leaves the title`() =
        assertEquals(
            "De verzekering verlengen",
            title("op vijftien oktober de verzekering verlengen", NoteLanguage.DUTCH),
        )

    @Test fun `english spoken time leaves the title`() =
        assertEquals(
            "Call the plumber",
            title("call the plumber tomorrow at three thirty", NoteLanguage.ENGLISH),
        )

    @Test fun `a title is still made when there is no date`() {
        val t = title("nog eens nadenken over de schutting", NoteLanguage.DUTCH)
        assertTrue(t, t.startsWith("Nog eens nadenken"))
    }
}
