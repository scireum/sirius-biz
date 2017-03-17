/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.db.mixing.Entity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Schema;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.HandledException;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.services.JSONStructuredOutput;

import java.util.Collection;

/**
 * Created by aha on 27.01.17.
 */
@Register
public class QueryTagController implements Controller {

    @Override
    public void onError(WebContext ctx, HandledException error) {
        ctx.respondWith().error(HttpResponseStatus.INTERNAL_SERVER_ERROR, error);
    }

    @Part
    private Schema schema;

    @Parts(QueryTagSuggester.class)
    private Collection<QueryTagSuggester> suggesters;

    @Routed(value = "/system/search/suggestions/:1", jsonCall = true)
    public void suggestions(WebContext ctx, JSONStructuredOutput out, String type) {
        String query = ctx.get("query").asString();
        out.beginArray("suggestions");
        if (Strings.isFilled(query)) {
            Class<? extends Entity> entityType =
                    schema.findDescriptor(type).map(EntityDescriptor::getType).orElse(null);
            for (QueryTagSuggester suggester : suggesters) {
                suggester.computeQueryTags(type, entityType, query, (tag) -> {
                    out.beginObject("suggestion");
                    out.property("name", tag.getLabel());
                    out.property("value", tag.toString());
                    out.endObject();
                });
            }
        }
        out.endArray();
    }
}
