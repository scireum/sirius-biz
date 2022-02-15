/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.biz.analytics.scheduler.AnalyticsSchedulerExecutor;

/**
 * Represents the executor used to schedule batches of "best effort" tasks used to compute metrics.
 */
public class MetricsBestEffortSchedulerExecutor extends AnalyticsSchedulerExecutor {

    public static final String QUEUE_METRICS_BEST_EFFORT = "metrics-best-effort";

    @Override
    public String queueName() {
        return QUEUE_METRICS_BEST_EFFORT;
    }
}
