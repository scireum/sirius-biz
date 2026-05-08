/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy

import io.mockk.every
import io.mockk.spyk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.storage.util.StorageUtils
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Hasher
import sirius.kernel.di.std.Part
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests storage URL signing compatibility for deprecated [Storage] URLs.
 *
 * Verifies that legacy storage URLs are now signed through [StorageUtils] using HMAC tokens while still accepting
 * existing MD5-signed URLs as fallback.
 */
@Suppress("DEPRECATION")
@ExtendWith(SiriusExtension::class)
class StorageSigningTest {

    @Test
    fun verifiesHmacAndLegacyMd5Hashes() {
        assertTrue(storage.verifyHash("physical-key", utils.computeHash("physical-key", 0)))

        val legacyToken = Hasher.md5().hash("physical-key$SHARED_SECRET").toHexString()
        assertTrue(storage.verifyHash("physical-key", legacyToken))
    }

    @Test
    fun computesHmacHashesForLegacyUrls() {
        val builder = spyk(DownloadBuilder(storage, "bucket", "object-key").withFileExtension("txt"))
        every { builder.physicalKey } answers { "physical-key" }

        val buildURL = Storage::class.java.getDeclaredMethod("buildURL", DownloadBuilder::class.java)
        buildURL.isAccessible = true

        val url = buildURL.invoke(storage, builder) as String
        val token = url.split("/")[4]

        assertTrue(token.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertFalse(token.matches(Regex("[0-9a-f]{32}")))
    }

    companion object {
        private const val SHARED_SECRET = "storage-test-secret"

        @field:Part
        private lateinit var storage: Storage

        @field:Part
        private lateinit var utils: StorageUtils
    }
}
