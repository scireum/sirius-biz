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
        return switch (this) {
            case ONE_DAY -> input.plusDays(1);
            case THREE_DAYS -> input.plusDays(3);
            case FOURTEEN_DAYS -> input.plusDays(14);
            case ONE_MONTH -> input.plusMonths(1);
            case THREE_MONTHS -> input.plusMonths(3);
            case ONE_YEAR -> input.plusYears(1);
            case THREE_YEARS -> input.plusYears(3);
            case SIX_YEARS -> input.plusYears(6);
            case TEN_YEARS -> input.plusYears(10);
        };
    }

    /**
     * Decrements the given date by the period represented by this value.
     *
     * @param input the date to move
     * @return the date decremented by this period
     */
    public LocalDate minus(LocalDate input) {
        return switch (this) {
            case ONE_DAY -> input.minusDays(1);
            case THREE_DAYS -> input.minusDays(3);
            case FOURTEEN_DAYS -> input.minusDays(14);
            case ONE_MONTH -> input.minusMonths(1);
            case THREE_MONTHS -> input.minusMonths(3);
            case ONE_YEAR -> input.minusYears(1);
            case THREE_YEARS -> input.minusYears(3);
            case SIX_YEARS -> input.minusYears(6);
            case TEN_YEARS -> input.minusYears(10);
        };
    }
}
