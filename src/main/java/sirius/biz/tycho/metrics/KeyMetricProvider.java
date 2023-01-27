/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.metrics;

import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Named;

import java.util.function.Supplier;

/**
 * Provides one or more key metrics for a <tt>metrics dashboard</tt>.
 * <p>
 * Providers must be {@link sirius.kernel.di.std.Register registered} to become visible to the framework. Note
 * that subclasses like {@link GlobalKeyMetricProvider}, {@link EntityKeyMetricProvider} or
 * {@link EntitiesKeyMetricProvider} provide some helper methods and also implement access control based on
 * {@link sirius.web.security.Permission}.
 *
 * @see KeyMetric
 */
@AutoRegister
public interface KeyMetricProvider extends Named {

    /**
     * Determines if the current user is eligible for the charts provided by this.
     *
     * @return <tt>true</tt> if the current user may access the charts of this provider, <tt>false</tt> otherwise
     */
    boolean isAccessible();

    /**
     * Collects all key metrics available for the given target.
     * <p>
     * Note that this doesn't actually emit the key metrics, but only creates {@link MetricDescription descriptors}.
     * These are used by the UI to request the key metric data asynchronously.
     *
     * @param target        the target to collect the key metrics for
     * @param metricFactory a factory which is used to create {@link MetricDescription metric descriptions}
     */
    void collectKeyMetrics(String target, Supplier<MetricDescription> metricFactory);

    /**
     * Actually emits the {@link KeyMetric} for the given target and metric name.
     *
     * @param target     the target to compute the key metric for
     * @param metricName the key metric to compute (as indicated by {@link MetricDescription#withMetricName(String)}).
     * @return the key metric for the requested parameters.
     */
    KeyMetric resolveKeyMetric(String target, String metricName);
}
