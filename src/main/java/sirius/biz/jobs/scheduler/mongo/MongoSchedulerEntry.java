/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler.mongo;

import sirius.biz.jobs.JobConfigData;
import sirius.biz.jobs.scheduler.SchedulerData;
import sirius.biz.jobs.scheduler.SchedulerEntry;
import sirius.biz.tenants.mongo.MongoTenantAware;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.kernel.di.std.Framework;

/**
 * Provides the MongoDB implementation for representing {@link SchedulerEntry scheduler entries}.
 */
@Framework(MongoSchedulerController.FRAMEWORK_SCHEDULER_MONGO)
@TranslationSource(SchedulerEntry.class)
public class MongoSchedulerEntry extends MongoTenantAware implements SchedulerEntry {

    private final SchedulerData schedulerData = new SchedulerData();
    private final JobConfigData jobConfigData = new JobConfigData();

    @Override
    public SchedulerData getSchedulerData() {
        return schedulerData;
    }

    @Override
    public JobConfigData getJobConfigData() {
        return jobConfigData;
    }

    @Override
    public String toString() {
        return jobConfigData.toString();
    }
}
