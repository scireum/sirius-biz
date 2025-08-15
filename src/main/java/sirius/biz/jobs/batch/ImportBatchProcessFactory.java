/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;

import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which are executed by the {@link ImportBatchProcessTaskExecutor}.
 */
public abstract class ImportBatchProcessFactory extends BatchProcessJobFactory {

    /**
     * Provides an executor for batch jobs which are all put in the prioritized queue named "import-jobs".
     */
    public static class ImportBatchProcessTaskExecutor extends BatchProcessTaskExecutor {

        @Override
        public String queueName() {
            return "import-jobs";
        }
    }

    @Override
    protected abstract ImportJob createJob(ProcessContext process);

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        // Nothing to collect by default...
    }

    @Override
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return ImportBatchProcessTaskExecutor.class;
    }

    @Override
    public String getIcon() {
        return "fa-solid fa-upload";
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.SIX_YEARS;
    }
}
