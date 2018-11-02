/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.process.ProcessLog;
import sirius.biz.process.Processes;
import sirius.kernel.di.std.Part;

public abstract class BasicProcessJobTaskExecutor extends DistributedTaskExecutor {

    @Part
    private Processes processes;

    @Part
    private Jobs jobs;

    @Override
    public void executeWork(JSONObject context) throws Exception {
        processes.execute(context.getString(BasicProcessJobFactory.CONTEXT_PROCESS), process -> {
            process.log(ProcessLog.info("Started"));
            try {
                String factoryId = context.getString(BasicProcessJobFactory.CONTEXT_JOB_FACTORY);
                jobs.setupTaskContext(factoryId);
                jobs.findFactory(factoryId, BasicProcessJobFactory.class).executeTask(process);
            } finally {
                if (process.isErroneous()) {
                    process.log(ProcessLog.warn("Done"));
                } else {
                    process.log(ProcessLog.success("Done"));
                }
            }
        });
    }
}
