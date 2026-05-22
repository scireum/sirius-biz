/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.cifs

import org.codelibs.jcifs.smb.impl.SmbFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.storage.layer3.VirtualFileSystem
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Value
import sirius.kernel.di.std.Part

/**
 * Tests the glue between the CIFS uplink and the jcifs library.
 */
@ExtendWith(SiriusExtension::class)
class CIFSUplinkTest {

    companion object {
        @Part
        private var vfs: VirtualFileSystem? = null
    }

    @Test
    fun `factory is registered under the cifs type`() {
        assertEquals("cifs", CIFSUplink.Factory().name)
    }

    @Test
    fun `factory creates anonymous virtual root backed by smb file`() {
        val uplink = CIFSUplink.Factory().make(
                "share",
                mapConfig(
                        "url" to "smb://example.invalid/share/",
                        "description" to "Test Share"
                )
        )

        assertInstanceOf(CIFSUplink::class.java, uplink)

        val root = uplink.getFile(vfs!!.root())

        assertEquals("share", root.name())
        assertEquals("/share", root.path())
        assertTrue(root.exists())
        assertTrue(root.isDirectory())
        assertTrue(root.tryAs(SmbFile::class.java).isPresent)
    }

    @Test
    fun `factory creates credentialed virtual root backed by smb file`() {
        val uplink = CIFSUplink.Factory().make(
                "secure-share",
                mapConfig(
                        "url" to "smb://example.invalid/secure-share/",
                        "domain" to "example.invalid",
                        "user" to "user",
                        "password" to "secret"
                )
        )

        val root = uplink.getFile(vfs!!.root())

        assertEquals("secure-share", root.name())
        assertTrue(root.tryAs(SmbFile::class.java).isPresent)
    }

    @Test
    fun `factory rejects credentials without a domain`() {
        assertThrows(IllegalArgumentException::class.java) {
            CIFSUplink.Factory().make(
                    "share",
                    mapConfig(
                            "url" to "smb://example.invalid/share/",
                            "user" to "user",
                            "password" to "secret"
                    )
            )
        }
    }

    private fun mapConfig(vararg entries: Pair<String, String>): (String) -> Value {
        val values = mapOf(*entries)
        return { key -> Value.of(values[key]) }
    }
}
