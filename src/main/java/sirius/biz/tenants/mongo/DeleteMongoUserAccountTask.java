/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.kernel.di.std.Register;

/**
 * Deletes all {@link UserAccount user accounts} of the given tenant.
 */
@Register(classes = DeleteTenantTask.class, framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class DeleteMongoUserAccountTask extends DeleteMongoEntitiesTask<MongoUserAccount> {

    @Override
    protected Class<MongoUserAccount> getEntityClass() {
        return MongoUserAccount.class;
    }

    @Override
    public int getPriority() {
        return 200;
    }
}
