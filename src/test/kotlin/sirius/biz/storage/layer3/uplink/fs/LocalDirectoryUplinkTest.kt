/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.fs

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.storage.layer3.FileSearch
import sirius.biz.storage.layer3.VirtualFile
import sirius.biz.storage.layer3.VirtualFileSystem
import sirius.biz.tenants.TenantsHelper
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests that the [LocalDirectoryUplink] does not follow symbolic links out of its base directory.
 *
 * The test configuration mounts "target" into the VFS as a local directory. All links created here point into a
 * temporary directory, so that nothing of value is reachable through them.
 */
@ExtendWith(SiriusExtension::class)
class LocalDirectoryUplinkTest {
    @Test
    fun `a symbolic link pointing out of its root is not followed`() {
        val outsideDirectory = Files.createTempDirectory("fs-uplink-test-outside").toFile()
        File(outsideDirectory, "secret.txt").writeText("secret")
        Files.createSymbolicLink(outsideLink.toPath(), outsideDirectory.toPath())

        try {
            assertThrows<HandledException> { vfs.resolve("$DIRECTORY/outside/secret.txt") }
            assertThrows<HandledException> { vfs.resolve("$DIRECTORY/outside") }
            assertFalse { childNames(vfs.resolve(DIRECTORY)).contains("outside") }
        } finally {
            Files.deleteIfExists(outsideLink.toPath())
            File(outsideDirectory, "secret.txt").delete()
            outsideDirectory.delete()
        }
    }

    @Test
    fun `a symbolic link without an existing target is neither listed nor written through`() {
        val outsideDirectory = Files.createTempDirectory("fs-uplink-test-outside").toFile()
        val outsideFile = File(outsideDirectory, "created.txt")
        Files.createSymbolicLink(outsideLink.toPath(), outsideFile.toPath())

        try {
            assertThrows<HandledException> {
                vfs.resolve("$DIRECTORY/outside").createOutputStream().use { it.write(1) }
            }
            assertFalse { outsideFile.exists() }
            assertFalse { childNames(vfs.resolve(DIRECTORY)).contains("outside") }
        } finally {
            Files.deleteIfExists(outsideLink.toPath())
            outsideFile.delete()
            outsideDirectory.delete()
        }
    }

    @Test
    fun `a file is not accessed once its directory has been replaced by a link pointing out of its root`() {
        val outsideDirectory = Files.createTempDirectory("fs-uplink-test-outside").toFile()
        File(outsideDirectory, "nested.txt").writeText("secret")
        val file = vfs.resolve("$DIRECTORY/sub/nested.txt")
        replaceSubDirectoryByLinkTo(outsideDirectory)

        try {
            assertThrows<HandledException> { file.createInputStream().use { it.readBytes() } }
            assertThrows<HandledException> { file.createOutputStream().use { it.write(1) } }
            assertThrows<HandledException> { file.delete() }
            assertEquals("secret", File(outsideDirectory, "nested.txt").readText())
        } finally {
            Files.deleteIfExists(File(localDirectory, "sub").toPath())
            File(outsideDirectory, "nested.txt").delete()
            outsideDirectory.delete()
        }
    }

    @Test
    fun `a file is neither moved nor changed once its directory has been replaced by a link pointing out of its root`() {
        val outsideDirectory = Files.createTempDirectory("fs-uplink-test-outside").toFile()
        val outsideFile = File(outsideDirectory, "nested.txt")
        outsideFile.writeText("secret")
        val file = vfs.resolve("$DIRECTORY/sub/nested.txt")
        val staleDirectory = vfs.resolve("$DIRECTORY/sub")
        val destination = vfs.resolve(DIRECTORY)
        replaceSubDirectoryByLinkTo(outsideDirectory)

        try {
            assertThrows<HandledException> { file.transferTo(destination).tryFastMove() }
            assertThrows<HandledException> { file.updateReadOnlyFlag(true) }
            assertThrows<HandledException> { childNames(staleDirectory) }
            assertThrows<HandledException> { staleDirectory.resolve("created").createAsDirectory() }
            assertFalse { File(localDirectory, "nested.txt").exists() }
            assertTrue { outsideFile.canWrite() }
            assertEquals(listOf("nested.txt"), outsideDirectory.list().orEmpty().toList())
        } finally {
            Files.deleteIfExists(File(localDirectory, "sub").toPath())
            outsideFile.delete()
            File(outsideDirectory, "created").delete()
            outsideDirectory.delete()
        }
    }

    @Test
    fun `a symbolic link pointing into its root is followed`() {
        Files.createSymbolicLink(insideLink.toPath(), File(localDirectory, "sub").absoluteFile.toPath())

        val file = vfs.resolve("$DIRECTORY/linked/nested.txt")

        assertEquals("nested", file.createInputStream().use { it.readBytes().decodeToString() })
        assertTrue { childNames(vfs.resolve(DIRECTORY)).contains("linked") }
    }

    @BeforeEach
    fun setUp() {
        cleanUp()
        File(localDirectory, "sub").mkdirs()
        File(localDirectory, "inside.txt").writeText("inside")
        File(localDirectory, "sub/nested.txt").writeText("nested")
        TenantsHelper.installTestTenant()
    }

    @AfterEach
    fun tearDown() {
        TenantsHelper.clearCurrentUser()
        cleanUp()
    }

    /**
     * Removes the test directory.
     *
     * The symbolic links are removed first and on their own, as a recursive deletion would follow them and delete
     * the contents of their targets.
     */
    private fun cleanUp() {
        Files.deleteIfExists(outsideLink.toPath())
        Files.deleteIfExists(insideLink.toPath())
        if (Files.isSymbolicLink(File(localDirectory, "sub").toPath())) {
            Files.delete(File(localDirectory, "sub").toPath())
        }
        localDirectory.deleteRecursively()
    }

    /**
     * Replaces the sub directory of the test directory by a symbolic link to the given directory.
     */
    private fun replaceSubDirectoryByLinkTo(target: File) {
        val directory = File(localDirectory, "sub")
        File(directory, "nested.txt").delete()
        directory.delete()
        Files.createSymbolicLink(directory.toPath(), target.toPath())
    }

    private fun childNames(directory: VirtualFile): List<String> {
        val names = mutableListOf<String>()
        directory.children(FileSearch.iterateAll { names.add(it.name()) })
        return names
    }

    companion object {
        private const val DIRECTORY = "/sftp-test-target/fs-uplink-test"

        private val localDirectory = File("target", "fs-uplink-test")
        private val outsideLink = File(localDirectory, "outside")
        private val insideLink = File(localDirectory, "linked")

        @JvmStatic
        @Part
        private lateinit var vfs: VirtualFileSystem
    }
}
