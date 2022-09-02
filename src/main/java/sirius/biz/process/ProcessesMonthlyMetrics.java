/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.analytics.metrics.jdbc.SQLMonthlyGlobalMetricComputer;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
    protected void compute(LocalDate date, LocalDateTime startOfPeriod, LocalDateTime endOfPeriod, boolean pastDate)
            throws Exception {
        Tuple<Integer, Integer> processMetrics =
                processes.computeProcessMetrics(startOfPeriod.toLocalDate(), endOfPeriod.toLocalDate(), null);
        metrics.updateGlobalMonthlyMetric(METRIC_NUM_PROCESSES, date, processMetrics.getFirst());
        metrics.updateGlobalMonthlyMetric(METRIC_PROCESS_DURATION, date, processMetrics.getSecond());
    }
}
