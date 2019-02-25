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
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Provides an import job for {@link SQLUserAccount user accounts} stored in a JDBC database.
 */
@Register(classes = JobFactory.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLUserAccountImportJobFactory extends LineBasedImportJobFactory {

    @Override
    protected LineBasedImportJob createJob(ProcessContext process) {
        return new LineBasedImportJob(SQLUserAccount.class,
                                      this.createProcessTitle(process.getContext()),
                                      process,
                                      createProcessor(process));
    }

    @Nonnull
    @Override
    public String getName() {
        return "import-sql-user-accounts";
    }
}
