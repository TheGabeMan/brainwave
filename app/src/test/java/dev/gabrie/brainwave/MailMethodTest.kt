package dev.gabrie.brainwave

import dev.gabrie.brainwave.settings.AppSettings
import dev.gabrie.brainwave.settings.MailMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailMethodTest {

    private val smtpReady = AppSettings(
        recipientEmail = "me@example.com",
        smtpHost = "smtp.example.com",
        smtpUsername = "me@example.com",
    )

    /** The owner's explicit requirement: the mail app is a choice, SMTP stays the default. */
    @Test fun `SMTP is the default method`() {
        assertEquals(MailMethod.SMTP, AppSettings().mailMethod)
    }

    @Test fun `with SMTP configured, mail is sent automatically and never handed off`() {
        val s = smtpReady
        assertTrue(s.sendsAutomatically)
        assertFalse(s.handsOffToMailApp)
        assertTrue(s.canEmail)
    }

    @Test fun `SMTP chosen but not configured means no email at all, not a silent fallback`() {
        val s = AppSettings(recipientEmail = "me@example.com")
        assertFalse(s.sendsAutomatically)
        assertFalse("must not quietly use the mail app", s.handsOffToMailApp)
        assertFalse(s.canEmail)
    }

    @Test fun `the mail app needs only a recipient, no server details`() {
        val s = AppSettings(mailMethod = MailMethod.MAIL_APP, recipientEmail = "me@example.com")
        assertTrue(s.handsOffToMailApp)
        assertTrue(s.canEmail)
        assertFalse("nothing is sent in the background", s.sendsAutomatically)
    }

    @Test fun `the mail app without a recipient cannot email`() {
        val s = AppSettings(mailMethod = MailMethod.MAIL_APP)
        assertFalse(s.handsOffToMailApp)
        assertFalse(s.canEmail)
    }

    /** Switching method must not let complete SMTP details send behind the user's back. */
    @Test fun `complete SMTP details are ignored once the mail app is chosen`() {
        val s = smtpReady.copy(mailMethod = MailMethod.MAIL_APP)
        assertFalse(s.sendsAutomatically)
        assertTrue(s.handsOffToMailApp)
    }
}
