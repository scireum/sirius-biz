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
import sirius.web.http.WebContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class PolarAreaChartJobFactory extends InteractiveJobFactory {

    @Override
    protected void generateResponse(WebContext request, Map<String, String> context) {
        List<String> labels = new ArrayList<>();
        Dataset dataset = new Dataset("");

        computeChartData(context, (label, value) -> {
            labels.add(label);
            dataset.addValue(value);
        });

        request.respondWith()
               .template("/templates/jobs/polarareachart.html.pasta",
                         this,
                         context,
                         Charts.formatLabels(labels),
                         dataset.getData());
    }

    protected abstract void computeChartData(Map<String, String> context, BiConsumer<String, Number> valueConsumer);
}
