/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import sirius.kernel.di.std.Named;

/**
 * Represents a limiter strategy used by {@link Isenguard}.
 */
public interface Limiter extends Named {

    /**
     * Determines if the given IP is currently blacklisted.
     *
     * @param ip the ip to check
     * @return <tt>true</tt> if the IP is currently blacklisted, <tt>false</tt> otherwise
     */
    boolean isIPBLacklisted(String ip);

    /**
     * Blocks the given IP for a certain amount of time.
     *
     * @param ipAddress the IP address to block
     */
    void block(String ipAddress);

    /**
     * Removes the given IP from the block list.
     *
     * @param ipAddress the IP address to unblock
     */
    void unblock(String ipAddress);

    /**
     * Increates the interval counter and determines if the given limit was reached.
     *
     * @param key               the unique name of this counter which represents the ip, realm and check-interval
     * @param intervalInSeconds the duration of this interval in seconds (used to remove outdated counters)
     * @param limit             the limit i.e. max number of calls
     * @param limitReachedOnce  the handler to execute once if the limit for this interval is reached
     * @return <tt>true</tt> if the interval was reached, <tt>false</tt> otherwise
     */
    boolean increaseAndCheckLimit(String key, int intervalInSeconds, int limit, Runnable limitReachedOnce);
}
