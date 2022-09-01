/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.time.LocalDate;

/**
 * Enumerates available comparison periods.
 */
public enum ComparisonPeriod {

    NONE, PREVIOUS_YEAR, PREVIOUS_MONTH, PREVIOUS_DAY;

    @Override
    public String toString() {
        return NLS.get(getClass().getSimpleName() + "." + name());
    }

    /**
     * Computes the proper comparison date for a given one.
     *
     * @param localDate the date to project
     * @return the proper comparison date for the given one
     */
    @Nullable
    public LocalDate computeDate(LocalDate localDate) {
        return switch (this) {
            case NONE -> null;
            case PREVIOUS_YEAR -> localDate.minusYears(1);
            case PREVIOUS_MONTH -> localDate.minusMonths(1);
            case PREVIOUS_DAY -> localDate.minusDays(1);
        };
    }
}
