/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import java.time.LocalDate;

/**
 * Represents an analytical task which is executed for each of the entities and in the interval specified by the
 * referenced {@link AnalyticsScheduler scheduler}.
 *
 * @param <E> the type of entities processed by this task
 */
public interface AnalyticalTask<E> {

    /**
     * The scheduler which is used to schedules and executes this task for each of its entities.
     *
     * @return the scheduler which will invoke this task
     */
    Class<? extends AnalyticsScheduler<E>> getScheduler();

    /**
     * Performs the actual computation for the given entity and date.
     *
     * @param target the entity to execute the computation for
     * @param date   the date for which this execution was scheduled. Depending on the system load, this might not be
     *               the current date.
     */
    void compute(E target, LocalDate date);
}
