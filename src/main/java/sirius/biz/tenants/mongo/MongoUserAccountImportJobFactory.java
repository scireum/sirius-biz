/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.file.EntityImportJob;
import sirius.biz.jobs.batch.file.EntityImportJobFactory;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.UserAccountController;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Provides an import job for {@link MongoUserAccount user accounts} stored in MongoDB.
 */
@Register(classes = JobFactory.class, framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
@Permission(UserAccountController.PERMISSION_MANAGE_USER_ACCOUNTS)
public class MongoUserAccountImportJobFactory extends EntityImportJobFactory {

    @Override
    protected EntityImportJob<?> createJob(ProcessContext process) {
        return new EntityImportJob<MongoUserAccount>(fileParameter,
                                                     ignoreEmptyParameter,
                                                     importModeParameter,
                                                     MongoUserAccount.class,
                                                     getDictionary(),
                                                     process) {
        };
    }

    @Override
    protected Class<? extends BaseEntity<?>> getImportType() {
        return MongoUserAccount.class;
    }

    @Override
    protected boolean hasPresetFor(Object targetObject) {
        return targetObject == MongoUserAccount.class;
    }

    @Nonnull
    @Override
    public String getName() {
        return "import-mongo-user-accounts";
    }
}
