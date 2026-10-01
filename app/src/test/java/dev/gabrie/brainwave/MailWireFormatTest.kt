package dev.gabrie.brainwave

import dev.gabrie.brainwave.data.Brainwave
import dev.gabrie.brainwave.mail.JavaMailInit
import dev.gabrie.brainwave.mail.MailComposer
import dev.gabrie.brainwave.mail.SmtpMailer
import dev.gabrie.brainwave.settings.AppSettings
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Properties
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.MimeMessage
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serialises the messages exactly as they would go over the wire.
 *
 * Building a MimeMessage succeeds even when a part has no content handler; the
 * failure only surfaces when the message is written out, which is when
 * Transport.send runs. So these tests call writeTo() — constructing the message
 * alone proves nothing.
 */
class MailWireFormatTest {

    private val settings = AppSettings(
        recipientEmail = "me@example.com",
        smtpHost = "smtp.example.com",
        smtpUsername = "me@example.com",
    )

    private val brainwave = Brainwave(
        id = 1,
        title = "Planten water geven",
        body = "morgen om half drie planten water geven",
        dueAt = LocalDateTime.of(2026, 10, 2, 14, 30).atZone(ZoneId.of("Europe/Brussels")).toInstant().toEpochMilli(),
        dueHasTime = true,
    )

    private fun build(mail: dev.gabrie.brainwave.mail.OutgoingMail): MimeMessage {
        JavaMailInit.ensure()
        return SmtpMailer().buildMessage(Session.getInstance(Properties()), mail, settings)
    }

    private fun wire(mail: dev.gabrie.brainwave.mail.OutgoingMail): String {
        val out = ByteArrayOutputStream()
        build(mail).writeTo(out)
        return out.toString(Charsets.UTF_8)
    }

    /** All the readable text in a message, decoded the way a mail client would. */
    private fun textOf(part: Part): String = when {
        part.isMimeType("multipart/*") -> {
            val multipart = part.content as Multipart
            (0 until multipart.count).joinToString("\n") { textOf(multipart.getBodyPart(it)) }
        }
        part.isMimeType("text/*") -> part.content as String
        else -> ""
    }

    @Test
    fun `the note mail serialises with the required subject tag`() {
        val raw = wire(MailComposer.note(brainwave, settings))
        assertTrue(raw.contains("Subject: [brainwave] Planten water geven"))
        assertTrue(raw.contains("morgen om half drie planten water geven"))
    }

    @Test
    fun `the whole-list mail serialises`() {
        val mail = MailComposer.list(listOf(brainwave))
        wire(mail) // must not throw
        val message = build(mail)
        // The subject has an em-dash, so on the wire it is RFC 2047-encoded;
        // decode it as a client would rather than searching the raw bytes.
        assertTrue(message.subject, message.subject.startsWith("[brainwave] Brainwave list"))
        assertTrue(textOf(message).contains("Planten water geven"))
    }

    /** The calendar is written directly now; no mail should carry calendar data. */
    @Test
    fun `no mail carries an iCalendar payload`() {
        for (mail in listOf(MailComposer.note(brainwave, settings), MailComposer.list(listOf(brainwave)))) {
            val raw = wire(mail)
            assertTrue("unexpected VCALENDAR", !raw.contains("BEGIN:VCALENDAR"))
        }
    }
}
