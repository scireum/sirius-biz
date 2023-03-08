/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.explorer;

import sirius.biz.analytics.metrics.Dataset;
import sirius.kernel.commons.Strings;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains the actual chart data to output into a {@link TimeSeriesChartFactory}.
 * <p>
 * Instances are created via {@link TimeSeries#createData(String)} or {@link TimeSeries#createDefaultData()} and then
 * filled with the proper data.
 * <p>
 * One of the main benefits of this class is, that data can be added in any order (as the date is always added as
 * timestamp). This also permits to collect monthly values and then interpolate them up to daily ones.
 */
public class TimeSeriesData {

    private final String label;
    private final Granularity granularity;
    private final boolean comparisonTimeSeries;
    private final Map<LocalDate, Double> values = new HashMap<>();

    protected TimeSeriesData(String label, Granularity granularity, boolean comparisonTimeSeries) {
        this.label = label;
        this.granularity = granularity;
        this.comparisonTimeSeries = comparisonTimeSeries;
    }

    /**
     * Adds a value for the given date.
     *
     * @param date  the date to add a value for
     * @param value the value to add
     * @return the time-series itself for fluent method calls
     */
    public TimeSeriesData addValue(LocalDate date, double value) {
        this.values.put(date, value);
        return this;
    }

    /**
     * Converts this data into a {@link Dataset} using the given {@link TimeSeries} to ensure a proper number of values
     * on the X axis.
     *
     * @param timeSeries the time-series used to specify the expected number of values on the X axis
     * @return the dataset representing the contained data
     */
    public Dataset toDataset(TimeSeries timeSeries) {
        Dataset dataset = new Dataset(label);
        if (comparisonTimeSeries) {
            dataset.markGray();
        }

        if (granularity == timeSeries.getGranularity()) {
            timeSeries.startDates().forEach(date -> dataset.addValue(values.getOrDefault(date, 0d)));
        } else if (granularity == Granularity.MONTH && timeSeries.getGranularity() == Granularity.DAY) {
            timeSeries.startDates().forEach(date -> dataset.addValue(values.getOrDefault(date.withDayOfMonth(1), 0d)));
        } else {
            throw new IllegalArgumentException(Strings.apply("Cannot convert data from granularity %s to %s",
                                                             granularity,
                                                             timeSeries.getGranularity()));
        }

        return dataset;
    }

    public Granularity getGranularity() {
        return granularity;
    }

    public boolean isComparisonTimeSeries() {
        return comparisonTimeSeries;
    }
}
