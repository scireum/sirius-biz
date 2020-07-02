/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.analytics.flags.jdbc.SQLPerformanceData;
import sirius.biz.analytics.flags.mongo.MongoPerformanceData;
import sirius.biz.packages.PackageData;
import sirius.biz.packages.Packages;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantController;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.MongoPageHelper;
import sirius.db.mixing.query.QueryField;
import sirius.db.mongo.MongoQuery;
import sirius.db.mongo.QueryBuilder;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.http.WebContext;

import java.util.Optional;

/**
 * Provides a GUI for managing tenants.
 */
@Register(classes = {Controller.class, TenantController.class}, framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoTenantController extends TenantController<String, MongoTenant, MongoUserAccount> {

    @Part
    private Packages packages;

    @Override
    protected BasePageHelper<MongoTenant, ?, ?, ?> getTenantsAsPage(WebContext ctx) {
        MongoPageHelper<MongoTenant> pageHelper = createTenantPageHelper(ctx,
                                                                         mango.select(MongoTenant.class)
                                                                              .orderAsc(Tenant.TENANT_DATA.inner(
                                                                                      TenantData.NAME)));
        pageHelper.applyExtenders("/tenants");
        return pageHelper;
    }

    @Override
    protected BasePageHelper<MongoTenant, ?, ?, ?> getSelectableTenantsAsPage(WebContext ctx,
                                                                              MongoTenant currentTenant) {
        MongoPageHelper<MongoTenant> pageHelper = createTenantPageHelper(ctx,
                                                                         queryPossibleTenants(currentTenant).orderAsc(
                                                                                 Tenant.TENANT_DATA.inner(TenantData.NAME)));
        pageHelper.applyExtenders("/tenants/select");
        return pageHelper;
    }

    private MongoPageHelper<MongoTenant> createTenantPageHelper(WebContext ctx, MongoQuery<MongoTenant> query) {
        MongoPageHelper<MongoTenant> pageHelper = MongoPageHelper.withQuery(query).withContext(ctx);

        pageHelper.withSearchFields(QueryField.startsWith(MongoTenant.SEARCH_PREFIXES));

        MongoPerformanceData.addFilterFacet(pageHelper);

        pageHelper.addTermAggregation(MongoTenant.TENANT_DATA.inner(TenantData.PACKAGE_DATA.inner(PackageData.PACKAGE_STRING)),
                                      name -> packages.getPackageName(TenantController.PACKAGE_SCOPE_TENANT, name));
        pageHelper.addTermAggregation(MongoTenant.TENANT_DATA.inner(TenantData.PACKAGE_DATA.inner(PackageData.UPGRADES)),
                                      name -> packages.getUpgradeName(TenantController.PACKAGE_SCOPE_TENANT, name));

        pageHelper.applyExtenders("/tenants/*");

        return pageHelper;
    }

    @Override
    public Optional<MongoTenant> resolveAccessibleTenant(String id, Tenant<?> currentTenant) {
        return queryPossibleTenants((MongoTenant) currentTenant).eq(MongoTenant.ID, id).first();
    }

    private MongoQuery<MongoTenant> queryPossibleTenants(MongoTenant currentTenant) {
        MongoQuery<MongoTenant> baseQuery = mango.select(MongoTenant.class);
        if (getUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT_AFFILIATE)) {
            return baseQuery;
        }
        if (currentTenant.getTenantData().isCanAccessParent()) {
            return baseQuery.where(QueryBuilder.FILTERS.or(QueryBuilder.FILTERS.and(QueryBuilder.FILTERS.eq(Tenant.PARENT,
                                                                                                            currentTenant),
                                                                                    QueryBuilder.FILTERS.eq(Tenant.TENANT_DATA
                                                                                                                    .inner(TenantData.PARENT_CAN_ACCESS),
                                                                                                            true)),
                                                           QueryBuilder.FILTERS.eq(MongoTenant.ID,
                                                                                   currentTenant.getParent().getId())));
        }
        return baseQuery.where(QueryBuilder.FILTERS.and(QueryBuilder.FILTERS.eq(Tenant.PARENT, currentTenant),
                                                        QueryBuilder.FILTERS.eq(Tenant.TENANT_DATA.inner(TenantData.PARENT_CAN_ACCESS),
                                                                                true)));
    }
}
