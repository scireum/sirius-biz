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
import sirius.web.services.InternalService;
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
     * @param webContext the current request
     * @param output     the JSON response
     * @param type       the entity type for provide suggestions for
     */
    @LoginRequired
    @SuppressWarnings("unchecked")
    @Routed("/system/search/suggestions/:1")
    @InternalService
    public void suggestions(WebContext webContext, JSONStructuredOutput output, String type) {
        String query = webContext.get("query").asString();
        output.beginArray("suggestions");
        if (Strings.isFilled(query)) {
            Class<?> entityType = mixing.findDescriptor(type).map(EntityDescriptor::getType).orElse(null);
            for (QueryTagSuggester suggester : suggesters) {
                suggester.computeQueryTags(type, (Class<? extends BaseEntity<?>>) entityType, query, tag -> {
                    output.beginObject("suggestion");
                    output.property("name", tag.getLabel());
                    output.property("color", tag.getColor());
                    output.property("value", tag.toString());
                    output.endObject();
                });
            }
        }
        output.endArray();
    }
}
