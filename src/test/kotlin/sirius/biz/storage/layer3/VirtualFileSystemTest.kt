/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import sirius.biz.tenants.TenantsHelper
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import sirius.web.http.TestRequest
import java.io.File
import java.net.URI
import java.util.EnumSet
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests that paths and names cannot be used to leave a root of the [VirtualFileSystem].
 *
 * The test configuration mounts "target" into the VFS as a local directory. A path which leaves this root therefore
 * ends up in the project directory, where it would find the <tt>pom.xml</tt>.
 */
@ExtendWith(SiriusExtension::class)
class VirtualFileSystemTest {
    @CsvSource(
        delimiter = '|', textBlock = """
        /sftp-test-target/vfs-test/inside.txt                 | /sftp-test-target/vfs-test/inside.txt
        /sftp-test-target/vfs-test/./inside.txt               | /sftp-test-target/vfs-test/inside.txt
        /sftp-test-target/vfs-test/sub/../inside.txt          | /sftp-test-target/vfs-test/inside.txt
        /sftp-test-target/vfs-test/sub/./../inside.txt        | /sftp-test-target/vfs-test/inside.txt
        /sftp-test-target/../sftp-test-target/vfs-test/inside.txt | /sftp-test-target/vfs-test/inside.txt
        sftp-test-target\vfs-test\sub\..\inside.txt           | /sftp-test-target/vfs-test/inside.txt"""
    )
    @ParameterizedTest
    fun `paths are normalized within the virtual file system`(path: String, expectedPath: String) {
        val file = vfs.resolve(path)

        assertEquals(expectedPath, file.path())
        assertEquals("inside", file.createInputStream().use { it.readBytes().decodeToString() })
    }

    @ValueSource(
        strings = [
            "/sftp-test-target/../pom.xml",
            "/sftp-test-target/vfs-test/../../pom.xml",
            "/sftp-test-target/vfs-test/sub/../../../pom.xml",
            "sftp-test-target\\vfs-test\\..\\..\\pom.xml",
            "/sftp-test-target/vfs-test/ ../ ../pom.xml"]
    )
    @ParameterizedTest
    fun `a path leaving its root does not reach the underlying file system`(path: String) {
        val file = vfs.resolve(path)

        assertEquals("/pom.xml", file.path())
        assertFalse { file.exists() }
    }

    @ValueSource(
        strings = [
            "/..",
            "/../pom.xml",
            "/sftp-test-target/../..",
            "/sftp-test-target/vfs-test/../../../pom.xml",
            "/sftp-test-target/vfs-test/.../pom.xml"]
    )
    @ParameterizedTest
    fun `a path leaving the virtual file system is rejected`(path: String) {
        assertThrows<IllegalArgumentException> { vfs.resolve(path) }
    }

    @ValueSource(
        strings = [
            "/sftp-test-target/vfs-test/ inside.txt",
            "/sftp-test-target/ vfs-test/inside.txt",
            "/sftp-test-target/vfs-test/ /inside.txt",
            " /sftp-test-target/vfs-test/inside.txt "]
    )
    @ParameterizedTest
    fun `whitespace around a path and in front of its segments is ignored`(path: String) {
        val file = vfs.resolve(path)

        assertEquals("$DIRECTORY/inside.txt", file.path())
        assertTrue { file.exists() }
    }

    @Test
    fun `whitespace at the end of a segment is kept as part of its name`() {
        assertEquals("$DIRECTORY /inside.txt", vfs.resolve("$DIRECTORY /inside.txt").path())
        assertThrows<IllegalArgumentException> { vfs.resolve("$DIRECTORY/sub/.. /inside.txt") }
    }

    @ValueSource(
        strings = [
            "/sftp-test-target/vfs-test/x\r\nCWD ..\r\nDELE victim.txt/y",
            "/sftp-test-target/vfs-test/x\ny",
            "/sftp-test-target/vfs-test/sub/x\ty"]
    )
    @ParameterizedTest
    fun `a path with control characters is rejected`(path: String) {
        assertThrows<IllegalArgumentException> { vfs.resolve(path) }
    }

    @CsvSource(
        delimiter = '|', textBlock = """
        inside.txt            | /sftp-test-target/vfs-test/inside.txt
        ./inside.txt          | /sftp-test-target/vfs-test/inside.txt
        sub/../inside.txt     | /sftp-test-target/vfs-test/inside.txt
        ./sub/nested.txt      | /sftp-test-target/vfs-test/sub/nested.txt"""
    )
    @ParameterizedTest
    fun `relative paths are resolved within their directory`(path: String, expectedPath: String) {
        val file = vfs.resolve(DIRECTORY).resolve(path)

        assertEquals(expectedPath, file.path())
        assertTrue { file.exists() }
    }

