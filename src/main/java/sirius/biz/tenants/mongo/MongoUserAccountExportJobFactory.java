/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.file.EntityExportJobFactory;
import sirius.biz.tenants.UserAccountController;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Provides an export for {@link MongoUserAccount user accounts}.
 */
@Register(classes = JobFactory.class, framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
@Permission(UserAccountController.PERMISSION_MANAGE_USER_ACCOUNTS)
public class MongoUserAccountExportJobFactory extends EntityExportJobFactory<MongoUserAccount> {

    @Override
    protected Class<MongoUserAccount> getExportType() {
        return MongoUserAccount.class;
    }

    @Nonnull
    @Override
    public String getName() {
        return "export-mongo-user-accounts";
    }
}
