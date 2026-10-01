package dev.gabrie.brainwave.mail

import dev.gabrie.brainwave.settings.AppSettings
import dev.gabrie.brainwave.settings.SmtpSecurity
import java.io.IOException
import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends mail directly over SMTP using the account configured in Settings.
 *
 * Direct SMTP (rather than handing off to a mail app) is what makes the flow
 * automatic: capturing a brainwave sends the note with no further taps. The password lives in the Keystore-backed
 * [dev.gabrie.brainwave.settings.SecretStore], never in plain preferences.
 */
class SmtpMailer {

    class MailException(message: String, cause: Throwable? = null) : IOException(message, cause)

    suspend fun send(mail: OutgoingMail, settings: AppSettings, password: String?) =
        withContext(Dispatchers.IO) {
            if (!settings.mailConfigured) {
                throw MailException("Mail is not configured. Add SMTP details and a recipient in Settings.")
            }
            JavaMailInit.ensure()

            val session = session(settings, password)
            val message = buildMessage(session, mail, settings)

            try {
                Transport.send(message)
            } catch (e: Exception) {
                throw MailException(e.message ?: "SMTP send failed", e)
            }
        }

    internal fun session(settings: AppSettings, password: String?): Session {
        val props = Properties().apply {
            put("mail.smtp.host", settings.smtpHost)
            put("mail.smtp.port", settings.smtpPort.toString())
            put("mail.smtp.connectiontimeout", "20000")
            put("mail.smtp.timeout", "60000")
            put("mail.smtp.writetimeout", "60000")
            // Some providers advertise TLSv1 only; pin the versions that are safe.
            put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")

            when (settings.smtpSecurity) {
                SmtpSecurity.STARTTLS -> {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                }
                SmtpSecurity.SSL -> {
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.socketFactory.port", settings.smtpPort.toString())
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                }
                SmtpSecurity.NONE -> Unit
            }

            val authenticate = settings.smtpUsername.isNotBlank() && !password.isNullOrEmpty()
            put("mail.smtp.auth", authenticate.toString())
        }

        val authenticator = if (settings.smtpUsername.isNotBlank() && !password.isNullOrEmpty()) {
            object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(settings.smtpUsername, password)
            }
        } else {
            null
        }

        return Session.getInstance(props, authenticator)
    }

    internal fun buildMessage(session: Session, mail: OutgoingMail, settings: AppSettings): MimeMessage {
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(settings.effectiveFrom, settings.fromName, "UTF-8"))
        message.setRecipients(
            Message.RecipientType.TO,
            InternetAddress.parse(settings.recipientEmail, false),
        )
        message.setSubject(mail.subject, "UTF-8")
        message.sentDate = Date()
        message.setHeader("X-Brainwave", "1")

        // multipart/alternative holds the renderings of the same content;
        // multipart/mixed wraps that plus any real attachments.
        val alternative = MimeMultipart("alternative")
        alternative.addBodyPart(MimeBodyPart().apply { setText(mail.text, "UTF-8") })

        mail.html?.let { html ->
            alternative.addBodyPart(MimeBodyPart().apply { setContent(html, "text/html; charset=UTF-8") })
        }

        val mixed = MimeMultipart("mixed")
        mixed.addBodyPart(MimeBodyPart().apply { setContent(alternative) })

        mail.attachments.forEach { attachment ->
            mixed.addBodyPart(
                MimeBodyPart().apply {
                    attachFile(attachment.file, attachment.mimeType, "base64")
                    setFileName(attachment.fileName)
                }
            )
        }

        message.setContent(mixed)
        message.saveChanges()
        return message
    }
}
