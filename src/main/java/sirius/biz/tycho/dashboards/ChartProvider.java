/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Named;

import java.util.function.Consumer;

/**
 * Provides one or more charts for a <tt>metrics dashboard</tt>.
 * <p>
 * Providers must be {@link sirius.kernel.di.std.Register registered} to become visible to the framework. Note
 * that subclasses like {@link GlobalChartProvider}, {@link EntityChartProvider} or {@link EntitiesChartProvider}
 * provide some helper methods and also implement access control based on {@link sirius.web.security.Permission}.
 */
@AutoRegister
public interface ChartProvider extends Named {

    /**
     * Determines if the current user is eligible for the charts provided by this.
     *
     * @return <tt>true</tt> if the current user may access the charts of this provider, <tt>false</tt> otherwise
     */
    boolean isAccessible();

    /**
     * Collects all charts available for the given target.
     * <p>
     * Note that this doesn't actually emit the charts, but only creates {@link MetricDescription descriptors}.
     * These are used by the UI to request the chart data asynchronously.
     *
     * @param target              the target to collect the charts for
     * @param descriptionConsumer the consumer which collects the emitted metric descriptions
     */
    void collectCharts(String target, Consumer<MetricDescription> descriptionConsumer);

    /**
     * Actually emits the chart data for the given target and chart name.
     *
     * @param target    the target to compute the chart for
     * @param chartName the chart to compute (as indicated by {@link MetricDescription#withMetricName(String)}).
     * @return the chart data for the requested parameters.
     */
    Chart resolveChart(String target, String chartName);
}
