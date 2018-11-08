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
import sirius.biz.process.ProcessLog;
import sirius.biz.process.Processes;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.web.http.WebContext;
import sirius.web.services.JSONStructuredOutput;

import java.util.function.Function;

public abstract class BatchProcessJobFactory extends BasicJobFactory {

    public static final String CONTEXT_PROCESS = "process";
    public static final String CONTEXT_JOB_FACTORY = "jobFactory";

    @Part
    protected Processes processes;

    @Part
    protected DistributedTasks tasks;

    @Override
    public void callInUI(WebContext request) {
        String processId = startWithContext(request::get);
        request.respondWith().redirectToGet("/ps/" + processId);
    }

    @Override
    public void runInCall(WebContext request, JSONStructuredOutput out, Function<String, Value> parameterProvider) {
        String processId = startWithContext(parameterProvider);
        processes.outputAsJSON(processId, out);
    }

    @Override
    public void runInBackground(Function<String, Value> parameterProvider) {
        startWithContext(parameterProvider);
    }

    protected String startWithContext(Function<String, Value> parameterProvider) {
        checkPermissions();
        String processId = processes.createProcessForCurrentUser(getLabel(), buildAndVerifyContext(parameterProvider));
        setupTaskContext();

        processes.log(processId, ProcessLog.info("Scheduled"));
        processes.addLink(processId, ProcessLink.outputLink("Job", "/job/" + getName()));

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

    protected abstract void executeTask(ProcessContext process);
}
