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
 * Provides a base implementation for batch jobs which are executed by the {@link CheckBatchProcessTaskExecutor}.
 */
public abstract class CheckBatchProcessFactory extends BatchProcessJobFactory {

    /**
     * Provides an executor for batch jobs which are all put in the prioritized queue named "check-jobs".
     */
    public static class CheckBatchProcessTaskExecutor extends BatchProcessTaskExecutor {

        @Override
        public String queueName() {
            return "check-jobs";
        }
    }

    @Override
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return CheckBatchProcessTaskExecutor.class;
    }

    @Override
    public String getIcon() {
        return "fa-regular fa-check-square";
    }
}
