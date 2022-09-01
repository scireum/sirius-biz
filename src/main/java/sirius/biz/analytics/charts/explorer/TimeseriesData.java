/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.analytics.charts.Dataset;
import sirius.kernel.commons.Strings;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains the actual chart data to output into a {@link TimeseriesChartFactory}.
 * <p>
 * Instances are created via {@link Timeseries#createData(String)} or {@link Timeseries#createDefaultData()} and then
 * filled with the proper data.
 * <p>
 * One of the main benefits of this class is, that data can be added in any order (as the date is always added as
 * timestamp). This also permits to collect monthly values and then interpolate them up to daily ones.
 */
public class TimeseriesData {

    private final String label;
    private final Granularity granularity;
    private final boolean comparisonTimeseries;
    private final Map<LocalDate, Double> values = new HashMap<>();

    protected TimeseriesData(String label, Granularity granularity, boolean comparisonTimeseries) {
        this.label = label;
        this.granularity = granularity;
        this.comparisonTimeseries = comparisonTimeseries;
    }

    /**
     * Adds a value for the given date.
     *
     * @param date  the date to add a value for
     * @param value the value to add
     * @return the time-series itself for fluent method calls
     */
    public TimeseriesData addValue(LocalDate date, double value) {
        this.values.put(date, value);
        return this;
    }

    /**
     * Converts this data into a {@link Dataset} using the given {@link Timeseries} to ensure a proper number of values
     * on the X axis.
     *
     * @param timeseries the time-series used to specify the expected number of values on the X axis
     * @return the dataset representing the contained data
     */
    public Dataset toDataset(Timeseries timeseries) {
        Dataset dataset = new Dataset(label);
        if (comparisonTimeseries) {
            dataset.markGray();
        }

        if (granularity == timeseries.getGranularity()) {
            timeseries.startDates().forEach(date -> dataset.addValue(values.getOrDefault(date, 0d)));
        } else if (granularity == Granularity.MONTH && timeseries.getGranularity() == Granularity.DAY) {
            timeseries.startDates().forEach(date -> dataset.addValue(values.getOrDefault(date.withDayOfMonth(1), 0d)));
        } else {
            throw new IllegalArgumentException(Strings.apply("Cannot convert data from granularity %s to %s",
                                                             granularity,
                                                             timeseries.getGranularity()));
        }

        return dataset;
    }

    public Granularity getGranularity() {
        return granularity;
    }

    public boolean isComparisonTimeseries() {
        return comparisonTimeseries;
    }
}
