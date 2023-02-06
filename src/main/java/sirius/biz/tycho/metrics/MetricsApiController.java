/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.metrics;

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
 * Note that this is called by the helpers defined in <tt>key-metrics.js</tt>. These are created / rendered by
 * the macros <tt>&lt;t:keyMetrics&gt;</tt> or <tt>&lt;t:metricsDashboard&gt;</tt>.
 */
@Register
public class MetricsApiController extends BizController {

    @Part
    private KeyMetrics keyMetrics;

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
            if (obj instanceof JSONObject object) {
                fetchMetric(object, output);
            }
        }
        output.endArray();
    }

    private void fetchMetric(JSONObject obj, JSONStructuredOutput output) {
        // For now, there are only "KeyMetrics" which may be lazy-loaded, but we might add some more in the future...
        if ("KeyMetric".equals(obj.getString("type"))) {
            fetchKeyMetric(obj, output);
        } else {
            output.beginObject("task").endObject();
        }
    }

    private void fetchKeyMetric(JSONObject obj, JSONStructuredOutput output) {
        try {
            KeyMetric metric = keyMetrics.resolveKeyMetric(obj.getString("provider"),
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
}