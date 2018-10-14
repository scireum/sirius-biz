/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

/**
 * Represents a set of counters.
 */
interface NamedCounters {

    /**
     * Increments and returns the counter with the given name.
     *
     * @param counter the name of the counter to increment.
     * @return the next counter value. If the counter was unknown, <tt>1</tt> will be returned.
     */
    long incrementAndGet(String counter);

    /**
     * Reads the given counter.
     *
     * @param counter the counter to read
     * @return the counter value or <tt>0</tt> if the counter is unknown.
     */
    long get(String counter);

    /**
     * Decrements the counter and returns the value.
     *
     * @param counter the counter to decrement
     * @return the decremented counter value. If the counter is unknown or was <tt>0</tt> already,
     * <tt>0</tt> will be returned.
     */
    long decrementAndGet(String counter);
}
