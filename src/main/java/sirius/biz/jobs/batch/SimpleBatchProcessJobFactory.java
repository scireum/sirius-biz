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
import sirius.biz.process.ProcessContext;

/**
 * Provides a base factory for very simple batch jobs, which do not need to wrap the execution in a {@link BatchJob}
 * but rather execute in a single method.
 * <p>
 * Note that this must only be used for very simple jobs with few or no parameters as no state must be stored in the
 * factory itself.
 * <p>
 * For more complex jobs which need to keep their parameters in fields and which also might want to split their
 * logic into several methods, subclass {@link BatchProcessJobFactory} (or its pre-defined sublcasses) and provide
 * a custom {@link BatchJob}.
 */
public abstract class SimpleBatchProcessJobFactory extends BatchProcessJobFactory {

    @Override
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return DefaultBatchProcessFactory.DefaultBatchProcessTaskExecutor.class;
    }

    @Override
    protected BatchJob createJob(ProcessContext process) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected final void executeTask(ProcessContext process) throws Exception {
        logParameters(process);
        execute(process);
    }

    /**
     * Executes the task on the target node and covered within the execution context of a {@link Process}.
     *
     * @param process the context of the previously generated process to communicate with the outside world
     * @throws Exception in case of any error which should abort this job
     */
    protected abstract void execute(ProcessContext process) throws Exception;

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.THREE_MONTHS;
    }
}
