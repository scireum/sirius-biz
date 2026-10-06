/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.function.Predicate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the [ArchiveExtractor], both for ZIP files (read via the Java APIs) and for all other formats, which are
 * read using the native 7-Zip libraries.
 *
 * The test archives in `src/test/resources/test-data/archives` contain non-ASCII file names, directories, a hidden
 * file and macOS metadata, so that the filtering and the file name decoding are covered as well.
 */
@ExtendWith(SiriusExtension::class)
class ArchiveExtractorTest {

    @Test
    fun `7-Zip native libraries are loaded and enable all archive formats`() {
        // ArchiveExtractor silently falls back to "ZIP only" if the native libraries cannot be loaded. Asserting
        // the extensions provided by 7-Zip ensures that a broken 7-Zip-JBinding upgrade fails the build instead.
        assertTrue(archiveExtractor.isArchiveFile("zip"))
        assertTrue(archiveExtractor.isArchiveFile("7z"))
        assertTrue(archiveExtractor.isArchiveFile("rar"))
        assertTrue(archiveExtractor.isArchiveFile("tar"))
    }

    @Test
    fun `ZIP with UTF-8 file names is extracted without hidden files and metadata`() {
        val files = extract("sample-utf8.zip")

        assertEquals(setOf("readme.txt", "docs/Grüße.txt"), files.keys)
        assertEquals("Hello ZIP", files["readme.txt"])
        assertEquals("Grüße aus Stuttgart", files["docs/Grüße.txt"])
    }

    @Test
    fun `legacy Windows ZIP falls back to ISO-8859-1 file names`() {
        // Windows used to store ZIP entry names as CP437 without setting the UTF-8 flag. The Java APIs reject these
        // names as invalid UTF-8, so ArchiveExtractor retries using ISO-8859-1. This keeps all characters, but
        // decodes non-ASCII ones incorrectly ("ü" is 0x81 and "ß" is 0xE1 in CP437). This test documents that
        // behavior. Note that 7-Zip 23.01 would even drop these characters entirely, which is why ZIP files must
        // not be routed through 7-Zip.
        val files = extract("sample-cp437.zip")

        val expectedName = String("Grüße.txt".toByteArray(charset("IBM437")), StandardCharsets.ISO_8859_1)
        assertEquals(setOf(expectedName), files.keys)
        assertEquals("Windows", files[expectedName])
    }

    @Test
    fun `7z archive is extracted without directories, hidden files and metadata`() {
        val files = extract("sample.7z")

        assertEquals(setOf("readme.txt", "docs/Grüße.txt", "docs/data.csv"), files.keys)
        assertEquals("Hello 7-Zip", files["readme.txt"])
        assertEquals("Grüße aus Stuttgart", files["docs/Grüße.txt"])
        assertEquals("a;b\n1;2", files["docs/data.csv"])
    }

    @Test
    fun `7z archive provides the last modification timestamp of its entries`() {
        val timestamps = HashMap<String, LocalDateTime>()
        archiveExtractor.extractAll("sample.7z", archive("sample.7z"), null) { file ->
            timestamps[file.filePath] = file.lastModified()
        }

        val expected = LocalDateTime.ofInstant(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.systemDefault())
        assertEquals(expected, timestamps["readme.txt"])
    }

    @Test
    fun `7z archive only yields entries accepted by the filter`() {
        val files = extract("sample.7z") { path -> path.endsWith(".csv") }

        assertEquals(setOf("docs/data.csv"), files.keys)
    }

    @Test
    fun `TAR archive is extracted using 7-Zip`() {
        val files = extract("sample.tar")

        assertEquals(setOf("readme.txt", "docs/Grüße.txt"), files.keys)
        assertEquals("Grüße aus Stuttgart", files["docs/Grüße.txt"])
    }

    @Test
    fun `extraction stops as soon as the consumer returns false`() {
        var filesSeen = 0
        archiveExtractor.extract("sample.7z", archive("sample.7z"), null) {
            filesSeen++
            false
        }

        assertEquals(1, filesSeen)
    }

    @Test
    fun `corrupt 7z archive results in a handled exception`() {
        assertThrows<HandledException> {
            extract("corrupt.7z")
        }
    }

    /**
     * Extracts the given test archive and returns the contents of all extracted files, keyed by their path.
     */
    private fun extract(archiveName: String, filter: Predicate<String>? = null): Map<String, String> {
        val files = LinkedHashMap<String, String>()
        archiveExtractor.extractAll(archiveName, archive(archiveName), filter) { file ->
            // The content has to be read right away, as the underlying buffer is released after the callback.
            files[file.filePath] = file.openInputStream().use { it.readBytes().toString(StandardCharsets.UTF_8) }
        }
        return files
    }

    private fun archive(archiveName: String) = Paths.get("src/test/resources/test-data/archives", archiveName).toFile()

    companion object {
        @Part
        @JvmStatic
        private lateinit var archiveExtractor: ArchiveExtractor
    }
}
