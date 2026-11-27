/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ftp

import org.apache.commons.net.ftp.FTPClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import sirius.biz.tenants.TenantsHelper
import sirius.kernel.SiriusExtension
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the built-in FTP server with a plain FTP client, which sends its paths to the server as they are.
 *
 * The test configuration mounts "target" into the VFS as a local directory. A path which leaves this root therefore
 * ends up in the project directory, where it would find the <tt>pom.xml</tt>.
 */
@ExtendWith(SiriusExtension::class)
class FTPServerTest {
    @ValueSource(
        strings = [
            "/sftp-test-target/ftp-server-test/inside.txt",
            "/sftp-test-target/ftp-server-test/./inside.txt",
            "/sftp-test-target/ftp-server-test/sub/../inside.txt",
            "/sftp-test-target/../sftp-test-target/ftp-server-test/inside.txt"]
    )
    @ParameterizedTest
    fun `files are read via paths with relative segments which stay within the file system`(path: String) {
        assertEquals("inside", retrieve(path))
    }

    @Test
    fun `the parent directory is reachable by changing the working directory`() {
        assertTrue { ftp.changeWorkingDirectory("$DIRECTORY/sub") }
        assertTrue { ftp.changeWorkingDirectory("..") }

        assertEquals(DIRECTORY, ftp.printWorkingDirectory())
        assertEquals("inside", retrieve("inside.txt"))
    }

    @ValueSource(
        strings = [
            "/sftp-test-target/../pom.xml",
            "/sftp-test-target/ftp-server-test/../../pom.xml",
            "/sftp-test-target/ftp-server-test/../../../pom.xml",
            "/../pom.xml",
            "../../pom.xml"]
    )
    @ParameterizedTest
    fun `files outside of a root cannot be read`(path: String) {
        assertTrue { ftp.changeWorkingDirectory(DIRECTORY) }

        assertFalse { retrieve(path).contains(POM_MARKER) }
    }

    @ValueSource(
        strings = [
            "/sftp-test-target/ftp-server-test/../../ftp-server-test-escaped.txt",
            "/sftp-test-target/../ftp-server-test-escaped.txt",
            "../../ftp-server-test-escaped.txt"]
    )
    @ParameterizedTest
    fun `files outside of a root cannot be written`(path: String) {
        assertTrue { ftp.changeWorkingDirectory(DIRECTORY) }
        assertFalse { ftp.storeFile(path, "escaped".byteInputStream()) }
        assertFalse { File("ftp-server-test-escaped.txt").exists() }
        assertFalse { File("target", "ftp-server-test-escaped.txt").exists() }
    }

    @BeforeEach
    fun setUp() {
        localDirectory.deleteRecursively()
        File(localDirectory, "sub").mkdirs()
        File(localDirectory, "inside.txt").writeText("inside")
        TenantsHelper.installTestTenant()

        ftp = FTPClient()
        ftp.connectTimeout = TIMEOUT_MILLIS
        ftp.defaultTimeout = TIMEOUT_MILLIS
        ftp.dataTimeout = Duration.ofMillis(TIMEOUT_MILLIS.toLong())
        ftp.connect("localhost", 22021)
        assertTrue { ftp.login("test", "test") }
        ftp.enterLocalPassiveMode()
    }

    @AfterEach
    fun tearDown() {
        if (::ftp.isInitialized) {
            ftp.disconnect()
        }
        TenantsHelper.clearCurrentUser()
        localDirectory.deleteRecursively()
        File("ftp-server-test-escaped.txt").delete()
    }

    private fun retrieve(path: String): String {
        val buffer = ByteArrayOutputStream()
        ftp.retrieveFile(path, buffer)
        return buffer.toString(Charsets.UTF_8)
    }

    private lateinit var ftp: FTPClient

    companion object {
        private const val DIRECTORY = "/sftp-test-target/ftp-server-test"
        private const val POM_MARKER = "<artifactId>sirius-biz</artifactId>"
        private const val TIMEOUT_MILLIS = 10_000

        private val localDirectory = File("target", "ftp-server-test")
    }
}
