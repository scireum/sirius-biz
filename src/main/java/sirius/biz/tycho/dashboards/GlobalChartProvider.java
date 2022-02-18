/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import java.util.function.Consumer;

/**
 * Provides charts which are shown on the main statistics dashboard of Tycho.
 *
 * @see Chart
 */
public abstract class GlobalChartProvider extends BasicChartProvider {

    public static final String GLOBAL_TARGET = "-";

    @Override
    public void collectCharts(String target, Consumer<MetricDescription> descriptionConsumer) {
        if (GLOBAL_TARGET.equals(target)) {
            collectCharts(descriptionConsumer);
        }
    }

    /**
     * Collects the actual charts.
     *
     * @param descriptionConsumer the consumer used to collect all charts
     */
    protected abstract void collectCharts(Consumer<MetricDescription> descriptionConsumer);

    @Override
    public Chart resolveChart(String target, String chartName) {
        return resolveChart(chartName);
    }

    /**
     * Actually emits the {@link Chart} data for the given chart name.
     *
     * @param chartName the name of the chart to resolve
     * @return the requested chart
     */
    protected abstract Chart resolveChart(String chartName);
}
