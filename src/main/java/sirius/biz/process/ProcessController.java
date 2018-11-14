/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.logs.ProcessLogHandler;
import sirius.biz.process.logs.ProcessLogState;
import sirius.biz.process.logs.ProcessLogType;
import sirius.biz.process.output.ProcessOutput;
import sirius.biz.process.output.ProcessOutputType;
import sirius.biz.web.BizController;
import sirius.biz.web.ElasticPageHelper;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticQuery;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.Message;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;
import sirius.web.services.JSONStructuredOutput;

import java.util.ArrayList;

@Register(classes = Controller.class)
public class ProcessController extends BizController {
    //TODO
    public static final String PERMISSION_MANAGE_PROCESSES = "permission-manage-processes";
    public static final String PERMISSION_MANAGE_ALL_PROCESSES = "permission-manage-all-processes";

    @Part
    private Processes processes;

    @Part
    private GlobalContext context;

    @Routed("/ps")
    @LoginRequired
    public void processes(WebContext ctx) {
        ElasticQuery<Process> query = elastic.select(Process.class).orderDesc(Process.STARTED);

        UserInfo user = UserContext.getCurrentUser();
        if (!user.hasPermission(PERMISSION_MANAGE_ALL_PROCESSES)) {
            query.eq(Process.TENANT_ID, user.getTenantId());
        }

        if (!user.hasPermission(PERMISSION_MANAGE_PROCESSES)) {
            query.eq(Process.USER_ID, user.getUserId());
        }

        query.where(Elastic.FILTERS.oneInField(Process.REQUIRED_PERMISSION, new ArrayList<>(user.getPermissions()))
                                   .orEmpty()
                                   .build());

        ElasticPageHelper<Process> ph = ElasticPageHelper.withQuery(query);
        ph.withContext(ctx);
        ph.addTermAggregation(Process.STATE, ProcessState.class);
        ph.addTermAggregation(Process.PROCESS_TYPE, value -> NLS.getIfExists(value, null).orElse(value));
        ph.addTimeAggregation(Process.STARTED,
                              DateRange.lastFiveMinutes(),
                              DateRange.lastFiveteenMinutes(),
                              DateRange.lastTwoHours(),
                              DateRange.today(),
                              DateRange.yesterday(),
                              DateRange.thisWeek(),
                              DateRange.lastWeek());
        ph.withSearchFields(QueryField.contains(ProcessLog.SEARCH_FIELD));

        ctx.respondWith().template("templates/process/processes.html.pasta", ph.asPage());
    }

    private Process findAccessibleProcess(String processId) {
        Process process = processes.fetchProcessForUser(processId).orElse(null);
        assertNotNull(process);

        return process;
    }

    @Routed("/ps/:1")
    @LoginRequired
    public void processDetails(WebContext ctx, String processId) {
        Process process = findAccessibleProcess(processId);

        ElasticQuery<ProcessLog> query = buildLogsQuery(process);
        ctx.respondWith().template("templates/process/process-details.html.pasta", process, query.limit(5).queryList());
    }

    @Routed("/ps/:1/logs")
    @LoginRequired
    public void processLogs(WebContext ctx, String processId) {
        Process process = findAccessibleProcess(processId);

        ElasticQuery<ProcessLog> query = buildLogsQuery(process);

        ElasticPageHelper<ProcessLog> ph = ElasticPageHelper.withQuery(query);
        ph.withContext(ctx);
        ph.addTermAggregation(ProcessLog.TYPE, ProcessLogType.class);
        ph.addTermAggregation(ProcessLog.STATE, ProcessLogState.class);
        ph.addTermAggregation(ProcessLog.MESSAGE_HANDLER, NLS::smartGet);
        ph.addTimeAggregation(ProcessLog.TIMESTAMP,
                              DateRange.lastFiveMinutes(),
                              DateRange.lastFiveteenMinutes(),
                              DateRange.lastTwoHours());
        ph.addTermAggregation(ProcessLog.NODE);
        ph.withSearchFields(QueryField.contains(ProcessLog.SEARCH_FIELD));

        ctx.respondWith().template("templates/process/process-logs.html.pasta", process, ph.asPage());
    }

