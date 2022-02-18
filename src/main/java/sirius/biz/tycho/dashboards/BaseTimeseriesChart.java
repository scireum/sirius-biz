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
import java.util.Collections;
import java.util.List;

/**
 * Provides basic functionality needed by all time-series based charts.
 */
public abstract class BaseTimeseriesChart implements Chart {

    private List<String> labels = Collections.emptyList();
    private final List<Dataset> datasets = new ArrayList<>();

    /**
     * Adds a list of labels to be added as X axis.
     *
     * @param labels the labels to use for the chart
     * @return the chart itself for fluent method calls
     */
    public BaseTimeseriesChart withLabels(List<String> labels) {
        this.labels = new ArrayList<>(labels);
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
     * @param compareToPreviousYear  determines if a comparison dataset for the previous year should be added
     * @param compareToPreviousMonth determines if a comparison dataset for the previous month should be added.
     *                               Note that this is only feasible for daily metrics.
     * @return the chart itself for fluent method calls
     */
    public BaseTimeseriesChart fromMetricQuery(MetricQuery metricQuery,
                                               boolean compareToPreviousYear,
                                               boolean compareToPreviousMonth) {
        LocalDate now = LocalDate.now();
        withLabels(metricQuery.labelsUntil(now, metricQuery.determineDefaultLimit()));

        addDataset(new Dataset(String.valueOf(now.getYear())).addValues(metricQuery.valuesUntil(now,
                                                                                                metricQuery.determineDefaultLimit())));
        if (compareToPreviousMonth) {
            addDataset(new Dataset(ComparisonPeriod.PREVIOUS_MONTH.toString()).addValues(metricQuery.valuesUntil(now.minusMonths(
                    1), metricQuery.determineDefaultLimit())));
        }

        if (compareToPreviousYear) {
            addDataset(new Dataset(ComparisonPeriod.PREVIOUS_YEAR.toString()).addValues(metricQuery.valuesUntil(now.minusYears(
                    1), metricQuery.determineDefaultLimit())));
        }

        return this;
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
            output.array("data", "data", dataset.getValues());
            output.endObject();
        }
        output.endArray();
        output.endObject();
    }

    protected abstract String getChartType();
}
