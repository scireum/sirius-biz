/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Executed once per minute to remove all IPs which have been blocked long enough.
 */
@Register(framework = Isenguard.FRAMEWORK_ISENGUARD)
public class RedisLimiterCleanupLoop extends BackgroundLoop {

    @Part
    private RedisLimiter redisLimiter;

    @Override
    public double maxCallFrequency() {
        return 1 / 60d;
    }

    @Nonnull
    @Override
    public String getName() {
        return "redis-limiter-cleanup";
    }

    @Nullable
    @Override
    protected String doWork() throws Exception {
        int numReclaimed = redisLimiter.cleanupBlockedIPs();
        if (numReclaimed > 0) {
            return Strings.apply("Removed %s blocked IPs", numReclaimed);
        } else {
            return null;
        }
    }
}
