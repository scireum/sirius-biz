/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.i5;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.metrics.Metric;
import sirius.kernel.health.metrics.MetricProvider;
import sirius.kernel.health.metrics.MetricsCollector;

/**
 * Provides monitoring data for the i5 connector(s).
 */
@Register
public class I5MetricProvider implements MetricProvider {

    @Part
    private I5Connector i5;

    @Override
    public void gather(MetricsCollector collector) {
        if (!i5.pools.isEmpty()) {
            collector.differentialMetric("i5_use", "i5-uses", "I5 Uses", i5.borrows.getCount(), Metric.UNIT_PER_MIN);
            collector.differentialMetric("i5_calls", "i5-calls", "I5 Calls", i5.calls.getCount(), Metric.UNIT_PER_MIN);
            collector.metric("i5_call_duration",
                             "i5-call-duration",
                             "I5 Call Duration",
                             i5.callDuration.getAndClear(),
                             Metric.UNIT_MS);
            collector.metric("i5_call_utilization",
                                                              "i5-call-utilization",
                                                              "I5 Call Utilization",
                                                              i5.callUtilization.getAndClear(),
                                                              Metric.UNIT_MS);
        }
    }
}
