/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.db.mixing.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Contains the parameters passed to {@link DailyMetricComputer#compute(ComputeParameters)},
 * {@link MonthlyMetricComputer#compute(ComputeParameters)} and
 * {@link MonthlyLargeMetricComputer#compute(ComputeParameters)}.
 *
 * @param date                           the date for which the computation should be performed
 * @param startOfPeriod                  the start of the period as {@link LocalDateTime}
 * @param endOfPeriod                    the end of the period as {@link LocalDateTime}
 * @param periodOutsideOfCurrentInterest <b>true</b> if the computation is performed for a past or future period
 *                                       (via the analytics command), or <b>false</b> if the computation is performed
 *                                       for the current period
 * @param bestEffortScheduled            <b>true</b> if the computation is performed on a best-effort basis, usually for
 *                                       a current, still incomplete period, or <b>false</b> otherwise
 * @param entity                         the entity to perform the computation for
 * @param <E>                            the type of entities being processed by the computer
 */
public record ComputeParameters<E extends BaseEntity<?>>(LocalDate date, LocalDateTime startOfPeriod,
                                                         LocalDateTime endOfPeriod,
                                                         boolean periodOutsideOfCurrentInterest,
                                                         boolean bestEffortScheduled, E entity) {

    /**
     * Provides the start of the period as {@link LocalDate}.
     *
     * @return the start of the period as {@link LocalDate}
     */
    public LocalDate startOfPeriodAsDate() {
        return startOfPeriod.toLocalDate();
    }

    /**
     * Provides the end of the period as {@link LocalDate}.
     *
     * @return the end of the period as {@link LocalDate}
     */
    public LocalDate endOfPeriodAsDate() {
        return endOfPeriod.toLocalDate();
    }
}
