/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;

/**
 * Provides an "empty" implementation which doesn't perform any limiting at all.
 */
@Register(classes = {Limiter.class, NOOPLimiter.class})
public class NOOPLimiter implements Limiter {

    @Nonnull
    @Override
    public String getName() {
        return "noop";
    }

    @Override
    public boolean isIPBlacklisted(String ip) {
        return false;
    }

    @Override
    public void block(String ipAddress) {
        // noop
    }

    @Override
    public void unblock(String ipAddress) {
        // noop
    }

    @Override
    public boolean increaseAndCheckLimit(String key, int intervalInSeconds, int limit, Runnable limitReachedOnce) {
        return false;
    }

    @Override
    public int readLimit(String key) {
        return 0;
    }

    @Override
    public Set<String> getBlockedIPs() {
        return Collections.emptySet();
    }
}
