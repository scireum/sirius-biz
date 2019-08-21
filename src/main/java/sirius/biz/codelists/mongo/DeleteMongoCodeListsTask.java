/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.mongo;

import sirius.biz.codelists.CodeList;
import sirius.biz.mongo.MongoBizEntity;
import sirius.biz.tenants.deletion.DeleteMongoEntitiesTask;
import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.kernel.di.std.Register;

/**
 * Deletes all {@link CodeList code lists} of the given tenant.
 */
@Register(classes = DeleteTenantTask.class, framework = MongoCodeLists.FRAMEWORK_CODE_LISTS_MONGO)
public class DeleteMongoCodeListsTask extends DeleteMongoEntitiesTask {

    @Override
    protected Class<? extends MongoBizEntity> getEntityClass() {
        return MongoCodeList.class;
    }

    @Override
    public int getPriority() {
        return 120;
    }
}