    @Test
    fun `a relative path pointing to its own directory yields that directory`() {
        val directory = vfs.resolve(DIRECTORY)

        assertEquals(DIRECTORY, directory.resolve(".").path())
        assertEquals(DIRECTORY, directory.resolve("sub/..").path())
    }

    @ValueSource(
        strings = [
            "..",
            "../inside.txt",
            "../../pom.xml",
            "sub/../../outside.txt",
            "./../outside.txt",
            "..\\..\\pom.xml"]
    )
    @ParameterizedTest
    fun `a relative path leaving its directory is rejected`(path: String) {
        val directory = vfs.resolve("$DIRECTORY/sub")

        assertThrows<IllegalArgumentException> { directory.resolve(path) }
    }

    @ValueSource(
        strings = [
            "",
            "..",
            ".",
            ".. ",
            " ..",
            "...",
            " ",
            "../pom.xml",
            "sub/nested.txt",
            "/etc/passwd",
            "//other.host/share/file.txt",
            "..\\pom.xml",
            "sub\\nested.txt",
            "tab\there.txt",
            "x\r\nDELE victim.txt",
            "x\nCWD ..",
            "x\u0000.txt"]
    )
    @ParameterizedTest
    fun `a child cannot be addressed by a path, a relative reference or a name with control characters`(name: String) {
        val directory = vfs.resolve(DIRECTORY)

        assertThrows<IllegalArgumentException> { directory.findChild(name) }
    }

    @ValueSource(strings = ["..data.txt", ".hidden", "data..txt", "data.", "my file.txt", "ünïcödé.txt"])
    @ParameterizedTest
    fun `unusual but plain names are accepted as child`(name: String) {
        val child = vfs.resolve(DIRECTORY).findChild(name)

        assertEquals("$DIRECTORY/$name", child.path())
    }

    @ValueSource(
        strings = [
            "..",
            ".",
            "...",
            "../renamed.txt",
            "../../renamed.txt",
            "sub/renamed.txt",
            "..\\renamed.txt",
            "re\nnamed.txt",
            "re\tnamed.txt"]
    )
    @ParameterizedTest
    fun `a file cannot be renamed to a path, a relative reference or a name with control characters`(name: String) {
        val file = vfs.resolve("$DIRECTORY/inside.txt")

        assertFalse { file.tryRename(name) }
        assertThrows<HandledException> { file.rename(name) }
        assertTrue { File(localDirectory, "inside.txt").exists() }
        assertFalse { File("renamed.txt").exists() }
        assertFalse { File("target", "renamed.txt").exists() }
        assertFalse { File(localDirectory, "sub/renamed.txt").exists() }
        assertEquals(listOf("inside.txt", "sub"), localDirectory.list().orEmpty().sorted())
    }

    @Test
    fun `a file can be renamed to a plain name`() {
        val file = vfs.resolve("$DIRECTORY/inside.txt")

        assertTrue { file.tryRename("renamed.txt") }
        assertTrue { File(localDirectory, "renamed.txt").exists() }
    }

    @Test
    fun `the path of a URL cannot reach the directory of another host`() {
        val directory = vfs.resolve(DIRECTORY)
        val options = EnumSet.of(RemoteFileResolver.Options.INCLUDE_HOST_NAME)

        assertEquals(
            "$DIRECTORY/good.example/image.jpg",
            directory.resolveOrLoadChildFromURL(
                URI("https://good.example/image.jpg"),
                FetchFromUrlMode.NEVER_FETCH,
                "jpg"::equals,
                options
            ).first.path()
        )
        assertThrows<HandledException> {
            directory.resolveOrLoadChildFromURL(
                URI("https://evil.example/../good.example/image.jpg"),
                FetchFromUrlMode.NEVER_FETCH,
                "jpg"::equals,
                options
            )
        }
    }

    @Test
    fun `the web interface delivers a file within a root`() {
        val response = fileRequest("$DIRECTORY/sub/../inside.txt")

        assertEquals("inside", response.contentAsString)
    }

