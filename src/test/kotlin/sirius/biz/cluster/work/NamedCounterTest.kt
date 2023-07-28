/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.db.redis.Redis
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import kotlin.test.assertEquals

@ExtendWith(SiriusExtension::class)
class NamedCounterTest {

    companion object {
        @Part
        private lateinit var redis: Redis
    }

    @Test
    fun `Local named counters works as expected`() {
        val counters = LocalNamedCounters()

        counters.incrementAndGet("test")
        counters.incrementAndGet("test")
        counters.incrementAndGet("test")
        counters.decrementAndGet("test")

        counters.incrementAndGet("test1")
        counters.decrementAndGet("test1")

        assertEquals(2, counters.get("test"))
        assertEquals(0, counters.get("test1"))
        assertEquals(0, counters.get("test2"))
    }

    @Test
    fun `Redis named counters works as expected`() {
        val counters = RedisNamedCounters("test_counters", redis)

        counters.incrementAndGet("test")
        counters.incrementAndGet("test")
        counters.incrementAndGet("test")
        counters.decrementAndGet("test")

        counters.incrementAndGet("test1")
        counters.decrementAndGet("test1")

        assertEquals(2, counters.get("test"))
        assertEquals(0, counters.get("test1"))
        assertEquals(0, counters.get("test2"))
    }
}
