/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.alibaba.fastjson.JSONObject;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.MultiMap;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Provides a base implementation which schedules all entities matching a given set of tasks (defined by a
 * subclass of {@link AnalyticalTask}) using a specified batch size.
 */
abstract class BaseAnalyticalTaskScheduler<B extends BaseEntity<?>> implements AnalyticsScheduler {

    @Part
    protected GlobalContext context;

    protected MultiMap<Class<?>, AnalyticalTask<?>> tasks;

    /**
     * Specifies the type of analytical tasks processed by this schedulder.
     *
     * @return the class of analytical tasks handled by this schedulder
     */
    protected abstract Class<?> getAnalyticalTaskType();

    @SuppressWarnings("unchecked")
    @Override
    public void scheduleBatches(Consumer<JSONObject> batchConsumer) {
        getTasks().keySet().forEach(type -> scheduleBatches((Class<? extends B>) type, batchConsumer));
    }

    /**
     * Schedules the batches for the given type of entities.
     *
     * @param type          the type of entities to schedule
     * @param batchConsumer the consumer which will distribute the batches accross the cluster
     */
    protected abstract void scheduleBatches(Class<? extends B> type, Consumer<JSONObject> batchConsumer);

    private MultiMap<Class<?>, AnalyticalTask<?>> getTasks() {
        if (tasks == null) {
            tasks = determineTasks();
        }
        return tasks;
    }

    @SuppressWarnings("unchecked")
    private MultiMap<Class<?>, AnalyticalTask<?>> determineTasks() {
        MultiMap<Class<?>, AnalyticalTask<?>> result = MultiMap.create();
        context.getParts((Class<AnalyticalTask<?>>) getAnalyticalTaskType())
               .stream()
               .filter(this::isMatchingEntityType)
               .forEach(task -> result.put(task.getType(), task));

        return result;
    }

    /**
     * Determines if the given task (and its associated entity type) is accepted by this scheduler.
     *
     * @param task the task to check
     * @return <tt>true</tt> if the task is processed by this schedulder, <tt>false</tt> otherwise
     */
    protected abstract boolean isMatchingEntityType(AnalyticalTask<?> task);

    @SuppressWarnings("unchecked")
    protected void executeEntity(B entity, LocalDate date) {
        for (AnalyticalTask<?> task : getTasks().get(entity.getClass())) {
            try {
                ((AnalyticalTask<B>) task).compute(date, entity);
            } catch (Exception ex) {
                Exceptions.handle()
                          .to(Log.BACKGROUND)
                          .error(ex)
                          .withSystemErrorMessage("The analytical task %s for entity %s (%s) failed: %s (%s)",
                                                  task.getClass().getName(),
                                                  entity.toString(),
                                                  entity.getClass().getName())
                          .handle();
            }
        }
    }
}
