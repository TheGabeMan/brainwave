package dev.gabrie.brainwave.mail

import java.io.File

data class MailAttachment(
    val file: File,
    val fileName: String,
    val mimeType: String,
)

/**
 * A message ready to hand to [SmtpMailer]. Keeping this transport-agnostic is
 * what lets the composer be unit-testable and the mailer dumb.
 */
data class OutgoingMail(
    val subject: String,
    val text: String,
    val html: String? = null,
    val attachments: List<MailAttachment> = emptyList(),
)
