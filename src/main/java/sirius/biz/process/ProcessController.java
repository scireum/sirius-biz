/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.web.BizController;
import sirius.biz.web.ElasticPageHelper;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticQuery;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;
import sirius.web.services.JSONStructuredOutput;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

@Register(classes = Controller.class)
public class ProcessController extends BizController {
    //TODO
    private static final String PERMISSION_MANAGE_PROCESSES = "manage-processes";
    private static final long MIN_COMPLETION_TIME_TO_DISABLE_AUTO_REFRESH = 20;

    @Part
    private Processes processes;

    @Routed("/ps")
    @LoginRequired
    public void processes(WebContext ctx) {
        ElasticQuery<Process> query = elastic.select(Process.class).orderDesc(Process.STARTED);

        UserInfo user = UserContext.getCurrentUser();
        //TODO make re-usable (the constant)
        if (!user.hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
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

        ctx.respondWith().template("templates/process/processes.html.pasta", this, ph.asPage());
    }

    @Routed("/ps/:1")
    @LoginRequired
    public void processDetails(WebContext ctx, String processId) {
        Process process = find(Process.class, processId);

        assertAccess(process);

        ElasticQuery<ProcessLog> query =
                elastic.select(ProcessLog.class).eq(ProcessLog.PROCESS, process).orderDesc(ProcessLog.TIMESTAMP);
        int numLogs = (int) query.count();

        ctx.respondWith().template("templates/process/process-details.html.pasta", this, process, numLogs, query.limit(5).queryList());
    }

    @Routed("/ps/:1/logs")
    @LoginRequired
    public void processLogs(WebContext ctx, String processId) {
        Process process = find(Process.class, processId);

        assertAccess(process);

        ElasticQuery<ProcessLog> query =
                elastic.select(ProcessLog.class).eq(ProcessLog.PROCESS, process).orderDesc(ProcessLog.TIMESTAMP);
        int numLogs = (int) query.count();

        ElasticPageHelper<ProcessLog> ph = ElasticPageHelper.withQuery(query);
        ph.withContext(ctx);
        ph.addTermAggregation(ProcessLog.TYPE, ProcessLogType.class);
        //TODO
        ph.addTermAggregation(ProcessLog.MESSAGE_HANDLER, value -> NLS.getIfExists(value, null).orElse(value));
        ph.addTimeAggregation(ProcessLog.TIMESTAMP,
                              DateRange.lastFiveMinutes(),
                              DateRange.lastFiveteenMinutes(),
                              DateRange.lastTwoHours());
        ph.addTermAggregation(ProcessLog.NODE);
        ph.withSearchFields(QueryField.contains(ProcessLog.SEARCH_FIELD));

        ctx.respondWith().template("templates/process/process-logs.html.pasta", this, process, numLogs, ph.asPage());
    }

    @Routed("/ps/:1/file/:2")
    @LoginRequired
    public void downloadProcessFile(WebContext ctx, String processId, String fileId) {
        Process process = find(Process.class, processId);
        assertAccess(process);

        for (ProcessFile file : process.getFiles()) {
            if (Strings.areEqual(file.getFileId(), fileId)) {
                processes.getStorage().serve(ctx, process, file);
                return;
            }
        }

        ctx.respondWith().error(HttpResponseStatus.NOT_FOUND);
    }

    private void assertAccess(Process process) {
        UserInfo user = UserContext.getCurrentUser();
        //TODO make re-usable (the constant) - even better use special permission and map via profile
        if (!user.hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
            assertTenant(process.getTenantId());
        }

        if (!Strings.areEqual(user.getUserId(), process.getUserId())) {
            assertPermission(PERMISSION_MANAGE_PROCESSES);
        }

        assertPermission(process.getRequiredPermission());
    }

    public String formatTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            return "";
        }

        if (timestamp.toLocalDate().equals(LocalDate.now())) {
            return NLS.toUserString(timestamp.toLocalTime());
        }

        return NLS.toUserString(timestamp);
    }

    public String formatDuration(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return "";
        }

        long absSeconds = Duration.between(from, to).getSeconds();
        return Strings.apply("%02d:%02d:%02d", absSeconds / 3600, (absSeconds % 3600) / 60, absSeconds % 60);
    }

    public boolean shouldAutorefresh(Process process) {
        if (process.getCompleted() == null || process.getState() != ProcessState.TERMINATED) {
            return true;
        }

        return Duration.between(process.getCompleted(), LocalDateTime.now()).getSeconds()
               < MIN_COMPLETION_TIME_TO_DISABLE_AUTO_REFRESH;
    }

    @Routed(value = "/ps/:1/api", jsonCall = true)
    public void process(WebContext ctx, JSONStructuredOutput out, String processId) {
        Process process = find(Process.class, processId);
        assertAccess(process);
        processes.outputAsJSON(processId, out);
    }
}
