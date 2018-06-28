/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard

import sirius.db.redis.Redis
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

class IsenguardSpec extends BaseSpecification {

    @Part
    private static Redis redis

    def "redis works"() {
        when:
        redis.exec({ -> "X" }, { db -> db.set("Tet", "1") })
        then:
        "1" == redis.query({ -> "X" }, { db -> db.get("Tet") })
    }
}
