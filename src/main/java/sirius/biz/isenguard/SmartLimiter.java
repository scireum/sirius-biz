/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Provides a "smart" implementation which uses redis if available and resorts to "noop" otherwise.
 */
@Register(classes = {Limiter.class, SmartLimiter.class})
public class SmartLimiter implements Limiter {

    @Part
    private RedisLimiter redisLimiter;

    @Part
    private NOOPLimiter noopLimiter;

    private Limiter effectiveLimiter;

    @Nonnull
    @Override
    public String getName() {
        return "smart";
    }

    private Limiter getLimiter() {
        if (effectiveLimiter == null) {
            if (redisLimiter.isConfigured()) {
                effectiveLimiter = redisLimiter;
            } else {
                effectiveLimiter = noopLimiter;
            }
        }

        return effectiveLimiter;
    }

    @Override
    public boolean isIPBlacklisted(String ip) {
        return getLimiter().isIPBlacklisted(ip);
    }

    @Override
    public void block(String ipAddress) {
        getLimiter().block(ipAddress);
    }

    @Override
    public void unblock(String ipAddress) {
        getLimiter().unblock(ipAddress);
    }

    @Override
    public boolean registerCallAndCheckLimit(String key, int intervalInSeconds, int limit, Runnable limitReachedOnce) {
        return getLimiter().registerCallAndCheckLimit(key, intervalInSeconds, limit, limitReachedOnce);
    }

    @Override
    public int readCallCount(String key) {
        return getLimiter().readCallCount(key);
    }

    @Override
    public Set<String> getBlockedIPs() {
        return getLimiter().getBlockedIPs();
    }
}
