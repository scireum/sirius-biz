/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks

abstract class LocksSpec extends BaseSpecification {

    @Part
    protected static Locks locks

            @Part
            protected static Tasks tasks

            def "an acquired lock cannot be locked again unless it has been released"() {
        when:
        locks.tryLock("test", Duration.ofSeconds(1))
        then:
        locks.tryLock("test", Duration.ofSeconds(1))
        and:
        locks.unlock("test")
        and:
        locks.isLocked("test")
        and:
        locks.unlock("test")
        and:
        locks.isLocked("test") == false
    }

    def "an acquired lock can be transferred to another thread"() {
        when: "Acquire test lock in main thread"
        locks.tryLock("test", Duration.ofSeconds(1))
        and: "Initiate a lock transfer"
        Runnable lockTransfer = locks.initiateLockTransfer("test")
        and: "Create a semaphore to sync actions across both threads"
        Semaphore semaphore = new Semaphore(1)
        and: "Acquire the semaphore, so that the forked thread doesn't start uncontrolled.."
        semaphore.acquire()
        and: "Fork a thread which transfers the lock to itself and unlocks it"
        tasks.defaultExecutor().start({ ->
            lockTransfer.run()
            semaphore.acquire()
            locks.unlock("test")
            semaphore.release()
        })
        then: "Ensure that the lock is still held (the semaphore blocks the forked thread)"
        locks.isLocked("test")
        when: "Enable forked thread to unlock the transferred lock"
        semaphore.release()
        and: "Await that the thread completed executing..."
        while (locks.isLocked("test")) {
            Thread.yield()
        }
        then: "The lock is now unlocked by the other thread..."
        !locks.isLocked("test")
    }

}
