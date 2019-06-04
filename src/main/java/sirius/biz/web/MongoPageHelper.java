/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.Mapping;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.MongoQuery;
import sirius.db.mongo.constraints.MongoConstraint;
import sirius.db.mongo.facets.MongoTermFacet;
import sirius.kernel.cache.ValueComputer;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.web.controller.Facet;
import sirius.web.controller.Page;

import java.util.Arrays;
import java.util.List;

/**
 * Implements a page helper for {@link MongoQuery MongoDB queries}.
 *
 * @param <E> the generic type of the entities being queried
 */
public class MongoPageHelper<E extends MongoEntity>
        extends BasePageHelper<E, MongoConstraint, MongoQuery<E>, MongoPageHelper<E>> {

    @Part
    private Mongo mongo;

    protected MongoPageHelper(MongoQuery<E> query) {
        super(query);
    }

    /**
     * Adds a automatic facet for values in the given field.
     *
     * @param field the field to aggregate on
     * @return the helper itself for fluent method calls
     */
    public MongoPageHelper<E> addTermAggregation(Mapping field) {
        return addTermAggregation(baseQuery.getDescriptor().findProperty(field.toString()).getLabel(), field, null);
    }

    /**
     * Adds a automatic facet for values in the given field.
     *
     * @param field      the field to aggregate on
     * @param translator the translator used to convert field values into filter labels
     * @return the helper itself for fluent method calls
     */
    public MongoPageHelper<E> addTermAggregation(Mapping field, ValueComputer<String, String> translator) {
        return addTermAggregation(baseQuery.getDescriptor().findProperty(field.toString()).getLabel(),
                                  field,
                                  translator);
    }

    /**
     * Adds a automatic facet for values in the given field.
     *
     * @param field    the field to aggregate on
     * @param enumType the type of enums in this field used for proper translation
     * @return the helper itself for fluent method calls
     */
    public MongoPageHelper<E> addTermAggregation(Mapping field, Class<? extends Enum<?>> enumType) {
        return addTermAggregation(baseQuery.getDescriptor().findProperty(field.toString()).getLabel(),
                                  field,
                                  value -> Arrays.stream(enumType.getEnumConstants())
                                                 .filter(enumConst -> Strings.areEqual(enumConst.name(), value))
                                                 .findFirst()
                                                 .map(Object::toString)
                                                 .orElse(value));
    }

    /**
     * Adds a automatic facet for values in the given field.
     *
     * @param title      the title to use for the facet
     * @param field      the field to aggregate on
     * @param translator the translator used to convert field values into filter labels
     * @return the helper itself for fluent method calls
     */
    public MongoPageHelper<E> addTermAggregation(String title,
                                                 Mapping field,
                                                 ValueComputer<String, String> translator) {
        ValueComputer<String, String> nonNullTranslator = translator == null ? key -> key : translator;

        Facet facet = new Facet(title, field.toString(), null, translator);
        addFilterFacet(facet);

        baseQuery.addFacet(new MongoTermFacet(field).onComplete(mongoFacet -> {
            if (mongoFacet.getValues().isEmpty()) {
                // If we didn't find any aggregation value we have to check if a filter for this
                // facet is active and artificially create an "0" item for this so that it can be
                // disabled...
                String filterValue = ctx.get(facet.getName()).asString();
                if (Strings.isFilled(filterValue)) {
                    facet.addItem(filterValue, nonNullTranslator.compute(filterValue), 0);
                }
            } else {
                for (Tuple<String, Integer> value : mongoFacet.getValues()) {
                    facet.addItem(value.getFirst(), nonNullTranslator.compute(value.getFirst()), value.getSecond());
                }
            }
        }));

        return this;
    }

    /**
     * Creates a new instance with the given base query.
     *
     * @param baseQuery the initial query to execute
     * @param <E>       the generic entity type being queried
     * @return a new instance operating on the given base query
     */
    public static <E extends MongoEntity> MongoPageHelper<E> withQuery(MongoQuery<E> baseQuery) {
        return new MongoPageHelper<>(baseQuery);
    }

    @Override
    protected void fillPage(Watch w, Page<E> result, List<E> items) {
        baseQuery.executeFacets();
        super.fillPage(w, result, items);
    }
}
