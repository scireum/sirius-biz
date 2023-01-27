/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.metrics;

import sirius.db.mixing.Entity;
import sirius.db.mixing.Mixing;

import java.util.function.Supplier;

/**
 * Provides a base implementation which provides key metrics for an entity class.
 *
 * @see KeyMetric
 */
public abstract class EntitiesKeyMetricProvider extends BasicKeyMetricProvider {

    /**
     * Specifies the type of entities for which key metrics are provided.
     *
     * @return the entity target class of this provider
     */
    protected abstract Class<? extends Entity> getTargetType();

    /**
     * Determines if the metrics generated by this provider are also globally visible.
     *
     * @return <tt>true</tt> if the metrics are globally visible (on the dashboard), <tt>false</tt> otherwise
     */
    protected abstract boolean isGloballyVisible();

    @Override
    public void collectKeyMetrics(String target, Supplier<MetricDescription> metricFactory) {
        if (Mixing.getNameForType(getTargetType()).equals(target) || (isGloballyVisible()
                                                                      && GlobalKeyMetricProvider.GLOBAL_TARGET.equals(
                target))) {
            collectKeyMetrics(metricFactory);
        }
    }

    /**
     * Collects the actual key metrics available for the {@link #getTargetType()}.
     *
     * @param metricFactory a factory which is used to create {@link MetricDescription metric descriptions}
     */
    protected abstract void collectKeyMetrics(Supplier<MetricDescription> metricFactory);

    @Override
    public KeyMetric resolveKeyMetric(String targetName, String metricName) {
        return resolveKeyMetric(metricName);
    }

    /**
     * Actually emits the {@link KeyMetric} for the metric name.
     *
     * @param metricName the name of the key metric to resolve
     * @return the requested metric
     */
    protected abstract KeyMetric resolveKeyMetric(String metricName);
}
