/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.util;

import org.apache.commons.pool2.impl.GenericObjectPool;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.RateLimit;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Provides a set of connection pools used by various uplinks.
 * <p>
 * Each uplink pool is configured via a {@link UplinkConnectorConfig} and its pool is created on demand (and also
 * drained and removed when idle for too long). Therefore an uplink can easily obtain a connector where without
 * caring to much if a new connection needs to be established or if an existing can be re-used.
 */
@Register(classes = UplinkConnectorPool.class)
public class UplinkConnectorPool {

    private static final Duration TIME_BETWEEN_EVICTION_RUNS = Duration.ofSeconds(30);
    private static final int NUM_TESTS_PER_EVICTION_RUN = 4;

    private final Map<UplinkConnectorConfig<?>, GenericObjectPool<UplinkConnector<?>>> pools =
            new ConcurrentHashMap<>();

    private final RateLimit cleanupLimit = RateLimit.timeInterval(1, TimeUnit.MINUTES);

    /**
     * Provides either a re-used or a new connector using the given config.
     *
     * @param config the config which instructs the framework on how to create and maintain connectors
     * @param <C>    the type of connector being requested
     * @return either a new or a pooled connector for the given config. Note that {@link UplinkConnector#close()} must
     * be called to put the connector back into the pool once it is no longer used.
     */
    @SuppressWarnings("unchecked")
    public <C> UplinkConnector<C> obtain(UplinkConnectorConfig<C> config) {
        try {
            return (UplinkConnector<C>) fetchPools().computeIfAbsent(config, this::createPool)
                                                    .borrowObject(config.maxWaitMillis);
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 3/Uplinks: Failed to obtain a connection for '%s' - %s (%s)",
                                                    config)
                            .handle();
        }
    }

    /**
     * Provides access to the underlying map of connection pools.
     * <p>
     * This method also evicts completely unused pools from time to time.
     *
     * @return the map of managed pools
     */
    protected synchronized Map<UplinkConnectorConfig<?>, GenericObjectPool<UplinkConnector<?>>> fetchPools() {
        if (cleanupLimit.check()) {
            removeDrainedPools();
        }

        return pools;
    }

    private void removeDrainedPools() {
        Iterator<Map.Entry<UplinkConnectorConfig<?>, GenericObjectPool<UplinkConnector<?>>>> iter =
                pools.entrySet().iterator();
        while (iter.hasNext()) {
            GenericObjectPool<UplinkConnector<?>> nextPool = iter.next().getValue();
            if (nextPool.getNumActive() + nextPool.getNumIdle() == 0) {
                iter.remove();
            }
        }
    }

    private GenericObjectPool<UplinkConnector<?>> createPool(UplinkConnectorConfig<?> uplinkConnectorConfig) {
        UplinkConnectorFactory uplinkConnectorFactory = new UplinkConnectorFactory(uplinkConnectorConfig);
        GenericObjectPool<UplinkConnector<?>> pool = new GenericObjectPool<>(uplinkConnectorFactory);
        uplinkConnectorFactory.linkToPool(pool);
        pool.setMaxIdle(uplinkConnectorConfig.maxIdle);
        pool.setMaxTotal(uplinkConnectorConfig.maxActive);
        pool.setDurationBetweenEvictionRuns(TIME_BETWEEN_EVICTION_RUNS);
        pool.setNumTestsPerEvictionRun(NUM_TESTS_PER_EVICTION_RUN);
        pool.setTestOnBorrow(true);
        pool.setTestWhileIdle(true);
        pool.setTestOnReturn(true);

        return pool;
    }
}
