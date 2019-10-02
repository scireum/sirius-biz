/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler.mongo;

import sirius.biz.jobs.scheduler.SchedulerData;
import sirius.biz.jobs.scheduler.SchedulerEntryProvider;
import sirius.db.mongo.Finder;
import sirius.db.mongo.Mango;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Supplies the {@link MongoSchedulerEntry SQL scheduler entries} to the {@link sirius.biz.jobs.scheduler.JobSchedulerLoop}.
 */
@Register(framework = MongoSchedulerController.FRAMEWORK_SCHEDULER_MONGO)
public class MongoSchedulerEntryProvider implements SchedulerEntryProvider<MongoSchedulerEntry> {

    @Part
    private Mango mango;

    @Override
    public List<MongoSchedulerEntry> getActiveScheduledJobs() {
        return mango.select(MongoSchedulerEntry.class)
                    .fields(MongoSchedulerEntry.ID,
                            MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.ENABLED),
                            MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.YEAR),
                            MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.MONTH),
                            MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.DAY_OF_MONTH),
                            MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.DAY_OF_WEEK),
                            MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.HOUR_OF_DAY),
                            MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.MINUTE),
                            MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.LAST_EXECUTION),
                            MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.NUMBER_OF_EXECUTIONS),
                            MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.RUNS),
                            MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.LAST_EXECUTION))
                    .eq(MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.ENABLED), true)
                    .where(Finder.FILTERS.or(Finder.FILTERS.ne(MongoSchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.RUNS),
                                                               0),
                                             Finder.FILTERS.notFilled(MongoSchedulerEntry.SCHEDULER_DATA.inner(
                                                     SchedulerData.RUNS))))
                    .queryList();
    }

    @Override
    public MongoSchedulerEntry fetchFullInformation(MongoSchedulerEntry job) {
        return mango.tryRefresh(job);
    }

    @Override
    public void markExecuted(MongoSchedulerEntry job, LocalDateTime timestamp) {
        job.getSchedulerData().rememberExecution(timestamp);
        mango.update(job);
    }

    @Override
    public List<MongoSchedulerEntry> getFileTriggeredJobs() {
        return Collections.emptyList();
    }
}
