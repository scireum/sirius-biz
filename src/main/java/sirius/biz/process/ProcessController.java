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
import sirius.kernel.async.DelayLine;
import sirius.kernel.async.Tasks;
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

/**
 * Provides the management UI for {@link java.lang.Process processes}.
 */
@Register(classes = Controller.class, framework = Processes.FRAMEWORK_PROCESSES)
public class ProcessController extends BizController {
    /**
     * Defines the permission required to view and manage processes of other users within the same tenant.
     */
    public static final String PERMISSION_MANAGE_PROCESSES = "permission-manage-processes";

    /**
     * Defines the permission required to view and manage processes of other tenants.
     */
    public static final String PERMISSION_MANAGE_ALL_PROCESSES = "permission-manage-all-processes";

    @Part
    private Processes processes;

    @Part
    private GlobalContext context;

    @Part
    private DelayLine delayLine;

    /**
     * Lists all processes visible to the current user.
     *
     * @param ctx the current request
     */
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

        ElasticPageHelper<Process> pageHelper = ElasticPageHelper.withQuery(query);
        pageHelper.withContext(ctx);
        pageHelper.addTermAggregation(Process.STATE, ProcessState.class);
        pageHelper.addParameterFacet("reference",
                                     "reference-label",
                                     Process.REFERENCES,
                                     NLS.get("ProcessController.reference"),
                                     null);
        pageHelper.addTermAggregation(Process.PROCESS_TYPE, value -> NLS.getIfExists(value, null).orElse(null));
        pageHelper.addTimeAggregation(Process.STARTED,
                                      DateRange.lastFiveMinutes(),
                                      DateRange.lastFiveteenMinutes(),
                                      DateRange.lastTwoHours(),
                                      DateRange.today(),
                                      DateRange.yesterday(),
                                      DateRange.thisWeek(),
                                      DateRange.lastWeek());
        pageHelper.withSearchFields(QueryField.contains(ProcessLog.SEARCH_FIELD));

