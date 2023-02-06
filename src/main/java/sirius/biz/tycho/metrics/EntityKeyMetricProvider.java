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
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;

import java.util.function.Supplier;

/**
 * Provides a base implementation which provides key metrics for an entity of a given type.
 *
 * @param <E> the generic entity target type
 * @see KeyMetric
 */
public abstract class EntityKeyMetricProvider<E extends Entity> extends BasicKeyMetricProvider {

    @Part
    protected Mixing mixing;

    /**
     * Returns the type of entities for which this provider is responsible.
     *
     * @return the target type for which key metrics are provided
     */
    protected abstract Class<E> getTargetType();

    @SuppressWarnings("unchecked")
    @Override
    public void collectKeyMetrics(String target, Supplier<MetricDescription> metricFactory) {
        Tuple<String, String> targetName = Mixing.splitUniqueName(target);
        if (Mixing.getNameForType(getTargetType()).equals(targetName.getFirst())) {
            E entity = (E) mixing.getDescriptor(getTargetType()).getMapper().resolve(target).orElse(null);
            if (entity != null) {
                collectKeyMetrics(entity, metricFactory);
            }
        }
    }

    /**
     * Collects all key metrics available for the given entity.
     *
     * @param entity        the entity to provide key metrics for
     * @param metricFactory a factory which is used to create {@link MetricDescription metric descriptions}
     */
    protected abstract void collectKeyMetrics(E entity, Supplier<MetricDescription> metricFactory);

    @SuppressWarnings("unchecked")
    @Override
    public KeyMetric resolveKeyMetric(String target, String metricName) {
        E entity = (E) mixing.getDescriptor(getTargetType()).getMapper().resolve(target).orElse(null);
        if (entity != null) {
            return resolveKeyMetric(entity, metricName);
        } else {
            throw new IllegalArgumentException(Strings.apply("Cannot resolve entity: %s", target));
        }
    }

    /**
     * Actually emits the {@link KeyMetric} for the given entity and metric name.
     *
     * @param entity     the target to compute the metric for
     * @param metricName the metric to compute (as indicated by {@link MetricDescription#withMetricName(String)}).
     * @return the metric data for the requested parameters.
     */
    protected abstract KeyMetric resolveKeyMetric(E entity, String metricName);
}
