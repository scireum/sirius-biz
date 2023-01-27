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

import java.util.function.Consumer;

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

    protected abstract boolean isGloballyVisible();

    @Override
    public void collectKeyMetrics(String target, Consumer<MetricDescription> descriptionConsumer) {
        if (Mixing.getNameForType(getTargetType()).equals(target) || (isGloballyVisible()
                                                                      && GlobalChartProvider.GLOBAL_TARGET.equals(target))) {
            collectKeyMetrics(descriptionConsumer);
        }
    }

    /**
     * Collects the actual key metrics available for the {@link #getTargetType()}.
     *
     * @param descriptionConsumer the consumer used to collect all key metrics
     */
    protected abstract void collectKeyMetrics(Consumer<MetricDescription> descriptionConsumer);

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
