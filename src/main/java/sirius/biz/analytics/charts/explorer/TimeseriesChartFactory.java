/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.analytics.charts.Dataset;
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
 * Subclasses will probably use one or more {@link TimeseriesComputer} to compute the actual chart data.
 * <p>
 * Subclasses have to be {@link sirius.kernel.di.std.Register registered} in order to be visible to the framework.
 *
 * @param <O> the type of entities expected by this factory
 */
public abstract class TimeseriesChartFactory<O> extends ChartFactory<O> {

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

    protected String determineChartType() {
        return stackValues() ? "area" : "line";
    }

    /**
     * Determines if values should be stacked instead of showing them side-by-side.
     *
     * @return <tt>true</tt> to create an "area" chart, <tt>false</tt> to create a normal line chart
     */
    protected boolean stackValues() {
        return false;
    }

    /**
     * Executes the computers to generate the data for the chart.
     *
     * @param hasComparisonPeriod determines if a comparison period is requested
     * @param isComparisonPeriod  determines if we're currently executing the comparison period
     * @param executor            the executor which can be supplied with one or more {@link TimeseriesComputer computers}
     * @throws Exception in case of any error while computing the chart data
     */
    protected abstract void computers(boolean hasComparisonPeriod,
                                      boolean isComparisonPeriod,
                                      Callback<TimeseriesComputer<O>> executor) throws Exception;

    @Override
    protected void computeData(O object,
                               LocalDate start,
                               LocalDate end,
                               Granularity granularity,
                               ComparisonPeriod comparisonPeriod,
                               Consumer<String> hints,
                               JSONStructuredOutput output) throws Exception {
        Timeseries timeseries = new Timeseries(start, end, granularity);
        List<TimeseriesData> data = new ArrayList<>();
        timeseries.withDataConsumer(data::add);
        output.property(OUTPUT_TYPE, determineChartType());
        output.array(OUTPUT_LABELS, OUTPUT_RANGE, timeseries.startDates().map(granularity::format).toList());

        // Run all "main" computers...
        computers(comparisonPeriod != null, false, computer -> {
            computer.compute(object, timeseries);
        });

        // Run the computers for the comparison period if necessary...
        Timeseries comparisonTimeseries = timeseries.comparisonSeries(comparisonPeriod);
        if (comparisonPeriod != null && comparisonPeriod != ComparisonPeriod.NONE) {
            computers(true, true, computer -> {
                computer.compute(object, comparisonTimeseries);
            });
        }

        // Output the collected data and determine if and how often we interpolate...
        output.beginArray(OUTPUT_DATASETS);
        for (TimeseriesData timeseriesData : data) {
            if (timeseriesData.isComparisonTimeseries()) {
                outputDataset(timeseriesData.toDataset(comparisonTimeseries), output);
            } else {
                outputDataset(timeseriesData.toDataset(timeseries), output);
            }
        }
        output.endArray();

        // Generate a hint based on the detected interpolations...
        generateInterpolationHint(hints, timeseries, data);

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

    private void generateInterpolationHint(Consumer<String> hints, Timeseries timeseries, List<TimeseriesData> data) {
        if (data.stream()
                .anyMatch(timeseriesData -> timeseriesData.getGranularity() == Granularity.MONTH
                                            && timeseries.getGranularity() == Granularity.DAY)) {
            if (data.stream()
                    .allMatch(timeseriesData -> timeseriesData.getGranularity() == Granularity.MONTH
                                                && timeseries.getGranularity() == Granularity.DAY)) {
                hints.accept(NLS.get("TimeseriesChartFactory.hintMonthlyValuesOnly"));
            } else {
                hints.accept(NLS.get("TimeseriesChartFactory.hintSomeMonthlyValues"));
            }
        }
    }

    private void generateComparisonHint(ComparisonPeriod comparisonPeriod,
                                        Consumer<String> hints,
                                        List<TimeseriesData> data) {
        if (comparisonPeriod != ComparisonPeriod.NONE && data.stream()
                                                             .noneMatch(TimeseriesData::isComparisonTimeseries)) {
            hints.accept(NLS.get("TimeseriesChartFactory.noComparisonSupported"));
        }
    }
}