        ctx.respondWith().template("templates/biz/process/processes.html.pasta", pageHelper.asPage());
    }

    private Process findAccessibleProcess(String processId) {
        Process process = processes.fetchProcessForUser(processId).orElse(null);
        assertNotNull(process);

        return process;
    }

    /**
     * Shows the details of the given process.
     *
     * @param ctx       the current request
     * @param processId the id of the process to show
     */
    @Routed("/ps/:1")
    @LoginRequired
    public void processDetails(WebContext ctx, String processId) {
        Process process = findAccessibleProcess(processId);

        ElasticQuery<ProcessLog> query = buildLogsQuery(process);
        ctx.respondWith()
           .template("templates/biz/process/process-details.html.pasta", process, query.limit(5).queryList());
    }

    /**
     * Shows the log entries of the given process.
     *
     * @param ctx       the current request
     * @param processId the id of the process to show the log entries for
     */
    @Routed("/ps/:1/logs")
    @LoginRequired
    public void processLogs(WebContext ctx, String processId) {
        Process process = findAccessibleProcess(processId);

        ElasticQuery<ProcessLog> query = buildLogsQuery(process);

        ElasticPageHelper<ProcessLog> ph = ElasticPageHelper.withQuery(query);
        ph.withContext(ctx);
        ph.withPageSize(100);
        ph.addTermAggregation(ProcessLog.TYPE, ProcessLogType.class);
        ph.addTermAggregation(ProcessLog.STATE, ProcessLogState.class);
        ph.addTermAggregation(ProcessLog.MESSAGE_HANDLER, value -> NLS.getIfExists(value, null).orElse(null));
        ph.addTimeAggregation(ProcessLog.TIMESTAMP,
                              DateRange.lastFiveMinutes(),
                              DateRange.lastFiveteenMinutes(),
                              DateRange.lastTwoHours());
        ph.addTermAggregation(ProcessLog.NODE);
        ph.withSearchFields(QueryField.contains(ProcessLog.SEARCH_FIELD));

        ctx.respondWith().template("templates/biz/process/process-logs.html.pasta", process, ph.asPage());
    }

    /**
     * Cancels the execution of the given process.
     *
     * @param ctx       the current request
     * @param processId the id of the process to cancel
     */
    @Routed("/ps/:1/cancel")
    @LoginRequired
    public void cancelProcess(WebContext ctx, String processId) {
        Process process = findAccessibleProcess(processId);
        processes.markCanceled(process.getId());

        // Give ES some time to digest the change before essentially reloading the page...
        delayLine.forkDelayed(Tasks.DEFAULT, 1, () -> {
            ctx.respondWith().redirectToGet("/ps/" + process.getId());
        });
    }

    /**
     * Toggles (enables / disables) debug output for the given process.
     *
     * @param ctx       the current request
     * @param processId the id of the process to enable or disable debugging for
     */
    @Routed("/ps/:1/toggleDebugging")
    @LoginRequired
    public void toggleDebugging(WebContext ctx, String processId) {
        Process process = findAccessibleProcess(processId);
        processes.changeDebugging(process.getId(), !process.isDebugging());

        // Give ES some time to digest the change before essentially reloading the page...
        delayLine.forkDelayed(Tasks.DEFAULT, 1, () -> {
            ctx.respondWith().redirectToGet("/ps/" + process.getId());
        });
    }

    /**
     * Executes the given action for the given process and log entry.
     *
     * @param ctx          the current request
     * @param processId    the process to which the entry belongs
     * @param processLogId the log entry to execute the action on
     * @param action       the action to execute
     */
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

    /**
     * Handles the default actions which simply toggle the state of a {@link ProcessLog}.
     *
     * @param ctx       the current request
     * @param log       the log entry to mutate
     * @param action    the action to execute
     * @param returnUrl the URL to redirect to once the action is completed
     */
    private void handleDefaultAction(WebContext ctx, ProcessLog log, String action, String returnUrl) {
        if (Strings.areEqual(ProcessLog.ACTION_MARK_OPEN, action)) {
            updateStateAndReturn(ctx, log, ProcessLogState.OPEN, returnUrl);
        } else if (Strings.areEqual(ProcessLog.ACTION_MARK_RESOLVED, action)) {
            updateStateAndReturn(ctx, log, ProcessLogState.RESOLVED, returnUrl);
        } else if (Strings.areEqual(ProcessLog.ACTION_MARK_IGNORED, action)) {
            updateStateAndReturn(ctx, log, ProcessLogState.IGNORED, returnUrl);
        } else {
            throw Exceptions.createHandled()
                            .withNLSKey("ProcessController.unknownAction")
                            .set("action", action)
                            .handle();
        }
    }

    private void updateStateAndReturn(WebContext ctx, ProcessLog log, ProcessLogState state, String returnUrl) {
        UserContext.message(Message.info(NLS.get("ProcessController.logUpdated")));
        processes.updateProcessLogStateAndReturn(log, state, ctx, returnUrl);
    }

    private ElasticQuery<ProcessLog> buildLogsQuery(Process process) {
        ElasticQuery<ProcessLog> query = elastic.select(ProcessLog.class)
                                                .where(Elastic.FILTERS.notExists(ProcessLog.OUTPUT))
                                                .eq(ProcessLog.PROCESS, process)
                                                .orderAsc(ProcessLog.SORT_KEY);

        UserInfo user = UserContext.getCurrentUser();
        if (!user.hasPermission(PERMISSION_MANAGE_ALL_PROCESSES)) {
            query.eq(ProcessLog.SYSTEM_MESSAGE, false);
        }

        return query;
    }

    /**
     * Renders the given output for the given process.
     *
     * @param ctx       the current request
     * @param processId the process to render the output for
     * @param name      the name of the output to render
     */
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

    /**
     * Serves a file attached to a process
     *
     * @param ctx       the current request
     * @param processId the process to which the file belongs
     * @param fileId    the id of the process file to serve
     */
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

    /**
     * Provides a JSON representation of the given process.
     *
     * @param ctx       the current request
     * @param out       the output to write the JSON data to
     * @param processId the process to output
     */
    @Routed(value = "/ps/:1/api", jsonCall = true)
    public void processAPI(WebContext ctx, JSONStructuredOutput out, String processId) {
        processes.outputAsJSON(processId, out);
    }
}
