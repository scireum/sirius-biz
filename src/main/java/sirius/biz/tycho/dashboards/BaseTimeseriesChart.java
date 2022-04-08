/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.biz.analytics.charts.ComparisonPeriod;
import sirius.biz.analytics.charts.Dataset;
import sirius.biz.analytics.metrics.MetricQuery;
import sirius.web.services.JSONStructuredOutput;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides basic functionality needed by all time-series based charts.
 */
public abstract class BaseTimeseriesChart implements Chart {

    private final List<String> labels = new ArrayList<>();
    private final List<Dataset> datasets = new ArrayList<>();

    /**
     * Adds a list of labels to be added as X axis.
     * <p>
     * Note that and previously added labels will be removed and replaced by the given list.
     *
     * @param labels the labels to use for the chart
     * @return the chart itself for fluent method calls
     */
    public BaseTimeseriesChart withLabels(List<String> labels) {
        this.labels.clear();
        this.labels.addAll(labels);
        return this;
    }

    /**
     * Adds a dataset to show in the chart.
     *
     * @param dataset the dataset to show
     * @return the chart itself for fluent method calls
     */
    public BaseTimeseriesChart addDataset(Dataset dataset) {
        this.datasets.add(dataset);
        return this;
    }

    /**
     * Uses the given {@link MetricQuery} to add one (or more) datasets to the chart.
     *
     * @param metricQuery            the query used to fetch the data from
     * @param scaleFactor            the scaling factor to apply. This can be used to output decimal data, which isn't
     *                               supported by {@link sirius.biz.analytics.metrics.Metrics} itself, which only stores
     *                               integer numbers.
     * @param compareToPreviousYear  determines if a comparison dataset for the previous year should be added
     * @param compareToPreviousMonth determines if a comparison dataset for the previous month should be added.
     *                               Note that this is only feasible for daily metrics.
     * @return the chart itself for fluent method calls
     */
    public BaseTimeseriesChart fromMetricQuery(MetricQuery metricQuery,
                                               float scaleFactor,
                                               boolean compareToPreviousYear,
                                               boolean compareToPreviousMonth) {
        LocalDate now = LocalDate.now();
        withLabels(metricQuery.labelsUntil(now, metricQuery.determineDefaultLimit()));

        addDataset(new Dataset(String.valueOf(now.getYear())).addValues(metricQuery.valuesUntil(now,
                                                                                                metricQuery.determineDefaultLimit()))
                                                             .scale(scaleFactor));
        if (compareToPreviousMonth) {
            Dataset previousMonth =
                    new Dataset(ComparisonPeriod.PREVIOUS_MONTH.toString()).addValues(metricQuery.valuesUntil(now.minusMonths(
                            1), metricQuery.determineDefaultLimit())).scale(scaleFactor);
            if (!compareToPreviousYear) {
                previousMonth.markGray();
            }
            addDataset(previousMonth);
        }

        if (compareToPreviousYear) {
            Dataset previousYear =
                    new Dataset(ComparisonPeriod.PREVIOUS_YEAR.toString()).addValues(metricQuery.valuesUntil(now.minusYears(
                            1), metricQuery.determineDefaultLimit())).scale(scaleFactor);
            if (!compareToPreviousMonth) {
                previousYear.markGray();
            }
            addDataset(previousYear);
        }

        return this;
    }

    /**
     * Uses the given {@link MetricQuery} to add one (or more) datasets to the chart.
     *
     * @param metricQuery            the query used to fetch the data from
     * @param compareToPreviousYear  determines if a comparison dataset for the previous year should be added
     * @param compareToPreviousMonth determines if a comparison dataset for the previous month should be added.
     *                               Note that this is only feasible for daily metrics.
     * @return the chart itself for fluent method calls
     */
    public BaseTimeseriesChart fromMetricQuery(MetricQuery metricQuery,
                                               boolean compareToPreviousYear,
                                               boolean compareToPreviousMonth) {
        return fromMetricQuery(metricQuery, 1, compareToPreviousYear, compareToPreviousMonth);
    }

    @Override
    public void writeJson(JSONStructuredOutput output) {
        output.beginObject("metric");
        output.property("type", getChartType());
        output.array("labels", "label", labels);
        output.beginArray("datasets");
        for (Dataset dataset : datasets) {
            output.beginObject("dataset");
            output.property("label", dataset.getLabel());
            output.property("axis", dataset.getAxis());
            if (dataset.isGray()) {
                output.property("color", Dataset.COLOR_GRAY);
            }
            output.array("data", "data", dataset.getValues());
            output.endObject();
        }
        output.endArray();
        output.endObject();
    }

    protected abstract String getChartType();
}
