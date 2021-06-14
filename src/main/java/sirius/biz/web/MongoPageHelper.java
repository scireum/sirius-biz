/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.DateRange;
import sirius.db.mixing.Mapping;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.MongoQuery;
import sirius.db.mongo.constraints.MongoConstraint;
import sirius.db.mongo.facets.MongoBooleanFacet;
import sirius.db.mongo.facets.MongoDateRangeFacet;
import sirius.db.mongo.facets.MongoTermFacet;
import sirius.kernel.cache.ValueComputer;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Facet;
import sirius.web.controller.FacetItem;
import sirius.web.controller.Page;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Implements a page helper for {@link MongoQuery MongoDB queries}.
 *
 * @param <E> the generic type of the entities being queried
 */
public class MongoPageHelper<E extends MongoEntity>
        extends BasePageHelper<E, MongoConstraint, MongoQuery<E>, MongoPageHelper<E>> {

    @PriorityParts(MongoPageHelperExtender.class)
    private static List<MongoPageHelperExtender<?>> extenders;

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
     * Adds a time series based aggregation.
     *
     * @param field        the field to filter on
     * @param useLocalDate determines if the filter should be applied as {@link java.time.LocalDate} (<tt>true</tt>)
     *                     or as {@link java.time.LocalDateTime} (<tt>false</tt>). This is crucial, as these
     *                     are entirely differently encoded in the database.
     * @param ranges       the ranges which are supported as filter values
     * @return the helper itself for fluent method calls
     */
    public MongoPageHelper<E> addTimeAggregation(Mapping field, boolean useLocalDate, DateRange... ranges) {
        return addTimeAggregation(field,
                                  baseQuery.getDescriptor().findProperty(field.toString()).getLabel(),
                                  useLocalDate,
                                  ranges);
    }

    /**
     * Adds a time series based aggregation.
     *
     * @param field        the field to filter on
     * @param title        the title of the filter shown to the user
     * @param useLocalDate determines if the filter should be applied as {@link java.time.LocalDate} (<tt>true</tt>)
     *                     or as {@link java.time.LocalDateTime} (<tt>false</tt>). This is crucial, as these
     *                     are entirely differently encoded in the database.
     * @param ranges       the ranges which are supported as filter values
     * @return the helper itself for fluent method calls
     */
    public MongoPageHelper<E> addTimeAggregation(Mapping field,
                                                 String title,
                                                 boolean useLocalDate,
                                                 DateRange... ranges) {
        Facet facet = createTimeFacet(field.toString(), title, useLocalDate, ranges);

        baseQuery.addFacet(new MongoDateRangeFacet(field, Arrays.asList(ranges)).onComplete(mongoFacet -> {
            Iterator<FacetItem> iter = facet.getAllItems().iterator();
            while (iter.hasNext()) {
                FacetItem item = iter.next();
                int numberOfHits = mongoFacet.getRanges()
                                             .stream()
                                             .filter(rangeAndCount -> Strings.areEqual(item.getKey(),
                                                                                       rangeAndCount.getFirst()
                                                                                                    .getKey()))
                                             .map(Tuple::getSecond)
                                             .findFirst()
                                             .orElse(0);
                if (numberOfHits > 0 || item.isActive()) {
                    item.setCount(numberOfHits);
                } else {
                    // If the item has no matches and isn't an active filter - remove as
                    // it is unneccessary...
                    iter.remove();
                }
            }
        }));

        return this;
    }

    /**
     * Adds a automatic facet for a boolean field.
     *
     * @param field the field to aggregate on
     * @return the helper itself for fluent method calls
     */
    public MongoPageHelper<E> addBooleanAggregation(Mapping field) {
        Facet facet = new Facet(baseQuery.getDescriptor().findProperty(field.toString()).getLabel(),
                                field.toString(),
                                null,
                                null);

        return addFacet(facet, (f, q) -> {
            f.addItem("true", NLS.get("NLS.yes"), -1);
            f.addItem("false", NLS.get("NLS.no"), -1);

            Value filterValue = Value.of(f.getValue());
            if (filterValue.isFilled()) {
                q.eq(Mapping.named(f.getName()), filterValue.asBoolean());
            }

            addBooleanQueryFacet(q, facet, field);
        });
    }

    private void addBooleanQueryFacet(MongoQuery<E> query, Facet facet, Mapping field) {
        query.addFacet(new MongoBooleanFacet(field).onComplete(mongoFacet -> {
            Iterator<FacetItem> iter = facet.getAllItems().iterator();
            while (iter.hasNext()) {
                FacetItem item = iter.next();
                int numberOfHits =
                        Strings.areEqual(item.getKey(), "true") ? mongoFacet.getNumTrue() : mongoFacet.getNumFalse();
                if (numberOfHits > 0 || item.isActive()) {
                    item.setCount(numberOfHits);
                } else {
                    // If the item has no matches and isn't an active filter - remove as
                    // it is unneccessary...
                    iter.remove();
                }
            }
        }));
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
                String filterValue = getParameterValue(facet.getName()).getString();
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
        if (!baseQuery.isForceFail()) {
            baseQuery.executeFacets();
        }
        super.fillPage(w, result, items);
    }

    /**
     * Applies all {@link MongoPageHelperExtender extenders} which are registered for the given name and the target
     * type of this page helper.
     *
     * @param extensionName the name of extensions to trigger (as given in
     *                      {@link MongoPageHelperExtender#getTargetExtension()}).
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public MongoPageHelper<E> applyExtenders(String extensionName) {
        extenders.stream()
                 .filter(extender -> Strings.areEqual(extensionName, extender.getTargetExtension()))
                 .filter(extender -> extender.getTargetType().isAssignableFrom(baseQuery.getDescriptor().getType()))
                 .forEach(extender -> ((MongoPageHelperExtender<E>) extender).extend(this));

        return this;
    }
}
