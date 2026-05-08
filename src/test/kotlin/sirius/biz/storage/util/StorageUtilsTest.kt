/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util

import org.junit.jupiter.api.Test
import sirius.kernel.commons.Hasher
import sirius.kernel.di.std.Part
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the storage URL signing helpers in [StorageUtils].
 *
 * Covers Base64URL HMAC token generation and verification, eternally valid HMAC tokens, malformed token rejection,
 * and legacy MD5 fallback verification for already issued URLs.
 */
class StorageUtilsTest {

    @Test
    fun computeHashUsesBase64UrlHmacSha256() {
        val token = utils.computeHash("physical-key", 0)

        assertEquals(43, token.length)
        assertTrue(token.matches(Regex("[A-Za-z0-9_-]+")))
        assertFalse(token.matches(Regex("[0-9a-f]{32}")))
    }

    @Test
    fun verifiesGeneratedHmacHashes() {
        val token = utils.computeHash("physical-key", 0)

        assertEquals(0, utils.verifyHash("physical-key", token, 2).orElseThrow())
        assertTrue(utils.verifyHash("other-key", token, 2).isEmpty)
    }

    @Test
    fun verifiesGeneratedEternalHmacHashes() {
        val token = utils.computeEternallyValidHash("physical-key")

        assertEquals(Int.MAX_VALUE, utils.verifyHash("physical-key", token, 2).orElseThrow())
    }

    @Test
    fun acceptsLegacyEternalMd5Hashes() {
        val legacyToken = legacyEternalMd5Token("physical-key")

        assertEquals(Int.MAX_VALUE, utils.verifyHash("physical-key", legacyToken, 2).orElseThrow())
    }

    @Test
    fun acceptsLegacyDayMd5Hashes() {
        val legacyToken = legacyDayMd5Token("physical-key", 0)

        assertEquals(0, utils.verifyHash("physical-key", legacyToken, 2).orElseThrow())
    }

    @Test
    fun rejectsMalformedHmacTokens() {
        assertTrue(utils.verifyHash("physical-key", "not-a-valid-hmac-token-but-long-enough", 2).isEmpty)
    }

    private fun legacyEternalMd5Token(key: String): String =
        Hasher.md5().hash(key + SHARED_SECRET).toHexString()

    private fun legacyDayMd5Token(key: String, offsetDays: Int): String {
        val midnight: Instant =
            LocalDate.now().plusDays(offsetDays.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant()
        return Hasher.md5().hash(key + midnight.toEpochMilli() + SHARED_SECRET).toHexString()
    }

    companion object {
        private const val SHARED_SECRET = "storage-test-secret"

        @field:Part
        private lateinit var utils: StorageUtils
    }
}
