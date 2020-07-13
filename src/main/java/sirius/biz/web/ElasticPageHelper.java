/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.es.AggregationBuilder;
import sirius.db.es.ElasticEntity;
import sirius.db.es.ElasticQuery;
import sirius.db.es.constraints.ElasticConstraint;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.Mapping;
import sirius.kernel.cache.ValueComputer;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Watch;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Facet;
import sirius.web.controller.FacetItem;
import sirius.web.controller.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
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
                                  AggregationBuilder.DEFAULT_TERM_AGGREGATION_BUCKET_COUNT);
    }

    /**
     * Adds a automatic facet for values in the given field.
     *
     * @param field      the field to aggregate on
     * @param translator the translator used to convert field values into filter labels
     * @return the helper itself for fluent method calls
     */
    public ElasticPageHelper<E> addTermAggregation(Mapping field, ValueComputer<String, String> translator) {
        return addTermAggregation(baseQuery.getDescriptor().findProperty(field.toString()).getLabel(),
                                  field,
                                  translator,
                                  AggregationBuilder.DEFAULT_TERM_AGGREGATION_BUCKET_COUNT);
    }

    /**
     * Adds a automatic facet for values in the given field.
     *
     * @param field    the field to aggregate on
     * @param enumType the type of enums in this field used for proper translation
     * @return the helper itself for fluent method calls
     */
    public ElasticPageHelper<E> addTermAggregation(Mapping field, Class<? extends Enum<?>> enumType) {
        return addTermAggregation(baseQuery.getDescriptor().findProperty(field.toString()).getLabel(),
                                  field,
                                  value -> Arrays.stream(enumType.getEnumConstants())
                                                 .filter(enumConst -> Strings.areEqual(enumConst.name(), value))
                                                 .findFirst()
                                                 .map(Object::toString)
                                                 .orElse(value),
                                  AggregationBuilder.DEFAULT_TERM_AGGREGATION_BUCKET_COUNT);
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
     * @param field        the field to filter on
     * @param useLocalDate determines if the filter should be applied as {@link java.time.LocalDate} (<tt>true</tt>)
     *                     or as {@link java.time.LocalDateTime} (<tt>false</tt>). This is crucial, as these
     *                     are entirely differently encoded in the database.
     * @param ranges       the ranges which are supported as filter values
     * @return the helper itself for fluent method calls
     */
    public ElasticPageHelper<E> addTimeAggregation(Mapping field, boolean useLocalDate, DateRange... ranges) {
        return addTimeAggregation(field,
                                  useLocalDate,
                                  baseQuery.getDescriptor().findProperty(field.toString()).getLabel(),
                                  ranges);
    }

    /**
     * Adds a time series based aggregation.
     *
     * @param field        the field to filter on
     * @param title        the title of the filter shown to the user
     * @param useLocalDate determines if the filter should be applied as {@link java.time.LocalDate} (<tt>true</tt>)
     *                     or as {@link java.time.LocalDateTime} (<tt>false</tt>). This is crucial, as these
     *                     are entirely differently encoded.
     * @param ranges       the ranges which are supported as filter values
     * @return the helper itself for fluent method calls
     */
    public ElasticPageHelper<E> addTimeAggregation(Mapping field,
                                                   boolean useLocalDate,
                                                   String title,
                                                   DateRange... ranges) {
        Facet facet = createTimeFacet(field.toString(), title, useLocalDate, ranges);
        baseQuery.addDateAggregation(field.toString(), field, Arrays.asList(ranges));
        aggregatingFacets.add(Tuple.create(facet, null));

        return this;
    }

    /**
     * Adds a automatic facet for a boolean field.
     *
     * @param field the field to aggregate on
     * @return the helper itself for fluent method calls
     */
    public ElasticPageHelper<E> addBooleanAggregation(Mapping field) {
        Facet facet = new Facet(baseQuery.getDescriptor().findProperty(field.toString()).getLabel(),
                                field.toString(),
                                null,
                                null);

        aggregatingFacets.add(Tuple.create(facet, null));
        baseQuery.addTermAggregation(field.toString(), field, 3);

        return addFacet(facet, (f, q) -> {
            f.addItem("1", NLS.get("NLS.yes"), -1);
            f.addItem("0", NLS.get("NLS.no"), -1);

            Value filterValue = Value.of(f.getValue());
            if (filterValue.isFilled()) {
                q.eq(Mapping.named(f.getName()), Strings.areEqual(f.getValue(), "1"));
            }
        });
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
            fillFacet(facet, translator);
        }
    }

    private void fillFacet(Facet facet, ValueComputer<String, String> translator) {
        if (facet.getItems().isEmpty()) {
            fillWithAggregationResults(facet, translator);
        } else {
            enhanceWithAggregationCounts(facet);
        }
    }

    private void enhanceWithAggregationCounts(Facet facet) {
        Map<String, Integer> counters = Tuple.toMap(baseQuery.getAggregation(facet.getName()).getTermCounts());
        Iterator<FacetItem> iter = facet.getAllItems().iterator();
        while (iter.hasNext()) {
            FacetItem item = iter.next();
            int numberOfHits = counters.getOrDefault(item.getKey(), 0);
            if (numberOfHits > 0 || item.isActive()) {
                item.setCount(numberOfHits);
            } else {
                // If the item has no matches and isn't an active filter - remove as
                // it is unneccessary...
                iter.remove();
            }
        }
    }

    private void fillWithAggregationResults(Facet facet, ValueComputer<String, String> translator) {
        baseQuery.getAggregation(facet.getName()).forEachBucket(bucket -> {
            facet.addItem(bucket.getKey(), translator.compute(bucket.getKey()), bucket.getDocCount());
        });

        if (facet.getItems().isEmpty()) {
            // If we didn't find any aggregation value we have to check if a filter for this
            // facet is active and artificially create an "0" item for this so that it can be
            // disabled...
            String filterValue = getParameterValue(facet.getName()).getString();
            if (Strings.isFilled(filterValue)) {
                facet.addItem(filterValue, translator.compute(filterValue), 0);
            }
        }
    }
}
