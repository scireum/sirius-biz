/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks

class SQLLocksSpec extends LocksSpec {

    def setupSpec() {
        locks.manager = Injector.context().getPart("sql", LockManager.class)
    }

}
