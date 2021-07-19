/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho;

import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.analytics.events.PageImpressionEvent;
import sirius.biz.web.BizController;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;

/**
 * Provides a default route for "/", "/admin" and "/dashboard" as these are the common entry points of the
 * "backend" which is based on the Tycho UI.
 * <p>
 * Note that we provide low priorities for each route, so that the application can easily overwrite each route if
 * needed.
 */
@Register
public class DashboardController extends BizController {

    @Part
    private EventRecorder eventRecorder;

    /**
     * Provides the main entry point for "/".
     *
     * @param webContext the request to respond to
     */
    @LoginRequired
    @Routed(value = "/", priority = 999)
    public void mainEntryPoint(WebContext webContext) {
        eventRecorder.record(new PageImpressionEvent().withAggregationUrl("/"));
        webContext.respondWith().template("/templates/biz/tycho/dashboard.html.pasta");
    }

    /**
     * Provides an alternative entry point via "/admin".
     *
     * @param webContext the request to respond to
     */
    @LoginRequired
    @Routed(value = "/admin", priority = 999)
    public void admin(WebContext webContext) {
        mainEntryPoint(webContext);
    }

    /**
     * Provides an alternative entry point via "/dashboard".
     *
     * @param webContext the request to respond to
     */
    @LoginRequired
    @Routed(value = "/dashboard", priority = 999)
    public void dashboard(WebContext webContext) {
        mainEntryPoint(webContext);
    }
}
