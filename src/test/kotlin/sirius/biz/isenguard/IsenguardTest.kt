/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the rate limiting capabilities of [Isenguard].
 */
@ExtendWith(SiriusExtension::class)
class IsenguardTest {

    @Test
    fun `Rate limiting works as intended`() {
        val scope = "127.0.0.1"
        val realm = "test"

        val counter = AtomicInteger()
        isenguard.registerCallAndCheckRateLimitReached(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        isenguard.registerCallAndCheckRateLimitReached(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        isenguard.registerCallAndCheckRateLimitReached(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        val thirdCheck = isenguard.checkRateLimitReached(scope, realm)
        val fourth = isenguard.registerCallAndCheckRateLimitReached(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        val fourthCheck = isenguard.checkRateLimitReached(scope, realm)
        val fifth = isenguard.registerCallAndCheckRateLimitReached(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        val fifthCheck = isenguard.checkRateLimitReached(scope, realm)
        val sixth = isenguard.registerCallAndCheckRateLimitReached(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        val sixthCheck = isenguard.checkRateLimitReached(scope, realm)

        assertFalse { thirdCheck }
        assertFalse { fourth }
        assertFalse { fourthCheck }
        assertTrue { fifth }
        assertTrue { fifthCheck }
        assertTrue { sixth }
        assertTrue { sixthCheck }
        assertEquals(1, counter.get())
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var isenguard: Isenguard
    }

}
