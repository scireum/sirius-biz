/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.process.ProcessContext;

import java.io.Closeable;
import java.io.IOException;

/**
 * Provides a base class for jobs executed by subclasses of {@link BatchProcessJobFactory}.
 * <p>
 * As factories are static, jobs can be thought of parameter objects which are created for each job execution
 * and therefore carry all the satet required for it.
 */
public abstract class BatchJob implements Closeable {

    protected ProcessContext process;

    /**
     * Creates a new batch job for the given batch process.
     * <p>
     * As a batch job is created per execution, subclasses can define fields and fill those from parameters
     * defined by their factory.
     *
     * @param process the context in which the process will be executed
     */
    protected BatchJob(ProcessContext process) {
        this.process = process;
    }

    /**
     * Performs the execution of the task.
     * <p>
     * As a batch job is created per execution, this method can branch into several other methods to keep the whole
     * process maintainable.
     *
     * @throws Exception in case on an error during execution
     */
    public abstract void execute() throws Exception;

    /**
     * Invoked independently of the outcome of {@link #execute()} to perform required cleanups.
     *
     * @throws IOException inherited by {@link Closeable} can be used to signal an error of any kind
     */
    @Override
    public void close() throws IOException {
        // may be overwritten by sub-classes
    }
}
