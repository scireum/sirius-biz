/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler.mongo;

import sirius.biz.jobs.scheduler.SchedulerEntry;
import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.biz.tenants.mongo.DeleteMongoEntitiesTask;
import sirius.kernel.di.std.Register;

/**
 * Deletes {@link SchedulerEntry schedulerEntries} of the given tenant.
 */
@Register(classes = DeleteTenantTask.class, framework = MongoSchedulerController.FRAMEWORK_SCHEDULER_MONGO)
public class DeleteMongoSchedulerEntriesTask extends DeleteMongoEntitiesTask<MongoSchedulerEntry> {

    @Override
    protected Class<MongoSchedulerEntry> getEntityClass() {
        return MongoSchedulerEntry.class;
    }

    @Override
    public int getPriority() {
        return 110;
    }
}
