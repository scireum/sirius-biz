/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import sirius.db.redis.Redis;
import sirius.db.redis.RedisDB;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Provides a limiter based on Redis.
 */
@Register(classes = {Limiter.class, RedisLimiter.class})
public class RedisLimiter implements Limiter {

    public static final String BLOCKED_IPS = "isenguard-blocked-ips";
    private static final String COUNTER_PREFIX = "isenguard-counter-";
    private static final long MAX_BLOCK_DURATION_MILLIS = TimeUnit.MINUTES.toMillis(10);

    @Part
    private Redis redis;
    private RedisDB redisDB;

    @ConfigValue("isenguard.redisName")
    private String redisName;

    @Nonnull
    @Override
    public String getName() {
        return "redis";
    }

    @Override
    public boolean isIPBLacklisted(String ip) {
        return getDB().query(() -> "Check for blocked IP", db -> db.zrank(BLOCKED_IPS, ip) != null);
    }

    @Override
    public void block(String ipAddress) {
        getDB().exec(() -> "Block IP", db -> db.zadd(BLOCKED_IPS, System.currentTimeMillis(), ipAddress));
    }

    @Override
    public void unblock(String ipAddress) {
        getDB().exec(() -> "Block IP", db -> db.zrem(BLOCKED_IPS, ipAddress));
    }

    protected int cleanupBlockedIPs() {
        if (!isConfigured()) {
            return 0;
        }

        return getDB().query(() -> "Remove blocked IPs",
                             db -> db.zremrangeByScore(BLOCKED_IPS,
                                                       0,
                                                       (System.currentTimeMillis() - MAX_BLOCK_DURATION_MILLIS)))
                      .intValue();
    }

    @Override
    public boolean increaseAndCheckLimit(String key, int intervalInSeconds, int limit, Runnable limitReachedOnce) {
        String effectiveKey = COUNTER_PREFIX + key;
        return getDB().query(() -> "Update rate limit: " + effectiveKey, db -> {
            long value = db.incr(effectiveKey);
            if (value == 1) {
                db.expire(effectiveKey, intervalInSeconds);
            } else if (value == limit) {
                limitReachedOnce.run();
            }

            return value >= limit;
        });
    }

    @Override
    public int readLimit(String key) {
        String effectiveKey = COUNTER_PREFIX + key;
        return getDB().query(() -> "Read rate limit: " + effectiveKey, db -> {
            String value = db.get(effectiveKey);
            if (Strings.isEmpty(value)) {
                return 0;
            }

            return Integer.parseInt(value);
        });
    }

    @Override
    public Set<String> getBlockedIPs() {
        return getDB().query(() -> "Query blocked IPs", db -> new HashSet<>(db.zrevrange(BLOCKED_IPS, 0, 50)));
    }

    public boolean isConfigured() {
        return getDB().isConfigured();
    }

    private RedisDB getDB() {
        if (redisDB == null) {
            redisDB = redis.getPool(redisName);
        }

        return redisDB;
    }
}
