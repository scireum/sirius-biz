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

import java.util.function.Consumer;

/**
 * Provides a base implementation which provides charts for an entity class.
 */
public abstract class EntitiesChartProvider extends BasicChartProvider {

    /**
     * Provides a base implementation which provides charts for an entity class.
     */
    protected abstract Class<? extends Entity> getTargetType();

    @Override
    public void collectCharts(String target, Consumer<MetricDescription> descriptionConsumer) {
        if (Mixing.getNameForType(getTargetType()).equals(target)) {
            collectCharts(descriptionConsumer);
        }
    }

    /**
     * Collects the actual charts available for the {@link #getTargetType()}.
     *
     * @param descriptionConsumer the consumer used to collect all charts
     */
    protected abstract void collectCharts(Consumer<MetricDescription> descriptionConsumer);

    @Override
    public Chart resolveChart(String targetName, String chartName) {
        return resolveChart(chartName);
    }

    /**
     * Resolves the given chart.
     *
     * @param chartName the name of the chart to resolve
     * @return the requested chart
     */
    protected abstract Chart resolveChart(String chartName);
}
