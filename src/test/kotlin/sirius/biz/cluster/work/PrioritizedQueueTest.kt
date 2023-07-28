/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.db.redis.Redis
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Json
import sirius.kernel.di.std.Part

@ExtendWith(SiriusExtension::class)
class PrioritizedQueueTest {

    companion object {
        @Part
        private lateinit var redis: Redis
    }

    @Test
    fun `Local prioritized queue works as expected`() {
        val queue = LocalPrioritizedQueue(null)

        queue.offer(30, Json.createObject().put("value", 30))
        queue.offer(20, Json.createObject().put("value", 20))
        queue.offer(10, Json.createObject().put("value", 10))


        assertEquals(10, queue.poll()?.path("value")?.asInt())
        assertEquals(20, queue.poll()?.path("value")?.asInt())
        assertEquals(30, queue.poll()?.path("value")?.asInt())
        assertNull(queue.poll())
    }

    @Test
    fun `Redis prioritized queue works as expected`() {
        val queue = RedisPrioritizedQueue(redis, "prioritized_test")

        queue.offer(30, Json.createObject().put("value", 30))
        queue.offer(20, Json.createObject().put("value", 20))
        queue.offer(10, Json.createObject().put("value", 10))

        assertEquals(10, queue.poll()?.path("value")?.asInt())
        assertEquals(20, queue.poll()?.path("value")?.asInt())
        assertEquals(30, queue.poll()?.path("value")?.asInt())
        assertNull(queue.poll())
    }
}
