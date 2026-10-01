package dev.gabrie.brainwave.mail

import dev.gabrie.brainwave.data.Brainwave
import dev.gabrie.brainwave.settings.AppSettings
import dev.gabrie.brainwave.util.Time
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Turns brainwaves into the kinds of mail the app sends.
 *
 * Every subject starts with the `[brainwave]` tag so a single mail filter can
 * catch all of it.
 */
object MailComposer {

    const val SUBJECT_TAG = "[brainwave]"

    private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)

    fun subjectFor(title: String): String = "$SUBJECT_TAG $title"

    /** The mail sent right after a brainwave is captured, with the audio attached. */
    fun note(brainwave: Brainwave, settings: AppSettings): OutgoingMail {
        val due = Time.formatDue(brainwave.dueAt, brainwave.dueHasTime)
        val dueLine = if (brainwave.dueAt == null) {
            "No due date"
        } else {
            "$due (${Time.formatAbsolute(brainwave.dueAt, brainwave.dueHasTime)})"
        }

        val text = buildString {
            appendLine(brainwave.title)
            appendLine()
            appendLine(brainwave.body)
            appendLine()
            appendLine("Due: $dueLine")
            appendLine("Captured: ${Time.formatAbsolute(brainwave.createdAt)}")
            appendLine()
            appendLine("— Brainwave")
        }

        val html = htmlDocument(
            """
            <h2>${escapeHtml(brainwave.title)}</h2>
            <p style="white-space:pre-wrap">${escapeHtml(brainwave.body)}</p>
            <table role="presentation" style="margin-top:16px;font-size:14px">
              <tr><td style="padding-right:12px;color:#666">Due</td><td><strong>${escapeHtml(dueLine)}</strong></td></tr>
              <tr><td style="padding-right:12px;color:#666">Captured</td><td>${escapeHtml(Time.formatAbsolute(brainwave.createdAt))}</td></tr>
            </table>
            """.trimIndent()
        )

        val audio = brainwave.audioPath?.let(::File)?.takeIf { it.exists() }
        val attachments = if (audio != null) {
            listOf(MailAttachment(audio, "${safeFileName(brainwave.title)}.m4a", "audio/mp4"))
        } else {
            emptyList()
        }

        return OutgoingMail(
            subject = subjectFor(brainwave.title),
            text = text,
            html = html,
            attachments = attachments,
        )
    }

    /** One mail containing the whole list, on demand from the home screen. */
    fun list(brainwaves: List<Brainwave>, today: LocalDate = LocalDate.now()): OutgoingMail {
        val open = brainwaves.filterNot { it.completed }
        val done = brainwaves.filter { it.completed }

        val text = buildString {
            appendLine("Brainwaves — ${today.format(LONG_DATE)}")
            appendLine()
            appendLine("OPEN (${open.size})")
            if (open.isEmpty()) appendLine("  (none)")
            open.forEach { appendLine("  • ${it.title} — ${Time.formatDue(it.dueAt, it.dueHasTime)}") }
            appendLine()
            appendLine("COMPLETED (${done.size})")
            if (done.isEmpty()) appendLine("  (none)")
            done.forEach { appendLine("  ✓ ${it.title} — ${Time.formatDue(it.dueAt, it.dueHasTime)}") }
            appendLine()
            appendLine("— Brainwave")
        }

        val html = htmlDocument(
            buildString {
                append("<h2>Brainwaves</h2>")
                append("<p style=\"color:#666\">${escapeHtml(today.format(LONG_DATE))}</p>")
                append(section("Open", open, false))
                append(section("Completed", done, true))
            }
        )

        return OutgoingMail(
            subject = "$SUBJECT_TAG Brainwave list — ${today.format(LONG_DATE)}",
            text = text,
            html = html,
        )
    }

    private fun section(heading: String, items: List<Brainwave>, completed: Boolean): String {
        if (items.isEmpty()) {
            return "<h3>$heading (0)</h3><p style=\"color:#999\">Nothing here.</p>"
        }
        val rows = items.joinToString("") { item ->
            val overdue = !completed && Time.isOverdue(item.dueAt, item.dueHasTime)
            val dueColor = if (overdue) "#b3261e" else "#666"
            val titleStyle = if (completed) "color:#999;text-decoration:line-through" else ""
            """
            <tr>
              <td style="padding:6px 12px 6px 0;border-bottom:1px solid #eee;$titleStyle">${escapeHtml(item.title)}</td>
              <td style="padding:6px 0;border-bottom:1px solid #eee;color:$dueColor;white-space:nowrap">
                ${escapeHtml(Time.formatDue(item.dueAt, item.dueHasTime))}
              </td>
            </tr>
            """.trimIndent()
        }
        return "<h3>$heading (${items.size})</h3><table style=\"width:100%;border-collapse:collapse;font-size:14px\">$rows</table>"
    }

    private fun htmlDocument(body: String): String = """
        <!doctype html>
        <html><body style="font-family:-apple-system,Roboto,Helvetica,Arial,sans-serif;color:#1c1b1f;max-width:640px">
        $body
        <p style="margin-top:24px;color:#999;font-size:12px">Sent by Brainwave</p>
        </body></html>
    """.trimIndent()

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** Keeps attachment names safe across mail clients and filesystems. */
    fun safeFileName(title: String): String {
        val cleaned = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().replace(' ', '-')
        return cleaned.take(48).ifBlank { "brainwave" }
    }
}
