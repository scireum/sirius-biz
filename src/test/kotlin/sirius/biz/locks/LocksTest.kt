/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import sirius.kernel.async.Tasks
import sirius.kernel.di.std.Part
import java.time.Duration
import java.util.concurrent.Semaphore
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(SiriusExtension::class)
abstract class LocksSpec {

    @Test
    fun `An acquired lock cannot be locked again unless it has been released`() {
        locks.tryLock("test", Duration.ofSeconds(1))
        locks.tryLock("test", Duration.ofSeconds(1))
        locks.unlock("test")
        assertTrue { locks.isLocked("test") }
        locks.unlock("test")
        assertFalse { locks.isLocked("test") }
    }

    @Test
    fun `An acquired lock can be transferred to another thread`() {
        // Acquire test lock in the main thread
        locks.tryLock("test", Duration.ofSeconds(1))
        // Initiate a lock transfer
        val lockTransfer = locks.initiateLockTransfer("test")
        // Create a semaphore to sync actions across both threads
        val semaphore = Semaphore(1)
        // Acquire the semaphore, so that the forked thread doesn't start uncontrolled...
        semaphore.acquire()
        // Fork a thread which transfers the lock to itself and unlocks it
        tasks.defaultExecutor().start { ->
            lockTransfer.run()
            semaphore.acquire()
            locks.unlock("test")
            semaphore.release()
        }
        // Ensure that the lock is still held (the semaphore blocks the forked thread)
        assertTrue { locks.isLocked("test") }
        // Enable forked thread to unlock the transferred lock
        semaphore.release()
        // Await that the thread completed executing...
        while (locks.isLocked("test")) {
            Thread.yield()
        }
        // The lock is now unlocked by the other thread...
        assertFalse { locks.isLocked("test") }
    }

    companion object {
        @Part
        @JvmStatic
        lateinit var locks: Locks

        @Part
        lateinit var tasks: Tasks
    }

}
