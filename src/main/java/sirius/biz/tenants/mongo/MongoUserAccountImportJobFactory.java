/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.file.EntityImportJobFactory;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.UserAccountController;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.http.QueryString;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;

/**
 * Provides an import job for {@link MongoUserAccount user accounts} stored in MongoDB.
 */
@Register(classes = JobFactory.class, framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
@Permission(UserAccountController.PERMISSION_MANAGE_USER_ACCOUNTS)
public class MongoUserAccountImportJobFactory extends EntityImportJobFactory {

    @Part
    private MongoTenants tenants;

    @Nonnull
    @Override
    public String getName() {
        return "import-mongo-user-accounts";
    }

    @Override
    protected void checkPermissions() {
        UserInfo currentUser = UserContext.getCurrentUser();
        if (tenants.getRequiredTenant().hasPermission(Tenant.PERMISSION_SYSTEM_TENANT)) {
            currentUser.assertPermission(UserAccountController.PERMISSION_MANAGE_SYSTEM_USERS);
        } else {
            currentUser.assertPermission(UserAccountController.PERMISSION_MANAGE_USER_ACCOUNTS);
        }
    }

    @Override
    protected Class<? extends BaseEntity<?>> getImportType() {
        return MongoUserAccount.class;
    }

    @Override
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return queryString.path().startsWith("/user-account");
    }
}
