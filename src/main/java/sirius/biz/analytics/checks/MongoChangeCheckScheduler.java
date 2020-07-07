/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.checks;

import sirius.biz.analytics.scheduler.AnalyticsBatchExecutor;
import sirius.biz.analytics.scheduler.AnalyticsScheduler;
import sirius.biz.analytics.scheduler.AnalyticsSchedulerExecutor;
import sirius.biz.analytics.scheduler.MongoAnalyticalTaskScheduler;
import sirius.biz.analytics.scheduler.ScheduleInterval;
import sirius.biz.protocol.TraceData;
import sirius.biz.protocol.Traced;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.MongoQuery;
import sirius.db.mongo.QueryBuilder;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.time.LocalDateTime;

/**
 * Provides the scheduler which is in charge of creating and executing the batches of MongoDB entities for which
 * {@link ChangeCheck change checks} are present.
 */
@Register
public class MongoChangeCheckScheduler extends MongoAnalyticalTaskScheduler {

    @Override
    protected Class<?> getAnalyticalTaskType() {
        return ChangeCheck.class;
    }

    @Override
    public Class<? extends AnalyticsSchedulerExecutor> getExecutorForScheduling() {
        return CheckSchedulerExecutor.class;
    }

    @Override
    public Class<? extends AnalyticsBatchExecutor> getExecutorForTasks() {
        return CheckBatchExecutor.class;
    }

    @Override
    protected <E extends MongoEntity> void extendBatchQuery(MongoQuery<E> query) {
        if (!Traced.class.isAssignableFrom(query.getDescriptor().getType())) {
            throw new IllegalArgumentException("Entities for which 'ChangeChecks' are created must implement 'Traced'.");
        }

        query.where(QueryBuilder.FILTERS.gt(Traced.TRACE.inner(TraceData.CHANGED_AT),
                                            LocalDateTime.now().minusDays(1)));
    }

    @Override
    public boolean useBestEffortScheduling() {
        return true;
    }

    @Override
    public ScheduleInterval getInterval() {
        return ScheduleInterval.DAILY;
    }

    @Nonnull
    @Override
    public String getName() {
        return "mongo-change-checks";
    }
}
