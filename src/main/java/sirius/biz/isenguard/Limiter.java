/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import sirius.kernel.di.std.Named;

import java.util.Set;

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
    boolean isIPBlacklisted(String ip);

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
     * Increases the call counter for the current interval and determines if the given limit was reached.
     *
     * @param key               the unique name of this counter which represents the scope, realm and check-interval
     * @param intervalInSeconds the duration of this interval in seconds (used to remove outdated counters)
     * @param limit             the limit i.e. max number of calls
     * @param limitReachedOnce  the handler to execute once if the limit for this interval is reached
     * @return <tt>true</tt> if the interval was reached, <tt>false</tt> otherwise
     */
    boolean registerCallAndCheckLimit(String key, int intervalInSeconds, int limit, Runnable limitReachedOnce);

    /**
     * Reads the current call count for the given key.
     *
     * @param key the unique name of this counter which represents the scope, realm and check-interval
     * @return the current call counter value for the given key
     */
    int readCallCount(String key);

    /**
     * Returns the set of currently blocked IPs.
     * <p>
     * For efficiency reasons, this set may be limited to the latest matches (e.g. the top 50).
     *
     * @return a set of currently blocked IPs
     */
    Set<String> getBlockedIPs();
}
