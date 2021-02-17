/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Values;
import sirius.kernel.di.std.Part;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Wraps the LRU cache functionality provided by Jupiter.
 */
public class LRUCache {

    private static final JupiterCommand CMD_PUT = new JupiterCommand("LRU.PUT");
    private static final JupiterCommand CMD_GET = new JupiterCommand("LRU.GET");
    private static final JupiterCommand CMD_EXTENDED_GET = new JupiterCommand("LRU.XGET");
    private static final JupiterCommand CMD_REMOVE = new JupiterCommand("LRU.REMOVE");
    private static final JupiterCommand CMD_FLUSH = new JupiterCommand("LRU.FLUSH");

    private final JupiterConnector connection;
    private final String cache;

    @Part
    private static Tasks tasks;

    private static class CacheResult {
        boolean active;
        boolean refresh;
        String value;

        CacheResult(boolean active, boolean refresh, String value) {
            this.active = active;
            this.refresh = refresh;
            this.value = value;
        }

        boolean isActive() {
            return active;
        }

        boolean isRefresh() {
            return refresh;
        }

        String getValue() {
            return value;
        }
    }

    protected LRUCache(JupiterConnector connection, String cache) {
        this.connection = connection;
        this.cache = cache;
    }

    /**
     * Returns the cached value associated with the given key.
     *
     * @param key the key used to lookup the value
     * @return the value or an empty optional if no value is presnet
     */
    public Optional<String> get(String key) {
        return connection.query(() -> Strings.apply("LRU.GET %s", cache), jupiter -> {
            jupiter.sendCommand(CMD_GET, cache, key);
            String result = jupiter.getBulkReply();
            if (Strings.isFilled(result)) {
                return Optional.of(result);
            } else {
                return Optional.empty();
            }
        });
    }

    /**
     * Tries to fetch a value from the cache or computes one if it is missing.
     *
     * @param key           the key used to perform the lookup
     * @param valueComputer the computer used to provide a value if none is in the cache
     * @return the cached value or the newly computed value if none was in the cache
     */
    public String computeIfAbsent(String key, Supplier<String> valueComputer) {
        Optional<String> result = get(key);
        if (!result.isPresent()) {
            String value = valueComputer.get();
            put(key, value);

            return value;
        } else {
            return result.get();
        }
    }

    /**
     * Fetches or computes a value to be used.
     * <p>
     * However, in contrast to {@link #computeIfAbsent(String, Supplier)} this will deliver a stale result from the
     * cache and use the <tt>valueComputer</tt> to asynchronically compute a new value for the cache.
     * <p>
     * For situations in which using a stale cache value is acceptable, this provides a super low latency solution
     * to cache data.
     *
     * @param key           the key used to perform the lookup
     * @param valueComputer the computer used to provide a value if none is in the cache
     * @return the cached value or the newly computed value if none was in the cache. Note that this value might
     * be stale if its <tt>softTTL</tt> has been reached but not its <tt>hardTTL</tt>
     */
    public String extendedGet(String key, Supplier<String> valueComputer) {
        CacheResult cacheResult = connection.query(() -> Strings.apply("LRU.XGET %s", cache), jupiter -> {
            jupiter.sendCommand(CMD_EXTENDED_GET, cache, key);
            Values reply = Values.of(jupiter.getObjectMultiBulkReply());
            boolean active = reply.at(0).asInt(0) == 1;
            boolean refresh = reply.at(1).asInt(0) == 1;
            String value = Jupiter.readString(reply.at(2));

            return new CacheResult(active, refresh, value);
        });

        // The values was not found in the cache (not even a stale one => we have to compute now)...
        if (!cacheResult.isActive() && !cacheResult.isRefresh()) {
            if (Jupiter.LOG.isFINE()) {
                Jupiter.LOG.FINE("LRU.EXTENDED_GET in %s for %s returned no value - computing now!", cache, key);
            }
            String value = valueComputer.get();
            put(key, value);

            return value;
        }

        if (cacheResult.isRefresh()) {
            if (Jupiter.LOG.isFINE()) {
                Jupiter.LOG.FINE("LRU.EXTENDED_GET in %s for %s returned a refreshable value - computing async!",
                                 cache,
                                 key);
            }
            tasks.executor("jupiter-lru-computer").dropOnOverload(() -> {
                Jupiter.LOG.INFO("Dropping computation of %s in cache %s", key, cache);
            }).fork(() -> {
                String value = valueComputer.get();
                put(key, value);
            });
        } else if (Jupiter.LOG.isFINE()) {
            Jupiter.LOG.FINE("LRU.EXTENDED_GET in %s for %s returned a valid value!", cache, key);
        }

        return cacheResult.getValue();
    }

    /**
     * Stores the given value for the given key.
     *
     * @param key   the key to store the value for
     * @param value the value to store
     */
    public void put(String key, String value) {
        connection.exec(() -> Strings.apply("LRU.PUT %s", cache), jupiter -> {
            jupiter.sendCommand(CMD_PUT, cache, key, value);
            jupiter.getStatusCodeReply();
        });
    }

    /**
     * Removes the given value from the cache.
     *
     * @param key the key of the value to remove
     */
    public void remove(String key) {
        connection.exec(() -> Strings.apply("LRU.REMOVE %s", cache), jupiter -> {
            jupiter.sendCommand(CMD_REMOVE, cache, key);
            jupiter.getStatusCodeReply();
        });
    }

    /**
     * Removes all values from the cache.
     */
    public void flush() {
        connection.exec(() -> Strings.apply("LRU.FLUSH %s", cache), jupiter -> {
            jupiter.sendCommand(CMD_FLUSH);
            jupiter.getStatusCodeReply();
        });
    }
}
