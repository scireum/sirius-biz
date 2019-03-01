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
import sirius.biz.process.ProcessContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Provides an import job for {@link SQLUserAccount user accounts} stored in a JDBC database.
 */
@Register(classes = JobFactory.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
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
        };
    }

    @Nonnull
    @Override
    public String getName() {
        return "import-sql-user-accounts";
    }
}
