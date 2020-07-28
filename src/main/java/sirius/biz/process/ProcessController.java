/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.cluster.work.DistributedTasks;
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
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
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
@Register(framework = Processes.FRAMEWORK_PROCESSES)
public class ProcessController extends BizController {
    /**
     * Defines the permission required to view and manage processes of other users within the same tenant.
     */
    public static final String PERMISSION_MANAGE_PROCESSES = "permission-manage-processes";

    /**
     * Defines the permission required to view and manage processes of other tenants.
     */
    public static final String PERMISSION_MANAGE_ALL_PROCESSES = "permission-manage-all-processes";

    private static final int NUMBER_OF_PREVIEW_LOGS = 15;

    @Part
    private Processes processes;

    @Part
    private GlobalContext context;

    @Part
    private DelayLine delayLine;

    @Part
    private DistributedTasks distributedTasks;

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
                                      false,
                                      DateRange.lastFiveMinutes(),
                                      DateRange.lastFiveteenMinutes(),
                                      DateRange.lastTwoHours(),
                                      DateRange.today(),
                                      DateRange.yesterday(),
                                      DateRange.thisWeek(),
                                      DateRange.lastWeek());
        pageHelper.withSearchFields(QueryField.contains(ProcessLog.SEARCH_FIELD));

        ctx.respondWith().template("/templates/biz/process/processes.html.pasta", pageHelper.asPage());
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

        // If the whole logs fit into the preview, we sort them in natural order,
        // otherwise we show the last N (descending)
        long numberOfLogs = query.count();
        if (numberOfLogs > NUMBER_OF_PREVIEW_LOGS) {
            query = query.orderDesc(ProcessLog.SORT_KEY).limit(NUMBER_OF_PREVIEW_LOGS);
        } else {
            query = query.orderAsc(ProcessLog.SORT_KEY);
        }

        ctx.respondWith().template("/templates/biz/process/process-details.html.pasta", process, query.queryList());
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

        ElasticPageHelper<ProcessLog> ph = ElasticPageHelper.withQuery(buildLogsQuery(process));
        ph.withContext(ctx);
        ph.withPageSize(100);
        ph.addTermAggregation(ProcessLog.TYPE, ProcessLogType.class);
        ph.addTermAggregation(ProcessLog.STATE, ProcessLogState.class);
        ph.addTermAggregation(ProcessLog.MESSAGE_TYPE, NLS::smartGet);
        if (getUser().hasPermission(ProcessController.PERMISSION_MANAGE_ALL_PROCESSES)) {
            ph.addBooleanAggregation(ProcessLog.SYSTEM_MESSAGE);
        }
        ph.addTimeAggregation(ProcessLog.TIMESTAMP,
                              false,
                              DateRange.lastFiveMinutes(),
                              DateRange.lastFiveteenMinutes(),
                              DateRange.lastTwoHours());
        ph.addTermAggregation(ProcessLog.NODE);
        ph.addSortFacet(Tuple.create("$ProcessController.sortDesc", qry -> qry.orderDesc(ProcessLog.SORT_KEY)),
                        Tuple.create("$ProcessController.sortAsc", qry -> qry.orderAsc(ProcessLog.SORT_KEY)));
        ph.withSearchFields(QueryField.contains(ProcessLog.SEARCH_FIELD));

        ctx.respondWith().template("/templates/biz/process/process-logs.html.pasta", process, ph.asPage());
    }

    /**
     * Trigger the creation of an export file containing the log messages of the given process.
     *
     * @param ctx       the current request
     * @param processId the process to export the output for
     * @param type      the desired export file format
     * @see ProcessController#exportOutput(WebContext, String, String, String)
     */
    @Routed("/ps/:1/logs/export/:2")
    @LoginRequired
    public void exportLogs(WebContext ctx, String processId, String type) {
        exportOutput(ctx, processId, null, type);
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
        delayLine.forkDelayed(Tasks.DEFAULT, 1, () -> ctx.respondWith().redirectToGet("/ps/" + process.getId()));
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
        delayLine.forkDelayed(Tasks.DEFAULT, 1, () -> ctx.respondWith().redirectToGet("/ps/" + process.getId()));
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
            if (handler != null && handler.executeAction(ctx, process, log, action, returnUrl)) {
                return;
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
                                                .eq(ProcessLog.PROCESS, process);

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
     * Trigger the creation of an export file for the selected output.
     * <p>
     * To compute the export, the referenced process is {@link Processes#restartProcess(String, String) restarted}
     * and an appropriate distributed task is submitted which will then perform the export.
     *
     * @param ctx       the current request
     * @param processId the process to export the output for
     * @param name      the name of the output to export
     * @param type      the desired export file format
     * @see ExportLogsAsFileTaskExecutor
     */
    @Routed("/ps/:1/output/:2/export/:3")
    @LoginRequired
    public void exportOutput(WebContext ctx, String processId, String name, String type) {
        // We need to perform this lookup to ensure that we may access the process...
        Process process = findAccessibleProcess(processId);

        processes.restartProcess(process.getId(),
                                 Strings.isEmpty(name) ?
                                 NLS.get("ProcessController.exportLogsReason") :
                                 NLS.fmtr("ProcessController.exportOutputReason").set("output", name).format());

        JSONObject exportSpec = new JSONObject();
        exportSpec.put(ExportLogsAsFileTaskExecutor.CONTEXT_PROCESS, process.getId());
        exportSpec.put(ExportLogsAsFileTaskExecutor.CONTEXT_OUTPUT, name);
        exportSpec.put(ExportLogsAsFileTaskExecutor.CONTEXT_FORMAT, type);
        distributedTasks.submitPrioritizedTask(ExportLogsAsFileTaskExecutor.class,
                                               UserContext.getCurrentUser().getTenantId(),
                                               exportSpec);

        UserContext.message(Message.info(NLS.get("ProcessController.exportStarted")));
        ctx.respondWith().redirectToGet("/ps/" + process.getId());
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
