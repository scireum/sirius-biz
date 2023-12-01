/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard

class IsenguardSpec extends BaseSpecification {

    @Part
    private static Isenguard isenguard

            def "rateLimitingWorks"() {
        when:
        def counter = new AtomicInteger()
        isenguard.isRateLimitReached("127.0.0.1",
                "test",
                Isenguard.USE_LIMIT_FROM_CONFIG,
                { -> counter.incrementAndGet() },
                { -> new RateLimitingInfo(null, null, null) })
        isenguard.isRateLimitReached("127.0.0.1",
                "test",
                Isenguard.USE_LIMIT_FROM_CONFIG,
                { -> counter.incrementAndGet() },
                { -> new RateLimitingInfo(null, null, null) })
        isenguard.isRateLimitReached("127.0.0.1",
                "test", Isenguard.USE_LIMIT_FROM_CONFIG,
                { -> counter.incrementAndGet() },
                { -> new RateLimitingInfo(null, null, null) })
        def fourth = isenguard.isRateLimitReached("127.0.0.1",
        "test",
        Isenguard.USE_LIMIT_FROM_CONFIG,
        { -> counter.incrementAndGet() },
        { -> new RateLimitingInfo(null, null, null) })
        def fifth = isenguard.isRateLimitReached("127.0.0.1",
        "test",
        Isenguard.USE_LIMIT_FROM_CONFIG,
        { -> counter.incrementAndGet() },
        { -> new RateLimitingInfo(null, null, null) })
        def sixth = isenguard.isRateLimitReached("127.0.0.1",
        "test", Isenguard.USE_LIMIT_FROM_CONFIG,
        { -> counter.incrementAndGet() },
        { -> new RateLimitingInfo(null, null, null) })
        then:
        fourth == false
        fifth == true
        sixth == true
        counter.get() == 1
    }

}
