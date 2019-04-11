/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.kernel.commons.Value;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;

import java.time.LocalDate;

/**
 * Provides a base implementation for executing batches of tasks generated by an {@link AnalyticsScheduler}.
 * <p>
 * This reads the scheduler name and date from each given task description and instructs the appropriate scheduler via
 * {@link AnalyticsScheduler#executeBatch(JSONObject, LocalDate)} to execute the specified batch.
 */
public abstract class AnalyticsBatchExecutor extends DistributedTaskExecutor {

    @Part
    protected GlobalContext globalContext;

    @Override
    public void executeWork(JSONObject context) throws Exception {
        String schedulerName = context.getString(AnalyticalEngine.CONTEXT_SCHEDULER_NAME);
        LocalDate date = Value.of(context.get(AnalyticalEngine.CONTEXT_DATE)).asLocalDate(LocalDate.now());
        AnalyticsScheduler scheduler = globalContext.findPart(schedulerName, AnalyticsScheduler.class);
        scheduler.executeBatch(context, date);
    }
}
