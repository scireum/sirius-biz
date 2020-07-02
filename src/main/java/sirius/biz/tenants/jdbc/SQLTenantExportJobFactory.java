/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.jobs.batch.file.EntityExportJobFactory;
import sirius.biz.tenants.TenantController;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.di.std.Register;
import sirius.web.http.QueryString;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Provides an export for {@link SQLTenant tenants}.
 */
@Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
@Permission(TenantController.PERMISSION_MANAGE_TENANTS)
public class SQLTenantExportJobFactory extends EntityExportJobFactory<SQLTenant, SmartQuery<SQLTenant>> {

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
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return queryString.path().startsWith("/tenant");
    }
}
