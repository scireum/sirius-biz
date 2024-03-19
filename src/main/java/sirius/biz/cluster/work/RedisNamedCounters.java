/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import sirius.db.redis.Redis;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Wait;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.util.List;
import java.util.Optional;

/**
 * Provides a set of counters which supports distributed concurrent access using Redis.
 */
class RedisNamedCounters implements NamedCounters {

    private static final int MAX_RETRIES_FOR_DECREMENT = 15;

    private final Redis redis;
    private final String name;

    protected RedisNamedCounters(String name, Redis redis) {
        this.name = name;
        this.redis = redis;
    }

    @Override
    public long incrementAndGet(String counter) {
        return redis.query(() -> Strings.apply("Increment counter %s for %s", counter, name),
                           db -> db.hincrBy(name, counter, 1));
    }

    @Override
    public long get(String counter) {
        return redis.query(() -> Strings.apply("Read counter %s for %s", counter, name),
                           db -> readCounter(db, counter).orElse(0L));
    }

    private Optional<Long> readCounter(Jedis db, String counter) {
        String value = db.hget(name, counter);
        if (value == null) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(Long.parseLong(value));
            } catch (NumberFormatException exception) {
                Exceptions.handle()
                          .to(Log.BACKGROUND)
                          .withSystemErrorMessage("Failed to parse counter value '%s' in counter '%s' of '%s'",
                                                  value,
                                                  counter,
                                                  name)
                          .handle();
                return Optional.of(0L);
            }
        }
    }

    @Override
    public long decrementAndGet(String counter) {
        int retries = MAX_RETRIES_FOR_DECREMENT;

        while (retries-- > 0) {
            Long value = redis.query(() -> Strings.apply("Decrement counter '%s' for '%s'", counter, name),
                                     db -> tryToDecrement(counter, db));

            if (value != null) {
                return value;
            }

            Wait.randomMillis(100, 250);
        }

        throw Exceptions.handle()
                        .to(Log.BACKGROUND)
                        .withSystemErrorMessage("Failed to decrement counter '%s' of '%s' after several attempts.",
                                                counter,
                                                name)
                        .handle();
    }

    private Long tryToDecrement(String counter, Jedis db) {
        db.watch(name);
        Optional<Long> counterValue = readCounter(db, counter);
        if (counterValue.isEmpty()) {
            db.unwatch();
            return 0L;
        }

        long nextCounterValue = Math.max(0, counterValue.get() - 1);
        Transaction txn = db.multi();
        if (nextCounterValue == 0) {
            txn.hdel(name, counter);
        } else {
            txn.hset(name, counter, String.valueOf(nextCounterValue));
        }

        List<?> response = txn.exec();
        if (response == null || response.isEmpty()) {
            return null;
        } else {
            return nextCounterValue;
        }
    }
}
