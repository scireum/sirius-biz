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
import sirius.db.mixing.Entity;
import sirius.db.mixing.OMA;
import sirius.db.mixing.SmartQuery;
import sirius.db.mixing.constraints.Like;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.web.controller.Facet;
import sirius.web.controller.Page;
import sirius.web.http.WebContext;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Created by aha on 08.05.15.
 */
public class PageHelper<E extends Entity> {

    private static final int PAGE_SIZE = 50;
    private WebContext ctx;
    private SmartQuery<E> baseQuery;
    private Column[] searchFields;
    private List<Tuple<Facet, BiConsumer<Facet, SmartQuery<E>>>> facets = Lists.newArrayList();

    public static <E extends Entity> PageHelper<E> withQuery(SmartQuery<E> baseQuery) {
        PageHelper<E> result = new PageHelper<E>();
        result.baseQuery = baseQuery;
        return result;
    }

    @Part
    private static Tenants tenants;

    public PageHelper<E> forCurrentTenant() {
        this.baseQuery.eq(TenantAware.TENANT, tenants.getRequiredTenant());
        return this;
    }

    public PageHelper<E> withContext(WebContext ctx) {
        this.ctx = ctx;
        return this;
    }

    public PageHelper<E> withSearchFields(Column... searchFields) {
        this.searchFields = searchFields;
        return this;
    }

    private PageHelper() {

    }

    public PageHelper<E> addFilterFacet(Facet facet) {
        return addFacet(facet, (f, q) -> q.eqIgnoreNull(Column.named(f.getName()), f.getValue()));
    }

    public PageHelper<E> addFacet(Facet facet, BiConsumer<Facet, SmartQuery<E>> filter) {
        return addFacet(facet, filter, null);
    }

    public PageHelper<E> addFacet(Facet facet,
                                  BiConsumer<Facet, SmartQuery<E>> filter,
                                  BiConsumer<Facet, SmartQuery<E>> itemsComputer) {
        Objects.requireNonNull(baseQuery);
        Objects.requireNonNull(ctx);

        facet.withValue(ctx.get(facet.getName()).asString());
        filter.accept(facet, baseQuery);

        facets.add(Tuple.create(facet, itemsComputer));

        return this;
    }

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

    public PageHelper<E> addQueryFacet(String name, String title, Function<SmartQuery<E>, SQLQuery> queryTransformer) {
        return addFacet(new Facet(title, name, null, null), (f, q) -> {
            if (Strings.isFilled(f.getValue())) {
                q.eq(Column.named(f.getName()), f.getValue());
            }
        }, (f, q) -> {
            try {
                SQLQuery qry = queryTransformer.apply(q);
                qry.iterateAll(r -> f.addItem(r.getValue(r.getFieldNames().get(0)).asString(),
                                              r.getValue(r.getFieldNames().get(1)).asString(),
                                              -1), new Limit(0, 100));
            } catch (SQLException e) {
                Exceptions.handle(OMA.LOG, e);
            }
        });
    }

    public Page<E> asPage() {
        Objects.requireNonNull(ctx);
        Watch w = Watch.start();
        Page<E> result = new Page<E>().withStart(1);
        result.bindToRequest(ctx);
        if (searchFields != null && searchFields.length > 0) {
            baseQuery.where(Like.allWordsInAnyField(result.getQuery(), searchFields));
        }

        for (Tuple<Facet, BiConsumer<Facet, SmartQuery<E>>> f : facets) {
            if (f.getSecond() != null) {
                f.getSecond().accept(f.getFirst(), baseQuery);
            }
            result.addFacet(f.getFirst());
        }

        List<E> items = baseQuery.skip(result.getStart() - 1).limit(PAGE_SIZE + 1).queryList();
        if (items.size() > PAGE_SIZE) {
            result.withHasMore(true);
            items.remove(items.size() - 1);
        }
        result.withDuration(w.duration());
        return result.withItems(items);
    }
}
