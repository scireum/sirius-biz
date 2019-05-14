/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.file.LineBasedImportJob;
import sirius.biz.jobs.batch.file.LineBasedImportJobFactory;
import sirius.biz.model.LoginData;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.UserAccountController;
import sirius.biz.tenants.UserAccountData;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Provides an import job for {@link SQLUserAccount user accounts} stored in a JDBC database.
 */
@Register(classes = JobFactory.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
@Permission(UserAccountController.PERMISSION_MANAGE_USER_ACCOUNTS)
public class SQLUserAccountImportJobFactory extends LineBasedImportJobFactory {

    @Part
    private SQLTenants tenants;

    @Override
    protected LineBasedImportJob<?> createJob(ProcessContext process) {
        SQLTenant currentTenant = tenants.getRequiredTenant();

        return new LineBasedImportJob<SQLUserAccount>(fileParameter, SQLUserAccount.class, process) {
            @Override
            protected SQLUserAccount fillAndVerify(SQLUserAccount entity) {
                setOrVerify(entity, entity.getTenant(), currentTenant);
                return super.fillAndVerify(entity);
            }

            @Override
            protected SQLUserAccount findAndLoad(Context ctx) {
                SQLUserAccount account = super.findAndLoad(ctx);

                Mapping passwordMapping = SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.GENERATED_PASSWORD);

                if (ctx.containsKey(passwordMapping.getName())) {
                    account.getUserAccountData().getLogin().setGeneratedPassword(ctx.getValue(passwordMapping.getName()).asString());
                }

                return account;
            }
        };
    }

    @Nonnull
    @Override
    public String getName() {
        return "import-sql-user-accounts";
    }
}
