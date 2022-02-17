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
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.MultiMap;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Microtiming;

import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Provides a base implementation which schedules all entities matching a given set of tasks (defined by a
 * subclass of {@link AnalyticalTask}) using a specified batch size.
 */
abstract class BaseAnalyticalTaskScheduler<B extends BaseEntity<?>> implements AnalyticsScheduler {

    @Part
    protected GlobalContext context;

    @Part
    protected Mixing mixing;

    @Part
    protected AnalyticalEngine analyticalEngine;

    protected MultiMap<Class<?>, AnalyticalTask<?>> tasks;

    private Boolean active;

    private Integer maxLevel;

    /**
     * Specifies the type of analytical tasks processed by this scheduler.
     *
     * @return the class of analytical tasks handled by this scheduler
     */
    protected abstract Class<?> getAnalyticalTaskType();

    protected abstract Class<?> getMinimalTargetType();

    @Override
    public boolean isActive() {
        if (active == null) {
            active = getTasks().values()
                               .stream()
                               .map(AnalyticalTask::getType)
                               .anyMatch(type -> getMinimalTargetType().isAssignableFrom(type));
        }

        return active.booleanValue();
    }

    @Override
    public int getMaxLevel() {
        if (maxLevel == null) {
            maxLevel = getTasks().values().stream().mapToInt(AnalyticalTask::getLevel).max().orElse(0);
        }

        return maxLevel;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void scheduleBatches(Consumer<JSONObject> batchConsumer) {
        getTasks().keySet()
                  .stream()
                  .filter(type -> getMinimalTargetType().isAssignableFrom(type))
                  .forEach(type -> scheduleBatchesForType(batchConsumer, (Class<? extends B>) type));
    }

    private void scheduleBatchesForType(Consumer<JSONObject> batchConsumer, Class<? extends B> type) {
        Watch watch = Watch.start();
        if (AnalyticalEngine.LOG.isFINE()) {
            AnalyticalEngine.LOG.FINE("Scheduling batches for type '%s' in '%s'...", type.getSimpleName(), getName());
        }
        scheduleBatches(type, batchConsumer);
        if (AnalyticalEngine.LOG.isFINE()) {
            AnalyticalEngine.LOG.FINE("Scheduling batches for type '%s' in '%s' took: %s",
                                      type.getSimpleName(),
                                      getName(),
                                      watch.duration());
        }
        if (Microtiming.isEnabled()) {
            watch.submitMicroTiming(AnalyticalEngine.MICROTIMING_KEY_ANALYTICS,
                                    Strings.apply("Scheduled batches for type '%s' in '%s'",
                                                  type.getSimpleName(),
                                                  getName()));
        }
    }

    /**
     * Schedules the batches for the given type of entities.
     *
     * @param type          the type of entities to schedule
     * @param batchConsumer the consumer which will distribute the batches across the cluster
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
               .filter(AnalyticalTask::isEnabled)
               .filter(this::isMatchingEntityType)
               .forEach(task -> result.put(task.getType(), task));

        return result;
    }

    /**
     * Determines if the given task (and its associated entity type) is accepted by this scheduler.
     *
     * @param task the task to check
     * @return <tt>true</tt> if the task is processed by this scheduler, <tt>false</tt> otherwise
     */
    protected abstract boolean isMatchingEntityType(AnalyticalTask<?> task);

    protected void executeEntity(B entity, Class<?> type, LocalDate date, int level) {
        Watch watch = Watch.start();
        for (AnalyticalTask<?> task : getTasks().get(type)) {
            if (task.getLevel() == level) {
                executeTaskForEntity(entity, type, date, task);
            }
        }
        if (AnalyticalEngine.LOG.isFINE()) {
            AnalyticalEngine.LOG.FINE("Executing tasks for '%s' ('%s') in '%s' took: %s",
                                      entity == null ? "-" : entity.getIdAsString(),
                                      type.getSimpleName(),
                                      getName(),
                                      watch.duration());
        }
        if (Microtiming.isEnabled()) {
            watch.submitMicroTiming(AnalyticalEngine.MICROTIMING_KEY_ANALYTICS,
                                    Strings.apply("Executed tasks for '%s'", type.getSimpleName()));
        }
    }

    @SuppressWarnings("unchecked")
    private void executeTaskForEntity(B entity, Class<?> type, LocalDate date, AnalyticalTask<?> task) {
        Watch watch = Watch.start();
        try {
            ((AnalyticalTask<B>) task).compute(date, entity);
        } catch (Exception ex) {
            Exceptions.handle()
                      .to(AnalyticalEngine.LOG)
                      .error(ex)
                      .withSystemErrorMessage("The analytical task %s for entity %s (%s) failed: %s (%s)",
                                              task.getClass().getName(),
                                              entity == null ? "-" : entity.getIdAsString(),
                                              type.getName())
                      .handle();
        }
        if (AnalyticalEngine.LOG.isFINE()) {
            AnalyticalEngine.LOG.FINE("Executing task '%s' for '%s' ('%s') in '%s' took: %s",
                                      task.getClass().getSimpleName(),
                                      entity == null ? "-" : entity.getIdAsString(),
                                      type.getSimpleName(),
                                      getName(),
                                      watch.duration());
        }
        if (Microtiming.isEnabled()) {
            watch.submitMicroTiming(AnalyticalEngine.MICROTIMING_KEY_ANALYTICS,
                                    Strings.apply("Executed task '%s' for '%s'",
                                                  task.getClass().getName(),
                                                  type.getSimpleName()));
        }
    }
}
