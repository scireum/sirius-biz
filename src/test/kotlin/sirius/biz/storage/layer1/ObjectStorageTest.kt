/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1


import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Streams
import sirius.kernel.di.std.Part
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the [ObjectStorage].
 */
@ExtendWith(SiriusExtension::class)
class ObjectStorageTest {
    @ParameterizedTest
    @CsvSource(
        textBlock = """
        128    | fs-test
        4096   | fs-test
        8192   | fs-test
        10000  | fs-test
        16384  | fs-test
        128    | fs-zip-test
        4096   | fs-zip-test
        8192   | fs-zip-test
        10000  | fs-zip-test
        16384  | fs-zip-test
        128    | fs-aes-test
        4096   | fs-aes-test
        8192   | fs-aes-test
        10000  | fs-aes-test
        16384  | fs-aes-test
        128    | fs-zip-aes-test
        4096   | fs-zip-aes-test
        8192   | fs-zip-aes-test
        10000  | fs-zip-aes-test
        16384  | fs-zip-aes-test
        128    | s3-test
        4096   | s3-test
        8192   | s3-test
        10000  | s3-test
        16384  | s3-test
        128    | s3-zip-test
        4096   | s3-zip-test
        8192   | s3-zip-test
        10000  | s3-zip-test
        16384  | s3-zip-test
        128    | s3-aes-test
        4096   | s3-aes-test
        8192   | s3-aes-test
        10000  | s3-aes-test
        16384  | s3-aes-test
        128    | s3-zip-aes-test
        4096   | s3-zip-aes-test
        8192   | s3-zip-aes-test
        10000  | s3-zip-aes-test
        16384  | s3-zip-aes-test""",
        delimiter = '|'
    )
    fun `storing and fetching data works as expected`(length: Int, space: String) {

        val testData = generateRandomData(length)
        val objectName = "test-data-" + length
        val objectSpace = storage.getSpace(space)

        objectSpace.upload(objectName, ByteArrayInputStream(testData), testData.size.toLong())

        val downloaded = objectSpace.download(objectName)

        assertTrue { downloaded.isPresent() }
        assertTrue { testData.contentEquals(Files.readAllBytes(downloaded.get().getFile().toPath())) }
        assertTrue { testData.contentEquals(Streams.toByteArray(objectSpace.getInputStream(objectName).get())) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["fs-test", "s3-test"])
    fun `deleting data works as expected`(space: String) {
        val testData = "test".toByteArray(StandardCharsets.UTF_8)
        val objectSpace = storage.getSpace(space)

        objectSpace.upload("delete-test", ByteArrayInputStream(testData), testData.size.toLong())

        val downloadedBeforeDelete = objectSpace.download("delete-test")

        objectSpace.delete("delete-test")

        val downloadedAfterDelete = objectSpace.download("delete-test")

        assertTrue { downloadedBeforeDelete.isPresent() }
        assertFalse { downloadedAfterDelete.isPresent() }

    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var storage: ObjectStorage

        private fun generateRandomData(length: Int): ByteArray {
            val rnd = Random()
            val result = ByteArray(length)
            rnd.nextBytes(result)
            return result
        }
    }
}
