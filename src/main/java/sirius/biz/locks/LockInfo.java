/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks;

import java.time.LocalDateTime;

/**
 * Describes a lock being held.
 */
public class LockInfo {

    private final String name;
    private final String owner;
    private final String thread;
    private final LocalDateTime acquired;

    /**
     * Creates a new lock info.
     *
     * @param name     the name of the lock
     * @param owner    the node which acquired the lock
     * @param thread   the thread which acquired the lock
     * @param acquired the timestamp when the lock was acquired
     */
    public LockInfo(String name, String owner, String thread, LocalDateTime acquired) {
        this.name = name;
        this.owner = owner;
        this.thread = thread;
        this.acquired = acquired;
    }

    /**
     * Returns the name of the lock.
     *
     * @return the name of the lock
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the node on which the lock was acquired.
     *
     * @return the node which holds the lock
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Returns the thread which acquired the lock.
     *
     * @return the thread which acquired the lock
     */
    public String getThread() {
        return thread;
    }

    /**
     * Returns the timestamp when the lock was acquired.
     *
     * @return the timestamp when the lock was acquired
     */
    public LocalDateTime getAcquired() {
        return acquired;
    }
}
