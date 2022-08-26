/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.jobs.StandardJobCategories;

/**
 * Provides a base implementation for batch jobs which are executed by the {@link DefaultBatchProcessTaskExecutor}.
 */
public abstract class DefaultBatchProcessFactory extends BatchProcessJobFactory {

    /**
     * Provides a default executor for batch jobs which are all put in the priorized queue named "jobs".
     * <p>
     * This is probably fine for miscellaneous jobs ({@link StandardJobCategories#CATEGORY_MISC}
     * but it is recommended to define purpose built executors and queues.
     */
    public static class DefaultBatchProcessTaskExecutor extends BatchProcessTaskExecutor {

        @Override
        public String queueName() {
            return "jobs";
        }
    }

    /**
     * Provides a default just like {@link DefaultBatchProcessTaskExecutor} but only executes the jobs partially.
     * <p>
     * This can be used by miscellaneous jobs which start a process that is finished / completed elsewhere.
     */
    public static class DefaultPartialBatchProcessTaskExecutor extends BatchProcessTaskExecutor {

        @Override
        public String queueName() {
            return "jobs";
        }

        @Override
        protected boolean shouldExecutePartially() {
            return true;
        }
    }

    @Override
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return DefaultBatchProcessTaskExecutor.class;
    }
}
