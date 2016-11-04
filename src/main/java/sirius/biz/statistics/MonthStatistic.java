/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.statistics;

import sirius.kernel.commons.Amount;

import java.time.LocalDate;

/**
 * Combines a set of informative values for a monthly value of a statistic.
 */
public class MonthStatistic {

    private final long value;
    private final long valueLastMonth;
    private final long valueLastYear;
    private final Amount increaseLastMonthPercent;
    private final Amount increaseLastYearPercent;
    private final LocalDate monthAsDate;

    protected MonthStatistic(long value, long valueLastMonth, long valueLastYear, LocalDate monthAsDate) {
        super();
        this.value = value;
        this.valueLastMonth = valueLastMonth;
        this.valueLastYear = valueLastYear;
        this.monthAsDate = monthAsDate;
        this.increaseLastMonthPercent = Amount.of(value).percentageDifferenceOf(Amount.of(valueLastMonth));
        this.increaseLastYearPercent = Amount.of(value).percentageDifferenceOf(Amount.of(valueLastYear));
    }

    /**
     * Returns the value of the statistic of the selected month.
     *
     * @return the value of the statistic
     */
    public long getValue() {
        return value;
    }

    /**
     * Returns the value of the statistic in the month before the selected month.
     *
     * @return the value of the previous month
     */
    public long getValueLastMonth() {
        return valueLastMonth;
    }

    /**
     * Returns the value of the statistic in the month one year before the selected month.
     *
     * @return the value of the same month one year ago
     */
    public long getValueLastYear() {
        return valueLastYear;
    }

    /**
     * Returns the percentual increase of the value in the selected month compared to the previous month.
     *
     * @return the percentual increase compared to the previous month or an empty amount, if no value can be computed
     */
    public Amount getIncreaseLastMonthPercent() {
        return increaseLastMonthPercent;
    }

    /**
     * Returns the percentual increase of the value in the selected month compared to the month one year ago.
     *
     * @return the percentual increase compared to the month one year ago or an empty amount, if no value can be
     * computed
     */
    public Amount getIncreaseLastYearPercent() {
        return increaseLastYearPercent;
    }

    /**
     * Returns a string representation of the percentual increase compared to the previous month.
     *
     * @return a string representing the precentual increase compared to the previous month
     */
    public String getIncreaseLastMonthPercentString() {
        return increaseLastMonthPercent.toPercentString();
    }

    /**
     * Returns a string representation of the percentual increase compared to the month one year ago.
     *
     * @return a string representing the precentual increase compared to the month one year ago
     */
    public String getIncreaseLastYearPercentString() {
        return increaseLastYearPercent.toPercentString();
    }

    /**
     * Returns the selected month as date.
     *
     * @return the 1st of the selected month as <tt>LocalDate</tt>
     */
    public LocalDate getMonthAsDate() {
        return monthAsDate;
    }
}
