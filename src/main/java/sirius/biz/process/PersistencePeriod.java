/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.kernel.nls.NLS;

import java.time.LocalDate;

/**
 * Represents a persistence period for which a {@link Process} is kept.
 */
public enum PersistencePeriod {

    ONE_DAY, THREE_DAYS, FOURTEEN_DAYS, ONE_MONTH, THREE_MONTHS, ONE_YEAR, THREE_YEARS, SIX_YEARS, TEN_YEARS;

    @Override
    public String toString() {
        return NLS.get(getClass().getSimpleName() + "." + name());
    }

    /**
     * Increments the given date by the period represented by this value.
     *
     * @param input the date to move
     * @return the date incremented by this period
     */
    public LocalDate plus(LocalDate input) {
        switch (this) {
            case ONE_DAY:
                return input.plusDays(1);
            case THREE_DAYS:
                return input.plusDays(3);
            case FOURTEEN_DAYS:
                return input.plusDays(14);
            case ONE_MONTH:
                return input.plusMonths(1);
            case THREE_MONTHS:
                return input.plusMonths(3);
            case ONE_YEAR:
                return input.plusYears(1);
            case THREE_YEARS:
                return input.plusYears(3);
            case SIX_YEARS:
                return input.plusYears(6);
            case TEN_YEARS:
                return input.plusYears(10);
        }

        throw new IllegalArgumentException(name());
    }

    /**
     * Decrements the given date by the period represented by this value.
     *
     * @param input the date to move
     * @return the date decremented by this period
     */
    public LocalDate minus(LocalDate input) {
        switch (this) {
            case ONE_DAY:
                return input.minusDays(1);
            case THREE_DAYS:
                return input.minusDays(3);
            case FOURTEEN_DAYS:
                return input.minusDays(14);
            case ONE_MONTH:
                return input.minusMonths(1);
            case THREE_MONTHS:
                return input.minusMonths(3);
            case ONE_YEAR:
                return input.minusYears(1);
            case THREE_YEARS:
                return input.minusYears(3);
            case SIX_YEARS:
                return input.minusYears(6);
            case TEN_YEARS:
                return input.minusYears(10);
        }

        throw new IllegalArgumentException(name());
    }
}
