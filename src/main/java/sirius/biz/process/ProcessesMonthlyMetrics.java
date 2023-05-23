/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.analytics.metrics.ComputeParameters;
import sirius.biz.analytics.metrics.jdbc.SQLMonthlyGlobalMetricComputer;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Provides some simple global metrics for the {@link Processes} framework.
 * <p>
 * Note that we use a {@link SQLMonthlyGlobalMetricComputer} here, but this will still work fine on MongoDB based
 * systems, as our metric hasn't any dependencies to any other entities.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
public class ProcessesMonthlyMetrics extends SQLMonthlyGlobalMetricComputer {

    /**
     * Contains the number of processes that completed on a given day.
     */
    public static final String METRIC_NUM_PROCESSES = "num-processes";

    /**
     * Contains the total computation time (in minutes) for the completed processes on a given day.
     */
    public static final String METRIC_PROCESS_DURATION = "process-duration";

    @Part
    private Processes processes;

    @Override
    protected void compute(ComputeParameters parameters) throws Exception {
        Tuple<Integer, Integer> processMetrics =
                processes.computeProcessMetrics(parameters.startOfPeriodAsDate(), parameters.endOfPeriodAsDate(), null);
        metrics.updateGlobalMonthlyMetric(METRIC_NUM_PROCESSES, parameters.date(), processMetrics.getFirst());
        metrics.updateGlobalMonthlyMetric(METRIC_PROCESS_DURATION, parameters.date(), processMetrics.getSecond());
    }
}
