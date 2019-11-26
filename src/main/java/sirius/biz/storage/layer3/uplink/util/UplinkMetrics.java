/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.util;

import org.apache.commons.pool2.impl.GenericObjectPool;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.metrics.MetricProvider;
import sirius.kernel.health.metrics.MetricState;
import sirius.kernel.health.metrics.MetricsCollector;

/**
 * Reports the number of active uplinks and their connections.
 */
@Register
public class UplinkMetrics implements MetricProvider {

    @Part
    private UplinkConnectorPool uplinkConnectorPool;

    @Override
    public void gather(MetricsCollector collector) {
        int numberOfActivePools = uplinkConnectorPool.fetchPools().size();
        int numberOfConnections =
                uplinkConnectorPool.fetchPools().values().stream().mapToInt(GenericObjectPool::getNumActive).sum();

        collector.metric("vfs-uplink-pools",
                         "Active VFS Uplink Pools",
                         numberOfActivePools,
                         null,
                         numberOfActivePools > 0 ? MetricState.GREEN : MetricState.GRAY);
        collector.metric("vfs-uplink-connections",
                         "Active VFS Uplink Connections",
                         numberOfConnections,
                         null,
                         numberOfConnections > 0 ? MetricState.GREEN : MetricState.GRAY);
    }
}
