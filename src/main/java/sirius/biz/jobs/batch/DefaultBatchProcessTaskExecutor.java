/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

/**
 * Provides a default executor for batch jobs which are all put in the priorized queue named "jobs".
 * <p>
 * This is probably fine for miscellaneous jobs but it is recommended to define purpose built executors and queues.
 */
public class DefaultBatchProcessTaskExecutor extends BatchProcessTaskExecutor {

    @Override
    public String queueName() {
        return "jobs";
    }
}
