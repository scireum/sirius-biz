/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.statistics;

import java.time.LocalDate;

/**
 * Defines aggregation levels supported for storing {@link StatisticalEvent}s.
 */
public enum AggregationLevel {
    /**
     * Stores one value per day
     */
    DAYS,

    /**
     * Stores one value per month
     */
    MONTHS,

    /**
     * Stores one value per year
     */
    YEARS,

    /**
     * Stores one value for the event and object
     */
    OVERALL;

    /**
     * Converts the given date into an appropriate "dummy" date for the given aggregation level.
     *
     * @param input the date to transform
     * @param lvl   the target aggregation level to transform to
     * @return the transformed date which will pick the 1st of the month or Jan 1st for years and Jan 1st 0001 for
     * overall values.
     */
    protected static LocalDate convertDate(LocalDate input, AggregationLevel lvl) {
        switch (lvl) {
            case DAYS:
                return input;
            case MONTHS:
                return input.withDayOfMonth(1);
            case YEARS:
                return input.withDayOfMonth(1).withMonth(1);
            case OVERALL:
                return input.withDayOfMonth(1).withMonth(1).withYear(1);
        }

        throw new IllegalArgumentException("Cannot convert " + input + " to " + lvl);
    }
}
