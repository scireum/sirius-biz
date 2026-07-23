/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import redis.clients.jedis.AbstractTransaction;
import redis.clients.jedis.UnifiedJedis;
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
                           db -> parseCounter(db.hget(name, counter), counter).orElse(0L));
    }

    private Optional<Long> parseCounter(String value, String counter) {
        if (value == null) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(Long.parseLong(value));
            } catch (NumberFormatException _) {
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

    private Long tryToDecrement(String counter, UnifiedJedis db) {
        // We need to WATCH, read and MULTI/EXEC on the very same connection, therefore we obtain a
        // transaction with manual control (doMulti = false) which pins a single connection instead
        // of letting each command borrow an arbitrary connection from the pool.
        try (AbstractTransaction transaction = db.transaction(false)) {
            transaction.watch(name);

            // Commands issued before multi() are executed immediately, so we can read the current
            // value through the very same (watched) connection.
            Optional<Long> counterValue = parseCounter(transaction.hget(name, counter).get(), counter);
            if (counterValue.isEmpty()) {
                transaction.unwatch();
                return 0L;
            }

            long nextCounterValue = Math.max(0, counterValue.get() - 1);
            transaction.multi();
            if (nextCounterValue == 0) {
                transaction.hdel(name, counter);
            } else {
                transaction.hset(name, counter, String.valueOf(nextCounterValue));
            }

            List<?> response = transaction.exec();
            if (response == null || response.isEmpty()) {
                return null;
            } else {
                return nextCounterValue;
            }
        }
    }
}
