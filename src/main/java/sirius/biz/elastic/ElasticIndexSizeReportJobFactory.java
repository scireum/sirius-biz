/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.analytics.reports.Cell;
import sirius.biz.analytics.reports.Report;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.interactive.ReportJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.es.Elastic;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permission;
import sirius.web.util.JSONPath;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Reports the cluster health and index size of Elasticsearch.
 */
@Register
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ElasticIndexSizeReportJobFactory extends ReportJobFactory {

    @Part
    private Elastic elastic;

    @Override
    public String getLabel() {
        return "Elasticsearch Index Health";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Reports the cluster health and index size of Elasticsearch.";
    }

    @Override
    protected void computeReport(Map<String, String> context,
                                 Report report,
                                 BiConsumer<String, Cell> additionalMetricConsumer) throws Exception {
        determineClusterHealth(additionalMetricConsumer);
        outputIndexSizes(report);
    }

    private void determineClusterHealth(BiConsumer<String, Cell> additionalMetricConsumer) {
        JSONObject clusterHealth = elastic.getLowLevelClient().clusterHealth();
        additionalMetricConsumer.accept("State", determineClusterStatus(clusterHealth));
        additionalMetricConsumer.accept("Number of Nodes",
                                        cells.rightAligned(clusterHealth.getIntValue("number_of_nodes")));
        additionalMetricConsumer.accept("Active Shards",
                                        cells.rightAligned(clusterHealth.getIntValue("active_shards")));
        additionalMetricConsumer.accept("Relocating Shards",
                                        cells.rightAligned(clusterHealth.getIntValue("relocating_shards")));
        additionalMetricConsumer.accept("Initializing Shards",
                                        cells.rightAligned(clusterHealth.getIntValue("initializing_shards")));
        additionalMetricConsumer.accept("Unassigned Shards",
                                        cells.rightAligned(clusterHealth.getIntValue("unassigned_shards")));
        additionalMetricConsumer.accept("Pending Tasks Shards",
                                        cells.rightAligned(clusterHealth.getIntValue("number_of_pending_tasks")));
    }

    private Cell determineClusterStatus(JSONObject clusterHealth) {
        String status = clusterHealth.getString("status");
        if (Strings.areEqual(status, "green")) {
            return cells.green(status);
        }
        if (Strings.areEqual(status, "yellow")) {
            return cells.yellow(status);
        }
        if (Strings.areEqual(status, "red")) {
            return cells.red(status);
        }

        return cells.of(status);
    }

    private void outputIndexSizes(Report report) {
        JSONObject indexStats = elastic.getLowLevelClient().indexStats();
        JSONObject indices = indexStats.getJSONObject("indices");
        report.addColumn("name", "Index");
        report.addColumn("docs", "Primary Docs");
        report.addColumn("size", "Primary Size");
        report.addColumn("total_docs", "Total Docs");
        report.addColumn("total_size", "Total Size");
        report.addColumn("shards", "Shards");
        report.addColumn("replicas", "Replicas");
        for (String indexName : indices.keySet()) {
            JSONObject total = indices.getJSONObject(indexName).getJSONObject("total");
            JSONObject primaries = indices.getJSONObject(indexName).getJSONObject("primaries");
            JSONObject settings = elastic.getLowLevelClient().indexSettings(indexName).getJSONObject(indexName);
            report.addCells(cells.of(indexName),
                            cells.rightAligned(JSONPath.queryValue(primaries, "docs.count").asLong(0)),
                            cells.rightAligned(NLS.formatSize(JSONPath.queryValue(primaries, "store.size_in_bytes")
                                                                      .asLong(0))),
                            cells.rightAligned(JSONPath.queryValue(total, "docs.count").asLong(0)),
                            cells.rightAligned(NLS.formatSize(JSONPath.queryValue(total, "store.size_in_bytes")
                                                                      .asLong(0))),
                            cells.rightAligned(JSONPath.queryValue(settings, "settings.index.number_of_shards")),
                            cells.rightAligned(JSONPath.queryValue(settings, "settings.index.number_of_replicas")));
        }
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        // No parameters required...
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }

    @Nonnull
    @Override
    public String getName() {
        return "elastic-index-size";
    }
}
