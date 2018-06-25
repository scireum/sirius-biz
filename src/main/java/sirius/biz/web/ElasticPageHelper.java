/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.es.ElasticEntity;
import sirius.db.es.ElasticQuery;
import sirius.db.mixing.Mapping;
import sirius.kernel.cache.ValueComputer;
import sirius.web.controller.Facet;
import sirius.web.controller.Page;
import sirius.web.http.WebContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to build a query, bind it to values given in a {@link WebContext} and create a resulting {@link Page}
 * which can be used to render a resulting table and filter box.
 *
 * @param <E> the generic type of the entities being queried
 */
public class ElasticPageHelper<E extends ElasticEntity>
        extends BasePageHelper<E, ElasticQuery<E>, ElasticPageHelper<E>> {

    private Map<String, Facet> aggregatingFacets = new HashMap<>();

    protected ElasticPageHelper(ElasticQuery<E> query) {
        super(query);
    }

    /**
     * Creates a new instance with the given base query.
     *
     * @param baseQuery the initial query to execute
     * @param <E>       the generic entity type being queried
     * @return a new instance operating on the given base query
     */
    public static <E extends ElasticEntity> ElasticPageHelper<E> withQuery(ElasticQuery<E> baseQuery) {
        return new ElasticPageHelper<>(baseQuery);
    }
//
//    public ElasticPageHelper<E> addTermAggregation(Mapping field) {
//    }
//
//    public ElasticPageHelper<E> addTermAggregation(String title,
//                                                   Mapping field,
//                                                   ValueComputer<String, String> translator) {
//        Facet facet = new Facet(title, field.toString(), null, translator);
//        addFilterFacet(facet);
//        aggregatingFacets.put(field.toString(), facet);
//        baseQuery.addTermAggregation(field);
//
//        return this;
//    }
}
