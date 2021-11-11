/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.mongo;

import sirius.biz.codelists.CodeList;
import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.biz.tenants.mongo.DeleteMongoEntitiesTask;
import sirius.kernel.di.std.Register;

/**
 * Deletes all {@link CodeList code lists} of the given tenant.
 */
@Register(classes = DeleteTenantTask.class, framework = MongoCodeLists.FRAMEWORK_CODE_LISTS_MONGO)
public class DeleteMongoCodeListsTask extends DeleteMongoEntitiesTask<MongoCodeList> {

    @Override
    protected Class<MongoCodeList> getEntityClass() {
        return MongoCodeList.class;
    }

    @Override
    public int getPriority() {
        return 120;
    }
}
