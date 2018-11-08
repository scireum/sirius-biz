/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.queries;

import sirius.biz.web.BizController;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.services.JSONStructuredOutput;

public class QueryController extends BizController {

    private static final String[] STRING_ARRAY = new String[0];

    @Part
    private GlobalContext globalContext;

    @Routed(value = "/analytics/inline/:1", jsonCall = true)
    public void inlineGraph(WebContext ctx, JSONStructuredOutput out, String queryName) {
        Query qry = globalContext.findPart(queryName, Query.class);
        qry.getPermissions().forEach(this::assertPermission);
        qry.generateInlineGraph(ctx::get, out);
    }
}
