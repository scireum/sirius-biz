/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.jobs.StandardJobCategories;
import sirius.biz.jobs.batch.file.EntityImportJobFactory;
import sirius.biz.tenants.UserAccountController;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.http.QueryString;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Provides an import job for {@link SQLUserAccount user accounts} stored in a JDBC database.
 */
@Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLUserAccountImportJobFactory extends EntityImportJobFactory {

    @Part
    private SQLTenants tenants;

    @Nonnull
    @Override
    public String getName() {
        return "import-sql-user-accounts";
    }

    @Override
    public String getCategory() {
        return StandardJobCategories.USERS_AND_TENANTS;
    }

    @Override
    public List<String> getRequiredPermissions() {
        List<String> requiredPermissions = super.getRequiredPermissions();
        requiredPermissions.add(UserAccountController.getUserManagementPermission());
        return requiredPermissions;
    }

    @Override
    protected Class<? extends BaseEntity<?>> getImportType() {
        return SQLUserAccount.class;
    }

    @Override
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return queryString.path().startsWith("/user-account");
    }
}