    @ValueSource(
        strings = [
            "/sftp-test-target/../pom.xml",
            "/sftp-test-target/vfs-test/../../pom.xml",
            "/sftp-test-target/vfs-test/../../../pom.xml",
            "/../pom.xml"]
    )
    @ParameterizedTest
    fun `the web interface does not deliver files outside of a root`(path: String) {
        val content = fileRequest(path).contentAsString

        assertFalse { content.contains(POM_MARKER) }
        // The rejection is reported as an unknown path, and not as an unexpected error
        assertFalse { content.contains("IllegalArgumentException") }
        assertFalse { content.contains("Invalid path") }
    }

    @Test
    fun `the web interface creates a directory within the chosen directory`() {
        postRequest("/fs/createDirectory", "parent" to DIRECTORY, "name" to "created")

        assertTrue { File(localDirectory, "created").isDirectory }
    }

    @ValueSource(strings = ["../escaped-directory", "sub/../../escaped-directory", "..\\escaped-directory"])
    @ParameterizedTest
    fun `the web interface does not create a directory outside of the chosen directory`(name: String) {
        postRequest("/fs/createDirectory", "parent" to DIRECTORY, "name" to name)

        assertFalse { File("target", "escaped-directory").exists() }
    }

    @Test
    fun `the web interface stores an upload within the chosen directory`() {
        uploadRequest("uploaded.txt")

        assertEquals("uploaded", File(localDirectory, "uploaded.txt").readText())
    }

    @ValueSource(strings = ["../escaped-upload.txt", "sub/../../escaped-upload.txt", "..\\escaped-upload.txt"])
    @ParameterizedTest
    fun `the web interface does not store an upload outside of the chosen directory`(filename: String) {
        uploadRequest(filename)

        assertFalse { File("target", "escaped-upload.txt").exists() }
    }

    @Test
    fun `the web interface deletes a file within a root`() {
        postRequest("/fs/delete", "path" to "$DIRECTORY/sub/../inside.txt")

        assertFalse { File(localDirectory, "inside.txt").exists() }
    }

    @ValueSource(strings = ["/sftp-test-target/../vfs-test-decoy.txt", "/sftp-test-target/vfs-test/../../vfs-test-decoy.txt"])
    @ParameterizedTest
    fun `the web interface does not delete files outside of a root`(path: String) {
        decoy.writeText("decoy")

        postRequest("/fs/delete", "path" to path)

        assertTrue { decoy.exists() }
    }

    @ValueSource(strings = ["/sftp-test-target/..", "/sftp-test-target/vfs-test/../.."])
    @ParameterizedTest
    fun `the web interface does not move files out of a root`(newParent: String) {
        postRequest("/fs/move", "path" to "$DIRECTORY/inside.txt", "newParent" to newParent)

        assertTrue { File(localDirectory, "inside.txt").exists() }
        assertFalse { File("inside.txt").exists() }
    }

    @BeforeEach
    fun setUp() {
        localDirectory.deleteRecursively()
        File(localDirectory, "sub").mkdirs()
        File(localDirectory, "inside.txt").writeText("inside")
        File(localDirectory, "sub/nested.txt").writeText("nested")
        TenantsHelper.installTestTenant()
    }

    @AfterEach
    fun tearDown() {
        TenantsHelper.clearCurrentUser()
        localDirectory.deleteRecursively()
        // Only exist if a test failed, but would then spoil all subsequent runs
        File("renamed.txt").delete()
        File("inside.txt").delete()
        File("target", "renamed.txt").delete()
        File("target", "escaped-upload.txt").delete()
        File("target", "escaped-directory").delete()
        decoy.delete()
    }

    private fun fileRequest(path: String) = TestRequest.GET("/fs").withParameter("path", path).also {
        TenantsHelper.installBackendUser(it)
    }.execute()

    private fun postRequest(uri: String, vararg parameters: Pair<String, String>) = TestRequest.SAFEPOST(uri).also {
        parameters.forEach { (name, value) -> it.withParameter(name, value) }
        TenantsHelper.installBackendUser(it)
    }.execute()

    private fun uploadRequest(filename: String) =
        TestRequest.SAFEPOST("/fs/upload").withParameter("path", DIRECTORY).withParameter("filename", filename).also {
            TenantsHelper.installBackendUser(it)
        }.preDispatch().sendResource("uploaded".byteInputStream()).execute()

    companion object {
        private const val DIRECTORY = "/sftp-test-target/vfs-test"
        private const val POM_MARKER = "<artifactId>sirius-biz</artifactId>"

        private val localDirectory = File("target", "vfs-test")

        /**
         * Resides in the project directory and thus outside the root, where a test tries to delete it.
         */
        private val decoy = File("vfs-test-decoy.txt")

        @JvmStatic
        @Part
        private lateinit var vfs: VirtualFileSystem
    }
}
