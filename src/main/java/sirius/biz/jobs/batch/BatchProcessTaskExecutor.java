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
import sirius.biz.jobs.Jobs;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.Processes;
import sirius.kernel.async.TaskContext;
import sirius.kernel.di.std.Part;

public abstract class BatchProcessTaskExecutor extends DistributedTaskExecutor {

    @Part
    private Processes processes;

    @Part
    private Jobs jobs;

    @Override
    public void executeWork(JSONObject context) throws Exception {
        String factoryId = context.getString(BatchProcessJobFactory.CONTEXT_JOB_FACTORY);
        TaskContext taskContext = TaskContext.get();
        taskContext.setSystem("JOBS");
        taskContext.setSubSystem(factoryId);

        processes.execute(context.getString(BatchProcessJobFactory.CONTEXT_PROCESS), process -> {
            process.log(ProcessLog.info().withMessage("Started"));
            try {
                jobs.findFactory(factoryId, BatchProcessJobFactory.class).executeTask(process);
            } catch (Exception e) {
                process.handle(e);
            } finally {
                if (process.isErroneous()) {
                    process.log(ProcessLog.warn().withMessage("Done"));
                } else {
                    process.log(ProcessLog.success().withMessage("Done"));
                }
            }
        });
    }
}
