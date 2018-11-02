/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.tenants.TenantUserManager;
import sirius.biz.web.BizController;
import sirius.biz.web.ElasticPageHelper;
import sirius.db.es.ElasticQuery;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Register(classes = Controller.class)
public class ProcessController extends BizController {
    //TODO
    private static final String PERMISSION_MANAGE_PROCESSES = "manage-processes";

    @Routed("/ps")
    @LoginRequired
    public void processes(WebContext ctx) {
        ElasticQuery<Process> query = elastic.select(Process.class).orderDesc(Process.SCHEDULED);

        UserInfo user = UserContext.getCurrentUser();
        //TODO make re-usable (the constant)
        if (!user.hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
            query.eq(Process.TENANT_ID, user.getTenantId());
        }

        if (!user.hasPermission(PERMISSION_MANAGE_PROCESSES)) {
            query.eq(Process.USER_ID, user.getUserId());
        }

        ElasticPageHelper<Process> ph = ElasticPageHelper.withQuery(query);
        ph.withContext(ctx);
        ph.addTermAggregation(Process.STATE, ProcessState.class);
        //TODO  ph.addBooleanFacet(Process.CANCELED.getName(), NLS.get("Process.canceled"));
        ph.addTermAggregation(Process.PROCESS_TYPE, value -> NLS.getIfExists(value, null).orElse(value));
        ph.addTimeAggregation(Process.SCHEDULED,
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
    public void process(WebContext ctx, String processId) {
        Process process = find(Process.class, processId);

        UserInfo user = UserContext.getCurrentUser();
        //TODO make re-usable (the constant)
        if (!user.hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
            assertTenant(process.getTenantId());
        }

        if (!Strings.areEqual(user.getUserId(), process.getUserId())) {
            assertPermission(PERMISSION_MANAGE_PROCESSES);
        }

        ElasticQuery<ProcessLog> query =
                elastic.select(ProcessLog.class).eq(ProcessLog.PROCESS, process).orderDesc(ProcessLog.TIMESTAMP);
        int numLogs = (int) query.count();

        ElasticPageHelper<ProcessLog> ph = ElasticPageHelper.withQuery(query);
        ph.withContext(ctx);
        ph.addTermAggregation(ProcessLog.TYPE, ProcessLogType.class);
        ph.addTermAggregation(ProcessLog.MESSAGE_TYPE, value -> NLS.getIfExists(value, null).orElse(value));
        ph.addTimeAggregation(ProcessLog.TIMESTAMP,
                              DateRange.lastFiveMinutes(),
                              DateRange.lastFiveteenMinutes(),
                              DateRange.lastTwoHours());
        ph.addTermAggregation(ProcessLog.NODE);
        ph.withSearchFields(QueryField.contains(ProcessLog.SEARCH_FIELD));

        ctx.respondWith().template("templates/process/process.html.pasta", this,process, numLogs, ph.asPage());
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
}
