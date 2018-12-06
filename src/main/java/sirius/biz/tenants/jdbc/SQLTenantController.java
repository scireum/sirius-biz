/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantController;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.jdbc.SQLTenant;
import sirius.biz.tenants.jdbc.SQLTenants;
import sirius.biz.tenants.jdbc.SQLUserAccount;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.SQLPageHelper;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.http.WebContext;

import java.util.Optional;

/**
 * Provides a GUI for managing tenants.
 */
@Register(classes = Controller.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLTenantController extends TenantController<Long, SQLTenant, SQLUserAccount> {

    @Override
    protected BasePageHelper<SQLTenant, ?, ?, ?> getTenantsAsPage() {
        return SQLPageHelper.withQuery(oma.select(SQLTenant.class).orderAsc(Tenant.TENANT_DATA.inner(TenantData.NAME)));
    }

    @Override
    protected Class<SQLTenant> getTenantClass() {
        return SQLTenant.class;
    }

    @Override
    protected BasePageHelper<SQLTenant, ?, ?, ?> getSelectableTenantsAsPage(WebContext ctx, SQLTenant currentTenant) {
        return SQLPageHelper.withQuery(queryPossibleTenants(currentTenant).orderAsc(Tenant.TENANT_DATA.inner(TenantData.NAME)));
    }

    @Override
    protected Optional<SQLTenant> tryToSelectTenant(String id, SQLTenant currentTenant) {
        return queryPossibleTenants(currentTenant).eq(SQLTenant.ID, id).first();
    }

    private SmartQuery<SQLTenant> queryPossibleTenants(SQLTenant currentTenant) {
        SmartQuery<SQLTenant> baseQuery = oma.select(SQLTenant.class);
        if (!hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
            if (currentTenant.getTenantData().isCanAccessParent()) {
                baseQuery.where(OMA.FILTERS.or(OMA.FILTERS.and(OMA.FILTERS.eq(Tenant.PARENT, currentTenant),
                                                               OMA.FILTERS.eq(Tenant.TENANT_DATA.inner(TenantData.PARENT_CAN_ACCESS),
                                                                              true)),
                                               OMA.FILTERS.eq(SQLTenant.ID, currentTenant.getParent().getId())));
            } else {
                baseQuery.where(OMA.FILTERS.and(OMA.FILTERS.eq(Tenant.PARENT, currentTenant),
                                                OMA.FILTERS.eq(Tenant.TENANT_DATA.inner(TenantData.PARENT_CAN_ACCESS),
                                                               true)));
            }
        }

        return baseQuery;
    }
}
