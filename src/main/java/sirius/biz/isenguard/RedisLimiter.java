/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import sirius.db.redis.Redis;

public class RedisLimiter implements Limiter {

    private Redis redis;

    public RedisLimiter(Redis redis) {
        this.redis = redis;
    }

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
        return redis.query(() -> "Rate Limit: " + key, db -> {
            long value = db.incr(key);
            if (value == 1) {
                db.expire(key, intervalInSeconds);
            } else if (value == limit) {
                limitReachedOnce.run();
            }

            return value >= limit;
        });
    }
}
