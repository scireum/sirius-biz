/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.packages.PackageData;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.TenantController;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantExportJobFactory;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Provides an export for {@link SQLTenant tenants}.
 */
@Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
@Permission(TenantController.PERMISSION_MANAGE_TENANTS)
public class SQLTenantExportJobFactory extends TenantExportJobFactory<SQLTenant, SmartQuery<SQLTenant>> {

    @Nonnull
    @Override
    public String getName() {
        return "export-sql-tenants";
    }

    @Override
    protected Class<SQLTenant> getExportType() {
        return SQLTenant.class;
    }

    @Override
    protected void extendSelectQuery(SmartQuery<SQLTenant> query, ProcessContext processContext) {
        processContext.getParameter(packageParameter).ifPresent(packageName -> {
            query.eq(SQLTenant.TENANT_DATA.inner(TenantData.PACKAGE_DATA).inner(PackageData.PACKAGE_STRING),
                     packageName);
        });
    }
}
