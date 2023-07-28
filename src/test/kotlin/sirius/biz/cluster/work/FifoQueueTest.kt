/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.db.redis.Redis
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Json
import sirius.kernel.di.std.Part
import kotlin.test.assertNull

@ExtendWith(SiriusExtension::class)
class FifoQueueTest {

    companion object {
        @Part
        private lateinit var redis: Redis
    }

    @Test
    fun `local FIFO works as expected`() {
        val fifo = LocalFifoQueue()

        fifo.offer(Json.createObject().put("value", 10))
        fifo.offer(Json.createObject().put("value", 20))
        fifo.offer(Json.createObject().put("value", 30))

        assertEquals(10, fifo.poll()?.path("value")?.asInt())
        assertEquals(20, fifo.poll()?.path("value")?.asInt())
        assertEquals(30, fifo.poll()?.path("value")?.asInt())
        assertNull(fifo.poll())
    }

    @Test
    fun `Redis FIFO works as expected`() {
        val fifo = RedisFifoQueue(redis, "fifo_test")

        fifo.offer(Json.createObject().put("value", 10))
        fifo.offer(Json.createObject().put("value", 20))
        fifo.offer(Json.createObject().put("value", 30))

        assertEquals(10, fifo.poll()?.path("value")?.asInt())
        assertEquals(20, fifo.poll()?.path("value")?.asInt())
        assertEquals(30, fifo.poll()?.path("value")?.asInt())
        assertNull(fifo.poll())
    }
}
