/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler.jdbc;

import sirius.biz.jobs.scheduler.SchedulerData;
import sirius.biz.jobs.scheduler.SchedulerEntryProvider;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Supplies the {@link SQLSchedulerEntry SQL scheduler entries} to the {@link sirius.biz.jobs.scheduler.JobSchedulerLoop}.
 */
@Register(framework = SQLSchedulerController.FRAMEWORK_SCHEDULER_JDBC)
public class SQLSchedulerEntryProvider implements SchedulerEntryProvider<SQLSchedulerEntry> {

    @Part
    private OMA oma;

    @Override
    public List<SQLSchedulerEntry> getActiveScheduledJobs() {
        return oma.select(SQLSchedulerEntry.class)
                  .fields(SQLSchedulerEntry.ID,
                          SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.ENABLED),
                          SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.YEAR),
                          SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.MONTH),
                          SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.DAY_OF_MONTH),
                          SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.DAY_OF_WEEK),
                          SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.HOUR_OF_DAY),
                          SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.MINUTE),
                          SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.LAST_EXECUTION),
                          SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.NUMBER_OF_EXECUTIONS),
                          SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.RUNS),
                          SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.LAST_EXECUTION))
                  .eq(SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.ENABLED), true)
                  .where(OMA.FILTERS.or(OMA.FILTERS.ne(SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.RUNS), 0),
                                        OMA.FILTERS.notFilled(SQLSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.RUNS))))
                  .queryList();
    }

    @Override
    public SQLSchedulerEntry fetchFullInformation(SQLSchedulerEntry job) {
        return oma.tryRefresh(job);
    }

    @Override
    public void markExecuted(SQLSchedulerEntry job, LocalDateTime timestamp) {
        job.getSchedulerData().rememberExecution(timestamp);
        oma.update(job);
    }
}
