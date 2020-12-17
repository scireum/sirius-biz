/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.biz.storage.layer1.replication.ReplicationManager;
import sirius.biz.storage.layer2.variants.ConversionEngine;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.metrics.MetricProvider;
import sirius.kernel.health.metrics.MetricsCollector;
import sirius.web.health.CachingLoadInfoProvider;
import sirius.web.health.LoadInfo;
import sirius.web.health.LoadInfoProvider;

import java.util.function.Consumer;

/**
 * Provides some metrics and load infos for the storage system.
 */
@Register(classes = {MetricProvider.class, LoadInfoProvider.class}, framework = StorageUtils.FRAMEWORK_STORAGE)
public class StorageMetrics extends CachingLoadInfoProvider implements MetricProvider {

    @Part
    private ReplicationManager replicationManager;

    @Part
    private ConversionEngine conversionEngine;

    @Override
    public void gather(MetricsCollector metricsCollector) {
        metricsCollector.differentialMetric("storage_uploads",
                                            "storage-uploads",
                                            "Storage Uploads",
                                            ObjectStorageSpace.getUploads(),
                                            "/min");
        metricsCollector.differentialMetric("storage_downloads",
                                            "storage-downloads",
                                            "Storage Downloads",
                                            ObjectStorageSpace.getDownloads(),
                                            "/min");
        metricsCollector.differentialMetric("storage_streams",
                                            "storage-streams",
                                            "Storage Streams",
                                            ObjectStorageSpace.getStreams(),
                                            "/min");
        metricsCollector.differentialMetric("storage_deliveries",
                                            "storage-deliveries",
                                            "Storage Deliveries",
                                            ObjectStorageSpace.getDeliveries(),
                                            "/min");
        metricsCollector.differentialMetric("storage_fallbacks",
                                            "storage-fallbacks",
                                            "Storage Fallbacks",
                                            ObjectStorageSpace.getFallbacks(),
                                            "/min");
        metricsCollector.differentialMetric("storage_client_errors",
                                            "storage-client-errors",
                                            "Storage Client Errors (4xx)",
                                            ObjectStorageSpace.getDeliveryClientFailures(),
                                            "/min");
        metricsCollector.differentialMetric("storage_server_errors",
                                            "storage-server-errors",
                                            "Storage Server Errors (5xx)",
                                            ObjectStorageSpace.getDeliveryServerFailures(),
                                            "/min");

        metricsCollector.differentialMetric("storage_replication_tasks",
                                            "storage-replication-tasks",
                                            "Storage Replication Tasks",
                                            replicationManager.getReplicationExecutionDuration().getCount(),
                                            "/min");
        metricsCollector.metric("storage_replication_duration",
                                "storage-replication-duration",
                                "Storage Replication Duration",
                                replicationManager.getReplicationExecutionDuration().getAndClear(),
                                "ms");

        metricsCollector.differentialMetric("storage_conversions",
                                            "storage-conversions",
                                            "Storage Conversions",
                                            conversionEngine.getConversionDuration().getCount(),
                                            "/min");
        metricsCollector.metric("storage_conversion_duration",
                                "storage-conversion-duration",
                                "Storage Conversion Duration",
                                conversionEngine.getConversionDuration().getAndClear(),
                                "ms");
    }

    @Override
    protected void computeLoadInfos(Consumer<LoadInfo> consumer) {

        replicationManager.getReplicationTaskStorage().ifPresent(replicationTaskStorage -> {
            consumer.accept(new LoadInfo("storage-total-replication-tasks",
                                         "Total Replication Tasks",
                                         replicationTaskStorage.countTotalNumberOfTasks()));
            consumer.accept(new LoadInfo("storage-executable-replication-tasks",
                                         "Executable Replication Tasks",
                                         replicationTaskStorage.countNumberOfExecutableTasks()));
            consumer.accept(new LoadInfo("storage-scheduled-replication-tasks",
                                         "Scheduled Replication Tasks",
                                         replicationTaskStorage.countNumberOfScheduledTasks()));
        });
    }

    @Override
    public String getLabel() {
        return "Storage";
    }
}
