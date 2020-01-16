/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.db.mixing.DateRange;

import javax.annotation.Nullable;

/**
 * Defines an extension of {@link EnumParameter} for {@link DateRange}.
 */
public class DateRangeParameter extends EnumParameter<DateRangeParameter.DateRanges> {

    /**
     * Creates a new parameter with the given name, label and enum type.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter
     */
    public DateRangeParameter(String name, String label) {
        super(name, label, DateRanges.class);
    }

    /**
     * Defines a list of {@link DateRange date ranges} exposed by the {@link DateRangeParameter}
     */
    public enum DateRanges {
        TODAY(DateRange.today()), THIS_WEEK(DateRange.thisWeek()), LAST_WEEK(DateRange.lastWeek()),
        THIS_MONTH(DateRange.thisMonth()), LAST_MONTH(DateRange.lastMonth()), THIS_YEAR(DateRange.thisYear()),
        LAST_YEAR(DateRange.lastYear()), BEFORE_LAST_YEAR(DateRange.beforeLastYear());

        private final DateRange dateRange;

        DateRanges(DateRange range) {
            this.dateRange = range;
        }

        /**
         * Returns the {@link DateRange} set in this enum.
         *
         * @return the date range
         */
        @Nullable
        public DateRange toDateRange() {
            return dateRange;
        }

        @Override
        public String toString() {
            return dateRange.toString();
        }
    }
}
