/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho;

import io.netty.handler.codec.http.QueryStringDecoder;
import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.analytics.events.PageImpressionEvent;
import sirius.biz.web.BizController;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides a default route for "/admin" and "/system/dashboard" as these are the common entry points of the
 * "backend" which is based on the Tycho UI.
 * <p>
 * Note, that sirius-web will redirect "/" and "/dashboard" to "/system/dashboard" if no other
 * controller or dispatcher handles these. We use this sort of complex approach here, to let the applications
 * decide which URIs are handled by themselves (and used for other purposes) and which are used for the main
 * entrypoint of the backed.
 */
@Register(classes = {Controller.class, DashboardController.class})
public class DashboardController extends BizController {

    @Part
    private EventRecorder eventRecorder;

    @PriorityParts(TechStackInfo.class)
    private List<TechStackInfo> techStack;

    private List<Tuple<String, String>> activeTechnologies;

    /**
     * Provides an entry point via "/admin".
     *
     * @param webContext the request to respond to
     */
    @LoginRequired
    @Routed(value = "/admin", priority = 999)
    public void admin(WebContext webContext) {
        String path = new QueryStringDecoder(webContext.getRequest().uri()).path();
        eventRecorder.record(new PageImpressionEvent().withAggregationUrl("/system/dashboard"));
        webContext.respondWith().template("/templates/biz/tycho/dashboard.html.pasta", path);
    }

    /**
     * Provides an alternative entry point via "/system/dashboard".
     *
     * @param webContext the request to respond to
     */
    @LoginRequired
    @Routed(value = "/system/dashboard", priority = 999)
    public void dashboard(WebContext webContext) {
        admin(webContext);
    }

    /**
     * Provides a list of tuples (image url + link) for all active open source technologies in this product.
     * <p>
     * These are provided via {@link TechStackInfo TechstackInfos}.
     *
     * @return a list of all active technologies to show
     */
    public List<Tuple<String, String>> getTechStack() {
        if (activeTechnologies == null) {
            List<Tuple<String, String>> technologies = new ArrayList<>();
            techStack.forEach(provider -> provider.fetchActiveTechnologies((image, link) -> technologies.add(Tuple.create(
                    image,
                    link))));
            activeTechnologies = technologies;
        }

        return Collections.unmodifiableList(activeTechnologies);
    }
}
