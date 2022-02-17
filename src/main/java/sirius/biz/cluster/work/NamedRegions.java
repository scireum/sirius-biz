/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import sirius.db.redis.Redis;
import sirius.kernel.commons.UnitOfWork;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Provides cluster-wide named regions.
 * <p>
 * A named region globally counts the active number of threads which are currently active within. Note that this
 * comes with a management overhead and should not be used in inner loops or other frequently called places. Also
 * note, that this might look like a locking pattern, but the {@link sirius.biz.locks.Locks} framework provides far
 * superior implementations. This is more or less an edge case to observe or verify some preconditions.
 *
 * @see sirius.biz.analytics.scheduler.AnalyticsBatchExecutor
 */
@Register(classes = NamedRegions.class)
public class NamedRegions {

    @Part
    private Redis redis;

    /**
     * Contains the counters for named regions.
     * <p>
     * This can be used to globally determine how many threads are currently within a named region.
     */
    private NamedCounters regions;

    private NamedCounters getRegions() {
        if (regions == null) {
            if (redis.isConfigured()) {
                regions = new RedisNamedCounters("cluster_named_regions", redis);
            } else {
                regions = new LocalNamedCounters();
            }
        }

        return regions;
    }

    /**
     * Executes the given code-block within the given region name.
     *
     * @param region the name of the region
     * @param task   the code to execute as this region
     * @throws Exception if the task itself throws an exception
     */
    public void inNamedRegion(String region, UnitOfWork task) throws Exception {
        getRegions().incrementAndGet(region);
        try {
            task.execute();
        } finally {
            getRegions().decrementAndGet(region);
        }
    }

    /**
     * Checks if the given region is free.
     *
     * @param region the name of the region
     * @return <tt>true</tt> if there are no other tasks in the given region, <tt>false</tt> otherwise
     */
    public boolean isNamedRegionFree(String region) {
        return getRegions().get(region) == 0;
    }
}
