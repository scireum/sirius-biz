/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

import sirius.biz.analytics.charts.Charts;
import sirius.biz.analytics.charts.Dataset;
import sirius.kernel.commons.ValueHolder;
import sirius.web.http.WebContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class LinearChartJobFactory extends InteractiveJobFactory {

    public static final String LINE_CHART_TEMPLATE = "/templates/jobs/linechart.html.pasta";
    public static final String BAR_CHART_TEMPLATE = "/templates/jobs/barchart.html.pasta";

    @Override
    protected void generateResponse(WebContext request, Map<String, String> context) {
        ValueHolder<List<String>> labels = new ValueHolder<>(null);
        List<Dataset> datasets = new ArrayList<>();

        computeChartData(context, labels::set, datasets::add);

        request.respondWith().template(getTemplate(), this, context, Charts.formatLabels(labels.get()), datasets);
    }

    protected String getTemplate() {
        return LINE_CHART_TEMPLATE;
    }

    protected abstract void computeChartData(Map<String, String> context,
                                             Consumer<List<String>> labelConsumer,
                                             Consumer<Dataset> datasetConsumer);
}
