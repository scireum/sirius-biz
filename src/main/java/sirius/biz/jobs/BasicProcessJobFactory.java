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
import sirius.biz.cluster.work.DistributedTasks;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.kernel.di.std.Part;

public abstract class BasicProcessJobFactory extends BasicJobFactory {

    public static final String CONTEXT_PROCESS = "process";
    public static final String CONTEXT_JOB_FACTORY = "jobFactory";

    @Part
    protected Processes processes;

    @Part
    protected DistributedTasks tasks;

    @Override
    protected String executeWithContext(JSONObject context) {
        String processId = processes.createProcessForCurrentUser(getLabel(), context);

        JSONObject executorContext =
                new JSONObject().fluentPut(CONTEXT_PROCESS, processId).fluentPut(CONTEXT_JOB_FACTORY, getName());
        if (isPrioritized()) {
            tasks.submitPrioritizedTask(getExecutor(), getPenaltyToken(), executorContext);
        } else {
            tasks.submitFIFOTask(getExecutor(), executorContext);
        }

        return "/ps/" + processId;
    }

    protected abstract String getPenaltyToken();

    protected abstract Class<? extends DistributedTaskExecutor> getExecutor();

    protected abstract boolean isPrioritized();

    protected abstract void executeTask(ProcessContext process);
}
