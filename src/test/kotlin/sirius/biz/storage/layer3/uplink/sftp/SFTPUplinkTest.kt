/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.sftp

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.storage.layer3.FileSearch
import sirius.biz.storage.layer3.VirtualFile
import sirius.biz.storage.layer3.VirtualFileSystem
import sirius.biz.tenants.TenantsHelper
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import java.io.File

/**
 * Tests the SFTP uplink against the built-in SSH server.
 *
 * The test configuration mounts "target" into the VFS, serves the VFS via the SSH server and mounts the SFTP view
 * of it once more. Each test therefore runs through the client and the server side of Apache MINA SSHD, which pins
 * the behaviour we rely on whenever the library is updated.
 */
@ExtendWith(SiriusExtension::class)
class SFTPUplinkTest {

    @Test
    fun `file written through the SFTP uplink arrives in the served directory`() {
        resolveViaSftp("written.txt").consumeStream("Hello from the client".byteInputStream(), 21)

        assertEquals("Hello from the client", File(localDirectory, "written.txt").readText())
    }

    @Test
    fun `file served by the SSH server is read through the SFTP uplink`() {
        File(localDirectory, "served.txt").writeText("Hello from the server")

        val file = resolveViaSftp("served.txt")

        assertTrue(file.exists())
        assertEquals(21, file.size())
        assertEquals("Hello from the server", file.createInputStream().use { it.readBytes().decodeToString() })
    }

    @Test
    fun `directory listing through the SFTP uplink shows served files and directories`() {
        File(localDirectory, "listed.txt").writeText("listed")
        File(localDirectory, "listed-directory").mkdirs()

        val children = mutableMapOf<String, Boolean>()
        vfs!!.resolve(SFTP_DIRECTORY).children(FileSearch.iterateAll { children[it.name()] = it.isDirectory })

        assertEquals(false, children["listed.txt"])
        assertEquals(true, children["listed-directory"])
    }

    @Test
    fun `SFTP uplink with a wrong password is rejected by the SSH server`() {
        // Both uplinks only differ in their password. As the correct one succeeds, the server is reachable and
        // the failure below can only be caused by the authentication...
        vfs!!.resolve(SFTP_DIRECTORY).children(FileSearch.iterateAll {})

        val directory = vfs!!.resolve("/sftp-test-wrong-password/$DIRECTORY_NAME")

        assertThrows(HandledException::class.java) {
            directory.children(FileSearch.iterateAll {})
        }
    }

    @BeforeEach
    fun setUp() {
        localDirectory.deleteRecursively()
        localDirectory.mkdirs()
        TenantsHelper.installTestTenant()
    }

    @AfterEach
    fun tearDown() {
        TenantsHelper.clearCurrentUser()
    }

    private fun resolveViaSftp(name: String): VirtualFile {
        return vfs!!.resolve("$SFTP_DIRECTORY/$name")
    }

    companion object {
        private const val DIRECTORY_NAME = "sftp-uplink-test"
        private const val SFTP_DIRECTORY = "/sftp-test-loopback/$DIRECTORY_NAME"

        private val localDirectory = File("target", DIRECTORY_NAME)

        @Part
        private var vfs: VirtualFileSystem? = null
    }
}
