/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.file.LineBasedImportJob;
import sirius.biz.jobs.batch.file.LineBasedImportJobFactory;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.mongo.MongoTenants;
import sirius.biz.tenants.mongo.MongoUserAccount;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Provides an import job for {@link MongoUserAccount user accounts} stored in MongoDB.
 */
@Register(classes = JobFactory.class, framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoUserAccountImportJobFactory extends LineBasedImportJobFactory {

    @Override
    protected LineBasedImportJob createJob(ProcessContext process) {
        return new LineBasedImportJob(fileParameter, MongoUserAccount.class, process);
    }

    @Nonnull
    @Override
    public String getName() {
        return "import-mongo-user-accounts";
    }
}
