/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import sirius.db.mixing.BaseEntity;

import java.time.LocalDate;

/**
 * Provides an analytical tasks which is executed on the given entity.
 * <p>
 * Subclasses (interfaces) of this need an {@link MongoAnalyticalTaskScheduler} and/or
 * {@link SQLAnalyticalTaskScheduler} which schedules the execution of this task for all matching entities.
 *
 * @param <E> the entity type to be processed by this task
 */
public interface AnalyticalTask<E extends BaseEntity<?>> {

    /**
     * Contains the default value to use for {@link #getLevel()}.
     *
     * @see #getLevel()
     */
    int DEFAULT_LEVEL = 0;

    /**
     * Returns the class of entities to be processed by this task.
     *
     * @return the class of entities to be processed by this task
     */
    Class<E> getType();

    /**
     * Checks if this task is enabled at all.
     * <p>
     * Note that this is only checked once at framework startup. This can be used to disable some tasks which
     * are known to be unused. This will save the scheduler from creating lots of batches which are then
     * skipped anyway.
     *
     * @return <tt>true</tt> if the task is enabled, <tt>false</tt> if the task is statically disabled for the runtime
     * of the system
     */
    boolean isEnabled();

    /**
     * Returns the priority level of this task.
     * <p>
     * As sometimes e.g. metrics depend on each other, we need a method of ensuring that a metric computer is executed
     * before another. This is achieved by the level returned here. The schedulers will guarantee, that first,
     * all tasks of the level <b>0</b> will be executed. Then, once <b>all</b> tasks have been fully performed,
     * all tasks for the level <b>1</b> will be performed and so on.
     *
     * @return the priority level of this task. Use {@link #DEFAULT_LEVEL} is this task doesn't depend on any other task
     */
    int getLevel();

    /**
     * Executes the analytical task for the given entity and reference date.
     *
     * @param date   the data for which the task is to be executed
     * @param entity the entity for which this task is to be executed
     * @throws Exception in case of an error during the computation
     */
    void compute(LocalDate date, E entity) throws Exception;
}
