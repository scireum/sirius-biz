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

public interface KeyMetricProvider<E> extends Named {

    Class<E> getTargetType();

    void collectKeyMetrics(E target, Consumer<MetricDescription> descriptionConsumer);

    KeyMetric resolveKeyMetric(String targetName, String metricName);
}
