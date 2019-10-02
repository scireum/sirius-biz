/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler.jdbc;

import sirius.biz.jobs.scheduler.JobConfigData;
import sirius.biz.jobs.scheduler.SchedulerData;
import sirius.biz.jobs.scheduler.SchedulerEntry;
import sirius.biz.jobs.scheduler.UploadTriggerData;
import sirius.biz.tenants.jdbc.SQLTenantAware;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;

/**
 * Provides the SQL implementation for representing {@link SchedulerEntry scheduler entries}.
 */
@Framework(SQLSchedulerController.FRAMEWORK_SCHEDULER_JDBC)
@TranslationSource(SchedulerEntry.class)
public class SQLSchedulerEntry extends SQLTenantAware implements SchedulerEntry {

    private final SchedulerData schedulerData = new SchedulerData();
    private final JobConfigData jobConfigData = new JobConfigData();
    private final UploadTriggerData uploadTriggerData = new UploadTriggerData(this);

    @Override
    public SchedulerData getSchedulerData() {
        return schedulerData;
    }

    @Override
    public JobConfigData getJobConfigData() {
        return jobConfigData;
    }

    @Override
    public UploadTriggerData getUploadTriggerData() {
        return uploadTriggerData;
    }

    @Override
    public String toString() {
        return Strings.isFilled(jobConfigData.getLabel()) ? jobConfigData.getLabel() : jobConfigData.getJobName();
    }
}
