/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.file.EntityExportJobFactory;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.query.Query;
import sirius.web.http.QueryString;

/**
 * Provides a base class for factories which export {@link Tenant tenants}.
 *
 * @param <E> the type of tenants being exported
 * @param <Q> the query type used to select tenants
 */
public abstract class TenantExportJobFactory<E extends BaseEntity<?> & Tenant<?>, Q extends Query<Q, E, ?>>
        extends EntityExportJobFactory<E, Q> {

    @Override
    public int getPriority() {
        return 9200;
    }

    @Override
    public String getCategory() {
        return StandardCategories.USERS_AND_TENANTS;
    }

    @Override
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return queryString.path().startsWith("/tenant");
    }
}
