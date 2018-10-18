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
import sirius.kernel.di.std.Part

class NamedCounterSpec extends BaseSpecification {

    @Part
    private static Redis redis

    def "local named conters works as expected"() {
        when:
        def counters = new LocalNamedCounters()
        and:
        counters.incrementAndGet("test")
        counters.incrementAndGet("test")
        counters.incrementAndGet("test")
        counters.decrementAndGet("test")
        and:
        counters.incrementAndGet("test1")
        counters.decrementAndGet("test1")
        then:
        counters.get("test") == 2
        counters.get("test1") == 0
        counters.get("test2") == 0
    }


    def "redis named counters works as expected"() {
        when:
        def counters = new RedisNamedCounters("test_counters", redis)
        and:
        counters.incrementAndGet("test")
        counters.incrementAndGet("test")
        counters.incrementAndGet("test")
        counters.decrementAndGet("test")
        and:
        counters.incrementAndGet("test1")
        counters.decrementAndGet("test1")
        then:
        counters.get("test") == 2
        counters.get("test1") == 0
        counters.get("test2") == 0
    }

}
