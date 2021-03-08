/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.jobs.BasicJobFactory;
import sirius.biz.jobs.Jobs;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.kernel.async.TaskContext;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;

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

    @Override
    public void executeWork(JSONObject context) throws Exception {
        String factoryId = context.getString(BatchProcessJobFactory.CONTEXT_JOB_FACTORY);

        setupTaskContext(factoryId);

        if (shouldExecutePartially()) {
            processes.partiallyExecute(context.getString(BatchProcessJobFactory.CONTEXT_PROCESS),
                                       process -> partiallyExecuteInProcess(factoryId, process));
        } else {
            processes.execute(context.getString(BatchProcessJobFactory.CONTEXT_PROCESS),
                              process -> executeInProcess(factoryId, process));
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
        process.log(ProcessLog.info().withNLSKey("BatchProcessTaskExecutor.started"));
        try {
            jobs.findFactory(factoryId, BatchProcessJobFactory.class).executeTask(process);
        } catch (Exception e) {
            process.handle(e);
        } finally {
            if (process.isErroneous()) {
                process.log(ProcessLog.warn().withNLSKey("BatchProcessTaskExecutor.completedButFailed"));
            } else {
                process.log(ProcessLog.success().withNLSKey("BatchProcessTaskExecutor.completedSuccessfully"));
            }
        }
    }

    protected void partiallyExecuteInProcess(String factoryId, ProcessContext process) {
        process.log(ProcessLog.info().withNLSKey("BatchProcessTaskExecutor.started"));
        try {
            jobs.findFactory(factoryId, BatchProcessJobFactory.class).executeTask(process);
        } catch (Exception e) {
            process.handle(e);
            process.log(ProcessLog.warn().withNLSKey("BatchProcessTaskExecutor.completedButFailed"));
            process.markCompleted();
        }
    }

    /**
     * Creates an appropriate task context just like {@link BasicJobFactory#setupTaskContext()} does for interactive
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
