/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.process.PersistencePeriod;

/**
 * Provides a base implementation for batch jobs which are executed by the {@link ExportBatchProcessTaskExecutor}.
 */
public abstract class ExportBatchProcessFactory extends BatchProcessJobFactory {

    /**
     * Provides an executor for batch jobs which are all put in the prioritized queue named "export-jobs".
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
    public String getIcon() {
        return "fa-solid fa-download";
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.FOURTEEN_DAYS;
    }
}
