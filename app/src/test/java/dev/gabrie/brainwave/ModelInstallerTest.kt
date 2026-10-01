package dev.gabrie.brainwave

import dev.gabrie.brainwave.ai.ModelInstaller
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelInstallerTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun archive(name: String = "model.zip") = File(tmp.root, name)
    private fun destination() = File(tmp.root, "vosk/nl")

    private fun assertRefused(block: () -> Unit): ModelInstaller.InstallException {
        try {
            block()
        } catch (e: ModelInstaller.InstallException) {
            return e
        }
        fail("expected the install to be refused")
        throw AssertionError()
    }

    @Test fun `a valid archive is unpacked without its top-level folder`() {
        val zip = TestZips.valid(archive())
        ModelInstaller().install(zip, TestZips.modelFor(zip), destination())

        assertEquals("acoustic-model", File(destination(), "am/final.mdl").readText())
        assertTrue(File(destination(), "conf/model.conf").isFile)
        assertFalse("the top-level folder must be stripped", File(destination(), TestZips.TOP).exists())
    }

    @Test fun `the checksum is recorded so a later catalogue update can spot a stale copy`() {
        val zip = TestZips.valid(archive())
        val model = TestZips.modelFor(zip)
        ModelInstaller().install(zip, model, destination())
        assertEquals(model.sha256, File(destination(), ModelInstaller.MARKER).readText())
    }

    @Test fun `a wrong checksum is refused before anything is unpacked`() {
        val zip = TestZips.valid(archive())
        val tampered = TestZips.modelFor(zip).copy(sha256 = "0".repeat(64))

        val error = assertRefused { ModelInstaller().install(zip, tampered, destination()) }

        assertTrue(error.message, error.message!!.contains("checksum"))
        assertFalse("nothing may be installed", destination().exists())
        assertFalse("no staging debris", File(tmp.root, "vosk/nl.partial").exists())
    }

    /** The classic attack: an entry whose name climbs out of the target folder. */
    @Test fun `a zip-slip entry is refused and nothing is written outside the target`() {
        val zip = TestZips.build(
            archive(),
            "${TestZips.TOP}/am/final.mdl" to "x".toByteArray(),
            "${TestZips.TOP}/../../escaped.txt" to "pwned".toByteArray(),
        )
        assertRefused { ModelInstaller().install(zip, TestZips.modelFor(zip), destination()) }

        assertFalse(File(tmp.root, "escaped.txt").exists())
        assertFalse(File(tmp.root.parentFile, "escaped.txt").exists())
        assertFalse(destination().exists())
    }

    @Test fun `an entry outside the expected top-level folder is refused`() {
        val zip = TestZips.build(
            archive(),
            "${TestZips.TOP}/am/final.mdl" to "x".toByteArray(),
            "somewhere-else/payload.bin" to "x".toByteArray(),
        )
        assertRefused { ModelInstaller().install(zip, TestZips.modelFor(zip), destination()) }
        assertFalse(destination().exists())
    }

    @Test fun `an absolute path entry is refused`() {
        val zip = TestZips.build(archive(), "/etc/evil" to "x".toByteArray())
        assertRefused { ModelInstaller().install(zip, TestZips.modelFor(zip), destination()) }
    }

    @Test fun `an archive without the acoustic model is refused as incomplete`() {
        val zip = TestZips.build(archive(), "${TestZips.TOP}/conf/model.conf" to "x".toByteArray())
        val error = assertRefused { ModelInstaller().install(zip, TestZips.modelFor(zip), destination()) }
        assertTrue(error.message, error.message!!.contains("incomplete"))
        assertFalse(destination().exists())
    }

    @Test fun `an archive that inflates past the cap is refused`() {
        // 1 MB of zeros deflates to a few KB: small on the wire, large unpacked.
        val zip = TestZips.build(
            archive(),
            "${TestZips.TOP}/am/final.mdl" to ByteArray(1_000_000),
        )
        assertRefused { ModelInstaller(maxUncompressedBytes = 100_000).install(zip, TestZips.modelFor(zip), destination()) }
        assertFalse(destination().exists())
        assertFalse(File(tmp.root, "vosk/nl.partial").exists())
    }

    @Test fun `too many entries are refused`() {
        val many = (1..30).map { "${TestZips.TOP}/f$it" to "x".toByteArray() }.toTypedArray()
        val zip = TestZips.build(archive(), "${TestZips.TOP}/am/final.mdl" to "x".toByteArray(), *many)
        assertRefused { ModelInstaller(maxEntries = 10).install(zip, TestZips.modelFor(zip), destination()) }
    }

    @Test fun `an empty archive is refused`() {
        val zip = TestZips.build(archive())
        assertRefused { ModelInstaller().install(zip, TestZips.modelFor(zip), destination()) }
    }

    @Test fun `a failed update leaves a working installed model untouched`() {
        val good = TestZips.valid(archive("good.zip"))
        ModelInstaller().install(good, TestZips.modelFor(good), destination())

        val bad = TestZips.build(archive("bad.zip"), "${TestZips.TOP}/conf/model.conf" to "x".toByteArray())
        assertRefused { ModelInstaller().install(bad, TestZips.modelFor(bad), destination()) }

        assertEquals("acoustic-model", File(destination(), "am/final.mdl").readText())
    }

    @Test fun `a successful update replaces the old copy completely`() {
        val first = TestZips.valid(archive("v1.zip"), extra = mapOf("only-in-v1.txt" to "old"))
        ModelInstaller().install(first, TestZips.modelFor(first), destination())
        assertTrue(File(destination(), "only-in-v1.txt").exists())

        val second = TestZips.valid(archive("v2.zip"), extra = mapOf("only-in-v2.txt" to "new"))
        ModelInstaller().install(second, TestZips.modelFor(second), destination())

        assertFalse("the old copy must not linger", File(destination(), "only-in-v1.txt").exists())
        assertTrue(File(destination(), "only-in-v2.txt").exists())
        assertFalse(File(tmp.root, "vosk/nl.old").exists())
    }
}
