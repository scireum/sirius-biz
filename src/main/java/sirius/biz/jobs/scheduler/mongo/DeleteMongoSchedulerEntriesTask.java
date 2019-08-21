/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler.mongo;

import sirius.biz.jobs.scheduler.SchedulerEntry;
import sirius.biz.mongo.MongoBizEntity;
import sirius.biz.tenants.deletion.DeleteMongoEntitiesTask;
import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.kernel.di.std.Register;

/**
 * Deletes {@link SchedulerEntry schedulerEntries} of the given tenant.
 */
@Register(classes = DeleteTenantTask.class, framework = MongoSchedulerController.FRAMEWORK_SCHEDULER_MONGO)
public class DeleteMongoSchedulerEntriesTask extends DeleteMongoEntitiesTask {

    @Override
    protected Class<? extends MongoBizEntity> getEntityClass() {
        return MongoSchedulerEntry.class;
    }

    @Override
    public int getPriority() {
        return 110;
    }
}
