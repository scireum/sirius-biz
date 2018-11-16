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
 * Provides a base implementation for batch jobs which are executed by the {@link DefaultBatchProcessTaskExecutor}.
 */
public abstract class DefaultBatchProcessFactory extends BatchProcessJobFactory {

    @Override
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return DefaultBatchProcessTaskExecutor.class;
    }
}
