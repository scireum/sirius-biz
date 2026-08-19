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
        isenguard.registerCallAndCheckRateLimitExceeded(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        isenguard.registerCallAndCheckRateLimitExceeded(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        isenguard.registerCallAndCheckRateLimitExceeded(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        val thirdCheck = isenguard.checkRateLimitExceeded(scope, realm)
        val fourth = isenguard.registerCallAndCheckRateLimitExceeded(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        val fourthCheck = isenguard.checkRateLimitExceeded(scope, realm)
        val fifth = isenguard.registerCallAndCheckRateLimitExceeded(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        val fifthCheck = isenguard.checkRateLimitExceeded(scope, realm)
        val sixth = isenguard.registerCallAndCheckRateLimitExceeded(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        val sixthCheck = isenguard.checkRateLimitExceeded(scope, realm)
        val seventh = isenguard.registerCallAndCheckRateLimitExceeded(
            scope,
            realm,
            Isenguard.USE_LIMIT_FROM_CONFIG,
            { counter.incrementAndGet() },
            { RateLimitingInfo(null, null, null) })
        val seventhCheck = isenguard.checkRateLimitExceeded(scope, realm)

        assertFalse { thirdCheck }
        assertFalse { fourth }
        assertFalse { fourthCheck }
        assertFalse { fifth }
        assertFalse { fifthCheck }
        assertTrue { sixth }
        assertTrue { sixthCheck }
        assertTrue { seventh }
        assertTrue { seventhCheck }
        assertEquals(1, counter.get())
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var isenguard: Isenguard
    }

}
