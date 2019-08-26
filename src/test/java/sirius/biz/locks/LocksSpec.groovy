/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks

import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

class LocksSpec extends BaseSpecification {

    @Part
    protected static Locks locks

    def "an acquired lock cannot be locked again unless it has been released"() {
        when:
        locks.tryLock("test", null)
        then:
        locks.tryLock("test", null)
        and:
        locks.unlock("test")
        and:
        locks.isLocked("test")
        and:
        locks.unlock("test")
        and:
        locks.isLocked("test") == false
    }

}
