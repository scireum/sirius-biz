/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantController;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.MongoPageHelper;
import sirius.db.mixing.query.QueryField;
import sirius.db.mongo.MongoQuery;
import sirius.db.mongo.QueryBuilder;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.http.WebContext;

import java.util.Optional;

/**
 * Provides a GUI for managing tenants.
 */
@Register(classes = {Controller.class, TenantController.class}, framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoTenantController extends TenantController<String, MongoTenant, MongoUserAccount> {

    @Override
    protected BasePageHelper<MongoTenant, ?, ?, ?> getTenantsAsPage() {
        MongoPageHelper<MongoTenant> pageHelper = MongoPageHelper.withQuery(mango.select(MongoTenant.class)
                                                                                 .orderAsc(Tenant.TENANT_DATA.inner(
                                                                                         TenantData.NAME)));
        pageHelper.withSearchFields(QueryField.startsWith(MongoTenant.SEARCH_PREFIXES));

        return pageHelper;
    }

    @Override
    protected BasePageHelper<MongoTenant, ?, ?, ?> getSelectableTenantsAsPage(WebContext ctx,
                                                                              MongoTenant currentTenant) {

        MongoPageHelper<MongoTenant> pageHelper =
                MongoPageHelper.withQuery(queryPossibleTenants(currentTenant).orderAsc(Tenant.TENANT_DATA.inner(
                        TenantData.NAME)));
        pageHelper.withSearchFields(QueryField.startsWith(MongoTenant.SEARCH_PREFIXES));

        return pageHelper;
    }

    @Override
    public Optional<MongoTenant> resolveAccessibleTenant(String id, Tenant<?> currentTenant) {
        return queryPossibleTenants((MongoTenant) currentTenant).eq(MongoTenant.ID, id).first();
    }

    private MongoQuery<MongoTenant> queryPossibleTenants(MongoTenant currentTenant) {
        MongoQuery<MongoTenant> baseQuery = mango.select(MongoTenant.class);
        if (!hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
            if (currentTenant.getTenantData().isCanAccessParent()) {
                baseQuery.where(QueryBuilder.FILTERS.or(QueryBuilder.FILTERS.and(QueryBuilder.FILTERS.eq(Tenant.PARENT,
                                                                                                         currentTenant),
                                                                                 QueryBuilder.FILTERS.eq(Tenant.TENANT_DATA
                                                                                                                 .inner(TenantData.PARENT_CAN_ACCESS),
                                                                                                         true)),
                                                        QueryBuilder.FILTERS.eq(MongoTenant.ID,
                                                                                currentTenant.getParent().getId())));
            } else {
                baseQuery.where(QueryBuilder.FILTERS.and(QueryBuilder.FILTERS.eq(Tenant.PARENT, currentTenant),
                                                         QueryBuilder.FILTERS.eq(Tenant.TENANT_DATA.inner(TenantData.PARENT_CAN_ACCESS),
                                                                                 true)));
            }
        }

        return baseQuery;
    }
}
