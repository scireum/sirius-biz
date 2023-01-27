/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.metrics;

import java.util.function.Supplier;

/**
 * Provides charts which are shown on the main statistics dashboard as well as the main dashboard of Tycho.
 *
 * @see KeyMetric
 */
public abstract class GlobalKeyMetricProvider extends BasicKeyMetricProvider {

    /**
     * Defines the placeholder target to be used for globally visible metrics.
     */
    public static final String GLOBAL_TARGET = "-";

    @Override
    public void collectKeyMetrics(String target, Supplier<MetricDescription> metricFactory) {
        if (GLOBAL_TARGET.equals(target)) {
            collectKeyMetrics(metricFactory);
        }
    }

    /**
     * Collects the actual key metrics.
     *
     * @param metricFactory a factory which is used to create {@link MetricDescription metric descriptions}
     */
    protected abstract void collectKeyMetrics(Supplier<MetricDescription> metricFactory);

    @Override
    public KeyMetric resolveKeyMetric(String target, String metricName) {
        return resolveKeyMetric(metricName);
    }

    /**
     * Actually emits the {@link KeyMetric} for the metric name.
     *
     * @param metricName the name of the metric to resolve
     * @return the requested metric
     */
    protected abstract KeyMetric resolveKeyMetric(String metricName);
}
