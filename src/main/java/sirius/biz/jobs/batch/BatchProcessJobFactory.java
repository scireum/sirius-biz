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
import sirius.biz.cluster.work.DistributedTasks;
import sirius.biz.jobs.BasicJobFactory;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.ProcessLink;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.kernel.di.std.Part;
import sirius.web.http.WebContext;
import sirius.web.services.JSONStructuredOutput;

import java.util.Map;

public abstract class BatchProcessJobFactory extends BasicJobFactory {

    public static final String CONTEXT_PROCESS = "process";
    public static final String CONTEXT_JOB_FACTORY = "jobFactory";

    @Part
    protected Processes processes;

    @Part
    protected DistributedTasks tasks;

    @Override
    public String getIcon() {
        return "fa-cogs";
    }

    @Override
    public void executeInUI(WebContext request, Map<String, String> context) {
        String processId = startWithContext(context);
        request.respondWith().redirectToGet("/ps/" + processId);
    }

    @Override
    protected void executeInCall(JSONStructuredOutput out, Map<String, String> context) {
        String processId = startWithContext(context);
        processes.outputAsJSON(processId, out);
    }

    @Override
    protected void executeInBackground(Map<String, String> context) {
        startWithContext(context);
    }

    protected String startWithContext(Map<String, String> context) {
        checkPermissions();
        String processId = processes.createProcessForCurrentUser(getLabel(), getIcon(), context);
        setupTaskContext();

        processes.log(processId, ProcessLog.info().withMessage("Scheduled"));
        processes.addLink(processId, new ProcessLink().withLabel("Job").withUri("/job/" + getName()));

        JSONObject executorContext =
                new JSONObject().fluentPut(CONTEXT_PROCESS, processId).fluentPut(CONTEXT_JOB_FACTORY, getName());
        if (isPrioritized()) {
            tasks.submitPrioritizedTask(getExecutor(), getPenaltyToken(), executorContext);
        } else {
            tasks.submitFIFOTask(getExecutor(), executorContext);
        }

        return processId;
    }

    protected abstract String getPenaltyToken();

    protected abstract Class<? extends DistributedTaskExecutor> getExecutor();

    protected abstract boolean isPrioritized();

    protected abstract void executeTask(ProcessContext process) throws Exception;
}
