/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

public class JavaLimiter implements Limiter {

    @Override
    public boolean isIPBLacklisted(String ip) {
        return false;
    }

    @Override
    public void block(String ipAddress) {

    }

    @Override
    public void unblock(String ipAddress) {

    }

    @Override
    public boolean increaseAndCheckLimit(String ip,
                                         String key,
                                         int intervalInSeconds,
                                         int limit,
                                         Runnable limitReachedOnce) {
        return false;
    }

}
