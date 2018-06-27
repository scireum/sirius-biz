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
import sirius.db.es.constraints.ElasticConstraint;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.Mapping;
import sirius.kernel.cache.ValueComputer;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Watch;
import sirius.web.controller.Facet;
import sirius.web.controller.FacetItem;
import sirius.web.controller.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Implements a page helper for {@link ElasticQuery elastic queries}.
 * <p>
 * Most notably this supports various aggregations for facets out of the box.
 *
 * @param <E> the generic type of the entities being queried
 */
public class ElasticPageHelper<E extends ElasticEntity>
        extends BasePageHelper<E, ElasticConstraint, ElasticQuery<E>, ElasticPageHelper<E>> {

    private static final ValueComputer<String, String> IDENTITY = key -> key;

    private List<Tuple<Facet, ValueComputer<String, String>>> aggregatingFacets = new ArrayList<>();

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

    /**
     * Adds a automatic facet for values in the given field.
     *
     * @param field the field to aggregate on
     * @return the helper itself for fluent method calls
     */
    public ElasticPageHelper<E> addTermAggregation(Mapping field) {
        return addTermAggregation(baseQuery.getDescriptor().findProperty(field.toString()).getLabel(),
                                  field,
                                  null,
                                  ElasticQuery.DEFAULT_TERM_AGGREGATION_BUCKET_COUNT);
    }

    /**
     * Adds a automatic facet for values in the given field.
     *
     * @param field           the field to aggregate on
     * @param numberOfBuckets the maximal number of buckets collect and return
     * @return the helper itself for fluent method calls
     */
    public ElasticPageHelper<E> addTermAggregation(Mapping field, int numberOfBuckets) {
        return addTermAggregation(baseQuery.getDescriptor().findProperty(field.toString()).getLabel(),
                                  field,
                                  null,
                                  numberOfBuckets);
    }

    /**
     * Adds a time series based aggregation.
     *
     * @param field  the field to filter on
     * @param ranges the ranges which are supported as filter values
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public ElasticPageHelper<E> addTimeAggregation(Mapping field, DateRange... ranges) {
        return addTimeAggregation(field, baseQuery.getDescriptor().findProperty(field.toString()).getLabel(), ranges);
    }

    /**
     * Adds a time series based aggregation.
     *
     * @param field  the field to filter on
     * @param title  the title of the filter shown to the user
     * @param ranges the ranges which are supported as filter values
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public ElasticPageHelper<E> addTimeAggregation(Mapping field, String title, DateRange... ranges) {
        Facet facet = createTimeFacet(field.toString(), title, ranges);
        baseQuery.addDateAggregation(field.toString(), field, Arrays.asList(ranges));
        aggregatingFacets.add(Tuple.create(facet, null));

        return this;
    }

    /**
     * Adds a automatic facet for values in the given field.
     *
     * @param title           the title to use for the facet
     * @param field           the field to aggregate on
     * @param translator      the translator used to convert field values into filter labels
     * @param numberOfBuckets the maximal number of buckets collect and return
     * @return the helper itself for fluent method calls
     */
    public ElasticPageHelper<E> addTermAggregation(String title,
                                                   Mapping field,
                                                   ValueComputer<String, String> translator,
                                                   int numberOfBuckets) {
        Facet facet = new Facet(title, field.toString(), null, translator);
        addFilterFacet(facet);
        aggregatingFacets.add(Tuple.create(facet, translator));
        baseQuery.addTermAggregation(field.toString(), field, numberOfBuckets);

        return this;
    }

    @Override
    protected void fillPage(Watch w, Page<E> result, List<E> items) {
        super.fillPage(w, result, items);

        for (Tuple<Facet, ValueComputer<String, String>> facetAndTranslator : aggregatingFacets) {
            Facet facet = facetAndTranslator.getFirst();
            ValueComputer<String, String> translator = facetAndTranslator.getSecond();
            if (translator == null) {
                translator = IDENTITY;
            }
            if (facet.getItems().isEmpty()) {
                for (Tuple<String, Integer> bucket : baseQuery.getAggregationBuckets(facet.getName())) {
                    facet.addItem(bucket.getFirst(), translator.compute(bucket.getFirst()), bucket.getSecond());
                }
            } else {
                Map<String, Integer> counters = Tuple.toMap(baseQuery.getAggregationBuckets(facet.getName()));
                for (FacetItem item : facet.getItems()) {
                    item.setCount(counters.getOrDefault(item.getKey(), 0));
                }
            }
        }
    }
}
