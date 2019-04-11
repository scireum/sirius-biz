/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.biz.analytics.scheduler.AnalyticsBatchExecutor;

/**
 * Represents the executor used to execute batches of guaranteed tasks used to compute metrics.
 */
public class MetricsGuaranteedBatchExecutor extends AnalyticsBatchExecutor {

    public static final String QUEUE_METRICS_BATCH = "metrics-batch";

    @Override
    public String queueName() {
        return QUEUE_METRICS_BATCH;
    }
}
