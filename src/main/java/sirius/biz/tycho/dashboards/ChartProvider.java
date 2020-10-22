/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.kernel.di.std.Named;

import java.util.function.Consumer;

public interface ChartProvider<E> extends Named {

    Class<E> getTargetType();

    void collectCharts(E target, Consumer<MetricDescription> descriptionConsumer);

    Chart resolveChart(String targetName, String chartName);
}
