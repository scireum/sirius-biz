/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

public class DefaultBatchProcessTaskExecutor extends BatchProcessTaskExecutor {

    @Override
    public String queueName() {
        return "jobs";
    }
}
