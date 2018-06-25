/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Query;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Watch;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Facet;
import sirius.web.controller.Page;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Helper class to build a query, bind it to values given in a {@link WebContext} and create a resulting {@link Page}
 * which can be used to render a resulting table and filter box.
 *
 * @param <E> the generic type of the entities being queried
 */
public class BasePageHelper<E extends BaseEntity<?>, Q extends Query<Q, E>, B extends BasePageHelper<E, Q, B>> {

    protected static final int DEFAULT_PAGE_SIZE = 25;
    protected WebContext ctx;
    protected Q baseQuery;
    protected Mapping[] searchFields;
    protected boolean advancedSearch;
    protected List<Tuple<Facet, BiConsumer<Facet, Q>>> facets = new ArrayList<>();
    protected int pageSize = DEFAULT_PAGE_SIZE;

    protected BasePageHelper(Q query) {
        this.baseQuery = query;
    }

    /**
     * Attaches a web context to the helper, to fetch filter and pagination values from.
     *
     * @param ctx the request to attach
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B withContext(WebContext ctx) {
        this.ctx = ctx;
        return (B) this;
    }

    /**
     * Specifies one or more search fields which will be searched if a <tt>query</tt>
     * if given in the <tt>WebContext</tt>.
     *
     * @param searchFields the fields to search in
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B withSearchFields(Mapping... searchFields) {
        this.searchFields = searchFields;
        return (B) this;
    }

    /**
     * Enables the {@link QueryCompiler} which supports SQL like queries and {@link QueryTag}s.
     *
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B enableAdvancedSearch() {
        this.advancedSearch = true;
        return (B) this;
    }

    /**
     * Adds a filter facet which will show distinct values of the given property.
     *
     * @param facet the facet to add
     * @return the helper itself for fluent method calls
     */
    public B addFilterFacet(Facet facet) {
        return addFacet(facet, (f, q) -> q.eqIgnoreNull(Mapping.named(f.getName()), f.getValue()));
    }

    /**
     * Adds a filter facet which a custom filter implementation.
     *
     * @param facet  the facet to add
     * @param filter the custom logic which determines how a filter value is applied to the query.
     * @return the helper itself for fluent method calls
     */
    public B addFacet(Facet facet, BiConsumer<Facet, Q> filter) {
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
    @SuppressWarnings("unchecked")
    public B addFacet(Facet facet, BiConsumer<Facet, Q> filter, BiConsumer<Facet, Q> itemsComputer) {
        Objects.requireNonNull(baseQuery);
        Objects.requireNonNull(ctx);

        facet.withValue(ctx.get(facet.getName()).getString());
        filter.accept(facet, baseQuery);

        facets.add(Tuple.create(facet, itemsComputer));

        return (B) this;
    }

    /**
     * Adds a time series based filter which permits to filter on certain time ranges.
     *
     * @param name   the name of the field to filter on
     * @param title  the title of the filter shown to the user
     * @param ranges the ranges which are supported as filter values
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B addTimeFacet(String name, String title, DateRange... ranges) {
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

        return (B) this;
    }

    /**
     * Adds a boolean based filter which permits to filter on boolean values.
     *
     * @param name  the name of the field to filter on
     * @param title the title of the filter shown to the user
     * @return the helper itself for fluent method calls
     */
    public B addBooleanFacet(String name, String title) {
        Objects.requireNonNull(ctx);

        Facet facet = new Facet(title, name, ctx.get(name).asString(), null);
        facet.addItem("true", NLS.get("NLS.yes"), -1);
        facet.addItem("false", NLS.get("NLS.no"), -1);

        return addFacet(facet, (f, q) -> {
            Value filterValue = Value.of(f.getValue());
            if (filterValue.isFilled()) {
                q.eq(Mapping.named(f.getName()), filterValue.asBoolean());
            }
        });
    }

    /**
     * Specifies the number of items shown on the page that gets rendered using this pageHelper.
     *
     * @param pageSize the number of items shown per page
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B withPageSize(int pageSize) {
        this.pageSize = pageSize;
        return (B) this;
    }

    /**
     * Wraps the given data into a {@link Page} which can be used to render a table, filterbox and support pagination.
     *
     * @return the given data wrapped as <tt>Page</tt>
     */
    public Page<E> asPage() {
        Objects.requireNonNull(ctx);
        Watch w = Watch.start();
        Page<E> result = new Page<E>().withStart(1).withPageSize(pageSize);
        result.bindToRequest(ctx);

//        if (advancedSearch) {
//            QueryCompiler compiler =
//                    new QueryCompiler(baseQuery.getEntityDescriptor(), result.getQuery(), searchFields);
//            Constraint constraint = compiler.compile();
//            if (constraint != null) {
//                baseQuery.eq(constraint);
//            }
//        } else {
//            if (searchFields != null && searchFields.length > 0) {
//                baseQuery.where(Like.allWordsInAnyField(result.getQuery(), searchFields));
//            }
//        }

        for (Tuple<Facet, BiConsumer<Facet, Q>> f : facets) {
            if (f.getSecond() != null) {
                f.getSecond().accept(f.getFirst(), baseQuery);
            }
            result.addFacet(f.getFirst());
        }

        try {
            baseQuery.skip(result.getStart() - 1);
            baseQuery.limit(pageSize + 1);
            List<E> items = baseQuery.queryList();
            if (items.size() > pageSize) {
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
