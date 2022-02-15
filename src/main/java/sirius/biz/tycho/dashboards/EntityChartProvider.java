/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.db.mixing.Entity;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;

import java.util.function.Consumer;

/**
 * Provides a base implementation which provides charts for an entity of a given type.
 *
 * @param <E> the generic entity target type
 */
public abstract class EntityChartProvider<E extends Entity> extends BasicChartProvider {

    @Part
    protected Mixing mixing;

    /**
     * Returns the type of entities for which this provider is responsible.
     *
     * @return the target type for which charts are provided
     */
    protected abstract Class<E> getTargetType();

    @SuppressWarnings("unchecked")
    @Override
    public void collectCharts(String target, Consumer<MetricDescription> descriptionConsumer) {
        Tuple<String, String> targetName = Mixing.splitUniqueName(target);
        if (Mixing.getNameForType(getTargetType()).equals(targetName.getFirst())) {
            E entity = (E) mixing.getDescriptor(getTargetType()).getMapper().resolve(target).orElse(null);
            if (entity != null) {
                collectCharts(entity, descriptionConsumer);
            }
        }
    }

    /**
     * Collects all charts available for the given entity.
     *
     * @param entity              the entity to provide charts for
     * @param descriptionConsumer the consumer which collects the emitted metric descriptions
     */
    protected abstract void collectCharts(E entity, Consumer<MetricDescription> descriptionConsumer);

    @SuppressWarnings("unchecked")
    @Override
    public Chart resolveChart(String target, String chartName) {
        E entity = (E) mixing.getDescriptor(getTargetType()).getMapper().resolve(target).orElse(null);
        if (entity != null) {
            return resolveChart(entity, chartName);
        } else {
            throw new IllegalArgumentException(Strings.apply("Cannot resolve entity: %s", target));
        }
    }

    /**
     * Actually emits the chart data for the given entity and chart name.
     *
     * @param entity    the target to compute the chart for
     * @param chartName the chart to compute (as indicated by {@link MetricDescription#withMetricName(String)}).
     * @return the chart data for the requested parameters.
     */
    protected abstract Chart resolveChart(E entity, String chartName);
}
