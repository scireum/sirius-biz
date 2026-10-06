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
import java.io.File
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
        val files = extract("sample.7z", filter = { path -> path.endsWith(".csv") })

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
    fun `7z entry above the in-memory threshold is extracted via a temporary file`() {
        // large.txt has 5 MB, which exceeds the 4 MB that ExtractedFileBuffer keeps in memory.
        val files = extract("large.7z")

        val expected = "0123456789abcdef".repeat(5 * 1024 * 1024 / 16)
        assertEquals(setOf("large.txt"), files.keys)
        assertEquals(expected, files["large.txt"])
    }

    @Test
    fun `temporary file is removed when the consumer fails`() {
        // large.7z stores its only file in a block of its own. Therefore, this also ensures that a failing consumer
        // does not abort the JVM, which 7-Zip-JBinding 23.01-2.2 does if the 7-ZIP callback fails for the last file
        // of a block.
        val temporaryFilesBefore = countTemporaryBufferFiles()

        assertThrows<HandledException> {
            archiveExtractor.extractAll("large.7z", archive("large.7z"), null) {
                throw IllegalStateException("Simulated failure while importing the file")
            }
        }

        assertEquals(temporaryFilesBefore, countTemporaryBufferFiles())
    }

    @Test
    fun `error thrown by the consumer is rethrown unchanged`() {
        // An Error must not escape into the native 7-ZIP code either, as 7-Zip-JBinding 23.01-2.2 would abort the JVM
        // (large.7z stores its only file in a block of its own). Instead, it is rethrown once 7-ZIP has returned.
        val temporaryFilesBefore = countTemporaryBufferFiles()

        val error = assertThrows<AssertionError> {
            archiveExtractor.extractAll("large.7z", archive("large.7z"), null) {
                throw AssertionError("Simulated error while importing the file")
            }
        }

        assertEquals("Simulated error while importing the file", error.message)
        assertEquals(temporaryFilesBefore, countTemporaryBufferFiles())
    }

    @Test
    fun `ZIP which cannot be read by Java falls back to 7-Zip`() {
        // A 7z archive disguised as ZIP is rejected by the Java APIs (with both charsets), so ArchiveExtractor has to
        // retry using 7-Zip, which detects the actual format by itself.
        val files = extract("sample.7z", fileName = "sample.zip")

        assertEquals(setOf("readme.txt", "docs/Grüße.txt", "docs/data.csv"), files.keys)
    }

    @Test
    fun `corrupt 7z archive results in a handled exception`() {
        val exception = assertThrows<HandledException> {
            extract("corrupt.7z")
        }

        // Ensures that 7-Zip itself rejected the archive, and not some unrelated error along the way.
        assertTrue(exception.message!!.contains("probably corrupted"), exception.message)
    }

    @Test
    fun `damaged data in a 7z archive results in a handled exception`() {
        // In contrast to corrupt.7z, the headers of this archive are intact, but its packed data has been damaged.
        // 7-ZIP therefore opens it fine, but reports a DATAERROR for its only file. This result has to reach the
        // caller instead of a generic "Error extracting all items" from 7-Zip-JBinding.
        val exception = assertThrows<HandledException> {
            extract("damaged-data.7z")
        }

        assertTrue(exception.message!!.contains("DATAERROR"), exception.message)
    }

    /**
     * Extracts the given test archive and returns the contents of all extracted files, keyed by their path.
     *
     * @param fileName the file name to report to the extractor, which determines the processing based on its
     * extension. Defaults to the name of the test archive itself.
     */
    private fun extract(
        archiveName: String,
        filter: Predicate<String>? = null,
        fileName: String = archiveName
    ): Map<String, String> {
        val files = LinkedHashMap<String, String>()
        archiveExtractor.extractAll(fileName, archive(archiveName), filter) { file ->
            // The content has to be read right away, as the underlying buffer is released after the callback.
            files[file.filePath] = file.openInputStream().use { it.readBytes().toString(StandardCharsets.UTF_8) }
        }
        return files
    }

    private fun archive(archiveName: String) = Paths.get("src/test/resources/test-data/archives", archiveName).toFile()

    /**
     * Counts the temporary files created by [ExtractedFileBuffer] once an entry exceeds its in-memory threshold.
     */
    private fun countTemporaryBufferFiles() =
        File(System.getProperty("java.io.tmpdir")).listFiles { file -> file.name.startsWith("sirius_archive_") }
            ?.size ?: 0

    companion object {
        @Part
        @JvmStatic
        private lateinit var archiveExtractor: ArchiveExtractor
    }
}
