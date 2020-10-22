/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler.mongo;

import sirius.biz.jobs.JobConfigData;
import sirius.biz.jobs.scheduler.SchedulerController;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.MongoPageHelper;
import sirius.kernel.di.std.Register;

/**
 * Provides the controller for managing the MongoDB based scheduler.
 */
@Register(framework = MongoSchedulerController.FRAMEWORK_SCHEDULER_MONGO)
public class MongoSchedulerController extends SchedulerController<MongoSchedulerEntry> {

    /**
     * Names the framework which must be enabled to activate the scheduling for MongoDB based entries.
     */
    public static final String FRAMEWORK_SCHEDULER_MONGO = "biz.scheduler-mongo";

    @Override
    protected Class<MongoSchedulerEntry> getEntryType() {
        return MongoSchedulerEntry.class;
    }

    @Override
    protected BasePageHelper<MongoSchedulerEntry, ?, ?, ?> getEntriesAsPage() {
        return MongoPageHelper.withQuery(tenants.forCurrentTenant(mango.select(MongoSchedulerEntry.class)
                                                                       .orderAsc(MongoSchedulerEntry.JOB_CONFIG_DATA.inner(
                                                                               JobConfigData.JOB))));
    }
}
