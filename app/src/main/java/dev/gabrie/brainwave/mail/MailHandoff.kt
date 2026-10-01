package dev.gabrie.brainwave.mail

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a message to the phone's mail app instead of sending it over SMTP.
 *
 * Android deliberately offers no way to send mail through another app without
 * the user looking at it, so this opens the mail app with the recipient,
 * subject, text and recording filled in, and the user taps Send. It is the
 * zero-setup alternative to SMTP: no password, no server details.
 *
 * An intent selector (`ACTION_SENDTO mailto:`) restricts it to email apps — a MIME
 * type cannot, see [buildIntent] — and the recording travels as a `content://` URI
 * through [FileProvider] rather than a file path, which other apps cannot read.
 */
object MailHandoff {

    data class Request(val mail: OutgoingMail, val recipient: String)

    fun buildIntent(context: Context, request: Request): Intent {
        val mail = request.mail
        val send = Intent(Intent.ACTION_SEND).apply {
            // The selector is what restricts the target to *email* apps. A type of
            // message/rfc822 alone does not: Signal, WhatsApp, Telegram and Quick
            // Share all accept any file type, so they would appear in the list
            // and a private note could be sent to a chat contact by mistake.
            // ACTION_SEND carries the attachment; the selector makes Android
            // resolve it as ACTION_SENDTO mailto:, which only mail apps handle.
            //
            // No `type` on this intent. Combined with a selector it makes the
            // intent unresolvable (START_INTENT_NOT_RESOLVED, observed on a
            // device): the selector is the whole of how this is matched.
            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            putExtra(Intent.EXTRA_EMAIL, arrayOf(request.recipient))
            putExtra(Intent.EXTRA_SUBJECT, mail.subject)
            putExtra(Intent.EXTRA_TEXT, mail.text)

            mail.attachments.firstOrNull()?.let { attachment ->
                val staged = AttachmentStaging.stage(File(context.cacheDir, EXPORTS_DIR), attachment)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", staged)
                putExtra(Intent.EXTRA_STREAM, uri)
                // ClipData + the grant flag is what makes the receiving app allowed to read the URI.
                clipData = ClipData.newRawUri(staged.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return send
    }

    /** Opens the mail app. Returns false when the phone has none. */
    fun launch(context: Context, request: Request): Boolean = try {
        val intent = buildIntent(context, request)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    /** Must match the `cache-path` in `res/xml/file_paths.xml`. */
    private const val EXPORTS_DIR = "exports"
}
