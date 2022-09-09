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

/**
 * Provides a base implementation for batch jobs which are executed by the {@link CheckBatchProcessTaskExecutor}.
 */
public abstract class CheckBatchProcessFactory extends BatchProcessJobFactory {

    /**
     * Provides an executor for batch jobs which are all put in the priorized queue named "check-jobs".
     * <p>
     * This should be used for jobs in {@link sirius.biz.jobs.JobCategory#CATEGORY_CHECK}.
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
    public String getCategory() {
        return JobCategory.CATEGORY_CHECK;
    }

    @Override
    public String getIcon() {
        return "far fa-check-square";
    }


}
