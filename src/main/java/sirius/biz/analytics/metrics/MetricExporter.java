/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.AutoRegister;

import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/**
 * Injects metrics into the entity export of the referenced entity.
 * <p>
 * Classes implementing this, must be {@link sirius.kernel.di.std.Register registered} to be visible to the framework.
 * Most probably the metric computer (e.g. {@link MonthlyMetricComputer}) itself can also implement this interface.
 *
 * @param <E> the entity type to extend
 */
@AutoRegister
public interface MetricExporter<E extends BaseEntity<?>> {

    /**
     * Returns the entity type to enrich.
     *
     * @return the type for which additional metrics should be exported
     */
    Class<E> getType();

    /**
     * Collects all metrics to inject into the output.
     * <p>
     * Use {@link MetricExportInfo#markAsDefaultExport()} to add a metric to the default export and
     * {@link MetricExportInfo#withPriority(int)} to supply a custom field position within the export.
     * <p>
     * The <tt>ToIntFunction</tt> should most probably be implemented using {@link Metrics#query()} calling
     * {@link MetricQuery#currentValue()} or {@link MetricQuery#lastValue()}.
     *
     * @param metricConsumer a customer to provide with all the metrics to export.
     */
    void collectExportableMetrics(BiConsumer<MetricExportInfo, ToIntFunction<E>> metricConsumer);
}
