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
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Provides an import job for {@link MongoUserAccount user accounts} stored in MongoDB.
 */
@Register(classes = JobFactory.class, framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoUserAccountImportJobFactory extends LineBasedImportJobFactory {

    @Part
    private MongoTenants tenants;

    @Override
    protected LineBasedImportJob<?> createJob(ProcessContext process) {
        MongoTenant currentTenant = tenants.getRequiredTenant();

        return new LineBasedImportJob<MongoUserAccount>(fileParameter, MongoUserAccount.class, process) {
            @Override
            protected MongoUserAccount fillAndVerify(MongoUserAccount entity) {
                setOrVerify(entity, entity.getTenant(), currentTenant);
                return super.fillAndVerify(entity);
            }
        };
    }

    @Nonnull
    @Override
    public String getName() {
        return "import-mongo-user-accounts";
    }
}
