/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.analytics.reports.Cell;
import sirius.biz.analytics.reports.Report;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.interactive.ReportJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.es.Elastic;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permission;

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
        ObjectNode clusterHealth = elastic.getLowLevelClient().clusterHealth();
        additionalMetricConsumer.accept("State", determineClusterStatus(clusterHealth));
        additionalMetricConsumer.accept("Number of Nodes",
                                        cells.rightAligned(clusterHealth.path("number_of_nodes").asInt()));
        additionalMetricConsumer.accept("Active Shards",
                                        cells.rightAligned(clusterHealth.path("active_shards").asInt()));
        additionalMetricConsumer.accept("Relocating Shards",
                                        cells.rightAligned(clusterHealth.path("relocating_shards").asInt()));
        additionalMetricConsumer.accept("Initializing Shards",
                                        cells.rightAligned(clusterHealth.path("initializing_shards").asInt()));
        additionalMetricConsumer.accept("Unassigned Shards",
                                        cells.rightAligned(clusterHealth.path("unassigned_shards").asInt()));
        additionalMetricConsumer.accept("Pending Tasks Shards",
                                        cells.rightAligned(clusterHealth.path("number_of_pending_tasks").asInt()));
    }

    private Cell determineClusterStatus(ObjectNode clusterHealth) {
        String status = clusterHealth.path("status").asText();
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
        ObjectNode indexStats = elastic.getLowLevelClient().indexStats();
        ObjectNode indices = Json.getObject(indexStats, "/indices");
        report.addColumn("name", "Index");
        report.addColumn("docs", "Primary Docs");
        report.addColumn("size", "Primary Size");
        report.addColumn("total_docs", "Total Docs");
        report.addColumn("total_size", "Total Size");
        report.addColumn("shards", "Shards");
        report.addColumn("replicas", "Replicas");
        for (Map.Entry<String, JsonNode> entry : indices.properties()) {
            String indexName = entry.getKey();
            ObjectNode index = Json.getObject(indices, indexName);
            ObjectNode total = Json.getObject(index, "total");
            ObjectNode primaries = Json.getObject(index, "primaries");
            ObjectNode settings = Json.getObject(elastic.getLowLevelClient().indexSettings(indexName), indexName);
            report.addCells(cells.of(indexName),
                            cells.rightAligned(primaries.path("docs").path("count").asLong()),
                            cells.rightAligned(NLS.formatSize(primaries.path("store").path("size_in_bytes").asLong())),
                            cells.rightAligned(total.path("docs").path("count").asLong()),
                            cells.rightAligned(NLS.formatSize(total.path("store").path("size_in_bytes").asLong())),
                            cells.rightAligned(settings.path("settings")
                                                       .path("index")
                                                       .path("number_of_shards")
                                                       .asLong()),
                            cells.rightAligned(settings.path("settings")
                                                       .path("index")
                                                       .path("number_of_replicas")
                                                       .asLong()));
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

    @Override
    public int getPriority() {
        return 8220;
    }
}
