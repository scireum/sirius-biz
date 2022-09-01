/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.file.EntityExportJobFactory;
import sirius.biz.tenants.UserAccountController;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.http.QueryString;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Provides an export for {@link MongoUserAccount user accounts}.
 */
@Register(framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoUserAccountExportJobFactory
        extends EntityExportJobFactory<MongoUserAccount, MongoQuery<MongoUserAccount>> {

    @Part
    private MongoTenants tenants;

    @Nonnull
    @Override
    public String getName() {
        return "export-mongo-user-accounts";
    }

    @Override
    public String getCategory() {
        return StandardCategories.USERS_AND_TENANTS;
    }

    @Override
    public List<String> getRequiredPermissions() {
        List<String> requiredPermissions = super.getRequiredPermissions();
        requiredPermissions.add(UserAccountController.getUserManagementPermission());
        return requiredPermissions;
    }

    @Override
    protected Class<MongoUserAccount> getExportType() {
        return MongoUserAccount.class;
    }

    @Override
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return queryString.path().startsWith("/user-account");
    }
}
