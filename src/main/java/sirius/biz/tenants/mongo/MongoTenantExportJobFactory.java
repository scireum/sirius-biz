/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.jobs.batch.file.EntityExportJobFactory;
import sirius.biz.tenants.TenantController;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.http.QueryString;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Provides an export for {@link MongoTenant tenants}.
 */
@Register(framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
@Permission(TenantController.PERMISSION_MANAGE_TENANTS)
public class MongoTenantExportJobFactory extends EntityExportJobFactory<MongoTenant, MongoQuery<MongoTenant>> {

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
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return queryString.path().startsWith("/tenant");
    }
}
