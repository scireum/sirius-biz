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
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.services.JSONStructuredOutput;

@Register(classes = Controller.class)
public class DashboardController extends BizController {

    @Part
    private Dashboards dashboards;

    @Routed(value = "/tycho/dashboard/api", jsonCall = true)
    public void fetchMetrics(WebContext webContext, JSONStructuredOutput output) {
        JSONObject jsonContent = webContext.getJSONContent();
        output.beginArray("metrics");
        for (Object obj : jsonContent.getJSONArray("metrics")) {
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
        }
    }

    private void fetchKeyMetric(JSONObject obj, JSONStructuredOutput output) {
        try {
            KeyMetric metric = dashboards.resolveKeyMetric(obj.getString("provider"),
                                                           obj.getString("target"),
                                                           obj.getString("metric"));
            metric.writeJSON(output);
        } catch (Exception e) {
        }
    }

    private void fetchChart(JSONObject obj, JSONStructuredOutput output) {
        try {
            Chart chart = dashboards.resolveChart(obj.getString("provider"),
                                                           obj.getString("target"),
                                                           obj.getString("metric"));
            chart.writeJSON(output);
        } catch (Exception e) {
        }
    }
}
