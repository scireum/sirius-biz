/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.cluster.work.DistributedTasks;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.ValueHolder;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Provides a base implementation for executing an {@link AnalyticsScheduler}.
 * <p>
 * This reads the scheduler name and date from each given task description and instructs the appropriate scheduler via
 * {@link AnalyticsScheduler#scheduleBatches(Consumer)} to compute all batches to be executed. The emitted batches
 * are then submitted via {@link DistributedTasks} to be executed by the appropriate {@link AnalyticsBatchExecutor}.
 */
public abstract class AnalyticsSchedulerExecutor implements DistributedTaskExecutor {

    @Part
    protected GlobalContext globalContext;

    @Part
    protected DistributedTasks cluster;

    @Override
    public void executeWork(ObjectNode context) throws Exception {
        String schedulerName = context.path(AnalyticalEngine.CONTEXT_SCHEDULER_NAME).asText();
        LocalDate date =
                Value.of(NLS.parseMachineString(LocalDate.class, context.path(AnalyticalEngine.CONTEXT_DATE).asText()))
                     .asLocalDate(LocalDate.now());
        int level = context.path(AnalyticalEngine.CONTEXT_LEVEL).asInt();
        AnalyticsScheduler scheduler = globalContext.findPart(schedulerName, AnalyticsScheduler.class);

        // Note that this code might, at first sight, look overly complex. The batchBuffer is essentially used, to
        // identify the last batch generated by the scheduler which is then marked as "last", so that this will then
        // re-schedule all tasks for the next level (in AnalyticsBatchExecutor.java)...
        ValueHolder<ObjectNode> batchBuffer = new ValueHolder<>(null);
        scheduler.scheduleBatches(batch -> {
            if (batchBuffer.get() != null) {
                scheduleBatch(scheduler, date, level, false, batchBuffer.get());
            }

            batchBuffer.set(batch);
        });

        if (batchBuffer.get() != null) {
            scheduleBatch(scheduler, date, level, true, batchBuffer.get());
        } else {
            // We did not generate any batches. Therefore, we need to emit an empty placeholder batch so that
            // the AnalyticsBatchExecutor sees the "SCHEDULE_NEXT_LEVEL" flag. This completely empty batch will be
            // filtered out later...
            scheduleBatch(scheduler, date, level, true, Json.createObject());
        }
    }

    protected void scheduleBatch(AnalyticsScheduler scheduler,
                                 LocalDate date,
                                 int level,
                                 boolean last,
                                 ObjectNode batch) {
        batch.put(AnalyticalEngine.CONTEXT_SCHEDULER_NAME, scheduler.getName());
        batch.putPOJO(AnalyticalEngine.CONTEXT_DATE, date);
        batch.put(AnalyticalEngine.CONTEXT_LEVEL, level);
        batch.put(AnalyticalEngine.CONTEXT_LAST, last);
        cluster.submitFIFOTask(scheduler.getExecutorForTasks(), batch);
    }
}
