/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.cluster.work.DistributedTaskExecutor;

/**
 * Provides a base implementation for batch jobs which are executed by the {@link ReportBatchProcessTaskExecutor}.
 * <p>
 * Note that for simple batch reports {@link SimpleReportBatchProcessFactory} can be used.
 */
public abstract class ReportBatchProcessFactory extends BatchProcessJobFactory {

    /**
     * Provides an executor for batch jobs which are all put in the prioritized queue named "report-jobs".
     */
    public static class ReportBatchProcessTaskExecutor extends BatchProcessTaskExecutor {

        @Override
        public String queueName() {
            return "report-jobs";
        }
    }

    @Override
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return ReportBatchProcessTaskExecutor.class;
    }

    @Override
    public String getIcon() {
        return "fas fa-chart-line";
    }
}
