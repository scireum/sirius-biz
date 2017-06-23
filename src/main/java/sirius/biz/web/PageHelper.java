/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import com.google.common.collect.Lists;
import sirius.biz.tenants.TenantAware;
import sirius.biz.tenants.Tenants;
import sirius.db.jdbc.SQLQuery;
import sirius.db.mixing.Column;
import sirius.db.mixing.Constraint;
import sirius.db.mixing.Entity;
import sirius.db.mixing.OMA;
import sirius.db.mixing.SmartQuery;
import sirius.db.mixing.constraints.Like;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Facet;
import sirius.web.controller.Page;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Helper class to build a query, bind it to values given in a {@link WebContext} and create a resulting {@link Page}
 * which can be used to render a resulting table and filter box.
 *
 * @param <E> the generic type of the entities being queries
 */
public class PageHelper<E extends Entity> {

    private static final int PAGE_SIZE = 50;
    private WebContext ctx;
    private SmartQuery<E> baseQuery;
    private Column[] searchFields;
    private boolean advancedSearch;
    private List<Tuple<Facet, BiConsumer<Facet, SmartQuery<E>>>> facets = Lists.newArrayList();

    @Part
    private static Tenants tenants;

    private PageHelper() {
    }

    /**
     * Creates a new instance with the given base query.
     *
     * @param baseQuery the initial query to execute
     * @param <E>       the generic entity type being queried
     * @return a new instance operating on the given base query
     */
    public static <E extends Entity> PageHelper<E> withQuery(SmartQuery<E> baseQuery) {
        PageHelper<E> result = new PageHelper<>();
        result.baseQuery = baseQuery;
        return result;
    }

    /**
     * Filters the results to only contain entities which belong to the current tenant.
     * <p>
     * The entity type must extend {@link TenantAware} for this to work.
     *
     * @return the helper itself for fluent method calls
     */
    public PageHelper<E> forCurrentTenant() {
        this.baseQuery.eq(TenantAware.TENANT, tenants.getRequiredTenant());
        return this;
    }

    /**
     * Attaches a web context to the helper, to fetch filter and pagination values from.
     *
     * @param ctx the request to attach
     * @return the helper itself for fluent method calls
     */
    public PageHelper<E> withContext(WebContext ctx) {
        this.ctx = ctx;
        return this;
    }

    /**
     * Specifies one or more search fields which will be searched if a <tt>query</tt>
     * if given in the <tt>WebContext</tt>.
     *
     * @param searchFields the fields to search in
     * @return the helper itself for fluent method calls
     */
    public PageHelper<E> withSearchFields(Column... searchFields) {
        this.searchFields = searchFields;
        return this;
    }

    /**
     * Enables the {@link QueryCompiler} which supports SQL like queries and {@link QueryTag}s.
     *
     * @return the helper itself for fluent method calls
     */
    public PageHelper<E> enableAdvancedSearch() {
        this.advancedSearch = true;
        return this;
    }

    /**
     * Adds a filter facet which will show distinct values of the given property.
     *
     * @param facet the facet to add
     * @return the helper itself for fluent method calls
     */
    public PageHelper<E> addFilterFacet(Facet facet) {
        return addFacet(facet, (f, q) -> q.eqIgnoreNull(Column.named(f.getName()), f.getValue()));
    }

    /**
     * Adds a filter facet which a custom filter implementation.
     *
     * @param facet  the facet to add
     * @param filter the custom logic which determines how a filter value is applied to the query.
     * @return the helper itself for fluent method calls
     */
    public PageHelper<E> addFacet(Facet facet, BiConsumer<Facet, SmartQuery<E>> filter) {
        return addFacet(facet, filter, null);
    }

    /**
     * Adds a filter facet with custom filter implementation and a custom item computer.
     *
     * @param facet         the facet to add
     * @param filter        the custom logic which determines how a filter value is applied to the query.
     * @param itemsComputer the custom logic which determines the list of items in the filter
     * @return the helper itself for fluent method calls
     */
    public PageHelper<E> addFacet(Facet facet,
                                  BiConsumer<Facet, SmartQuery<E>> filter,
                                  BiConsumer<Facet, SmartQuery<E>> itemsComputer) {
        Objects.requireNonNull(baseQuery);
        Objects.requireNonNull(ctx);

        facet.withValue(ctx.get(facet.getName()).asString(null));
        filter.accept(facet, baseQuery);

        facets.add(Tuple.create(facet, itemsComputer));

        return this;
    }

