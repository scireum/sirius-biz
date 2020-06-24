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
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Provides a base implementation for interactive charts which display a single dataset.
 */
public abstract class SingleDatasetChartJobFactory extends InteractiveJobFactory {

    @Override
    protected void generateResponse(WebContext request, Map<String, String> context) {
        List<String> labels = new ArrayList<>();
        Dataset dataset = new Dataset("");
        List<Tuple<String, Cell>> additionalMetrics = new ArrayList<>();

        try {
            computeChartData(context, (label, value) -> {
                labels.add(label);
                dataset.addValue(value);
            }, (name, value) -> additionalMetrics.add(Tuple.create(name, value)));
        } catch (Exception e) {
            UserContext.handle(e);
        }

        request.respondWith()
               .template(getTemplate(),
                         this,
                         context,
                         LinearChartJobFactory.formatLabels(labels),
                         dataset.renderData(),
                         additionalMetrics);
    }

    /**
     * Specifies the template used to render the chart.
     *
     * @return the name or path of the template to render
     */
    protected abstract String getTemplate();

    /**
     * Computes the chart data based on the given parameters.
     *
     * @param context                  the parameters provided by the user
     * @param valueConsumer            used to collect datapoints (label and value) to render as chart
     * @param additionalMetricConsumer used to collect additional metrics (label and the value represented as cell).
     *                                 This can be used to output averages, min and max values etc.
     * @throws Exception in case of an unexpected error which aborted the computations
     */
    protected abstract void computeChartData(Map<String, String> context,
                                             BiConsumer<String, Number> valueConsumer,
                                             BiConsumer<String, Cell> additionalMetricConsumer) throws Exception;
}
