package dev.gabrie.brainwave

import dev.gabrie.brainwave.mail.AttachmentStaging
import dev.gabrie.brainwave.mail.MailAttachment
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentStagingTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun recording(content: String = "audio-bytes"): File =
        File(tmp.root, "brainwave-1790946000000.m4a").apply { writeText(content) }

    private fun exports() = File(tmp.root, "cache/exports")

    @Test fun `the recording is shared as a copy under its friendly name`() {
        val original = recording()
        val staged = AttachmentStaging.stage(exports(), MailAttachment(original, "Call-the-plumber.m4a", "audio/mp4"))

        assertEquals("Call-the-plumber.m4a", staged.name)
        assertEquals("audio-bytes", staged.readText())
        assertTrue("the original stays where it is", original.exists())
        assertFalse("a copy, not the same file", staged.canonicalPath == original.canonicalPath)
    }

    @Test fun `a second brainwave with the same title replaces the first copy`() {
        val att = MailAttachment(recording("one"), "Same.m4a", "audio/mp4")
        AttachmentStaging.stage(exports(), att)
        val second = AttachmentStaging.stage(exports(), att.copy(file = recording("two")))
        assertEquals("two", second.readText())
        assertEquals(1, exports().listFiles()!!.size)
    }

    @Test fun `copies older than a day are tidied away, fresh ones are kept`() {
        val now = 500_000_000L                       // well past one day (86_400_000 ms) from epoch
        exports().mkdirs()
        val old = File(exports(), "old.m4a").apply { writeText("x"); setLastModified(now - 2 * 86_400_000L) }
        val fresh = File(exports(), "fresh.m4a").apply { writeText("x"); setLastModified(now - 60_000L) }

        AttachmentStaging.stage(exports(), MailAttachment(recording(), "new.m4a", "audio/mp4"), now = now)

        assertFalse("two days old: gone", old.exists())
        assertTrue("a minute old: kept", fresh.exists())
        assertTrue(File(exports(), "new.m4a").exists())
    }

    /** The staged name comes from a brainwave title, i.e. from speech or typing. */
    @Test fun `a hostile name cannot escape the share folder`() {
        val staged = AttachmentStaging.stage(
            exports(),
            MailAttachment(recording(), "../../../etc/evil.m4a", "audio/mp4"),
        )
        assertTrue(staged.canonicalPath.startsWith(exports().canonicalPath + File.separator))
        assertEquals("evil.m4a", staged.name)
    }

    @Test fun `safeName strips directories, odd characters and leading dots`() {
        assertEquals("evil.m4a", AttachmentStaging.safeName("a/b/../evil.m4a"))
        assertEquals("evil.m4a", AttachmentStaging.safeName("C:\\temp\\evil.m4a"))
        assertEquals("note__.m4a", AttachmentStaging.safeName("note<>.m4a"))
        assertEquals("hidden.m4a", AttachmentStaging.safeName(".hidden.m4a"))
    }

    @Test fun `accented letters survive, symbols do not`() {
        assertEquals("Café plan.m4a", AttachmentStaging.safeName("Café plan.m4a"))
        assertEquals("Café _ plan.m4a", AttachmentStaging.safeName("Café ☕ plan.m4a"))
    }

    @Test fun `an empty name falls back to a sensible one`() {
        assertEquals("brainwave.m4a", AttachmentStaging.safeName(""))
        assertEquals("brainwave.m4a", AttachmentStaging.safeName("///"))
        assertEquals("brainwave.m4a", AttachmentStaging.safeName("..."))
    }
}
