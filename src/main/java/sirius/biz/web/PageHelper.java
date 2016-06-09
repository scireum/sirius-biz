/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.tenants.TenantAware;
import sirius.biz.tenants.Tenants;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.db.mixing.Column;
import sirius.db.mixing.Entity;
import sirius.db.mixing.SmartQuery;
import sirius.db.mixing.constraints.Like;
import sirius.web.controller.Page;
import sirius.web.http.WebContext;

import java.util.List;

/**
 * Created by aha on 08.05.15.
 */
public class PageHelper<E extends Entity> {

    private static final int PAGE_SIZE = 50;
    private WebContext ctx;
    private SmartQuery<E> baseQuery;
    private Column[] searchFields;

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


    public Page<E> asPage() {
        if (ctx == null) {
            throw new IllegalStateException("no web context present");
        }
        Watch w = Watch.start();
        Page<E> result = new Page<E>().withStart(1);
        result.bindToRequest(ctx);
        if (searchFields != null && searchFields.length > 0) {
            baseQuery.where(Like.allWordsInAnyField(result.getQuery(), searchFields));
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
