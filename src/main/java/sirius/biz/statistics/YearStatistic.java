/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.statistics;

import sirius.kernel.commons.Amount;

/**
 * Combines a set of informative values for a yearly value of a statistic.
 */
public class YearStatistic {

    private final long value;
    private final long valueLastYear;
    private final Amount increaseLastYearPercent;

    protected YearStatistic(long value, long valueLastYear) {
        this.value = value;
        this.valueLastYear = valueLastYear;
        this.increaseLastYearPercent = Amount.of(value).percentageDifferenceOf(Amount.of(valueLastYear));
    }

    /**
     * Returns the value of the statistic of the selected year.
     *
     * @return the value of the statistic
     */
    public long getValue() {
        return value;
    }

    /**
     * Returns the value of the statistic one year before ago.
     *
     * @return the value one year ago
     */
    public long getValueLastYear() {
        return valueLastYear;
    }

    /**
     * Returns the percentual increase of the value in the selected year compared to one year ago.
     *
     * @return the percentual increase compared to one year ago or an empty amount, if no value can be
     * computed
     */
    public Amount getIncreaseLastYearPercent() {
        return increaseLastYearPercent;
    }

    /**
     * Returns a string representation of the percentual increase compared to one year ago.
     *
     * @return a string representing the precentual increase compared to one year ago
     */
    public String getIncreaseLastYearPercentString() {
        return increaseLastYearPercent.toPercentString();
    }
}
