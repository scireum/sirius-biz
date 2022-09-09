/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.jobs.JobCategory;
import sirius.biz.process.PersistencePeriod;

/**
 * Provides a base implementation for batch jobs which are executed by the {@link ExportBatchProcessTaskExecutor}.
 */
public abstract class ExportBatchProcessFactory extends BatchProcessJobFactory {

    /**
     * Provides an executor for batch jobs which are all put in the priorized queue named "export-jobs".
     * <p>
     * This should be used for jobs in {@link sirius.biz.jobs.JobCategory#CATEGORY_EXPORT}.
     */
    public static class ExportBatchProcessTaskExecutor extends BatchProcessTaskExecutor {

        /**
         * Contains the queue name being used by export jobs.
         */
        public static final String QUEUE_NAME = "export-jobs";

        @Override
        public String queueName() {
            return QUEUE_NAME;
        }
    }

    @Override
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return ExportBatchProcessTaskExecutor.class;
    }

    @Override
    public String getCategory() {
        return JobCategory.CATEGORY_EXPORT;
    }

    @Override
    public String getIcon() {
        return "fa fa-download";
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.FOURTEEN_DAYS;
    }
}
