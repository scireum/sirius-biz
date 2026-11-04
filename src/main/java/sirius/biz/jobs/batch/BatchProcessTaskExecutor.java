/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.jobs.BasicJobFactory;
import sirius.biz.jobs.Jobs;
import sirius.biz.mailsubscriptions.JobCompletionMails;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.scripting.ScriptableEventHandler;
import sirius.biz.scripting.Scripting;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import tools.jackson.databind.node.ObjectNode;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

/**
 * Provides a base implementation for an executor which receives a scheduled task, created by a
 * {@link BatchProcessJobFactory}, resolves the attached process and invokes
 * {@link BatchProcessJobFactory#executeTask(ProcessContext)} to actually execute the job.
 */
public abstract class BatchProcessTaskExecutor implements DistributedTaskExecutor {

    @Part
    @Nullable
    private Processes processes;

    @Part
    private Jobs jobs;

    @Part
    @Nullable
    private JobCompletionMails jobCompletionMails;

    @Override
    public void executeWork(ObjectNode context) throws Exception {
        String factoryId = context.path(BatchProcessJobFactory.CONTEXT_JOB_FACTORY).asString(null);
        String processId = context.path(BatchProcessJobFactory.CONTEXT_PROCESS).asString(null);

        setupTaskContext(factoryId);

        if (shouldExecutePartially()) {
            processes.partiallyExecute(processId, process -> partiallyExecuteInProcess(factoryId, process));
        } else {
            processes.execute(processId, process -> executeInProcess(factoryId, process));
            notifyAboutCompletion(processId);
        }
    }

    /**
     * Loads the completed process and passes it to {@link JobCompletionMails}.
     * <p>
     * Does nothing if mail subscriptions are disabled. Errors are only logged, so that sending a mail never lets
     * the job itself fail.
     *
     * @param processId the id of the process which has just completed
     */
    private void notifyAboutCompletion(String processId) {
        if (jobCompletionMails == null) {
            return;
        }

        try {
            processes.fetchProcess(processId).ifPresent(jobCompletionMails::handleCompletedJob);
        } catch (Exception exception) {
            Exceptions.handle(Log.BACKGROUND, exception);
        }
    }

    /**
     * Determines if the process should be marked completed once the execution is done or if additional distributed
     * tasks have been scheduled.
     *
     * @return <tt>true</tt> if the process shouldn't be marked as completed, <tt>false</tt> otherwise
     */
    protected boolean shouldExecutePartially() {
        return false;
    }

    protected void executeInProcess(String factoryId, ProcessContext process) {
        process.markRunning();
        process.log(ProcessLog.info().withNLSKey("BatchProcessTaskExecutor.started"));
        BatchProcessJobFactory factory = null;
        try {
            factory = jobs.findFactory(factoryId, BatchProcessJobFactory.class);
            factory.executeTask(process);
        } catch (Exception exception) {
            process.handle(exception);
        } finally {
            if (factory != null) {
                fireFinishedEvent(factory, process);
            }
            if (process.isErroneous()) {
                process.log(ProcessLog.warn().withNLSKey("BatchProcessTaskExecutor.completedButFailed"));
            } else {
                process.log(ProcessLog.success().withNLSKey("BatchProcessTaskExecutor.completedSuccessfully"));
            }
        }
    }

    /**
     * Fires a {@link BatchJobFinishedEvent} if the factory {@linkplain BatchProcessJobFactory#enableScriptableEvents()
     * enables scriptable events}.
     * <p>
     * Errors of the event handlers are logged by the dispatcher itself. Anything else, including an {@link Error}, is
     * only logged here, as this runs in a <tt>finally</tt> block and must neither let the job fail nor replace an
     * exception which is already being propagated.
     *
     * @param factory the factory which has executed the job
     * @param process the process in which the job has been executed
     */
    @SuppressWarnings("java:S1181")
    @Explain("Runs in a finally block and must never replace the outcome of the job.")
    private void fireFinishedEvent(BatchProcessJobFactory factory, ProcessContext process) {
        if (!factory.enableScriptableEvents()) {
            return;
        }

        try {
            ScriptableEventHandler eventHandler = new ScriptableEventHandler();
            eventHandler.initializeEventDispatchers();
            eventHandler.handleEvent(new BatchJobFinishedEvent(factory, process));
        } catch (Throwable throwable) {
            Exceptions.handle(Scripting.LOG, throwable);
        }
    }

    protected void partiallyExecuteInProcess(String factoryId, ProcessContext process) {
        process.markRunning();
        process.log(ProcessLog.info().withNLSKey("BatchProcessTaskExecutor.started"));
        Watch watch = Watch.start();
        try {
            jobs.findFactory(factoryId, BatchProcessJobFactory.class).executeTask(process);
        } catch (Exception exception) {
            process.handle(exception);
            process.log(ProcessLog.warn().withNLSKey("BatchProcessTaskExecutor.completedButFailed"));
            process.markCompleted((int) watch.elapsed(TimeUnit.SECONDS, false));
        }
    }

    /**
     * Creates an appropriate task context just like <tt>BasicJobFactory.setupTaskContext()</tt> does for interactive
     * jobs.
     *
     * @param factoryId the name of the {@link sirius.biz.jobs.JobFactory} which will execute the job.
     */
    private void setupTaskContext(String factoryId) {
        TaskContext taskContext = TaskContext.get();
        taskContext.setSystem(BasicJobFactory.SYSTEM_JOBS);
        taskContext.setSubSystem(factoryId);
    }
}
