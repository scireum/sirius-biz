/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.kernel.commons.Tuple;
import sirius.kernel.nls.NLS;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Represents a timeseries used by the {@link TimeSeriesChartFactory}.
 * <p>
 * As the {@link TimeSeriesChartFactory} itself and also its subclasses operate heavily on the time-ranges to query,
 * this helper class is used to represent which time slots to query. It also drags along a consumer to collect all
 * generated output data ({@link TimeSeriesData}) as well as a flag if this is a normal time-series or one computed
 * for the {@link ComparisonPeriod}.
 * <p>
 * Next to actually deriving the comparison period and being used as a value object, this also provides a very helpful
 * method {@link #toMonthlySeries()} which can be used for data-sources which only can provide values on a "per month"
 * level, even if days are requested. This class, along with {@link TimeSeriesData} and the {@link TimeSeriesChartFactory}
 * will then work together, to interpolate the data, so that the output matches the other charts, while also notifying
 * the user, that an interpolation step happened.
 * <p>
 * Being a helper-class, instances are only created by the {@link TimeSeriesChartFactory}, but as noted above,
 * subclasses of it might use the getters and {@link #toMonthlySeries()} in order to generate simple and effective
 * queries.
 */
public class TimeSeries {

    private Consumer<TimeSeriesData> dataConsumer;

    private final LocalDate requestedStart;
    private final LocalDate requestedEnd;
    private final Granularity granularity;
    private final List<Tuple<LocalDate, LocalDate>> ranges;

    private boolean comparisonTimeseries = false;

    protected TimeSeries(LocalDate requestedStart, LocalDate requestedEnd, Granularity granularity) {
        this(requestedStart, requestedEnd, granularity, computeRanges(requestedStart, requestedEnd, granularity));
    }

    protected TimeSeries(LocalDate requestedStart,
                         LocalDate requestedEnd,
                         Granularity granularity,
                         List<Tuple<LocalDate, LocalDate>> ranges) {
        this.requestedStart = requestedStart;
        this.requestedEnd = requestedEnd;
        this.granularity = granularity;
        this.ranges = ranges;
    }

    protected TimeSeries withDataConsumer(Consumer<TimeSeriesData> dataConsumer) {
        this.dataConsumer = dataConsumer;
        return this;
    }

    /**
     * Creates the default {@link TimeSeriesData} to output into a chart.
     *
     * @return creates a timeseries data with the default label to use if only one chart line is present
     */
    public TimeSeriesData createDefaultData() {
        return createData(comparisonTimeseries ? "$TimeSeries.comparisonDataset" : "$TimeSeries.currentDataset");
    }

    /**
     * Creates a {@link TimeSeriesData} for this timeseries with the given name.
     *
     * @param label the label to show for the generated data.
     * @return the data object to be supplied with values
     */
    public TimeSeriesData createData(String label) {
        TimeSeriesData data = new TimeSeriesData(NLS.smartGet(label), granularity, comparisonTimeseries);

        if (dataConsumer != null) {
            dataConsumer.accept(data);
        }
        return data;
    }

    private static List<Tuple<LocalDate, LocalDate>> computeRanges(LocalDate requestedStart,
                                                                   LocalDate requestedEnd,
                                                                   Granularity granularity) {
        List<Tuple<LocalDate, LocalDate>> outputDates = new ArrayList<>();
        LocalDate date = requestedStart;
        while (!date.isAfter(requestedEnd)) {
            outputDates.add(Tuple.create(date, granularity.computeEndOfRange(date)));
            date = granularity.increment(date);
        }
        return outputDates;
    }

    /**
     * Contains the original start date.
     * <p>
     * Note that {@link #getStart()} is probably more safe to use, as this is known to be adjusted.
     *
     * @return the original start of the period to query
     */
    public LocalDate getRequestedStart() {
        return requestedStart;
    }

    /**
     * Returns the start date of the period to query.
     *
     * @return the start of the period to query
     */
    public LocalDate getStart() {
        return ranges.get(0).getFirst();
    }

    /**
     * Contains the original end date.
     * <p>
     * Note that this might be misleading, as for {@link Granularity#MONTH}, this will be the <b>first</b> of the month
     * to query, not the end date. Use {@link #getEnd()} to obtain the actual end date to use.
     *
     * @return the original end of the period to query
     */
    public LocalDate getRequestedEnd() {
        return requestedEnd;
    }

    /**
     * Returns the effective end date of the period to query.
     *
     * @return the end of the last range to query
     */
    public LocalDate getEnd() {
        return ranges.get(ranges.size() - 1).getSecond();
    }

    public Granularity getGranularity() {
        return granularity;
    }

    /**
     * Returns all effective ranges to query or show.
     *
     * @return a list of all ranges to query
     */
    public List<Tuple<LocalDate, LocalDate>> getRanges() {
        return Collections.unmodifiableList(ranges);
    }

    /**
     * A stream of the start dates of all ranges to query.
     *
     * @return the start ranges to query. These are also shown as chart labels in the final chart.
     */
    public Stream<LocalDate> startDates() {
        return ranges.stream().map(Tuple::getFirst);
    }

    /**
     * Enforces {@link Granularity#MONTH} by creating a new {@link TimeSeries} if necessary.
     * <p>
     * This can be used to answer to "daily" queries if only monthly queries are present. This will compact the
     * ranges, so that only a reduced number of ranges is present.
     * <p>
     * Note that {@link TimeSeriesData#toDataset(TimeSeries)} will detect this difference and perform proper
     * interpolation (by using the same value for each day of a month), so that daily and monthly charts have
     * the same number of values on the X axis and thus can be properly compared.
     *
     * @return a timeseries which is guaranteed to have {@link Granularity#MONTH} as granularity
     */
    public TimeSeries toMonthlySeries() {
        if (granularity == Granularity.MONTH) {
            return this;
        }

        TimeSeries monthlySeries = new TimeSeries(requestedStart.withDayOfMonth(1),
                                                  requestedEnd.with(TemporalAdjusters.lastDayOfMonth()),
                                                  Granularity.MONTH).withDataConsumer(dataConsumer);
        if (isComparisonTimeseries()) {
            monthlySeries.markAsComparisonSeries();
        }

        return monthlySeries;
    }

    protected TimeSeries comparisonSeries(ComparisonPeriod comparisonPeriod) {
        return new TimeSeries(comparisonPeriod.computeDate(requestedStart),
                              comparisonPeriod.computeDate(requestedEnd),
                              granularity,
                              ranges.stream()
                                    .map(range -> Tuple.create(comparisonPeriod.computeDate(range.getFirst()),
                                                               comparisonPeriod.computeDate(range.getSecond())))
                                    .toList()).withDataConsumer(dataConsumer).markAsComparisonSeries();
    }

    protected TimeSeries markAsComparisonSeries() {
        this.comparisonTimeseries = true;
        return this;
    }

    public boolean isComparisonTimeseries() {
        return comparisonTimeseries;
    }
}
