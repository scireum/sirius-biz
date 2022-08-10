/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

import sirius.biz.analytics.charts.Dataset;
import sirius.biz.analytics.reports.Cell;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.ValueHolder;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Provides a base implementation for interactive line or bar charts.
 */
public abstract class LinearChartJobFactory extends InteractiveJobFactory {

    /**
     * Contains the name of the template used to render line charts.
     */
    public static final String LINE_CHART_TEMPLATE = "/templates/biz/jobs/linechart.html.pasta";

    /**
     * Contains the name of the template used to render bar charts.
     */
    public static final String BAR_CHART_TEMPLATE = "/templates/biz/jobs/barchart.html.pasta";

    @Override
    public String getIcon() {
        return "fas fa-chart-area";
    }

    @Override
    protected void generateResponse(WebContext request, Map<String, String> context) {
        ValueHolder<List<String>> labels = new ValueHolder<>(null);
        List<Dataset> datasets = new ArrayList<>();
        List<Tuple<String, Cell>> additionalMetrics = new ArrayList<>();

        try {
            computeChartData(context,
                             labels::set,
                             datasets::add,
                             (name, value) -> additionalMetrics.add(Tuple.create(name, value)));
        } catch (Exception e) {
            UserContext.handle(e);
        }

        request.respondWith()
               .template(getTemplate(), this, context, formatLabels(labels.get()), datasets, additionalMetrics);
    }

    /**
     * A simple helper method which formats a list of labels into a string expected by the JavaScript API.
     *
     * @param labels the labels to encode
     * @return the labels encoded as JS string
     */
    protected static String formatLabels(List<String> labels) {
        if (labels == null) {
            return "";
        }

        return labels.stream().map(label -> "'" + label + "'").collect(Collectors.joining(","));
    }

    /**
     * Defines the template used to render the chart.
     * <p>
     * Use {@link #BAR_CHART_TEMPLATE} to render a bar instead of a line chart.
     *
     * @return the template used to render the result
     */
    protected String getTemplate() {
        return LINE_CHART_TEMPLATE;
    }

    /**
     * Computes the chart data based on the given parameters.
     *
     * @param context                  the parameters provided by the user
     * @param labelConsumer            used to collect labels for the X axis
     * @param datasetConsumer          used to collect datasets which each represent either a line or bar
     * @param additionalMetricConsumer used to collect additional metrics (label and the value represented as cell).
     *                                 This can be used to output averages, min and max values etc.
     * @throws Exception in case of an unexpected error which aborted the computations
     */
    protected abstract void computeChartData(Map<String, String> context,
                                             Consumer<List<String>> labelConsumer,
                                             Consumer<Dataset> datasetConsumer,
                                             BiConsumer<String, Cell> additionalMetricConsumer) throws Exception;
}
