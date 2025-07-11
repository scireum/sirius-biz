/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.biz.analytics.reports.Cell;
import sirius.biz.analytics.reports.Report;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.interactive.ReportJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.Row;
import sirius.db.jdbc.SQLQuery;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Urls;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Provides a report which shows the utilization of the Clickhouse database which stores all recorded events.
 */
@Register
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ClickhousePartsReportJobFactory extends ReportJobFactory {

    private static final String COLUMN_DATABASE = "database";
    private static final String COLUMN_TABLE = "table";
    private static final String COLUMN_ROWS = "rows";
    private static final String COLUMN_SIZE = "size";
    private static final String COLUMN_DISK_SIZE = "diskSize";
    private static final String COLUMN_PARTITIONS = "partitions";

    @Part
    private EventRecorder recorder;

    @Part
    private Mixing mixing;

    private Map<String, String> tableNameToType;

    @Override
    public String getLabel() {
        return "Clickhouse Utilization";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Shows the size of the analytics data warehouse stored in Clickhouse.";
    }

    @Override
    protected void computeReport(Map<String, String> context,
                                 Report report,
                                 BiConsumer<String, Cell> additionalMetricConsumer) throws Exception {
        SQLQuery query = recorder.getDatabase()
                                 .createQuery("SELECT database,"
                                              + " table,"
                                              + " sum(rows) as rows,"
                                              + " sum(data_uncompressed_bytes) size,"
                                              + " sum(bytes_on_disk) diskSize,"
                                              + " count(*) as partitions"
                                              + " FROM system.parts"
                                              + " WHERE database <> 'system'"
                                              + " GROUP BY database, table"
                                              + " ORDER BY database, table");

        report.addColumn(COLUMN_DATABASE, "Database");
        report.addColumn(COLUMN_TABLE, "Table");
        report.addColumn(COLUMN_ROWS, "Rows");
        report.addColumn(COLUMN_SIZE, "Size");
        report.addColumn(COLUMN_DISK_SIZE, "Size on Disk");
        report.addColumn(COLUMN_PARTITIONS, "Partitions");

        Amount numberOfTables = Amount.ZERO;
        Amount totalRows = Amount.ZERO;
        Amount totalSize = Amount.ZERO;
        Amount totalDiskSize = Amount.ZERO;
        Amount numberOfPartitions = Amount.ZERO;

        for (Row row : query.queryList()) {
            String tableName = row.getValue(COLUMN_TABLE).asString();
            report.addCells(cells.of(row.getValue(COLUMN_DATABASE).get()),
                            cells.link(tableName, determineLinkToSizeChart(tableName), true),
                            cells.rightAligned(row.getValue(COLUMN_ROWS).getAmount()),
                            cells.rightAligned(NLS.formatSize(row.getValue(COLUMN_SIZE).asLong(0))),
                            cells.rightAligned(NLS.formatSize(row.getValue(COLUMN_DISK_SIZE).asLong(0))),
                            cells.rightAligned(row.getValue(COLUMN_PARTITIONS).getAmount()));

            numberOfTables = numberOfTables.add(Amount.ONE);
            totalRows = totalRows.add(row.getValue(COLUMN_ROWS).getAmount());
            totalSize = totalSize.add(row.getValue(COLUMN_SIZE).getAmount());
            totalDiskSize = totalDiskSize.add(row.getValue(COLUMN_DISK_SIZE).getAmount());
            numberOfPartitions = numberOfPartitions.add(row.getValue(COLUMN_PARTITIONS).getAmount());
        }

        additionalMetricConsumer.accept("Num. Tables", cells.of(numberOfTables));
        additionalMetricConsumer.accept("Total Rows", cells.of(totalRows));
        additionalMetricConsumer.accept("Total Size", cells.of(NLS.formatSize(totalSize.getAmount().longValue())));
        additionalMetricConsumer.accept("Total Disk Size",
                                        cells.of(NLS.formatSize(totalDiskSize.getAmount().longValue())));
        additionalMetricConsumer.accept("Num. Partitions", cells.of(numberOfPartitions));
    }

    private String determineLinkToSizeChart(String tableName) {
        if (tableNameToType == null) {
            tableNameToType = mixing.getDescriptors()
                                    .stream()
                                    .collect(Collectors.toMap(EntityDescriptor::getRelationName,
                                                              descriptor -> Mixing.getNameForType(descriptor.getType()),
                                                              (mapValue, encounteredValue) -> mapValue));
        }

        return Optional.ofNullable(tableNameToType.get(tableName))
                       .map(typeName -> Strings.apply("/job/%s?type=%s",
                                                      ClickhouseSizeChartJobFactory.JOB_NAME,
                                                      Urls.encode(typeName)))
                       .orElse(null);
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        // there are no parameters for this job...
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }

    @Nonnull
    @Override
    public String getName() {
        return "clickhouse-parts";
    }

    @Override
    public int getPriority() {
        return 8210;
    }
}
