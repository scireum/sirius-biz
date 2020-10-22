/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.HelperFactory;
import sirius.web.security.ScopeInfo;

import javax.annotation.Nonnull;

public class DashboardHelper {

    @Register
    public static class DashboardHelperFactory implements HelperFactory<DashboardHelper> {

        @Nonnull
        @Override
        public Class<DashboardHelper> getHelperType() {
            return DashboardHelper.class;
        }

        @Nonnull
        @Override
        public String getName() {
            return "tycho-dashboards";
        }

        @Nonnull
        @Override
        public DashboardHelper make(@Nonnull ScopeInfo scope) {
            return new DashboardHelper();
        }
    }

    @Part
    private Dashboards dashboards;

    public JSONObject fetchImportantMetrics(Object target) {
        JSONObject result = new JSONObject();
        JSONArray metrics = new JSONArray();
        result.put("metrics", metrics);
        dashboards.fetchImportantKeyMetrics(target, 4)
                  .stream()
                  .map(MetricDescription::asJSON)
                  .peek(json -> json.put("type", "KeyMetric"))
                  .forEach(metrics::add);
        dashboards.fetchImportantCharts(target, 4)
                  .stream()
                  .map(MetricDescription::asJSON)
                  .peek(json -> json.put("type", "Chart"))
                  .forEach(metrics::add);

        return result;
    }

    public JSONObject fetchAllMetrics(Object target) {
        JSONObject result = new JSONObject();
        JSONArray metrics = new JSONArray();
        result.put("metrics", metrics);
        dashboards.fetchAllKeyMetrics(target)
                  .stream()
                  .map(MetricDescription::asJSON)
                  .peek(json -> json.put("type", "KeyMetric"))
                  .forEach(metrics::add);
        dashboards.fetchAllCharts(target)
                  .stream()
                  .map(MetricDescription::asJSON)
                  .peek(json -> json.put("type", "Chart"))
                  .forEach(metrics::add);

        return result;
    }
}
