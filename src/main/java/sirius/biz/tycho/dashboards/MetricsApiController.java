/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.web.BizController;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

/**
 * Provides the API called by the metrics dashboard UI.
 * <p>
 * Note that this is called by the helpers defined in <tt>metrics-dashboards.js</tt>. These are created / rendered by
 * the macros <tt>&lt;t:keyMetrics&gt;</tt> or <tt>&lt;t:metricsDashboard&gt;</tt>.
 */
@Register
public class MetricsApiController extends BizController {

    @Part
    private MetricsDashboard dashboards;

    /**
     * Fetches the key metrics and charts as defined by the UI.
     * <p>
     * We use this "lazy loading" approach, so that a rendered page is never slowed down by the metrics
     * framework.
     *
     * @param webContext the current request
     * @param output     the output to write the JSON data to
     */
    @InternalService
    @Routed("/tycho/metrics/api")
    public void fetchMetrics(WebContext webContext, JSONStructuredOutput output) {
        JSONObject jsonContent = webContext.getJSONContent();
        output.beginArray("tasks");
        for (Object obj : jsonContent.getJSONArray("tasks")) {
            if (obj instanceof JSONObject) {
                fetchMetric((JSONObject) obj, output);
            }
        }
        output.endArray();
    }

    private void fetchMetric(JSONObject obj, JSONStructuredOutput output) {
        if ("KeyMetric".equals(obj.getString("type"))) {
            fetchKeyMetric(obj, output);
        } else if ("Chart".equals(obj.getString("type"))) {
            fetchChart(obj, output);
        } else {
            output.beginObject("task").endObject();
        }
    }

    private void fetchKeyMetric(JSONObject obj, JSONStructuredOutput output) {
        try {
            KeyMetric metric = dashboards.resolveKeyMetric(obj.getString("provider"),
                                                           obj.getString("target"),
                                                           obj.getString("metric"));
            metric.writeJson(output);
        } catch (Exception e) {
            Exceptions.handle()
                      .to(Log.APPLICATION)
                      .error(e)
                      .withSystemErrorMessage("Failed to fetch key metric for: %s - %s (%s)", obj.toJSONString())
                      .handle();
        }
    }

    private void fetchChart(JSONObject obj, JSONStructuredOutput output) {
        try {
            Chart chart = dashboards.resolveChart(obj.getString("provider"),
                                                  obj.getString("target"),
                                                  obj.getString("metric"));
            chart.writeJson(output);
        } catch (Exception e) {
            Exceptions.handle()
                      .to(Log.APPLICATION)
                      .error(e)
                      .withSystemErrorMessage("Failed to fetch chart for: %s - %s (%s)", obj.toJSONString())
                      .handle();
        }
    }
}
