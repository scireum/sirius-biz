/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.explorer;

import sirius.biz.analytics.metrics.Dataset;
import sirius.kernel.commons.Callback;
import sirius.kernel.nls.NLS;
import sirius.web.services.JSONStructuredOutput;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Provides a base class to output time-series data into the {@link DataExplorerController Data-Explorer}.
 * <p>
 * Subclasses will probably use one or more {@link TimeSeriesComputer} to compute the actual chart data.
 * <p>
 * Subclasses have to be {@link sirius.kernel.di.std.Register registered} in order to be visible to the framework.
 *
 * @param <O> the type of entities expected by this factory
 */
public abstract class TimeSeriesChartFactory<O> extends ChartFactory<O> {

    private static final String[] ICONS = {"fas fa-chart-line", "fas fa-chart-area", "fas fa-chart-bar"};
    private static final String OUTPUT_TYPE = "type";
    private static final String OUTPUT_LABELS = "labels";
    private static final String OUTPUT_RANGE = "range";
    private static final String OUTPUT_DATASETS = "datasets";
    private static final String OUTPUT_DATASET = "dataset";
    private static final String OUTPUT_LABEL = "label";
    private static final String OUTPUT_AXIS = "axis";
    private static final String OUTPUT_COLOR = "color";
    private static final String OUTPUT_DATA = "data";

    @Override
    public String getIcon() {
        return ICONS[Math.abs(getName().hashCode() % ICONS.length)];
    }

    protected String determineChartType(boolean hasComparisonPeriod) {
        return stackValues(hasComparisonPeriod) ? "area" : "line";
    }

    /**
     * Determines if values should be stacked instead of showing them side-by-side.
     *
     * @param hasComparisonPeriod <tt>true</tt> if a comparison period is requested, <tt>false</tt> otherwise
     * @return <tt>true</tt> to create an "area" chart, <tt>false</tt> to create a normal line chart
     */
    protected boolean stackValues(boolean hasComparisonPeriod) {
        return false;
    }

    /**
     * Executes the computers to generate the data for the chart.
     *
     * @param hasComparisonPeriod determines if a comparison period is requested
     * @param isComparisonPeriod  determines if we're currently executing the comparison period
     * @param executor            the executor which can be supplied with one or more {@link TimeSeriesComputer computers}
     * @throws Exception in case of any error while computing the chart data
     */
    protected abstract void computers(boolean hasComparisonPeriod,
                                      boolean isComparisonPeriod,
                                      Callback<TimeSeriesComputer<O>> executor) throws Exception;

    @Override
    protected void computeData(O object,
                               LocalDate start,
                               LocalDate end,
                               Granularity granularity,
                               ComparisonPeriod comparisonPeriod,
                               Consumer<String> hints,
                               JSONStructuredOutput output) throws Exception {
        TimeSeries timeSeries = new TimeSeries(start, end, granularity);
        List<TimeSeriesData> data = new ArrayList<>();
        timeSeries.withDataConsumer(data::add);
        output.property(OUTPUT_TYPE,
                        determineChartType(comparisonPeriod != null && comparisonPeriod != ComparisonPeriod.NONE));
        output.array(OUTPUT_LABELS, OUTPUT_RANGE, timeSeries.startDates().map(granularity::format).toList());

        // Run all "main" computers...
        computers(comparisonPeriod != ComparisonPeriod.NONE, false, computer -> {
            computer.compute(object, timeSeries);
        });

        // Run the computers for the comparison period if necessary...
        TimeSeries comparisonTimeSeries = timeSeries.comparisonSeries(comparisonPeriod);
        if (comparisonPeriod != null && comparisonPeriod != ComparisonPeriod.NONE) {
            computers(true, true, computer -> {
                computer.compute(object, comparisonTimeSeries);
            });
        }

        // Output the collected data and determine if and how often we interpolate...
        output.beginArray(OUTPUT_DATASETS);
        for (TimeSeriesData timeseriesData : data) {
            if (timeseriesData.isComparisonTimeSeries()) {
                outputDataset(timeseriesData.toDataset(comparisonTimeSeries), output);
            } else {
                outputDataset(timeseriesData.toDataset(timeSeries), output);
            }
        }
        output.endArray();

        // Generate a hint based on the detected interpolations...
        generateInterpolationHint(hints, timeSeries, data);

        // Generated another hint, if a comparison period was requested, but none is present in the output...
        generateComparisonHint(comparisonPeriod, hints, data);
    }

    private void outputDataset(Dataset dataset, JSONStructuredOutput output) {
        output.beginObject(OUTPUT_DATASET);
        output.property(OUTPUT_LABEL, dataset.getLabel());
        output.property(OUTPUT_AXIS, dataset.getAxis());
        if (dataset.isGray()) {
            output.property(OUTPUT_COLOR, Dataset.COLOR_GRAY);
        }
        output.array(OUTPUT_DATA, OUTPUT_DATA, dataset.getValues());
        output.endObject();
    }

    private void generateInterpolationHint(Consumer<String> hints, TimeSeries timeseries, List<TimeSeriesData> data) {
        if (data.stream()
                .anyMatch(timeSeriesData -> timeSeriesData.getGranularity() == Granularity.MONTH
                                            && timeseries.getGranularity() == Granularity.DAY)) {
            if (data.stream()
                    .allMatch(timeSeriesData -> timeSeriesData.getGranularity() == Granularity.MONTH
                                                && timeseries.getGranularity() == Granularity.DAY)) {
                hints.accept(NLS.get("TimeSeriesChartFactory.hintMonthlyValuesOnly"));
            } else {
                hints.accept(NLS.get("TimeSeriesChartFactory.hintSomeMonthlyValues"));
            }
        }
    }

    private void generateComparisonHint(ComparisonPeriod comparisonPeriod,
                                        Consumer<String> hints,
                                        List<TimeSeriesData> data) {
        if (comparisonPeriod != ComparisonPeriod.NONE && data.stream()
                                                             .noneMatch(TimeSeriesData::isComparisonTimeSeries)) {
            hints.accept(NLS.get("TimeSeriesChartFactory.noComparisonSupported"));
        }
    }
}
