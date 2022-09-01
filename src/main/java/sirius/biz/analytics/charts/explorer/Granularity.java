/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.kernel.nls.NLS;

import java.time.LocalDate;

/**
 * Enumerates available granularities, which are actually the resolution of a requested period.
 */
public enum Granularity {
    DAY, MONTH;

    /**
     * Moves to the next date for a given one.
     *
     * @param date the data to increment
     * @return the "next" date for the given one
     */
    public LocalDate increment(LocalDate date) {
        return switch (this) {
            case DAY -> date.plusDays(1);
            case MONTH -> date.plusMonths(1);
        };
    }

    /**
     * Computes the date which matches the end of the range represented by this granularity.
     *
     * @param date the date to compute the range end for
     * @return the end of the range in which the date is
     */
    public LocalDate computeEndOfRange(LocalDate date) {
        return switch (this) {
            case DAY -> date;
            case MONTH -> date.plusMonths(1).minusDays(1);
        };
    }

    /**
     * Formats the given date according to this granularity.
     *
     * @param date the date for format
     * @return a properly formatted string for this granularity
     */
    public String format(LocalDate date) {
        return switch (this) {
            case DAY -> date.getDayOfMonth() + ". " + NLS.getMonthName(date.getMonthValue());
            case MONTH -> NLS.getMonthName(date.getMonthValue()) + " " + date.getYear();
        };
    }
}
