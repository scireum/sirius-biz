/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantSearchProvider;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.QueryField;
import sirius.db.mongo.Mango;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Makes {@link MongoTenant tenants} visible in the {@link sirius.biz.tycho.search.OpenSearchController}.
 * <p>
 * This additionally provides a secondary action to "select" / become a tenant.
 */
@Register(framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoTenantSearchProvider extends TenantSearchProvider<String, MongoTenant> {

    @Part
    private Mango mango;

    @Override
    protected Query<?, MongoTenant, ?> createBaseQuery(String query) {
        return mango.select(MongoTenant.class)
                    .orderAsc(Tenant.TENANT_DATA.inner(TenantData.NAME))
                    .queryString(query, QueryField.startsWith(MongoTenant.SEARCH_PREFIXES));
    }
}
