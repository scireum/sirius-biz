/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.process.ProcessContext;
import sirius.biz.scripting.ScriptableEvent;
import sirius.kernel.async.TaskContext;

/**
 * Signals that a batch job, started by a {@link BatchProcessJobFactory}, has finished.
 * <p>
 * The event is fired by the {@link BatchProcessTaskExecutor} once
 * {@link BatchProcessJobFactory#executeTask(ProcessContext)} has returned or thrown. For the standard implementation
 * of that method, this means that the {@link BatchJob} has been executed and {@linkplain BatchJob#close() closed}.
 * Factories which override it are responsible themselves for what has been persisted at that point. The event is
 * still fired within the process, but before the process is marked as completed.
 * <p>
 * It is only fired for factories which {@linkplain BatchProcessJobFactory#enableScriptableEvents() enable scriptable
 * events}, and not for jobs which are executed partially (see {@link BatchProcessTaskExecutor#shouldExecutePartially()}).
 * <p>
 * As the job has already finished, the event cannot be {@linkplain #abort() aborted}, and the outcome of its handlers
 * has no effect on the outcome of the job.
 * <p>
 * This might be used to perform follow-up steps like notifying an external system or starting another job.
 * <p>
 * The event is fired for every job, so a handler which is only interested in specific jobs filters them by the
 * {@linkplain BatchProcessJobFactory#getName() name} of their factory. This is preferred over a typed event, as the
 * name is stable and independent of the class implementing the factory:
 * <pre>{@code
 * registry.registerHandler(sirius.biz.jobs.batch.BatchJobFinishedEvent.class, |event| {
 *     if (event.getFactory().getName() == "import-products" && !event.isJobErroneous()) {
 *         // perform the follow-up steps...
 *     }
 * });
 * }</pre>
 */
public class BatchJobFinishedEvent extends ScriptableEvent {

    private final BatchProcessJobFactory factory;
    private final ProcessContext process;

    /**
     * Creates a new event for the given factory and process.
     *
     * @param factory the factory which has executed the job
     * @param process the process in which the job has been executed
     */
    public BatchJobFinishedEvent(BatchProcessJobFactory factory, ProcessContext process) {
        super();
        this.factory = factory;
        this.process = process;
    }

    public BatchProcessJobFactory getFactory() {
        return factory;
    }

    public ProcessContext getProcess() {
        return process;
    }

    /**
     * Determines if the job ended erroneously.
     * <p>
     * This is the case if the job was aborted by an exception, logged an error into its process or was
     * {@linkplain #isJobCanceled() canceled}. This matches the state the process will be marked with once it is
     * completed, which {@link ProcessContext#isErroneous()} doesn't reflect yet for canceled jobs.
     * <p>
     * Note that this is unrelated to {@link #isFailed()}, which reports a failure of an event handler.
     *
     * @return <tt>true</tt> if the job ended erroneously, <tt>false</tt> if it completed successfully
     */
    public boolean isJobErroneous() {
        return process.isErroneous() || isJobCanceled();
    }

    /**
     * Determines if the job was canceled before it could finish.
     * <p>
     * This is the case if a user canceled the process or if the node is shutting down.
     *
     * @return <tt>true</tt> if the job was canceled, <tt>false</tt> otherwise
     */
    public boolean isJobCanceled() {
        return !TaskContext.get().isActive();
    }

    /**
     * Rejects aborting this event, as the job has already finished and there is nothing left to abort.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void abort() {
        throw new UnsupportedOperationException(
                "A BatchJobFinishedEvent cannot be aborted as the job has already finished.");
    }

    @Override
    public String toString() {
        return "BatchJobFinishedEvent: " + factory.getName() + " as Process: " + process.getProcessId();
    }
}
