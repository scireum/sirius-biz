/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter

import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

/**
 * Note that this only tests the communication between sirius and Jupiter.
 * <p>
 * The caching itself is tested throughoutly in Jupiter itself.
 */
class LRUCacheSpec extends BaseSpecification {

    @Part
    private static Jupiter jupiter

    def "get/put/remove works"() {
        given:
        def cache = jupiter.getDefault().lru("test")
        when:
        cache.put("test", "value")
        then:
        cache.get("test").get() == "value"
        and:
        cache.extendedGet("test", { ignored -> null }) == "value"
        and:
        cache.extendedGet("test1", { ignored -> "value1" }) == "value1"
        when:
        cache.remove("test")
        then:
        !cache.get("test").isPresent()
    }

}
