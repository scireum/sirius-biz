/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks

import sirius.db.mixing.Schema
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

import java.time.Duration

class LocksSpec extends BaseSpecification {

    @Part
    private static Locks locks;

    @Part
    private static Schema schema;

    def "an acquired lock cannot be locked again unless it has been released"() {
        given:
        schema.getReadyFuture().await(Duration.ofSeconds(45));
        when:
        locks.tryLock("test", null);
        then:
        locks.tryLock("test", null) == false;
        and:
        locks.unlock("test");
        locks.tryLock("test", null) == true;
        locks.unlock("test");
    }

}
