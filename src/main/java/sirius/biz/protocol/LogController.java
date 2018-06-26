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
import sirius.db.mixing.DateRange;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;

/**
 * Provides a GUI for viewing system logs.
 */
@Register(classes = Controller.class, framework = Protocols.FRAMEWORK_PROTOCOLS)
public class LogController extends BizController {

    /**
     * Lists all recorded log entries.
     *
     * @param ctx the current request
     */
    @Permission(Protocols.PERMISSION_SYSTEM_PROTOCOLS)
    @DefaultRoute
    @Routed("/system/logs")
    public void logs(WebContext ctx) {
        ElasticPageHelper<LoggedMessage> ph =
                ElasticPageHelper.withQuery(elastic.select(LoggedMessage.class).orderDesc(LoggedMessage.TOD));
        ph.withContext(ctx);
        ph.addTermAggregation(LoggedMessage.CATEGORY);
        ph.addTermAggregation(LoggedMessage.LEVEL);
        ph.addTermAggregation(LoggedMessage.NODE);
        ph.addTimeAggregation(LoggedMessage.TOD,
                              DateRange.lastFiveMinutes(),
                              DateRange.lastFiveteenMinutes(),
                              DateRange.lastTwoHours(),
                              DateRange.today(),
                              DateRange.yesterday(),
                              DateRange.thisWeek(),
                              DateRange.lastWeek());
        ph.withSearchFields(QueryField.contains(LoggedMessage.CATEGORY),
                            QueryField.contains(LoggedMessage.LEVEL),
                            QueryField.contains(LoggedMessage.MESSAGE));

        ctx.respondWith().template("templates/protocol/logs.html.pasta", ph.asPage());
    }
}
