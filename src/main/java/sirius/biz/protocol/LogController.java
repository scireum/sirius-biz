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
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;

/**
 * Provides a GUI for viewing system logs.
 */
@Register(framework = Protocols.FRAMEWORK_PROTOCOLS)
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
        ph.withPageSize(100);
        ph.addTermAggregation(LoggedMessage.CATEGORY, 100);
        ph.addTermAggregation(LoggedMessage.LEVEL);
        ph.addTermAggregation(LoggedMessage.NODE);
        ph.addTimeAggregation(LoggedMessage.TOD,
                              false,
                              DateRange.LAST_FIVE_MINUTES,
                              DateRange.LAST_FIFTEEN_MINUTES,
                              DateRange.LAST_TWO_HOURS,
                              DateRange.TODAY,
                              DateRange.YESTERDAY,
                              DateRange.THIS_WEEK,
                              DateRange.LAST_WEEK);
        ph.withSearchFields(QueryField.contains(LoggedMessage.SEARCH_FIELD));
        ph.withTotalCount();

        ctx.respondWith().template("/templates/biz/protocol/logs.html.pasta", ph.asPage());
    }
}
