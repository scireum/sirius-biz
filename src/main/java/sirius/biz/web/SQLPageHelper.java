/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SQLQuery;
import sirius.db.jdbc.SmartQuery;
import sirius.db.jdbc.constraints.SQLConstraint;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.health.Exceptions;
import sirius.web.controller.Facet;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Implements a page helper for {@link SmartQuery smart queries}.
 *
 * @param <E> the generic type of the entities being queried
 */
public class SQLPageHelper<E extends SQLEntity>
        extends BasePageHelper<E, SQLConstraint, SmartQuery<E>, SQLPageHelper<E>> {

    @PriorityParts(SQLPageHelperExtender.class)
    private static List<SQLPageHelperExtender<?>> extenders;

    protected SQLPageHelper(SmartQuery<E> query) {
        super(query);
    }

    /**
     * Creates a new instance with the given base query.
     *
     * @param baseQuery the initial query to execute
     * @param <E>       the generic entity type being queried
     * @return a new instance operating on the given base query
     */
    public static <E extends SQLEntity> SQLPageHelper<E> withQuery(SmartQuery<E> baseQuery) {
        return new SQLPageHelper<>(baseQuery);
    }

    /**
     * Adds a query based filter which uses the given query to determine which filter items are shown.
     *
     * @param name             the name of the field to filter on
     * @param title            the title of the filter shown to the user
     * @param queryTransformer used to generate the sub-query which determines which filter values to show
     * @return the helper itself for fluent method calls
     */
    public SQLPageHelper<E> addQueryFacet(String name,
                                          String title,
                                          Function<SmartQuery<E>, SQLQuery> queryTransformer) {
        return addQueryFacet(name, title, queryTransformer, UnaryOperator.identity());
    }

    /**
     * Adds a query based filter which uses the given query to determine which filter items are shown.
     *
     * @param name             the name of the field to filter on
     * @param title            the title of the filter shown to the user
     * @param queryTransformer used to generate the sub-query which determines which filter values to show
     * @param labelProvider    used to customize the values used as label
     * @return the helper itself for fluent method calls
     */
    public SQLPageHelper<E> addQueryFacet(String name,
                                          String title,
                                          Function<SmartQuery<E>, SQLQuery> queryTransformer,
                                          UnaryOperator<String> labelProvider) {
        return addFacet(new Facet(title, name), (facet, query) -> {
            if (Strings.isFilled(facet.getValue())) {
                query.eq(Mapping.named(facet.getName()), facet.getValue());
            }
        }, (facet, query) -> {
            try {
                SQLQuery qry = queryTransformer.apply(query);
                qry.iterateAll(r -> {
                    Iterator<Tuple<String, Object>> iter = r.getFieldsList().iterator();
                    if (!iter.hasNext()) {
                        return;
                    }
                    String key = Value.of(iter.next().getSecond()).asString();
                    String label = key;
                    if (iter.hasNext()) {
                        label = Value.of(iter.next().getSecond()).asString();
                    }
                    facet.addItem(key, labelProvider.apply(label), -1);
                }, new Limit(0, 100));
            } catch (SQLException exception) {
                Exceptions.handle(OMA.LOG, exception);
            }
        });
    }

    /**
     * Applies all {@link SQLPageHelperExtender extenders} which are registered for the given name and the target
     * type of this page helper.
     *
     * @param extensionName the name of extensions to trigger (as given in
     *                      {@link SQLPageHelperExtender#getTargetExtension()}).
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public SQLPageHelper<E> applyExtenders(String extensionName) {
        extenders.stream()
                 .filter(extender -> Strings.areEqual(extensionName, extender.getTargetExtension()))
                 .filter(extender -> extender.getTargetType().isAssignableFrom(baseQuery.getDescriptor().getType()))
                 .forEach(extender -> ((SQLPageHelperExtender<E>) extender).extend(this));

        return this;
    }
}
