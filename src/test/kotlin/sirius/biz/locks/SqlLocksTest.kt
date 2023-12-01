/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks

import org.junit.jupiter.api.BeforeAll
import sirius.kernel.di.Injector

/**
 * Tests the SQL backed [LockManager] implementation.
 */
class SqlLocksTest : LocksSpec() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            locks.javaClass.getDeclaredField("manager").apply {
                isAccessible = true
                set(locks, Injector.context().getPart("sql", LockManager::class.java))
            }
        }
    }
}
