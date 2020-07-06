/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.biz.importer.BaseImportHandler;
import sirius.biz.importer.EntityImportHandlerExtender;
import sirius.biz.importer.ImporterContext;
import sirius.biz.importer.format.FieldDefinition;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.MultiMap;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Provides a convenient way of semi-automatically exporting metrics for an entity.
 * <p>
 * To extend the export of an entity, a {@link MetricExporter} has to be created and
 * registered. Most probably this can be the {@link MonthlyMetricComputer} (or the like)
 * itself. This can now decide which metric to make exportable (or even place them
 * in the default export).
 */
@Register
public class MetricsImportHandlerExtender implements EntityImportHandlerExtender {

    private static final int DEFAULT_PRIORITY_OFFSET = 500;
    private static final String METRIC_FIELD_PREFIX = "metric-";

    @Parts(MetricExporter.class)
    private PartCollection<MetricExporter<?>> exporters;

    private MultiMap<Class<?>, MetricExportInfo> exportableMetrics;

    private Collection<MetricExportInfo> fetchMetricsForType(EntityDescriptor descriptor) {
        if (exportableMetrics == null) {
            MultiMap<Class<?>, MetricExportInfo> result = MultiMap.createOrdered();
            for (MetricExporter<?> exporter : exporters) {
                exporter.collectExportableMetrics((info, extractor) -> {
                    info.withExtractor(extractor);
                    result.put(exporter.getType(), info);
                });
            }

            exportableMetrics = result;
        }

        return exportableMetrics.get(descriptor.getType());
    }

    @Nullable
    @Override
    public <E extends BaseEntity<?>> Function<? super E, ?> createExtractor(BaseImportHandler<E> handler,
                                                                            EntityDescriptor descriptor,
                                                                            ImporterContext context,
                                                                            String fieldToExport) {
        for (MetricExportInfo metricInfo : fetchMetricsForType(descriptor)) {
            if (Strings.areEqual(METRIC_FIELD_PREFIX + metricInfo.getName(), fieldToExport)) {
                return entity -> metricInfo.getExtractor().applyAsInt(entity);
            }
        }

        return null;
    }

    @Override
    public FieldDefinition resolveCustomField(BaseImportHandler<? extends BaseEntity<?>> handler,
                                              EntityDescriptor descriptor,
                                              String field) {
        for (MetricExportInfo metricInfo : fetchMetricsForType(descriptor)) {
            if (Strings.areEqual(METRIC_FIELD_PREFIX + metricInfo.getName(), field)) {
                return FieldDefinition.numericField(field).withLabel(metricInfo.getLabel()).addAlias(field);
            }
        }

        return null;
    }

    @Override
    public void collectDefaultExportableMappings(BaseImportHandler<? extends BaseEntity<?>> handler,
                                                 EntityDescriptor descriptor,
                                                 BiConsumer<Integer, Mapping> collector) {
        AtomicInteger priorityGenerator = new AtomicInteger(DEFAULT_PRIORITY_OFFSET);
        fetchMetricsForType(descriptor).stream()
                                       .filter(MetricExportInfo::isDefaultExport)
                                       .forEach(metric -> collector.accept(metric.getEffectivePriority(priorityGenerator),
                                                                           Mapping.named(METRIC_FIELD_PREFIX
                                                                                         + metric.getName())));
    }

    @Override
    public void collectExportableMappings(BaseImportHandler<? extends BaseEntity<?>> handler,
                                          EntityDescriptor descriptor,
                                          BiConsumer<Integer, Mapping> collector) {
        AtomicInteger priorityGenerator = new AtomicInteger(DEFAULT_PRIORITY_OFFSET);
        fetchMetricsForType(descriptor).stream()
                                       .filter(info -> !info.isDefaultExport())
                                       .forEach(metric -> collector.accept(metric.getEffectivePriority(priorityGenerator),
                                                                           Mapping.named(METRIC_FIELD_PREFIX
                                                                                         + metric.getName())));
    }
}
