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
        isenguard.increaseAndCheckRateLimitReached(scope,
                realm,
                Isenguard.USE_LIMIT_FROM_CONFIG,
                { -> counter.incrementAndGet() },
                { -> RateLimitingInfo(null, null, null) })
        isenguard.increaseAndCheckRateLimitReached(scope,
                realm,
                Isenguard.USE_LIMIT_FROM_CONFIG,
                { -> counter.incrementAndGet() },
                { -> RateLimitingInfo(null, null, null) })
        isenguard.increaseAndCheckRateLimitReached(scope,
                realm,
                Isenguard.USE_LIMIT_FROM_CONFIG,
                { -> counter.incrementAndGet() },
                { -> RateLimitingInfo(null, null, null) })
        val fourth = isenguard.increaseAndCheckRateLimitReached(scope,
                realm,
                Isenguard.USE_LIMIT_FROM_CONFIG,
                { -> counter.incrementAndGet() },
                { -> RateLimitingInfo(null, null, null) })
        val fifth = isenguard.increaseAndCheckRateLimitReached(scope,
                realm,
                Isenguard.USE_LIMIT_FROM_CONFIG,
                { -> counter.incrementAndGet() },
                { -> RateLimitingInfo(null, null, null) })
        val sixth = isenguard.increaseAndCheckRateLimitReached(scope,
                realm,
                Isenguard.USE_LIMIT_FROM_CONFIG,
                { -> counter.incrementAndGet() },
                { -> RateLimitingInfo(null, null, null) })

        assertFalse { fourth }
        assertTrue { fifth }
        assertTrue { sixth }
        assertEquals(1, counter.get())
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var isenguard: Isenguard
    }

}