    /**
     * Adds a time series based filter which permits to filter on certain time ranges.
     *
     * @param name   the name of the field to filter on
     * @param title  the title of the filter shown to the user
     * @param ranges the ranges which are supported as filter values
     * @return the helper itself for fluent method calls
     */
    public PageHelper<E> addTimeFacet(String name, String title, DateRange... ranges) {
        Facet facet = new Facet(title, name, null, null);
        addFacet(facet, (f, q) -> {
            for (DateRange range : ranges) {
                if (Strings.areEqual(f.getValue(), range.getKey())) {
                    range.applyTo(name, q);
                }
            }
        });
        for (DateRange range : ranges) {
            facet.addItem(range.getKey(), range.toString(), -1);
        }

        return this;
    }

    /**
     * Adds a query based filter which uses the given query to determine which filter items are shown.
     *
     * @param name             the name of the field to filter on
     * @param title            the title of the filter shown to the user
     * @param queryTransformer used to generate the sub-query which determines which filter values to show
     * @return the helper itself for fluent method calls
     */
    public PageHelper<E> addQueryFacet(String name, String title, Function<SmartQuery<E>, SQLQuery> queryTransformer) {
        return addFacet(new Facet(title, name, null, null), (f, q) -> {
            if (Strings.isFilled(f.getValue())) {
                q.eq(Column.named(f.getName()), f.getValue());
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

    /**
     * Adds a boolean based filter which permits to filter on boolean values.
     *
     * @param name  the name of the field to filter on
     * @param title the title of the filter shown to the user
     * @return the helper itself for fluent method calls
     */
    public PageHelper<E> addBooleanFacet(String name, String title) {
        Objects.requireNonNull(ctx);

        Facet facet = new Facet(title, name, ctx.get(name).asString(), null);
        facet.addItem("true", NLS.get("NLS.yes"), -1);
        facet.addItem("false", NLS.get("NLS.no"), -1);

        return addFacet(facet, (f, q) -> {
            Value isUsed = Value.of(f.getValue());
            if (isUsed.isFilled()) {
                q.eqIgnoreNull(Column.named(f.getName()), isUsed.asBoolean());
            }
        });
    }

    /**
     * Wraps the given data into a {@link Page} which can be used to render a table, filterbox and support pagination.
     *
     * @return the given data wrapped as <tt>Page</tt>
     */
    public Page<E> asPage() {
        Objects.requireNonNull(ctx);
        Watch w = Watch.start();
        Page<E> result = new Page<E>().withStart(1).withPageSize(PAGE_SIZE);
        result.bindToRequest(ctx);

        if (advancedSearch) {
            QueryCompiler compiler =
                    new QueryCompiler(baseQuery.getEntityDescriptor(), result.getQuery(), searchFields);
            Constraint constraint = compiler.compile();
            if (constraint != null) {
                baseQuery.where(constraint);
            }
        } else {
            if (searchFields != null && searchFields.length > 0) {
                baseQuery.where(Like.allWordsInAnyField(result.getQuery(), searchFields));
            }
        }

        for (Tuple<Facet, BiConsumer<Facet, SmartQuery<E>>> f : facets) {
            if (f.getSecond() != null) {
                f.getSecond().accept(f.getFirst(), baseQuery);
            }
            result.addFacet(f.getFirst());
        }

        try {
            List<E> items = baseQuery.skip(result.getStart() - 1).limit(PAGE_SIZE + 1).queryList();
            if (items.size() > PAGE_SIZE) {
                result.withHasMore(true);
                items.remove(items.size() - 1);
            }
            result.withDuration(w.duration());
            result.withItems(items);
        } catch (Exception e) {
            UserContext.handle(e);
        }

        return result;
    }
}
