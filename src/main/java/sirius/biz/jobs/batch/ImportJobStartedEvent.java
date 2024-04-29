/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.process.ProcessContext;
import sirius.biz.scripting.ScriptableEvent;

/**
 * Signals that an {@link ImportJob} has been started.
 * <p>
 * This might be used to perform some preparation steps like downloading the file to import into the work directory.
 *
 * @param <T> the import job type being started
 */
public class ImportJobStartedEvent<T> extends ScriptableEvent {

    private final ImportJob job;
    private final ProcessContext process;

    /**
     * Creates a new event for the given job and process.
     *
     * @param job     the job being started
     * @param process the process which has been started for the job
     */
    public ImportJobStartedEvent(ImportJob job, ProcessContext process) {
        super();
        this.job = job;
        this.process = process;
    }

    public ImportJob getJob() {
        return job;
    }

    public ProcessContext getProcess() {
        return process;
    }

    @SuppressWarnings("unchecked")

    @Override
    public String toString() {
        return "ImportJobStartedEvent: " + job.getClass().getName() + " as Process: " + process.getProcessId();
    }
}
