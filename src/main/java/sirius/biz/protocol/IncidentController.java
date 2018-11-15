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
 * Provides a GUI for viewing incidents.
 */
@Register(classes = Controller.class, framework = Protocols.FRAMEWORK_PROTOCOLS)
public class IncidentController extends BizController {

    /**
     * Lists all recorded errors.
     *
     * @param ctx the current request
     */
    @Permission(Protocols.PERMISSION_SYSTEM_PROTOCOLS)
    @DefaultRoute
    @Routed("/system/errors")
    public void errors(WebContext ctx) {
        ElasticPageHelper<StoredIncident> ph = ElasticPageHelper.withQuery(elastic.select(StoredIncident.class)
                                                                                  .orderDesc(StoredIncident.LAST_OCCURRENCE));
        ph.withContext(ctx);
        ph.addTermAggregation(StoredIncident.CATEGORY);
        ph.addTermAggregation(StoredIncident.NODE);
        ph.addTimeAggregation(StoredIncident.LAST_OCCURRENCE,
                              DateRange.lastFiveMinutes(),
                              DateRange.lastFiveteenMinutes(),
                              DateRange.lastTwoHours(),
                              DateRange.today(),
                              DateRange.yesterday(),
                              DateRange.thisWeek(),
                              DateRange.lastWeek());
        ph.withSearchFields(QueryField.contains(StoredIncident.SEARCH_FIELD));

        ctx.respondWith()
           .template("templates/protocol/errors.html.pasta",
                     ph.asPage(),
                     (int) elastic.select(StoredIncident.class).count());
    }

    /**
     * Shows the details of the given error
     *
     * @param id  the ID of the {@link StoredIncident} to show
     * @param ctx the current request
     */
    @Permission(Protocols.PERMISSION_SYSTEM_PROTOCOLS)
    @Routed("/system/error/:1")
    public void error(WebContext ctx, String id) {
        StoredIncident incident = find(StoredIncident.class, id);
        ctx.respondWith().template("templates/protocol/error.html.pasta", incident);
    }
}
