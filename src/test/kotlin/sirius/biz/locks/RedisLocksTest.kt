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
 * Tests the Redis backed [LockManager] implementation.
 */
class RedisLocksTest : LocksSpec() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            locks.javaClass.getDeclaredField("manager").apply {
                isAccessible = true
                set(locks, Injector.context().getPart("redis", LockManager::class.java))
            }
        }
    }
}
