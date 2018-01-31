/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.biz.web.BizController;
import sirius.biz.web.DateRange;
import sirius.biz.web.PageHelper;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;

/**
 * Provides a GUI for viewing incidents.
 */
@Register(classes = Controller.class, framework = Protocols.FRAMEWORK_PROTOCOLS)
public class IncidentsController extends BizController {

    /**
     * Lists all recorded errors.
     *
     * @param ctx the current request
     */
    @Permission(Protocols.PERMISSION_SYSTEM_PROTOCOLS)
    @DefaultRoute
    @Routed("/system/errors")
    public void errors(WebContext ctx) {
        PageHelper<Incident> ph = PageHelper.withQuery(oma.select(Incident.class).orderDesc(Incident.LAST_OCCURRENCE));
        ph.withContext(ctx);
        ph.addQueryFacet(Incident.CATEGORY.getName(),
                         NLS.get("Incident.category"),
                         q -> oma.select(Incident.class)
                                 .distinctFields(Incident.CATEGORY, Incident.CATEGORY)
                                 .asSQLQuery());
        ph.addTimeFacet(Incident.LAST_OCCURRENCE.getName(),
                        NLS.get("Incident.lastOccurrence"),
                        DateRange.lastFiveMinutes(),
                        DateRange.lastFiveteenMinutes(),
                        DateRange.lastTwoHours(),
                        DateRange.today(),
                        DateRange.yesterday(),
                        DateRange.thisWeek(),
                        DateRange.lastWeek());
        ph.withSearchFields(Incident.CATEGORY, Incident.MESSAGE);

        ctx.respondWith().template("templates/protocol/errors.html.pasta", ph.asPage(), (int)oma.select(Incident.class).count());
    }

    /**
     * Shows the details of the given error
     *
     * @param id  the ID of the {@link Incident} to show
     * @param ctx the current request
     */
    @Permission(Protocols.PERMISSION_SYSTEM_PROTOCOLS)
    @Routed("/system/error/:1")
    public void error(WebContext ctx, String id) {
        Incident incident = find(Incident.class, id);
        ctx.respondWith().template("templates/protocol/error.html.pasta", incident);
    }
}
