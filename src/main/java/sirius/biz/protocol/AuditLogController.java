/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.biz.web.BizController;
import sirius.biz.web.ElasticPageHelper;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticQuery;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;

/**
 * Provides an admin GUI to display {@link AuditLogEntry audit log entries}.
 */
@Register
public class AuditLogController extends BizController {

    /**
     * Names the permissions required to view the protocol.
     */
    public static final String PERMISSION_AUDIT_LOGS = "permission-audit-logs";

    /**
     * Names the permissions required to view the audit log page.
     */
    private static final String PERMISSION_VIEW_AUDIT_LOGS = "permission-view-audit-log";

    /**
     * Renders some metrics to determine system growth.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Routed("/audit-log")
    @Permission(PERMISSION_VIEW_AUDIT_LOGS)
    public void auditLog(WebContext ctx) {
        ElasticQuery<AuditLogEntry> query = elastic.select(AuditLogEntry.class).orderDesc(AuditLogEntry.TIMESTAMP);

        if (!hasPermission(Protocols.PERMISSION_SYSTEM_PROTOCOLS)) {
            if (!hasPermission(PERMISSION_AUDIT_LOGS)) {
                query.where(Elastic.FILTERS.or(Elastic.FILTERS.eq(AuditLogEntry.USER,
                                                                  UserContext.getCurrentUser().getUserId()),
                                               Elastic.FILTERS.eq(AuditLogEntry.CAUSED_BY_USER,
                                                                  UserContext.getCurrentUser().getUserId())));
            } else {
                query.where(Elastic.FILTERS.or(Elastic.FILTERS.eq(AuditLogEntry.TENANT,
                                                                  UserContext.getCurrentUser().getTenantId()),
                                               Elastic.FILTERS.eq(AuditLogEntry.CAUSED_BY_USER,
                                                                  UserContext.getCurrentUser().getUserId())));
            }

            query.eq(AuditLogEntry.HIDDEN, false);
        }

        ElasticPageHelper<AuditLogEntry> ph = ElasticPageHelper.withQuery(query);
        ph.withContext(ctx);
        ph.withPageSize(100);
        ph.addTimeAggregation(AuditLogEntry.TIMESTAMP,
                              false,
                              DateRange.LAST_FIVE_MINUTES,
                              DateRange.LAST_FIFTEEN_MINUTES,
                              DateRange.LAST_TWO_HOURS,
                              DateRange.TODAY,
                              DateRange.YESTERDAY,
                              DateRange.THIS_WEEK,
                              DateRange.LAST_WEEK);
        ph.withSearchFields(QueryField.contains(AuditLogEntry.SEARCH_FIELD));

        ctx.respondWith().template("/templates/biz/protocol/audit_logs.html.pasta", ph.asPage());
    }
}
