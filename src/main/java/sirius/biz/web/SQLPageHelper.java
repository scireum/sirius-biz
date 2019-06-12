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
import sirius.web.controller.Page;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

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
        return addFacet(new Facet(title, name, null, null), (f, q) -> {
            if (Strings.isFilled(f.getValue())) {
                q.eq(Mapping.named(f.getName()), f.getValue());
            }
        }, (f, q) -> {
            try {
                SQLQuery qry = queryTransformer.apply(q);
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
                    f.addItem(key, label, -1);
                }, new Limit(0, 100));
            } catch (SQLException e) {
                Exceptions.handle(OMA.LOG, e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Page<E> asPage() {
        for (SQLPageHelperExtender<?> extender : extenders) {
            if (extender.getTargetType().isAssignableFrom(baseQuery.getDescriptor().getType())) {
                ((SQLPageHelperExtender<E>) extender).extend(this);
            }
        }
        return super.asPage();
    }
}