    @Routed("/ps/:1/action/:2/:3")
    @LoginRequired
    public void executeLogAction(WebContext ctx, String processId, String processLogId, String action) {
        Process process = findAccessibleProcess(processId);
        String returnUrl = ctx.get("returnUrl").asString();
        try {
            ProcessLog log = elastic.select(ProcessLog.class)
                                    .eq(ProcessLog.ID, processLogId)
                                    .eq(ProcessLog.PROCESS, process)
                                    .queryFirst();
            assertNotNull(log);

            ProcessLogHandler handler = log.getHandler().orElse(null);
            if (handler != null) {
                if (handler.executeAction(ctx, process, log, action, returnUrl)) {
                    return;
                }
            }

            handleDefaultAction(ctx, log, action, returnUrl);
        } catch (Exception e) {
            UserContext.handle(e);
            ctx.respondWith().redirectToGet(returnUrl);
        }
    }

    private void handleDefaultAction(WebContext ctx, ProcessLog log, String action, String returnUrl) {
        if (Strings.areEqual(ProcessLog.ACTION_MARK_OPEN, action)) {
            UserContext.message(Message.info(NLS.get("ProcessController.logUpdate")));
            processes.updateProcessLogStateAndReturn(log, ProcessLogState.OPEN, ctx, returnUrl);
        } else if (Strings.areEqual(ProcessLog.ACTION_MARK_RESOLVED, action)) {
            UserContext.message(Message.info(NLS.get("ProcessController.logUpdate")));
            processes.updateProcessLogStateAndReturn(log, ProcessLogState.RESOLVED, ctx, returnUrl);
        } else if (Strings.areEqual(ProcessLog.ACTION_MARK_IGNORED, action)) {
            UserContext.message(Message.info(NLS.get("ProcessController.logUpdate")));
            processes.updateProcessLogStateAndReturn(log, ProcessLogState.IGNORED, ctx, returnUrl);
        } else {
            throw Exceptions.createHandled()
                            .withNLSKey("ProcessController.unknownAction")
                            .set("action", action)
                            .handle();
        }
    }

    private ElasticQuery<ProcessLog> buildLogsQuery(Process process) {
        ElasticQuery<ProcessLog> query = elastic.select(ProcessLog.class)
                                                .where(Elastic.FILTERS.notExists(ProcessLog.OUTPUT))
                                                .eq(ProcessLog.PROCESS, process)
                                                .orderDesc(ProcessLog.SORT_KEY);

        UserInfo user = UserContext.getCurrentUser();
        if (!user.hasPermission(PERMISSION_MANAGE_ALL_PROCESSES)) {
            query.eq(ProcessLog.SYSTEM_MESSAGE, false);
        }

        return query;
    }

    @Routed("/ps/:1/output/:2")
    @LoginRequired
    public void processOutput(WebContext ctx, String processId, String name) {
        Process process = findAccessibleProcess(processId);

        try {
            for (ProcessOutput output : process.getOutputs()) {
                if (Strings.areEqual(output.getName(), name)) {
                    ProcessOutputType outputType = context.findPart(output.getType(), ProcessOutputType.class);
                    outputType.render(ctx, process, output);
                    return;
                }
            }

            UserContext.message(Message.error(NLS.fmtr("ProcessController.unknownOutput")
                                                 .set("output", name)
                                                 .format()));
        } catch (Exception e) {
            UserContext.handle(e);
        }

        processDetails(ctx, processId);
    }

    @Routed("/ps/:1/file/:2")
    @LoginRequired
    public void downloadProcessFile(WebContext ctx, String processId, String fileId) {
        Process process = findAccessibleProcess(processId);

        for (ProcessFile file : process.getFiles()) {
            if (Strings.areEqual(file.getFileId(), fileId)) {
                processes.getStorage().serve(ctx, process, file);
                return;
            }
        }

        ctx.respondWith().error(HttpResponseStatus.NOT_FOUND);
    }

    @Routed(value = "/ps/:1/api", jsonCall = true)
    public void processAPI(WebContext ctx, JSONStructuredOutput out, String processId) {
        Process process = findAccessibleProcess(processId);
        processes.outputAsJSON(processId, out);
    }
}
