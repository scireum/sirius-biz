/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.model.AddressData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantSearchProvider;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Makes {@link SQLTenant tenants} visible in the {@link sirius.biz.tycho.search.OpenSearchController}.
 * <p>
 * This additionally provides a secondary action to "select" / become a tenant.
 */
@Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLTenantSearchProvider extends TenantSearchProvider<Long, SQLTenant> {

    @Part
    private OMA oma;

    @Override
    protected Query<?, SQLTenant, ?> createBaseQuery(String query) {
        return oma.select(SQLTenant.class)
                  .orderAsc(Tenant.TENANT_DATA.inner(TenantData.NAME))
                  .queryString(query,
                               QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.NAME)),
                               QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER)),
                               QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.ADDRESS)
                                                                     .inner(AddressData.STREET)),
                               QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.ADDRESS)
                                                                     .inner(AddressData.CITY)));
    }
}
