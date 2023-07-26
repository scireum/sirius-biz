/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import sirius.web.controller.Message
import sirius.web.controller.MessageLevel
import sirius.web.http.DistributedUserMessageCache
import java.util.Collections

@ExtendWith(SiriusExtension::class)
class RedisUserMessageCacheTest {

    companion object {
        @Part
        private lateinit var cache: DistributedUserMessageCache
    }

    @Test
    fun `test put value and then get value works, but second get is empty`() {
        val key = "key" + System.currentTimeMillis()

        cache.put(key, Collections.singletonList(Message.error().withHTMLMessage("Test error message")))

        val result = cache.getAndRemove(key)
        assertEquals(1, result.size)
        assertTrue(result[0].html.contains("Test error message"))
        assertEquals(MessageLevel.PROBLEM, result[0].type)
        assertTrue(cache.getAndRemove(key).isEmpty())
    }
}
