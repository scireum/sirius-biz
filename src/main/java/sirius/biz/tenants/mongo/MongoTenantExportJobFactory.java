/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.packages.PackageData;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.TenantController;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantExportJobFactory;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Provides an export for {@link MongoTenant tenants}.
 */
@Register(framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
@Permission(TenantController.PERMISSION_MANAGE_TENANTS)
public class MongoTenantExportJobFactory extends TenantExportJobFactory<MongoTenant, MongoQuery<MongoTenant>> {

    @Nonnull
    @Override
    public String getName() {
        return "export-mongo-tenants";
    }

    @Override
    protected Class<MongoTenant> getExportType() {
        return MongoTenant.class;
    }

    @Override
    protected void extendSelectQuery(MongoQuery<MongoTenant> query, ProcessContext processContext) {
        processContext.getParameter(packageParameter).ifPresent(packageName -> {
            query.eq(MongoTenant.TENANT_DATA.inner(TenantData.PACKAGE_DATA).inner(PackageData.PACKAGE_STRING),
                     packageName);
        });

        processContext.getParameter(upgradesParameter).ifPresent(upgrades -> {
            query.where(query.filters()
                             .allInField(MongoTenant.TENANT_DATA.inner(TenantData.PACKAGE_DATA)
                                                                .inner(PackageData.UPGRADES),
                                         List.of(upgrades.split(","))));
        });
    }
}
