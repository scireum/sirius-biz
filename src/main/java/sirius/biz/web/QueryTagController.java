/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.web.controller.BasicController;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.services.JSONStructuredOutput;

import java.util.Collection;

/**
 * Provides the glue logic between the <tt>taggedSearch</tt> component and the {@link QueryTagSuggester}s.
 * <p>
 * Provides an JSON service which collects all suggestions provided by the available suggesters.
 */
@Register
public class QueryTagController extends BasicController {

    @Part
    private Mixing mixing;

    @Parts(QueryTagSuggester.class)
    private Collection<QueryTagSuggester> suggesters;

    /**
     * Provides suggestions for the given entity type and query.
     *
     * @param ctx  the current request
     * @param out  the JSON response
     * @param type the entity type for provide suggestions for
     */
    @LoginRequired
    @SuppressWarnings("unchecked")
    @Routed(value = "/system/search/suggestions/:1", jsonCall = true)
    public void suggestions(WebContext ctx, JSONStructuredOutput out, String type) {
        String query = ctx.get("query").asString();
        out.beginArray("suggestions");
        if (Strings.isFilled(query)) {
            Class<?> entityType = mixing.findDescriptor(type).map(EntityDescriptor::getType).orElse(null);
            for (QueryTagSuggester suggester : suggesters) {
                suggester.computeQueryTags(type, (Class<? extends BaseEntity<?>>) entityType, query, tag -> {
                    out.beginObject("suggestion");
                    out.property("name", tag.getLabel());
                    out.property("color", tag.getColor());
                    out.property("value", tag.toString());
                    out.endObject();
                });
            }
        }
        out.endArray();
    }
}
