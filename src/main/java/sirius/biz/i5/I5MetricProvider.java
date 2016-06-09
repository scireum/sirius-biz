package sirius.biz.i5;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
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
            collector.differentialMetric("i5-use", "i5-uses", "I5 Uses", i5.borrows.getCount(), "/min");
            collector.differentialMetric("i5-calls", "i5-calls", "I5 Calls", i5.calls.getCount(), "/min");
            collector.metric("i5-call-duration", "I5 Call Duration", i5.callDuration.getAndClearAverage(), "ms");
            collector.metric("i5-call-utilization",
                             "I5 Call Utilization",
                             i5.callUtilization.getAndClearAverage(),
                             "ms");
        }
    }
}
