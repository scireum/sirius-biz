/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

public interface Limiter {
    boolean isIPBLacklisted(String ip);

    void block(String ipAddress);

    void unblock(String ipAddress);

    boolean increaseAndCheckLimit(String ip, String key, int intervalInSeconds, int limit, Runnable limitReachedOnce);

}
