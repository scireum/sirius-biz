/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work

import sirius.db.redis.Redis
import sirius.kernel.BaseSpecification
import sirius.kernel.commons.Json
import sirius.kernel.di.std.Part

class PrioritizedQueueSpec extends BaseSpecification {

    @Part
    private static Redis redis

    def "local prioritized queue works as expected"() {
        when:
        def queue = new LocalPrioritizedQueue()
        and:
        queue.offer(30, Json.createObject().put("value", 30))
        queue.offer(20, Json.createObject().put("value", 20))
        queue.offer(10, Json.createObject().put("value", 10))
        then:
        queue.poll().path("value").asInt() == 10
        queue.poll().path("value").asInt() == 20
        queue.poll().path("value").asInt() == 30
        queue.poll() == null
    }


    def "redis fifo works as expected"() {
        when:
        def queue = new RedisPrioritizedQueue(redis, "prioritized_test")
        and:
        queue.offer(30, Json.createObject().put("value", 30))
        queue.offer(20, Json.createObject().put("value", 20))
        queue.offer(10, Json.createObject().put("value", 10))
        then:
        queue.poll().path("value").asInt() == 10
        queue.poll().path("value").asInt() == 20
        queue.poll().path("value").asInt() == 30
        queue.poll() == null
    }

}
