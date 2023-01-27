/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.metrics;

import java.util.function.Consumer;

/**
 * Provides charts which are shown on the main statistics dashboard as well as the main dashboard of Tycho.
 *
 * @see KeyMetric
 */
public abstract class GlobalKeyMetricProvider extends BasicKeyMetricProvider {

    @Override
    public void collectKeyMetrics(String target, Consumer<MetricDescription> descriptionConsumer) {
        if (GlobalChartProvider.GLOBAL_TARGET.equals(target)) {
            collectKeyMetrics(descriptionConsumer);
        }
    }

    /**
     * Collects the actual key metrics.
     *
     * @param descriptionConsumer the consumer used to collect all metrics
     */
    protected abstract void collectKeyMetrics(Consumer<MetricDescription> descriptionConsumer);

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
