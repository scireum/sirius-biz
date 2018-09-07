/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic.protocol;

import sirius.biz.web.BizController;
import sirius.biz.web.ElasticPageHelper;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticQuery;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.UserContext;

/**
 * Provides an admin GUI to display {@link AuditLogEntry audit log entries}.
 */
@Register(classes = Controller.class)
public class AuditLogController extends BizController {

    /**
     * Names the permissions required to view the protocol.
     */
    public static final String PERMISSION_AUDIT_LOGS = "permission-audit-logs";

    /**
     * Renders some metrics to determine system growth.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Routed("/audit-log")
    public void auditLog(WebContext ctx) {
        ElasticQuery<AuditLogEntry> query = elastic.select(AuditLogEntry.class).orderDesc(AuditLogEntry.TIMESTAMP);

        if (!hasPermission(Protocols.PERMISSION_SYSTEM_PROTOCOLS)) {
            if (!hasPermission(PERMISSION_AUDIT_LOGS)) {
                query.where(Elastic.FILTERS.or(Elastic.FILTERS.eq(AuditLogEntry.USER,
                                                                  UserContext.getCurrentUser().getUserId()),
                                               Elastic.FILTERS.eq(AuditLogEntry.CAUSED_BY_USER,
                                                                  UserContext.getCurrentUser().getUserId())));
            } else {
                query.eq(AuditLogEntry.TENANT, UserContext.getCurrentUser().getTenantId());
            }
        }

        ElasticPageHelper<AuditLogEntry> ph = ElasticPageHelper.withQuery(query);
        ph.withContext(ctx);
        ph.withPageSize(100);
        ph.addTimeAggregation(AuditLogEntry.TIMESTAMP,
                              DateRange.lastFiveMinutes(),
                              DateRange.lastFiveteenMinutes(),
                              DateRange.lastTwoHours(),
                              DateRange.today(),
                              DateRange.yesterday(),
                              DateRange.thisWeek(),
                              DateRange.lastWeek());
        ph.withSearchFields(QueryField.contains(AuditLogEntry.USER),
                            QueryField.contains(AuditLogEntry.IP),
                            QueryField.contains(AuditLogEntry.MESSAGE));

        ctx.respondWith().template("templates/protocol/audit_logs.html.pasta", ph.asPage());
    }
}
