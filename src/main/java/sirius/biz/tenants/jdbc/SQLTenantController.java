/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.analytics.flags.jdbc.SQLPerformanceData;
import sirius.biz.model.AddressData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantController;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.SQLPageHelper;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.http.WebContext;

import java.util.Optional;

/**
 * Provides a GUI for managing tenants.
 */
@Register(classes = {Controller.class, TenantController.class}, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLTenantController extends TenantController<Long, SQLTenant, SQLUserAccount> {

    @Override
    protected BasePageHelper<SQLTenant, ?, ?, ?> getTenantsAsPage(WebContext ctx) {
        SmartQuery<SQLTenant> query = oma.select(SQLTenant.class).orderAsc(Tenant.TENANT_DATA.inner(TenantData.NAME));
        SQLPageHelper<SQLTenant> pageHelper = createTenantPageHelper(ctx, query);
        pageHelper.applyExtenders("/tenants");

        return pageHelper;
    }

    private SQLPageHelper<SQLTenant> createTenantPageHelper(WebContext ctx, SmartQuery<SQLTenant> query) {
        SQLPageHelper<SQLTenant> pageHelper = SQLPageHelper.withQuery(query).withContext(ctx);
        pageHelper.withSearchFields(QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.NAME)),
                                    QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER)),
                                    QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.ADDRESS)
                                                                          .inner(AddressData.STREET)),
                                    QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.ADDRESS)
                                                                          .inner(AddressData.CITY)));

        SQLPerformanceData.addFilterFacet(pageHelper);

        pageHelper.applyExtenders("/tenants/*");
        return pageHelper;
    }

    @Override
    protected BasePageHelper<SQLTenant, ?, ?, ?> getSelectableTenantsAsPage(WebContext ctx, SQLTenant currentTenant) {
        SmartQuery<SQLTenant> query =
                queryPossibleTenants(currentTenant).orderAsc(Tenant.TENANT_DATA.inner(TenantData.NAME));
        SQLPageHelper<SQLTenant> pageHelper = createTenantPageHelper(ctx, query);
        pageHelper.applyExtenders("/tenants/select");

        return pageHelper;
    }

    @Override
    public Optional<SQLTenant> resolveAccessibleTenant(String id, Tenant<?> currentTenant) {
        return queryPossibleTenants((SQLTenant) currentTenant).eq(SQLTenant.ID, id).first();
    }

    private SmartQuery<SQLTenant> queryPossibleTenants(SQLTenant currentTenant) {
        SmartQuery<SQLTenant> baseQuery = oma.select(SQLTenant.class);
        if (getUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT_AFFILIATE)) {
            return baseQuery;
        }
        if (currentTenant.getTenantData().isCanAccessParent()) {
            return baseQuery.where(OMA.FILTERS.or(OMA.FILTERS.and(OMA.FILTERS.eq(Tenant.PARENT, currentTenant),
                                                                  OMA.FILTERS.eq(Tenant.TENANT_DATA.inner(TenantData.PARENT_CAN_ACCESS),
                                                                                 true)),
                                                  OMA.FILTERS.eq(SQLTenant.ID, currentTenant.getParent().getId())));
        }
        return baseQuery.where(OMA.FILTERS.and(OMA.FILTERS.eq(Tenant.PARENT, currentTenant),
                                               OMA.FILTERS.eq(Tenant.TENANT_DATA.inner(TenantData.PARENT_CAN_ACCESS),
                                                              true)));
    }
}
