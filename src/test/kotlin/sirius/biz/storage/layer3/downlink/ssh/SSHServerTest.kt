/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh

import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.SftpClientFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import sirius.biz.tenants.TenantsHelper
import sirius.kernel.SiriusExtension
import java.io.File
import java.io.IOException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the built-in SSH server with a plain SFTP client, which sends its paths to the server as they are.
 *
 * The test configuration mounts "target" into the VFS as a local directory. A path which leaves this root therefore
 * ends up in the project directory, where it would find the <tt>pom.xml</tt>.
 */
@ExtendWith(SiriusExtension::class)
class SSHServerTest {
    @ValueSource(
        strings = [
            "/sftp-test-target/ssh-server-test/inside.txt",
            "/sftp-test-target/ssh-server-test/./inside.txt",
            "/sftp-test-target/ssh-server-test/sub/../inside.txt",
            "/sftp-test-target/../sftp-test-target/ssh-server-test/inside.txt"]
    )
    @ParameterizedTest
    fun `files are read via paths with relative segments which stay within the file system`(path: String) {
        assertEquals("inside", sftp().read(path).use { it.readBytes().decodeToString() })
    }

    @ValueSource(
        strings = [
            "/sftp-test-target/../pom.xml",
            "/sftp-test-target/ssh-server-test/../../pom.xml",
            "/sftp-test-target/ssh-server-test/../../../pom.xml",
            "/../pom.xml",
            "/../../etc/hosts"]
    )
    @ParameterizedTest
    fun `files outside of a root cannot be read`(path: String) {
        assertThrows<IOException> { sftp().read(path).use { it.readBytes() } }
    }

    @ValueSource(
        strings = [
            "/sftp-test-target/ssh-server-test/../../ssh-server-test-escaped.txt",
            "/sftp-test-target/../ssh-server-test-escaped.txt"]
    )
    @ParameterizedTest
    fun `files outside of a root cannot be written`(path: String) {
        assertThrows<IOException> { sftp().write(path).use { it.write("escaped".toByteArray()) } }

        assertFalse { File("ssh-server-test-escaped.txt").exists() }
    }

    @Test
    fun `a file cannot be moved out of its root`() {
        assertThrows<IOException> { sftp().rename("$DIRECTORY/inside.txt", "$DIRECTORY/../../inside.txt") }

        assertFalse { File("inside.txt").exists() }
        assertTrue { File(localDirectory, "inside.txt").exists() }
    }

    @Test
    fun `a file is moved via a path with relative segments which stays within its root`() {
        sftp().rename("$DIRECTORY/inside.txt", "$DIRECTORY/sub/../sub/inside.txt")

        assertTrue { File(localDirectory, "sub/inside.txt").exists() }
    }

    @BeforeEach
    fun setUp() {
        localDirectory.deleteRecursively()
        File(localDirectory, "sub").mkdirs()
        File(localDirectory, "inside.txt").writeText("inside")
        TenantsHelper.installTestTenant()

        val sshClient = SshClient.setUpDefaultClient().also { client = it }
        sshClient.start()
        val clientSession = sshClient.connect("test", "localhost", 22022).verify(TIMEOUT).session.also { session = it }
        clientSession.addPasswordIdentity("test")
        clientSession.auth().verify(TIMEOUT)
        sftp = SftpClientFactory.instance().createSftpClient(clientSession)
    }

    @AfterEach
    fun tearDown() {
        sftp?.close()
        session?.close()
        client?.stop()
        TenantsHelper.clearCurrentUser()
        localDirectory.deleteRecursively()
        File("ssh-server-test-escaped.txt").delete()
        File("inside.txt").delete()
    }

    private fun sftp() = checkNotNull(sftp)

    private var client: SshClient? = null
    private var session: ClientSession? = null
    private var sftp: SftpClient? = null

    companion object {
        private const val DIRECTORY = "/sftp-test-target/ssh-server-test"

        private val TIMEOUT = Duration.ofSeconds(10)
        private val localDirectory = File("target", "ssh-server-test")
    }
}
