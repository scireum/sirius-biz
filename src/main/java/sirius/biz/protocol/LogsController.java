/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.biz.web.BizController;
import sirius.biz.web.SQLPageHelper;
import sirius.db.mixing.DateRange;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;

/**
 * Provides a GUI for viewing system logs.
 */
@Register(classes = Controller.class, framework = Protocols.FRAMEWORK_PROTOCOLS)
public class LogsController extends BizController {

    /**
     * Lists all recorded log entries.
     *
     * @param ctx the current request
     */
    @Permission(Protocols.PERMISSION_SYSTEM_PROTOCOLS)
    @DefaultRoute
    @Routed("/system/logs")
    public void logs(WebContext ctx) {
        SQLPageHelper<LogEntry> ph = SQLPageHelper.withQuery(oma.select(LogEntry.class).orderDesc(LogEntry.TOD));
        ph.withContext(ctx);
        ph.enableAdvancedSearch();
        ph.addQueryFacet(LogEntry.CATEGORY.getName(),
                         NLS.get("LogEntry.category"),
                         q -> oma.select(LogEntry.class).distinctFields(LogEntry.CATEGORY, LogEntry.CATEGORY).asSQLQuery());
        ph.addQueryFacet(LogEntry.LEVEL.getName(),
                         NLS.get("LogEntry.level"),
                         q -> oma.select(LogEntry.class).distinctFields(LogEntry.LEVEL, LogEntry.LEVEL).asSQLQuery());
        ph.addTimeFacet(LogEntry.TOD.getName(),
                        NLS.get("LogEntry.tod"),
                        DateRange.lastFiveMinutes(),
                        DateRange.lastFiveteenMinutes(),
                        DateRange.lastTwoHours(),
                        DateRange.today(),
                        DateRange.yesterday(),
                        DateRange.thisWeek(),
                        DateRange.lastWeek());
        ph.withSearchFields(LogEntry.CATEGORY, LogEntry.LEVEL, LogEntry.MESSAGE);

        ctx.respondWith().template("templates/protocol/logs.html.pasta", ph.asPage());
    }
}
